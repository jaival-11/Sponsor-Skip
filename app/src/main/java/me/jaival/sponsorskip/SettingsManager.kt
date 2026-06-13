/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright (C) 2026 Jaival
 */
package me.jaival.sponsorskip

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

object SettingsManager {
    var targetPackages: Set<String>
        get() = prefs.getStringSet("targetPackages", setOf("com.google.android.youtube")) ?: setOf("com.google.android.youtube")
        set(value) = prefs.edit().putStringSet("targetPackages", value).apply()

    private const val PREFS_NAME = "skipper_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        val targetContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val deviceContext = context.createDeviceProtectedStorageContext()
            deviceContext.moveSharedPreferencesFrom(context, PREFS_NAME)
            deviceContext
        } else {
            context
        }
        prefs = targetContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isPrivacyAccepted: Boolean
        get() = prefs.getBoolean("privacy_accepted", false)
        set(value) { prefs.edit().putBoolean("privacy_accepted", value).commit() }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_master_switch", true)
        set(value) { prefs.edit().putBoolean("service_master_switch", value).commit() }

    var isLoggingEnabled: Boolean
        get() = prefs.getBoolean("logging_enabled", false)
        set(value) { prefs.edit().putBoolean("logging_enabled", value).commit() }

    fun getSegmentAction(category: String): Int {
        return prefs.getInt("cat_$category", if (category == "sponsor") 1 else 0)
    }

    fun setSegmentAction(category: String, action: Int) {
        prefs.edit().putInt("cat_$category", action).commit()
    }

    var skippedCount: Int
        get() = prefs.getInt("stat_count", 0)
        set(value) { prefs.edit().putInt("stat_count", value).commit() }

    var timeSavedMs: Long
        get() = prefs.getLong("stat_time", 0L)
        set(value) { prefs.edit().putLong("stat_time", value).commit() }

    var minSegmentDuration: Float
        get() = prefs.getFloat("min_segment_duration", 0f)
        set(value) { prefs.edit().putFloat("min_segment_duration", value).commit() }

    fun getPreReleaseSetting(context: Context): Boolean {
        if (prefs.contains("pre_release_updates")) {
            return prefs.getBoolean("pre_release_updates", false)
        }
        val vName = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch(e:Exception){""}
        return vName?.contains("dev") == true
    }

    fun setPreReleaseSetting(value: Boolean) {
        prefs.edit().putBoolean("pre_release_updates", value).commit()
    }
}
