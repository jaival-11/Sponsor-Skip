package me.jaival.sponsorskip

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class PermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        
        val permNotif = findViewById<TextView>(R.id.permNotif)
        val permOverlay = findViewById<TextView>(R.id.permOverlay)
        val permToast = findViewById<TextView>(R.id.permToast)

        val hasListener = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        permNotif.text = "Notification Listener  " + (if (hasListener) "✅" else "❌")
        permNotif.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        val hasOverlay = Settings.canDrawOverlays(this)
        permOverlay.text = "Draw Overlays  " + (if (hasOverlay) "✅" else "❌")
        permOverlay.setOnClickListener { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }

        var hasToasts = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasToasts = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        permToast.text = "Post Notifications (Toasts)  " + (if (hasToasts) "✅" else "❌")
        permToast.setOnClickListener {
            if (!hasToasts && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
