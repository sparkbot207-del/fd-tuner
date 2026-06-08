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

    /** Result of a PID preset write: which param names landed vs which did not. */
    data class PidWriteResult(val written: List<String>, val notWritten: List<String>)

    /**
     * Write a reviewed set of PID values through the EXISTING confirmed write path.
     *
     * The six PID params share three word addresses (StartKI/MidKI at 0x0F, MaxKI/StartKP at
     * 0x10, MidKP/MaxKP at 0x11). We combine each byte-pair into a single packed word in memory
     * and write ONCE per address, so the high byte never clobbers the low byte just written.
     * Respects the isDemo guard. On a BLE failure it stops and reports which values landed and
     * which did not — the caller must not assume all six wrote.
     */
    suspend fun writePidPreset(items: List<Pair<ParamDef, Int>>): PidWriteResult {
        class Pending(var raw: Int, val oldRaw: Int, val names: MutableList<String>)

        val byAddr = LinkedHashMap<Int, Pending>()
        for ((param, displayValue) in items) {
            val addr = param.addr ?: continue
            val cur = byAddr[addr]
            if (cur == null) {
                val base = bleManager.rawParams.value[addr] ?: 0
                byAddr[addr] = Pending(param.packValue(base, displayValue), base, mutableListOf(param.name))
            } else {
                cur.raw = param.packValue(cur.raw, displayValue)
                cur.names.add(param.name)
            }
        }

        val written = mutableListOf<String>()
        val notWritten = mutableListOf<String>()
        val entries = byAddr.entries.toList()
        for ((idx, e) in entries.withIndex()) {
            val addr = e.key
            val pending = e.value
            val ok = if (bleManager.isDemo) {
                bleManager.updateDemoParam(addr, pending.raw); true
            } else {
                bleManager.writeParam(addr, pending.raw)
            }
            if (ok) {
                written += pending.names
                ChangeLogManager.log(
                    getApplication(),
                    ChangeLogEntry(
                        timestamp = ChangeLogManager.nowIso(),
                        paramAddr = addr,
                        paramName = pending.names.joinToString("+"),
                        oldValue = pending.oldRaw,
                        newValue = pending.raw,
                        isDemo = bleManager.isDemo
                    )
                )
            } else {
                notWritten += pending.names
                for (j in (idx + 1) until entries.size) notWritten += entries[j].value.names
                break
            }
        }
        return PidWriteResult(written, notWritten)
    }
}
