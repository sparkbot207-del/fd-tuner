package com.bretthalliday.fdtuner.data

import com.bretthalliday.fdtuner.model.ParamDef

/**
 * Complete parameter definitions for all 10 sections.
 * Mirrors the factory Fardriver app menus exactly.
 *
 * For params without a confirmed address: addr=null.
 * These will show "Connect to read" and disable write.
 */
object ParamDefinitions {

    const val SECTION_PARAMETERS = "Parameters"
    const val SECTION_RATIOS_SPEED = "Ratios in Speed"
    const val SECTION_RATIOS_GEAR = "Ratios in Gear"
    const val SECTION_ENERGY_REGEN = "Energy Regenerate"
    const val SECTION_FUNCTIONS = "Functions"
    const val SECTION_DISPLAY = "Display"
    const val SECTION_PROTECT = "Protect"
    const val SECTION_PID = "PID Paras"
    const val SECTION_PRODUCT = "Product"
    const val SECTION_FIXED = "FixedParas"

    val SECTIONS = listOf(
        SECTION_PARAMETERS,
        SECTION_RATIOS_SPEED,
        SECTION_RATIOS_GEAR,
        SECTION_ENERGY_REGEN,
        SECTION_FUNCTIONS,
        SECTION_DISPLAY,
        SECTION_PROTECT,
        SECTION_PID,
        SECTION_PRODUCT,
        SECTION_FIXED
    )

    // ---- Parameters Section ----

    private val parametersSection = listOf(
        ParamDef(
            name = "PhaseOffset",
            addr = 0x0C,
            section = SECTION_PARAMETERS,
            scale = 10f,
            unit = "°",
            minVal = -1800,
            maxVal = 1800,
            notes = "Phase angle offset"
        ),
        ParamDef(
            name = "Motor Direction",
            addr = 0x0B,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 7,
            notes = "bit7 of addr 0x0B; 0=Normal, 1=Reversed"
        ),
        ParamDef(
            name = "RatedVoltage",
            addr = 0x17,
            section = SECTION_PARAMETERS,
            scale = 10f,
            unit = "V",
            minVal = 0,
            maxVal = 1000,
            notes = "Nominal battery voltage",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "MaxSpeed",
            addr = 0x15,
            section = SECTION_PARAMETERS,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Maximum motor RPM limit",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "MaxLineCurr",
            addr = 0x19,
            section = SECTION_PARAMETERS,
            scale = 4f,
            unit = "A",
            minVal = 0,
            maxVal = 999,
            notes = "Maximum battery line current",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "ThrottleResponse",
            addr = 0x1A,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 0,
            maxVal = 2,
            bitMask = 0x03,
            bitShift = 2,
            isLoByte = true,
            notes = "0=Line, 1=Sport, 2=ECO"
        ),
        ParamDef(
            name = "BoostLineCurr",
            addr = 0x26,
            section = SECTION_PARAMETERS,
            scale = 4f,
            unit = "A",
            minVal = 0,
            maxVal = 999,
            notes = "Boost / custom max line current",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "PhaseExchange",
            addr = 0x0B,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 0,
            isHiByte = true,
            notes = "Phase wire swap; bit0 of hi-byte at addr 0x0B"
        ),
        ParamDef(
            name = "WeakCharacter",
            addr = 0x1A,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 0,
            maxVal = 3,
            bitMask = 0x03,
            bitShift = 4,
            isLoByte = true,
            notes = "Field-weakening level (WeakA)"
        ),
        ParamDef(
            name = "ThrottleLow",
            addr = 0x08,
            section = SECTION_PARAMETERS,
            scale = 20f,
            unit = "V",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Throttle low voltage threshold"
        ),
        ParamDef(
            name = "TempSensor",
            addr = 0x0B,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 0,
            maxVal = 7,
            bitMask = 0x07,
            bitShift = 3,
            notes = "0=None,1=PTC,2=NTC230K,3=KTY84,4=CACU,5=KTY83,6=NTC10K,7=NTC100K"
        ),
        ParamDef(
            name = "PolePairs",
            addr = 0x14,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 1,
            maxVal = 50,
            isLoByte = true,
            notes = "Motor pole pairs — critical for speed calc"
        ),
        ParamDef(
            name = "RatedSpeed",
            addr = 0x18,
            section = SECTION_PARAMETERS,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Rated (base) motor speed"
        ),
        ParamDef(
            name = "RatedPower",
            addr = 0x16,
            section = SECTION_PARAMETERS,
            unit = "W",
            minVal = 0,
            maxVal = 99999,
            notes = "Motor rated power"
        ),
        ParamDef(
            name = "BackSpeed",
            addr = 0x28,
            section = SECTION_PARAMETERS,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Reverse speed limit"
        ),
        ParamDef(
            name = "MaxPhaseCurr",
            addr = 0x2D,
            section = SECTION_PARAMETERS,
            scale = 4f,
            unit = "A",
            minVal = 0,
            maxVal = 999,
            notes = "Maximum phase current",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "BoostPhaseCurr",
            addr = 0x27,
            section = SECTION_PARAMETERS,
            scale = 4f,
            unit = "A",
            minVal = 0,
            maxVal = 999,
            notes = "Boost / custom max phase current",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "ThrottleHigh",
            addr = 0x08,
            section = SECTION_PARAMETERS,
            scale = 20f,
            unit = "V",
            minVal = 0,
            maxVal = 255,
            isHiByte = true,
            notes = "Throttle high voltage threshold"
        )
    )

    // ---- PID Paras Section ----

    private val pidSection = listOf(
        ParamDef(
            name = "AN",
            addr = 0x9C,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 16,
            bitMask = 0x0F,
            bitShift = 0,
            isLoByte = true,
            notes = "Wave type bits[3:0]"
        ),
        ParamDef(
            name = "LM",
            addr = 0x9C,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 31,
            bitMask = 0x1F,
            bitShift = 4,
            isLoByte = true,
            notes = "Wave interval bits[7:4]"
        ),
        ParamDef(
            name = "StartKI",
            addr = 0x0F,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Current loop KI at start"
        ),
        ParamDef(
            name = "MidKI",
            addr = 0x0F,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isHiByte = true,
            notes = "Current loop KI at mid"
        ),
        ParamDef(
            name = "MaxKI",
            addr = 0x10,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Current loop KI at max"
        ),
        ParamDef(
            name = "StartKP",
            addr = 0x10,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isHiByte = true,
            notes = "Current loop KP at start"
        ),
        ParamDef(
            name = "MidKP",
            addr = 0x11,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Current loop KP at mid"
        ),
        ParamDef(
            name = "MaxKP",
            addr = 0x11,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isHiByte = true,
            notes = "Current loop KP at max"
        ),
        ParamDef(
            name = "SpeedKI",
            addr = 0x07,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Speed loop KI"
        ),
        ParamDef(
            name = "SpeedKP",
            addr = 0x07,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isHiByte = true,
            notes = "Speed loop KP"
        ),
        ParamDef(
            name = "CurveTime",
            addr = 0x0A,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "Acceleration curve time"
        ),
        ParamDef(
            name = "MOE",
            addr = 0x12,
            section = SECTION_PID,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "LD field compensation"
        )
    )

    // ---- Protect Section ----

    private val protectSection = listOf(
        ParamDef(
            name = "HighVolProtect",
            addr = 0x25,
            section = SECTION_PROTECT,
            scale = 10f,
            unit = "V",
            minVal = 0,
            maxVal = 1000,
            notes = "Over-voltage cutoff threshold",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "HighVolRestore",
            addr = null,
            section = SECTION_PROTECT,
            scale = 10f,
            unit = "V",
            notes = "≈ HighVolProtect + 2V (derived)"
        ),
        ParamDef(
            name = "LowVolProtect",
            addr = 0x1F,
            section = SECTION_PROTECT,
            scale = 10f,
            unit = "V",
            minVal = 0,
            maxVal = 1000,
            notes = "Under-voltage cutoff threshold",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "LowVolRestore",
            addr = null,
            section = SECTION_PROTECT,
            scale = 10f,
            unit = "V",
            notes = "≈ LowVolProtect + 2V (derived)"
        ),
        ParamDef(
            name = "MotorTempProtect",
            addr = null,
            section = SECTION_PROTECT,
            unit = "°C",
            notes = "Motor over-temp cutoff (addr in 0x82 area)"
        ),
        ParamDef(
            name = "MosTempProtect",
            addr = null,
            section = SECTION_PROTECT,
            unit = "°C",
            notes = "MOSFET over-temp cutoff (addr in 0x7C area)"
        ),
        ParamDef(
            name = "ZeroBattCoeff",
            addr = 0x0D,
            section = SECTION_PROTECT,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "SOC 0% voltage coefficient"
        ),
        ParamDef(
            name = "FullBattCoeff",
            addr = 0x0E,
            section = SECTION_PROTECT,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "SOC 100% voltage coefficient"
        ),
        ParamDef(
            name = "IntRes",
            addr = 0x1D,
            section = SECTION_PROTECT,
            unit = "mΩ",
            minVal = 0,
            maxVal = 9999,
            notes = "Battery internal resistance"
        ),
        ParamDef(
            name = "BattRatedCap",
            addr = 0x1C,
            section = SECTION_PROTECT,
            unit = "Ah",
            minVal = 0,
            maxVal = 9999,
            notes = "Battery rated capacity"
        )
    )

    // ---- Energy Regenerate Section ----

    private val energyRegenSection = buildList {
        // Left #1 — StopBackCurr
        add(ParamDef(
            name = "StopBackCurr",
            addr = null,
            section = SECTION_ENERGY_REGEN,
            scale = 4f,
            unit = "A",
            notes = "Regen current at stop (addr TBD — in 0x63 area)"
        ))
        // Left #2 — BattRatedCapacity
        add(ParamDef(
            name = "Batt RatedCapacity",
            addr = 0x1C,
            section = SECTION_ENERGY_REGEN,
            unit = "Ah",
            minVal = 0,
            maxVal = 9999,
            notes = "Battery rated capacity in amp-hours"
        ))
        // Left #3 — FreeThrottle
        add(ParamDef(
            name = "FreeThrottle",
            addr = 0x2C,
            section = SECTION_ENERGY_REGEN,
            unit = "%",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Regen throttle percentage threshold"
        ))
        // Right #1 — MaxBackCurr
        add(ParamDef(
            name = "MaxBackCurr",
            addr = null,
            section = SECTION_ENERGY_REGEN,
            scale = 4f,
            unit = "A",
            isSafetyCritical = true,
            notes = "Maximum regen current — increasing allows stronger braking (addr TBD)"
        ))

        // Regen curve: 18 RPM/% pairs
        // nratio values (%) confirmed from fardriver.hpp:
        //   nratio_0..13 → addr TBD (before 0x94)
        //   nratio_14 → 0x94 lo byte
        //   nratio_15 → 0x94 hi byte
        //   nratio_16 → 0x9A lo byte
        //   nratio_17 → 0x9A hi byte
        // RPM setpoints → addr TBD (in 0x63/0x69 area)

        val regenNratioAddrs: List<Triple<Int?, Boolean, Boolean>> = listOf(
            // addr,   isLoByte, isHiByte
            Triple(null, false, false), // point 1  — nratio_0, addr TBD
            Triple(null, false, false), // point 2  — nratio_1
            Triple(null, false, false), // point 3  — nratio_2
            Triple(null, false, false), // point 4  — nratio_3
            Triple(null, false, false), // point 5  — nratio_4
            Triple(null, false, false), // point 6  — nratio_5
            Triple(null, false, false), // point 7  — nratio_6
            Triple(null, false, false), // point 8  — nratio_7
            Triple(null, false, false), // point 9  — nratio_8
            Triple(null, false, false), // point 10 — nratio_9
            Triple(null, false, false), // point 11 — nratio_10
            Triple(null, false, false), // point 12 — nratio_11
            Triple(null, false, false), // point 13 — nratio_12
            Triple(null, false, false), // point 14 — nratio_13
            Triple(0x94, true,  false), // point 15 — nratio_14 @ 0x94 lo
            Triple(0x94, false, true ), // point 16 — nratio_15 @ 0x94 hi
            Triple(0x9A, true,  false), // point 17 — nratio_16 @ 0x9A lo
            Triple(0x9A, false, true ), // point 18 — nratio_17 @ 0x9A hi
        )

        for (i in 1..18) {
            val (pctAddr, pctLo, pctHi) = regenNratioAddrs[i - 1]
            // RPM setpoint for each curve point
            add(ParamDef(
                name = "Regen${i}_RPM",
                addr = null,   // TBD — confirm from controller capture
                section = SECTION_ENERGY_REGEN,
                unit = "RPM",
                minVal = 0,
                maxVal = 9999,
                notes = "Regen curve point $i — RPM setpoint (addr TBD)"
            ))
            // Regen percentage for each curve point
            add(ParamDef(
                name = "Regen${i}_Pct",
                addr = pctAddr,
                section = SECTION_ENERGY_REGEN,
                unit = "%",
                minVal = -100,
                maxVal = 100,
                isLoByte = pctLo,
                isHiByte = pctHi,
                notes = "Regen curve point $i — regen %" +
                    if (pctAddr == null) " (addr TBD)" else " @ 0x${pctAddr.toString(16)}"
            ))
        }
    }

    // ---- Ratios in Speed Section ----

    private val ratiosSpeedSection = buildList {
        // 9 RPM set points: 0x30-0x38
        for (i in 1..9) {
            add(
                ParamDef(
                    name = "Ratio${i}_RPM",
                    addr = 0x30 + (i - 1),
                    section = SECTION_RATIOS_SPEED,
                    unit = "RPM",
                    minVal = 0,
                    maxVal = 9999,
                    notes = "Speed curve point $i — RPM setpoint"
                )
            )
        }
        // 9 Percentage values: 0x39-0x41
        for (i in 1..9) {
            add(
                ParamDef(
                    name = "Ratio${i}_Pct",
                    addr = 0x39 + (i - 1),
                    section = SECTION_RATIOS_SPEED,
                    unit = "%",
                    minVal = 0,
                    maxVal = 100,
                    notes = "Speed curve point $i — current percentage"
                )
            )
        }
        add(
            ParamDef(
                name = "LD",
                addr = 0x12,
                section = SECTION_RATIOS_SPEED,
                unit = "",
                minVal = 0,
                maxVal = 9999,
                notes = "D-axis inductance compensation"
            )
        )
        add(
            ParamDef(
                name = "LQ",
                addr = 0x1B,
                section = SECTION_RATIOS_SPEED,
                unit = "",
                minVal = 0,
                maxVal = 9999,
                notes = "Q-axis inductance compensation"
            )
        )
        add(
            ParamDef(
                name = "FAIF",
                addr = 0x09,
                section = SECTION_RATIOS_SPEED,
                unit = "",
                minVal = -32768,
                maxVal = 32767,
                notes = "Field-weakening advance factor (int16)"
            )
        )
        add(
            ParamDef(
                name = "LimitSpeed",
                addr = null,
                section = SECTION_RATIOS_SPEED,
                unit = "RPM",
                notes = "Field-weakening start RPM (addr TBD)"
            )
        )
    }

    // ---- Ratios in Gear Section ----

    private val ratiosGearSection = listOf(
        ParamDef(
            name = "LowSpeedLineRatio",
            addr = null,
            section = SECTION_RATIOS_GEAR,
            unit = "%",
            notes = "Low gear line current ratio (addr in 0x7C area)"
        ),
        ParamDef(
            name = "LowSpeedPhaseRatio",
            addr = null,
            section = SECTION_RATIOS_GEAR,
            unit = "%",
            notes = "Low gear phase current ratio (addr in 0x7C area)"
        ),
        ParamDef(
            name = "LowSpeed",
            addr = 0x29,
            section = SECTION_RATIOS_GEAR,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Low gear speed limit"
        ),
        ParamDef(
            name = "MidSpeedLineRatio",
            addr = null,
            section = SECTION_RATIOS_GEAR,
            unit = "%",
            notes = "Mid gear line current ratio (addr in 0x82 area)"
        ),
        ParamDef(
            name = "MidSpeedPhaseRatio",
            addr = null,
            section = SECTION_RATIOS_GEAR,
            unit = "%",
            notes = "Mid gear phase current ratio (addr in 0x82 area)"
        ),
        ParamDef(
            name = "MidSpeed",
            addr = 0x2A,
            section = SECTION_RATIOS_GEAR,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Mid gear speed limit"
        )
    )

    // ---- Functions Section ----

    private val functionsSection = listOf(
        ParamDef(name = "BoostPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "CruisePin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "SideStandPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "ForwardPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "BackwardPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "HighSpeedPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "LowSpeedPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "ChargePin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "AntiTheftPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "SeatPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "SpeedLimitPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "SwitchVoltagePin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "PausePin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "RepairPin", addr = null, section = SECTION_FUNCTIONS, notes = "Pin assignment — addr TBD"),
        ParamDef(name = "BoostTime", addr = null, section = SECTION_FUNCTIONS, unit = "ms", notes = "Boost hold time — addr TBD"),
        ParamDef(name = "BoostRelease", addr = null, section = SECTION_FUNCTIONS, notes = "Boost release config — addr TBD"),
        ParamDef(
            name = "HighLowSpeed",
            addr = null,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 1,
            notes = "High/Low speed toggle — addr TBD"
        ),
        ParamDef(
            name = "EmptyRun",
            addr = null,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 1,
            notes = "No-load run enable — addr TBD"
        ),
        ParamDef(
            name = "Gear",
            addr = 0x1A,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 7,
            bitMask = 0x07,
            bitShift = 5,
            isLoByte = true,
            notes = "GearConfig bits[7:5]"
        ),
        ParamDef(
            name = "Brake",
            addr = 0x0B,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 15,
            bitMask = 0x0F,
            bitShift = 0,
            isLoByte = true,
            notes = "BrakeConfig bits[3:0]"
        ),
        ParamDef(
            name = "Park",
            addr = 0x0B,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 3,
            bitMask = 0x03,
            bitShift = 5,
            isLoByte = true,
            notes = "ParkConfig bits[6:5]"
        ),
        ParamDef(
            name = "Cruise",
            addr = 0x21,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 0,
            notes = "Cruise control enable bit"
        ),
        ParamDef(
            name = "Push",
            addr = 0x21,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 1,
            notes = "Walk-push assist enable bit"
        ),
        ParamDef(
            name = "EABS",
            addr = 0x21,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 2,
            notes = "Electronic ABS enable bit"
        ),
        ParamDef(
            name = "Follow",
            addr = 0x1A,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 3,
            bitMask = 0x03,
            bitShift = 0,
            isLoByte = true,
            notes = "FollowConfig bits[1:0]"
        )
    )

    // ---- Display Section ----

    private val displaySection = listOf(
        ParamDef(
            name = "SpeedPulses",
            addr = 0x1A,
            section = SECTION_DISPLAY,
            unit = "",
            minVal = 0,
            maxVal = 31,
            bitMask = 0x1F,
            bitShift = 0,
            isHiByte = true,
            notes = "Speedometer pulse count bits[4:0] of hi-byte"
        ),
        ParamDef(name = "SpeedoMeter", addr = null, section = SECTION_DISPLAY, notes = "Speedometer type — addr TBD"),
        ParamDef(name = "CAN", addr = null, section = SECTION_DISPLAY, notes = "CAN bus config — addr TBD"),
        ParamDef(name = "WheelRatio", addr = null, section = SECTION_DISPLAY, notes = "Wheel gear ratio — addr TBD"),
        ParamDef(name = "GearRatio", addr = null, section = SECTION_DISPLAY, notes = "Gear display ratio — addr TBD"),
        ParamDef(name = "WheelWidth", addr = null, section = SECTION_DISPLAY, unit = "mm", notes = "Wheel width — addr TBD"),
        ParamDef(name = "WheelR", addr = null, section = SECTION_DISPLAY, unit = "mm", notes = "Wheel radius — addr TBD"),
        ParamDef(name = "Step", addr = null, section = SECTION_DISPLAY, notes = "Step size — addr TBD"),
        ParamDef(name = "SpecialFrame", addr = null, section = SECTION_DISPLAY, notes = "Special frame mode — addr TBD"),
        ParamDef(
            name = "SQH",
            addr = 0xBB,
            section = SECTION_DISPLAY,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Display SQH parameter"
        ),
        ParamDef(
            name = "Pulse",
            addr = 0xBB,
            section = SECTION_DISPLAY,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isHiByte = true,
            notes = "Display pulse parameter"
        )
    )

    // ---- Product Section ----

    private val productSection = listOf(
        ParamDef(
            name = "FwReRatio",
            addr = 0x1E,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Forward/reverse ratio"
        ),
        ParamDef(name = "ReCurrRatio", addr = null, section = SECTION_PRODUCT, notes = "Regen current ratio — addr TBD"),
        ParamDef(
            name = "VolSelectRatio",
            addr = 0x9F,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 255,
            isLoByte = true,
            notes = "Voltage select ratio"
        ),
        ParamDef(name = "WeakCurrCoeff", addr = null, section = SECTION_PRODUCT, notes = "Field-weakening current coefficient — addr TBD"),
        ParamDef(
            name = "BCEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 3,
            notes = "BC (boost control) enable bit"
        ),
        ParamDef(
            name = "SeatEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 4,
            notes = "Seat sensor enable bit"
        ),
        ParamDef(
            name = "PEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 5,
            notes = "P gear enable bit"
        ),
        ParamDef(
            name = "AutoBackPEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 6,
            notes = "Auto-back to P gear enable"
        ),
        ParamDef(
            name = "CruiseEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 0,
            notes = "Cruise control enable (shared with Functions)"
        ),
        ParamDef(
            name = "EABSEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 2,
            notes = "EABS enable (shared with Functions)"
        ),
        ParamDef(
            name = "PushEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 1,
            notes = "Walk-push enable (shared with Functions)"
        ),
        ParamDef(
            name = "OverSpeedAlarm",
            addr = 0x22,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 0,
            notes = "Over-speed alarm enable"
        ),
        ParamDef(
            name = "RememberGear",
            addr = 0x22,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 1,
            notes = "Remember last gear on power-on"
        ),
        ParamDef(
            name = "BackEnable",
            addr = 0x22,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 2,
            notes = "Reverse gear enable"
        ),
        ParamDef(
            name = "RelayDelay1S",
            addr = 0x22,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 3,
            notes = "1-second relay delay enable"
        ),
        ParamDef(
            name = "RelayOut",
            addr = 0x9C,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 15,
            notes = "Relay output control bit"
        ),
        ParamDef(
            name = "AlarmDelay",
            addr = 0x13,
            section = SECTION_PRODUCT,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "Alarm activation delay"
        )
    )

    // ---- FixedParas Section ----

    private val fixedParasSection = listOf(
        ParamDef(
            name = "Ratio1_RPM_Fixed",
            addr = 0x5D,
            section = SECTION_FIXED,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Fixed speed ratio block 1"
        ),
        ParamDef(
            name = "Ratio2_RPM_Fixed",
            addr = 0x63,
            section = SECTION_FIXED,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Fixed speed ratio block 2"
        ),
        ParamDef(
            name = "Ratio3_RPM_Fixed",
            addr = 0x69,
            section = SECTION_FIXED,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Fixed speed ratio block 3"
        ),
        ParamDef(
            name = "VoltageAdj",
            addr = null,
            section = SECTION_FIXED,
            scale = 10f,
            unit = "V",
            notes = "Voltage calibration — addr TBD"
        ),
        ParamDef(
            name = "CurrentAdj",
            addr = null,
            section = SECTION_FIXED,
            scale = 10f,
            unit = "A",
            notes = "Current calibration — addr TBD"
        )
    )

    // ---- Combined map & accessors ----

    val allParams: List<ParamDef> =
        parametersSection +
        ratiosSpeedSection +
        ratiosGearSection +
        energyRegenSection +
        functionsSection +
        displaySection +
        protectSection +
        pidSection +
        productSection +
        fixedParasSection

    val bySection: Map<String, List<ParamDef>> = allParams.groupBy { it.section }

    fun forSection(section: String): List<ParamDef> = bySection[section] ?: emptyList()
}
