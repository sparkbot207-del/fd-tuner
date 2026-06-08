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

    // ---- PID tune presets ----

    /** Look up one of the six PID params by its (official) name within the PID Paras section. */
    fun pidParamByName(name: String): ParamDef? =
        ParamDefinitions.forSection(ParamDefinitions.SECTION_PID).firstOrNull { it.name == name }

    // ---- Quick Tune ----

    /** Find any param by exact name across the full set. */
    fun paramByName(name: String): ParamDef? =
        ParamDefinitions.allParams.firstOrNull { it.name == name }

    /** Field-weakening curve points (Ratios in Speed) in order — excludes LimitSpeed. */
    val fwCurveParams: List<ParamDef> =
        ParamDefinitions.forSection(ParamDefinitions.SECTION_RATIOS_SPEED)
            .filter { it.name == "RatioMin" || it.name == "RatioMax" || it.name.startsWith("Ratio @") }

    data class BatchResult(val written: List<String>, val notWritten: List<String>)

    /**
     * Write [displayValue] (raw units) to every param, combining writes that share a word so the
     * hi byte never clobbers the lo byte, via the existing write path (isDemo guard). On a BLE
     * failure it stops and reports which params landed and which did not. Used ONLY by the
     * user-initiated, bounded "reset curve to 100%" action.
     */
    suspend fun writeSameValueGrouped(params: List<ParamDef>, displayValue: Int): BatchResult {
        class Pending(var raw: Int, val oldRaw: Int, val names: MutableList<String>)
        val byAddr = LinkedHashMap<Int, Pending>()
        for (p in params) {
            val addr = p.addr ?: continue
            val cur = byAddr[addr]
            if (cur == null) {
                val base = bleManager.rawParams.value[addr] ?: 0
                byAddr[addr] = Pending(p.packValue(base, displayValue), base, mutableListOf(p.name))
            } else {
                cur.raw = p.packValue(cur.raw, displayValue)
                cur.names.add(p.name)
            }
        }
        val written = mutableListOf<String>()
        val notWritten = mutableListOf<String>()
        val entries = byAddr.entries.toList()
        for ((idx, e) in entries.withIndex()) {
            val ok = if (bleManager.isDemo) {
                bleManager.updateDemoParam(e.key, e.value.raw); true
            } else {
                bleManager.writeParam(e.key, e.value.raw)
            }
            if (ok) {
                written += e.value.names
                ChangeLogManager.log(
                    getApplication(),
                    ChangeLogEntry(
                        timestamp = ChangeLogManager.nowIso(),
                        paramAddr = e.key,
                        paramName = e.value.names.joinToString("+"),
                        oldValue = e.value.oldRaw,
                        newValue = e.value.raw,
                        isDemo = bleManager.isDemo
                    )
                )
            } else {
                notWritten += e.value.names
                for (j in idx + 1 until entries.size) notWritten += entries[j].value.names
                break
            }
        }
        return BatchResult(written, notWritten)
    }


}
