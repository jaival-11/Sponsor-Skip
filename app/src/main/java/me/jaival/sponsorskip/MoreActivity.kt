package me.jaival.sponsorskip

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class MoreActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val switchPreRelease = findViewById<MaterialSwitch>(R.id.switchPreRelease)
        switchPreRelease.isChecked = SettingsManager.getPreReleaseSetting(this)

        switchPreRelease.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setPreReleaseSetting(isChecked)
        }

        findViewById<View>(R.id.btnSetMinDuration).setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val input = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(SettingsManager.minSegmentDuration.toString())
                setPadding(48, 32, 48, 32)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Minimum Segment Duration")
                .setMessage("Enter min. duration for a segment to skip. Segments shorter than it will be not skipped (in seconds).")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val valStr = input.text.toString()
                    val value = valStr.toFloatOrNull() ?: 0f
                    SettingsManager.minSegmentDuration = value
                    android.widget.Toast.makeText(this, "Minimum duration set to $value seconds", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<View>(R.id.btnSetDebug).setOnClickListener { 
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, DebugActivity::class.java)) 
        }
    }
}
