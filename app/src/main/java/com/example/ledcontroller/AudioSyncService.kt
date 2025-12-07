package com.example.ledcontroller

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import java.util.Random
import java.util.LinkedList

class AudioSyncService : Service() {

    private var audioJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false

    // WakeLock variable (Keeps CPU awake when screen is off)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        val waveformFlow = MutableStateFlow(List(30) { 0.02f })
        var isServiceRunning = false

        // --- SHARED SETTINGS ---
        var isDiscoMode = false
        var selectedStaticColor: Int = Color.WHITE
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Initialize WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LedController:AudioSyncWakeLock")

        // 2. RETRIEVE LAST COLOR (To avoid starting with white flash)
        try {
            val prefs = getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
            val savedHue = prefs.getFloat("saved_hue", 0f)
            val savedSat = prefs.getFloat("saved_sat", 0f)

            // Force brightness to 100% (1f) for base color,
            // as audio logic handles brightness variation.
            val hsv = floatArrayOf(savedHue, savedSat, 1f)
            selectedStaticColor = Color.HSVToColor(hsv)
        } catch (e: Exception) {
            selectedStaticColor = Color.WHITE
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopForegroundService()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            isRunning = true
            isServiceRunning = true

            // Acquire WakeLock: Max CPU Priority (10h timeout safety)
            try {
                wakeLock?.acquire(10 * 60 * 60 * 1000L)
            } catch (e: Exception) { e.printStackTrace() }

            startForegroundService()
            startAudioProcess()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val stopIntent = Intent(this, AudioSyncService::class.java).apply { action = "STOP" }
        val pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "LED_SYNC_CHANNEL")
            .setContentTitle(getString(R.string.notif_active_title))
            .setContentText(getString(R.string.notif_active_desc))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), pendingStopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun stopForegroundService() {
        isRunning = false
        isServiceRunning = false
        audioJob?.cancel()

        // Release CPU
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) { e.printStackTrace() }

        waveformFlow.value = List(30) { 0.02f }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}
        BleManager.disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun startAudioProcess() {
        audioJob = serviceScope.launch(Dispatchers.Default) {
            // MAX Audio Priority to prevent cuts
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            val sampleRate = 44100
            // Optimized buffer (2048 for reactivity, min 4096 for stability)
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = maxOf(minBuffer, 4096)

            var audioRecord: AudioRecord? = null

            try {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                audioRecord.startRecording()

                val buffer = ShortArray(bufferSize)

                // Audio Settings
                var maxRms = 2000f
                val MIN_SENSITIVITY = 2000f
                val decayRate = 0.99f

                // Logic Variables
                var currentHue = 0f
                var localAverage = 0f
                var lastBeatTime = 0L

                // Bluetooth Anti-Lag
                var lastBleSendTime = 0L
                val minDelayBetweenSends = 40L // Limit to ~25 FPS to save CPU when screen is off

                val waveHistory = LinkedList<Float>()
                repeat(30) { waveHistory.add(0.02f) }

                while (isRunning) {
                    val readSize = audioRecord.read(buffer, 0, bufferSize)
                    if (readSize > 0) {
                        // Optimized RMS calc (1 sample out of 2)
                        var sum = 0.0
                        for (i in 0 until readSize step 2) {
                            val sample = buffer[i]
                            sum += sample * sample
                        }
                        val rawRms = sqrt(sum / (readSize / 2)).toFloat()

                        // 1. AGC (Automatic Gain Control)
                        if (rawRms > maxRms) maxRms = rawRms
                        else maxRms = maxRms * decayRate
                        if (maxRms < MIN_SENSITIVITY) maxRms = MIN_SENSITIVITY

                        // 2. Amplitude normalization
                        val amplitude = (rawRms / maxRms).coerceIn(0f, 1f)

                        // UI Graph Update
                        waveHistory.removeFirst()
                        waveHistory.add(amplitude)
                        waveformFlow.emit(ArrayList(waveHistory))

                        // --- LED LOGIC (With Anti-Lag Protection) ---
                        val now = System.currentTimeMillis()

                        // Only send if enough time passed since last command
                        if (now - lastBleSendTime >= minDelayBetweenSends) {
                            localAverage = (localAverage * 0.9f) + (amplitude * 0.1f)
                            val isBeat = (amplitude > localAverage + 0.2f) && (amplitude > 0.4f) && (now - lastBeatTime > 250)

                            if (isDiscoMode) {
                                if (isBeat) {
                                    lastBeatTime = now
                                    currentHue = (currentHue + 45 + Random().nextInt(30)) % 360
                                    val hsv = floatArrayOf(currentHue, 0.6f, 1f)
                                    val colorInt = Color.HSVToColor(hsv)
                                    BleManager.sendColor(Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
                                    lastBleSendTime = now
                                } else {
                                    val displayBri = amplitude * amplitude
                                    if (displayBri > 0.02f) {
                                        val hsv = floatArrayOf(currentHue, 1f, displayBri)
                                        val colorInt = Color.HSVToColor(hsv)
                                        BleManager.sendColor(Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
                                        lastBleSendTime = now
                                    } else {
                                        BleManager.sendColor(0, 0, 0)
                                        lastBleSendTime = now
                                    }
                                }
                            } else {
                                // STATIC MODE (Uses color loaded in onCreate)
                                val displayBri = amplitude * amplitude
                                if (displayBri > 0.02f) {
                                    val r = (Color.red(selectedStaticColor) * displayBri).toInt()
                                    val g = (Color.green(selectedStaticColor) * displayBri).toInt()
                                    val b = (Color.blue(selectedStaticColor) * displayBri).toInt()
                                    BleManager.sendColor(r, g, b)
                                    lastBleSendTime = now
                                } else {
                                    BleManager.sendColor(0, 0, 0)
                                    lastBleSendTime = now
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) {}
                BleManager.sendColor(0,0,0)
                waveformFlow.value = List(30) { 0.02f }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LED_SYNC_CHANNEL",
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}