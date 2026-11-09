package com.krlbryn.sophia.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.ProviderException
import com.krlbryn.sophia.core.SearchEngine
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.data.repo.StudyRepository
import com.krlbryn.sophia.data.repo.secretNameFor
import com.krlbryn.sophia.data.secure.SecretStore
import com.krlbryn.sophia.data.settings.AccentColor
import com.krlbryn.sophia.data.settings.AppSettings
import com.krlbryn.sophia.data.settings.DownloadLocation
import com.krlbryn.sophia.data.settings.PortalBookmark
import com.krlbryn.sophia.data.settings.ResponseDetail
import com.krlbryn.sophia.data.settings.SettingsRepository
import com.krlbryn.sophia.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data object Ok : ConnectionTest
    data class Failed(val message: String) : ConnectionTest
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val secrets: SecretStore,
    private val study: StudyRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val storedKeys: StateFlow<Set<String>> = secrets.storedNames

    private val _test = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val connectionTest: StateFlow<ConnectionTest> = _test.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    fun setTheme(v: ThemeMode) = run { repo.setThemeMode(v) }
    fun setAccent(v: AccentColor) = run { repo.setAccent(v) }
    fun setDisplayName(v: String) = run { repo.setDisplayName(v) }
    fun setSearchEngine(v: SearchEngine) = run { repo.setSearchEngine(v) }
    fun setJavaScript(v: Boolean) = run { repo.setJavaScriptEnabled(v) }
    fun setSafeBrowsing(v: Boolean) = run { repo.setSafeBrowsing(v) }
    fun setPortalOnly(v: Boolean) = run { repo.setPortalOnly(v) }
    fun setTextZoom(v: Int) = run { repo.setTextZoom(v) }
    fun setThirdPartyCookies(v: Boolean) = run { repo.setThirdPartyCookies(v) }
    fun setDownloadLocation(v: DownloadLocation) = run { repo.setDownloadLocation(v) }
    fun revokePopups(origin: String) = run { repo.revokePopups(origin) }
    fun removeExtraHost(host: String) = run { repo.removeExtraPortalHost(host) }
    fun setResponseDetail(v: ResponseDetail) = run { repo.setResponseDetail(v) }

    fun setHistoryEnabled(v: Boolean) = run { repo.setHistoryEnabled(v) }

    fun clearHistory() = run {
        library.clearHistory()
        say("History cleared")
    }

    fun setHomepage(raw: String) = run {
        if (repo.setHomepage(raw)) say("Homepage saved") else say("Use an https:// address for the homepage.")
    }

    fun setPortalBookmark(bookmark: PortalBookmark, raw: String) = run {
        if (repo.setPortalBookmark(bookmark, raw)) {
            say(if (raw.isBlank()) "${bookmark.label} bookmark cleared" else "${bookmark.label} bookmark saved")
        } else {
            say("Use a secure https:// address.")
        }
    }

    fun addExtraHost(raw: String) = run {
        if (!repo.addExtraPortalHost(raw)) say("That doesn't look like a website address.")
    }

    fun setProvider(v: AiProviderId) = run {
        repo.setAiProvider(v)
        _test.value = ConnectionTest.Idle
    }

    fun setModel(provider: AiProviderId, model: String) = run {
        if (!repo.setModel(provider, model)) say("That model name isn't valid.")
        _test.value = ConnectionTest.Idle
    }

    fun setCompatibleBaseUrl(raw: String) = run {
        if (!repo.setCompatibleBaseUrl(raw)) say("Use an https:// address for the service.") else say("Address saved")
        _test.value = ConnectionTest.Idle
    }

    /** Stores the key encrypted with the Android Keystore. The plain text is never kept in state. */
    fun saveApiKey(provider: AiProviderId, key: String) = run {
        val trimmed = key.trim()
        if (trimmed.isEmpty() || trimmed.length > 400 || trimmed.any { it.isWhitespace() }) {
            say("That key doesn't look right.")
            return@run
        }
        secrets.put(secretNameFor(provider), trimmed)
        _test.value = ConnectionTest.Idle
        say("Key saved securely")
    }

    fun removeApiKey(provider: AiProviderId) = run {
        secrets.remove(secretNameFor(provider))
        _test.value = ConnectionTest.Idle
        say("Key removed")
    }

    fun testConnection() = run {
        _test.value = ConnectionTest.Running
        _test.value = try {
            study.testConnection(settings.value.aiProvider)
            ConnectionTest.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderException) {
            ConnectionTest.Failed(e.message ?: "Connection failed.")
        } catch (e: Exception) {
            ConnectionTest.Failed("Connection failed.")
        }
    }
}
