package com.bretthalliday.fdtuner.model

/**
 * Definition of a single tunable parameter.
 *
 * @param name       Display name shown in the UI
 * @param addr       Word address (null = unknown/not writable)
 * @param section    Section this param belongs to
 * @param scale      Divide rawValue by scale to get display value (e.g. 10.0 for /10)
 * @param unit       Unit label (V, A, RPM, °C, etc.)
 * @param minVal     Minimum allowed display value
 * @param maxVal     Maximum allowed display value
 * @param bitMask    If non-null, only these bits are used in the word (read-modify-write)
 * @param bitShift   Shift amount when extracting/packing bits
 * @param isHiByte   If true, value lives in the high byte of the word
 * @param isLoByte   If true, value lives in the low byte of the word
 * @param notes      Optional extra info (shown as a hint in edit dialog)
 */
data class ParamDef(
    val name: String,
    val addr: Int?,          // null = address unknown, read-only placeholder
    val section: String,
    val scale: Float = 1f,
    val unit: String = "",
    val minVal: Int = 0,
    val maxVal: Int = 9999,
    val bitMask: Int? = null,
    val bitShift: Int = 0,
    val isHiByte: Boolean = false,
    val isLoByte: Boolean = false,
    val notes: String = "",
    val isSafetyCritical: Boolean = false,
    /** True if this param is live telemetry; write button should be hidden/disabled. */
    val isReadOnly: Boolean = false
) {
    /** True if this param can be written to the controller */
    /** True if this param can be written to the controller (has an address AND is not read-only telemetry) */
    val isWritable: Boolean get() = addr != null && !isReadOnly

    /** Extract the display value from a raw word read from the controller */
    fun extractValue(rawWord: Int): Int {
        val working = if (isHiByte) {
            (rawWord shr 8) and 0xFF
        } else if (isLoByte) {
            rawWord and 0xFF
        } else {
            rawWord
        }
        return if (bitMask != null) {
            (working shr bitShift) and bitMask
        } else {
            working
        }
    }

    /** Pack a new display value into a raw word, preserving unrelated bits */
    fun packValue(rawWord: Int, newDisplayValue: Int): Int {
        return if (bitMask != null) {
            if (isHiByte) {
                val shifted = (newDisplayValue and bitMask) shl bitShift
                val hiCleared = rawWord and (((bitMask shl bitShift) shl 8).inv())
                hiCleared or (shifted shl 8)
            } else if (isLoByte) {
                val shifted = (newDisplayValue and bitMask) shl bitShift
                val loCleared = rawWord and ((bitMask shl bitShift).inv() and 0xFF)
                (rawWord and 0xFF00) or (loCleared or shifted)
            } else {
                val shifted = (newDisplayValue and bitMask) shl bitShift
                val cleared = rawWord and (bitMask shl bitShift).inv()
                cleared or shifted
            }
        } else if (isHiByte) {
            (rawWord and 0x00FF) or ((newDisplayValue and 0xFF) shl 8)
        } else if (isLoByte) {
            (rawWord and 0xFF00) or (newDisplayValue and 0xFF)
        } else {
            newDisplayValue
        }
    }

    /**
     * Format the display value as a string WITHOUT unit.
     * The unit label is shown separately by the tvParamUnit TextView in the layout
     * to avoid double-suffix (e.g. "3000 RPM RPM").
     */
    fun formatDisplay(rawWord: Int?): String {
        if (rawWord == null) return "—"
        val extracted = extractValue(rawWord)
        return if (scale != 1f) "%.1f".format(extracted / scale)
        else "$extracted"
    }
}
