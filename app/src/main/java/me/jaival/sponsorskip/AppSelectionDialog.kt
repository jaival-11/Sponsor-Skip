package me.jaival.sponsorskip

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.*

object AppSelectionDialog {
    fun show(activity: Activity) {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        val progress = AlertDialog.Builder(activity)
            .setView(ProgressBar(activity).apply { setPadding(50, 50, 50, 50) })
            .setMessage("Loading installed apps...")
            .setCancelable(false)
            .show()

        scope.launch(Dispatchers.IO) {
            val pm = activity.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val currentActiveSet = SettingsManager.targetPackages.toMutableSet()

            val masterList = mutableListOf<Triple<String, String, Boolean>>()
            for (app in installed) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null || currentActiveSet.contains(app.packageName)) {
                    val label = pm.getApplicationLabel(app).toString()
                    masterList.add(Triple(label, app.packageName, currentActiveSet.contains(app.packageName)))
                }
            }

            withContext(Dispatchers.Main) {
                progress.dismiss()

                val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 0) }
                val searchInput = EditText(activity).apply { hint = "Search apps..."; setSingleLine(); minHeight = (48 * activity.resources.displayMetrics.density).toInt() }
                val listView = ListView(activity).apply { divider = null; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }

                container.addView(searchInput)
                container.addView(listView)

                var displayList = masterList.sortedWith(compareBy({ !it.third }, { it.first.lowercase() })).toMutableList()

                val adapter = object : BaseAdapter() {
                    override fun getCount() = displayList.size
                    override fun getItem(p0: Int) = displayList[p0]
                    override fun getItemId(p0: Int) = p0.toLong()
                    override fun getView(pos: Int, conv: View?, parent: ViewGroup?): View {
                        val cb = (conv as? CheckBox) ?: CheckBox(activity).apply { setPadding(0, 32, 0, 32); textSize = 16f }
                        val item = displayList[pos]

                        cb.text = item.first
                        cb.isChecked = item.third

                        cb.setOnClickListener { view ->
                            val isChecked = (view as CheckBox).isChecked
                            val targetPkg = item.second

                            val applyCheck = { finalState: Boolean ->
                                val masterIdx = masterList.indexOfFirst { it.second == targetPkg }
                                if (masterIdx != -1) masterList[masterIdx] = masterList[masterIdx].copy(third = finalState)
                                if (finalState) currentActiveSet.add(targetPkg) else currentActiveSet.remove(targetPkg)

                                val query = searchInput.text.toString().lowercase()
                                displayList = masterList.filter { it.first.lowercase().contains(query) }.sortedWith(compareBy({ !it.third }, { it.first.lowercase() })).toMutableList()
                                notifyDataSetChanged()
                            }

                            // Reverse Guard: If user adds Spotify to YouTube list while Spot is active
                            if (isChecked && targetPkg == SettingsManager.SPOTIFY_PACKAGE && SettingsManager.isSpotEnabled) {
                                AlertDialog.Builder(activity)
                                    .setTitle("App Conflict")
                                    .setMessage("Spotify is currently active in Spot SponsorBlock.\n\nIf you add it to the Main YouTube list, Spot SponsorBlock will be turned off.")
                                    .setPositiveButton("Continue") { _, _ ->
                                        SettingsManager.isSpotEnabled = false
                                        applyCheck(true)
                                        Toast.makeText(activity, "Disabled Spot SponsorBlock", Toast.LENGTH_SHORT).show()
                                    }
                                    .setNegativeButton("Cancel") { _, _ -> cb.isChecked = false }
                                    .show()
                            } else { applyCheck(isChecked) }
                        }
                        return cb
                    }
                }
                listView.adapter = adapter

                searchInput.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s.toString().lowercase()
                        displayList = masterList.filter { it.first.lowercase().contains(query) }.sortedWith(compareBy({ !it.third }, { it.first.lowercase() })).toMutableList()
                        adapter.notifyDataSetChanged()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })

                AlertDialog.Builder(activity)
                    .setTitle("Select Apps (YouTube SponsorBlock)")
                    .setView(container)
                    .setPositiveButton("Save") { _, _ ->
                        SettingsManager.targetPackages = currentActiveSet
                        AppLogger.log("[Settings] Saved YouTube packages. Count: ${currentActiveSet.size}")
                        Toast.makeText(activity, "App list saved", Toast.LENGTH_SHORT).show()
                        activity.sendBroadcast(Intent("me.jaival.sponsorskip.TOGGLE_SERVICE"))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
