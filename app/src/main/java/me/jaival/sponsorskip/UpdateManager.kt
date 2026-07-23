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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

object UpdateManager {
    private val client = OkHttpClient()

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val rClean = remote.removePrefix("v").trim()
        val lClean = local.removePrefix("v").trim()

        val rParts = rClean.split("-", limit = 2)
        val lParts = lClean.split("-", limit = 2)

        val rMain = rParts[0].split(".").map { it.toIntOrNull() ?: 0 }
        val lMain = lParts[0].split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(rMain.size, lMain.size)
        for (i in 0 until length) {
            val rVal = rMain.getOrElse(i) { 0 }
            val lVal = lMain.getOrElse(i) { 0 }
            if (rVal > lVal) return true
            if (rVal < lVal) return false
        }

        val rHasPre = rParts.size > 1
        val lHasPre = lParts.size > 1

        if (!rHasPre && lHasPre) return true 
        if (rHasPre && !lHasPre) return false 

        if (rHasPre && lHasPre) {
            val rPre = rParts[1].split(".")
            val lPre = lParts[1].split(".")
            val pLen = maxOf(rPre.size, lPre.size)
            for (i in 0 until pLen) {
                val rp = rPre.getOrElse(i) { "" }
                val lp = lPre.getOrElse(i) { "" }
                if (rp == lp) continue
                
                val rInt = rp.toIntOrNull()
                val lInt = lp.toIntOrNull()
                if (rInt != null && lInt != null) return rInt > lInt
                return rp > lp
            }
        }
        return false
    }

    suspend fun checkUpdate(context: Context, manual: Boolean) {
        if (!manual) {
            if (!SettingsManager.isAutoUpdateCheckEnabled) {
                AppLogger.log("[UPDATER] Skipped app-open check: Auto check for updates is disabled in settings.")
                return
            }
            val lastCheck = SettingsManager.lastCheckTime
            val now = System.currentTimeMillis()
            if (now - lastCheck < 4 * 60 * 60 * 1000L) {
                AppLogger.log("[UPDATER] Skipped app-open check: checked within last 4 hours (${(now - lastCheck) / 1000 / 60} mins ago).")
                return
            }
        }

        try {
            val currentVersionRaw = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            val currentVersion = currentVersionRaw.removePrefix("v").trim()
            val includePreRelease = SettingsManager.getPreReleaseSetting(context)

            AppLogger.log("[UPDATER] Local App Version: '$currentVersion' | Pre-release Allowed: $includePreRelease")

            val req = Request.Builder().url("https://api.github.com/repos/jaival-11/Sponsor-Skip/releases").header("User-Agent", "Sponsor-Skip").build()
            val res = withContext(Dispatchers.IO) { client.newCall(req).execute() }

            if (!res.isSuccessful) {
                AppLogger.log("[UPDATER] HTTP check failed: ${res.code}")
                if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "Repo not found or no releases yet.", Toast.LENGTH_SHORT).show() }
                return
            }

            SettingsManager.lastCheckTime = System.currentTimeMillis()

            val jsonString = res.body?.string() ?: "[]"
            val jsonArray = JSONArray(jsonString)
            
            var latestTag = ""
            var latestUrl = ""

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val isPre = obj.optBoolean("prerelease", false)
                val branch = obj.optString("target_commitish", "")
                val tagName = obj.optString("tag_name", "")
                
                val isPreReleaseEntity = isPre || branch == "dev" || tagName.contains("dev")

                if (!includePreRelease && isPreReleaseEntity) continue

                val tag = tagName.removePrefix("v").trim()
                if (tag.isNotBlank()) {
                    if (latestTag.isEmpty() || isNewerVersion(tag, latestTag)) {
                        latestTag = tag
                        val assets = obj.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            latestUrl = assets.optJSONObject(0)?.optString("browser_download_url") ?: ""
                        } else {
                            latestUrl = "" 
                        }
                    }
                }
            }
            
            AppLogger.log("[UPDATER] Evaluated Latest Remote Tag: '$latestTag'")

            val isNewer = if (latestTag.isNotBlank()) isNewerVersion(latestTag, currentVersion) else false
            AppLogger.log("[UPDATER] Is Remote ('$latestTag') mathematically newer than Local ('$currentVersion')? $isNewer")

            if (latestTag.isNotBlank() && isNewer) {
                if (latestUrl.isNotBlank()) {
                    SettingsManager.pendingUpdateTag = latestTag
                    SettingsManager.pendingUpdateUrl = latestUrl
                    withContext(Dispatchers.Main) { showUpdateDialog(context, latestTag, latestUrl, currentVersion) }
                } else {
                    AppLogger.log("[UPDATER] ABORT: Newer release found, but it has no APK file attached.")
                    if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "Release found but no APK attached.", Toast.LENGTH_SHORT).show() }
                }
            } else {
                if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "You are on the latest version!", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            AppLogger.log("[UPDATER] FATAL ERROR: ${e.message}")
            if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "Error checking update.", Toast.LENGTH_SHORT).show() }
        }
    }

    suspend fun checkUpdateBackground(context: Context): Boolean {
        if (!SettingsManager.isAutoUpdateCheckEnabled) {
            AppLogger.log("[UPDATER] Skipped 24h background check: Auto check for updates is disabled in settings.")
            return true
        }
        try {
            val currentVersionRaw = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
            val currentVersion = currentVersionRaw.removePrefix("v").trim()
            val includePreRelease = SettingsManager.getPreReleaseSetting(context)

            AppLogger.log("[UPDATER] 24h Background Check - Local App Version: '$currentVersion'")

            val req = Request.Builder().url("https://api.github.com/repos/jaival-11/Sponsor-Skip/releases").header("User-Agent", "Sponsor-Skip").build()
            val res = withContext(Dispatchers.IO) { client.newCall(req).execute() }

            if (!res.isSuccessful) {
                AppLogger.log("[UPDATER] 24h Background Check failed: HTTP ${res.code}")
                return false
            }

            SettingsManager.lastCheckTime = System.currentTimeMillis()

            val jsonString = res.body?.string() ?: "[]"
            val jsonArray = JSONArray(jsonString)
            
            var latestTag = ""
            var latestUrl = ""

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val isPre = obj.optBoolean("prerelease", false)
                val branch = obj.optString("target_commitish", "")
                val tagName = obj.optString("tag_name", "")
                
                val isPreReleaseEntity = isPre || branch == "dev" || tagName.contains("dev")

                if (!includePreRelease && isPreReleaseEntity) continue

                val tag = tagName.removePrefix("v").trim()
                if (tag.isNotBlank()) {
                    if (latestTag.isEmpty() || isNewerVersion(tag, latestTag)) {
                        latestTag = tag
                        val assets = obj.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            latestUrl = assets.optJSONObject(0)?.optString("browser_download_url") ?: ""
                        } else {
                            latestUrl = "" 
                        }
                    }
                }
            }

            val isNewer = if (latestTag.isNotBlank()) isNewerVersion(latestTag, currentVersion) else false
            AppLogger.log("[UPDATER] 24h Background Check - Latest Tag: '$latestTag', Is Newer: $isNewer")

            if (latestTag.isNotBlank() && isNewer && latestUrl.isNotBlank()) {
                SettingsManager.pendingUpdateTag = latestTag
                SettingsManager.pendingUpdateUrl = latestUrl
                postUpdateNotification(context, latestTag, latestUrl)
            }

            return true
        } catch (e: Exception) {
            AppLogger.log("[UPDATER] 24h Background Check error: ${e.message}")
            return false
        }
    }

    fun postUpdateNotification(context: Context, tag: String, apkUrl: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "update_channel"
        val notifId = 1002

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_SHOW_UPDATE", true)
            putExtra("EXTRA_UPDATE_TAG", tag)
            putExtra("EXTRA_UPDATE_URL", apkUrl)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = "Update to version $tag is available, update now"
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update available")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            notificationManager.notify(notifId, builder.build())
            AppLogger.log("[UPDATER] Posted notification for update: version $tag")
        } catch (e: Exception) {
            AppLogger.log("[UPDATER] Failed to post update notification: ${e.message}")
        }
    }

    fun showUpdateDialog(context: Context, tag: String, apkUrl: String, currentVersion: String) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("New version available")
            .setMessage("Current app version is v$currentVersion. An update to v$tag is available. Open changelog to see changes")
            .setCancelable(false)
            .setPositiveButton("Download") { _, _ ->
                SettingsManager.pendingUpdateTag = ""
                SettingsManager.pendingUpdateUrl = ""
                Toast.makeText(context, "Downloading v$tag, check notification for progress", Toast.LENGTH_LONG).show()
                CoroutineScope(Dispatchers.IO).launch { downloadAndInstall(context, apkUrl, tag) }
            }
            .setNegativeButton("Later") { _, _ ->
                SettingsManager.pendingUpdateTag = ""
                SettingsManager.pendingUpdateUrl = ""
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        context.getExternalFilesDir(null)?.listFiles()?.forEach { 
                            if (it.name.endsWith(".apk")) it.delete() 
                        }
                    } catch (e: Exception) {}
                }
            }
            .setNeutralButton("Changelog", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jaival-11/Sponsor-Skip/releases/tag/v$tag"))
                context.startActivity(intent)
            }
        }
        dialog.show()
    }

    private suspend fun downloadAndInstall(context: Context, url: String, version: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "update_channel"
        val notifId = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading Sponsor Skip v$version")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        try {
            val req = Request.Builder().url(url).build()
            val res = withContext(Dispatchers.IO) { client.newCall(req).execute() }
            val file = File(context.getExternalFilesDir(null), "update_$version.apk")

            withContext(Dispatchers.IO) {
                val body = res.body ?: throw Exception("Empty body")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(8 * 1024)
                var bytesCopied = 0L
                var bytes = inputStream.read(buffer)
                var lastUpdateTime = 0L

                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytesCopied += bytes

                    if (totalBytes > 0) {
                        val progress = (bytesCopied * 100 / totalBytes).toInt()
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime > 500) {
                            builder.setProgress(100, progress, false)
                            notificationManager.notify(notifId, builder.build())
                            lastUpdateTime = currentTime
                        }
                    }
                    bytes = inputStream.read(buffer)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
            }

            notificationManager.cancel(notifId)

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            notificationManager.cancel(notifId)
            withContext(Dispatchers.Main) { Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show() }
        }
    }
}
