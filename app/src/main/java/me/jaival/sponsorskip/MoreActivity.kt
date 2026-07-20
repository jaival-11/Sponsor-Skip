/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright © 2026 Jaival
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

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class MoreActivity : AppCompatActivity() {

    private val exportBackupLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val jsonStr = SettingsManager.exportSettingsJson()
                    outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                AppLogger.log("[BACKUP] Backup successfully created at $uri")
                Toast.makeText(this, "Backup created successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                AppLogger.log("[BACKUP] Export failed: ${e.message}")
                Toast.makeText(this, "Failed to create backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val jsonStr = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (!jsonStr.isNullOrEmpty() && SettingsManager.importSettingsJson(jsonStr)) {
                    AppLogger.log("[BACKUP] Backup successfully restored from $uri")
                    Toast.makeText(this, "Backup restored successfully", Toast.LENGTH_SHORT).show()
                    updateUiState()
                    showRestartDialog()
                } else {
                    AppLogger.log("[BACKUP] Failed to restore: Invalid backup file")
                    Toast.makeText(this, "Invalid backup file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                AppLogger.log("[BACKUP] Import failed: ${e.message}")
                Toast.makeText(this, "Failed to restore backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRestartDialog() {
        AppLogger.log("[BACKUP] Prompting user to restart app after successful restoration")
        AlertDialog.Builder(this)
            .setTitle("Restart Required")
            .setMessage("Settings and statistics have been restored. Please restart the app to apply all changes.")
            .setCancelable(false)
            .setPositiveButton("Restart") { _, _ ->
                AppLogger.log("[BACKUP] User confirmed restart, restarting application")
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    val restartIntent = Intent.makeRestartActivityTask(intent.component)
                    startActivity(restartIntent)
                }
                Runtime.getRuntime().exit(0)
            }
            .show()
    }

    private fun updateUiState() {
        findViewById<MaterialSwitch>(R.id.switchStrictSearch)?.isChecked = SettingsManager.isStrictSearchEnabled
        findViewById<MaterialSwitch>(R.id.switchPreRelease)?.isChecked = SettingsManager.getPreReleaseSetting(this)
        findViewById<MaterialSwitch>(R.id.switchSpot)?.isChecked = SettingsManager.isSpotEnabled
        findViewById<MaterialSwitch>(R.id.switchForeground)?.isChecked = SettingsManager.isForegroundEnabled
        findViewById<MaterialSwitch>(R.id.switchSkipCountTracking)?.isChecked = SettingsManager.isSkipCountTrackingEnabled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more)
        val switchStrict = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchStrictSearch)
        switchStrict?.isChecked = SettingsManager.isStrictSearchEnabled
        switchStrict?.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.isStrictSearchEnabled = isChecked
        }
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() 

}

        fun View.haptic() = this.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val switchPreRelease = findViewById<MaterialSwitch>(R.id.switchPreRelease)
        switchPreRelease.isChecked = SettingsManager.getPreReleaseSetting(this)
        switchPreRelease.setOnCheckedChangeListener { _, isChecked -> SettingsManager.setPreReleaseSetting(isChecked) }

        val switchSpot = findViewById<MaterialSwitch>(R.id.switchSpot)
        switchSpot.isChecked = SettingsManager.isSpotEnabled

        switchSpot.setOnClickListener { view ->
            view.haptic()
            val isChecked = switchSpot.isChecked

            if (isChecked && SettingsManager.targetPackages.contains(SettingsManager.SPOTIFY_PACKAGE)) {
                AlertDialog.Builder(this)
                    .setTitle("App Conflict")
                    .setMessage("Spotify is currently selected in Main YouTube SponsorBlock.\n\nIf you continue, it will be removed from there and Spot SponsorBlock will be enabled.")
                    .setPositiveButton("Continue") { _, _ ->
                        val updated = SettingsManager.targetPackages.toMutableSet()
                        updated.remove(SettingsManager.SPOTIFY_PACKAGE)
                        SettingsManager.targetPackages = updated

                        SettingsManager.isSpotEnabled = true
                        sendBroadcast(Intent("me.jaival.sponsorskip.TOGGLE_SERVICE"))
                        Toast.makeText(this, "Enabled Spot SponsorBlock", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel") { _, _ -> switchSpot.isChecked = false }
                    .show()
            } else {
                SettingsManager.isSpotEnabled = isChecked
                sendBroadcast(Intent("me.jaival.sponsorskip.TOGGLE_SERVICE"))
            }
        }

        val switchFg = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchForeground)
        switchFg?.isChecked = SettingsManager.isForegroundEnabled
        switchFg?.setOnClickListener { view ->
            val isChecked = switchFg.isChecked
            if (isChecked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                switchFg.isChecked = false
                // Skip the custom dialog and directly ask for system permission
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
                return@setOnClickListener
            }
            SettingsManager.isForegroundEnabled = isChecked
        }

        val switchSkipCount = findViewById<MaterialSwitch>(R.id.switchSkipCountTracking)
        switchSkipCount?.isChecked = SettingsManager.isSkipCountTrackingEnabled
        switchSkipCount?.setOnCheckedChangeListener { _, isChecked ->
            AppLogger.log("[UI] Skip count tracking toggled: $isChecked")
            SettingsManager.isSkipCountTrackingEnabled = isChecked
        }

        findViewById<View>(R.id.btnSetMinDuration).setOnClickListener {
            it.haptic()
            val input = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(SettingsManager.minSegmentDuration.toString())
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(this)
                .setTitle("Minimum Segment Duration")
                .setMessage("Enter min. duration for a segment to skip. Segments shorter than it will be not skipped (in seconds).")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val valStr = input.text.toString()
                    val value = valStr.toFloatOrNull() ?: 0f
                    SettingsManager.minSegmentDuration = value
                    Toast.makeText(this, "Minimum duration set to $value seconds", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View>(R.id.btnBackupRestore).setOnClickListener {
            it.haptic()
            val options = arrayOf("Backup Settings and Stastics", "Restore Settings and Stastics")
            AlertDialog.Builder(this)
                .setTitle("Backup & Restore")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> exportBackupLauncher.launch("sponsorskip_backup.json")
                        1 -> importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                }
                .show()
        }

        findViewById<View>(R.id.btnSetDebug).setOnClickListener { it.haptic(); startActivity(Intent(this, DebugActivity::class.java)) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            SettingsManager.isForegroundEnabled = granted
            findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchForeground)?.isChecked = granted
        }
    }
}
