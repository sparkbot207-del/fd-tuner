package com.bretthalliday.fdtuner.ui.params

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bretthalliday.fdtuner.ble.FardriverBleManager
import com.bretthalliday.fdtuner.data.ParamDefinitions
import com.bretthalliday.fdtuner.model.ParamDef
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ParamsViewModel(private val bleManager: FardriverBleManager) : ViewModel() {

    val rawParams: StateFlow<Map<Int, Int>> = bleManager.rawParams

    val sections: List<String> = ParamDefinitions.SECTIONS

    fun paramsForSection(section: String): List<ParamDef> = ParamDefinitions.forSection(section)

    /**
     * Write a new value to a param, performing read-modify-write for bit-packed params.
     * Returns true on success.
     */
    val isDemo: Boolean get() = bleManager.isDemo

    fun writeParam(param: ParamDef, displayValue: Int) {
        if (bleManager.isDemo) return  // No writes in demo mode
        val addr = param.addr ?: return
        viewModelScope.launch {
            val currentRaw = bleManager.rawParams.value[addr] ?: 0
            val newRaw = param.packValue(currentRaw, displayValue)
            bleManager.writeParam(addr, newRaw)
        }
    }

    /**
     * Get the current raw word for a param's address (for display in edit dialog).
     */
    fun getRawWord(param: ParamDef): Int? {
        val addr = param.addr ?: return null
        return bleManager.rawParams.value[addr]
    }
}
