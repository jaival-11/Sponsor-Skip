package me.jaival.sponsorskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class SkipAccessibilityService : AccessibilityService() {
    private val skipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("SponsorSkipper", "Sponsor block hit! Executing gesture skips...")
            simulateDoubleTapToSkip()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("me.jaival.sponsorskip.SKIP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(skipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(skipReceiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(skipReceiver)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {} // Stay dormant to save battery
    override fun onInterrupt() {}

    private fun simulateDoubleTapToSkip() {
        val displayMetrics = resources.displayMetrics
        val x = (displayMetrics.widthPixels * 0.75).toFloat()
        val y = (displayMetrics.heightPixels * 0.35).toFloat()

        // Create the double-tap sequence
        val path = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path, 0, 50)
        val stroke2 = GestureDescription.StrokeDescription(path, 100, 50)

        val builder = GestureDescription.Builder()
        builder.addStroke(stroke1)
        builder.addStroke(stroke2)
        dispatchGesture(builder.build(), null, null)
    }
}
