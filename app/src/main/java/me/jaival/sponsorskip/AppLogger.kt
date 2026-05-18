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
