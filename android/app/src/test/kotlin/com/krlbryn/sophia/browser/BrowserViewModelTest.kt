package com.krlbryn.sophia.browser

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.krlbryn.sophia.core.SwipeDirection
import com.krlbryn.sophia.data.db.SophiaDatabase
import com.krlbryn.sophia.data.repo.AssistantInbox
import com.krlbryn.sophia.data.repo.DownloadsRepository
import com.krlbryn.sophia.data.repo.LibraryRepository
import com.krlbryn.sophia.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BrowserViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: SophiaDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var inbox: AssistantInbox
    private lateinit var vm: BrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, SophiaDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb") })
        inbox = AssistantInbox()
        vm = BrowserViewModel(
            app,
            SavedStateHandle(),
            settings,
            LibraryRepository(db.bookmarks(), db.history(), db.shortcuts()),
            DownloadsRepository(app, db.downloads()),
            inbox,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun swipeLeftShowsNavigationAndSwipeRightShowsBookmarks() {
        assertEquals(ControlsPanel.NONE, vm.controlsPanel.value)
        vm.onSwipe(SwipeDirection.LEFT)
        assertEquals(ControlsPanel.NAVIGATION, vm.controlsPanel.value)
        vm.hideControls()
        vm.onSwipe(SwipeDirection.RIGHT)
        assertEquals(ControlsPanel.BOOKMARKS, vm.controlsPanel.value)
    }

    @Test
    fun swipingBackTowardTheCentreClosesThePanel() {
        vm.onSwipe(SwipeDirection.LEFT)
        vm.onSwipe(SwipeDirection.RIGHT)
        assertEquals(ControlsPanel.NONE, vm.controlsPanel.value)

        vm.onSwipe(SwipeDirection.RIGHT)
        vm.onSwipe(SwipeDirection.LEFT)
        assertEquals(ControlsPanel.NONE, vm.controlsPanel.value)
    }

    @Test
    fun repeatingTheSameSwipeKeepsThePanelOpen() {
        vm.onSwipe(SwipeDirection.LEFT)
        vm.onSwipe(SwipeDirection.LEFT)
        assertEquals(ControlsPanel.NAVIGATION, vm.controlsPanel.value)
    }

    @Test
    fun accessibleAlternativeOpensEitherPanel() {
        vm.showControls(ControlsPanel.BOOKMARKS)
        assertEquals(ControlsPanel.BOOKMARKS, vm.controlsPanel.value)
        vm.showControls(ControlsPanel.NAVIGATION)
        assertEquals(ControlsPanel.NAVIGATION, vm.controlsPanel.value)
    }

    @Test
    fun openingThePortalCreatesAnHttpsTab() {
        vm.openPortal()
        val tab = vm.activeTab
        assertEquals("https://discipulusv2.amasystem.net/", tab?.url)
        assertEquals(false, tab?.canGoBack)
    }

    @Test
    fun backAndForwardWithNoHistoryDoNothing() {
        vm.openPortal()
        vm.back()
        vm.forward()
        assertEquals(1, vm.tabs.value.size)
    }

    @Test
    fun linksFromOtherAppsMustBeHttps() {
        vm.openFromIntent("http://discipulusv2.amasystem.net/")
        assertTrue(vm.tabs.value.isEmpty())
        vm.openFromIntent("https://discipulusv2.amasystem.net/course")
        assertEquals(1, vm.tabs.value.size)
    }

    @Test
    fun javascriptUrlsAreNeverOpened() {
        vm.open("javascript:alert(1)")
        assertTrue(vm.tabs.value.none { it.url.startsWith("javascript") })
    }

    @Test
    fun sharedTextWaitsInTheAssistantInbox() = runTest {
        vm.receiveSharedText("Photosynthesis converts light energy", "Notes app")
        assertEquals("Notes app", inbox.pending.value?.source)
        val event = withTimeout(1_000) { vm.eventFlow.first() }
        assertEquals(BrowserEvent.OpenAssistant, event)
    }

    @Test
    fun closingTheLastTabLeavesNoActiveTab() {
        vm.newTab()
        val id = vm.activeTabId.value!!
        vm.closeTab(id)
        assertNull(vm.activeTabId.value)
    }
}
