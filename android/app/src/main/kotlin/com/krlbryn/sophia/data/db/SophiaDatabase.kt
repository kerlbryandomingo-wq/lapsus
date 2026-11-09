package com.krlbryn.sophia.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookmarkFolder::class, Bookmark::class, HistoryEntry::class, DownloadRecord::class, PortalShortcut::class, StudyNote::class],
    version = 1,
    exportSchema = true,
)
abstract class SophiaDatabase : RoomDatabase() {
    abstract fun bookmarks(): BookmarkDao
    abstract fun history(): HistoryDao
    abstract fun downloads(): DownloadDao
    abstract fun shortcuts(): ShortcutDao
    abstract fun notes(): StudyNoteDao
}
