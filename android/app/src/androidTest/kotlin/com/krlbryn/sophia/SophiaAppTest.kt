package com.krlbryn.sophia

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krlbryn.sophia.core.AdaptiveLayout
import com.krlbryn.sophia.core.LayoutClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks on a real device or emulator, run in CI on a phone and a tablet profile. */
@RunWith(AndroidJUnit4::class)
class SophiaAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val layout: LayoutClass
        get() {
            val c = compose.activity.resources.configuration
            return AdaptiveLayout.classify(c.screenWidthDp.toFloat(), c.screenHeightDp.toFloat())
        }

    private val inNavigation: SemanticsMatcher =
        hasAnyAncestor(hasTestTag("bottom_nav") or hasTestTag("nav_rail") or hasTestTag("nav_drawer"))

    private fun goTo(label: String) {
        compose.onNode(hasText(label) and inNavigation).performClick()
        compose.waitForIdle()
    }

    @Test
    fun homeOffersTheDiscipulusPortal() {
        compose.onNodeWithTag("portal_hero").assertExists()
        compose.onNodeWithTag("open_discipulus").assertExists()
    }

    @Test
    fun navigationFollowsTheWindowSize() {
        val expected = when (layout) {
            LayoutClass.COMPACT -> "bottom_nav"
            LayoutClass.MEDIUM -> "nav_rail"
            LayoutClass.EXPANDED -> "nav_drawer"
        }
        compose.onNodeWithTag(expected).assertExists()
    }

    @Test
    fun bookmarksAlwaysIncludeThePortal() {
        goTo("Bookmarks")
        compose.onNodeWithTag("builtin_bookmark").assertExists()
    }

    @Test
    fun aboutCreditsSophiaAndKrlbryn() {
        goTo("Settings")
        compose.onNodeWithTag("settings_about").performClick()
        compose.onNodeWithTag("about_version").performScrollTo().assertExists()
        compose.onNodeWithText("Made by KRLBRYN").performScrollTo().assertExists()
    }

    @Test
    fun swipesRevealTheGesturePanels() {
        goTo("Browser")
        // Swipes are made inside the page area, not across the navigation drawer or rail.
        val content = compose.onNodeWithTag("browser_content")
        content.performTouchInput { swipeLeft(startX = right - 40f, endX = left + 40f, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithTag("nav_controls").assertExists()
        // A new tab has no history, so Back and Forward are disabled.
        compose.onNode(hasText("Back") and hasAnyAncestor(hasTestTag("nav_controls"))).assertIsNotEnabled()

        // Swiping back toward the centre closes it; swiping right then opens the portal panel.
        compose.onNodeWithTag("controls_scrim").performTouchInput { swipeRight(startX = left + 40f, endX = right - 40f, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithTag("nav_controls").assertDoesNotExist()
        content.performTouchInput { swipeRight(startX = left + 40f, endX = right - 40f, durationMillis = 200) }
        compose.waitForIdle()
        compose.onNodeWithTag("bookmark_controls").assertExists()
        compose.onNodeWithTag("builtin_discipulus").assertExists()

        // Tapping the page outside the panel dismisses it.
        compose.onNodeWithTag("controls_scrim").performTouchInput { click(Offset(right - 20f, top + 20f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("bookmark_controls").assertDoesNotExist()
    }

    @Test
    fun menuIsAnAlternativeToSwiping() {
        goTo("Browser")
        compose.onNodeWithTag("browser_menu").performClick()
        compose.onNodeWithText("Navigation controls").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_controls").assertExists()
    }
}
