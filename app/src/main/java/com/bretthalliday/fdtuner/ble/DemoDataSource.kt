package com.bretthalliday.fdtuner.ble

import com.bretthalliday.fdtuner.model.TelemetryData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

/**
 * Simulates a live Fardriver controller for demo mode.
 * Generates realistic telemetry that cycles through a typical ride profile.
 * All param addresses are pre-populated with typical 72v FD-72680 values.
 */
object DemoDataSource {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    private val _telemetry = MutableStateFlow<TelemetryData?>(null)
    val telemetry: StateFlow<TelemetryData?> = _telemetry

    private val _rawParams = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val rawParams: StateFlow<Map<Int, Int>> = _rawParams

    // Typical 72v Fardriver 72680 / QS205 config values
    // addr → raw word value (matches FardriverProtocol address map)
    private val demoParams: Map<Int, Int> = mapOf(
        // Parameters section
        0x0C to 0,           // PhaseOffset: 0°
        0x0B to 0x0000,      // Motor Direction: normal, TempSensor: NTC10K(6)
        0x17 to 720,         // RatedVoltage: 72.0V
        0x15 to 5000,        // MaxSpeed: 5000 RPM
        0x19 to (280 * 4),   // MaxLineCurr: 280A (raw = 280*4 = 1120)
        0x1A to 0x0001,      // ThrottleResponse: Sport
        0x26 to (300 * 4),   // BoostLineCurr: 300A
        0x14 to 23,          // PolePairs: 23 (QS205)
        0x18 to 3500,        // RatedSpeed: 3500 RPM
        0x16 to 8000,        // RatedPower: 8000W
        0x28 to 800,         // BackSpeed: 800 RPM
        0x2D to (680 * 4),   // MaxPhaseCurr: 680A (72680 = 680A phase)
        0x27 to (700 * 4),   // BoostPhaseCurr: 700A
        0x08 to (( (210) shl 8) or 80), // ThrottleHigh: 210/20=10.5V, ThrottleLow: 80/20=4.0V
        0x0B to ((6 shl 3) or 0), // TempSensor: NTC10K(6)

        // PID Paras
        0x0F to ((8 shl 8) or 4),   // MidKI=8, StartKI=4
        0x10 to ((40 shl 8) or 12), // StartKP=40, MaxKI=12
        0x11 to ((120 shl 8) or 80),// MaxKP=120, MidKP=80
        0x07 to ((80 shl 8) or 6),  // SpeedKP=80, SpeedKI=6
        0x0A to 50,                  // CurveTime: 50
        0x12 to 0,                   // LD: 0
        0x9C to ((22 shl 4) or 0),  // LM=22, AN=0

        // Protect
        0x25 to 840,         // HighVolProtect: 84.0V
        0x1F to 600,         // LowVolProtect: 60.0V
        0x0D to 0,           // ZeroBattCoeff
        0x0E to 100,         // FullBattCoeff
        0x1C to 55,          // BattRatedCap: 55Ah
        0x1D to 25,          // IntRes: 25mΩ

        // Ratios in Speed / Gear
        0x1B to 800,         // LQ: 800
        0x09 to 0,           // FAIF: 0
        0x29 to 1500,        // LowSpeed: 1500 RPM
        0x2A to 3000,        // MidSpeed: 3000 RPM

        // Product / Functions
        0x21 to 0b00110101,  // CruiseEnable, PEnable, EABSEnable, BCState
        0x1E to 100,         // FwReRatio: 100

        // Energy Regen
        0x2C to 1,           // FreeThrottle: 1
    )

    fun start() {
        _rawParams.value = demoParams
        job?.cancel()
        job = scope.launch {
            var tick = 0
            while (isActive) {
                val t = tick.toDouble()

                // Simulate a ride: accelerate, cruise, decel, idle, repeat
                val phase = (t % 200) / 200.0  // 0..1 over 200 ticks = 100s cycle

                val (rpm, current, gear) = when {
                    phase < 0.15 -> Triple(                        // Accelerating
                        (phase / 0.15 * 3800).toInt(),
                        (phase / 0.15 * 250).toInt(),
                        if (phase < 0.05) 1 else 2
                    )
                    phase < 0.55 -> Triple(                        // Cruising
                        (3200 + sin(t * 0.3) * 200).toInt(),
                        (45 + sin(t * 0.15) * 25).toInt(),
                        2
                    )
                    phase < 0.70 -> Triple(                        // Decelerating / regen
                        ((1.0 - (phase - 0.55) / 0.15) * 3200).toInt(),
                        (-30 - sin(t * 0.2) * 15).toInt(),        // negative = regen
                        2
                    )
                    phase < 0.80 -> Triple(500, 5, 1)              // Slow / stop
                    else -> Triple(0, 0, 0)                        // Idle
                }

                val voltage = 74.2 - (phase * 3.5) + sin(t * 0.1) * 0.3
                val soc = (90 - phase * 20).toInt().coerceIn(0, 100)
                val ctrlTemp = (38 + phase * 18 + sin(t * 0.05) * 2).toInt()
                val motorTemp = (45 + phase * 22 + sin(t * 0.04) * 3).toInt()

                // Speed from RPM: (RPM / polePairs) * circumference * 60 / 1000 / 1.609
                val polePairs = 23
                val circumM = 2.1
                val speedKmh = (rpm.toDouble() / polePairs) * circumM * 60.0 / 1000.0
                val speedMph = speedKmh * 0.621371

                val gearStr = when (gear) { 0 -> "N"; 1 -> "L"; 2 -> "M"; else -> "H" }

                _telemetry.value = TelemetryData(
                    voltage = voltage.toFloat(),
                    lineCurrent = current.toFloat(),
                    rpm = rpm,
                    gear = gearStr,
                    speed = speedMph.toFloat(),
                    speedUnit = "mph",
                    controllerTemp = ctrlTemp,
                    motorTemp = motorTemp,
                    soc = soc
                )

                tick++
                delay(500)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _telemetry.value = null
        _rawParams.value = emptyMap()
    }

    val isRunning get() = job?.isActive == true

    /** Update a single param in the demo map (reflects immediately in UI). */
    fun updateParam(addr: Int, value: Int) {
        val updated = HashMap(_rawParams.value).apply { put(addr, value) }
        _rawParams.value = updated
    }
}
