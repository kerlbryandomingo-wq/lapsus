package com.krlbryn.sophia.data.repo

import com.krlbryn.sophia.core.SophiaConstants
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.data.db.Bookmark
import com.krlbryn.sophia.data.db.BookmarkDao
import com.krlbryn.sophia.data.db.BookmarkFolder
import com.krlbryn.sophia.data.db.HistoryDao
import com.krlbryn.sophia.data.db.HistoryEntry
import com.krlbryn.sophia.data.db.PortalShortcut
import com.krlbryn.sophia.data.db.ShortcutDao
import com.krlbryn.sophia.data.db.likePattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/** Bookmarks, folders, browsing history and home-screen portal shortcuts. */
@Singleton
class LibraryRepository @Inject constructor(
    private val bookmarks: BookmarkDao,
    private val history: HistoryDao,
    private val shortcuts: ShortcutDao,
) {
    fun searchBookmarks(query: String): Flow<List<Bookmark>> = bookmarks.search(likePattern(query))
    fun recentBookmarks(limit: Int): Flow<List<Bookmark>> = bookmarks.recent(limit)
    fun folders(): Flow<List<BookmarkFolder>> = bookmarks.folders()
    fun isBookmarked(url: String): Flow<Boolean> = if (isBuiltIn(url)) flowOf(true) else bookmarks.isBookmarked(url)

    /** Adds or removes a bookmark for the page. Returns true when the page is now bookmarked. */
    suspend fun toggleBookmark(url: String, title: String): Boolean {
        if (bookmarks.findByUrl(url) != null) {
            bookmarks.deleteByUrl(url)
            return false
        }
        bookmarks.insert(Bookmark(title = cleanTitle(title, url), url = url))
        return true
    }

    suspend fun addBookmark(title: String, rawUrl: String, folderId: Long?): Boolean {
        val url = UrlPolicy.validateHomepage(rawUrl) ?: return false
        if (isBuiltIn(url) || bookmarks.findByUrl(url) != null) return true
        bookmarks.insert(Bookmark(title = cleanTitle(title, url), url = url, folderId = folderId))
        return true
    }

    suspend fun updateBookmark(bookmark: Bookmark, title: String, rawUrl: String, folderId: Long?): Boolean {
        val url = UrlPolicy.validateHomepage(rawUrl) ?: return false
        if (isBuiltIn(url)) {
            bookmarks.delete(bookmark)
            return true
        }
        bookmarks.update(bookmark.copy(title = cleanTitle(title, url), url = url, folderId = folderId))
        return true
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarks.delete(bookmark)

    suspend fun addFolder(name: String): Long? {
        val clean = name.trim().take(60)
        if (clean.isEmpty()) return null
        return bookmarks.insertFolder(BookmarkFolder(name = clean))
    }

    suspend fun renameFolder(folder: BookmarkFolder, name: String) {
        val clean = name.trim().take(60)
        if (clean.isNotEmpty()) bookmarks.updateFolder(folder.copy(name = clean))
    }

    suspend fun deleteFolder(folder: BookmarkFolder) = bookmarks.deleteFolder(folder)

    fun searchHistory(query: String, limit: Int = 500): Flow<List<HistoryEntry>> = history.search(likePattern(query), limit)

    fun lastPortalVisit(): Flow<HistoryEntry?> = history.lastVisitMatching(likePattern("amasystem.net"))

    /** Records a visit. Repeated loads of the same page within 30 minutes update one entry. */
    suspend fun recordVisit(url: String, title: String, now: Long = System.currentTimeMillis()) {
        if (UrlPolicy.schemeOf(url) !in setOf("http", "https")) return
        val last = history.latest()
        if (last != null && last.url == url && now - last.visitedAt < 30 * 60_000) {
            history.touch(last.id, cleanTitle(title.ifBlank { last.title }, url), now)
        } else {
            history.insert(HistoryEntry(url = url, title = cleanTitle(title, url), visitedAt = now))
            history.trim(SophiaConstants.MAX_HISTORY_ENTRIES)
        }
    }

    suspend fun updateTitle(url: String, title: String) {
        if (title.isNotBlank()) history.updateLatestTitle(url, title.take(300))
    }

    suspend fun deleteHistory(id: Long) = history.delete(id)
    suspend fun clearHistory() = history.clear()

    fun shortcuts(): Flow<List<PortalShortcut>> = shortcuts.all()

    suspend fun addShortcut(title: String, rawUrl: String): Boolean {
        val url = UrlPolicy.validateHomepage(rawUrl) ?: return false
        shortcuts.insert(PortalShortcut(title = cleanTitle(title, url), url = url, position = shortcuts.nextPosition()))
        return true
    }

    suspend fun updateShortcut(shortcut: PortalShortcut, title: String, rawUrl: String): Boolean {
        val url = UrlPolicy.validateHomepage(rawUrl) ?: return false
        shortcuts.update(shortcut.copy(title = cleanTitle(title, url), url = url))
        return true
    }

    suspend fun deleteShortcut(shortcut: PortalShortcut) = shortcuts.delete(shortcut)

    companion object {
        /**
         * Sophia's one built-in bookmark. It is not stored in the database, so it cannot be edited,
         * duplicated or deleted, and clearing history or bookmarks never removes it.
         */
        const val BUILT_IN_TITLE = "AMA Discipulus"
        const val BUILT_IN_URL = SophiaConstants.DEFAULT_PORTAL_URL

        fun isBuiltIn(url: String): Boolean = UrlPolicy.displayUrl(url) == UrlPolicy.displayUrl(BUILT_IN_URL)
    }

    private fun cleanTitle(title: String, url: String): String =
        title.trim().take(300).ifEmpty { UrlPolicy.hostOf(url).ifEmpty { url } }
}
