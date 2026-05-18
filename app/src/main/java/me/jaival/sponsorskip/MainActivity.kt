package me.jaival.sponsorskip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val categories = mapOf(
        "sponsor" to "Sponsors", "intro" to "Intermissions/Intros", "outro" to "Endcards/Credits",
        "interaction" to "Interaction Reminders", "selfpromo" to "Unpaid/Self Promotion",
        "music_offtopic" to "Non-Music Section", "preview" to "Previews/Recaps", "filler" to "Filler Tangent"
    )

    private val statsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshStats() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        AppLogger.init(this)
        AppLogger.log("[UI] MainActivity onCreate triggered.")
        setContentView(R.layout.activity_main)

        fun View.haptic() = this.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        val viewHome = findViewById<View>(R.id.view_home)
        val viewSettings = findViewById<View>(R.id.view_settings)
        
        findViewById<BottomNavigationView>(R.id.bottom_nav).setOnItemSelectedListener { item ->
            item.actionView?.haptic()
            AppLogger.log("[UI] Navigated to ${item.title}")
            when (item.itemId) {
                R.id.nav_home -> { viewHome.visibility = View.VISIBLE; viewSettings.visibility = View.GONE }
                R.id.nav_settings -> { viewHome.visibility = View.GONE; viewSettings.visibility = View.VISIBLE }
            }
            true
        }

        populateSegments()
        refreshStats()
        setupFooter()

        val switchMaster = findViewById<MaterialSwitch>(R.id.switchMaster)
        
        // Ensure switch accurately reflects state on load before attaching listener
        val hasNotifInit = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (!hasNotifInit && SettingsManager.isServiceEnabled) {
            SettingsManager.isServiceEnabled = false
            AppLogger.log("[UI] Auto-disabled service on load due to revoked permissions.")
        }
        switchMaster.isChecked = SettingsManager.isServiceEnabled

        switchMaster.setOnCheckedChangeListener { view, isChecked ->
            val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)
            
            // PERMISSION GUARD
            if (isChecked && !hasNotif) {
                view.haptic()
                AppLogger.log("[GUARD] Blocked master toggle: Missing Notification Listener permission.")
                switchMaster.isChecked = false // Auto-revert
                Toast.makeText(this@MainActivity, "Permission required to start service.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
                return@setOnCheckedChangeListener
            }

            // ONLY run if the state actually changed (prevents infinite loop from auto-revert)
            if (SettingsManager.isServiceEnabled != isChecked) {
                view.haptic()
                SettingsManager.isServiceEnabled = isChecked
                AppLogger.log("[SETTINGS] Master switch manually toggled to: $isChecked")
                sendBroadcast(Intent("me.jaival.sponsorskip.TOGGLE_SERVICE"))
                updateGreyOutState(isChecked)
            }
        }

        findViewById<View>(R.id.cardUpdate).setOnClickListener { it.haptic(); AppLogger.log("[UI] Manual update check requested."); lifecycleScope.launch { UpdateManager.checkUpdate(this@MainActivity, true) } }
        findViewById<View>(R.id.btnSetPerms).setOnClickListener { it.haptic(); AppLogger.log("[UI] Opened Permissions Page"); startActivity(Intent(this, PermissionsActivity::class.java)) }
        findViewById<View>(R.id.btnSetBackup).setOnClickListener { it.haptic(); Toast.makeText(this, "Backup coming soon!", Toast.LENGTH_SHORT).show() }
        findViewById<View>(R.id.btnSetDebug).setOnClickListener { it.haptic(); AppLogger.log("[UI] Opened Debug Logs"); startActivity(Intent(this, DebugActivity::class.java)) }
        findViewById<View>(R.id.btnSetRepo).setOnClickListener { it.haptic(); AppLogger.log("[UI] Opened Codeberg Repo"); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip"))) }
        findViewById<View>(R.id.btnSetBugs).setOnClickListener { it.haptic(); AppLogger.log("[UI] Opened Codeberg Issues"); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip/issues"))) }

        AppLogger.log("[SYSTEM] Launching background auto-update check...")
        lifecycleScope.launch { UpdateManager.checkUpdate(this@MainActivity, false) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statsReceiver, IntentFilter("me.jaival.sponsorskip.STATS_UPDATED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statsReceiver, IntentFilter("me.jaival.sponsorskip.STATS_UPDATED"))
        }
    }

    override fun onResume() {
        super.onResume()
        updateGreyOutState(SettingsManager.isServiceEnabled)
    }

    private fun updateGreyOutState(isEnabled: Boolean) {
        val container = findViewById<LinearLayout>(R.id.segmentsContainer)
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i) as MaterialCardView
            card.alpha = if (isEnabled) 1.0f else 0.5f
            val rg = (card.getChildAt(0) as LinearLayout).getChildAt(1) as RadioGroup
            for (j in 0 until rg.childCount) rg.getChildAt(j).isEnabled = isEnabled
        }
    }

    private fun populateSegments() {
        val container = findViewById<LinearLayout>(R.id.segmentsContainer)
        categories.forEach { (key, label) ->
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
                setCardBackgroundColor(Color.TRANSPARENT)
                strokeWidth = 2
            }
            val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
            val title = TextView(this).apply { text = label; textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, 16) }
            val radioGroup = RadioGroup(this).apply {
                orientation = RadioGroup.HORIZONTAL
                addView(RadioButton(context).apply { id = 0; text = "Off" })
                addView(RadioButton(context).apply { id = 1; text = "Skip automatically" })
                check(SettingsManager.getSegmentAction(key))
                setOnCheckedChangeListener { view, checkedId ->
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    AppLogger.log("[SETTINGS] Category '$key' changed to state: $checkedId")
                    SettingsManager.setSegmentAction(key, checkedId)
                }
            }
            layout.addView(title)
            layout.addView(radioGroup)
            card.addView(layout)
            container.addView(card)
        }
    }

    private fun refreshStats() {
        val ms = SettingsManager.timeSavedMs
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        val h = (ms / (1000 * 60 * 60))
        val timeStr = if (h > 0) "${h}h ${m}m ${s}s" else "${m}m ${s}s"
        findViewById<TextView>(R.id.tvStats).text = "${SettingsManager.skippedCount} Segments | $timeStr"
    }

    private fun setupFooter() {
        val footer = findViewById<TextView>(R.id.tvFooter)
        val text = "Made by Jaival, with ❤️"
        val spannable = SpannableString(text)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                widget.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival")))
            }
        }
        val start = text.indexOf("Jaival")
        spannable.setSpan(clickableSpan, start, start + 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        footer.text = spannable
        footer.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun onDestroy() {
        AppLogger.log("[UI] MainActivity destroyed.")
        unregisterReceiver(statsReceiver)
        super.onDestroy()
    }
}
