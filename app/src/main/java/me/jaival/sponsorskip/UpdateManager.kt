/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright (C) 2026 Jaival
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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object UpdateManager {
    const val CURRENT_VERSION = "1.0.9"
    private val client = OkHttpClient()

    suspend fun checkUpdate(context: Context, manual: Boolean) {
        try {
            val req = Request.Builder().url("https://codeberg.org/api/v1/repos/jaival/Sponsor-Skip/releases/latest").build()
            val res = withContext(Dispatchers.IO) { client.newCall(req).execute() }

            if (!res.isSuccessful) {
                if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "Repo not found or no releases yet.", Toast.LENGTH_SHORT).show() }
                return
            }

            val json = JSONObject(res.body?.string() ?: "")
            val tag = json.optString("tag_name", "").replace("v", "")

            if (tag.isNotBlank() && tag != CURRENT_VERSION) {
                val apkUrl = json.optJSONArray("assets")?.optJSONObject(0)?.optString("browser_download_url")
                if (apkUrl != null) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(context, tag, apkUrl)
                    }
                }
            } else {
                if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "You are on the latest version!", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "Error checking update.", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showUpdateDialog(context: Context, tag: String, apkUrl: String) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("New version available")
            .setMessage("Current app version is $CURRENT_VERSION. An update to v$tag is available. Open changelog to see changes")
            .setCancelable(false)
            .setPositiveButton("Download") { _, _ ->
                Toast.makeText(context, "Downloading v$tag, check notification for progress", Toast.LENGTH_LONG).show()
                CoroutineScope(Dispatchers.IO).launch { downloadAndInstall(context, apkUrl, tag) }
            }
            .setNegativeButton("Later", null)
            .setNeutralButton("Changelog", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip/releases/tag/v$tag"))
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
                        // Update UI a maximum of twice per second to prevent notification spam lag
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

            // Remove progress notification once download finishes
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
