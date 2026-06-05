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
import kotlin.math.max

data class Segment(val startMs: Long, val endMs: Long, val category: String)

class MediaNotificationService : NotificationListenerService() {
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var skipSegments = mutableListOf<Segment>()
    private var ytController: MediaController? = null
    private var trackingJob: Job? = null
    private var fetchJob: Job? = null
    private var sessionManager: MediaSessionManager? = null
    private var currentTitle = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        handleSessions(sessions)
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (!SettingsManager.isServiceEnabled) return
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
            val initialDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            if (title.isNotBlank() && title != currentTitle) {
                currentTitle = title
                AppLogger.log("[SERVICE] === METADATA DETECTED ===")
                
                fetchJob?.cancel()
                fetchJob = scope.launch {
                    if (ytController?.playbackState?.state != PlaybackState.STATE_PLAYING) {
                        AppLogger.log("[SERVICE] Title: '$title' is buffering or paused. Waiting for playback to begin...")
                        while (ytController?.playbackState?.state != PlaybackState.STATE_PLAYING && isActive) {
                            delay(400)
                        }
                        if (!isActive) {
                            AppLogger.log("[SERVICE] Fetch aborted: User skipped before video loaded.")
                            return@launch
                        }
                        AppLogger.log("[SERVICE] Playback fully started for '$title'.")
                    }
                    
                    val freshMetadata = ytController?.metadata
                    val actualDuration = freshMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: initialDuration

                    if (actualDuration <= 181000L) {
                        AppLogger.log("[SERVICE] Title: '$title' | Duration: ${actualDuration}ms. (Shorts/Scroll suspect -> Adding 1.5s debounce delay)")
                        delay(1500)
                    } else {
                        AppLogger.log("[SERVICE] Title: '$title' | Duration: ${actualDuration}ms. (Long-form video -> Bypassing delay)")
                    }
                    
                    if (isActive) {
                        fetchSegmentsAndTrack(title)
                    } else {
                        AppLogger.log("[SERVICE] Fetch aborted: User scrolled away before debounce timer finished.")
                    }
                }
            }
        }
    }

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "me.jaival.sponsorskip.TOGGLE_SERVICE") {
                if (!SettingsManager.isServiceEnabled) {
                    AppLogger.log("[SERVICE] KILL SIGNAL RECEIVED. Wiping memory and unregistering hooks.")
                    trackingJob?.cancel()
                    fetchJob?.cancel()
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
        val filter = IntentFilter("me.jaival.sponsorskip.TOGGLE_SERVICE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MediaNotificationService::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            if (SettingsManager.isServiceEnabled) {
                handleSessions(sessionManager?.getActiveSessions(component))
            }
        } catch (e: Exception) {
            AppLogger.log("[ERROR] Failed to hook Media Router: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppLogger.log("[SERVICE] ⚠️ WARNING: System abruptly unbound the NotificationListenerService!")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestRebind(ComponentName(this, MediaNotificationService::class.java))
                AppLogger.log("[SERVICE] Fired requestRebind() to wake the service back up.")
            }
        } catch (e: Exception) {
            AppLogger.log("[SERVICE] Rebind request failed: ${e.message}")
        }
    }

    private fun handleSessions(sessions: List<MediaController>?) {
        if (!SettingsManager.isServiceEnabled) return
        val newYtController = sessions?.find { SettingsManager.targetPackages.contains(it.packageName) }

        if (newYtController != null) {
            if (ytController?.sessionToken == newYtController.sessionToken) return
            AppLogger.log("[SERVICE] Hooked into MediaController (Token: ${newYtController.sessionToken}).")
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
                fetchJob?.cancel()
                skipSegments.clear()
            }
        }
    }

    private suspend fun fetchSegmentsAndTrack(title: String) {
        try {
            val query = URLEncoder.encode(title, "UTF-8")
            val searchReq = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$query")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val searchRes = client.newCall(searchReq).execute()
            val html = searchRes.body?.string() ?: ""

            val regex = Regex("""/watch\?v=([a-zA-Z0-9_-]{11})""")
            val match = regex.find(html)

            if (match == null) {
                if (SettingsManager.isLoggingEnabled) showToast("Error: Could not extract Video ID")
                return
            }

            val videoId = match.groupValues[1]
            AppLogger.log("[SCRAPER] Extracted Video ID: $videoId")

            val categoriesArr = """["sponsor","intro","outro","interaction","selfpromo","music_offtopic","preview","filler","hook"]"""
            val encCategories = URLEncoder.encode(categoriesArr, "UTF-8")
            val apiUrl = "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$encCategories"

            AppLogger.log("[API] Executing GET: $apiUrl")
            val sponsorReq = Request.Builder().url(apiUrl).build()
            val sponsorRes = client.newCall(sponsorReq).execute()
            AppLogger.log("[API] Response Code: ${sponsorRes.code}")

            if (!sponsorRes.isSuccessful) {
                if (SettingsManager.isLoggingEnabled) showToast("No segments are there for the video")
                return
            }

            val sponsorJson = JSONArray(sponsorRes.body?.string() ?: "[]")
            
            skipSegments.clear()
            val armedSegments = mutableListOf<Segment>()

            for (i in 0 until sponsorJson.length()) {
                val obj = sponsorJson.getJSONObject(i)
                val segment = obj.getJSONArray("segment")
                val category = obj.getString("category")

                val action = SettingsManager.getSegmentAction(category)
                val actionStr = if (action == 1) "Skip" else "Off"
                AppLogger.log("[PARSE] Evaluated block [$category]. User setting = $actionStr")

                if (action == 1) {
                    val start = (segment.getDouble(0) * 1000).toLong()
                    val end = (segment.getDouble(1) * 1000).toLong()
                    armedSegments.add(Segment(start, end, category))
                }
            }

            val sorted = armedSegments.sortedBy { it.startMs }
            val armedCount = sorted.size
            
            if (sorted.isNotEmpty()) {
                var current = sorted[0]
                for (i in 1 until sorted.size) {
                    val next = sorted[i]
                    if (current.endMs >= next.startMs - 1000) {
                        AppLogger.log("[TRACKER] Fusing adjacent segments: [${current.category}] and [${next.category}] into a multiple block.")
                        current = Segment(current.startMs, max(current.endMs, next.endMs), "multiple")
                    } else {
                        skipSegments.add(current)
                        current = next
                    }
                }
                skipSegments.add(current)
            }

            if (skipSegments.isEmpty()) {
                if (SettingsManager.isLoggingEnabled) showToast("No segments are there for the video")
            } else {
                AppLogger.log("[TRACKER] Engaging playback loop. Monitoring ${skipSegments.size} merged blocks (from $armedCount original enabled segments).")
                showToast("Loaded $armedCount segments to skip")
                startTracking()
            }
        } catch (e: Exception) {
            AppLogger.log("[FATAL EXCEPTION] Trace: ${e.message}")
            if (SettingsManager.isLoggingEnabled) showToast("Error fetching segments")
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
                        lastLogTime = System.currentTimeMillis()
                    }

                    val hitSegment = skipSegments.find { pos in it.startMs..it.endMs }

                    if (hitSegment != null) {
                        AppLogger.log("[TRACKER] ⚠️ CROSSED BOUNDARY: ${hitSegment.category.uppercase()} at $pos ms")
                        AppLogger.log("[TRACKER] Skip Authorized. Executing seekTo(${hitSegment.endMs})")
                        ytController?.transportControls?.seekTo(hitSegment.endMs)

                        val savedMs = hitSegment.endMs - hitSegment.startMs
                        SettingsManager.skippedCount += if (hitSegment.category == "multiple") 2 else 1
                        SettingsManager.timeSavedMs += savedMs
                        sendBroadcast(Intent("me.jaival.sponsorskip.STATS_UPDATED").setPackage(packageName))

                        if (hitSegment.category == "multiple") {
                            AppLogger.log("[TRACKER] Skipped a fused multi-segment block.")
                            showToast("Skipped Multiple segments")
                        } else {
                            showToast("Skipped: ${hitSegment.category.uppercase()}")
                        }
                        
                        skipSegments.remove(hitSegment)
                        delay(2000)
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
        unregisterReceiver(toggleReceiver)
        scope.cancel()
        super.onDestroy()
    }
}
