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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
}
