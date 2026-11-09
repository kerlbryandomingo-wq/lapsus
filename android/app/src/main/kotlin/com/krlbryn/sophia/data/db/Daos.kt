package com.krlbryn.sophia.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Escapes LIKE wildcards in user search text; queries use ESCAPE '\'. */
fun likePattern(query: String): String =
    "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

@Dao
interface BookmarkDao {
    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE :pattern ESCAPE '\\' OR url LIKE :pattern ESCAPE '\\' " +
            "ORDER BY created_at DESC",
    )
    fun search(pattern: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): Bookmark?

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM bookmark_folders ORDER BY name COLLATE NOCASE")
    fun folders(): Flow<List<BookmarkFolder>>

    @Insert
    suspend fun insertFolder(folder: BookmarkFolder): Long

    @Update
    suspend fun updateFolder(folder: BookmarkFolder)

    @Delete
    suspend fun deleteFolder(folder: BookmarkFolder)
}

@Dao
interface HistoryDao {
    @Query(
        "SELECT * FROM history WHERE title LIKE :pattern ESCAPE '\\' OR url LIKE :pattern ESCAPE '\\' " +
            "ORDER BY visited_at DESC LIMIT :limit",
    )
    fun search(pattern: String, limit: Int): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY visited_at DESC LIMIT 1")
    suspend fun latest(): HistoryEntry?

    @Query("SELECT * FROM history WHERE url LIKE :pattern ESCAPE '\\' ORDER BY visited_at DESC LIMIT 1")
    fun lastVisitMatching(pattern: String): Flow<HistoryEntry?>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("UPDATE history SET title = :title, visited_at = :visitedAt WHERE id = :id")
    suspend fun touch(id: Long, title: String, visitedAt: Long)

    @Query("UPDATE history SET title = :title WHERE id = (SELECT id FROM history WHERE url = :url ORDER BY visited_at DESC LIMIT 1)")
    suspend fun updateLatestTitle(url: String, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visited_at DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY created_at DESC")
    fun all(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadRecord?

    @Insert
    suspend fun insert(record: DownloadRecord): Long

    @Update
    suspend fun update(record: DownloadRecord)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY position, id")
    fun all(): Flow<List<PortalShortcut>>

    @Insert
    suspend fun insert(shortcut: PortalShortcut): Long

    @Update
    suspend fun update(shortcut: PortalShortcut)

    @Delete
    suspend fun delete(shortcut: PortalShortcut)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shortcuts")
    suspend fun nextPosition(): Int
}

@Dao
interface StudyNoteDao {
    @Query("SELECT * FROM study_notes ORDER BY created_at DESC")
    fun all(): Flow<List<StudyNote>>

    @Insert
    suspend fun insert(note: StudyNote): Long

    @Query("DELETE FROM study_notes WHERE id = :id")
    suspend fun delete(id: Long)
}
