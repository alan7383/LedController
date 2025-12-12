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
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_CMD_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
}

object BleManager {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var context: Context? = null

    // Connexion statique dédiée au Widget
    private var activeGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice

    private val _scannedDevices = MutableStateFlow<List<ScanResult>>(emptyList())
    val scannedDevices: StateFlow<List<ScanResult>> = _scannedDevices

    var targetDeviceAddress: String? = null
    private var isManualScan = false
    private var lastUiUpdate = 0L

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    fun init(ctx: Context) {
        if (context == null) {
            context = ctx.applicationContext
            bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun executeCommand(context: Context, r: Int, g: Int, b: Int): Boolean {
        // Exécution en thread IO pour fluidité maximale
        return withContext(Dispatchers.IO) {
            init(context)

            val payload = byteArrayOf(0x7E.toByte(), 0x00, 0x05, 0x03, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())
            payload[4] = r.toByte()
            payload[5] = g.toByte()
            payload[6] = b.toByte()

            // 1. Priorité App Principale
            if (_connectionState.value == ConnectionState.CONNECTED && bluetoothGatt != null) {
                sendColor(r, g, b)
                return@withContext true
            }

            // 2. Smart Reuse (Réutilisation connexion Widget)
            if (activeGatt != null) {
                val service = activeGatt?.getService(BleConstants.SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConstants.CHARACTERISTIC_CMD_UUID)

                if (characteristic != null) {
                    try {
                        val success = writeCharacteristicCompat(activeGatt!!, characteristic, payload)
                        if (success) return@withContext true
                    } catch (e: Exception) {
                        Log.e("BleManager", "Reuse failed")
                    }
                }
                activeGatt?.close()
                activeGatt = null
            }

            // 3. Nouvelle Connexion
            if (targetDeviceAddress == null) {
                val prefs = context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
                targetDeviceAddress = prefs.getString("last_device_address", null)
            }
            val address = targetDeviceAddress ?: return@withContext false
            val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@withContext false

            return@withContext withTimeoutOrNull(3000) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val oneShotCallback = object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                gatt.discoverServices()
                                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                if (activeGatt == gatt) activeGatt = null
                                if (continuation.isActive) continuation.resume(false)
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                val service = gatt.getService(BleConstants.SERVICE_UUID)
                                val characteristic = service?.getCharacteristic(BleConstants.CHARACTERISTIC_CMD_UUID)

                                if (characteristic != null) {
                                    val success = writeCharacteristicCompat(gatt, characteristic, payload)
                                    activeGatt = gatt // On garde la connexion
                                    if (continuation.isActive) continuation.resume(success)
                                } else {
                                    if (continuation.isActive) continuation.resume(false)
                                }
                            }
                        }
                    }
                    activeGatt = device.connectGatt(context, false, oneShotCallback)
                }
            } ?: false
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristicCompat(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, payload: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val code = gatt.writeCharacteristic(char, payload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                code == BluetoothStatusCodes.SUCCESS
            } else {
                char.value = payload
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(char)
            }
        } catch (e: Exception) { false }
    }

    // --- RESTE DU CODE STANDARD ---
    @SuppressLint("MissingPermission")
    fun ensureConnection(context: Context, onConnected: () -> Unit) {
        if (bluetoothGatt != null && _connectionState.value == ConnectionState.CONNECTED) {
            onConnected()
            return
        }
        init(context)
        if (targetDeviceAddress == null) {
            val prefs = context.getSharedPreferences("LedControllerPrefs", Context.MODE_PRIVATE)
            targetDeviceAddress = prefs.getString("last_device_address", null)
        }
        val address = targetDeviceAddress ?: return
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device != null) {
                connectToDevice(device)
                mainHandler.postDelayed({ onConnected() }, 1500)
            }
        } catch (e: Exception) { Log.e("BleManager", "Erreur reconnexion widget", e) }
    }

    @SuppressLint("MissingPermission")
    fun startScan(manual: Boolean = false) {
        if (bluetoothAdapter?.isEnabled == false) return
        if (!manual && targetDeviceAddress != null && bluetoothManager != null) {
            try {
                val connectedDevices = bluetoothManager!!.getConnectedDevices(BluetoothProfile.GATT)
                val existingDevice = connectedDevices.find { it.address == targetDeviceAddress }
                if (existingDevice != null) {
                    connectToDevice(existingDevice)
                    return
                }
            } catch (e: Exception) { }
        }
        if (_isScanning.value && !manual) return
        if (_isScanning.value) stopScan()
        isManualScan = manual
        if (manual) { _scannedDevices.value = emptyList(); lastUiUpdate = 0L }
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        _isScanning.value = true
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ if (_isScanning.value) stopScan() }, 10000)
        try { scanner?.startScan(null, settings, scanCallback) } catch (e: Exception) { _isScanning.value = false }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() { try { _isScanning.value = false; bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (e: Exception) {} }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect(); bluetoothGatt?.close(); bluetoothGatt = null
        activeGatt?.close(); activeGatt = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan(); _connectionState.value = ConnectionState.CONNECTING; _connectedDevice.value = device
        mainHandler.removeCallbacksAndMessages(null); bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: result.scanRecord?.deviceName ?: "Unknown"
            val isTarget = device.address == targetDeviceAddress
            if (isTarget || deviceName.contains("LED", true) || deviceName.contains("Triones", true)) {
                if (isManualScan) {
                    val currentList = _scannedDevices.value.toMutableList()
                    val index = currentList.indexOfFirst { it.device.address == device.address }
                    if (index != -1) currentList[index] = result else currentList.add(result)
                    _scannedDevices.value = currentList
                } else if (isTarget) { stopScan(); connectToDevice(device) }
            }
        }
        override fun onScanFailed(errorCode: Int) { _isScanning.value = false }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED; _connectedDevice.value = null
                bluetoothGatt?.close(); bluetoothGatt = null
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleConstants.CHARACTERISTIC_CMD_UUID)

                if (characteristic != null) {
                    commandCharacteristic = characteristic
                    _connectionState.value = ConnectionState.CONNECTED
                }
            }
        }
    }

    private val commandPayload = byteArrayOf(0x7E.toByte(), 0x00, 0x05, 0x03, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())

    @SuppressLint("MissingPermission")
    fun sendColor(r: Int, g: Int, b: Int) {
        if (bluetoothGatt == null || commandCharacteristic == null) return
        commandPayload[4] = r.toByte(); commandPayload[5] = g.toByte(); commandPayload[6] = b.toByte()
        writeCharacteristicCompat(bluetoothGatt!!, commandCharacteristic!!, commandPayload)
    }
}