package com.bretthalliday.fdtuner.ui.params

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bretthalliday.fdtuner.ble.FardriverBleManager
import com.bretthalliday.fdtuner.data.ChangeLogEntry
import com.bretthalliday.fdtuner.data.ChangeLogManager
import com.bretthalliday.fdtuner.data.ParamDefinitions
import com.bretthalliday.fdtuner.model.ParamDef
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ParamsViewModel(
    application: Application,
    private val bleManager: FardriverBleManager
) : AndroidViewModel(application) {

    val rawParams: StateFlow<Map<Int, Int>> = bleManager.rawParams

    val sections: List<String> = ParamDefinitions.SECTIONS

    fun paramsForSection(section: String): List<ParamDef> = ParamDefinitions.forSection(section)

    /** All params across every section — used by search. */
    val allParams: List<ParamDef> by lazy {
        ParamDefinitions.SECTIONS.flatMap { ParamDefinitions.forSection(it) }
    }

    /** Read-only live state/telemetry params shown on the Diagnostics screen. */
    val diagnosticsParams: List<ParamDef> = ParamDefinitions.diagnosticsParams

    val isDemo: Boolean get() = bleManager.isDemo

    /**
     * Write a new value to a param, performing read-modify-write for bit-packed params.
     * Logs the change to ChangeLogManager for both demo and live writes.
     */
    fun writeParam(param: ParamDef, displayValue: Int) {
        val addr = param.addr ?: return
        viewModelScope.launch {
            val currentRaw = bleManager.rawParams.value[addr] ?: 0
            val oldRaw = currentRaw
            val newRaw = param.packValue(currentRaw, displayValue)
            if (bleManager.isDemo) {
                // Demo mode: update local map only, no BLE write
                bleManager.updateDemoParam(addr, newRaw)
            } else {
                bleManager.writeParam(addr, newRaw)
            }
            // Log the change (both demo and live)
            ChangeLogManager.log(
                getApplication(),
                ChangeLogEntry(
                    timestamp = ChangeLogManager.nowIso(),
                    paramAddr = addr,
                    paramName = param.name,
                    oldValue = oldRaw,
                    newValue = newRaw,
                    isDemo = bleManager.isDemo
                )
            )
        }
    }

    /**
     * Get the current raw word for a param's address (for display in edit dialog).
     */
    suspend fun loadProfile(
        params: Map<Int, Int>,
        onProgress: (Int, Int) -> Unit
    ): Boolean = bleManager.writeAllParams(params, onProgress)

    fun getRawWord(param: ParamDef): Int? {
        val addr = param.addr ?: return null
        return bleManager.rawParams.value[addr]
    }
}
