package com.krlbryn.sophia.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
}

/** Decision for a top-level navigation in the browser. */
sealed interface NavigationDecision {
    data object Allow : NavigationDecision

    /** Hand the URL to another Android app (mail, phone, store, an app link). */
    data class External(val url: String) : NavigationDecision

    data class Block(val reason: BlockReason) : NavigationDecision
}

enum class BlockReason { INVALID, SCHEME, INSECURE, CREDENTIALS }

data class NavigationContext(
    val portalOnly: Boolean = false,
    val extraPortalHosts: List<String> = emptyList(),
    /** Origins (scheme://host[:port]) the user explicitly chose to open over plain HTTP. */
    val allowedHttpOrigins: Set<String> = emptySet(),
)

enum class SecurityLevel { SECURE, LOCAL, INSECURE, CERT_ERROR, INTERNAL, NONE }

/**
 * URL rules for the Sophia browser. A Kotlin port of Lapsus for Windows' src/shared/url.ts,
 * kept pure so it is unit-tested without Android.
 */
object UrlPolicy {
    private val SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val HOST_LIKE_RE = Regex(
        "^(localhost|\\[[0-9a-fA-F:]+]|\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,63})(:\\d{1,5})?([/?#].*)?$",
    )
    private val WHITESPACE = Regex("\\s")
    private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "geo", "market", "intent")

    fun isLoopbackHost(hostname: String): Boolean {
        val h = hostname.lowercase().removePrefix("[").removeSuffix("]")
        return h == "localhost" || h == "::1" || Regex("^127(?:\\.\\d{1,3}){3}$").matches(h)
    }

    /** Turns address-bar input into a URL, or a search URL. Returns null for blank input. */
    fun normalizeAddressInput(raw: String, engine: SearchEngine): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        val hasSpace = WHITESPACE.containsMatchIn(input)

        val hostMatch = HOST_LIKE_RE.matchEntire(input)
        if (hostMatch != null && !hasSpace) {
            val host = hostMatch.groupValues[1]
            val scheme = if (isLoopbackHost(host)) "http" else "https"
            "$scheme://$input".toHttpUrlOrNull()?.let { return it.toString() }
        }

        if (SCHEME_RE.containsMatchIn(input) && !hasSpace) {
            val scheme = schemeOf(input)
            if (scheme == "http" || scheme == "https") {
                input.toHttpUrlOrNull()?.let { return it.toString() }
            } else if (scheme != null && input.length > scheme.length + 1) {
                return input
            }
        }

        val encoded = URLEncoder.encode(input, Charsets.UTF_8.name()).replace("+", "%20")
        return engine.template.replace("%s", encoded)
    }

    fun isPortalHost(hostname: String, extraHosts: List<String> = emptyList()): Boolean {
        val h = hostname.lowercase().removeSuffix(".")
        return (SophiaConstants.PORTAL_DOMAINS + extraHosts).any { d ->
            val domain = d.lowercase()
            h == domain || h.endsWith(".$domain")
        }
    }

    fun evaluateNavigation(rawUrl: String, ctx: NavigationContext = NavigationContext()): NavigationDecision {
        val scheme = schemeOf(rawUrl) ?: return NavigationDecision.Block(BlockReason.INVALID)
        return when (scheme) {
            "http", "https" -> {
                if (hasUserInfo(rawUrl)) return NavigationDecision.Block(BlockReason.CREDENTIALS)
                val url = rawUrl.toHttpUrlOrNull() ?: return NavigationDecision.Block(BlockReason.INVALID)
                if (url.host.isEmpty()) return NavigationDecision.Block(BlockReason.INVALID)
                if (url.scheme == "http") {
                    val allowed = isLoopbackHost(url.host) || originOf(rawUrl) in ctx.allowedHttpOrigins
                    if (!allowed) return NavigationDecision.Block(BlockReason.INSECURE)
                }
                if (ctx.portalOnly && !isLoopbackHost(url.host) && !isPortalHost(url.host, ctx.extraPortalHosts)) {
                    return NavigationDecision.External(url.toString())
                }
                NavigationDecision.Allow
            }
            "about" -> if (rawUrl == "about:blank") NavigationDecision.Allow else NavigationDecision.Block(BlockReason.SCHEME)
            in EXTERNAL_SCHEMES -> NavigationDecision.External(rawUrl)
            // file:, content:, javascript:, data:, blob:, chrome:, and anything unknown.
            else -> NavigationDecision.Block(BlockReason.SCHEME)
        }
    }

    /** Lower-cased scheme, or null when the string has none. */
    fun schemeOf(rawUrl: String): String? =
        SCHEME_RE.find(rawUrl.trim())?.value?.dropLast(1)?.lowercase()

    /** "https://host[:port]" for http(s) URLs, otherwise null. Default ports are omitted. */
    fun originOf(rawUrl: String): String? {
        val url = rawUrl.toHttpUrlOrNull() ?: return null
        val default = (url.scheme == "https" && url.port == 443) || (url.scheme == "http" && url.port == 80)
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        return if (default) "${url.scheme}://$host" else "${url.scheme}://$host:${url.port}"
    }

    fun hostOf(rawUrl: String): String = rawUrl.toHttpUrlOrNull()?.host ?: ""

    private fun hasUserInfo(rawUrl: String): Boolean {
        val url = rawUrl.toHttpUrlOrNull() ?: return false
        return url.username.isNotEmpty() || url.password.isNotEmpty()
    }

    /** URLs Sophia may hand to another app. */
    fun isSafeExternalUrl(rawUrl: String): Boolean {
        val scheme = schemeOf(rawUrl) ?: return false
        return when (scheme) {
            "http", "https" -> rawUrl.toHttpUrlOrNull() != null && !hasUserInfo(rawUrl)
            in EXTERNAL_SCHEMES -> true
            else -> false
        }
    }

    fun securityLevelFor(rawUrl: String, certError: Boolean = false): SecurityLevel {
        if (certError) return SecurityLevel.CERT_ERROR
        return when (schemeOf(rawUrl)) {
            "https" -> SecurityLevel.SECURE
            "http" -> if (isLoopbackHost(hostOf(rawUrl))) SecurityLevel.LOCAL else SecurityLevel.INSECURE
            null -> SecurityLevel.NONE
            else -> if (rawUrl == "about:blank") SecurityLevel.NONE else SecurityLevel.INTERNAL
        }
    }

    /** Address-bar display form: drops the trailing slash on bare origins. */
    fun displayUrl(rawUrl: String): String {
        val url = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        return if (url.encodedPath == "/" && url.encodedQuery == null && url.encodedFragment == null) {
            originOf(rawUrl) ?: rawUrl
        } else {
            url.toString()
        }
    }

    /** Validates a user-entered shortcut or homepage. Only https (or loopback http) is accepted. */
    fun validateHomepage(raw: String): String? {
        val normalized = raw.trim().let { if (schemeOf(it) == null) "https://$it" else it }
        val url = normalized.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.scheme == "http" && !isLoopbackHost(url.host)) return null
        return url.toString()
    }

    private val HOSTNAME_RE = Regex("^(?:[a-z0-9-]+\\.)+[a-z]{2,63}$")

    /** Normalizes an extra portal host ("example.edu"); null when it is not a plain host name. */
    fun normalizeHostname(raw: String): String? = raw.trim().lowercase().takeIf { HOSTNAME_RE.matches(it) }
}
