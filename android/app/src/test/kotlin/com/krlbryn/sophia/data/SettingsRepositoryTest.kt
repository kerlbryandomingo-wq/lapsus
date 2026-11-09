package com.krlbryn.sophia.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())
    private val repo by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun defaultsAreSafe() = runTest {
        val s = repo.current()
        assertTrue(s.safeBrowsing)
        assertEquals("https://discipulusv2.amasystem.net/", s.homepage)
        assertEquals(AiProviderId.NONE, s.aiProvider)
        assertTrue(s.popupAllowedOrigins.isEmpty())
    }

    @Test
    fun homepageMustBeHttps() = runTest {
        assertFalse(repo.setHomepage("http://example.com"))
        assertFalse(repo.setHomepage("javascript:alert(1)"))
        assertTrue(repo.setHomepage("https://example.com/start"))
        assertEquals("https://example.com/start", repo.current().homepage)
    }

    @Test
    fun textZoomIsClamped() = runTest {
        repo.setTextZoom(500)
        assertEquals(200, repo.current().textZoom)
        repo.setTextZoom(10)
        assertEquals(50, repo.current().textZoom)
    }

    @Test
    fun compatibleBaseUrlRejectsPlainHttp() = runTest {
        assertFalse(repo.setCompatibleBaseUrl("http://api.example.com/v1"))
        assertTrue(repo.setCompatibleBaseUrl("https://api.example.com/v1/"))
        assertEquals("https://api.example.com/v1", repo.current().compatibleBaseUrl)
    }

    @Test
    fun popupPermissionCanBeRevoked() = runTest {
        repo.allowPopups("https://example.com")
        assertEquals(setOf("https://example.com"), repo.current().popupAllowedOrigins)
        repo.revokePopups("https://example.com")
        assertTrue(repo.current().popupAllowedOrigins.isEmpty())
    }
}
