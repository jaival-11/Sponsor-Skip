package me.jaival.sponsorskip
import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "skipper_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_master_switch", true)
        set(value) = prefs.edit().putBoolean("service_master_switch", value).apply()

    fun getSegmentAction(category: String): Int {
        return prefs.getInt("cat_$category", if (category == "sponsor") 1 else 0)
    }

    fun setSegmentAction(category: String, action: Int) {
        prefs.edit().putInt("cat_$category", action).apply()
    }

    var skippedCount: Int
        get() = prefs.getInt("stat_count", 0)
        set(value) = prefs.edit().putInt("stat_count", value).apply()

    var timeSavedMs: Long
        get() = prefs.getLong("stat_time", 0L)
        set(value) = prefs.edit().putLong("stat_time", value).apply()
}
