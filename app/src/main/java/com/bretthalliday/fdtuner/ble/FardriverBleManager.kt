package com.bretthalliday.fdtuner.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.bretthalliday.fdtuner.model.TelemetryData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages all BLE communication with a Fardriver controller.
 *
 * All GATT operations are serialized via [gattMutex] to prevent concurrent calls
 * that Android's BLE stack cannot handle.
 *
 * Lifecycle: call [startScan] to discover, [connect] to connect,
 * [disconnect] to tear down cleanly.
 */
class FardriverBleManager(private val context: Context) {

    companion object {
        private const val TAG = "FardriverBle"
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val MTU_REQUEST = 512
        private val SERVICE_UUID = UUID.fromString(FardriverProtocol.SERVICE_UUID)
        private val CHAR_UUID = UUID.fromString(FardriverProtocol.CHARACTERISTIC_UUID)
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // ---- Public state flows ----

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices

    private val _rawParams = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val rawParams: StateFlow<Map<Int, Int>> = _rawParams

    private val _telemetry = MutableStateFlow<TelemetryData?>(null)
    val telemetry: StateFlow<TelemetryData?> = _telemetry

    // ---- Session Snapshot (Feature 2) ----

    private var sessionSnapshot: Map<Int, Int>? = null
    private val _snapshotAvailable = MutableStateFlow(false)
    val snapshotAvailable: StateFlow<Boolean> = _snapshotAvailable

    fun takeSessionSnapshot() {
        sessionSnapshot = HashMap(_rawParams.value)
        _snapshotAvailable.value = sessionSnapshot!!.isNotEmpty()
    }

    fun hasSnapshot(): Boolean = sessionSnapshot != null && sessionSnapshot!!.isNotEmpty()

    suspend fun restoreSnapshot(): Boolean {
        val snap = sessionSnapshot ?: return false
        return writeAllParams(snap)
    }

    fun clearSnapshot() {
        sessionSnapshot = null
        _snapshotAvailable.value = false
    }

    // ---- Settings (written by SettingsFragment) ----

    var polePairs: Int = 4
    var wheelCircumferenceMm: Int = 2100
    var useMph: Boolean = true

    // ---- Internal state ----

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gattMutex = Mutex()
    private val rawParamsMutable = ConcurrentHashMap<Int, Int>()

    private var bluetoothGatt: BluetoothGatt? = null
    private var bleCharacteristic: BluetoothGattCharacteristic? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanJob: Job? = null

    // Continuations for async GATT callbacks
    private var mtuContinuation: CancellableContinuation<Int>? = null
    private var writeContinuation: CancellableContinuation<Boolean>? = null

    private val scannedDeviceMap = mutableMapOf<String, ScannedDevice>()

    // ---- Scan ----

    fun startScan(bluetoothAdapter: BluetoothAdapter) {
        if (_connectionState.value is ConnectionState.Scanning) return

        scannedDeviceMap.clear()
        _scannedDevices.value = emptyList()
        _connectionState.value = ConnectionState.Scanning(0)

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.Error("BLE scanner not available")
            return
        }

        val filters = buildScanFilters()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanJob = scope.launch {
            scanner?.startScan(filters, settings, scanCallback)
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan error: ${e.message}")
        }
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    private fun buildScanFilters(): List<ScanFilter> {
        val byService = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        return listOf(byService)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (!name.startsWith("FD-", ignoreCase = true)) return

            val entry = ScannedDevice(
                name = name,
                address = device.address,
                rssi = result.rssi,
                device = device
            )
            scannedDeviceMap[device.address] = entry
            _scannedDevices.value = scannedDeviceMap.values.sortedByDescending { it.rssi }
            _connectionState.value = ConnectionState.Scanning(scannedDeviceMap.size)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _connectionState.value = ConnectionState.Error("Scan failed (code $errorCode)")
        }
    }

    // ---- Connect ----

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.Connecting(device.name ?: device.address)
        rawParamsMutable.clear()
        _rawParams.value = emptyMap()

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        clearSnapshot()
        scope.launch {
            gattMutex.withLock {
                try {
                    bluetoothGatt?.disconnect()
                    delay(200)
                    bluetoothGatt?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "disconnect error: ${e.message}")
                }
                bluetoothGatt = null
                bleCharacteristic = null
            }
        }
        _connectionState.value = ConnectionState.Disconnected
    }

    // ---- Write param ----

    suspend fun writeParam(addr: Int, value: Int): Boolean {
        // Feature 2: capture snapshot before the very first write of this session
        if (sessionSnapshot == null) takeSessionSnapshot()
        val pkt = FardriverProtocol.buildWritePacket(addr, value)
        return writeRaw(pkt)
    }

    private suspend fun writeRaw(data: ByteArray): Boolean = gattMutex.withLock {
        val gatt = bluetoothGatt ?: return@withLock false
        val char = bleCharacteristic ?: return@withLock false

        return@withLock suspendCancellableCoroutine { cont ->
            writeContinuation = cont
            char.value = data
            @Suppress("DEPRECATION")
            val ok = gatt.writeCharacteristic(char)
            if (!ok) {
                writeContinuation = null
                cont.resume(false) {}
            }
        }
    }

    // ---- GATT Callback ----

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected, requesting MTU $MTU_REQUEST")
                    gatt.requestMtu(MTU_REQUEST)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected (status=$status)")
                    val wasConnected = _connectionState.value is ConnectionState.Connected
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    bleCharacteristic = null
                    _connectionState.value = ConnectionState.Disconnected
                    _telemetry.value = null

                    // Auto-reconnect if unexpected disconnection
                    if (wasConnected && status != BluetoothGatt.GATT_SUCCESS) {
                        Log.i(TAG, "Unexpected disconnect, signaling UI to rescan")
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU changed to $mtu (status=$status)")
            mtuContinuation?.resume(mtu) {}
            mtuContinuation = null

            // Discover services after MTU is set
            scope.launch {
                delay(100)
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                _connectionState.value = ConnectionState.Error("Service discovery failed")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Fardriver service not found")
                _connectionState.value = ConnectionState.Error("FD service not found")
                return
            }

            val char = service.getCharacteristic(CHAR_UUID)
            if (char == null) {
                Log.e(TAG, "Fardriver characteristic not found")
                _connectionState.value = ConnectionState.Error("FD characteristic not found")
                return
            }

            bleCharacteristic = char

            scope.launch {
                enableNotifications(gatt, char)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: return
            handleIncomingPacket(data)
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPacket(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            val cont = writeContinuation
            writeContinuation = null
            cont?.resume(success) {}
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.i(TAG, "Descriptor write status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Notifications enabled — start data gathering
                scope.launch {
                    delay(200)
                    val deviceName = gatt.device.name ?: gatt.device.address
                    _connectionState.value = ConnectionState.Connected(deviceName, gatt.device.address)
                    Log.i(TAG, "Sending data gather command")
                    writeRaw(FardriverProtocol.buildDataGatherPacket())
                }
            } else {
                _connectionState.value = ConnectionState.Error("Notification enable failed")
            }
        }
    }

    // ---- Helpers ----

    private suspend fun enableNotifications(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic
    ) {
        gattMutex.withLock {
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor != null) {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            } else {
                Log.w(TAG, "CCCD descriptor not found — notifications may not work")
                // Still mark as connected and try sending data gather
                val deviceName = gatt.device.name ?: gatt.device.address
                _connectionState.value = ConnectionState.Connected(deviceName, gatt.device.address)
                writeRaw(FardriverProtocol.buildDataGatherPacket())
            }
        }
    }

    private fun handleIncomingPacket(data: ByteArray) {
        val words = FardriverProtocol.parseStatusPacket(data) ?: return
        rawParamsMutable.putAll(words)
        val snapshot = HashMap(rawParamsMutable)
        _rawParams.value = snapshot

        // Decode telemetry from the live addresses
        val telData = FardriverProtocol.decodeTelemetry(
            snapshot, polePairs, wheelCircumferenceMm, useMph
        )
        _telemetry.value = telData
    }

    // ---- Bulk write (profile load) ----

    /**
     * Write every param in the map to the controller, one at a time.
     * Emits progress (0..total). Skips known read-only / telemetry addresses.
     */
    suspend fun writeAllParams(
        params: Map<Int, Int>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean {
        // Skip live telemetry addresses — don't write those back
        val readOnlyAddrs = setOf(0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7,
                                   0xE8, 0xE9, 0xEA, 0xEB, 0xEC, 0xED,
                                   0xEE, 0xEF, 0xF0, 0xA0)
        val writable = params.filterKeys { it !in readOnlyAddrs }
        val total = writable.size
        var done = 0
        for ((addr, value) in writable) {
            val ok = writeParam(addr, value)
            if (!ok) return false
            done++
            onProgress(done, total)
            delay(60) // give controller time between writes
        }
        return true
    }

    // ---- Demo mode ----

    fun startDemo() {
        disconnect()
        DemoDataSource.start()
        _connectionState.value = ConnectionState.Demo
        scope.launch {
            DemoDataSource.telemetry.collect { _telemetry.value = it }
        }
        scope.launch {
            DemoDataSource.rawParams.collect { _rawParams.value = it }
        }
    }

    fun stopDemo() {
        clearSnapshot()
        DemoDataSource.stop()
        _connectionState.value = ConnectionState.Disconnected
        _rawParams.value = emptyMap()
        _telemetry.value = null
    }

    val isDemo get() = _connectionState.value == ConnectionState.Demo

    /** Update a single param in the local map (demo mode only). */
    fun updateDemoParam(addr: Int, value: Int) {
        DemoDataSource.updateParam(addr, value)
        // _rawParams will update via the collect in startDemo()
    }

    /** Merge an entire profile map in one shot — avoids race with the collect coroutine. */
    fun bulkUpdateDemoParams(params: Map<Int, Int>) {
        DemoDataSource.bulkUpdateParams(params)
        // Single emission from DemoDataSource.rawParams feeds _rawParams via collect
    }

    fun cleanup() {
        scope.cancel()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "cleanup error: ${e.message}")
        }
        bluetoothGatt = null
    }
}

data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice
)
