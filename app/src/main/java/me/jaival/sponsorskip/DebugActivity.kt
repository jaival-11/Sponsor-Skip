package me.jaival.sponsorskip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class DebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val tvLogs = findViewById<TextView>(R.id.tvLogs)

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshLogs(tvLogs) }
        
        findViewById<Button>(R.id.btnCopy).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SponsorSkipper Logs", tvLogs.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Logs copied!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
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

        refreshLogs(tvLogs)
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
