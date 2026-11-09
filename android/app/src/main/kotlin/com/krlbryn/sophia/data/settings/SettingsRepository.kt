package com.krlbryn.sophia.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.krlbryn.sophia.core.AiConfigValidation
import com.krlbryn.sophia.core.AiProviderId
import com.krlbryn.sophia.core.SearchEngine
import com.krlbryn.sophia.core.UrlPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Application preferences in DataStore. Every value is validated on read and falls back to its default. */
@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val displayName = stringPreferencesKey("display_name")
        val search = stringPreferencesKey("search_engine")
        val homepage = stringPreferencesKey("homepage")
        val js = booleanPreferencesKey("javascript")
        val safeBrowsing = booleanPreferencesKey("safe_browsing")
        val history = booleanPreferencesKey("history_enabled")
        val portalOnly = booleanPreferencesKey("portal_only")
        val extraHosts = stringSetPreferencesKey("extra_portal_hosts")
        val textZoom = intPreferencesKey("text_zoom")
        val thirdPartyCookies = booleanPreferencesKey("third_party_cookies")
        val downloadLocation = stringPreferencesKey("download_location")
        val popupOrigins = stringSetPreferencesKey("popup_origins")
        val aiProvider = stringPreferencesKey("ai_provider")
        val openAiModel = stringPreferencesKey("openai_model")
        val anthropicModel = stringPreferencesKey("anthropic_model")
        val compatibleModel = stringPreferencesKey("compatible_model")
        val compatibleBaseUrl = stringPreferencesKey("compatible_base_url")
        val responseDetail = stringPreferencesKey("response_detail")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::fromPreferences)

    suspend fun current(): AppSettings = settings.first()

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    internal fun fromPreferences(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOr(p[K.theme], d.themeMode),
            accent = enumOr(p[K.accent], d.accent),
            displayName = p[K.displayName]?.take(40) ?: d.displayName,
            searchEngine = enumOr(p[K.search], d.searchEngine),
            homepage = p[K.homepage]?.let(UrlPolicy::validateHomepage) ?: d.homepage,
            javaScriptEnabled = p[K.js] ?: d.javaScriptEnabled,
            safeBrowsing = p[K.safeBrowsing] ?: d.safeBrowsing,
            historyEnabled = p[K.history] ?: d.historyEnabled,
            portalOnly = p[K.portalOnly] ?: d.portalOnly,
            extraPortalHosts = p[K.extraHosts]?.mapNotNull(UrlPolicy::normalizeHostname)?.sorted() ?: d.extraPortalHosts,
            textZoom = p[K.textZoom]?.coerceIn(50, 200) ?: d.textZoom,
            thirdPartyCookies = p[K.thirdPartyCookies] ?: d.thirdPartyCookies,
            downloadLocation = enumOr(p[K.downloadLocation], d.downloadLocation),
            popupAllowedOrigins = p[K.popupOrigins] ?: d.popupAllowedOrigins,
            aiProvider = enumOr(p[K.aiProvider], d.aiProvider),
            openAiModel = p[K.openAiModel]?.takeIf(AiConfigValidation::isValidModel) ?: d.openAiModel,
            anthropicModel = p[K.anthropicModel]?.takeIf(AiConfigValidation::isValidModel) ?: d.anthropicModel,
            compatibleModel = p[K.compatibleModel]?.takeIf(AiConfigValidation::isValidModel) ?: d.compatibleModel,
            compatibleBaseUrl = p[K.compatibleBaseUrl]?.let(AiConfigValidation::normalizeBaseUrl) ?: d.compatibleBaseUrl,
            responseDetail = enumOr(p[K.responseDetail], d.responseDetail),
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    suspend fun setThemeMode(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setAccent(v: AccentColor) = edit { it[K.accent] = v.name }
    suspend fun setDisplayName(v: String) = edit { it[K.displayName] = v.trim().take(40) }
    suspend fun setSearchEngine(v: SearchEngine) = edit { it[K.search] = v.name }

    /** Returns false when the URL is not acceptable as a homepage. */
    suspend fun setHomepage(raw: String): Boolean {
        val url = UrlPolicy.validateHomepage(raw) ?: return false
        edit { it[K.homepage] = url }
        return true
    }

    suspend fun setJavaScriptEnabled(v: Boolean) = edit { it[K.js] = v }
    suspend fun setSafeBrowsing(v: Boolean) = edit { it[K.safeBrowsing] = v }
    suspend fun setHistoryEnabled(v: Boolean) = edit { it[K.history] = v }
    suspend fun setPortalOnly(v: Boolean) = edit { it[K.portalOnly] = v }
    suspend fun setTextZoom(v: Int) = edit { it[K.textZoom] = v.coerceIn(50, 200) }
    suspend fun setThirdPartyCookies(v: Boolean) = edit { it[K.thirdPartyCookies] = v }
    suspend fun setDownloadLocation(v: DownloadLocation) = edit { it[K.downloadLocation] = v.name }

    suspend fun addExtraPortalHost(raw: String): Boolean {
        val host = UrlPolicy.normalizeHostname(raw) ?: return false
        edit { it[K.extraHosts] = (it[K.extraHosts].orEmpty() + host).take(20).toSet() }
        return true
    }

    suspend fun removeExtraPortalHost(host: String) = edit { it[K.extraHosts] = it[K.extraHosts].orEmpty() - host }

    suspend fun allowPopups(origin: String) = edit { it[K.popupOrigins] = it[K.popupOrigins].orEmpty() + origin }
    suspend fun revokePopups(origin: String) = edit { it[K.popupOrigins] = it[K.popupOrigins].orEmpty() - origin }

    suspend fun setAiProvider(v: AiProviderId) = edit { it[K.aiProvider] = v.name }

    suspend fun setModel(provider: AiProviderId, model: String): Boolean {
        if (!AiConfigValidation.isValidModel(model)) return false
        val key = when (provider) {
            AiProviderId.OPENAI -> K.openAiModel
            AiProviderId.ANTHROPIC -> K.anthropicModel
            AiProviderId.COMPATIBLE -> K.compatibleModel
            else -> return false
        }
        edit { it[key] = model.trim() }
        return true
    }

    suspend fun setCompatibleBaseUrl(raw: String): Boolean {
        val url = AiConfigValidation.normalizeBaseUrl(raw) ?: return false
        edit { it[K.compatibleBaseUrl] = url }
        return true
    }

    suspend fun setResponseDetail(v: ResponseDetail) = edit { it[K.responseDetail] = v.name }
}
