package me.jaival.sponsorskip

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        val permToast = findViewById<TextView>(R.id.permToast)
        val permBattery = findViewById<TextView>(R.id.permBattery)
        val permHelp = findViewById<TextView>(R.id.permHelp)

        val hasListener = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        permNotif.text = "Notification Listener  " + (if (hasListener) "✅" else "❌")
        permNotif.setOnClickListener {
            if (hasListener) {
                Toast.makeText(this, "Notification listener permission already granted", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        var hasToasts = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasToasts = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        permToast.text = "Post Notifications (Toasts)  " + (if (hasToasts) "✅" else "❌")
        permToast.setOnClickListener {
            if (hasToasts) {
                Toast.makeText(this, "Post notification permission already granted", Toast.LENGTH_SHORT).show()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBattery = pm.isIgnoringBatteryOptimizations(packageName)
        permBattery.text = "Battery Optimization\n(It may fix detection issues, if faced)  \n" + (if (isIgnoringBattery) "✅ (Unrestricted)" else "⚠️ (Optimized)")

        permBattery.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Battery Optimization")
                .setMessage("If you are having issues with the app randomly stopping in the background, try disabling battery optimization to allow the service to run uninterrupted.\n\nIt is recommended to force stop the app, and reopen to ensure stability")
                .setPositiveButton("Disable") { _, _ ->
                    if (isIgnoringBattery) {
                        Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        permHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Read Carefully")
                .setMessage("Please do the following to bypass restricted permission issue:\n\nClick on app info > click on ⋮ at the top > \"Allow restricted permission\". Then comeback to Sponsor Skip.")
                .setPositiveButton("App Info") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .show()
        }
    }
}
