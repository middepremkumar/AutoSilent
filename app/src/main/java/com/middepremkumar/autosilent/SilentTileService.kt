package com.middepremkumar.autosilent

import android.media.AudioManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Lets the user manually flip silent/normal from the notification shade, overriding the schedule. */
class SilentTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
        } else {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        updateTile()
    }

    private fun updateTile() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val isSilent = audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        qsTile?.state = if (isSilent) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.label = if (isSilent) getString(R.string.tile_label_silent) else getString(R.string.tile_label_normal)
        qsTile?.updateTile()
    }
}
