package com.krlbryn.sophia.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krlbryn.sophia.data.db.Bookmark
import com.krlbryn.sophia.data.db.HistoryEntry
import com.krlbryn.sophia.data.db.PortalShortcut
import com.krlbryn.sophia.data.repo.DownloadItem
import com.krlbryn.sophia.data.repo.DownloadsRepository
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.data.repo.NetworkMonitor
import com.krlbryn.sophia.data.repo.StudyRepository
import com.krlbryn.sophia.data.secure.SecretStore
import com.krlbryn.sophia.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.krlbryn.sophia.core.UrlPolicy
import javax.inject.Inject

data class HomeState(
    val displayName: String = "",
    val online: Boolean = true,
    val historyEnabled: Boolean = true,
    val recent: List<HistoryEntry> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val shortcuts: List<PortalShortcut> = emptyList(),
    val downloads: List<DownloadItem> = emptyList(),
    val lastPortalVisit: Long? = null,
    val assistantConfigured: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    settings: SettingsRepository,
    downloads: DownloadsRepository,
    network: NetworkMonitor,
    study: StudyRepository,
    secrets: SecretStore,
) : ViewModel() {

    private val recent = settings.settings.map { it.historyEnabled }.flatMapLatest { enabled ->
        if (enabled) library.searchHistory("", 6) else flowOf(emptyList())
    }

    private val base = combine(
        settings.settings,
        network.isOnline,
        recent,
        library.recentBookmarks(6),
        library.shortcuts(),
    ) { s, online, recentPages, bookmarks, shortcuts ->
        HomeState(
            displayName = s.displayName,
            online = online,
            historyEnabled = s.historyEnabled,
            recent = recentPages,
            bookmarks = bookmarks,
            shortcuts = shortcuts,
        )
    }

    val state: StateFlow<HomeState> = combine(
        base,
        downloads.observe().map { it.take(3) },
        library.lastPortalVisit(),
        settings.settings,
        secrets.storedNames,
    ) { home, recentDownloads, portal, s, keys ->
        home.copy(
            downloads = recentDownloads,
            lastPortalVisit = portal?.visitedAt,
            assistantConfigured = study.isConfigured(s, keys),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun addShortcut(title: String, url: String): Boolean {
        if (UrlPolicy.validateHomepage(url) == null) return false
        viewModelScope.launch { library.addShortcut(title, url) }
        return true
    }

    fun updateShortcut(s: PortalShortcut, title: String, url: String): Boolean {
        if (UrlPolicy.validateHomepage(url) == null) return false
        viewModelScope.launch { library.updateShortcut(s, title, url) }
        return true
    }

    fun deleteShortcut(s: PortalShortcut) {
        viewModelScope.launch { library.deleteShortcut(s) }
    }
}
