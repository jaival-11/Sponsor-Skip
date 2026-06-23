/*
 * Sponsor Skip - Auto-skips SponsorBlock segments in YouTube videos
 * Copyright © 2026 Jaival
 */
package me.jaival.sponsorskip

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.pm.PackageManager
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
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
  private val categories = listOf(
    Triple("sponsor", "Sponsor", "Paid promotion, paid referrals and direct advertisements. Not for self-promotion or free shout-outs to causes / creators / websites / products they like"),
    Triple("selfpromo", "Unpaid / Self Promotion", "Similar to Sponsor except for unpaid / self promotion. Includes sections about merchandise, donations, or information about who they collaborated with"),
    Triple("interaction", "Interaction Reminder", "A short reminder to like, subscribe or follow them in the middle of content. If it is long or about something specific, it should instead be under self promotion"),
    Triple("intro", "Intermission / Intro Animation", "An interval without actual content. Could be a pause, static frame, or repeating animation. Does not include transitions containing information"),
    Triple("outro", "Endcards / Credits", "Credits or when the YouTube endcards appear. Not for conclusions with information"),
    Triple("preview", "Preview / Recap", "Collection of clips that show what is coming up or what happened in the video or in other videos of a series, where all information is repeated elsewhere"),
    Triple("hook", "Hook / Greetings", "Narrated trailers for the upcoming video, greetings and goodbyes. Does not include sections that add additional content"),
    Triple("filler", "Tangent / Jokes", "Tangential scenes or jokes that are not required to understand the main content of the video. Does not include sections providing context or background details"),
    Triple("music_offtopic", "Music: Non-Music Section", "Only for use in music videos. Sections of music videos without music that are not already covered by another category")
  )

  private val statsReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) { refreshStats() }
  }
  
  private var privacyDialog: AlertDialog? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SettingsManager.init(this)
    AppLogger.init(this)
    AppLogger.log("[UI] MainActivity onCreate triggered.")
    setContentView(R.layout.activity_main)
    
    if (!SettingsManager.isPrivacyAccepted) { showPrivacyDialog() }

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
    
    (findViewById<TextView>(R.id.tvStats).parent as View).setOnClickListener { view ->
      if (SettingsManager.skippedCount == 0) return@setOnClickListener
      view.haptic()
      AlertDialog.Builder(this@MainActivity)
        .setTitle("Reset Statistics")
        .setMessage("Are you sure you want to reset your saved segments and time?")
        .setPositiveButton("Reset") { _, _ ->
          SettingsManager.skippedCount = 0
          SettingsManager.timeSavedMs = 0L
          refreshStats()
        }
        .setNegativeButton("Cancel", null)
        .show()
    }
    setupFooter()
    
    val switchMaster = findViewById<MaterialSwitch>(R.id.switchMaster)
    val hasNotifInit = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    if (!hasNotifInit && SettingsManager.isServiceEnabled) {
      SettingsManager.isServiceEnabled = false
    }
    switchMaster.isChecked = SettingsManager.isServiceEnabled

    switchMaster.setOnCheckedChangeListener { view, isChecked ->
      val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)
      if (isChecked && !hasNotif) {
        view.haptic()
        switchMaster.isChecked = false 
        Toast.makeText(this@MainActivity, "Permission required to start service.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this@MainActivity, PermissionsActivity::class.java))
        return@setOnCheckedChangeListener
      }
      if (SettingsManager.isServiceEnabled != isChecked) {
        view.haptic()
        SettingsManager.isServiceEnabled = isChecked
        sendBroadcast(Intent("me.jaival.sponsorskip.TOGGLE_SERVICE"))
        updateGreyOutState(isChecked)
      }
    }

    // Beta Tag and Icon styling for Custom Apps Button
    val btnCustomApps = findViewById<TextView>(R.id.btnSetBackup)
    val span = SpannableString("Custom Apps  beta ")
    span.setSpan(BackgroundColorSpan(Color.parseColor("#44888888")), 13, 17, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    span.setSpan(TypefaceSpan("monospace"), 13, 17, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    span.setSpan(RelativeSizeSpan(0.75f), 13, 17, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    btnCustomApps.text = span
    
    

    
    try {
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.tvVersion).text = "Version ${pInfo.versionName} (Tap to check)"
    } catch (e: Exception) { }
    
    findViewById<View>(R.id.cardUpdate).setOnClickListener { it.haptic(); lifecycleScope.launch { UpdateManager.checkUpdate(this@MainActivity, true) } }
    findViewById<View>(R.id.btnSetPerms).setOnClickListener { it.haptic(); startActivity(Intent(this, PermissionsActivity::class.java)) }
    btnCustomApps.setOnClickListener { it.haptic(); AppSelectionDialog.show(this@MainActivity) }
    findViewById<View>(R.id.btnSetMore).setOnClickListener { it.haptic(); startActivity(Intent(this, MoreActivity::class.java)) }
    findViewById<View>(R.id.btnSetRepo).setOnClickListener { it.haptic(); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip"))) }
    findViewById<View>(R.id.btnSetBugs).setOnClickListener { it.haptic(); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip#bug-reports-feature-suggestions"))) }
    findViewById<View>(R.id.btnSetFeature).setOnClickListener { it.haptic(); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip#bug-reports-feature-suggestions"))) }
    findViewById<View>(R.id.btnSetContact).setOnClickListener { it.haptic(); val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "Unknown" }; val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:jaival7909@gmail.com?subject=" + Uri.encode("Sponsor Skip - v$version")) }; startActivity(Intent.createChooser(intent, "Send Email")) }
    findViewById<View>(R.id.btnSetPrivacy).setOnClickListener { it.haptic(); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/jaival/Sponsor-Skip/src/branch/main/PRIVACY.md"))) }
    findViewById<View>(R.id.btnSetLicense).setOnClickListener { it.haptic(); AlertDialog.Builder(this).setTitle("License & Warranty").setMessage("Sponsor Skip: Auto-skips SponsorBlock segments in YouTube videos\nCopyright © 2026 Jaival\n\nThis program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.").setPositiveButton("View Full GPLv3") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))) }.setNegativeButton("Close", null).show() }
    findViewById<View>(R.id.btnSetCredits).setOnClickListener { view ->
      view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
      
      val handler = java.lang.reflect.InvocationHandler { _, method, args ->
          try {
              // Intercept clicks on both the main card and the bottom license badge
              if ((method.name == "onLibraryContentClicked" || method.name == "onLibraryBottomClicked") && args != null && args.size >= 2) {
                  val v = args[0] as android.view.View
                  val library = args[1] as com.mikepenz.aboutlibraries.entity.Library
                  
                  val license = library.licenses.firstOrNull()
                  var url = license?.url ?: ""
                  val name = license?.name?.lowercase() ?: ""
                  val lowerUrl = url.lowercase()
                  
                  // SPDX URL Auto-Corrector
                  if (name.contains("apache") || lowerUrl.contains("apache.org/licenses/license-2.0")) {
                      url = "https://spdx.org/licenses/Apache-2.0.html"
                  } else if (name.contains("mit") || lowerUrl.contains("opensource.org/licenses/mit")) {
                      url = "https://spdx.org/licenses/MIT.html"
                  } else if (url.startsWith("http://")) {
                      // Force HTTPS for any other legacy links
                      url = url.replace("http://", "https://")
                  }
                  
                  if (url.isNotBlank()) {
                      try {
                          v.context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                      } catch (e: Exception) {
                          android.widget.Toast.makeText(v.context, "Failed to open link", android.widget.Toast.LENGTH_SHORT).show()
                      }
                  } else {
                      android.widget.Toast.makeText(v.context, "No license URL provided by library", android.widget.Toast.LENGTH_SHORT).show()
                  }
                  
                  // Returning TRUE consumes the click, completely blocking the release page from opening
                  return@InvocationHandler true 
              }
          } catch (e: Exception) { }
          
          // Disable ALL other default behaviors (like Repo/Author clicks) by forcing true
          if (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == java.lang.Boolean::class.java || method.returnType.name == "boolean") {
              return@InvocationHandler true
          }
          null
      }
      
      com.mikepenz.aboutlibraries.LibsConfiguration.listener = java.lang.reflect.Proxy.newProxyInstance(
          com.mikepenz.aboutlibraries.LibsConfiguration.LibsListener::class.java.classLoader,
          arrayOf(com.mikepenz.aboutlibraries.LibsConfiguration.LibsListener::class.java),
          handler
      ) as com.mikepenz.aboutlibraries.LibsConfiguration.LibsListener
      
      com.mikepenz.aboutlibraries.LibsBuilder()
          .withLicenseShown(true)
          .start(this)
    }

    if (SettingsManager.isPrivacyAccepted) { lifecycleScope.launch { UpdateManager.checkUpdate(this@MainActivity, false) } }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(statsReceiver, IntentFilter("me.jaival.sponsorskip.STATS_UPDATED"), Context.RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(statsReceiver, IntentFilter("me.jaival.sponsorskip.STATS_UPDATED"))
    }
  }

  private fun showPrivacyDialog() {
    if (privacyDialog?.isShowing == true) return
    val message = "To keep things transparent and respect your privacy, here is exactly how the app works under the hood:\n\n1. Finding the Video: The app requires 'Notification Access' to securely read your device's active media player. This lets the app see the Title of the video you are watching. The app DOES NOT read your personal messages or other notifications.\n\n2. Getting the Video ID: Because the media player doesn't provide a direct link, the app searches the public YouTube website using the Title to grab the official 'Video ID'. However, no account data, logins, or cookies are sent.\n\n3. Skipping the segments: The app sends that Video ID to the community-run SponsorBlock API (sponsor.ajay.app) to get the skip timestamps.\n\n4. Local processing: The actual skipping happens entirely on your phone. The app never collects, store, share, or sell your viewing history.\n\nYou can know more from our Privacy Policy\n\nBy tapping 'Accept', you consent to our Privacy Policy."
    privacyDialog = AlertDialog.Builder(this).setTitle("Welcome to Sponsor Skip!").setMessage(message).setCancelable(false).setPositiveButton("Accept") { _, _ -> SettingsManager.isPrivacyAccepted = true; lifecycleScope.launch { UpdateManager.checkUpdate(this@MainActivity, false) } }.setNegativeButton("Decline") { _, _ -> finishAffinity() }.setNeutralButton("Privacy Policy", null).create()
    privacyDialog?.setOnShowListener {
      privacyDialog?.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { view ->
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

  
  private fun wakeUpListenerService() {
      if (SettingsManager.isServiceEnabled && NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
          try {
              val component = ComponentName(this, MediaNotificationService::class.java)
              val pm = packageManager
              pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
              pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
              AppLogger.log("[UI] Performed internal component reset to cure NLS Zombie state.")
          } catch (e: Exception) {
              AppLogger.log("[UI] Component reset failed: ${e.message}")
          }
      }
  }

  private fun updateGreyOutState(isEnabled: Boolean) {
    val container = findViewById<LinearLayout>(R.id.segmentsContainer)
    for (i in 0 until container.childCount) {
      val card = container.getChildAt(i) as MaterialCardView
      card.alpha = if (isEnabled) 1.0f else 0.5f
      val rg = (card.getChildAt(0) as LinearLayout).getChildAt(2) as RadioGroup
      for (j in 0 until rg.childCount) rg.getChildAt(j).isEnabled = isEnabled
    }
  }

  private fun populateSegments() {
    val container = findViewById<LinearLayout>(R.id.segmentsContainer)
    categories.forEach { info ->
      val key = info.first
      val label = info.second
      val desc = info.third

      val card = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
        setCardBackgroundColor(Color.TRANSPARENT)
        strokeWidth = 2
      }
      val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
      
      // Header containing the title and the dropdown arrow
      val headerLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, 16)
      }
      
      val title = TextView(this).apply { 
        text = label
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }
      
      val arrow = TextView(this).apply {
        text = "▼"
        textSize = 14f
        setPadding(16, 0, 0, 0)
      }
      
      headerLayout.addView(title)
      headerLayout.addView(arrow)
      
      // The hidden description text box
      val descText = TextView(this).apply {
        text = desc
        textSize = 14f
        visibility = View.GONE
        setPadding(0, 0, 0, 16)
        alpha = 0.8f // Slight dimming to differentiate from the title
      }
      
      // Toggle logic for expanding/collapsing the description
      headerLayout.setOnClickListener {
        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (descText.visibility == View.GONE) {
            descText.visibility = View.VISIBLE
            arrow.text = "▲"
        } else {
            descText.visibility = View.GONE
            arrow.text = "▼"
        }
      }

      val radioGroup = RadioGroup(this).apply {
        orientation = RadioGroup.HORIZONTAL
        val offId = View.generateViewId()
        val skipId = View.generateViewId()
        addView(RadioButton(context).apply { id = offId; text = "Off"; contentDescription = "$label Off"; minHeight = (48 * resources.displayMetrics.density).toInt() })
        addView(RadioButton(context).apply { id = skipId; text = "Skip automatically"; contentDescription = "$label Skip automatically"; minHeight = (48 * resources.displayMetrics.density).toInt() })
        check(if (SettingsManager.getSegmentAction(key) == 1) skipId else offId)
        setOnCheckedChangeListener { view, checkedId ->
          if (checkedId != -1) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val action = if (checkedId == skipId) 1 else 0
            SettingsManager.setSegmentAction(key, action)
          }
        }
      }
      
      layout.addView(headerLayout)
      layout.addView(descText)
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
    val taglineText = "SponsorBlock for native android"
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
    unregisterReceiver(statsReceiver)
    privacyDialog?.dismiss()
    super.onDestroy()
  }
  
  private fun showCustomAppsDialog() {
      val progressDialog = AlertDialog.Builder(this)
          .setView(ProgressBar(this).apply { setPadding(50, 50, 50, 50) })
          .setMessage("Loading installed apps...")
          .setCancelable(false)
          .show()

      lifecycleScope.launch(Dispatchers.IO) {
          val pm = packageManager
          val installedApps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
          val selectedPackages = SettingsManager.targetPackages.toMutableSet()
          
          val masterList = mutableListOf<Triple<String, String, Boolean>>()
          for (appInfo in installedApps) {
              val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
              if (launchIntent != null || selectedPackages.contains(appInfo.packageName)) {
                  val appName = pm.getApplicationLabel(appInfo).toString()
                  masterList.add(Triple(appName, appInfo.packageName, selectedPackages.contains(appInfo.packageName)))
              }
          }
          
          withContext(Dispatchers.Main) {
              progressDialog.dismiss()
              
              val container = LinearLayout(this@MainActivity).apply {
                  orientation = LinearLayout.VERTICAL
                  setPadding(48, 32, 48, 0)
              }
              
              val searchInput = EditText(this@MainActivity).apply {
                  hint = "Search apps..."
                  setSingleLine()
                  minHeight = (48 * resources.displayMetrics.density).toInt()
                  setHintTextColor(android.graphics.Color.parseColor("#B0B0B0"))
              }
              
              val listView = ListView(this@MainActivity).apply {
                  divider = null
                  layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
              }
              
              container.addView(searchInput)
              container.addView(listView)
              
              var currentList = masterList.sortedWith(compareBy({ !it.third }, { it.first.lowercase() })).toMutableList()
              
              val adapter = object : BaseAdapter() {
                  override fun getCount() = currentList.size
                  override fun getItem(p0: Int) = currentList[p0]
                  override fun getItemId(p0: Int) = p0.toLong()
                  override fun getView(pos: Int, conv: View?, parent: ViewGroup?): View {
                      val cb = (conv as? CheckBox) ?: CheckBox(this@MainActivity).apply {
                          setPadding(0, 32, 0, 32)
                          textSize = 16f
                      }
                      val item = currentList[pos]
                      
                      cb.setOnCheckedChangeListener(null)
                      cb.text = item.first
                      cb.isChecked = item.third
                      
                      cb.setOnCheckedChangeListener { _, isChecked ->
                          val masterIdx = masterList.indexOfFirst { it.second == item.second }
                          if (masterIdx != -1) {
                              masterList[masterIdx] = masterList[masterIdx].copy(third = isChecked)
                          }
                          
                          if (isChecked) selectedPackages.add(item.second)
                          else selectedPackages.remove(item.second)
                          
                          val query = searchInput.text.toString().lowercase()
                          currentList = masterList.filter { it.first.lowercase().contains(query) }
                              .sortedWith(compareBy({ !it.third }, { it.first.lowercase() }))
                              .toMutableList()
                              
                          notifyDataSetChanged()
                          listView.setSelection(0)
                      }
                      return cb
                  }
              }
              listView.adapter = adapter
              
              searchInput.addTextChangedListener(object : android.text.TextWatcher {
                  override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                  override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                      val query = s.toString().lowercase()
                      currentList = masterList.filter { it.first.lowercase().contains(query) }
                          .sortedWith(compareBy({ !it.third }, { it.first.lowercase() }))
                          .toMutableList()
                      adapter.notifyDataSetChanged()
                  }
                  override fun afterTextChanged(s: android.text.Editable?) {}
              })
              
              AlertDialog.Builder(this@MainActivity)
                  .setTitle("Select Custom Apps")
                  .setView(container)
                  .setPositiveButton("Save") { _, _ ->
                      SettingsManager.targetPackages = selectedPackages
                      AppLogger.log("[Settings] Saved target packages. Total selected: ${selectedPackages.size}")
                      Toast.makeText(this@MainActivity, "Target apps updated", Toast.LENGTH_SHORT).show()
                  }
                  .setNegativeButton("Cancel", null)
                  .show()
          }
      }
  }
}
