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

        findViewById<View>(R.id.btnSetDebug).setOnClickListener { it.haptic(); startActivity(Intent(this, DebugActivity::class.java)) }
    }
}
