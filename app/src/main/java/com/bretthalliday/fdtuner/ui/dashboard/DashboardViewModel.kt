package com.bretthalliday.fdtuner.ui.dashboard

import androidx.lifecycle.ViewModel
import com.bretthalliday.fdtuner.ble.FardriverBleManager
import com.bretthalliday.fdtuner.model.TelemetryData
import kotlinx.coroutines.flow.StateFlow

class DashboardViewModel(private val bleManager: FardriverBleManager) : ViewModel() {

    val telemetry: StateFlow<TelemetryData?> = bleManager.telemetry
    val rawParams: StateFlow<Map<Int, Int>> = bleManager.rawParams

    fun disconnect() {
        if (bleManager.isDemo) bleManager.stopDemo()
        else bleManager.disconnect()
    }
}
