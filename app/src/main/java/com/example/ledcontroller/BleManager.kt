package com.example.ledcontroller

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

object BleConstants {
    // Standard UUIDs for ELK/Triones/Lotus Lantern devices
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
            // Properly retrieve BluetoothManager
            bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(manual: Boolean = false) {
        if (bluetoothAdapter?.isEnabled == false) return

        // --- ZOMBIE CONNECTION SECURITY BLOCK ---
        // Wraps in try-catch because getConnectedDevices might crash
        // if BLUETOOTH_CONNECT permission was revoked.
        if (!manual && targetDeviceAddress != null && bluetoothManager != null) {
            try {
                val connectedDevices = bluetoothManager!!.getConnectedDevices(BluetoothProfile.GATT)
                val existingDevice = connectedDevices.find { it.address == targetDeviceAddress }

                if (existingDevice != null) {
                    Log.d("BleManager", "Zombie device found: ${existingDevice.name}. Recovering...")
                    connectToDevice(existingDevice)
                    return // Success: Short-circuit the scan
                }
            } catch (e: SecurityException) {
                Log.e("BleManager", "Missing permission to check existing connections.")
                // Fallback to normal scan
            } catch (e: Exception) {
                Log.e("BleManager", "Error checking existing connections", e)
            }
        }
        // ----------------------------------------

        if (_isScanning.value && !manual) return
        if (_isScanning.value) stopScan()

        isManualScan = manual
        if (manual) {
            _scannedDevices.value = emptyList()
            lastUiUpdate = 0L
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _isScanning.value = true
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ if (_isScanning.value) stopScan() }, 10000)

        try {
            scanner?.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            Log.e("BleManager", "Error startScan: ${e.message}")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            _isScanning.value = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan() // Always stop scan before connecting
        _connectionState.value = ConnectionState.CONNECTING
        _connectedDevice.value = device
        mainHandler.removeCallbacksAndMessages(null)

        // If a GATT instance already exists, close it to prevent leaks
        bluetoothGatt?.close()

        // autoConnect = false is crucial for fast initial connection
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val unknownStr = context?.getString(R.string.unknown_device) ?: "Unknown"
            val deviceName = device.name ?: result.scanRecord?.deviceName ?: unknownStr
            val record = result.scanRecord
            val serviceUuids = record?.serviceUuids

            val hasCompatibleUUID = serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID } == true
            val hasCompatibleName = deviceName.contains("ELK", true) || deviceName.contains("Triones", true) || deviceName.contains("LED", true)
            val isTarget = device.address == targetDeviceAddress

            if (hasCompatibleUUID || hasCompatibleName || isTarget) {
                if (isManualScan) {
                    val currentList = _scannedDevices.value.toMutableList()
                    val index = currentList.indexOfFirst { it.device.address == device.address }
                    if (index != -1) currentList[index] = result else currentList.add(result)

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUiUpdate > 500) {
                        _scannedDevices.value = currentList
                        lastUiUpdate = currentTime
                    }
                } else {
                    if (targetDeviceAddress != null && isTarget) {
                        stopScan()
                        connectToDevice(device)
                    }
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            Log.e("BleManager", "Scan failed code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDevice.value = null
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                if (service != null) {
                    commandCharacteristic = service.getCharacteristic(BleConstants.CHARACTERISTIC_CMD_UUID)
                    if (commandCharacteristic != null) {
                        _connectionState.value = ConnectionState.CONNECTED
                    }
                }
            }
        }
    }

    // Protocol payload for Triones/ELK
    private val commandPayload = byteArrayOf(0x7E.toByte(), 0x00, 0x05, 0x03, 0x00, 0x00, 0x00, 0x00, 0xEF.toByte())

    @SuppressLint("MissingPermission")
    fun sendColor(r: Int, g: Int, b: Int) {
        if (bluetoothGatt == null || commandCharacteristic == null) return
        commandPayload[4] = r.toByte()
        commandPayload[5] = g.toByte()
        commandPayload[6] = b.toByte()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(commandCharacteristic!!, commandPayload, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                commandCharacteristic?.value = commandPayload
                commandCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                bluetoothGatt?.writeCharacteristic(commandCharacteristic)
            }
        } catch (e: Exception) {
            Log.e("BleManager", "Error sending color", e)
        }
    }
}