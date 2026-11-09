package com.krlbryn.sophia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krlbryn.sophia.core.AdaptiveLayout
import com.krlbryn.sophia.core.LayoutClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks on a real device or emulator, run in CI on a phone and a tablet profile. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SophiaAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val layout: LayoutClass
        get() {
            val c = compose.activity.resources.configuration
            return AdaptiveLayout.classify(c.screenWidthDp.toFloat(), c.screenHeightDp.toFloat())
        }

    private val navTag: String
        get() = when (layout) {
            LayoutClass.COMPACT -> "bottom_nav"
            LayoutClass.MEDIUM -> "nav_rail"
            LayoutClass.EXPANDED -> "nav_drawer"
        }

    private val inNavigation: SemanticsMatcher =
        hasAnyAncestor(hasTestTag("bottom_nav") or hasTestTag("nav_rail") or hasTestTag("nav_drawer"))

    private fun swipeFromLeftEdge() =
        compose.onNodeWithTag("browser_content").performTouchInput { swipeRight(startX = left + 8f, endX = left + width * 0.7f, durationMillis = 200) }

    private fun swipeFromRightEdge() =
        compose.onNodeWithTag("browser_content").performTouchInput { swipeLeft(startX = right - 8f, endX = right - width * 0.7f, durationMillis = 200) }

    /** The page has started: either the WebView or (offline) Sophia's error page is showing. */
    private fun awaitPage() = compose.waitUntil(15_000) {
        compose.onAllNodes(hasTestTag("webview") or hasTestTag("error_page")).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openSettingsFromBrowser() {
        awaitPage()
        swipeFromRightEdge()
        compose.waitForIdle()
        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
    }

    // Test A: normal state.
    @Test
    fun launchesStraightIntoAFullscreenPageWithNothingOnTop() {
        awaitPage()
        compose.onNodeWithTag("browser_content").assertExists()
        compose.onNodeWithTag("controls_scrim").assertDoesNotExist()
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
        compose.onNodeWithTag("bookmark_controls").assertDoesNotExist()
        compose.onNodeWithTag("bottom_nav").assertDoesNotExist()
        compose.onNodeWithTag("nav_rail").assertDoesNotExist()
        compose.onNodeWithTag("nav_drawer").assertDoesNotExist()
    }

    // Test B: left panel.
    @Test
    fun leftEdgeSwipeShowsAddressBarAndBookmarks() {
        awaitPage()
        swipeFromLeftEdge()
        compose.waitForIdle()
        compose.onNodeWithTag("bookmark_controls").assertExists()
        compose.onNodeWithTag("panel_address").assertExists()
        compose.onNodeWithTag("panel_bookmark_discipulus").assertExists()
        compose.onNodeWithTag("panel_bookmark_lms").assertExists()
        compose.onNodeWithTag("panel_bookmark_aurora").assertExists()

        // LMS has no address yet, so Sophia asks for one instead of guessing.
        compose.onNodeWithTag("panel_bookmark_lms").performClick()
        compose.onNodeWithTag("bookmark_setup_url").assertExists()
        compose.onNodeWithText("Cancel").performClick()

        // Tapping the page beside the panel closes it and removes the dimming.
        compose.onNodeWithTag("controls_scrim").performTouchInput { click(Offset(right - 20f, centerY)) }
        compose.waitForIdle()
        compose.onNodeWithTag("bookmark_controls").assertDoesNotExist()
        compose.onNodeWithTag("controls_scrim").assertDoesNotExist()
    }

    // Test C: right panel.
    @Test
    fun rightEdgeSwipeShowsControlsAndSwipingBackClosesThem() {
        awaitPage()
        swipeFromRightEdge()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_controls").assertExists()
        listOf("Back", "Forward", "Reload", "Settings").forEach { label ->
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("nav_controls"))).assertExists()
        }
        // Swiping the panel back toward its edge closes it.
        compose.onNodeWithTag("controls_scrim").performTouchInput { swipeRight(startX = left + 20f, endX = left + width * 0.6f, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
        compose.onNodeWithTag("controls_scrim").assertDoesNotExist()
    }

    @Test
    fun swipesInTheMiddleOfThePageDoNotOpenPanels() {
        awaitPage()
        compose.onNodeWithTag("browser_content").performTouchInput { swipeRight(startX = centerX - 60f, endX = centerX + 300f, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithTag("bookmark_controls").assertDoesNotExist()
    }

    @Test
    fun settingsControlOpensSettingsWithTheAppNavigation() {
        openSettingsFromBrowser()
        compose.onNodeWithTag(navTag).assertExists()
        compose.onNodeWithTag("settings_about").performClick()
        compose.onNodeWithTag("about_version").performScrollTo().assertExists()
        compose.onNodeWithText("Made by KRLBRYN").performScrollTo().assertExists()
    }

    @Test
    fun bookmarksScreenAlwaysIncludesThePortal() {
        openSettingsFromBrowser()
        compose.onNode(hasText("Bookmarks") and inNavigation).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("builtin_bookmark").assertExists()
    }
}
