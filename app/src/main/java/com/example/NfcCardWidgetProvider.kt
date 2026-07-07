package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.NfcProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NfcCardWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ACTIVATE_PROFILE = "com.example.action.ACTIVATE_PROFILE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_PROFILE_TITLE = "extra_profile_title"
        const val EXTRA_PROFILE_URL = "extra_profile_url"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform widget updates on a background thread because of Room database queries
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val dao = db.nfcProfileDao()
            val profiles = try {
                dao.getAllProfiles().first()
            } catch (e: Exception) {
                emptyList()
            }

            val sharedPrefs = context.getSharedPreferences("nfc_prefs", Context.MODE_PRIVATE)
            val activeId = sharedPrefs.getInt("active_profile_id", -1)
            val activeProfile = profiles.find { it.id == activeId }
            val isEmulating = sharedPrefs.getBoolean("emulation_enabled", true)

            // Take the first 3 alternative profiles (not current active)
            val alternatives = profiles.filter { it.id != activeId }.take(3)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)

                // 1. Update Active Card Details
                if (activeProfile != null) {
                    views.setViewVisibility(R.id.widget_active_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_active_title, activeProfile.title)
                    views.setTextViewText(R.id.widget_active_url, activeProfile.url)
                    if (isEmulating) {
                        views.setTextViewText(R.id.widget_status, "● HCE BROADCASTING")
                    } else {
                        views.setTextViewText(R.id.widget_status, "● EMULATION PAUSED")
                    }
                } else if (profiles.isNotEmpty()) {
                    // Set first one as active fallback if nothing selected
                    val fallback = profiles[0]
                    views.setViewVisibility(R.id.widget_active_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_active_title, fallback.title)
                    views.setTextViewText(R.id.widget_active_url, fallback.url)
                    views.setTextViewText(R.id.widget_status, "● TAP A CARD BELOW")
                } else {
                    // No cards exist
                    views.setViewVisibility(R.id.widget_active_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_active_title, "No Profiles Added")
                    views.setTextViewText(R.id.widget_active_url, "Open the app to add your first URL card")
                    views.setTextViewText(R.id.widget_status, "● INACTIVE")
                }

                // 2. Update Alternatives List (up to 3 items)
                val itemContainers = arrayOf(
                    R.id.widget_item_container_1,
                    R.id.widget_item_container_2,
                    R.id.widget_item_container_3
                )
                val itemTitles = arrayOf(
                    R.id.widget_item_title_1,
                    R.id.widget_item_title_2,
                    R.id.widget_item_title_3
                )
                val itemUrls = arrayOf(
                    R.id.widget_item_url_1,
                    R.id.widget_item_url_2,
                    R.id.widget_item_url_3
                )

                // Show alternatives list label only if there are any alternative profiles
                if (alternatives.isEmpty()) {
                    views.setViewVisibility(R.id.widget_list_label, View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_list_label, View.VISIBLE)
                }

                for (i in 0 until 3) {
                    if (i < alternatives.size) {
                        val profile = alternatives[i]
                        views.setViewVisibility(itemContainers[i], View.VISIBLE)
                        views.setTextViewText(itemTitles[i], profile.title)
                        views.setTextViewText(itemUrls[i], profile.url)

                        // Set up tap action to activate this profile directly
                        val intent = Intent(context, NfcCardWidgetProvider::class.java).apply {
                            action = ACTION_ACTIVATE_PROFILE
                            putExtra(EXTRA_PROFILE_ID, profile.id)
                            putExtra(EXTRA_PROFILE_TITLE, profile.title)
                            putExtra(EXTRA_PROFILE_URL, profile.url)
                        }

                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            profile.id, // unique requestCode to prevent bundling override
                            intent,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            } else {
                                PendingIntent.FLAG_UPDATE_CURRENT
                            }
                        )
                        views.setOnClickPendingIntent(itemContainers[i], pendingIntent)
                    } else {
                        // Hide unused slots
                        views.setViewVisibility(itemContainers[i], View.GONE)
                    }
                }

                // Update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_ACTIVATE_PROFILE) {
            val profileId = intent.getIntExtra(EXTRA_PROFILE_ID, -1)
            val profileTitle = intent.getStringExtra(EXTRA_PROFILE_TITLE) ?: ""
            val profileUrl = intent.getStringExtra(EXTRA_PROFILE_URL) ?: ""

            if (profileId != -1) {
                // 1. Persist the active selection and enable emulation
                val sharedPrefs = context.getSharedPreferences("nfc_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putInt("active_profile_id", profileId)
                    .putString("active_profile_url", profileUrl)
                    .putBoolean("emulation_enabled", true)
                    .apply()

                // Also save to nfc_link_prefs / url for spec compliance
                val linkPrefs = context.getSharedPreferences("nfc_link_prefs", Context.MODE_PRIVATE)
                linkPrefs.edit()
                    .putString("url", profileUrl)
                    .apply()

                // Enable the HostApduService component dynamically
                try {
                    val pm = context.packageManager
                    val componentName = ComponentName(context, "com.example.NdefHostApduService")
                    pm.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Trigger rich physical haptic feedback (120ms sharp vibration)
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(120)
                    }
                } catch (e: Exception) {
                    // Fail gracefully if device lacks vibration motor
                }

                // 3. Display instant feedback Toast
                Toast.makeText(context, "Activated Profile: $profileTitle", Toast.LENGTH_SHORT).show()

                // 4. Force refresh the widget view immediately to update highlighted cards
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisAppWidget = ComponentName(context, NfcCardWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }
}
