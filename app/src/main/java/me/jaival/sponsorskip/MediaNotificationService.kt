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
    private var currentTitleOrId = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions -> handleSessions(sessions) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val currentPkg = ytController?.packageName ?: ""
            val isSpotApp = (currentPkg == SettingsManager.SPOTIFY_PACKAGE) && SettingsManager.isSpotEnabled
            val isYtApp = SettingsManager.targetPackages.contains(currentPkg) && SettingsManager.isServiceEnabled

            if (!isSpotApp && !isYtApp) return

            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
            val initialDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val targetIdentifier = if (isSpotApp) {
                val rawMediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
                if (rawMediaId.contains(":")) rawMediaId.substringAfterLast(":") else rawMediaId
            } else { title }

            if (targetIdentifier.isNotBlank() && targetIdentifier != currentTitleOrId) {
                currentTitleOrId = targetIdentifier
                val modePrefix = if (isSpotApp) "[SPOT SERVICE]" else "[SERVICE]"
                
                AppLogger.log("$modePrefix === METADATA DETECTED ($currentPkg) ===")
                AppLogger.log("$modePrefix --- RAW METADATA DUMP ---")
                metadata?.keySet()?.forEach { key ->
                    val value = try { metadata.getString(key) ?: metadata.getLong(key).toString() } catch (e: Exception) { "Binary/Object" }
                    AppLogger.log("$modePrefix $key: $value")
                }
                AppLogger.log("$modePrefix ---------------------------")

                fetchJob?.cancel()
                fetchJob = scope.launch {
                    if (ytController?.playbackState?.state != PlaybackState.STATE_PLAYING) {
                        AppLogger.log("$modePrefix Target is buffering/paused. Waiting for playback...")
                        while (ytController?.playbackState?.state != PlaybackState.STATE_PLAYING && isActive) { delay(400) }
                        if (!isActive) return@launch
                        AppLogger.log("$modePrefix Playback started for '$targetIdentifier'.")
                    }

                    val freshMetadata = ytController?.metadata
                    val actualDuration = freshMetadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: initialDuration

                    if (!isSpotApp && actualDuration <= 181000L) {
                        AppLogger.log("$modePrefix Short video suspect -> 1.5s debounce delay")
                        delay(1500)
                    }

                    if (!isActive) return@launch

                    if (isSpotApp) {
                        AppLogger.log("$modePrefix Direct Metadata ID Extracted: '$targetIdentifier' (Bypassing HTML Search)")
                        fetchSegmentsAndTrack(targetIdentifier, true)
                    } else {
                        fetchSegmentsAndTrack(title, false)
                    }
                }
            }
        }
    }

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "me.jaival.sponsorskip.TOGGLE_SERVICE") {
                if (!SettingsManager.isServiceEnabled && !SettingsManager.isSpotEnabled) {
                    AppLogger.log("[SERVICE] MASTER KILL SIGNAL. Wiping memory and detaching hooks.")
                    trackingJob?.cancel(); fetchJob?.cancel(); ytController?.unregisterCallback(callback); ytController = null; currentTitleOrId = ""; skipSegments.clear()
                } else {
                    AppLogger.log("[SERVICE] CONFIG CHANGED. Re-evaluating active hooks.")
                    val component = ComponentName(this@MediaNotificationService, MediaNotificationService::class.java)
                    handleSessions(sessionManager?.getActiveSessions(component))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this); AppLogger.init(this)
        val filter = IntentFilter("me.jaival.sponsorskip.TOGGLE_SERVICE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(toggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(toggleReceiver, filter)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MediaNotificationService::class.java)
        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            handleSessions(sessionManager?.getActiveSessions(component))
        } catch (e: Exception) { AppLogger.log("[ERROR] Failed NLS hook: ${e.message}") }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        AppLogger.log("[SERVICE] ⚠️ WARNING: System abruptly unbound NotificationListenerService!")
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) requestRebind(ComponentName(this, MediaNotificationService::class.java)) } catch (e: Exception) {}
    }

    private fun handleSessions(sessions: List<MediaController>?) {
        val newController = sessions?.find { controller ->
            val pkg = controller.packageName
            val inSpot = (pkg == SettingsManager.SPOTIFY_PACKAGE) && SettingsManager.isSpotEnabled
            val inYt = SettingsManager.targetPackages.contains(pkg) && SettingsManager.isServiceEnabled
            inSpot || inYt
        }

        if (newController != null) {
            if (ytController?.sessionToken == newController.sessionToken) return
            AppLogger.log("[SERVICE] Hooked into MediaController (${newController.packageName}).")
            ytController?.unregisterCallback(callback); ytController = newController; ytController?.registerCallback(callback)
            callback.onMetadataChanged(ytController?.metadata)
        } else {
            if (ytController != null) {
                AppLogger.log("[SERVICE] Active playback detached.")
                ytController?.unregisterCallback(callback); ytController = null; currentTitleOrId = ""; trackingJob?.cancel(); fetchJob?.cancel(); skipSegments.clear()
            }
        }
    }

    private suspend fun fetchSegmentsAndTrack(targetInput: String, isSpotMode: Boolean) {
        try {
            val targetVideoId: String

            if (isSpotMode) {
                targetVideoId = targetInput
            } else {
                val query = URLEncoder.encode(targetInput, "UTF-8")
                val searchReq = Request.Builder().url("https://www.youtube.com/results?search_query=$query").header("User-Agent", "Mozilla/5.0").build()
                val searchRes = client.newCall(searchReq).execute()
                val html = searchRes.body?.string() ?: ""
                val match = Regex("""/watch\?v=([a-zA-Z0-9_-]{11})""").find(html)

                if (match == null) { if (SettingsManager.isLoggingEnabled) showToast("Error: Could not extract Video ID"); return }
                targetVideoId = match.groupValues[1]
                AppLogger.log("[SCRAPER] Extracted YouTube ID: $targetVideoId")
            }

            val serviceParam = if (isSpotMode) "&service=spotify" else ""
            val categoriesArr = """["sponsor","intro","outro","interaction","selfpromo","music_offtopic","preview","filler","hook"]"""
            val encCategories = URLEncoder.encode(categoriesArr, "UTF-8")
            val apiUrl = "https://sponsor.ajay.app/api/skipSegments?videoID=$targetVideoId$serviceParam&categories=$encCategories"

            AppLogger.log("[API] Executing GET: $apiUrl")
            val sponsorRes = client.newCall(Request.Builder().url(apiUrl).build()).execute()
            AppLogger.log("[API] Response Code: ${sponsorRes.code}")

            if (!sponsorRes.isSuccessful) { if (SettingsManager.isLoggingEnabled) showToast("No segments are there for the audio/video"); return }

            val sponsorJson = JSONArray(sponsorRes.body?.string() ?: "[]")
            skipSegments.clear()
            val armedSegments = mutableListOf<Segment>()

            for (i in 0 until sponsorJson.length()) {
                val obj = sponsorJson.getJSONObject(i)
                val segment = obj.getJSONArray("segment")
                val category = obj.getString("category")
                val action = SettingsManager.getSegmentAction(category)
                val actionStr = if (action == 1) "Skip" else "Off"

                if (action == 1) {
                    val durationSec = segment.getDouble(1) - segment.getDouble(0)
                    val minDur = SettingsManager.minSegmentDuration.toDouble()
                    if (durationSec < minDur) {
                        AppLogger.log("[PARSE] Evaluated [$category] = $actionStr (BLOCKED: ${String.format("%.2f", durationSec)}s < ${minDur}s min)")
                    } else {
                        AppLogger.log("[PARSE] Evaluated [$category] = $actionStr")
                        armedSegments.add(Segment((segment.getDouble(0) * 1000).toLong(), (segment.getDouble(1) * 1000).toLong(), category))
                    }
                } else { AppLogger.log("[PARSE] Evaluated [$category] = $actionStr") }
            }

            val sorted = armedSegments.sortedBy { it.startMs }
            val armedCount = sorted.size

            if (sorted.isNotEmpty()) {
                var current = sorted[0]
                for (i in 1 until sorted.size) {
                    val next = sorted[i]
                    if (current.endMs >= next.startMs - 1000) {
                        AppLogger.log("[TRACKER] Fusing adjacent segments into multiple block.")
                        current = Segment(current.startMs, max(current.endMs, next.endMs), "multiple")
                    } else { skipSegments.add(current); current = next }
                }
                skipSegments.add(current)
            }

            if (skipSegments.isNotEmpty()) {
                AppLogger.log("[TRACKER] Engaging playback loop for ${skipSegments.size} merged blocks.")
                showToast("Loaded $armedCount segments to skip")
                startTracking()
            }
        } catch (e: Exception) {
            AppLogger.log("[FATAL] Trace: ${e.message}")
            if (SettingsManager.isLoggingEnabled) showToast("Error fetching segments")
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val state = ytController?.playbackState
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    val pos = state.position
                    val hit = skipSegments.find { pos in it.startMs..it.endMs }

                    if (hit != null) {
                        AppLogger.log("[TRACKER] ⚠️ CROSSED BOUNDARY: ${hit.category.uppercase()} at $pos ms")
                        ytController?.transportControls?.seekTo(hit.endMs)

                        SettingsManager.skippedCount += if (hit.category == "multiple") 2 else 1
                        SettingsManager.timeSavedMs += (hit.endMs - hit.startMs)
                        sendBroadcast(Intent("me.jaival.sponsorskip.STATS_UPDATED").setPackage(packageName))

                        showToast(if (hit.category == "multiple") "Skipped Multiple segments" else "Skipped: ${hit.category.uppercase()}")
                        skipSegments.remove(hit)
                        delay(2000)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun showToast(msg: String) = mainHandler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
    override fun onDestroy() { unregisterReceiver(toggleReceiver); scope.cancel(); super.onDestroy() }
}
