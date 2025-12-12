package com.example.ledcontroller.ui.screens.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ledcontroller.service.AudioSyncService
import com.example.ledcontroller.R
import com.example.ledcontroller.viewmodel.MainViewModel
import com.example.ledcontroller.ui.components.VisualizerView
import com.example.ledcontroller.ui.components.PermissionRequestUI
import android.app.Activity
import android.view.WindowManager

// Helper function to check notification access
// Ideally move this to a util file like com.example.ledcontroller.util.PermissionUtils.kt
fun isNotificationServiceEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

// ------------------------------------------------------------------------
// --- MUSIC SCREEN (Visualizer + Metadata) ---
// ------------------------------------------------------------------------
@Composable
fun MusicSyncScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    // -----------------------------------------

    var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { hasNotificationAccess = isNotificationServiceEnabled(context) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { hasAudioPermission = it }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!hasAudioPermission) {
            PermissionRequestUI(Icons.Rounded.Mic, stringResource(R.string.perm_audio_title), stringResource(R.string.perm_audio_desc), stringResource(R.string.auth_button)) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        } else if (!hasNotificationAccess) {
            PermissionRequestUI(Icons.Rounded.Album, stringResource(R.string.perm_required_title), stringResource(R.string.perm_required_desc), stringResource(R.string.auth_button)) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        } else {
            MusicPlayerInterface(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MusicPlayerInterface(viewModel: MainViewModel) {
    // Collect flows from ViewModel
    val waveform by viewModel.waveform.collectAsState(initial = List(30) { 0.02f })
    val isMusicModeActive = viewModel.isMusicModeActive
    val trackTitle by viewModel.trackTitle.collectAsState()
    val artistName by viewModel.artistName.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val isExternalPlaying by viewModel.isExternalMusicPlaying.collectAsState()

    var isDiscoModeService by remember { mutableStateOf(AudioSyncService.isDiscoMode) }

    val currentEnergy = waveform.lastOrNull() ?: 0f
    val animatedPulse by animateFloatAsState(targetValue = currentEnergy, label = "pulse")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ZONE 1: COVER & VISUALIZER
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .shadow(elevation = (8 + (animatedPulse * 20)).dp, shape = RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = albumArt,
                        label = "AlbumArt",
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f)).togetherWith(
                                fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 1.05f)
                            )
                        }
                    ) { bmp ->
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(), contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MusicNote, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                    VisualizerView(waveform = waveform, modifier = Modifier.fillMaxSize().padding(12.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ZONE 2: INFO & CONTROLS
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(trackTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(if (artistName.isNotEmpty()) artistName else stringResource(R.string.music_waiting), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { viewModel.skipPrev() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(32.dp))
                }

                FilledIconButton(
                    onClick = { viewModel.playPause() },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(if (isExternalPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, Modifier.size(32.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ZONE 3: MODE & TOGGLE
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = { AudioSyncService.isDiscoMode = false; isDiscoModeService = false },
                    label = { Text(stringResource(R.string.mode_static), fontWeight = if(!isDiscoModeService) FontWeight.Bold else FontWeight.Normal) },
                    icon = { if (!isDiscoModeService) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (!isDiscoModeService) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        labelColor = if (!isDiscoModeService) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    border = if (!isDiscoModeService) null else SuggestionChipDefaults.suggestionChipBorder(enabled = true),
                    modifier = Modifier.weight(1f)
                )

                SuggestionChip(
                    onClick = { AudioSyncService.isDiscoMode = true; isDiscoModeService = true },
                    label = { Text(stringResource(R.string.scene_disco), fontWeight = if(isDiscoModeService) FontWeight.Bold else FontWeight.Normal) },
                    icon = { if (isDiscoModeService) Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isDiscoModeService) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        labelColor = if (isDiscoModeService) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        iconContentColor = if (isDiscoModeService) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    border = if (isDiscoModeService) null else SuggestionChipDefaults.suggestionChipBorder(enabled = true),
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                onClick = { if (isMusicModeActive) viewModel.stopMusicSync() else viewModel.startMusicSync() },
                shape = RoundedCornerShape(24.dp),
                color = if (isMusicModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(72.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMusicModeActive) stringResource(R.string.sync_active) else stringResource(R.string.sync_enable),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isMusicModeActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isMusicModeActive) stringResource(R.string.sync_active_desc) else stringResource(R.string.sync_enable_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMusicModeActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isMusicModeActive,
                        onCheckedChange = { if (it) viewModel.startMusicSync() else viewModel.stopMusicSync() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = Color.Transparent
                        ),
                        thumbContent = {
                            Icon(
                                imageVector = if (isMusicModeActive) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                tint = if (isMusicModeActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    )
                }
            }
        }
    }
}