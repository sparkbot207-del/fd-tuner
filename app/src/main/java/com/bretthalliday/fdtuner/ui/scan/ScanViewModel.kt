package com.bretthalliday.fdtuner.ui.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import com.bretthalliday.fdtuner.ble.ConnectionState
import com.bretthalliday.fdtuner.ble.FardriverBleManager
import com.bretthalliday.fdtuner.ble.ScannedDevice
import kotlinx.coroutines.flow.StateFlow

class ScanViewModel(private val bleManager: FardriverBleManager) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices

    fun startScan(adapter: BluetoothAdapter) {
        bleManager.startScan(adapter)
    }

    fun stopScan() {
        bleManager.stopScan()
    }

    fun connect(device: BluetoothDevice) {
        bleManager.connect(device)
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}
