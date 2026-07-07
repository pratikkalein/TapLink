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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        _activeProfileId.value = repository.getActiveProfileId()
        checkNfcStatus()
        seedDatabaseIfEmpty()
    }

    private fun seedDatabaseIfEmpty() {
        viewModelScope.launch {
            val currentList = repository.getAllProfiles().first()
            if (currentList.isEmpty()) {
                val default1 = NfcProfile(title = "Google AI Studio", url = "https://ai.studio/build")
                val default2 = NfcProfile(title = "Google Search", url = "https://google.com")
                val default3 = NfcProfile(title = "Material Design 3", url = "https://m3.material.io")
                
                repository.insertProfile(default1)
                repository.insertProfile(default2)
                repository.insertProfile(default3)
                
                // Retrieve again to get auto-generated IDs and set the first one as active
                val seeded = repository.getAllProfiles().first()
                if (seeded.isNotEmpty()) {
                    setActiveProfile(seeded[0])
                }
            }
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

    fun addProfile(title: String, url: String) {
        viewModelScope.launch {
            val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val profile = NfcProfile(title = title, url = formattedUrl)
            repository.insertProfile(profile)
            updateWidgets()
        }
    }

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
