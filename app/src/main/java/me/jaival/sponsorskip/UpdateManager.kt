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
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object UpdateManager {
    const val CURRENT_VERSION = "1.0.7"
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
                        Toast.makeText(context, "Downloading v$tag...", Toast.LENGTH_SHORT).show()
                    }
                    downloadAndInstall(context, apkUrl, tag)
                }
            } else {
                if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "You are on the latest version!", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            if (manual) withContext(Dispatchers.Main) { Toast.makeText(context, "Error checking update.", Toast.LENGTH_SHORT).show() }
        }
    }

    private suspend fun downloadAndInstall(context: Context, url: String, version: String) {
        try {
            val req = Request.Builder().url(url).build()
            val res = withContext(Dispatchers.IO) { client.newCall(req).execute() }
            val file = File(context.getExternalFilesDir(null), "update_$version.apk")
            
            withContext(Dispatchers.IO) {
                val fos = FileOutputStream(file)
                fos.write(res.body?.bytes())
                fos.close()
            }

            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show() }
        }
    }
}
