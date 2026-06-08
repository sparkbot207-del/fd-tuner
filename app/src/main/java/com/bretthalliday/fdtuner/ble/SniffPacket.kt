package com.bretthalliday.fdtuner.ble

/**
 * One decoded status packet from the controller (or a synthetic demo packet).
 *
 * @param timestampMs  Wall-clock time when the packet arrived (System.currentTimeMillis)
 * @param id           6-bit message id (data[1] and 0x3F)
 * @param baseAddr     flashReadAddr[id] — the word address of the first word in this block
 * @param rawHex       16 raw bytes as space-separated uppercase hex (e.g. "AA 46 E2 …")
 * @param words        6 decoded words: wordAddr (blockBase+offset) → uint16 value
 */
data class SniffPacket(
    val timestampMs: Long,
    val id: Int,
    val baseAddr: Int,
    val rawHex: String,
    val words: Map<Int, Int>
)
