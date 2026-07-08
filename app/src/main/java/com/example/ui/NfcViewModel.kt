package com.example.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NfcCardWidgetProvider
import com.example.data.AppDatabase
import com.example.data.NfcProfile
import com.example.data.NfcRepository
import com.example.NdefHostApduService
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern
import java.util.concurrent.TimeUnit

class NfcViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NfcRepository
    val profiles: StateFlow<List<NfcProfile>>

    private val _activeProfileId = MutableStateFlow(-1)
    val activeProfileId: StateFlow<Int> = _activeProfileId.asStateFlow()

    private val _isNfcEnabled = MutableStateFlow(false)
    val isNfcEnabled: StateFlow<Boolean> = _isNfcEnabled.asStateFlow()

    private val _isNfcSupported = MutableStateFlow(true)
    val isNfcSupported: StateFlow<Boolean> = _isNfcSupported.asStateFlow()

    private val _isEmulating = MutableStateFlow(true)
    val isEmulating: StateFlow<Boolean> = _isEmulating.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.AUTO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(application)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NfcRepository(database.nfcProfileDao(), application)
        
        profiles = repository.getAllProfiles().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        val sharedPrefs = application.getSharedPreferences("nfc_prefs", Context.MODE_PRIVATE)
        _isEmulating.value = sharedPrefs.getBoolean("emulation_enabled", true)
        _themeMode.value = runCatching {
            ThemeMode.valueOf(sharedPrefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name)
        }.getOrDefault(ThemeMode.AUTO)
        _dynamicColor.value = sharedPrefs.getBoolean("dynamic_color", true)
        _hapticsEnabled.value = sharedPrefs.getBoolean("haptics_enabled", true)
        _activeProfileId.value = repository.getActiveProfileId()
        checkNfcStatus()
        loadCategories()
    }

    // ───────────── Categories (stored as an ordered list in prefs) ─────────────

    private fun loadCategories() {
        val stored = prefs().getString("categories", null)
        _categories.value = if (stored != null) {
            parseCategories(stored)
        } else {
            // Seed only "Social" on first run.
            listOf("Social").also { persistCategories(it) }
        }
    }

    private fun parseCategories(json: String): List<String> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        listOf("Social")
    }

    private fun persistCategories(list: List<String>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        prefs().edit().putString("categories", arr.toString()).apply()
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _categories.value
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return
        val updated = current + trimmed
        _categories.value = updated
        persistCategories(updated)
    }

    fun deleteCategory(name: String) {
        val updated = _categories.value.filterNot { it == name }
        _categories.value = updated
        persistCategories(updated)
    }

    fun reorderCategories(newOrder: List<String>) {
        _categories.value = newOrder
        persistCategories(newOrder)
    }

    private fun prefs() =
        getApplication<Application>().getSharedPreferences("nfc_prefs", Context.MODE_PRIVATE)

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs().edit().putString("theme_mode", mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs().edit().putBoolean("dynamic_color", enabled).apply()
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs().edit().putBoolean("haptics_enabled", enabled).apply()
    }

    fun clearAllTags() {
        viewModelScope.launch {
            repository.clearAll()
            _activeProfileId.value = -1
            val context = getApplication<Application>()
            context.getSharedPreferences("nfc_prefs", Context.MODE_PRIVATE).edit()
                .remove("active_profile_id")
                .remove("active_profile_url")
                .apply()
            context.getSharedPreferences("nfc_link_prefs", Context.MODE_PRIVATE).edit()
                .remove("url")
                .apply()
            updateWidgets()
        }
    }

    fun checkNfcStatus() {
        if (nfcAdapter == null) {
            _isNfcSupported.value = false
            _isNfcEnabled.value = false
        } else {
            _isNfcSupported.value = true
            _isNfcEnabled.value = nfcAdapter.isEnabled
        }
    }

    fun addProfile(title: String, url: String, category: String = "Social") {
        viewModelScope.launch {
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            
            // Fetch OpenGraph data
            val ogData = fetchOpenGraphData(formattedUrl)
            
            val finalTitle = when {
                title.isNotBlank() -> title
                ogData.title.isNotBlank() -> ogData.title
                else -> {
                    // fallback to domain name
                    try {
                        val uri = java.net.URI(formattedUrl)
                        var domain = uri.host ?: formattedUrl
                        if (domain.startsWith("www.")) {
                            domain = domain.substring(4)
                        }
                        domain.substringBefore('.').replaceFirstChar { it.uppercase() }
                    } catch (e: Exception) {
                        formattedUrl
                    }
                }
            }
            
            val profile = NfcProfile(
                title = finalTitle,
                url = formattedUrl,
                description = ogData.description,
                imageUrl = ogData.imageUrl,
                category = category
            )
            repository.insertProfile(profile)
            updateWidgets()
        }
    }

    private suspend fun fetchOpenGraphData(url: String): OpenGraphData {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""
                        
                        val ogTitle = extractMetaTag(html, "og:title")
                        val twitterTitle = extractMetaTag(html, "twitter:title")
                        val title = ogTitle ?: twitterTitle ?: extractTitleTag(html) ?: ""
                        
                        val description = extractMetaTag(html, "og:description") 
                            ?: extractMetaTag(html, "twitter:description")
                            ?: extractMetaTag(html, "description")
                        
                        // Try og:image -> twitter:image -> apple-touch-icon -> shortcut icon -> standard icon
                        val rawImageUrl = extractMetaTag(html, "og:image")
                            ?: extractMetaTag(html, "twitter:image")
                            ?: extractMetaTag(html, "twitter:image:src")
                            ?: extractLinkTag(html, "apple-touch-icon")
                            ?: extractLinkTag(html, "apple-touch-icon-precomposed")
                            ?: extractLinkTag(html, "shortcut icon")
                            ?: extractLinkTag(html, "icon")
                        
                        val imageUrl = resolveUrl(url, rawImageUrl)
                        
                        OpenGraphData(title.trim(), description?.trim(), imageUrl)
                    } else {
                        OpenGraphData("", null, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                OpenGraphData("", null, null)
            }
        }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String?): String? {
        if (relativeUrl == null || relativeUrl.isBlank()) return null
        val trimmed = relativeUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            val baseUri = java.net.URI(baseUrl)
            val scheme = baseUri.scheme ?: "https"
            if (trimmed.startsWith("//")) {
                "$scheme:$trimmed"
            } else if (trimmed.startsWith("/")) {
                val host = baseUri.host ?: ""
                val port = if (baseUri.port != -1) ":${baseUri.port}" else ""
                "$scheme://$host$port$trimmed"
            } else {
                val path = baseUri.path ?: ""
                val directory = if (path.endsWith("/")) path else path.substringBeforeLast('/', "/")
                val host = baseUri.host ?: ""
                val port = if (baseUri.port != -1) ":${baseUri.port}" else ""
                val normalizedPath = if (directory.endsWith("/")) "$directory$trimmed" else {
                    if (directory.isEmpty()) "/$trimmed" else "$directory/$trimmed"
                }
                "$scheme://$host$port$normalizedPath"
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    private fun extractLinkTag(html: String, relValue: String): String? {
        val patterns = listOf(
            Pattern.compile("<link\\s+[^>]*rel=[\"']$relValue[\"']\\s+[^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<link\\s+[^>]*href=[\"']([^\"']+)[\"']\\s+[^>]*rel=[\"']$relValue[\"']", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val raw = matcher.group(1) ?: ""
                return raw
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
            }
        }
        return null
    }

    private fun extractMetaTag(html: String, property: String): String? {
        val patterns = listOf(
            Pattern.compile("<meta\\s+[^>]*property=[\"']$property[\"']\\s+[^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"']\\s+[^>]*property=[\"']$property[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*name=[\"']$property[\"']\\s+[^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"']\\s+[^>]*name=[\"']$property[\"']", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val raw = matcher.group(1) ?: ""
                // Decode simple HTML entities if any
                return raw
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
            }
        }
        return null
    }

    private fun extractTitleTag(html: String): String? {
        val pattern = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val raw = matcher.group(1) ?: ""
            return raw
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }
        return null
    }

    private data class OpenGraphData(val title: String, val description: String?, val imageUrl: String?)

    fun deleteProfile(profile: NfcProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            _activeProfileId.value = repository.getActiveProfileId()
            
            // Also update raw preference cache for service
            val context = getApplication<Application>()
            val sharedPrefs = context.getSharedPreferences("nfc_prefs", android.content.Context.MODE_PRIVATE)
            if (sharedPrefs.getInt("active_profile_id", -1) == profile.id || sharedPrefs.getInt("active_profile_id", -1) == -1) {
                sharedPrefs.edit()
                    .remove("active_profile_id")
                    .remove("active_profile_url")
                    .apply()

                val linkPrefs = context.getSharedPreferences("nfc_link_prefs", android.content.Context.MODE_PRIVATE)
                linkPrefs.edit()
                    .remove("url")
                    .apply()
            }
            updateWidgets()
        }
    }

    fun setActiveProfile(profile: NfcProfile) {
        viewModelScope.launch {
            repository.setActiveProfileId(profile.id)
            _activeProfileId.value = profile.id
            
            // Save to shared preference cache for the HCE Service
            val context = getApplication<Application>()
            val sharedPrefs = context.getSharedPreferences("nfc_prefs", android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putInt("active_profile_id", profile.id)
                .putString("active_profile_url", profile.url)
                .apply()

            // Also save to nfc_link_prefs / url for spec compliance
            val linkPrefs = context.getSharedPreferences("nfc_link_prefs", android.content.Context.MODE_PRIVATE)
            linkPrefs.edit()
                .putString("url", profile.url)
                .apply()

            updateWidgets()
        }
    }

    fun setEmulationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _isEmulating.value = enabled
            val context = getApplication<Application>()
            val sharedPrefs = context.getSharedPreferences("nfc_prefs", android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putBoolean("emulation_enabled", enabled)
                .apply()

            // Enable or disable the HCE service component dynamically
            try {
                val pm = context.packageManager
                val componentName = ComponentName(context, NdefHostApduService::class.java)
                val newState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    componentName,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            updateWidgets()
        }
    }

    private fun updateWidgets() {
        try {
            val context = getApplication<Application>()
            val intent = Intent(context, NfcCardWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context, NfcCardWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
