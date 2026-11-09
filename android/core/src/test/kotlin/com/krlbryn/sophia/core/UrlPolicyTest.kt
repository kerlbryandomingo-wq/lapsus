package com.krlbryn.sophia.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    private val g = SearchEngine.GOOGLE

    @Test fun blankInputGivesNull() = assertNull(UrlPolicy.normalizeAddressInput("   ", g))

    @Test fun bareHostBecomesHttps() =
        assertEquals("https://discipulusv2.amasystem.net/", UrlPolicy.normalizeAddressInput("discipulusv2.amasystem.net", g))

    @Test fun hostWithPathKeepsPath() =
        assertEquals("https://example.com/a?b=1", UrlPolicy.normalizeAddressInput("example.com/a?b=1", g))

    @Test fun loopbackBecomesHttp() =
        assertEquals("http://localhost:8080/", UrlPolicy.normalizeAddressInput("localhost:8080", g))

    @Test fun explicitUrlKept() =
        assertEquals("http://example.com/", UrlPolicy.normalizeAddressInput("http://example.com", g))

    @Test fun wordsBecomeSearch() =
        assertEquals("https://www.google.com/search?q=photosynthesis%20light%20reaction", UrlPolicy.normalizeAddressInput("photosynthesis light reaction", g))

    @Test fun singleWordIsSearch() =
        assertEquals("https://duckduckgo.com/?q=calculus", UrlPolicy.normalizeAddressInput("calculus", SearchEngine.DUCKDUCKGO))

    @Test fun httpsIsAllowed() =
        assertEquals(NavigationDecision.Allow, UrlPolicy.evaluateNavigation("https://discipulusv2.amasystem.net/login"))

    @Test fun plainHttpIsBlockedUntilAllowed() {
        assertEquals(NavigationDecision.Block(BlockReason.INSECURE), UrlPolicy.evaluateNavigation("http://example.com/"))
        val ctx = NavigationContext(allowedHttpOrigins = setOf("http://example.com"))
        assertEquals(NavigationDecision.Allow, UrlPolicy.evaluateNavigation("http://example.com/page", ctx))
    }

    @Test fun loopbackHttpAllowed() =
        assertEquals(NavigationDecision.Allow, UrlPolicy.evaluateNavigation("http://127.0.0.1:3000/"))

    @Test fun credentialsInUrlBlocked() =
        assertEquals(NavigationDecision.Block(BlockReason.CREDENTIALS), UrlPolicy.evaluateNavigation("https://user:pw@example.com/"))

    @Test fun dangerousSchemesBlocked() {
        for (u in listOf("javascript:alert(1)", "file:///sdcard/x", "content://x/y", "data:text/html,hi", "chrome://flags", "about:config")) {
            assertEquals(u, NavigationDecision.Block(BlockReason.SCHEME), UrlPolicy.evaluateNavigation(u))
        }
    }

    @Test fun appSchemesGoExternal() {
        assertEquals(NavigationDecision.External("mailto:a@b.c"), UrlPolicy.evaluateNavigation("mailto:a@b.c"))
        assertTrue(UrlPolicy.evaluateNavigation("intent://scan/#Intent;scheme=zxing;end") is NavigationDecision.External)
    }

    @Test fun portalOnlySendsOtherSitesOut() {
        val ctx = NavigationContext(portalOnly = true, extraPortalHosts = listOf("example.edu"))
        assertEquals(NavigationDecision.Allow, UrlPolicy.evaluateNavigation("https://x.amaes.edu.ph/", ctx))
        assertEquals(NavigationDecision.Allow, UrlPolicy.evaluateNavigation("https://lms.example.edu/", ctx))
        assertTrue(UrlPolicy.evaluateNavigation("https://google.com/", ctx) is NavigationDecision.External)
    }

    @Test fun portalHostMatching() {
        assertTrue(UrlPolicy.isPortalHost("discipulusv2.amasystem.net"))
        assertTrue(UrlPolicy.isPortalHost("AMASYSTEM.NET."))
        assertFalse(UrlPolicy.isPortalHost("evilamasystem.net"))
    }

    @Test fun securityLevels() {
        assertEquals(SecurityLevel.SECURE, UrlPolicy.securityLevelFor("https://a.com"))
        assertEquals(SecurityLevel.INSECURE, UrlPolicy.securityLevelFor("http://a.com"))
        assertEquals(SecurityLevel.LOCAL, UrlPolicy.securityLevelFor("http://localhost/"))
        assertEquals(SecurityLevel.CERT_ERROR, UrlPolicy.securityLevelFor("https://a.com", certError = true))
    }

    @Test fun displayUrlDropsTrailingSlash() {
        assertEquals("https://example.com", UrlPolicy.displayUrl("https://example.com/"))
        assertEquals("https://example.com/a", UrlPolicy.displayUrl("https://example.com/a"))
        assertEquals("http://example.com:8080", UrlPolicy.displayUrl("http://example.com:8080/"))
    }

    @Test fun homepageValidation() {
        assertEquals("https://example.edu/", UrlPolicy.validateHomepage("example.edu"))
        assertNull(UrlPolicy.validateHomepage("http://example.edu"))
        assertNull(UrlPolicy.validateHomepage("https://u:p@example.edu"))
    }

    @Test fun safeExternal() {
        assertTrue(UrlPolicy.isSafeExternalUrl("tel:+6312345"))
        assertFalse(UrlPolicy.isSafeExternalUrl("file:///x"))
    }

    @Test fun hostnameNormalization() {
        assertEquals("example.edu", UrlPolicy.normalizeHostname(" Example.EDU "))
        assertNull(UrlPolicy.normalizeHostname("https://example.edu"))
    }
}
