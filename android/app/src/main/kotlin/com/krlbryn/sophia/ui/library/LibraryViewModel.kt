package com.krlbryn.sophia.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.data.db.Bookmark
import com.krlbryn.sophia.data.db.BookmarkFolder
import com.krlbryn.sophia.data.db.HistoryEntry
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which bookmarks to show: everything, those outside any folder, or one folder. */
sealed interface FolderFilter {
    data object All : FolderFilter
    data object Unfiled : FolderFilter
    data class Folder(val id: Long) : FolderFilter
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow<FolderFilter>(FolderFilter.All)
    val filter: StateFlow<FolderFilter> = _filter.asStateFlow()

    private val debounced = _query.debounce(150)

    val folders: StateFlow<List<BookmarkFolder>> =
        library.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = combine(debounced.flatMapLatest { library.searchBookmarks(it) }, _filter) { list, f ->
        when (f) {
            FolderFilter.All -> list
            FolderFilter.Unfiled -> list.filter { it.folderId == null }
            is FolderFilter.Folder -> list.filter { it.folderId == f.id }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryEntry>> = debounced.flatMapLatest { library.searchHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyEnabled: StateFlow<Boolean> = settings.settings.map { it.historyEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setQuery(q: String) {
        _query.value = q.take(200)
    }

    fun setFilter(f: FolderFilter) {
        _filter.value = f
    }

    fun addBookmark(title: String, url: String, folderId: Long?): Boolean {
        if (UrlPolicy.validateHomepage(url) == null) return false
        viewModelScope.launch { library.addBookmark(title, url, folderId) }
        return true
    }

    fun updateBookmark(b: Bookmark, title: String, url: String, folderId: Long?): Boolean {
        if (UrlPolicy.validateHomepage(url) == null) return false
        viewModelScope.launch { library.updateBookmark(b, title, url, folderId) }
        return true
    }

    fun deleteBookmark(b: Bookmark) = viewModelScope.launch { library.deleteBookmark(b) }

    fun addFolder(name: String) = viewModelScope.launch { library.addFolder(name) }
    fun renameFolder(f: BookmarkFolder, name: String) = viewModelScope.launch { library.renameFolder(f, name) }

    fun deleteFolder(f: BookmarkFolder) = viewModelScope.launch {
        library.deleteFolder(f)
        if (_filter.value == FolderFilter.Folder(f.id)) _filter.value = FolderFilter.All
    }

    fun deleteHistory(e: HistoryEntry) = viewModelScope.launch { library.deleteHistory(e.id) }
    fun clearHistory() = viewModelScope.launch { library.clearHistory() }
}
