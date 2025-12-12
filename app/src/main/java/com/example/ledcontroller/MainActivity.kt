package com.example.ledcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.*
import com.example.ledcontroller.BuildConfig

// ------------------------------------------------------------------------
// --- CUSTOM THEME WRAPPERS ---
// ------------------------------------------------------------------------

@Composable
fun AppTheme(
    themeMode: Int,
    useAmoled: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    var colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    // Apply AMOLED Black override if enabled and in dark mode
    if (useAmoled && darkTheme) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color.Black,
            surfaceVariant = Color(0xFF121212),
            onBackground = Color.White,
            onSurface = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(), // Ensure Typography is defined in Type.kt
        content = content
    )
}

// ------------------------------------------------------------------------
// --- VIEWMODEL ---
// ------------------------------------------------------------------------
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

// ------------------------------------------------------------------------
// --- MAIN ACTIVITY ---
// ------------------------------------------------------------------------
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ENABLE EDGE-TO-EDGE (Essential for Modern Android Look)
        enableEdgeToEdge()

        setContent {
            AppTheme(themeMode = viewModel.themeMode, useAmoled = viewModel.isAmoledMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppContent(viewModel)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// --- NAVIGATION & UTILS ---
// ------------------------------------------------------------------------
fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

fun checkPermissions(context: Context): Boolean {
    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                hasLocation
    } else {
        hasLocation
    }
}

enum class AppScreen { Home, Settings }

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedPref = remember { context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE) }
    var isSetupDone by remember { mutableStateOf(sharedPref.getBoolean("is_setup_done", false)) }

    // Navigation State
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }

    // Bluetooth Launcher logic
    val enableBluetoothLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.bleManager.startScan(manual = false)
    }
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isSetupDone) {
                if (checkPermissions(context)) {
                    if (bluetoothAdapter?.isEnabled == false) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        enableBluetoothLauncher.launch(enableBtIntent)
                    } else if (viewModel.bleManager.connectionState.value == BleManager.ConnectionState.DISCONNECTED) {
                        if (viewModel.bleManager.targetDeviceAddress != null) viewModel.bleManager.startScan(manual = false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!isSetupDone) {
        OnboardingScreen { name ->
            sharedPref.edit().putBoolean("is_setup_done", true).putString("room_name", name).apply()
            isSetupDone = true
        }
    } else {
        // Simple screen transition
        AnimatedContent(
            targetState = currentScreen,
            label = "NavTransition",
            transitionSpec = {
                if (targetState == AppScreen.Settings) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
                }
            }
        ) { screen ->
            when (screen) {
                AppScreen.Home -> {
                    val defaultName = stringResource(R.string.room_name_placeholder)
                    val roomName = sharedPref.getString("room_name", defaultName) ?: defaultName
                    HomeScreenWithBottomNav(viewModel, roomName) { currentScreen = AppScreen.Settings }
                }
                AppScreen.Settings -> {
                    val defaultName = stringResource(R.string.room_name_placeholder)
                    var roomName by remember { mutableStateOf(sharedPref.getString("room_name", defaultName) ?: defaultName) }

                    SettingsScreen(
                        viewModel = viewModel,
                        roomName = roomName,
                        onBack = { currentScreen = AppScreen.Home },
                        onRoomNameChange = { newName ->
                            roomName = newName
                            sharedPref.edit().putString("room_name", newName).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreenWithBottomNav(viewModel: MainViewModel, roomName: String, onOpenSettings: () -> Unit) {
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp // Flat for Amoled look
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_control)) },
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Equalizer, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_music)) },
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Crossfade(targetState = currentTab, label = "TabSwitch") { tab ->
                if (tab == 0) {
                    ProControlScreen(viewModel.bleManager, roomName, viewModel, onOpenSettings)
                } else {
                    MusicSyncScreen(viewModel)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// --- MUSIC SCREEN (Visualizer + Metadata) ---
// ------------------------------------------------------------------------
@Composable
fun MusicSyncScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
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

@Composable
fun PermissionRequestUI(icon: ImageVector, title: String, desc: String, buttonText: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(80.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(desc, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MusicPlayerInterface(viewModel: MainViewModel) {
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

@Composable
fun VisualizerView(waveform: List<Float>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val barCount = 29
    val animations = remember { List(barCount) { Animatable(0f) } }

    LaunchedEffect(waveform) {
        val centerIndex = barCount / 2
        animations.forEachIndexed { index, animatable ->
            val distFromCenter = kotlin.math.abs(index - centerIndex)
            val waveIndex = (waveform.size - 1 - distFromCenter).coerceAtLeast(0)
            val rawAmplitude = waveform.getOrElse(waveIndex) { 0f }
            val scaleFactor = 1f - (distFromCenter.toFloat() / centerIndex.toFloat()).pow(1.5f)
            val targetValue = (rawAmplitude * scaleFactor).coerceIn(0.01f, 1f)

            launch {
                animatable.animateTo(targetValue, animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing))
            }
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val totalBarWidth = width / barCount
        val barWidth = totalBarWidth * 0.6f
        val maxHeight = height * 0.9f

        animations.forEachIndexed { index, animatable ->
            val x = (index * totalBarWidth) + (totalBarWidth / 2)
            val barHeight = (animatable.value * maxHeight).coerceAtLeast(6f)
            drawLine(
                color = barColor,
                start = Offset(x, (height / 2) - (barHeight / 2)),
                end = Offset(x, (height / 2) + (barHeight / 2)),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

// ------------------------------------------------------------------------
// --- PRO CONTROL SCREEN & PRESETS ---
// ------------------------------------------------------------------------

enum class PresetIcon(val icon: ImageVector, val labelRes: Int) {
    SUNNY(Icons.Rounded.WbSunny, R.string.icon_sunny), FIRE(Icons.Rounded.LocalFireDepartment, R.string.icon_fire),
    WATER(Icons.Rounded.WaterDrop, R.string.icon_water), FOREST(Icons.Rounded.Forest, R.string.icon_forest),
    NIGHT(Icons.Rounded.Bedtime, R.string.icon_night), STAR(Icons.Rounded.Star, R.string.icon_star),
    HEART(Icons.Rounded.Favorite, R.string.icon_love), BOLT(Icons.Rounded.Bolt, R.string.icon_energy),
    HOME(Icons.Rounded.Home, R.string.icon_home), LIVING(Icons.Rounded.Weekend, R.string.icon_living),
    KITCHEN(Icons.Rounded.Kitchen, R.string.icon_kitchen), BATH(Icons.Rounded.Bathtub, R.string.icon_bath),
    BEDROOM(Icons.Rounded.SingleBed, R.string.icon_bedroom), DESK(Icons.Rounded.Desk, R.string.icon_desk),
    GAME(Icons.Rounded.Gamepad, R.string.icon_game), MOVIE(Icons.Rounded.Movie, R.string.icon_movie),
    BOOK(Icons.Rounded.MenuBook, R.string.icon_book), MUSIC(Icons.Rounded.MusicNote, R.string.icon_music),
    WORK(Icons.Rounded.Work, R.string.icon_work), COMPUTER(Icons.Rounded.Computer, R.string.icon_computer),
    PARTY(Icons.Rounded.Celebration, R.string.icon_party), COFFEE(Icons.Rounded.Coffee, R.string.icon_coffee)
}

data class LightPreset(val id: String = UUID.randomUUID().toString(), val name: String, val hue: Float, val sat: Float, val bri: Float, val iconName: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onSetupComplete: (String) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var roomName by remember { mutableStateOf("") }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) step = 1 else Toast.makeText(context, context.getString(R.string.perm_required_desc), Toast.LENGTH_LONG).show()
    }
    val suggestions = listOf(
        R.string.icon_living, R.string.icon_bedroom, R.string.icon_kitchen,
        R.string.icon_desk, R.string.icon_game, R.string.icon_bath
    ).map { stringResource(it) }

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AnimatedContent(targetState = step, label = "Onboarding") { currentStep ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (currentStep == 0) {
                        Icon(Icons.Rounded.Lightbulb, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.welcome_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                PermissionItem(Icons.Rounded.Bluetooth, stringResource(R.string.permission_bt_title), stringResource(R.string.permission_bt_desc))
                                PermissionItem(Icons.Rounded.LocationOn, stringResource(R.string.permission_loc_title), stringResource(R.string.permission_loc_desc))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                perms.add(Manifest.permission.BLUETOOTH_SCAN)
                                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.auth_button)) }
                    } else {
                        Icon(Icons.Rounded.Home, null, Modifier.size(64.dp), MaterialTheme.colorScheme.tertiary)
                        Text(stringResource(R.string.where_leds), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = roomName, onValueChange = { roomName = it }, label = { Text(stringResource(R.string.room_name_label)) }, placeholder = { Text(stringResource(R.string.room_name_placeholder)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Text(stringResource(R.string.suggestions), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEach { s -> FilterChip(selected = roomName == s, onClick = { roomName = s }, label = { Text(s) }) }
                        }
                        Spacer(Modifier.height(16.dp))
                        val defaultRoom = stringResource(R.string.icon_living)
                        Button(onClick = { onSetupComplete(roomName.ifBlank { defaultRoom }) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.finish_button)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProControlScreen(bleManager: BleManager, initialRoomName: String, viewModel: MainViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val connectionState by bleManager.connectionState.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()

    // TopAppBar Scroll Behavior for Collapsing Toolbar effect
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val sharedPref = remember { context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    var roomName by remember { mutableStateOf(sharedPref.getString("room_name", initialRoomName) ?: initialRoomName) }
    var hue by remember { mutableFloatStateOf(sharedPref.getFloat("saved_hue", 0f)) }
    var saturation by remember { mutableFloatStateOf(sharedPref.getFloat("saved_sat", 0f)) }
    var brightness by remember { mutableFloatStateOf(sharedPref.getFloat("saved_bri", 1f)) }
    var isPowerOn by remember { mutableStateOf(sharedPref.getBoolean("saved_power", false)) }
    var isDiscoMode by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeviceListDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<LightPreset?>(null) }

    val scaffoldState = rememberBottomSheetScaffoldState()
    val scannedDevices by bleManager.scannedDevices.collectAsState()

    // Auto open scan if no device
    LaunchedEffect(scannedDevices) {
        if (bleManager.targetDeviceAddress == null &&
            connectionState == BleManager.ConnectionState.DISCONNECTED &&
            scannedDevices.isNotEmpty() && !showDeviceListDialog
        ) {
            showDeviceListDialog = true
        }
    }

    // Auto Save
    LaunchedEffect(hue, saturation, brightness, isPowerOn) {
        sharedPref.edit()
            .putFloat("saved_hue", hue)
            .putFloat("saved_sat", saturation)
            .putFloat("saved_bri", brightness)
            .putBoolean("saved_power", isPowerOn)
            .apply()
    }

    // Preset Management
    val defaultPresets = listOf(
        LightPreset(name = context.getString(R.string.preset_white), hue = 0f, sat = 0f, bri = 1f, iconName = "SUNNY"),
        LightPreset(name = context.getString(R.string.preset_reading), hue = 30f, sat = 0.6f, bri = 0.8f, iconName = "BOOK"),
        LightPreset(name = context.getString(R.string.preset_cinema), hue = 240f, sat = 0.9f, bri = 0.3f, iconName = "MOVIE"),
        LightPreset(name = context.getString(R.string.preset_night), hue = 15f, sat = 1f, bri = 0.1f, iconName = "BED"),
        LightPreset(name = context.getString(R.string.preset_gaming), hue = 280f, sat = 1f, bri = 1f, iconName = "GAME")
    )

    var presets by remember {
        mutableStateOf(
            try {
                val json = sharedPref.getString("saved_presets", null)
                if (json != null) {
                    val type = object : TypeToken<List<LightPreset>>() {}.type
                    gson.fromJson<List<LightPreset>>(json, type)
                } else defaultPresets
            } catch (e: Exception) { defaultPresets }
        )
    }

    LaunchedEffect(presets) {
        val json = gson.toJson(presets)
        sharedPref.edit().putString("saved_presets", json).apply()
    }

    val pureColors = listOf(
        Color.Red to 0f, Color(0xFFFFA500) to 30f, Color.Yellow to 60f,
        Color.Green to 120f, Color.Cyan to 180f, Color.Blue to 240f,
        Color.Magenta to 300f, Color.White to 0f
    )

    fun performHaptic() { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }

    fun updateLight(h: Float, s: Float, b: Float) {
        if (!isDiscoMode) {
            val color = Color.hsv(h, s, b)
            bleManager.sendColor((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        }
    }

    // Local Disco Loop
    LaunchedEffect(isDiscoMode, isPowerOn) {
        if (isDiscoMode && isPowerOn) {
            var dHue = 0f
            while (isActive) {
                dHue = (dHue + 10) % 360
                val c = Color.hsv(dHue, 1f, brightness)
                bleManager.sendColor((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                delay(50)
            }
        }
    }

    // Real-time BLE color sending
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(hue, saturation, brightness) }.conflate().collect { (h, s, b) ->
            if (connectionState == BleManager.ConnectionState.CONNECTED && !isDiscoMode && isPowerOn && !viewModel.isMusicModeActive) {
                updateLight(h, s, b)
                delay(30)
            }
        }
    }

    // Power ON/OFF Management
    LaunchedEffect(isPowerOn, connectionState) {
        if (connectionState == BleManager.ConnectionState.CONNECTED && !isDiscoMode) {
            if (isPowerOn) {
                if (!viewModel.isMusicModeActive) {
                    delay(200)
                    updateLight(hue, saturation, brightness)
                }
            } else {
                bleManager.sendColor(0, 0, 0)
            }
        }
    }

    // UI
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        // Attach nested scroll so the TopAppBar collapses
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(roomName, fontWeight = FontWeight.Bold)
                        if (connectionState == BleManager.ConnectionState.CONNECTED) {
                            Text(stringResource(R.string.connected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        } else if (connectionState == BleManager.ConnectionState.CONNECTING || isScanning) {
                            Text(stringResource(R.string.scanning), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        } else {
                            Text(stringResource(R.string.disconnect), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showDeviceListDialog = true }) {
                        Icon(
                            if (connectionState == BleManager.ConnectionState.CONNECTED) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothSearching,
                            "Devices",
                            tint = if (connectionState == BleManager.ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        sheetDragHandle = null,
        sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        sheetContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface),
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetTonalElevation = 0.dp,
        sheetPeekHeight = 150.dp,
        sheetContent = {
            Box(modifier = Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                ) {
                    // Drag Handle Indicator
                    Box(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            .align(Alignment.CenterHorizontally)
                    )

                    // BRIGHTNESS SLIDER
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.BrightnessLow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Slider(
                            value = brightness,
                            onValueChange = {
                                brightness = it
                                if (!isPowerOn && it > 0) isPowerOn = true
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        )
                        Icon(Icons.Rounded.BrightnessHigh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(stringResource(R.string.pure_colors).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(pureColors) { (color, h) ->
                            Box(
                                modifier = Modifier.size(46.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(color).border(1.dp, Color(0xFF333333), CircleShape)
                                    .clickable {
                                        performHaptic()
                                        hue = h
                                        saturation = if (color == Color.White) 0f else 1f
                                        isPowerOn = true
                                        isDiscoMode = false
                                        AudioSyncService.selectedStaticColor = Color.hsv(h, if (color == Color.White) 0f else 1f, 1f).toArgb()
                                    }
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.my_presets).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Surface(onClick = { showAddDialog = true }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.height(32.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.add), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        item {
                            PresetChip(
                                stringResource(R.string.scene_disco),
                                Icons.Rounded.AutoAwesome,
                                MaterialTheme.colorScheme.primary,
                                isDiscoMode,
                                { isDiscoMode = !isDiscoMode; if (!isPowerOn) isPowerOn = true; performHaptic() }
                            )
                        }
                        items(presets, key = { it.id }) { preset ->
                            val iconVector = try { PresetIcon.valueOf(preset.iconName).icon } catch (e: Exception) { Icons.Rounded.Lightbulb }
                            val iconColor = if (preset.sat < 0.1f) MaterialTheme.colorScheme.onSurface else Color.hsv(preset.hue, preset.sat, 1f)

                            PresetChip(
                                name = preset.name,
                                icon = iconVector,
                                color = iconColor,
                                isSelected = false,
                                onClick = {
                                    hue = preset.hue; saturation = preset.sat; brightness = preset.bri; isPowerOn = true; isDiscoMode = false; performHaptic()
                                    AudioSyncService.selectedStaticColor = Color.hsv(preset.hue, preset.sat, 1f).toArgb()
                                },
                                onLongClick = { performHaptic(); presetToDelete = preset }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                ColorWheel(Modifier.size(300.dp), brightness, hue, saturation) { h, s ->
                    hue = h; saturation = s; if (!isPowerOn) isPowerOn = true; isDiscoMode = false
                    AudioSyncService.selectedStaticColor = Color.hsv(h, s, 1f).toArgb()
                }

                FilledIconToggleButton(
                    checked = isPowerOn, onCheckedChange = { isPowerOn = it; performHaptic() },
                    modifier = Modifier.size(80.dp).shadow(15.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Color(0xFF151515), contentColor = Color.Gray, checkedContainerColor = Color.White, checkedContentColor = Color.Black)
                ) { Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(32.dp)) }
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    if (showDeviceListDialog) DeviceSelectionDialog(bleManager, { showDeviceListDialog = false }) { device -> bleManager.targetDeviceAddress = device.address; sharedPref.edit().putString("last_device_address", device.address).apply(); bleManager.connectToDevice(device); showDeviceListDialog = false }
    if (presetToDelete != null) AlertDialog(onDismissRequest = { presetToDelete = null }, icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_confirm, presetToDelete?.name ?: "")) }, confirmButton = { Button(onClick = { presets = presets.filter { it.id != presetToDelete?.id }; presetToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { presetToDelete = null }) { Text(stringResource(R.string.cancel)) } })
    if (showAddDialog) AddPresetDialog(hue, saturation, brightness, { showAddDialog = false }, { newPreset -> presets = presets + newPreset; showAddDialog = false })
}

@Composable
fun DeviceSelectionDialog(bleManager: BleManager, onDismiss: () -> Unit, onDeviceSelected: (android.bluetooth.BluetoothDevice) -> Unit) {
    val context = LocalContext.current
    val scannedDevices by bleManager.scannedDevices.collectAsState()
    val connectedDevice by bleManager.connectedDevice.collectAsState()
    val connectionState by bleManager.connectionState.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()
    val sharedPref = remember { context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) { bleManager.startScan(manual = true) }
    DisposableEffect(Unit) { onDispose { bleManager.stopScan() } }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { bleManager.startScan(manual = true) }, enabled = !isScanning) {
                        if (isScanning) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (connectionState == BleManager.ConnectionState.CONNECTED && connectedDevice != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.BluetoothConnected, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.width(16.dp))
                                Column { Text(connectedDevice?.name ?: stringResource(R.string.unknown_device), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(connectedDevice?.address ?: "", style = MaterialTheme.typography.bodySmall) }
                            }
                            Button(onClick = { bleManager.disconnect(); bleManager.targetDeviceAddress = null; sharedPref.edit().remove("last_device_address").apply(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ bleManager.startScan(manual = true) }, 1000) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect)) }
                        }
                    }
                    Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.scanning_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp))
                } else if (isScanning && scannedDevices.isEmpty()) { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.scanning_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Spacer(Modifier.height(16.dp)) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scannedDevices) { result -> if (result.device.address != connectedDevice?.address) DeviceItem(result, onClick = { onDeviceSelected(result.device) }) }
                }
                Spacer(Modifier.height(16.dp)); TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(scanResult: ScanResult, onClick: () -> Unit) {
    val deviceName = scanResult.device.name ?: stringResource(R.string.unknown_device)
    val rssi = scanResult.rssi
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bluetooth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(deviceName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Text(scanResult.device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            Column(horizontalAlignment = Alignment.End) { Icon(imageVector = Icons.Rounded.SignalWifi4Bar, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (rssi > -70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline); Text(stringResource(R.string.signal, rssi), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun ColorWheel(modifier: Modifier = Modifier, brightness: Float = 1f, currentHue: Float, currentSat: Float, onColorChanged: (hue: Float, saturation: Float) -> Unit) {
    var inputSize by remember { mutableStateOf(IntSize.Zero) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    val view = LocalView.current

    LaunchedEffect(inputSize, currentHue, currentSat) {
        if (inputSize != IntSize.Zero) {
            val center = Offset(inputSize.width / 2f, inputSize.height / 2f)
            val radius = min(inputSize.width, inputSize.height) / 2f
            if (currentSat == 0f) touchPosition = center
            else {
                val angleRad = Math.toRadians(currentHue.toDouble())
                val dist = currentSat * radius
                touchPosition = Offset(x = (center.x + dist * cos(angleRad)).toFloat(), y = (center.y + dist * sin(angleRad)).toFloat())
            }
        }
    }

    Canvas(modifier = modifier.aspectRatio(1f).onSizeChanged { inputSize = it }
        .pointerInput(Unit) {
            detectTapGestures(onPress = { offset ->
                val center = Offset(inputSize.width / 2f, inputSize.height / 2f)
                val radius = min(inputSize.width, inputSize.height) / 2f
                val dx = offset.x - center.x
                val dy = offset.y - center.y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance <= radius + 50) {
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (angle < 0) angle += 360f
                    val rawSaturation = (distance / radius).coerceIn(0f, 1f)
                    val saturation = if (rawSaturation > 0.95f) 1f else rawSaturation
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onColorChanged(angle, saturation)
                }
            })
        }
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val center = Offset(inputSize.width / 2f, inputSize.height / 2f)
                val radius = min(inputSize.width, inputSize.height) / 2f
                val position = change.position
                val dx = position.x - center.x
                val dy = position.y - center.y
                val distance = sqrt(dx * dx + dy * dy)
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle < 0) angle += 360f
                val rawSaturation = (distance / radius).coerceIn(0f, 1f)
                val saturation = if (rawSaturation > 0.95f) 1f else rawSaturation
                onColorChanged(angle, saturation)
            }
        }) {
        val center = center
        val radius = size.minDimension / 2
        val sweepGradient = Brush.sweepGradient(colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red), center = center)
        drawCircle(brush = sweepGradient, radius = radius)
        val radialGradient = Brush.radialGradient(colors = listOf(Color.White, Color.Transparent), center = center, radius = radius)
        drawCircle(brush = radialGradient, radius = radius)
        drawCircle(color = Color.Black.copy(alpha = 1f - brightness), radius = radius)
        touchPosition?.let { pos ->
            drawCircle(color = Color.White, radius = 12.dp.toPx(), center = pos, style = Stroke(width = 3.dp.toPx()))
            drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = 14.dp.toPx(), center = pos, style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetChip(name: String, icon: ImageVector, color: Color, isSelected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else color.copy(alpha = 1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun AddPresetDialog(initialHue: Float, initialSat: Float, initialBri: Float, onDismiss: () -> Unit, onSave: (LightPreset) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(PresetIcon.HEART) }
    var hue by remember { mutableFloatStateOf(initialHue) }
    var sat by remember { mutableFloatStateOf(initialSat) }
    var bri by remember { mutableFloatStateOf(initialBri) }
    val focusManager = LocalFocusManager.current

    fun getHex(h: Float, s: Float): String {
        val c = Color.hsv(h, s, 1f)
        return String.format("#%02X%02X%02X", (c.red*255).toInt(), (c.green*255).toInt(), (c.blue*255).toInt())
    }
    var hexCode by remember { mutableStateOf(getHex(initialHue, initialSat)) }
    var hexError by remember { mutableStateOf(false) }

    LaunchedEffect(hue, sat) { hexCode = getHex(hue, sat) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.new_preset_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.room_name_label)) },
                        singleLine = true, shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(selectedIcon.icon, null) }, modifier = Modifier.fillMaxWidth()
                    )

                    Box(contentAlignment = Alignment.Center) {
                        ColorWheel(modifier = Modifier.size(220.dp), brightness = 1f, currentHue = hue, currentSat = sat) { h, s -> hue = h; sat = s; focusManager.clearFocus() }
                        Box(modifier = Modifier.size(50.dp).shadow(8.dp, CircleShape).background(Color.hsv(hue, sat, 1f), CircleShape).border(2.dp, Color.White, CircleShape))
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Brightness6, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                                Slider(value = bri, onValueChange = { bri = it }, modifier = Modifier.weight(1f))
                                Text("${(bri*100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(35.dp), textAlign = TextAlign.End)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Tag, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                                BasicTextField(
                                    value = hexCode,
                                    onValueChange = { input ->
                                        val upper = input.uppercase(Locale.ROOT)
                                        hexCode = upper
                                        if (upper.length == 7 && upper.startsWith("#")) {
                                            try {
                                                val c = android.graphics.Color.parseColor(upper)
                                                val h = FloatArray(3)
                                                android.graphics.Color.colorToHSV(c, h)
                                                hue = h[0]; sat = h[1]; hexError = false
                                            } catch (e: Exception) { hexError = true }
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (hexError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                                    singleLine = true,
                                    decorationBox = { inner -> Box(Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) { if (hexCode.isEmpty()) Text("#", color = MaterialTheme.colorScheme.outline); inner() } },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.choose_icon), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            items(PresetIcon.values()) { icon ->
                                val sel = selectedIcon == icon
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clip(RoundedCornerShape(12.dp)).clickable { selectedIcon = icon }.padding(vertical = 8.dp)) {
                                    Box(modifier = Modifier.size(48.dp).background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape).border(if (sel) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(icon.icon, null, tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Spacer(Modifier.height(4.dp))
                                    Text(stringResource(icon.labelRes), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (name.isNotBlank()) onSave(LightPreset(name = name, hue = hue, sat = sat, bri = bri, iconName = selectedIcon.name)) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// --- SETTINGS SCREEN (UPDATED FOR M3) ---
// ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    roomName: String,
    onBack: () -> Unit,
    onRoomNameChange: (String) -> Unit
) {
    val context = LocalContext.current
    // M3 Scroll Behavior for Collapsing Toolbar
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 16.dp)
        ) {

            // --- GENERAL SECTION ---
            item {
                SettingsGroupTitle(stringResource(R.string.settings_category_general))
                SettingsGroupCard {
                    SettingsItem(
                        title = stringResource(R.string.room_name_label),
                        subtitle = roomName,
                        icon = Icons.Rounded.Edit,
                        onClick = { showRenameDialog = true }
                    )
                }
            }

            // --- APPEARANCE SECTION ---
            item {
                SettingsGroupTitle(stringResource(R.string.settings_category_appearance))
                SettingsGroupCard {
                    SettingsItem(
                        title = stringResource(R.string.theme_title),
                        subtitle = when(viewModel.themeMode) {
                            1 -> stringResource(R.string.theme_light)
                            2 -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.system_default)
                        },
                        icon = Icons.Rounded.BrightnessMedium,
                        onClick = { showThemeDialog = true }
                    )

                    // Native M3 ListItem Switch
                    SettingsItem(
                        title = stringResource(R.string.amoled_mode),
                        subtitle = stringResource(R.string.amoled_desc),
                        icon = Icons.Rounded.DarkMode,
                        hasSwitch = true,
                        switchState = viewModel.isAmoledMode,
                        onSwitchChange = { viewModel.toggleAmoled(it, context) }
                    )

                    SettingsItem(
                        title = stringResource(R.string.language),
                        subtitle = getCurrentLanguageName(),
                        icon = Icons.Rounded.Language,
                        onClick = { showLanguageDialog = true }
                    )
                }
            }

            // --- ABOUT SECTION ---
            item {
                SettingsGroupTitle(stringResource(R.string.settings_category_about))
                SettingsGroupCard {
                    SettingsItem(
                        title = stringResource(R.string.app_name),
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                        icon = Icons.Rounded.Info,
                        onClick = {
                            versionClickCount++
                            if (versionClickCount >= 7) {
                                versionClickCount = 0
                                showEasterEggDialog = true
                            }
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.dev_passion),
                        subtitle = stringResource(R.string.github_link),
                        icon = Icons.Rounded.Code,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/alan7383"))
                            context.startActivity(intent)
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.licenses),
                        subtitle = stringResource(R.string.licenses_desc),
                        icon = Icons.Rounded.Description,
                        onClick = { showLicensesDialog = true }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showRenameDialog) RenameDialog(currentName = roomName, onDismiss = { showRenameDialog = false }) { newName -> onRoomNameChange(newName); showRenameDialog = false }
    if (showLanguageDialog) LanguageSelectionDialog { showLanguageDialog = false }
    if (showThemeDialog) ThemeSelectionDialog(viewModel.themeMode, { showThemeDialog = false }) { viewModel.updateTheme(it, context); showThemeDialog = false }
    if (showEasterEggDialog) EasterEggDialog(onDismiss = { showEasterEggDialog = false })
    if (showLicensesDialog) LicensesScreen(onDismiss = { showLicensesDialog = false })
}

// ------------------------------------------------------------------------
// --- UTILITY COMPONENTS (NATIVE M3) ---
// ------------------------------------------------------------------------

@Composable
fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
    )
}

/**
 * Modern Card container for settings.
 * Uses `surfaceContainerHigh` (or fallback) to match Google Settings style.
 */
@Composable
fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            // Try to use 'surfaceContainerHigh' if available in your theme, otherwise use surfaceVariant
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/**
 * Wrapper around the official Material 3 ListItem.
 * Provides standard heights, padding, and alignment.
 */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    hasSwitch: Boolean = false,
    switchState: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        leadingContent = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        trailingContent = {
            if (hasSwitch) {
                Switch(
                    checked = switchState,
                    onCheckedChange = onSwitchChange
                )
            }
            // Chevron is optional in M3, usually omitted inside grouped cards unless strictly nav
        },
        modifier = Modifier
            .clickable(enabled = onClick != null || hasSwitch) {
                if (hasSwitch && onSwitchChange != null) onSwitchChange(!switchState)
                else onClick?.invoke()
            },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent // Maintains the card background
        )
    )
}

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.room_name_label)) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { Button(onClick = { if(text.isNotBlank()) onConfirm(text) }) { Text(stringResource(R.string.finish_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun LanguageSelectionDialog(onDismiss: () -> Unit) {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "system" else currentLocales.get(0)?.language ?: "en"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                LanguageOption(text = stringResource(R.string.system_default), selected = currentTag == "system") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()); onDismiss() }
                LanguageOption(text = "English", selected = currentTag == "en") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")); onDismiss() }
                LanguageOption(text = "Français", selected = currentTag == "fr") { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr")); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ThemeSelectionDialog(currentMode: Int, onDismiss: () -> Unit, onThemeSelected: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_title)) },
        text = {
            Column {
                LanguageOption(text = stringResource(R.string.system_default), selected = currentMode == 0) { onThemeSelected(0) }
                LanguageOption(text = stringResource(R.string.theme_light), selected = currentMode == 1) { onThemeSelected(1) }
                LanguageOption(text = stringResource(R.string.theme_dark), selected = currentMode == 2) { onThemeSelected(2) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun LanguageOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun getCurrentLanguageName(): String {
    val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
    return locale.displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

@Composable
fun EasterEggDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.dev_passion), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.dev_desc), textAlign = TextAlign.Center) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

// ------------------------------------------------------------------------
// --- OPEN SOURCE LICENSES SCREEN ---
// ------------------------------------------------------------------------

data class OpenSourceLibrary(val name: String, val author: String, val license: String = "Apache 2.0", val url: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val libraries = listOf(
        OpenSourceLibrary("Kotlin", "JetBrains", url = "https://kotlinlang.org/"),
        OpenSourceLibrary("AndroidX Core & AppCompat", "Google", url = "https://developer.android.com/jetpack/androidx"),
        OpenSourceLibrary("Jetpack Compose", "Google", url = "https://developer.android.com/jetpack/compose"),
        OpenSourceLibrary("Material Design 3", "Google", url = "https://m3.material.io/"),
        OpenSourceLibrary("Kotlin Coroutines", "JetBrains", url = "https://github.com/Kotlin/kotlinx.coroutines"),
        OpenSourceLibrary("Gson", "Google", url = "https://github.com/google/gson")
    )

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.licenses_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(libraries) { lib ->
                    LicenseItem(lib) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))) }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun LicenseItem(library: OpenSourceLibrary, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = library.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.author, library.author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(text = library.license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(imageVector = Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}