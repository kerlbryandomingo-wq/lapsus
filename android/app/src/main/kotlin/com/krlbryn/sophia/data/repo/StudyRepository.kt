package com.krlbryn.sophia.data.repo

import com.krlbryn.sophia.core.AiProvider
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.AiRequest
import com.krlbryn.sophia.core.ParseResult
import com.krlbryn.sophia.core.ProviderException
import com.krlbryn.sophia.core.StudyAnswer
import com.krlbryn.sophia.core.StudyAssistant
import com.krlbryn.sophia.core.StudyMode
import com.krlbryn.sophia.data.db.StudyNote
import com.krlbryn.sophia.data.db.StudyNoteDao
import com.krlbryn.sophia.data.secure.SecretStore
import com.krlbryn.sophia.data.settings.AppSettings
import com.krlbryn.sophia.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Name under which each provider's API key is stored in the [SecretStore]. */
fun secretNameFor(provider: AiProviderId): String = "api_key_" + provider.name.lowercase()

/** Sends reviewed study material to the provider the student configured, and stores saved answers. */
@Singleton
class StudyRepository @Inject constructor(
    private val providers: Map<AiProviderId, @JvmSuppressWildcards AiProvider>,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    private val notes: StudyNoteDao,
) {
    private val json = Json

    fun isConfigured(s: AppSettings, storedKeys: Set<String>): Boolean = when (s.aiProvider) {
        AiProviderId.NONE -> false
        AiProviderId.DEMO -> true
        AiProviderId.COMPATIBLE -> s.compatibleBaseUrl.isNotBlank() && s.compatibleModel.isNotBlank() &&
            secretNameFor(s.aiProvider) in storedKeys
        else -> s.modelFor(s.aiProvider).isNotBlank() && secretNameFor(s.aiProvider) in storedKeys
    }

    /** [material] must already have been through [StudyAssistant.prepareInput] and shown to the student. */
    suspend fun ask(mode: StudyMode, material: String, note: String): StudyAnswer {
        val s = settings.current()
        val providerId = s.aiProvider
        val provider = providers[providerId]
            ?: throw ProviderException("Choose an AI provider in Settings first.", ProviderException.Code.CONFIG)
        val key = if (providerId == AiProviderId.DEMO) "" else secrets.get(secretNameFor(providerId))
            ?: throw ProviderException("Add your ${providerId.label} API key in Settings first.", ProviderException.Code.CONFIG)
        val detail = s.responseDetail.instruction
        val fullNote = listOf(note.trim(), detail).filter { it.isNotEmpty() }.joinToString(" ")
        val raw = provider.ask(
            AiRequest(
                apiKey = key,
                model = s.modelFor(providerId),
                userMessage = StudyAssistant.buildUserMessage(mode, material, fullNote),
                baseUrl = s.compatibleBaseUrl.takeIf { providerId == AiProviderId.COMPATIBLE },
            ),
        )
        return when (val parsed = StudyAssistant.parseAnswer(raw)) {
            is ParseResult.Ok -> parsed.answer
            is ParseResult.Error -> throw ProviderException(parsed.message, ProviderException.Code.INVALID_RESPONSE)
        }
    }

    suspend fun testConnection(providerId: AiProviderId) {
        val s = settings.current()
        val provider = providers[providerId]
            ?: throw ProviderException("Choose an AI provider first.", ProviderException.Code.CONFIG)
        val key = if (providerId == AiProviderId.DEMO) "" else secrets.get(secretNameFor(providerId))
            ?: throw ProviderException("Add an API key first.", ProviderException.Code.CONFIG)
        provider.test(key, s.modelFor(providerId), s.compatibleBaseUrl.takeIf { providerId == AiProviderId.COMPATIBLE })
    }

    fun savedNotes(): Flow<List<StudyNote>> = notes.all()

    suspend fun save(mode: StudyMode, answer: StudyAnswer): Long = notes.insert(
        StudyNote(
            mode = mode.name,
            title = answer.title.ifBlank { mode.label },
            content = answer.toPlainText(),
            answerJson = json.encodeToString(StudyAnswer.serializer(), answer),
        ),
    )

    fun decode(note: StudyNote): StudyAnswer? =
        runCatching { json.decodeFromString(StudyAnswer.serializer(), note.answerJson) }.getOrNull()

    suspend fun deleteNote(id: Long) = notes.delete(id)
}
