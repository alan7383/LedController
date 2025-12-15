package com.example.ledcontroller.util

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.ledcontroller.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

// Simple data class for device persistence
data class SavedDevice(val name: String, val address: String)

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_CMD_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    // Alternative UUIDs for generic controllers
    val SERVICE_UUID_ALT_1: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val SERVICE_UUID_ALT_2: UUID = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
}

object BleManager {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private val gson = Gson()

    // Target address for auto-reconnection (used by MainActivity)
    var targetDeviceAddress: String? = null

    // Track if scan was triggered manually to avoid auto-connect conflicts
    private var isManualScan = false

    // --- STATE MANAGEMENT ---

    // Thread-safe map for multiple active connections
    private val activeConnections = ConcurrentHashMap<String, BluetoothGatt>()
    private val activeCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _connectedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val connectedDevices: StateFlow<List<BluetoothDevice>> = _connectedDevices

    private val _scannedDevices = MutableStateFlow<List<ScanResult>>(emptyList())
    val scannedDevices: StateFlow<List<ScanResult>> = _scannedDevices

    private val _knownDevices = MutableStateFlow<List<SavedDevice>>(emptyList())
    val knownDevices: StateFlow<List<SavedDevice>> = _knownDevices

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    // AllowList for "Intelligent Scan" filtering
    private val KNOWN_LED_NAMES = listOf(
        "LED", "Light", "Lotus", "Happy", "ELK", "BLEDOM", "Triones",
        "QHM", "JTY", "OA", "duoCo", "Melpo", "Ks", "Marvel", "Zengge"
    )

    // --- INITIALIZATION ---

    fun init(ctx: Context) {
        if (context == null) {
            context = ctx.applicationContext
            bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter

            // Load persisted devices
            loadKnownDevices()

            // Load target address but do NOT connect immediately.
            // We rely on MainActivity to start the scan or connection flow.
            val prefs = context?.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
            targetDeviceAddress = prefs?.getString("last_device_address", null)
        }
    }

    // --- PERSISTENCE LOGIC ---

    private fun loadKnownDevices() {
        val prefs = context?.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE) ?: return
        val json = prefs.getString("known_devices_list", null)
        if (json != null) {
            val type = object : TypeToken<List<SavedDevice>>() {}.type
            _knownDevices.value = gson.fromJson(json, type)
        }
    }

    private fun saveDeviceToMemory(device: BluetoothDevice) {
        val currentList = _knownDevices.value.toMutableList()
        // Avoid duplicates based on MAC address
        if (currentList.none { it.address == device.address }) {
            @SuppressLint("MissingPermission")
            val name = device.name ?: context?.getString(R.string.unknown_device) ?: "Device"

            currentList.add(SavedDevice(name, device.address))
            _knownDevices.value = currentList

            val prefs = context?.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
            prefs?.edit()?.putString("known_devices_list", gson.toJson(currentList))?.apply()
        }
    }

    fun removeKnownDevice(address: String) {
        val currentList = _knownDevices.value.toMutableList()
        currentList.removeAll { it.address == address }
        _knownDevices.value = currentList

        val prefs = context?.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        prefs?.edit()?.putString("known_devices_list", gson.toJson(currentList))?.apply()

        // If currently connected, disconnect it
        val gatt = activeConnections[address]
        gatt?.disconnect()
    }

    // --- INTELLIGENT SCANNING ---

    private fun isLedController(result: ScanResult): Boolean {
        val device = result.device

        // 1. Priority: Previous target or known device
        if (targetDeviceAddress != null && device.address == targetDeviceAddress) return true
        if (_knownDevices.value.any { it.address == device.address }) return true

        // 2. Filter by Name (AllowList)
        val name = device.name ?: result.scanRecord?.deviceName
        if (!name.isNullOrEmpty() && KNOWN_LED_NAMES.any { name.contains(it, ignoreCase = true) }) return true

        // 3. Filter by Service UUID
        val uuids = result.scanRecord?.serviceUuids ?: return false
        val knownUuids = listOf(
            ParcelUuid(BleConstants.SERVICE_UUID),
            ParcelUuid(BleConstants.SERVICE_UUID_ALT_1),
            ParcelUuid(BleConstants.SERVICE_UUID_ALT_2)
        )
        return uuids.any { it in knownUuids }
    }

    @SuppressLint("MissingPermission")
    fun startScan(manual: Boolean = false) {
        if (bluetoothAdapter?.isEnabled == false) return

        isManualScan = manual

        // Clear list only on manual refresh to avoid UI flickering during auto-reconnect
        if (manual) _scannedDevices.value = emptyList()

        if (_isScanning.value) stopScan()

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        // Low Latency for faster discovery
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        _isScanning.value = true
        mainHandler.removeCallbacksAndMessages(null)

        // Timeout safety (12s)
        mainHandler.postDelayed({ if (_isScanning.value) stopScan() }, 12000)

        try {
            scanner?.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            _isScanning.value = false
            Log.e("BleManager", "Scan start failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            _isScanning.value = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // Ignore scan stop errors
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (isLedController(result)) {
                val currentList = _scannedDevices.value.toMutableList()
                val index = currentList.indexOfFirst { it.device.address == result.device.address }

                // Update existing or add new
                if (index != -1) currentList[index] = result else currentList.add(result)
                _scannedDevices.value = currentList

                // Auto-connect logic (only if not manual scan and target found)
                if (!isManualScan && targetDeviceAddress != null && result.device.address == targetDeviceAddress) {
                    Log.d("BleManager", "Target found via scan, auto-connecting...")
                    connectToDevice(result.device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    // --- CONNECTION MANAGEMENT ---

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (activeConnections.containsKey(device.address)) return

        _connectionState.value = ConnectionState.CONNECTING
        saveDeviceToMemory(device)

        targetDeviceAddress = device.address
        val prefs = context?.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        prefs?.edit()?.putString("last_device_address", device.address)?.apply()

        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val address = gatt.device.address
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    activeConnections[address] = gatt
                    updateConnectedList()
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    activeConnections.remove(address)
                    activeCharacteristics.remove(address)
                    gatt.close()
                    updateConnectedList()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Try standard UUID, then alternatives
                    val service = gatt.getService(BleConstants.SERVICE_UUID)
                        ?: gatt.getService(BleConstants.SERVICE_UUID_ALT_1)
                        ?: gatt.getService(BleConstants.SERVICE_UUID_ALT_2)

                    // Find writable characteristic (Command or No-Response)
                    val characteristic = service?.getCharacteristic(BleConstants.CHARACTERISTIC_CMD_UUID)
                        ?: service?.characteristics?.firstOrNull { (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 }

                    if (characteristic != null) {
                        activeCharacteristics[gatt.device.address] = characteristic
                        updateConnectedList()
                    }
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToAddress(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device != null) connectToDevice(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(device: BluetoothDevice) {
        val gatt = activeConnections[device.address]
        gatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        activeConnections.values.forEach { it.disconnect(); it.close() }
        activeConnections.clear()
        activeCharacteristics.clear()
        updateConnectedList()
    }

    // Legacy method shortcut
    fun disconnect() = disconnectAll()

    private fun updateConnectedList() {
        val list = activeConnections.values.map { it.device }.distinctBy { it.address }
        _connectedDevices.value = list
        _connectionState.value = if (list.isNotEmpty()) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    }

    // --- COMMAND EXECUTION ---

    // Magic bytes for LED Controller (Triones/Lotus protocol)
    private val commandPayload = byteArrayOf(0x7E.toByte(), 0x00, 0x05, 0x03, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())

    @SuppressLint("MissingPermission")
    fun sendColor(r: Int, g: Int, b: Int) {
        if (activeConnections.isEmpty()) return

        commandPayload[4] = r.toByte()
        commandPayload[5] = g.toByte()
        commandPayload[6] = b.toByte()

        // Broadcast to all active devices
        activeConnections.forEach { (address, gatt) ->
            val char = activeCharacteristics[address]
            if (char != null) writeCharacteristicCompat(gatt, char, commandPayload)
        }
    }

    // --- TILE SERVICE LOGIC (QUICK SETTINGS) ---

    /**
     * Entry point for TileService.
     * Attempts to reuse active connection or triggers a "One-Shot" connection if the app is killed.
     */
    suspend fun executeCommand(context: Context, r: Int, g: Int, b: Int): Boolean {
        // 1. If app is alive and connected, use existing connection (Fast)
        if (activeConnections.isNotEmpty()) {
            sendColor(r, g, b)
            return true
        }

        // 2. If app is killed, initialize and attempt one-shot
        init(context)

        val prefs = context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
        val lastAddress = prefs.getString("last_device_address", null) ?: return false

        return connectAndSendOneShot(context, lastAddress, r, g, b)
    }

    /**
     * Connects, sends command, and disconnects immediately.
     * Used when the main application is not running.
     */
    @SuppressLint("MissingPermission")
    private suspend fun connectAndSendOneShot(context: Context, address: String, r: Int, g: Int, b: Int): Boolean {
        return kotlinx.coroutines.withTimeoutOrNull(4000) { // 4s Safety Timeout
            suspendCancellableCoroutine<Boolean> { continuation ->
                val device = bluetoothAdapter?.getRemoteDevice(address)
                if (device == null) {
                    if (continuation.isActive) continuation.resume(false, null)
                    return@suspendCancellableCoroutine
                }

                val oneShotCallback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            gatt.close()
                            // Fail if disconnected before sending
                            if (continuation.isActive) continuation.resume(false, null)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val service = gatt.getService(BleConstants.SERVICE_UUID)
                                ?: gatt.getService(BleConstants.SERVICE_UUID_ALT_1)
                                ?: gatt.getService(BleConstants.SERVICE_UUID_ALT_2)

                            val char = service?.getCharacteristic(BleConstants.CHARACTERISTIC_CMD_UUID)
                                ?: service?.characteristics?.firstOrNull { (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 }

                            if (char != null) {
                                val payload = byteArrayOf(0x7E.toByte(), 0x00, 0x05, 0x03, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())
                                payload[4] = r.toByte()
                                payload[5] = g.toByte()
                                payload[6] = b.toByte()

                                writeCharacteristicCompat(gatt, char, payload)

                                // Success! Wait briefly for packet transmission then disconnect
                                Handler(Looper.getMainLooper()).postDelayed({
                                    gatt.disconnect()
                                    gatt.close()
                                    if (continuation.isActive) continuation.resume(true, null)
                                }, 300)
                            } else {
                                gatt.disconnect()
                            }
                        } else {
                            gatt.disconnect()
                        }
                    }
                }

                device.connectGatt(context, false, oneShotCallback)
            }
        } ?: false
    }

    // Helper to handle WriteType based on Android version
    @SuppressLint("MissingPermission")
    private fun writeCharacteristicCompat(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, payload: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                char.value = payload
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            Log.e("BleManager", "Write failed", e)
        }
    }
}