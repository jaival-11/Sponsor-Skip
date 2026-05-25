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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.widget.Toast
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

data class Segment(val startMs: Long, val endMs: Long, val category: String)

class MediaNotificationService : NotificationListenerService() {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var skipSegments = mutableListOf<Segment>()
    private var ytController: MediaController? = null
    private var trackingJob: Job? = null
    private var sessionManager: MediaSessionManager? = null
    private var currentTitle = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (!SettingsManager.isServiceEnabled) return
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""

            if (title.isNotBlank() && title != currentTitle) {
                currentTitle = title
                AppLogger.log("[SERVICE] === METADATA DETECTED ===")
                AppLogger.log("[SERVICE] Extracted Title: '$title'")
                scope.launch { fetchSegmentsAndTrack(title) }
            }
        }
    }

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "me.jaival.sponsorskip.TOGGLE_SERVICE") {
                if (!SettingsManager.isServiceEnabled) {
                    AppLogger.log("[SERVICE] KILL SIGNAL RECEIVED. Wiping memory and unregistering hooks.")
                    trackingJob?.cancel()
                    ytController?.unregisterCallback(callback)
                    ytController = null
                    currentTitle = ""
                    skipSegments.clear()
                } else {
                    AppLogger.log("[SERVICE] REVIVE SIGNAL RECEIVED. Re-attaching hooks to Android System.")
                    val component = ComponentName(this@MediaNotificationService, MediaNotificationService::class.java)
                    handleSessions(sessionManager?.getActiveSessions(component))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        AppLogger.init(this)
        AppLogger.log("[SERVICE] NotificationListenerService onCreate() executed.")
        val filter = IntentFilter("me.jaival.sponsorskip.TOGGLE_SERVICE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppLogger.log("[SERVICE] System granted active listener connection.")
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MediaNotificationService::class.java)
        try {
            val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
                handleSessions(sessions)
            }
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            if (SettingsManager.isServiceEnabled) {
                AppLogger.log("[SERVICE] Checking for currently active media sessions...")
                handleSessions(sessionManager?.getActiveSessions(component))
            }
        } catch (e: Exception) {
            AppLogger.log("[ERROR] Failed to hook Media Router: ${e.message}")
        }
    }

    private fun handleSessions(sessions: List<MediaController>?) {
        if (!SettingsManager.isServiceEnabled) return
        
        // --- THE FIX IS HERE ---
        val newYtController = sessions?.find { SettingsManager.targetPackages.contains(it.packageName) }
        
        if (newYtController != null) {
            if (ytController?.sessionToken == newYtController.sessionToken) return
            AppLogger.log("[SERVICE] Hooked into MediaController (Token: ${newYtController.sessionToken}, Package: ${newYtController.packageName}).")
            ytController?.unregisterCallback(callback)
            ytController = newYtController
            ytController?.registerCallback(callback)
            callback.onMetadataChanged(ytController?.metadata)
        } else {
            if (ytController != null) {
                AppLogger.log("[SERVICE] Target playback closed or paused heavily. Detaching.")
                ytController?.unregisterCallback(callback)
                ytController = null
                currentTitle = ""
                trackingJob?.cancel()
                skipSegments.clear()
            }
        }
    }

    private suspend fun fetchSegmentsAndTrack(title: String) {
        try {
            AppLogger.log("[SCRAPER] Encoding title and firing background search request...")
            val query = URLEncoder.encode(title, "UTF-8")
            val searchReq = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$query")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            
            val searchRes = client.newCall(searchReq).execute()
            AppLogger.log("[SCRAPER] Search HTTP Response Code: ${searchRes.code}")
            val html = searchRes.body?.string() ?: ""
            AppLogger.log("[SCRAPER] Downloaded ${html.length} characters of HTML.")
            
            val regex = Regex("""/watch\?v=([a-zA-Z0-9_-]{11})""")
            val match = regex.find(html)

            if (match == null) {
                AppLogger.log("[SCRAPER ERROR] Regex failed to find a valid 11-character ID.")
                showToast("Error: Could not extract Video ID")
                return
            }

            val videoId = match.groupValues[1]
            AppLogger.log("[SCRAPER] Found Video ID: $videoId. Building SponsorBlock API Request...")
            
            val categoriesArr = """["sponsor","intro","outro","interaction","selfpromo","music_offtopic","preview","filler"]"""
            val encCategories = URLEncoder.encode(categoriesArr, "UTF-8")
            val apiUrl = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$encCategories"
            
            AppLogger.log("[API] Executing GET: $apiUrl")
            val sponsorReq = Request.Builder().url(apiUrl).build()
            val sponsorRes = client.newCall(sponsorReq).execute()
            AppLogger.log("[API] Response Code: ${sponsorRes.code}")

            if (!sponsorRes.isSuccessful) {
                AppLogger.log("[API] Request failed or no segments exist for this video.")
                showToast("No segments are there for the video")
                return
            }

            val sponsorJson = JSONArray(sponsorRes.body?.string() ?: "[]")
            AppLogger.log("[API] Successfully parsed JSON Array containing ${sponsorJson.length()} raw blocks.")
            
            skipSegments.clear()
            var configuredToSkipCount = 0

            for (i in 0 until sponsorJson.length()) {
                val obj = sponsorJson.getJSONObject(i)
                val segment = obj.getJSONArray("segment")
                val category = obj.getString("category")
                
                val action = SettingsManager.getSegmentAction(category)
                AppLogger.log("[PARSE] Evaluated block [$category]. User setting = $action")
                
                if (action == 1) {
                    configuredToSkipCount++
                }

                val start = (segment.getDouble(0) * 1000).toLong()
                val end = (segment.getDouble(1) * 1000).toLong()
                skipSegments.add(Segment(start, end, category))
            }

            if (skipSegments.isEmpty()) {
                AppLogger.log("[TRACKER] Array is empty after processing. Terminating loop.")
                showToast("No segments are there for the video")
            } else {
                AppLogger.log("[TRACKER] Engaging playback loop. Monitoring ${skipSegments.size} total blocks ($configuredToSkipCount armed for skip).")
                showToast("Loaded ${skipSegments.size} segments ($configuredToSkipCount to skip)")
                startTracking()
            }
        } catch (e: Exception) {
            AppLogger.log("[FATAL EXCEPTION] Trace: ${e.message}")
            showToast("Error fetching segments")
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        var lastLogTime = 0L

        trackingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val state = ytController?.playbackState
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    val pos = state.position
                    
                    if (System.currentTimeMillis() - lastLogTime > 15000) {
                        AppLogger.log("[TRACKER] Heartbeat - Playhead at $pos ms")
                        lastLogTime = System.currentTimeMillis()
                    }

                    val hitSegment = skipSegments.find { pos in it.startMs..it.endMs }

                    if (hitSegment != null) {
                        AppLogger.log("[TRACKER] ⚠️ CROSSED BOUNDARY: ${hitSegment.category.uppercase()} at $pos ms")
                        if (SettingsManager.getSegmentAction(hitSegment.category) == 1) { 
                            AppLogger.log("[TRACKER] Skip Authorized. Executing seekTo(${hitSegment.endMs})")
                            ytController?.transportControls?.seekTo(hitSegment.endMs)
                            
                            val savedMs = hitSegment.endMs - hitSegment.startMs
                            SettingsManager.skippedCount += 1
                            SettingsManager.timeSavedMs += savedMs
                            sendBroadcast(Intent("me.jaival.sponsorskip.STATS_UPDATED").setPackage(packageName))
                            
                            showToast("Skipped: ${hitSegment.category.uppercase()}")
                            skipSegments.remove(hitSegment)
                            delay(2000) 
                        } else {
                            AppLogger.log("[TRACKER] Skip Denied (Configured to Off). Removing from armed array.")
                            skipSegments.remove(hitSegment)
                        }
                    }
                }
                delay(1000)
            }
        }
    }
    
    private fun showToast(message: String) {
        mainHandler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        AppLogger.log("[SERVICE] NotificationListenerService destroyed by system.")
        unregisterReceiver(toggleReceiver)
        scope.cancel()
        super.onDestroy()
    }
}
