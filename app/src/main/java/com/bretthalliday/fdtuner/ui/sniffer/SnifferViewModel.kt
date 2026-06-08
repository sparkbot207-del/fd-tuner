package com.bretthalliday.fdtuner.ui.sniffer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bretthalliday.fdtuner.ble.FardriverBleManager
import com.bretthalliday.fdtuner.ble.SniffPacket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the packet sniffer UI. Strictly read-only — never calls any write method.
 *
 * State:
 *  - [latestByAddress]  most recent SniffPacket per block base address
 *  - [changedAddresses] fires the base address of any block whose words changed
 *  - session log ring buffer (cap 5 000) used for CSV export
 *  - session min/max per word address (for detail view)
 *  - annotation list (timestamp + tag), merged into the CSV export
 */
class SnifferViewModel(private val bleManager: FardriverBleManager) : ViewModel() {

    // ---- Sorted list of unique block base addresses (derived from protocol table) ----
    val blockAddresses: List<Int> =
        com.bretthalliday.fdtuner.ble.FardriverProtocol.flashReadAddr.toSortedSet().toList()

    // ---- Latest packet per block ----
    private val _latestByAddress = MutableStateFlow<Map<Int, SniffPacket>>(emptyMap())
    val latestByAddress: StateFlow<Map<Int, SniffPacket>> = _latestByAddress

    // ---- Changed-block signal for flash animation ----
    private val _changedAddresses = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 64)
    val changedAddresses: SharedFlow<Int> = _changedAddresses

    // ---- Session log (ring buffer, newest last) ----
    private val sessionLog = ArrayDeque<SniffPacket>()
    private val SESSION_LOG_CAP = 5_000

    // ---- Session min/max per word address ----
    private val _sessionMinMax = mutableMapOf<Int, Pair<Int, Int>>()
    fun getMinMax(wordAddr: Int): Pair<Int, Int>? = _sessionMinMax[wordAddr]

    // ---- Annotations: (timestampMs, tag) ----
    private val annotations = mutableListOf<Pair<Long, String>>()

    // ---- Packet counter for UI subtitle ----
    private val _packetCount = MutableStateFlow(0)
    val packetCount: StateFlow<Int> = _packetCount

    init {
        viewModelScope.launch {
            bleManager.sniffPackets.collect { packet ->
                val prev = _latestByAddress.value[packet.baseAddr]

                // Update latest map
                _latestByAddress.value = HashMap(_latestByAddress.value).apply {
                    put(packet.baseAddr, packet)
                }

                // Session log
                if (sessionLog.size >= SESSION_LOG_CAP) sessionLog.removeFirst()
                sessionLog.addLast(packet)
                _packetCount.value = sessionLog.size

                // Update per-word min/max
                packet.words.forEach { (addr, value) ->
                    val (mn, mx) = _sessionMinMax[addr] ?: Pair(value, value)
                    _sessionMinMax[addr] = Pair(minOf(mn, value), maxOf(mx, value))
                }

                // Signal changed block (only if at least one word differs)
                if (prev != null && prev.words != packet.words) {
                    _changedAddresses.tryEmit(packet.baseAddr)
                }
            }
        }
    }

    // ---- Annotations ----

    fun addAnnotation(tag: String) {
        annotations.add(Pair(System.currentTimeMillis(), tag))
    }

    // ---- Session management ----

    fun clearSession() {
        sessionLog.clear()
        annotations.clear()
        _sessionMinMax.clear()
        _latestByAddress.value = emptyMap()
        _packetCount.value = 0
    }

    // ---- CSV export ----

    /**
     * Builds CSV text by merging the session log with annotation markers (sorted by time).
     * Columns: timestamp_ms, id, base_addr, raw_hex_16,
     *          w0_addr, w0_val, w1_addr, w1_val, w2_addr, w2_val,
     *          w3_addr, w3_val, w4_addr, w4_val, w5_addr, w5_val, tag
     */
    fun buildCsvText(): String {
        val sb = StringBuilder()
        sb.append(
            "timestamp_ms,id,base_addr,raw_hex_16," +
            "w0_addr,w0_val,w1_addr,w1_val,w2_addr,w2_val," +
            "w3_addr,w3_val,w4_addr,w4_val,w5_addr,w5_val,tag\n"
        )

        // Merge packets + annotation rows, sorted by time
        data class MergeRow(val ts: Long, val packet: SniffPacket?, val tag: String?)
        val rows = mutableListOf<MergeRow>()
        sessionLog.mapTo(rows) { MergeRow(it.timestampMs, it, null) }
        annotations.mapTo(rows) { MergeRow(it.first, null, it.second) }
        rows.sortBy { it.ts }

        rows.forEach { row ->
            if (row.packet != null) {
                val p = row.packet
                val sortedWords = p.words.entries.sortedBy { it.key }
                val wordCols = (0..5).joinToString(",") { i ->
                    val e = sortedWords.getOrNull(i)
                    if (e != null) "0x${"%02X".format(e.key)},${e.value}" else ","
                }
                sb.append("${p.timestampMs},${p.id},0x${"%02X".format(p.baseAddr)}," +
                          "\"${p.rawHex}\",$wordCols,\n")
            } else {
                // Annotation marker row — empty packet fields, tag in last column
                sb.append("${row.ts},,,,,,,,,,,,,,\"${row.tag ?: ""}\"\n")
            }
        }
        return sb.toString()
    }

    /** Write CSV to cache dir and return a share Intent via FileProvider. */
    fun buildShareIntent(context: Context): Intent {
        val csv = buildCsvText()
        val fileName = "fd_sniff_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FarDriver packet sniffer export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
