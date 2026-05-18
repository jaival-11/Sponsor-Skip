package me.jaival.sponsorskip

import android.app.Application
import com.google.android.material.color.DynamicColors

class SkipperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // This single line enables Monet dynamic theming across the whole app
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
