package com.krlbryn.sophia.core

import java.util.Locale
import kotlin.math.abs

object Formatting {
    fun greeting(hourOfDay: Int): String = when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    fun relativeTime(thenMillis: Long, nowMillis: Long): String {
        val diff = abs(nowMillis - thenMillis) / 1000
        return when {
            diff < 45 -> "just now"
            diff < 90 -> "1 minute ago"
            diff < 3600 -> "${diff / 60} minutes ago"
            diff < 5400 -> "1 hour ago"
            diff < 86_400 -> "${diff / 3600} hours ago"
            diff < 172_800 -> "yesterday"
            else -> "${diff / 86_400} days ago"
        }
    }

    fun bytes(size: Long): String {
        if (size < 0) return "Unknown size"
        if (size < 1024) return "$size B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = size.toDouble() / 1024
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return String.format(Locale.US, if (value < 10) "%.1f %s" else "%.0f %s", value, units[i])
    }
}

object FileNames {
    private val UNSAFE = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]")

    /** Makes a server-supplied file name safe to store: no paths, no control characters, bounded length. */
    fun sanitize(raw: String?, fallback: String = "download"): String {
        var name = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        name = name.replace(UNSAFE, "_").trim().trim('.')
        if (name.isEmpty()) name = fallback
        if (name.length > 120) {
            val ext = extensionOf(name)
            name = name.take(120 - ext.length - 1).trimEnd('.') + if (ext.isNotEmpty()) ".$ext" else ""
        }
        return name
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }?.lowercase() ?: ""

    /** Short human label for a file, e.g. "PDF document". */
    fun typeLabel(fileName: String, mimeType: String?): String {
        val ext = extensionOf(fileName)
        return when {
            ext == "pdf" || mimeType == "application/pdf" -> "PDF document"
            ext in setOf("doc", "docx", "odt", "rtf") -> "Word document"
            ext in setOf("xls", "xlsx", "ods", "csv") -> "Spreadsheet"
            ext in setOf("ppt", "pptx", "odp") -> "Presentation"
            ext in setOf("zip", "rar", "7z", "tar", "gz") -> "Archive"
            ext == "apk" -> "Android app package"
            mimeType?.startsWith("image/") == true || ext in setOf("png", "jpg", "jpeg", "gif", "webp") -> "Image"
            mimeType?.startsWith("video/") == true || ext in setOf("mp4", "mkv", "webm", "mov") -> "Video"
            mimeType?.startsWith("audio/") == true || ext in setOf("mp3", "m4a", "wav", "ogg") -> "Audio"
            mimeType?.startsWith("text/") == true || ext == "txt" -> "Text file"
            ext.isNotEmpty() -> "${ext.uppercase()} file"
            else -> "File"
        }
    }
}

/** Window size classes used to choose the phone or tablet interface. */
enum class LayoutClass { COMPACT, MEDIUM, EXPANDED }

object AdaptiveLayout {
    /**
     * Width breakpoints follow Material 3 window size classes (600dp / 840dp). A phone in landscape
     * (short height) gets the medium layout, with a navigation rail instead of a bottom bar.
     */
    fun classify(widthDp: Float, heightDp: Float): LayoutClass = when {
        widthDp >= 840f && heightDp >= 480f -> LayoutClass.EXPANDED
        widthDp >= 600f -> LayoutClass.MEDIUM
        heightDp < 480f && widthDp > heightDp -> LayoutClass.MEDIUM
        else -> LayoutClass.COMPACT
    }
}
