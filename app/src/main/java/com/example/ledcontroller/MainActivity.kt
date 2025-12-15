package com.example.ledcontroller

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ledcontroller.ui.screens.home.OnboardingScreen
import com.example.ledcontroller.ui.screens.home.ProControlScreen
import com.example.ledcontroller.ui.screens.music.MusicSyncScreen
import com.example.ledcontroller.ui.screens.settings.SettingsScreen
import com.example.ledcontroller.util.BleManager
import com.example.ledcontroller.viewmodel.MainViewModel
import com.example.ledcontroller.ui.theme.AppTheme

/**
 * Main Entry Point of the Application.
 * Handles navigation, theme setup, and lifecycle events for Bluetooth management.
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Apply the custom application theme defined in ui/theme/Theme.kt
            // Includes support for Dark Mode, Dynamic Colors, and AMOLED blacks
            AppTheme(themeMode = viewModel.themeMode, useAmoled = viewModel.isAmoledMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainAppContent(viewModel)
                }
            }
        }
    }
}

// Simple navigation enum representing the top-level screens
enum class AppScreen { Home, Settings }

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedPref = remember { context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE) }

    // Check if the user has completed the initial onboarding (Room Name setup)
    var isSetupDone by remember { mutableStateOf(sharedPref.getBoolean("is_setup_done", false)) }

    // Navigation State
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }

    // Bluetooth Permission & Enable Launcher
    val enableBluetoothLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.bleManager.startScan(manual = false)
    }
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    // --- Lifecycle Management ---
    // Automatically triggers a scan or reconnection attempt when the app resumes.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isSetupDone) {
                // Ensure all necessary permissions are granted before attempting BLE operations
                if (checkPermissions(context)) {
                    if (bluetoothAdapter?.isEnabled == false) {
                        // Prompt user to enable Bluetooth if it's off
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        enableBluetoothLauncher.launch(enableBtIntent)
                    } else if (viewModel.bleManager.connectionState.value == BleManager.ConnectionState.DISCONNECTED) {
                        // If previously connected to a specific device, try to find it again
                        if (viewModel.bleManager.targetDeviceAddress != null) {
                            viewModel.bleManager.startScan(manual = false)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- Main UI Content ---
    if (!isSetupDone) {
        OnboardingScreen { name ->
            sharedPref.edit().putBoolean("is_setup_done", true).putString("room_name", name).apply()
            isSetupDone = true
        }
    } else {
        // Handle transitions between Home and Settings screens
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
                tonalElevation = 0.dp
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

/**
 * Utility function to check if required runtime permissions are granted.
 * Handles differences between Android 12+ (S) and older versions.
 */
fun checkPermissions(context: Context): Boolean {
    val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                hasLocation
    } else {
        hasLocation
    }
}