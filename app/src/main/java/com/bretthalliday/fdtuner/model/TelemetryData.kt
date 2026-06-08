package com.bretthalliday.fdtuner.model

/**
 * Live telemetry decoded from Fardriver status packets.
 * All values are already converted to display units.
 *
 * Address map (confirmed from fardriver.hpp):
 *   AddrE2 block: 0xE2=errorFlags, 0xE5=MeasureSpeed(raw=mechRPM×4), 0xE6=gear
 *   AddrE8 block: 0xE8=voltage(deci_V), 0xE9=ctrlTemp, 0xEA=lineCurrent(int16,/4),
 *                 0xEB=motorTemp, 0xEC=SOC
 *   AddrEE block: 0xF0-0xF2=phase currents (24-bit big-endian, 1.953125×sqrt(raw))
 */
data class TelemetryData(
    /** Battery voltage in Volts */
    val voltage: Float = 0f,
    /** Battery/line current in Amps (negative = regen) */
    val lineCurrent: Float = 0f,
    /** Phase A current in Amps RMS */
    val aPhaseCurrent: Float = 0f,
    /** Phase C current in Amps RMS */
    val cPhaseCurrent: Float = 0f,
    /** Motor mechanical RPM (raw ÷ 4) */
    val rpm: Int = 0,
    /** Speed in [speedUnit] */
    val speed: Float = 0f,
    /** "mph" or "km/h" */
    val speedUnit: String = "mph",
    /** Controller (MOS) temperature in °C */
    val controllerTemp: Int = 0,
    /** Motor temperature in °C */
    val motorTemp: Int = 0,
    /** State of charge 0-100% */
    val soc: Int = 0,
    /** Gear: 0=N, 1=L, 2=M, 3=H */
    val gear: Int = 0,
    /** Raw error/status flags from AddrE2 */
    val errorFlags: Int = 0
)
