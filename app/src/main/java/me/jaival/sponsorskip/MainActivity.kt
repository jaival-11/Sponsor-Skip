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
    
    private var privacyDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        AppLogger.init(this)
        AppLogger.log("[UI] MainActivity onCreate triggered.")
        setContentView(R.layout.activity_main)
        
        // ENFORCE PRIVACY GATEKEEPER
        if (!SettingsManager.isPrivacyAccepted) {
            showPrivacyDialog()
        }

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

        val hasNotifInit = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (!hasNotifInit && SettingsManager.isServiceEnabled) {
            SettingsManager.isServiceEnabled = false
            AppLogger.log("[UI] Auto-disabled service on load due to revoked permissions.")
        }
        switchMaster.isChecked = SettingsManager.isServiceEnabled

        switchMaster.setOnCheckedChangeListener { view, isChecked ->
            val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)

            if (isChecked && !hasNotif) {
                view.haptic()
                AppLogger.log("[GUARD] Blocked master toggle: Missing Notification Listener permission.")
                switchMaster.isChecked = false 
                Toast.makeText(this@MainActivity, "Permission required to start service.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
                return@setOnCheckedChangeListener
            }

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
        findViewById<View>(R.id.btnSetBugs).setOnClickListener { it.haptic(); AppLogger.log("[UI] Opened Bug Reports"); Toast.makeText(this, "You can also contact the developer directly, but Codeberg is preferred", Toast.LENGTH_LONG).show(); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip#bug-reports-feature-suggestions"))) }
        findViewById<View>(R.id.btnSetFeature).setOnClickListener { it.haptic(); AppLogger.log("[UI] Opened Feature Requests"); Toast.makeText(this, "You can also contact the developer directly, but Codeberg is preferred", Toast.LENGTH_LONG).show(); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip#bug-reports-feature-suggestions"))) }
        findViewById<View>(R.id.btnSetContact).setOnClickListener { it.haptic(); AppLogger.log("[UI] Opened Email Client"); val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:jaival7909@gmail.com?subject=" + Uri.encode("Sponsor Skip - App Contact")) }; startActivity(Intent.createChooser(intent, "Send Email")) }
        findViewById<View>(R.id.btnSetPrivacy).setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip/src/branch/main/PRIVACY.md"))) }
        findViewById<View>(R.id.btnSetLicense).setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); androidx.appcompat.app.AlertDialog.Builder(this).setTitle("License & Warranty").setMessage("Sponsor Skip: Auto-skips SponsorBlock segments in YouTube videos\nCopyright (C) 2026 Jaival\n\nThis program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.").setPositiveButton("View Full GPLv3") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))) }.setNegativeButton("Close", null).show() }
        findViewById<View>(R.id.btnSetCredits).setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); com.mikepenz.aboutlibraries.LibsBuilder().withLicenseShown(true).start(this) }

        AppLogger.log("[SYSTEM] Launching background auto-update check...")
        lifecycleScope.launch { UpdateManager.checkUpdate(this@MainActivity, false) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statsReceiver, IntentFilter("me.jaival.sponsorskip.STATS_UPDATED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statsReceiver, IntentFilter("me.jaival.sponsorskip.STATS_UPDATED"))
        }
    }

    private fun showPrivacyDialog() {
        if (privacyDialog?.isShowing == true) return

        val message = "To keep things transparent and respect your privacy, here is exactly how the app works under the hood:\n\n" +
            "1. Finding the Video: We require 'Notification Access' to securely read your device's active media player. This lets us see the Title of the video you are watching. We DO NOT read your personal messages or other notifications.\n\n" +
            "2. Getting the Video ID: Because the media player doesn't provide a direct link, the app anonymously searches the public YouTube website using the Title to grab the official 'Video ID'. While your IP address connects to YouTube during this search, no account data, logins, or cookies are sent.\n\n" +
            "3. Skipping the Ads: We send that Video ID to the community-run SponsorBlock API (sponsor.ajay.app) to get the skip timestamps. This request is processed under their privacy policy. Your IP connects to their servers anonymously and is never tied to your personal identity.\n\n" +
            "4. Local processing: The actual skipping happens entirely on your phone. We never collect, store, share, or sell your viewing history.\n\n" +
            "By tapping 'Accept', you consent to this data flow and our Privacy Policy."

        privacyDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Welcome to Sponsor Skip!")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Accept") { _, _ -> SettingsManager.isPrivacyAccepted = true }
            .setNegativeButton("Decline") { _, _ -> finishAffinity() }
            .setNeutralButton("Privacy Policy", null)
            .create()

        // Override Neutral button to prevent the dialog from closing when reading the policy
        privacyDialog?.setOnShowListener {
            privacyDialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip/src/branch/main/PRIVACY.md")))
            }
        }
        privacyDialog?.show()
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
                
                val offId = View.generateViewId()
                val skipId = View.generateViewId()
                
                addView(RadioButton(context).apply { id = offId; text = "Off" })
                addView(RadioButton(context).apply { id = skipId; text = "Skip automatically" })
                
                check(if (SettingsManager.getSegmentAction(key) == 1) skipId else offId)
                
                setOnCheckedChangeListener { view, checkedId ->
                    if (checkedId != -1) { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        val action = if (checkedId == skipId) 1 else 0
                        AppLogger.log("[SETTINGS] Category '$key' changed to state: $action")
                        SettingsManager.setSegmentAction(key, action)
                    }
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
        val tvTagline = findViewById<TextView>(R.id.tvTagline)
        val taglineText = "SponsorBlock for Vanilla YouTube"
        val spannableTagline = SpannableString(taglineText)
        val clickableSponsorBlock = object : ClickableSpan() {
            override fun onClick(widget: View) {
                widget.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ajayyy/SponsorBlock")))
            }
        }
        spannableTagline.setSpan(clickableSponsorBlock, 0, 12, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvTagline.text = spannableTagline
        tvTagline.movementMethod = LinkMovementMethod.getInstance()
        
        val footer = findViewById<TextView>(R.id.tvFooter)
        val text = "Made with ❤️ by Jaival"
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
        privacyDialog?.dismiss()
        super.onDestroy()
    }
}
