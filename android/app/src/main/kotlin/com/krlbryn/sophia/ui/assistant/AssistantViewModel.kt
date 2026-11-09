package com.krlbryn.sophia.ui.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.PreparedInput
import com.krlbryn.sophia.core.ProviderException
import com.krlbryn.sophia.core.StudyAnswer
import com.krlbryn.sophia.core.StudyAssistant
import com.krlbryn.sophia.core.StudyMode
import com.krlbryn.sophia.data.db.StudyNote
import com.krlbryn.sophia.data.repo.AssistantInbox
import com.krlbryn.sophia.data.repo.StudyRepository
import com.krlbryn.sophia.data.secure.SecretStore
import com.krlbryn.sophia.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AssistantStage { EDIT, REVIEW, LOADING, RESULT }

data class AssistantUi(
    val mode: StudyMode = StudyMode.EXPLAIN,
    val input: String = "",
    val note: String = "",
    val source: String? = null,
    val stage: AssistantStage = AssistantStage.EDIT,
    val prepared: PreparedInput? = null,
    val answer: StudyAnswer? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

data class ProviderStatus(val configured: Boolean, val provider: AiProviderId)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val study: StudyRepository,
    private val inbox: AssistantInbox,
    private val savedState: SavedStateHandle,
    settings: SettingsRepository,
    secrets: SecretStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        AssistantUi(
            input = savedState.get<String>(KEY_INPUT).orEmpty(),
            mode = savedState.get<String>(KEY_MODE)?.let { m -> StudyMode.entries.firstOrNull { it.name == m } } ?: StudyMode.EXPLAIN,
        ),
    )
    val ui: StateFlow<AssistantUi> = _ui.asStateFlow()

    val status: StateFlow<ProviderStatus> = combine(settings.settings, secrets.storedNames) { s, keys ->
        ProviderStatus(study.isConfigured(s, keys), s.aiProvider)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderStatus(false, AiProviderId.NONE))

    val notes: StateFlow<List<StudyNote>> = study.savedNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var job: Job? = null

    init {
        viewModelScope.launch {
            inbox.pending.collect { item ->
                if (item != null) {
                    inbox.take()
                    setInput(item.text)
                    _ui.update { it.copy(source = item.source, stage = AssistantStage.EDIT, answer = null, error = null) }
                }
            }
        }
    }

    fun setMode(mode: StudyMode) {
        savedState[KEY_MODE] = mode.name
        _ui.update { it.copy(mode = mode) }
    }

    fun setInput(text: String) {
        val capped = text.take(StudyAssistantLimits.MAX_EDIT_CHARS)
        savedState[KEY_INPUT] = capped
        _ui.update { it.copy(input = capped) }
    }

    fun setNote(text: String) = _ui.update { it.copy(note = text.take(500)) }

    /** Step 1: clean and redact, then show the student exactly what would be sent. Nothing is sent yet. */
    fun review() {
        val prepared = StudyAssistant.prepareInput(_ui.value.input)
        if (prepared.text.isBlank()) {
            _ui.update { it.copy(error = "Paste or type some study material first.") }
            return
        }
        _ui.update { it.copy(prepared = prepared, stage = AssistantStage.REVIEW, error = null) }
    }

    fun backToEdit() = _ui.update { it.copy(stage = AssistantStage.EDIT, error = null) }

    /** Step 2: send the reviewed text to the student's chosen provider. */
    fun send() {
        val current = _ui.value
        val prepared = current.prepared ?: return
        job?.cancel()
        _ui.update { it.copy(stage = AssistantStage.LOADING, error = null, saved = false) }
        job = viewModelScope.launch {
            try {
                val answer = study.ask(current.mode, prepared.text, current.note)
                _ui.update { it.copy(stage = AssistantStage.RESULT, answer = answer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException) {
                _ui.update { it.copy(stage = AssistantStage.REVIEW, error = e.message) }
            } catch (e: Exception) {
                _ui.update { it.copy(stage = AssistantStage.REVIEW, error = "The request failed. Try again.") }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _ui.update { it.copy(stage = AssistantStage.REVIEW) }
    }

    fun save() {
        val s = _ui.value
        val answer = s.answer ?: return
        viewModelScope.launch {
            study.save(s.mode, answer)
            _ui.update { it.copy(saved = true) }
        }
    }

    fun startOver() {
        setInput("")
        _ui.update { AssistantUi(mode = it.mode) }
    }

    fun openNote(note: StudyNote) {
        val answer = study.decode(note) ?: return
        val mode = StudyMode.entries.firstOrNull { it.name == note.mode } ?: StudyMode.EXPLAIN
        _ui.update { it.copy(mode = mode, answer = answer, stage = AssistantStage.RESULT, saved = true, error = null) }
    }

    fun deleteNote(note: StudyNote) = viewModelScope.launch { study.deleteNote(note.id) }

    private companion object {
        const val KEY_INPUT = "assistant_input"
        const val KEY_MODE = "assistant_mode"
    }
}

object StudyAssistantLimits {
    /** Text the editor accepts; anything beyond the provider limit is trimmed at review time and shown. */
    const val MAX_EDIT_CHARS = 30_000
}
