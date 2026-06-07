package com.bretthalliday.fdtuner.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Foreground service that:
 *  1. Shows a persistent notification with BLE status
 *  2. On unexpected disconnect, auto-reconnects every 3s up to 10 attempts
 *  3. Stores the last connected device address in SharedPreferences
 *  4. Skips auto-reconnect when isDemo == true
 *  5. Exposes a Binder for MainActivity to bind to
 */
class BleConnectionService : Service() {

    companion object {
        private const val TAG = "BleConnectionService"
        const val CHANNEL_ID = "fd_ble_channel"
        const val NOTIFICATION_ID = 1001
        const val PREF_LAST_DEVICE_ADDRESS = "last_device_address"
        private const val PREFS_NAME = "fd_service"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val RECONNECT_INTERVAL_MS = 3_000L
        private const val CONNECT_WAIT_MS = 5_000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleConnectionService = this@BleConnectionService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reconnectJob: Job? = null
    private var wasConnected = false
    private var attachedManager: FardriverBleManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Disconnected"))
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /**
     * Called by MainActivity after binding. Observes connection state and manages
     * auto-reconnect logic.
     */
    fun attachBleManager(manager: FardriverBleManager) {
        attachedManager = manager
        scope.launch {
            manager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        wasConnected = true
                        reconnectJob?.cancel()
                        reconnectJob = null
                        updateNotification("Connected to ${state.deviceName}")
                    }
                    is ConnectionState.Disconnected -> {
                        if (wasConnected && !manager.isDemo) {
                            wasConnected = false
                            tryAutoReconnect(manager)
                        } else {
                            wasConnected = false
                        }
                        updateNotification("Disconnected")
                    }
                    is ConnectionState.Scanning -> {
                        wasConnected = false
                        updateNotification("Scanning...")
                    }
                    is ConnectionState.Connecting -> {
                        updateNotification("Connecting to ${state.deviceName}...")
                    }
                    is ConnectionState.Demo -> {
                        wasConnected = false
                        reconnectJob?.cancel()
                        reconnectJob = null
                        updateNotification("Demo Mode")
                    }
                    is ConnectionState.Error -> {
                        updateNotification("Error: ${state.message}")
                    }
                }
            }
        }
    }

    private fun tryAutoReconnect(manager: FardriverBleManager) {
        if (manager.isDemo) return
        val lastAddress = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_LAST_DEVICE_ADDRESS, null) ?: run {
            Log.d(TAG, "No last device address stored — skipping auto-reconnect")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val bluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.w(TAG, "Bluetooth not available for reconnect")
                return@launch
            }

            var attempts = 0
            while (attempts < MAX_RECONNECT_ATTEMPTS && isActive) {
                val attemptNum = attempts + 1
                Log.i(TAG, "Auto-reconnect attempt $attemptNum/$MAX_RECONNECT_ATTEMPTS to $lastAddress")
                updateNotification("Reconnecting... ($attemptNum/$MAX_RECONNECT_ATTEMPTS)")

                try {
                    @Suppress("MissingPermission")
                    val device = bluetoothAdapter.getRemoteDevice(lastAddress)
                    manager.connect(device)
                    // Wait to see if it succeeded
                    delay(CONNECT_WAIT_MS)
                    if (manager.connectionState.value is ConnectionState.Connected) {
                        Log.i(TAG, "Auto-reconnect succeeded")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt $attemptNum failed: ${e.message}")
                }

                attempts++
                if (attempts < MAX_RECONNECT_ATTEMPTS) {
                    delay(RECONNECT_INTERVAL_MS)
                }
            }

            if (attempts >= MAX_RECONNECT_ATTEMPTS) {
                Log.w(TAG, "Auto-reconnect exhausted all attempts")
                updateNotification("Disconnected")
            }
        }
    }

    /** Call when a device connects successfully to persist it for later auto-reconnect. */
    fun saveLastDeviceAddress(address: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_DEVICE_ADDRESS, address)
            .apply()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BLE Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Fardriver Whisperer BLE connection status"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fardriver Whisperer")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(statusText: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
