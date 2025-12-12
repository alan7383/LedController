package com.example.ledcontroller.service

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.ledcontroller.util.BleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LedTileService : TileService() {

    // Scope pour lancer les commandes Bluetooth en arrière-plan
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    // Appelé quand l'utilisateur ouvre le panneau de configuration rapide
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    // Appelé quand l'utilisateur clique sur l'icône
    override fun onClick() {
        super.onClick()

        // 1. Récupérer l'état actuel et inverser
        val prefs = getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        val wasOn = prefs.getBoolean("saved_power", false)
        val newState = !wasOn

        // 2. Mettre à jour l'UI de la tuile tout de suite (pour la réactivité perçue)
        val tile = qsTile
        tile.state = if (newState) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()

        // 3. Sauvegarder le nouvel état
        prefs.edit().putBoolean("saved_power", newState).apply()

        // 4. Envoyer la commande Bluetooth (En arrière-plan)
        serviceScope.launch {
            if (newState) {
                // Si on allume : on récupère la dernière couleur sauvegardée
                val hue = prefs.getFloat("saved_hue", 0f)
                val sat = prefs.getFloat("saved_sat", 0f)
                val bri = prefs.getFloat("saved_bri", 1f)

                // Conversion HSV vers RGB
                val colorInt = Color.HSVToColor(floatArrayOf(hue, sat, bri))
                val r = Color.red(colorInt)
                val g = Color.green(colorInt)
                val b = Color.blue(colorInt)

                // On envoie la commande (BleManager gère la reconnexion auto)
                BleManager.executeCommand(applicationContext, r, g, b)
            } else {
                // Si on éteint : on envoie du noir (0,0,0)
                BleManager.executeCommand(applicationContext, 0, 0, 0)
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        val isPowerOn = prefs.getBoolean("saved_power", false)

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