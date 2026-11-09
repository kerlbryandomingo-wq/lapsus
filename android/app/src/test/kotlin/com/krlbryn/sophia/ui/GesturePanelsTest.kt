package com.krlbryn.sophia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krlbryn.sophia.browser.ControlsPanel
import com.krlbryn.sophia.core.SwipeDirection
import com.krlbryn.sophia.ui.browser.GesturePanels
import com.krlbryn.sophia.ui.browser.PanelBookmark
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

    private val bookmarks = listOf(
        PanelBookmark("Discipulus", "https://discipulusv2.amasystem.net/", "discipulus"),
        PanelBookmark("LMS", null, "lms"),
        PanelBookmark("Aurora Login", null, "aurora"),
    )

    private fun show(canGoBack: Boolean = false, canGoForward: Boolean = false) {
        compose.setContent {
            SophiaTheme {
                Box(Modifier.fillMaxSize()) {
                    GesturePanels(
                        panel = panel,
                        wide = false,
                        currentUrl = "https://discipulusv2.amasystem.net/",
                        bookmarks = bookmarks,
                        tabs = emptyList(),
                        activeTabId = null,
                        focusAddress = false,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        canReload = true,
                        onSwipe = { d ->
                            calls += "swipe:$d"
                            // Same rule as BrowserViewModel.onSwipe.
                            panel = when (d) {
                                SwipeDirection.LEFT -> if (panel == ControlsPanel.BOOKMARKS) ControlsPanel.NONE else ControlsPanel.NAVIGATION
                                SwipeDirection.RIGHT -> if (panel == ControlsPanel.NAVIGATION) ControlsPanel.NONE else ControlsPanel.BOOKMARKS
                            }
                        },
                        onDismiss = { calls += "dismiss"; panel = ControlsPanel.NONE },
                        onNavigate = { calls += "go:$it" },
                        onBookmark = { calls += "bookmark:${it.key}" },
                        onManageBookmarks = { calls += "manage" },
                        onSelectTab = {},
                        onCloseTab = {},
                        onBack = { calls += "back" },
                        onForward = { calls += "forward" },
                        onReload = { calls += "reload" },
                        onSettings = { calls += "settings" },
                    )
                }
            }
        }
    }

    @Test
    fun normalStateDrawsNothingOverThePage() {
        show()
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
        compose.onNodeWithTag("bookmark_controls").assertDoesNotExist()
        // No scrim: the page keeps its own colours.
        compose.onNodeWithTag("controls_scrim").assertDoesNotExist()
    }

    @Test
    fun rightPanelHasFourControlsAndDisablesUnavailableOnes() {
        panel = ControlsPanel.NAVIGATION
        show(canGoBack = false, canGoForward = true)
        compose.onNodeWithTag("nav_controls").assertIsDisplayed()
        compose.onNodeWithText("Back").assertIsNotEnabled()
        compose.onNodeWithText("Forward").assertIsEnabled().performClick()
        compose.onNodeWithText("Reload").assertIsEnabled().performClick()
        compose.onNodeWithText("Settings").assertIsEnabled().performClick()
        assertEquals(listOf("forward", "reload", "settings"), calls)
    }

    @Test
    fun leftPanelHasAddressBarAndThreeBookmarks() {
        panel = ControlsPanel.BOOKMARKS
        show()
        compose.onNodeWithTag("bookmark_controls").assertIsDisplayed()
        compose.onNodeWithText("Address Bar").assertIsDisplayed()
        compose.onNodeWithText("Bookmarks").assertIsDisplayed()
        compose.onNodeWithTag("panel_bookmark_discipulus").performClick()
        compose.onNodeWithTag("panel_bookmark_lms").performClick()
        compose.onNodeWithTag("panel_bookmark_aurora").performClick()
        compose.onNodeWithText("Manage").performClick()
        assertEquals(listOf("bookmark:discipulus", "bookmark:lms", "bookmark:aurora", "manage"), calls)
    }

    @Test
    fun addressBarNavigates() {
        panel = ControlsPanel.BOOKMARKS
        show()
        compose.onNodeWithTag("panel_address").performTextReplacement("photosynthesis")
        compose.onNodeWithTag("panel_go").performClick()
        assertEquals(listOf("go:photosynthesis"), calls)
    }

    @Test
    fun tappingThePageBesideAPanelClosesIt() {
        panel = ControlsPanel.NAVIGATION
        show()
        compose.onNodeWithTag("controls_scrim").performTouchInput { click(Offset(20f, 20f)) }
        compose.waitForIdle()
        assertTrue("dismiss" in calls)
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
        compose.onNodeWithTag("controls_scrim").assertDoesNotExist()
    }

    @Test
    fun swipingAPanelTowardItsEdgeClosesIt() {
        panel = ControlsPanel.BOOKMARKS
        show()
        compose.onNodeWithTag("bookmark_controls").performTouchInput { swipeLeft(startX = right - 10f, endX = left, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithTag("bookmark_controls").assertDoesNotExist()

        panel = ControlsPanel.NAVIGATION
        compose.waitForIdle()
        compose.onNodeWithTag("controls_scrim").performTouchInput { swipeRight(startX = left + 10f, endX = left + 300f, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
    }
}
