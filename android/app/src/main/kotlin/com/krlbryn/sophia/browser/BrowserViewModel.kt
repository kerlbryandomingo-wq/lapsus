package com.krlbryn.sophia.browser

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krlbryn.sophia.core.BlockReason
import com.krlbryn.sophia.core.SophiaConstants
import com.krlbryn.sophia.core.NavigationContext
import com.krlbryn.sophia.core.NavigationDecision
import com.krlbryn.sophia.core.SwipeDirection
import com.krlbryn.sophia.core.UrlPolicy
import com.krlbryn.sophia.data.repo.AssistantInbox
import com.krlbryn.sophia.data.repo.DownloadsRepository
import com.krlbryn.sophia.data.repo.EnqueueResult
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.data.settings.AppSettings
import com.krlbryn.sophia.data.settings.DownloadLocation
import com.krlbryn.sophia.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/**
 * The Sophia browser engine: tabs, their WebViews and the security policy applied to them.
 *
 * Scoped to the Activity, so pages survive moving between Home, Bookmarks and the browser and survive
 * configuration changes. WebViews are created with a [MutableContextWrapper] whose base is swapped
 * between the Activity (while shown) and the Application (when the Activity goes away), so they never
 * leak an Activity. Tab URLs are kept in [SavedStateHandle] so tabs come back after process death.
 *
 * No JavaScript interface is ever added to a WebView, nothing is injected into pages, and the user agent,
 * focus, visibility and window state that pages see are WebView's own.
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val app: Application,
    private val savedState: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val library: LibraryRepository,
    private val downloads: DownloadsRepository,
    private val inbox: AssistantInbox,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _tabs = MutableStateFlow<List<TabUi>>(emptyList())
    val tabs: StateFlow<List<TabUi>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<Long?>(null)
    val activeTabId: StateFlow<Long?> = _activeTabId.asStateFlow()

    private val _notice = MutableStateFlow<BrowserNotice?>(null)
    val notice: StateFlow<BrowserNotice?> = _notice.asStateFlow()

    private val _toolbarVisible = MutableStateFlow(true)
    val toolbarVisible: StateFlow<Boolean> = _toolbarVisible.asStateFlow()

    /** A page's element in genuine fullscreen (for example a lecture video), or null. */
    private val _fullscreenView = MutableStateFlow<View?>(null)
    val fullscreenView: StateFlow<View?> = _fullscreenView.asStateFlow()
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val _controlsPanel = MutableStateFlow(ControlsPanel.NONE)
    val controlsPanel: StateFlow<ControlsPanel> = _controlsPanel.asStateFlow()

    /**
     * Handles a recognised horizontal swipe. Swiping left reveals Back/Forward/Reload from the right edge;
     * swiping right reveals the bookmarks panel from the left edge. Swiping back toward the centre closes it.
     */
    fun onSwipe(direction: SwipeDirection) {
        _controlsPanel.value = when (direction) {
            SwipeDirection.LEFT -> if (_controlsPanel.value == ControlsPanel.BOOKMARKS) ControlsPanel.NONE else ControlsPanel.NAVIGATION
            SwipeDirection.RIGHT -> if (_controlsPanel.value == ControlsPanel.NAVIGATION) ControlsPanel.NONE else ControlsPanel.BOOKMARKS
        }
    }

    /** Accessible alternative to the swipe gestures (menu items, TalkBack actions, keyboard). */
    fun showControls(panel: ControlsPanel) {
        _controlsPanel.value = panel
    }

    fun hideControls() {
        _controlsPanel.value = ControlsPanel.NONE
    }

    /** Incremented to move keyboard focus to the address bar (Ctrl+L, "Search" buttons). */
    private val _focusAddressRequests = MutableStateFlow(0)
    val focusAddressRequests: StateFlow<Int> = _focusAddressRequests.asStateFlow()

    fun requestAddressFocus() {
        _toolbarVisible.value = true
        _focusAddressRequests.update { it + 1 }
    }

    private val events = Channel<BrowserEvent>(Channel.BUFFERED)
    val eventFlow: Flow<BrowserEvent> = events.receiveAsFlow()

    private val webViews = mutableMapOf<Long, WebView>()
    private val pendingUrls = mutableMapOf<Long, String>()
    private var nextId = 1L

    /** Origins the student chose to open over plain HTTP in this session only. */
    private val allowedHttpOrigins = mutableSetOf<String>()

    init {
        val saved = savedState.get<ArrayList<String>>(KEY_URLS).orEmpty()
        val active = savedState.get<Int>(KEY_ACTIVE) ?: 0
        if (saved.isNotEmpty()) {
            val restored = saved.map { url -> TabUi(id = nextId++, url = url, title = UrlPolicy.hostOf(url)) }
            restored.forEach { if (it.url.isNotEmpty()) pendingUrls[it.id] = it.url }
            _tabs.value = restored
            _activeTabId.value = restored.getOrNull(active)?.id ?: restored.first().id
        }
        viewModelScope.launch {
            settings.collect { s -> webViews.values.forEach { applySettings(it, s) } }
        }
    }

    // region Tabs

    val activeTab: TabUi? get() = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun hasWebView(tabId: Long): Boolean = webViews.containsKey(tabId) || pendingUrls.containsKey(tabId)

    private fun updateTab(id: Long, block: (TabUi) -> TabUi) {
        _tabs.update { list -> list.map { if (it.id == id) block(it) else it } }
        persist()
    }

    private fun persist() {
        val list = _tabs.value
        savedState[KEY_URLS] = ArrayList(list.map { it.url })
        savedState[KEY_ACTIVE] = list.indexOfFirst { it.id == _activeTabId.value }.coerceAtLeast(0)
    }

    private fun addTab(url: String, select: Boolean = true): Long? {
        if (_tabs.value.size >= SophiaConstants.MAX_TABS) {
            showNotice("You have ${SophiaConstants.MAX_TABS} tabs open. Close one to open another.")
            return null
        }
        val id = nextId++
        _tabs.update { it + TabUi(id = id, url = url, title = UrlPolicy.hostOf(url), security = UrlPolicy.securityLevelFor(url)) }
        if (url.isNotEmpty()) pendingUrls[id] = url
        if (select) _activeTabId.value = id
        persist()
        return id
    }

    fun newTab() {
        addTab("")
        _toolbarVisible.value = true
    }

    fun selectTab(id: Long) {
        if (_tabs.value.any { it.id == id }) {
            _activeTabId.value = id
            _toolbarVisible.value = true
            persist()
        }
    }

    fun closeTab(id: Long) {
        val list = _tabs.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        webViews.remove(id)?.let(::destroyWebView)
        pendingUrls.remove(id)
        val remaining = list.filterNot { it.id == id }
        _tabs.value = remaining
        if (_activeTabId.value == id) {
            _activeTabId.value = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id
        }
        persist()
    }

    // endregion

    // region Navigation

    private fun navContext(s: AppSettings = settings.value) =
        NavigationContext(s.portalOnly, s.extraPortalHosts, allowedHttpOrigins.toSet())

    /** Opens what the student typed or tapped. [newTab] forces a new tab; otherwise the current tab is reused. */
    fun open(input: String, newTab: Boolean = false) {
        val url = UrlPolicy.normalizeAddressInput(input, settings.value.searchEngine) ?: return
        when (val decision = UrlPolicy.evaluateNavigation(url, navContext())) {
            NavigationDecision.Allow -> loadAllowed(url, newTab)
            is NavigationDecision.External -> openExternal(decision.url, userInitiated = true)
            is NavigationDecision.Block -> explainBlock(url, decision.reason)
        }
    }

    fun openPortal(newTab: Boolean = false) = open(SophiaConstants.DEFAULT_PORTAL_URL, newTab)

    /** Brings the browser screen forward (keyboard shortcuts). */
    fun showBrowser() {
        events.trySend(BrowserEvent.ShowBrowser)
    }

    /** A portal link opened from another app. Only https portal links reach this (see the manifest filter). */
    fun openFromIntent(url: String) {
        if (UrlPolicy.schemeOf(url) != "https") return
        open(url, newTab = true)
        events.trySend(BrowserEvent.ShowBrowser)
    }

    /** Text shared to Sophia from another app; it waits in the assistant for the student to review. */
    fun receiveSharedText(text: String, source: String) {
        if (text.isBlank()) return
        inbox.send(text.take(SophiaConstants.MAX_AI_INPUT_CHARS * 2), source)
        events.trySend(BrowserEvent.OpenAssistant)
    }

    fun goHome() = open(settings.value.homepage)

    private fun loadAllowed(url: String, newTab: Boolean) {
        val current = activeTab
        if (newTab || current == null) {
            addTab(url)
        } else {
            val wv = webViews[current.id]
            updateTab(current.id) { it.copy(url = url, error = null, security = UrlPolicy.securityLevelFor(url)) }
            if (wv != null) wv.loadUrl(url) else pendingUrls[current.id] = url
        }
        _toolbarVisible.value = true
    }

    fun back() {
        if (_fullscreenView.value != null) {
            exitFullscreen()
            return
        }
        val tab = activeTab ?: return
        val wv = webViews[tab.id]
        if (wv != null && wv.canGoBack()) wv.goBack()
    }

    fun forward() {
        activeTab?.let { webViews[it.id] }?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload() {
        val tab = activeTab ?: return
        val wv = webViews[tab.id]
        when {
            tab.error is PageError.Crashed || wv == null -> {
                if (tab.url.isNotEmpty()) pendingUrls[tab.id] = tab.url
                updateTab(tab.id) { it.copy(error = null) }
            }
            tab.error != null -> {
                updateTab(tab.id) { it.copy(error = null) }
                wv.loadUrl(tab.error.url)
            }
            else -> wv.reload()
        }
    }

    fun stop() {
        activeTab?.let { webViews[it.id] }?.stopLoading()
    }

    // endregion

    // region WebView lifecycle

    /** Returns the tab's WebView, creating it if needed, attached to [activityContext]. */
    fun obtainWebView(tabId: Long, activityContext: Context): WebView {
        webViews[tabId]?.let { wv ->
            (wv.context as? MutableContextWrapper)?.baseContext = activityContext
            (wv.parent as? ViewGroup)?.removeView(wv)
            pendingUrls.remove(tabId)?.let { wv.loadUrl(it) }
            return wv
        }
        val wv = createWebView(tabId, activityContext)
        webViews[tabId] = wv
        pendingUrls.remove(tabId)?.let { wv.loadUrl(it) }
        return wv
    }

    /** Called when the Activity is destroyed but the ViewModel survives (for example a theme change). */
    fun detachFromActivity() {
        webViews.values.forEach { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            (wv.context as? MutableContextWrapper)?.baseContext = app
        }
    }

    fun onActivityPause() {
        webViews.values.forEach { it.onPause() }
        CookieManager.getInstance().flush()
    }

    fun onActivityResume() {
        webViews.values.forEach { it.onResume() }
    }

    override fun onCleared() {
        webViews.values.forEach(::destroyWebView)
        webViews.clear()
    }

    private fun destroyWebView(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        wv.webChromeClient = null
        wv.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(wv: WebView, s: AppSettings) {
        with(wv.settings) {
            javaScriptEnabled = s.javaScriptEnabled
            safeBrowsingEnabled = s.safeBrowsing
            textZoom = s.textZoom
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, s.thirdPartyCookies)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createWebView(tabId: Long, activityContext: Context): WebView {
        val wv = WebView(MutableContextWrapper(activityContext))
        with(wv.settings) {
            domStorageEnabled = true
            // Pages may never read local files or content providers.
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            // Pop-ups reach onCreateWindow, where Sophia applies its own user-gesture rule.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        applySettings(wv, settings.value)
        wv.webViewClient = SophiaWebViewClient(tabId)
        wv.webChromeClient = SophiaChromeClient(tabId)
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult
            val extra = hit.extra
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> if (extra != null) {
                    events.trySend(BrowserEvent.LinkMenu(extra, isImage = false)); true
                } else false
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ->
                    if (extra != null && UrlPolicy.schemeOf(extra) in setOf("http", "https")) {
                        events.trySend(BrowserEvent.LinkMenu(extra, isImage = true)); true
                    } else false
                else -> false
            }
        }
        wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY -> onScrolled(scrollY - oldScrollY, scrollY) }
        // Observes touches for the swipe controls without consuming them, so scrolling, text selection,
        // links and form controls keep working exactly as in any WebView.
        val swipeWatcher = SwipeWatcher(wv) { onSwipe(it) }
        wv.setOnTouchListener { _, event ->
            swipeWatcher.onTouch(event)
            false
        }
        return wv
    }

    private fun onScrolled(dy: Int, y: Int) {
        if (y <= 0 || dy < -12) _toolbarVisible.value = true else if (dy > 12 && y > 120) _toolbarVisible.value = false
    }

    fun showToolbar() {
        _toolbarVisible.value = true
    }

    private inner class SophiaWebViewClient(private val tabId: Long) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            return when (val decision = UrlPolicy.evaluateNavigation(url, navContext())) {
                NavigationDecision.Allow -> false
                is NavigationDecision.External -> {
                    openExternal(decision.url, userInitiated = request.hasGesture())
                    true
                }
                is NavigationDecision.Block -> {
                    explainBlock(url, decision.reason)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            updateTab(tabId) {
                it.copy(url = url, loading = true, error = null, security = UrlPolicy.securityLevelFor(url))
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            val title = view.title.orEmpty()
            updateTab(tabId) {
                it.copy(
                    url = url,
                    title = title.ifBlank { it.title },
                    loading = false,
                    progress = 100,
                    canGoBack = view.canGoBack(),
                    canGoForward = view.canGoForward(),
                )
            }
            if (settings.value.historyEnabled) {
                viewModelScope.launch { library.recordVisit(url, title) }
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            updateTab(tabId) {
                it.copy(url = url, canGoBack = view.canGoBack(), canGoForward = view.canGoForward(), security = UrlPolicy.securityLevelFor(url))
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val url = request.url.toString()
            updateTab(tabId) { it.copy(loading = false, error = PageError.Network(url, error.description?.toString().orEmpty())) }
        }

        /** Certificate problems are never bypassed: the load is cancelled and the student is told why. */
        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            val problem = when (error.primaryError) {
                SslError.SSL_EXPIRED -> "The site's security certificate has expired."
                SslError.SSL_IDMISMATCH -> "The certificate was issued for a different site."
                SslError.SSL_UNTRUSTED -> "The certificate is not from a trusted authority."
                SslError.SSL_NOTYETVALID -> "The certificate is not valid yet. Check your device's date and time."
                SslError.SSL_DATE_INVALID -> "The certificate's date is invalid. Check your device's date and time."
                else -> "The site's security certificate could not be verified."
            }
            updateTab(tabId) { it.copy(loading = false, error = PageError.Certificate(error.url ?: it.url, problem)) }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Recover instead of crashing Sophia: drop this WebView; Reload creates a fresh one.
            webViews.remove(tabId)?.let(::destroyWebView)
            updateTab(tabId) { it.copy(loading = false, error = PageError.Crashed(it.url)) }
            return true
        }
    }

    private inner class SophiaChromeClient(private val tabId: Long) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            updateTab(tabId) { it.copy(progress = newProgress, loading = newProgress < 100) }
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (title.isNullOrBlank()) return
            val url = view.url.orEmpty()
            updateTab(tabId) { it.copy(title = title) }
            if (settings.value.historyEnabled && url.isNotEmpty()) {
                viewModelScope.launch { library.updateTitle(url, title) }
            }
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val origin = UrlPolicy.originOf(view.url.orEmpty())
            val allowed = isUserGesture || (origin != null && origin in settings.value.popupAllowedOrigins)
            if (!allowed) {
                showNotice(
                    "Blocked a pop-up from ${UrlPolicy.hostOf(view.url.orEmpty()).ifEmpty { "this page" }}.",
                    origin?.let { NoticeAction.AllowPopups(it) },
                )
                return false
            }
            val newId = addTab("", select = true) ?: return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val context = (view.context as? MutableContextWrapper)?.baseContext ?: view.context
            transport.webView = obtainWebView(newId, context)
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            webViews.entries.firstOrNull { it.value == window }?.key?.let(::closeTab)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            val intent = try {
                fileChooserParams.createIntent()
            } catch (e: Exception) {
                return false
            }
            events.trySend(BrowserEvent.ChooseFiles(intent, filePathCallback))
            return true
        }

        /** Camera, microphone and other protected resources are not granted; Sophia does not hold those permissions. */
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
            showNotice("${UrlPolicy.hostOf(request.origin.toString()).ifEmpty { "This page" }} asked for camera or microphone access. Sophia does not grant it.")
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (_fullscreenView.value != null) {
                callback.onCustomViewHidden()
                return
            }
            fullscreenCallback = callback
            _fullscreenView.value = view
        }

        override fun onHideCustomView() {
            _fullscreenView.value = null
            fullscreenCallback = null
        }
    }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        _fullscreenView.value = null
    }

    // endregion

    // region Policy feedback

    private fun showNotice(message: String, action: NoticeAction? = null) {
        _notice.value = BrowserNotice(message, action)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun runNoticeAction(action: NoticeAction) {
        dismissNotice()
        when (action) {
            is NoticeAction.AllowHttp -> {
                allowedHttpOrigins += action.origin
                loadAllowed(action.url, newTab = false)
            }
            is NoticeAction.AllowPopups -> viewModelScope.launch {
                settingsRepository.allowPopups(action.origin)
                events.send(BrowserEvent.Message("Pop-ups allowed for ${UrlPolicy.hostOf(action.origin)}. Try again."))
            }
            is NoticeAction.OpenExternal -> events.trySend(BrowserEvent.LaunchExternal(action.intent, null))
        }
    }

    private fun explainBlock(url: String, reason: BlockReason) {
        when (reason) {
            BlockReason.INSECURE -> {
                val origin = UrlPolicy.originOf(url)
                showNotice(
                    "${UrlPolicy.hostOf(url)} isn't using a secure connection. Information you send could be read by others.",
                    origin?.let { NoticeAction.AllowHttp(it, url) },
                )
            }
            BlockReason.CREDENTIALS -> showNotice("Sophia blocked a link with a user name or password in it.")
            BlockReason.SCHEME -> showNotice("Sophia doesn't open this kind of link.")
            BlockReason.INVALID -> showNotice("That address isn't valid.")
        }
    }

    private fun openExternal(url: String, userInitiated: Boolean) {
        val intent = ExternalLinks.toIntent(url) ?: run {
            showNotice("Sophia doesn't open this kind of link.")
            return
        }
        if (userInitiated) {
            events.trySend(BrowserEvent.LaunchExternal(intent, ExternalLinks.fallbackUrl(url)))
        } else {
            // A page tried to switch to another app without a tap: ask first.
            showNotice("This page wants to open another app.", NoticeAction.OpenExternal(intent))
        }
    }

    // endregion

    // region Downloads, page tools

    private fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        viewModelScope.launch {
            val message = when (val r = downloads.enqueue(url, userAgent, contentDisposition, mimeType, settings.value.downloadLocation)) {
                is EnqueueResult.Started -> if (r.location == DownloadLocation.PUBLIC_DOWNLOADS) {
                    "Downloading ${r.fileName} to Download/Sophia"
                } else {
                    "Downloading ${r.fileName} to Sophia app storage"
                }
                is EnqueueResult.Rejected -> r.message
            }
            events.send(BrowserEvent.Message(message))
        }
    }

    fun downloadLink(url: String) = startDownload(url, activeTab?.let { webViews[it.id] }?.settings?.userAgentString, null, null)

    fun bookmarkState(url: String) = library.isBookmarked(url)

    fun toggleBookmark() {
        val tab = activeTab ?: return
        if (tab.url.isEmpty()) return
        viewModelScope.launch {
            if (LibraryRepository.isBuiltIn(tab.url)) {
                events.send(BrowserEvent.Message("AMA Discipulus is Sophia's built-in bookmark"))
                return@launch
            }
            val now = library.toggleBookmark(tab.url, tab.title)
            events.send(BrowserEvent.Message(if (now) "Bookmarked" else "Bookmark removed"))
        }
    }

    /**
     * Sends the selected text, or else the page's visible text, to the Study Assistant for review.
     * This reads the page once with a one-off script; nothing stays injected and no bridge is exposed.
     */
    fun sendPageToAssistant(selectionOnly: Boolean) {
        val tab = activeTab ?: return
        val wv = webViews[tab.id] ?: return
        val script = if (selectionOnly) {
            "(function(){return String(window.getSelection ? window.getSelection() : '')})()"
        } else {
            "(function(){var s=String(window.getSelection ? window.getSelection() : '');" +
                "return s.trim().length>0 ? s : (document.body ? document.body.innerText : '')})()"
        }
        wv.evaluateJavascript(script) { result ->
            val text = runCatching { (Json.parseToJsonElement(result) as? JsonPrimitive)?.content }.getOrNull().orEmpty()
            if (text.isBlank()) {
                events.trySend(BrowserEvent.Message("Select some text on the page first."))
            } else {
                inbox.send(text.take(SophiaConstants.MAX_AI_INPUT_CHARS * 2), tab.title.ifBlank { UrlPolicy.hostOf(tab.url) })
                events.trySend(BrowserEvent.OpenAssistant)
            }
        }
    }

    /** Clears cookies, site storage and the HTTP cache for all pages. */
    fun clearBrowsingData(cookies: Boolean, cache: Boolean) {
        if (cookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
        if (cache) {
            val wv = webViews.values.firstOrNull()
            if (wv != null) {
                wv.clearCache(true)
            } else {
                // clearCache applies to the whole app; a temporary WebView is enough to call it.
                WebView(app).apply { clearCache(true); destroy() }
            }
        }
    }

    fun clearTabHistory() {
        webViews.values.forEach { it.clearHistory() }
        _tabs.update { list -> list.map { it.copy(canGoBack = false, canGoForward = false) } }
    }

    // endregion

    private companion object {
        const val KEY_URLS = "browser_tab_urls"
        const val KEY_ACTIVE = "browser_active_tab"
    }
}

/** Builds safe intents for links that belong to other apps. */
object ExternalLinks {
    fun toIntent(url: String): Intent? {
        if (!UrlPolicy.isSafeExternalUrl(url)) return null
        return if (UrlPolicy.schemeOf(url) == "intent") {
            val parsed = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (e: Exception) {
                return null
            }
            // Only let the link name an action and data; never a specific component or a selector.
            parsed.apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** The https fallback an intent: link may carry, used when no app can handle it. */
    fun fallbackUrl(url: String): String? {
        if (UrlPolicy.schemeOf(url) != "intent") return null
        val fallback = Regex("S\\.browser_fallback_url=([^;]+)").find(url)?.groupValues?.get(1) ?: return null
        val decoded = runCatching { Uri.decode(fallback) }.getOrNull() ?: return null
        return decoded.takeIf { UrlPolicy.schemeOf(it) == "https" }
    }
}
