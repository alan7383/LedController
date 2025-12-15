package com.example.ledcontroller.service

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.ledcontroller.util.BleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LedTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        val prefs = getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        val isPowerOn = prefs.getBoolean("saved_power", false)

        qsTile?.let { updateTileUi(it, isPowerOn) }
    }

    override fun onClick() {
        super.onClick()

        val prefs = getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        val wasOn = prefs.getBoolean("saved_power", false)
        val newState = !wasOn

        qsTile?.let { updateTileUi(it, newState) }

        prefs.edit().putBoolean("saved_power", newState).apply()

        serviceScope.launch {
            if (newState) {
                val hue = prefs.getFloat("saved_hue", 0f)
                val sat = prefs.getFloat("saved_sat", 0f)
                val bri = prefs.getFloat("saved_bri", 1f)
                val colorInt = Color.HSVToColor(floatArrayOf(hue, sat, bri))

                BleManager.executeCommand(
                    applicationContext,
                    Color.red(colorInt),
                    Color.green(colorInt),
                    Color.blue(colorInt)
                )
            } else {
                BleManager.executeCommand(applicationContext, 0, 0, 0)
            }
        }
    }

    private fun updateTileUi(tile: Tile, isPowerOn: Boolean) {
        tile.state = if (isPowerOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isPowerOn) "On" else "Off"
        }

        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}