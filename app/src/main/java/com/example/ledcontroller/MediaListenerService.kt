package com.example.ledcontroller

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

// --- REPOSITORY (Bridge between Service and UI) ---
object MusicStateRepo {
    private val _trackTitle = MutableStateFlow("")
    val trackTitle = _trackTitle.asStateFlow()

    private val _artistName = MutableStateFlow("")
    val artistName = _artistName.asStateFlow()

    private val _albumArt = MutableStateFlow<Bitmap?>(null)
    val albumArt = _albumArt.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    var transportControls: MediaController.TransportControls? = null

    fun update(title: String?, artist: String?, bmp: Bitmap?, playing: Boolean) {
        // IMPORTANT CORRECTION:
        // Update text/image ONLY if title is provided (meaning it's a metadata update).
        // If title is null, it's just a state change (Play/Pause),
        // so we keep the old image and texts.
        if (title != null) {
            _trackTitle.value = title
            _artistName.value = artist ?: ""

            // Replace image (whether it's new or null)
            // This preserves previous cover if we don't change tracks
            _albumArt.value = bmp
        }

        // Play/Pause state is always updated
        _isPlaying.value = playing
    }
}

class MediaListenerService : NotificationListenerService() {

    private var mediaController: MediaController? = null

    // Scope for loading images (Soundcloud/Spotify URIs) without blocking
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onListenerConnected() {
        super.onListenerConnected()
        initMediaSessionListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun initMediaSessionListener() {
        val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaListenerService::class.java)

        try {
            sessionManager.addOnActiveSessionsChangedListener({ controllers ->
                processControllers(controllers)
            }, componentName)
            // Immediate initialization
            processControllers(sessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.e("MediaListener", "Missing notification permission")
        }
    }

    private fun processControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) return

        val controller = controllers.firstOrNull() ?: return
        MusicStateRepo.transportControls = controller.transportControls

        if (mediaController?.packageName != controller.packageName) {
            mediaController = controller
            registerCallback(controller)
        }

        // Force initial update
        updateMetadata(controller.metadata)
        updatePlaybackState(controller.playbackState)
    }

    private fun registerCallback(controller: MediaController) {
        controller.registerCallback(object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                updatePlaybackState(state)
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                updateMetadata(metadata)
            }
        })
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        if (metadata == null) return

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val isPlayingState = mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING

        // Launch coroutine to handle heavy image processing or URI (Soundcloud)
        serviceScope.launch(Dispatchers.IO) {
            val bitmap = resolveAlbumArt(metadata)

            withContext(Dispatchers.Main) {
                // Here we send EVERYTHING (Title + Image), so Repo will update image
                MusicStateRepo.update(title, artist, bitmap, isPlayingState)
            }
        }
    }

    // Robust function compatible with Soundcloud (URI) and Spotify (Bitmap/URI)
    private fun resolveAlbumArt(metadata: MediaMetadata): Bitmap? {
        // 1. Direct Bitmap (Legacy method)
        var bmp: Bitmap? = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (bmp == null) {
            bmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        }

        if (bmp != null) {
            return try {
                bmp.copy(Bitmap.Config.ARGB_8888, true)
            } catch (e: Exception) { null }
        }

        // 2. URIs (Soundcloud, newer Android versions)
        val artUriStr = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        if (!artUriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(artUriStr)
                return loadBitmapFromUri(uri)
            } catch (e: Exception) {
                Log.w("MediaListener", "Error loading URI: $artUriStr", e)
            }
        }
        return null
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING

        // CORRECTION HERE TOO:
        // Send null for title/artist/image.
        // Thanks to MusicStateRepo logic, this will update ONLY the Playing status
        // WITHOUT clearing the current cover art.
        MusicStateRepo.update(null, null, null, isPlaying)
    }
}