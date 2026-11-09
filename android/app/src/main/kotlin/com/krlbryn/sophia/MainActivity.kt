package com.krlbryn.sophia

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.krlbryn.sophia.browser.BrowserViewModel
import com.krlbryn.sophia.browser.ControlsPanel
import com.krlbryn.sophia.ui.navigation.SophiaApp
import com.krlbryn.sophia.ui.theme.SophiaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val browser: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settings by browser.settings.collectAsStateWithLifecycle()
            SophiaTheme(themeMode = settings.themeMode, accent = settings.accent) {
                SophiaApp(browser)
            }
        }

        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let(browser::openFromIntent)
            Intent.ACTION_SEND -> if (intent.type == "text/plain") {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val source = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(120) ?: "Shared text"
                browser.receiveSharedText(text, source)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        browser.onActivityPause()
    }

    override fun onResume() {
        super.onResume()
        browser.onActivityResume()
    }

    override fun onDestroy() {
        // The WebViews live in the ViewModel. When the Activity is only being recreated they are kept
        // and re-attached; otherwise the ViewModel is cleared and destroys them.
        if (isChangingConfigurations) browser.detachFromActivity()
        super.onDestroy()
    }

    /** Keyboard shortcuts for tablets and Chromebooks with a hardware keyboard. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleShortcut(event) || super.onKeyDown(keyCode, event)

    private fun handleShortcut(e: KeyEvent): Boolean {
        val ctrl = e.isCtrlPressed && !e.isAltPressed
        val alt = e.isAltPressed && !e.isCtrlPressed
        when {
            ctrl && e.keyCode == KeyEvent.KEYCODE_L -> { browser.showBrowser(); browser.requestAddressFocus() }
            ctrl && e.keyCode == KeyEvent.KEYCODE_T -> { browser.newTab(); browser.showBrowser(); browser.requestAddressFocus() }
            ctrl && e.keyCode == KeyEvent.KEYCODE_W -> browser.activeTabId.value?.let(browser::closeTab) ?: return false
            ctrl && e.keyCode == KeyEvent.KEYCODE_R -> browser.reload()
            ctrl && e.keyCode == KeyEvent.KEYCODE_D -> browser.toggleBookmark()
            ctrl && e.keyCode == KeyEvent.KEYCODE_B -> { browser.showBrowser(); browser.showControls(ControlsPanel.BOOKMARKS) }
            ctrl && e.keyCode == KeyEvent.KEYCODE_J -> { browser.showBrowser(); browser.showControls(ControlsPanel.NAVIGATION) }
            alt && e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> browser.back()
            alt && e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> browser.forward()
            e.keyCode == KeyEvent.KEYCODE_F5 -> browser.reload()
            else -> return false
        }
        return true
    }
}
