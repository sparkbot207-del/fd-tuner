package com.bretthalliday.fdtuner.ble

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

enum class AlertType { MOTOR_TEMP, BATT_LOW }

data class AlertEvent(val message: String, val type: AlertType)

/**
 * Monitors telemetry and emits [AlertEvent] on rising-edge threshold crossings.
 * "Rising-edge only" means each alert fires once when crossing the threshold,
 * not on every telemetry tick while above it.
 */
class AlertMonitor(
    private val bleManager: FardriverBleManager,
    private val prefs: SharedPreferences
) {
    // Track last-alerted state to implement rising-edge only (don't spam)
    private var motorTempAlerted = false
    private var battLowAlerted = false

    private val alertsEnabled: Boolean
        get() = prefs.getBoolean("alerts_enabled", true)

    private val motorTempThreshold: Int
        get() = prefs.getInt("alert_motor_temp", 80)

    private val battVoltLowThreshold: Float
        get() = prefs.getFloat("alert_batt_low", 60.0f)

    /**
     * Returns a Flow that emits [AlertEvent] whenever a threshold is first crossed.
     * Subsequent telemetry ticks while still above the threshold are silently skipped.
     */
    fun alertFlow(): Flow<AlertEvent> =
        bleManager.telemetry
            .mapNotNull { telem ->
                if (telem == null || !alertsEnabled) {
                    // Reset alert state when no telemetry so next crossing fires again
                    null
                } else {
                    checkAlerts(telem.motorTemp, telem.voltage)
                }
            }

    private fun checkAlerts(motorTemp: Int, battVolt: Float): AlertEvent? {
        // Motor temp: rising-edge trigger
        if (motorTemp >= motorTempThreshold) {
            if (!motorTempAlerted) {
                motorTempAlerted = true
                return AlertEvent(
                    "⚠️ Motor temp ${motorTemp}°C ≥ ${motorTempThreshold}°C threshold!",
                    AlertType.MOTOR_TEMP
                )
            }
        } else {
            motorTempAlerted = false // reset when below threshold
        }

        // Battery low: rising-edge trigger
        if (battVolt <= battVoltLowThreshold) {
            if (!battLowAlerted) {
                battLowAlerted = true
                return AlertEvent(
                    "⚠️ Battery ${"%.1f".format(battVolt)}V ≤ ${"%.1f".format(battVoltLowThreshold)}V threshold!",
                    AlertType.BATT_LOW
                )
            }
        } else {
            battLowAlerted = false // reset when above threshold
        }

        return null
    }

    /** Reset alert state on disconnect so the next connection fires fresh. */
    fun reset() {
        motorTempAlerted = false
        battLowAlerted = false
    }
}
