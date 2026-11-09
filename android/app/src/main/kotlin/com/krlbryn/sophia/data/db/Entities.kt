package com.krlbryn.sophia.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmark_folders")
data class BookmarkFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookmarkFolder::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("url"), Index("folder_id")],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history", indices = [Index("url"), Index("visited_at")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    @ColumnInfo(name = "visited_at") val visitedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "downloads", indices = [Index(value = ["system_id"], unique = true)])
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Id from Android's DownloadManager. */
    @ColumnInfo(name = "system_id") val systemId: Long,
    val url: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    /** Where the file was saved: a DownloadLocation name. */
    val location: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** Set when the student cancelled; DownloadManager then forgets the download. */
    val cancelled: Boolean = false,
)

@Entity(tableName = "shortcuts")
data class PortalShortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int = 0,
)

@Entity(tableName = "study_notes", indices = [Index("created_at")])
data class StudyNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val title: String,
    /** Plain-text rendering of the answer. */
    val content: String,
    /** The validated answer as JSON, so it can be shown again with full formatting. */
    @ColumnInfo(name = "answer_json") val answerJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
