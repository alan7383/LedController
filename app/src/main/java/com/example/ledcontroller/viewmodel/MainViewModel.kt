package com.example.ledcontroller.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledcontroller.AudioSyncService
import com.example.ledcontroller.MusicStateRepo
import com.example.ledcontroller.util.BleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val bleManager = BleManager

    // Using waveform from AudioSyncService
    val waveform = AudioSyncService.waveformFlow

    val trackTitle = MusicStateRepo.trackTitle
    val artistName = MusicStateRepo.artistName
    val albumArt = MusicStateRepo.albumArt
    val isExternalMusicPlaying = MusicStateRepo.isPlaying

    var isMusicModeActive by mutableStateOf(false)
    var isAmoledMode by mutableStateOf(false)
    var themeMode by mutableIntStateOf(0)

    init {
        BleManager.init(application)
        val sharedPref = application.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        bleManager.targetDeviceAddress = sharedPref.getString("last_device_address", null)
        isAmoledMode = sharedPref.getBoolean("amoled_mode", false)

        try {
            themeMode = sharedPref.getInt("theme_mode", 0)
        } catch (e: ClassCastException) {
            sharedPref.edit().putInt("theme_mode", 0).apply()
            themeMode = 0
        }

        // Check if service is already running
        isMusicModeActive = AudioSyncService.isServiceRunning
    }

    fun updateTheme(mode: Int, context: Context) {
        themeMode = mode
        context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
            .edit().putInt("theme_mode", mode).apply()
    }

    fun toggleAmoled(enable: Boolean, context: Context) {
        isAmoledMode = enable
        context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("amoled_mode", enable).apply()
    }

    fun skipNext() = MusicStateRepo.transportControls?.skipToNext()
    fun skipPrev() = MusicStateRepo.transportControls?.skipToPrevious()
    fun playPause() {
        if (isExternalMusicPlaying.value) MusicStateRepo.transportControls?.pause()
        else MusicStateRepo.transportControls?.play()
    }

    fun startMusicSync() {
        if (isMusicModeActive) return
        isMusicModeActive = true

        val intent = Intent(getApplication(), AudioSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun stopMusicSync(turnOffLights: Boolean = true) {
        if (!isMusicModeActive) return
        isMusicModeActive = false

        // Stop the SERVICE
        val intent = Intent(getApplication(), AudioSyncService::class.java).apply {
            action = "STOP"
        }
        getApplication<Application>().startService(intent)

        if (turnOffLights) {
            viewModelScope.launch(Dispatchers.IO) {
                delay(100)
                bleManager.sendColor(0, 0, 0)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}