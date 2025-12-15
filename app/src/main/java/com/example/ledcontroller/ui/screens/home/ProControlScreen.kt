package com.example.ledcontroller.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ledcontroller.service.AudioSyncService
import com.example.ledcontroller.R
import com.example.ledcontroller.data.LightPreset
import com.example.ledcontroller.data.PresetIcon
import com.example.ledcontroller.util.BleManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import java.util.Locale

// --- LOCAL IMPORTS ---
import com.example.ledcontroller.viewmodel.MainViewModel
import com.example.ledcontroller.ui.components.ColorWheel
import com.example.ledcontroller.ui.components.PresetChip
import com.example.ledcontroller.ui.components.PermissionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProControlScreen(
    bleManager: BleManager,
    initialRoomName: String,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {

    val context = LocalContext.current
    val view = LocalView.current
    val connectionState by bleManager.connectionState.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()

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

    // Auto open scan if no device is connected
    LaunchedEffect(scannedDevices) {
        if (bleManager.targetDeviceAddress == null &&
            connectionState == BleManager.ConnectionState.DISCONNECTED &&
            scannedDevices.isNotEmpty() && !showDeviceListDialog
        ) {
            showDeviceListDialog = true
        }
    }

    LaunchedEffect(hue, saturation, brightness, isPowerOn) {
        sharedPref.edit()
            .putFloat("saved_hue", hue)
            .putFloat("saved_sat", saturation)
            .putFloat("saved_bri", brightness)
            .putBoolean("saved_power", isPowerOn)
            .apply()
    }

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(roomName, fontWeight = FontWeight.Bold)
                        if (connectionState == BleManager.ConnectionState.CONNECTED) {
                            Text(stringResource(R.string.connected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        } else if (connectionState == BleManager.ConnectionState.CONNECTING || isScanning) {
                            // Using secondary color to fit Material You theme (avoiding pink tertiary)
                            Text(stringResource(R.string.scanning), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
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
                            contentDescription = stringResource(R.string.devices_icon_desc),
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
                    Box(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            .align(Alignment.CenterHorizontally)
                    )

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
                                name = stringResource(R.string.scene_disco),
                                icon = Icons.Rounded.AutoAwesome,
                                color = MaterialTheme.colorScheme.primary,
                                isSelected = isDiscoMode,
                                onClick = { isDiscoMode = !isDiscoMode; if (!isPowerOn) isPowerOn = true; performHaptic() }
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

    if (showDeviceListDialog) DeviceSelectionDialog(bleManager) { showDeviceListDialog = false }
    if (presetToDelete != null) AlertDialog(onDismissRequest = { presetToDelete = null }, icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_confirm, presetToDelete?.name ?: "")) }, confirmButton = { Button(onClick = { presets = presets.filter { it.id != presetToDelete?.id }; presetToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { presetToDelete = null }) { Text(stringResource(R.string.cancel)) } })
    if (showAddDialog) AddPresetDialog(hue, saturation, brightness, { showAddDialog = false }, { newPreset -> presets = presets + newPreset; showAddDialog = false })
}

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
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

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

// -----------------------------------------------------------------------------
// --- GOOGLE STYLE: MATERIAL 3 DEVICE DIALOG ---
// -----------------------------------------------------------------------------

@Composable
fun DeviceSelectionDialog(bleManager: BleManager, onDismiss: () -> Unit) {
    val scannedDevices by bleManager.scannedDevices.collectAsState()
    val connectedDevices by bleManager.connectedDevices.collectAsState()
    val knownDevices by bleManager.knownDevices.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()

    LaunchedEffect(Unit) { bleManager.startScan(manual = true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 550.dp)
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            ) {

                // --- HEADER ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Text(
                        stringResource(R.string.devices_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    } else {
                        IconButton(onClick = { bleManager.startScan(manual = true) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // --- SMART SCROLLING LIST ---
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    // Allow list to shrink if few items
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // SECTION 1: MY DEVICES
                    if (knownDevices.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.my_devices_header),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp)
                            )
                        }
                        items(knownDevices) { saved ->
                            val isConnected = connectedDevices.any { it.address == saved.address }
                            val isAvailable = isConnected || scannedDevices.any { it.device.address == saved.address }

                            SavedDeviceItemM3(
                                name = saved.name,
                                address = saved.address,
                                isConnected = isConnected,
                                isAvailable = isAvailable,
                                onToggle = { shouldConnect ->
                                    if (shouldConnect) bleManager.connectToAddress(saved.address)
                                    else connectedDevices.find { it.address == saved.address }?.let { bleManager.disconnectDevice(it) }
                                },
                                onDelete = { bleManager.removeKnownDevice(saved.address) }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // SECTION 2: AVAILABLE DEVICES
                    val newDevices = scannedDevices.map { it.device }
                        .distinctBy { it.address }
                        .filter { scanned -> knownDevices.none { known -> known.address == scanned.address } }

                    if (newDevices.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.other_devices_header),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(newDevices) { device ->
                            DeviceItemM3(
                                device = device,
                                onToggle = { bleManager.connectToDevice(device) }
                            )
                        }
                    } else if (knownDevices.isEmpty() && isScanning) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.scanning_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // --- FOOTER ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// --- PRO ITEM (High Contrast & Layout Fix) ---
@Composable
fun SavedDeviceItemM3(
    name: String,
    address: String,
    isConnected: Boolean,
    isAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    // DYNAMIC COLORS
    val containerColor = if (isConnected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (isConnected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    val iconBgColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val iconColor = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable { onToggle(!isConnected) },

        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = contentColor,
            supportingColor = contentColor.copy(alpha = 0.8f)
        ),

        leadingContent = {
            Box(
                modifier = Modifier.size(40.dp).background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        },

        headlineContent = {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    address,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (!isConnected && isAvailable) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.detected_status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        },

        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isConnected) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.width(4.dp))
                }

                Switch(
                    checked = isConnected,
                    onCheckedChange = { onToggle(it) },
                    thumbContent = if (isConnected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    )
}

// --- SIMPLE ITEM (New Devices) ---
@SuppressLint("MissingPermission")
@Composable
fun DeviceItemM3(device: android.bluetooth.BluetoothDevice, onToggle: (Boolean) -> Unit) {
    val name = device.name ?: stringResource(R.string.unknown_device_default)

    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle(true) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),

        leadingContent = {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Bluetooth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        headlineContent = { Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Button(
                onClick = { onToggle(true) },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text(stringResource(R.string.link_device), style = MaterialTheme.typography.labelMedium)
            }
        }
    )
}