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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File

class DebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        val svLogs = findViewById<ScrollView>(R.id.svLogs)
        val switchLogs = findViewById<MaterialSwitch>(R.id.switchLogs)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        val btnClear = findViewById<Button>(R.id.btnClear)

        fun updateUiState(isEnabled: Boolean) {
            btnRefresh.isEnabled = isEnabled
            btnCopy.isEnabled = isEnabled
            btnClear.isEnabled = isEnabled
            btnClear.alpha = if (isEnabled) 1.0f else 0.5f
            svLogs.visibility = if (isEnabled) View.VISIBLE else View.GONE
        }

        switchLogs.isChecked = SettingsManager.isLoggingEnabled
        updateUiState(SettingsManager.isLoggingEnabled)

        switchLogs.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.isLoggingEnabled = isChecked
            updateUiState(isChecked)
            if (isChecked) refreshLogs(tvLogs)
        }

        btnRefresh.setOnClickListener { refreshLogs(tvLogs) }
        
        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SponsorSkip Logs", tvLogs.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Logs copied!", Toast.LENGTH_SHORT).show()
        }

        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to permanently delete all logs?")
                .setPositiveButton("Clear") { _, _ ->
                    AppLogger.clearLogs()
                    refreshLogs(tvLogs)
                    Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        if (SettingsManager.isLoggingEnabled) refreshLogs(tvLogs)
    }

    private fun refreshLogs(textView: TextView) {
        try {
            val logFile = File(getExternalFilesDir(null), "skipper_logs.txt")
            if (logFile.exists()) {
                textView.text = logFile.readLines().reversed().joinToString("\n")
            } else {
                textView.text = "Log file is empty."
            }
        } catch (e: Exception) {
            textView.text = "Error reading logs: ${e.message}"
        }
    }
}
