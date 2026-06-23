package me.jaival.sponsorskip

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SponsorTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        SettingsManager.init(this)
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val nextState = !SettingsManager.isServiceEnabled
        SettingsManager.isServiceEnabled = nextState
        sendBroadcast(Intent("me.jaival.sponsorskip.TOGGLE_SERVICE"))
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isEnabled = SettingsManager.isServiceEnabled

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Sponsor Skip"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) "On" else "Off"
        }
        tile.updateTile()
    }
}
