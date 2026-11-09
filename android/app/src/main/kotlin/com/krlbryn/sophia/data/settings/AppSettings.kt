package com.krlbryn.sophia.data.settings

import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.SophiaConstants
import com.krlbryn.sophia.core.SearchEngine

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

enum class AccentColor(val label: String) { MAROON("AMA maroon"), NAVY("AMA navy"), GOLD("AMA gold") }

enum class DownloadLocation(val label: String, val detail: String) {
    PUBLIC_DOWNLOADS("Downloads folder", "Download/Sophia, visible in the Files app"),
    APP_STORAGE("Sophia app storage", "Private to Sophia; removed when Sophia is uninstalled"),
}

/** The fixed bookmarks in the browser's left panel, after AMA Discipulus. */
enum class PortalBookmark(val label: String) { LMS("LMS"), AURORA("Aurora Login") }

enum class ResponseDetail(val label: String, val instruction: String) {
    BRIEF("Brief", "Keep the answer short and focused."),
    STANDARD("Standard", ""),
    DETAILED("Detailed", "Give a thorough answer with worked reasoning and examples."),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.MAROON,
    val displayName: String = "",
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val homepage: String = SophiaConstants.DEFAULT_PORTAL_URL,
    val javaScriptEnabled: Boolean = true,
    val safeBrowsing: Boolean = true,
    val historyEnabled: Boolean = true,
    val portalOnly: Boolean = false,
    val extraPortalHosts: List<String> = emptyList(),
    val textZoom: Int = 100,
    /** Needed by portals that embed content from other sites (video, documents, sign-in). */
    val thirdPartyCookies: Boolean = true,
    val downloadLocation: DownloadLocation = DownloadLocation.PUBLIC_DOWNLOADS,
    /** Origins the student allowed to open pop-ups without a tap. */
    val popupAllowedOrigins: Set<String> = emptySet(),
    val aiProvider: AiProviderId = AiProviderId.NONE,
    val openAiModel: String = AiProviderId.OPENAI.defaultModel,
    val anthropicModel: String = AiProviderId.ANTHROPIC.defaultModel,
    val compatibleModel: String = "",
    val compatibleBaseUrl: String = "",
    val responseDetail: ResponseDetail = ResponseDetail.STANDARD,
    /** Addresses for the LMS and Aurora Login bookmarks. Empty until the student sets them up. */
    val lmsUrl: String = "",
    val auroraUrl: String = "",
) {
    fun urlFor(bookmark: PortalBookmark): String = when (bookmark) {
        PortalBookmark.LMS -> lmsUrl
        PortalBookmark.AURORA -> auroraUrl
    }

    fun modelFor(provider: AiProviderId): String = when (provider) {
        AiProviderId.OPENAI -> openAiModel
        AiProviderId.ANTHROPIC -> anthropicModel
        AiProviderId.COMPATIBLE -> compatibleModel
        AiProviderId.DEMO -> "demo"
        AiProviderId.NONE -> ""
    }
}
