package com.krlbryn.sophia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krlbryn.sophia.browser.ControlsPanel
import com.krlbryn.sophia.ui.browser.GesturePanels
import com.krlbryn.sophia.ui.theme.SophiaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class GesturePanelsTest {
    @get:Rule val compose = createComposeRule()

    private var panel by mutableStateOf(ControlsPanel.NONE)
    private val calls = mutableListOf<String>()

    private fun show(canGoBack: Boolean = false, canGoForward: Boolean = false) {
        compose.setContent {
            SophiaTheme {
                Box(Modifier.fillMaxSize()) {
                    GesturePanels(
                        panel = panel,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        canReload = true,
                        onSwipe = { calls += "swipe:$it" },
                        onDismiss = { calls += "dismiss"; panel = ControlsPanel.NONE },
                        onBack = { calls += "back" },
                        onForward = { calls += "forward" },
                        onReload = { calls += "reload" },
                        onOpenPortal = { calls += "portal" },
                        onOpenBookmarks = { calls += "bookmarks" },
                    )
                }
            }
        }
    }

    @Test
    fun nothingIsShownUntilAPanelOpens() {
        show()
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
        compose.onNodeWithTag("bookmark_controls").assertDoesNotExist()
        compose.onNodeWithTag("controls_scrim").assertDoesNotExist()
    }

    @Test
    fun navigationPanelDisablesUnavailableButtons() {
        panel = ControlsPanel.NAVIGATION
        show(canGoBack = false, canGoForward = true)
        compose.onNodeWithTag("nav_controls").assertIsDisplayed()
        compose.onNodeWithText("Back").assertIsNotEnabled()
        compose.onNodeWithText("Forward").assertIsEnabled()
        compose.onNodeWithText("Reload").assertIsEnabled().performClick()
        assertEquals(listOf("reload"), calls)
    }

    @Test
    fun bookmarksPanelOffersOnlyThePortalAndTheBookmarksScreen() {
        panel = ControlsPanel.BOOKMARKS
        show()
        compose.onNodeWithTag("bookmark_controls").assertIsDisplayed()
        compose.onNodeWithText("AMA Discipulus").assertIsDisplayed()
        compose.onNodeWithTag("builtin_discipulus").performClick()
        compose.onNodeWithText("Bookmarks").performClick()
        assertEquals(listOf("portal", "bookmarks"), calls)
    }

    @Test
    fun tappingOutsideThePanelDismissesIt() {
        panel = ControlsPanel.NAVIGATION
        show()
        compose.onNodeWithTag("controls_scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()
        assertTrue("dismiss" in calls)
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
    }
}
