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
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun log(message: String) {
        if (!SettingsManager.isLoggingEnabled) return
        Log.d("SponsorSkipper", message)
        val ctx = context ?: return
        try {
            val logFile = File(ctx.getExternalFilesDir(null), "skipper_logs.txt")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            FileWriter(logFile, true).use { it.append("$timestamp - $message\n") }
        } catch (e: Exception) {
            Log.e("SponsorSkipper", "Log write failed", e)
        }
    }

    fun clearLogs() {
        val ctx = context ?: return
        try {
            val logFile = File(ctx.getExternalFilesDir(null), "skipper_logs.txt")
            if (logFile.exists()) {
                logFile.writeText("") // Clears the file contents
                log("=== LOGS CLEARED ===")
            }
        } catch (e: Exception) {
            Log.e("SponsorSkipper", "Failed to clear logs", e)
        }
    }
}
