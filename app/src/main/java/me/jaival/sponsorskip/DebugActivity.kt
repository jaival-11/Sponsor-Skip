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
        val switchRedact = findViewById<MaterialSwitch>(R.id.switchRedact)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        val btnClear = findViewById<Button>(R.id.btnClear)

        fun updateUiState(isEnabled: Boolean) {
            btnRefresh.isEnabled = isEnabled
            btnCopy.isEnabled = isEnabled
            btnClear.isEnabled = isEnabled
            btnClear.alpha = if (isEnabled) 1.0f else 0.5f
            svLogs.visibility = if (isEnabled) View.VISIBLE else View.GONE
            switchRedact.visibility = if (isEnabled) View.VISIBLE else View.GONE
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
            val rawLogs = tvLogs.text.toString()

            val finalLogs = if (switchRedact.isChecked) {
                rawLogs
                    .replace(Regex("(?i)(title[:=]\\s*).+"), "$1[title]")
                    .replace(Regex("(?i)(TITLE:\\s*).+"), "$1[title]")
                    .replace(Regex("(?i)(id[:=]\\s*)[a-zA-Z0-9_-]{11}"), "$1[id]")
                    .replace(Regex("v=[a-zA-Z0-9_-]{11}"), "v=[id]")
                    .replace(Regex("(?i)(Title: )'[^']*'"), "$1[title]")
                    .replace(Regex("(?i)(started for )'[^']*'"), "$1[title]")
                    .replace(Regex("(?i)(MEDIA_ID[:=]\\s*).+"), "$1[id]")
                    .replace(Regex("(?i)(Extracted: )'[^']*'"), "$1'[id]'")
                    .replace(Regex("(?i)(Extracted Title: )'[^']*'"), "$1[title]")
            } else {
                rawLogs
            }

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SponsorSkip Logs", finalLogs)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, if (switchRedact.isChecked) "Redacted logs copied! Always check logs before sharing." else "Raw logs copied! Always check logs before sharing.", Toast.LENGTH_LONG).show()
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
