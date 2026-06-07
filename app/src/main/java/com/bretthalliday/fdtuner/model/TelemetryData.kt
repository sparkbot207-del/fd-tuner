package com.bretthalliday.fdtuner.model

/**
 * Live telemetry decoded from Fardriver status packets.
 * All values are already converted to display units.
 */
data class TelemetryData(
    /** Battery voltage in Volts */
    val voltage: Float = 0f,
    /** Line current in Amps (negative = regen) */
    val lineCurrent: Float = 0f,
    /** Motor RPM (raw, unsigned) */
    val rpm: Int = 0,
    /** Gear: "N", "L", "M", "H" */
    val gear: String = "N",
    /** Controller (MOS) temperature in °C */
    val controllerTemp: Int = 0,
    /** Motor temperature in °C */
    val motorTemp: Int = 0,
    /** State of charge 0-100% */
    val soc: Int = 0,
    /** Speed in [speedUnit] */
    val speed: Float = 0f,
    /** "mph" or "km/h" */
    val speedUnit: String = "mph"
)
