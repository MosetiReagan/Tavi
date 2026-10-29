package com.example.tavi.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.tavi.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class TaviQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_FROM_ASSISTANT_INVOCATION", true)
        }
        if (isLocked) {
            unlockAndRun {
                startActivityAndCollapse(intent)
            }
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
