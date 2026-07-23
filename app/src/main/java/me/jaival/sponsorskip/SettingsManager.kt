/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright (C) 2026 Jaival
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>
 */

package me.jaival.sponsorskip

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

object SettingsManager {
    var isForegroundEnabled: Boolean
        get() = prefs.getBoolean("foreground_service_enabled", false)
        set(value) { 
            prefs.edit().putBoolean("foreground_service_enabled", value).commit()
            syncForegroundService()
        }

    var isStrictSearchEnabled: Boolean
        get() = prefs.getBoolean("strict_search_enabled", false)
        set(value) = prefs.edit().putBoolean("strict_search_enabled", value).apply()

    var isSkipCountTrackingEnabled: Boolean
        get() = prefs.getBoolean("skip_count_tracking_enabled", false)
        set(value) { prefs.edit().putBoolean("skip_count_tracking_enabled", value).commit() }

    var isAutoUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean("auto_update_check_enabled", true)
        set(value) {
            prefs.edit().putBoolean("auto_update_check_enabled", value).commit()
            if (::appContext.isInitialized) {
                if (value) {
                    UpdateCheckWorker.schedule(appContext)
                } else {
                    UpdateCheckWorker.cancel(appContext)
                }
            }
        }


    const val SPOTIFY_PACKAGE = "com.spotify.music"

    var targetPackages: Set<String>
        get() = prefs.getStringSet("targetPackages", setOf("com.google.android.youtube")) ?: setOf("com.google.android.youtube")
        set(value) = prefs.edit().putStringSet("targetPackages", value).apply()

    private const val PREFS_NAME = "skipper_prefs"
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    fun syncForegroundService() {
        if (::appContext.isInitialized) {
            val intent = android.content.Intent(appContext, SkipperForegroundService::class.java)
            if (isForegroundEnabled && isServiceEnabled) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        appContext.startForegroundService(intent)
                    } else {
                        appContext.startService(intent)
                    }
                } catch (e: Exception) {
                    AppLogger.log("[SERVICE] Failed to start foreground service: ${e.message}")
                }
            } else {
                try {
                    appContext.stopService(intent)
                } catch (e: Exception) {
                    AppLogger.log("[SERVICE] Failed to stop foreground service: ${e.message}")
                }
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val deviceContext = context.createDeviceProtectedStorageContext()
            deviceContext.moveSharedPreferencesFrom(context, PREFS_NAME)
            deviceContext
        } else { context }
        prefs = targetContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        syncForegroundService()
    }

    var isPrivacyAccepted: Boolean
        get() = prefs.getBoolean("privacy_accepted", false)
        set(value) { prefs.edit().putBoolean("privacy_accepted", value).commit() }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_master_switch", true)
        set(value) {
            prefs.edit().putBoolean("service_master_switch", value).commit()
            syncForegroundService()
            if (::appContext.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    android.service.quicksettings.TileService.requestListeningState(
                        appContext,
                        android.content.ComponentName(appContext, SponsorTileService::class.java)
                    )
                } catch (e: Exception) {}
            }
        }

    var isLoggingEnabled: Boolean
        get() = prefs.getBoolean("logging_enabled", false)
        set(value) { prefs.edit().putBoolean("logging_enabled", value).commit() }

    fun getSegmentAction(category: String): Int = prefs.getInt("cat_$category", if (category == "sponsor") 1 else 0)
    fun setSegmentAction(category: String, action: Int) { prefs.edit().putInt("cat_$category", action).commit() }

    var skippedCount: Int
        get() = prefs.getInt("stat_count", 0)
        set(value) { prefs.edit().putInt("stat_count", value).commit() }

    var timeSavedMs: Long
        get() = prefs.getLong("stat_time", 0L)
        set(value) { prefs.edit().putLong("stat_time", value).commit() }

    var minSegmentDuration: Float
        get() = prefs.getFloat("min_segment_duration", 0f)
        set(value) { prefs.edit().putFloat("min_segment_duration", value).commit() }

    var skipOffset: Float
        get() = if (prefs.contains("skip_offset")) prefs.getFloat("skip_offset", 0f) else prefs.getFloat("sync_offset", 0f)
        set(value) { prefs.edit().putFloat("skip_offset", value).commit() }

    fun getPreReleaseSetting(context: Context): Boolean {
        val vName = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch(e: Exception) { "" }
        if (vName?.contains("dev") == true) {
            if (prefs.getBoolean("pre_release_updates", false) != true) {
                setPreReleaseSetting(true)
                AppLogger.log("[SETTINGS] Pre-release version detected ('$vName'). Auto-enabled and saved pre-release updates toggle.")
            }
            return true
        }
        if (prefs.contains("pre_release_updates")) return prefs.getBoolean("pre_release_updates", false)
        return false
    }

    fun setPreReleaseSetting(value: Boolean) { 
        prefs.edit().putBoolean("pre_release_updates", value).commit() 
        AppLogger.log("[SETTINGS] Pre-release updates setting set to $value")
    }

    var lastCheckTime: Long
        get() = prefs.getLong("last_update_check_time", 0L)
        set(value) { prefs.edit().putLong("last_update_check_time", value).apply() }

    var pendingUpdateTag: String
        get() = prefs.getString("pending_update_tag", "") ?: ""
        set(value) { prefs.edit().putString("pending_update_tag", value).apply() }

    var pendingUpdateUrl: String
        get() = prefs.getString("pending_update_url", "") ?: ""
        set(value) { prefs.edit().putString("pending_update_url", value).apply() }

    var isSpotEnabled: Boolean
        get() = prefs.getBoolean("spot_master_switch", false) // Default OFF per instructions
        set(value) { prefs.edit().putBoolean("spot_master_switch", value).commit() }

    fun exportSettingsJson(): String {
        val json = org.json.JSONObject()
        // Explicitly include statistics and update preferences
        json.put("stat_count", skippedCount)
        json.put("stat_time", timeSavedMs)
        json.put("auto_update_check_enabled", isAutoUpdateCheckEnabled)
        val preRelease = if (::appContext.isInitialized) getPreReleaseSetting(appContext) else prefs.getBoolean("pre_release_updates", false)
        json.put("pre_release_updates", preRelease)

        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            when (value) {
                is Set<*> -> {
                    val array = org.json.JSONArray()
                    for (item in value) {
                        array.put(item)
                    }
                    json.put(key, array)
                }
                else -> json.put(key, value)
            }
        }
        AppLogger.log("[BACKUP] Exported ${json.length()} keys including statistics and update preferences (autoUpdate: $isAutoUpdateCheckEnabled, preRelease: $preRelease)")
        return json.toString(4)
    }

    fun importSettingsJson(jsonStr: String): Boolean {
        return try {
            val json = org.json.JSONObject(jsonStr)
            val editor = prefs.edit()
            val keys = json.keys()
            var count = 0
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is org.json.JSONArray -> {
                        val set = mutableSetOf<String>()
                        for (i in 0 until value.length()) {
                            set.add(value.getString(i))
                        }
                        editor.putStringSet(key, set)
                    }
                    is Number -> {
                        if (key == "min_segment_duration" || key == "skip_offset" || key == "sync_offset") {
                            editor.putFloat(key, value.toFloat())
                        } else if (key == "stat_time") {
                            editor.putLong(key, value.toLong())
                        } else if (key == "stat_count" || key.startsWith("cat_")) {
                            editor.putInt(key, value.toInt())
                        } else {
                            if (value is Double || value is Float) {
                                editor.putFloat(key, value.toFloat())
                            } else if (value is Long) {
                                editor.putLong(key, value.toLong())
                            } else {
                                editor.putInt(key, value.toInt())
                            }
                        }
                    }
                }
                count++
            }
            editor.commit()
            syncForegroundService()
            if (::appContext.isInitialized) {
                if (isAutoUpdateCheckEnabled) {
                    UpdateCheckWorker.schedule(appContext)
                } else {
                    UpdateCheckWorker.cancel(appContext)
                }
                appContext.sendBroadcast(android.content.Intent("me.jaival.sponsorskip.STATS_UPDATED"))
            }
            val restoredPreRelease = if (::appContext.isInitialized) getPreReleaseSetting(appContext) else prefs.getBoolean("pre_release_updates", false)
            AppLogger.log("[BACKUP] Restored $count keys including statistics and update preferences (autoUpdate: $isAutoUpdateCheckEnabled, preRelease: $restoredPreRelease)")
            true
        } catch (e: Exception) {
            AppLogger.log("[BACKUP] Error importing settings and statistics: ${e.message}")
            false
        }
    }
}
