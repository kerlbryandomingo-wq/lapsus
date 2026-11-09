package com.krlbryn.sophia.browser

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import com.krlbryn.sophia.core.SecurityLevel

sealed interface PageError {
    val url: String

    data class Network(override val url: String, val description: String) : PageError
    data class Certificate(override val url: String, val problem: String) : PageError
    data class Crashed(override val url: String) : PageError
}

data class TabUi(
    val id: Long,
    /** Empty for a new tab that shows the Sophia start page. */
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val security: SecurityLevel = SecurityLevel.NONE,
    val error: PageError? = null,
) {
    val isStartPage: Boolean get() = url.isEmpty()
}

/** A message shown above the page, optionally with one action the student can take. */
data class BrowserNotice(val message: String, val action: NoticeAction? = null, val id: Long = System.nanoTime())

sealed interface NoticeAction {
    val label: String

    data class AllowHttp(val origin: String, val url: String) : NoticeAction { override val label = "Open over HTTP" }
    data class AllowPopups(val origin: String) : NoticeAction { override val label = "Always allow" }
    data class OpenExternal(val intent: Intent) : NoticeAction { override val label = "Open app" }
}

/** One-shot requests from the browser that need the Activity. */
sealed interface BrowserEvent {
    data class LaunchExternal(val intent: Intent, val fallbackUrl: String?) : BrowserEvent
    data class ChooseFiles(val intent: Intent, val callback: ValueCallback<Array<Uri>>) : BrowserEvent
    data class LinkMenu(val url: String, val isImage: Boolean) : BrowserEvent
    data class Message(val text: String) : BrowserEvent
    data object OpenAssistant : BrowserEvent
    data object ShowBrowser : BrowserEvent
}

/** Gesture-revealed control panels (swipe left: navigation; swipe right: bookmarks). */
enum class ControlsPanel { NONE, NAVIGATION, BOOKMARKS }
