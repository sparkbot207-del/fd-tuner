package com.bretthalliday.fdtuner.data

import com.bretthalliday.fdtuner.model.ParamDef
import com.bretthalliday.fdtuner.model.GeneratedParamDefs

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
    /** Read-only live state/telemetry (blocks D6/E2/E8/EE/F4/FA). Never writable, never in the editor. */
    const val SECTION_DIAGNOSTICS = "Diagnostics"

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
            isHiByte = true,       // HPP Addr06 byte11 = HI byte of word 0x0B
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 7,          // bit7 of hi byte
            notes = "HPP Addr06 byte11 bit7 (HI byte of 0x0B). 0=Normal, 1=Reversed."
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
            scale = 4f,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Maximum motor RPM limit (raw = mechanical_RPM × 4)",
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
            isLoByte = true,       // HPP Addr06 byte10 = LO byte of word 0x0B
            unit = "",
            minVal = 0,
            maxVal = 7,
            bitMask = 0x07,
            bitShift = 4,          // bits[4:6] of lo byte (was 3 — wrong)
            notes = "HPP Addr06 byte10 bits4-6. 0=None,1=PTC,2=NTC230K,3=KTY84,4=CACU,5=KTY83,6=NTC10K,7=NTC100K"
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
            scale = 4f,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Rated (base) motor speed (raw = mechanical_RPM × 4)"
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
            scale = 4f,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Reverse speed limit (raw = mechanical_RPM × 4)"
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
        ),
        // ---- Inductance / field-weakening params (Fix 6 — confirmed from live ND72680) ----
        ParamDef(
            name = "LD",
            addr = 0x12,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "D-axis inductance (weak magnetic compensation)"
        ),
        ParamDef(
            name = "LQ",
            addr = 0x1B,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "Q-axis inductance"
        ),
        ParamDef(
            name = "FAIF",
            addr = 0x09,
            section = SECTION_PARAMETERS,
            unit = "",
            minVal = -32768,
            maxVal = 32767,
            notes = "Starting resonance / field-weakening advance factor (int16)"
        ),
        ParamDef(
            name = "LimitSpeed",
            addr = 0x6C,
            section = SECTION_PARAMETERS,
            scale = 4f,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Speed limiter setpoint (Addr69 block; raw = mechanical_RPM × 4)"
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
            addr = 0x83,
            section = SECTION_PROTECT,
            scale = 10f,
            unit = "V",
            minVal = 0,
            maxVal = 1500,
            notes = "HPP Addr82 word 0x83, /10. Over-voltage restore threshold."
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
            addr = 0x84,
            section = SECTION_PROTECT,
            isLoByte = true,
            unit = "°C",
            minVal = 0,
            maxVal = 250,
            notes = "HPP Addr82 byte4 (lo of 0x84). Motor over-temp cutoff.",
            isSafetyCritical = true
        ),
        ParamDef(
            name = "MosTempProtect",
            addr = 0x85,
            section = SECTION_PROTECT,
            isLoByte = true,
            unit = "°C",
            minVal = 0,
            maxVal = 250,
            notes = "HPP Addr82 byte6 (lo of 0x85). MOSFET over-temp cutoff.",
            isSafetyCritical = true
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
    //
    // PROTOCOL FACT: RPM setpoints in this curve are FIXED constants derived from the
    // raw field name (e.g. Ratio500 ÷ 4 = 125 RPM). Only the percentage byte is stored.
    // Addresses confirmed from fardriver.hpp and validated against a live ND72680_24_A_H9.
    //
    // addr 0x88 lo=RatioMin,    hi=Ratio500  (125 RPM)
    // addr 0x89 lo=Ratio1000  (250 RPM),    hi=Ratio1500  (375 RPM)
    // addr 0x8A lo=Ratio2000  (500 RPM),    hi=Ratio2500  (625 RPM)
    // addr 0x8B lo=Ratio3000  (750 RPM),    hi=Ratio3500  (875 RPM)
    // addr 0x8C lo=Ratio4000 (1000 RPM),    hi=Ratio4500 (1125 RPM)
    // addr 0x8D lo=Ratio5000 (1250 RPM),    hi=Ratio5500 (1375 RPM)
    // addr 0x8E lo=Ratio6000 (1500 RPM),    hi=Ratio6500 (1625 RPM)
    // addr 0x8F lo=Ratio7000 (1750 RPM),    hi=Ratio7500 (1875 RPM)
    // addr 0x90 lo=Ratio8000 (2000 RPM),    hi=Ratio8500 (2125 RPM)
    // addr 0x91 lo=Ratio9000 (2250 RPM),    hi=RatioMax

    private val ratiosSpeedSection = listOf(
        ParamDef(name = "RatioMin",          addr = 0x88, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Floor output % applied below the first breakpoint (125 RPM)"),
        ParamDef(name = "Ratio @ 125 RPM",  addr = 0x88, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio500 ÷ 4 = 125 RPM"),
        ParamDef(name = "Ratio @ 250 RPM",  addr = 0x89, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio1000 ÷ 4 = 250 RPM"),
        ParamDef(name = "Ratio @ 375 RPM",  addr = 0x89, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio1500 ÷ 4 = 375 RPM"),
        ParamDef(name = "Ratio @ 500 RPM",  addr = 0x8A, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio2000 ÷ 4 = 500 RPM"),
        ParamDef(name = "Ratio @ 625 RPM",  addr = 0x8A, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio2500 ÷ 4 = 625 RPM"),
        ParamDef(name = "Ratio @ 750 RPM",  addr = 0x8B, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio3000 ÷ 4 = 750 RPM"),
        ParamDef(name = "Ratio @ 875 RPM",  addr = 0x8B, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio3500 ÷ 4 = 875 RPM"),
        ParamDef(name = "Ratio @ 1000 RPM", addr = 0x8C, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio4000 ÷ 4 = 1000 RPM"),
        ParamDef(name = "Ratio @ 1125 RPM", addr = 0x8C, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio4500 ÷ 4 = 1125 RPM"),
        ParamDef(name = "Ratio @ 1250 RPM", addr = 0x8D, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio5000 ÷ 4 = 1250 RPM"),
        ParamDef(name = "Ratio @ 1375 RPM", addr = 0x8D, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio5500 ÷ 4 = 1375 RPM"),
        ParamDef(name = "Ratio @ 1500 RPM", addr = 0x8E, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio6000 ÷ 4 = 1500 RPM"),
        ParamDef(name = "Ratio @ 1625 RPM", addr = 0x8E, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio6500 ÷ 4 = 1625 RPM"),
        ParamDef(name = "Ratio @ 1750 RPM", addr = 0x8F, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio7000 ÷ 4 = 1750 RPM"),
        ParamDef(name = "Ratio @ 1875 RPM", addr = 0x8F, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio7500 ÷ 4 = 1875 RPM"),
        ParamDef(name = "Ratio @ 2000 RPM", addr = 0x90, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio8000 ÷ 4 = 2000 RPM"),
        ParamDef(name = "Ratio @ 2125 RPM", addr = 0x90, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Ratio8500 ÷ 4 = 2125 RPM"),
        ParamDef(name = "Ratio @ 2250 RPM", addr = 0x91, section = SECTION_RATIOS_SPEED, isLoByte = true,  unit = "%", minVal = 0, maxVal = 100, notes = "Ratio9000 ÷ 4 = 2250 RPM"),
        ParamDef(name = "RatioMax",          addr = 0x91, section = SECTION_RATIOS_SPEED, isHiByte = true, unit = "%", minVal = 0, maxVal = 100, notes = "Maximum speed ratio (addr 0x91 hi)")
    )

    // ---- Ratios in Gear Section ----
    //
    // Addr30 block (starts at 0x30, 12 bytes = 6 words):
    //   word 0x32: lo=LowSpeedLineCurr, hi=MidSpeedLineCurr
    //   word 0x33: lo=LowSpeedPhaseCurr, hi=MidSpeedPhaseCurr
    // Storage format: percentage = (raw × 100) / 128
    //   e.g. raw=51 → 40%, raw=64 → 50%, raw=77 → 60%, raw=96 → 75%
    // Values shown as raw (0–128). Do NOT use scale=1.28f — packValue doesn’t apply
    // scale on write, so writing a percentage directly would be wrong.
    // TODO: add a custom display/pack lambda when the ParamDef model supports it.

    private val ratiosGearSection = listOf(
        ParamDef(
            name = "LowSpeedLineRatio",
            addr = 0x32,
            section = SECTION_RATIOS_GEAR,
            isLoByte = true,
            unit = "",
            minVal = 0,
            maxVal = 128,
            notes = "Low gear line current ratio — raw 0–128; pct = raw×100/128. Verified: 40% → raw≈51"
        ),
        ParamDef(
            name = "MidSpeedLineRatio",
            addr = 0x32,
            section = SECTION_RATIOS_GEAR,
            isHiByte = true,
            unit = "",
            minVal = 0,
            maxVal = 128,
            notes = "Mid gear line current ratio — raw 0–128; pct = raw×100/128. Verified: 60% → raw≈77"
        ),
        ParamDef(
            name = "LowSpeedPhaseRatio",
            addr = 0x33,
            section = SECTION_RATIOS_GEAR,
            isLoByte = true,
            unit = "",
            minVal = 0,
            maxVal = 128,
            notes = "Low gear phase current ratio — raw 0–128; pct = raw×100/128. Verified: 50% → raw=64"
        ),
        ParamDef(
            name = "MidSpeedPhaseRatio",
            addr = 0x33,
            section = SECTION_RATIOS_GEAR,
            isHiByte = true,
            unit = "",
            minVal = 0,
            maxVal = 128,
            notes = "Mid gear phase current ratio — raw 0–128; pct = raw×100/128. Verified: 75% → raw=96"
        ),
        ParamDef(
            name = "LowSpeed",
            addr = 0x29,
            section = SECTION_RATIOS_GEAR,
            scale = 4f,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Low gear speed limit (raw = mechanical_RPM × 4). Verified: 750 RPM → raw=3000"
        ),
        ParamDef(
            name = "MidSpeed",
            addr = 0x2A,
            section = SECTION_RATIOS_GEAR,
            scale = 4f,
            unit = "RPM",
            minVal = 0,
            maxVal = 9999,
            notes = "Mid gear speed limit (raw = mechanical_RPM × 4). Verified: 1500 RPM → raw=6000"
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
            name = "GearConfig",
            addr = 0x1A,
            section = SECTION_FUNCTIONS,
            isHiByte = true,       // HPP Addr18 byte5 = HI byte of word 0x1A (was isLoByte — wrong byte)
            unit = "",
            minVal = 0,
            maxVal = 5,
            bitMask = 0x07,
            bitShift = 5,          // bits[5:7] of hi byte
            notes = "HPP Addr18 byte5 bits5-7 (HI byte of 0x1A). 0=DefaultN,1=DefaultD,..."
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
            name = "CruiseEnable",
            addr = 0x21,
            section = SECTION_FUNCTIONS,
            isLoByte = true,       // HPP Addr1E byte6 = LO byte of word 0x21
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 4,          // bit4 (was bit0 — wrong)
            notes = "HPP Addr1E byte6 bit4 (lo of 0x21). XHStat / cruise enable."
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
            name = "EABSEnable",
            addr = 0x21,
            section = SECTION_FUNCTIONS,
            isLoByte = true,       // HPP Addr1E byte6 = LO byte of word 0x21
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 5,          // bit5 (was bit2 — wrong)
            notes = "HPP Addr1E byte6 bit5 (lo of 0x21). Electronic regen brake enable."
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
        ),
        // ---- Acceleration/deceleration limits (confirmed from HPP Addr2A block) ----
        ParamDef(
            name = "MaxAcc",
            addr = 0x2F,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "HPP Addr2A word 0x2F. Maximum acceleration rate."
        ),
        ParamDef(
            name = "MaxDec",
            addr = 0x2B,
            section = SECTION_FUNCTIONS,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "HPP Addr2A word 0x2B. Maximum deceleration rate."
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
        // ---- HPP AddrD0 block: wheel/speed display config (confirmed from fardriver.hpp) ----
        // speed = MeasureSpeed × 0.00376991136 × (WheelRadius×1270 + WheelWidth×WheelRatio) / RateRatio
        ParamDef(
            name = "WheelRadius",
            addr = 0xD2,
            section = SECTION_DISPLAY,
            isHiByte = true,       // HPP AddrD0 byte5 (hi of 0xD2)
            unit = "",
            minVal = 0,
            maxVal = 255,
            notes = "HPP AddrD0 byte5 (hi of 0xD2). Used in speed formula."
        ),
        ParamDef(
            name = "WheelWidth",
            addr = 0xD3,
            section = SECTION_DISPLAY,
            isHiByte = true,       // HPP AddrD0 byte7 (hi of 0xD3)
            unit = "",
            minVal = 0,
            maxVal = 255,
            notes = "HPP AddrD0 byte7 (hi of 0xD3)."
        ),
        ParamDef(
            name = "WheelRatio",
            addr = 0xD2,
            section = SECTION_DISPLAY,
            isLoByte = true,       // HPP AddrD0 byte4 (lo of 0xD2)
            unit = "",
            minVal = 0,
            maxVal = 255,
            notes = "HPP AddrD0 byte4 (lo of 0xD2). Wheel gear ratio."
        ),
        ParamDef(
            name = "RateRatio",
            addr = 0xD4,
            section = SECTION_DISPLAY,
            unit = "",
            minVal = 0,
            maxVal = 9999,
            notes = "HPP AddrD0 word 0xD4. Speed display ratio (SpeedRatio)."
        ),
        // ---- Live read-only telemetry (isReadOnly=true — value shown, write disabled) ----
        ParamDef(
            name = "ThrottleVoltage",
            addr = 0x82,
            section = SECTION_DISPLAY,
            scale = 100f,
            unit = "V",
            isReadOnly = true,
            notes = "HPP Addr82 word 0x82, ×0.01. Live throttle position — read only."
        ),
        ParamDef(
            name = "AVGPower",
            addr = 0xD1,
            section = SECTION_DISPLAY,
            isHiByte = true,
            scale = 0.25f,
            unit = "Wh/km",
            isReadOnly = true,
            notes = "HPP AddrD0 byte3 (hi of 0xD1), ×4. Average power — read only."
        ),
        ParamDef(
            name = "AVGSpeed",
            addr = 0xD3,
            section = SECTION_DISPLAY,
            isLoByte = true,
            unit = "km/h",
            isReadOnly = true,
            notes = "HPP AddrD0 byte8 (lo of 0xD3). Average speed — read only."
        ),
        ParamDef(name = "GearRatio", addr = null, section = SECTION_DISPLAY, notes = "Gear display ratio — addr TBD"),
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
            isLoByte = true,       // byte6 of Addr1E block = lo of 0x21
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 4,          // bit4 (was bit0 — wrong)
            notes = "HPP Addr1E byte6 bit4 (lo of 0x21). Cruise enable."
        ),
        ParamDef(
            name = "EABSEnable",
            addr = 0x21,
            section = SECTION_PRODUCT,
            isLoByte = true,       // byte6 of Addr1E block = lo of 0x21
            unit = "",
            minVal = 0,
            maxVal = 1,
            bitMask = 0x01,
            bitShift = 5,          // bit5 (was bit2 — wrong)
            notes = "HPP Addr1E byte6 bit5 (lo of 0x21). EABS enable."
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

    // ---- Curated (hand-built) defs: kept only for their safety bounds, flags & notes ----
    // The complete, authoritative set comes from GeneratedParamDefs (HPP). We merge the curated
    // richer fields (min/max write bounds, isSafetyCritical, notes) onto matching generated
    // entries by name, so regenerating the HPP set never loses curated safety polish.

    private val curatedParams: List<ParamDef> =
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

    /**
     * Per-field polish applied over the generated set. Re-running the generator never loses this.
     * Each entry transforms the generated ParamDef (usually via .copy()).
     */
    val OVERRIDES: Map<String, (ParamDef) -> ParamDef> = mapOf(
        // --- unit corrections (generator infers units from scale; some are wrong) ---
        "PhaseOffset"       to { p: ParamDef -> p.copy(unit = "\u00B0") },   // degrees, not V
        "MotorTempProtect"  to { p: ParamDef -> p.copy(unit = "\u00B0C") },
        "MosTempProtect"    to { p: ParamDef -> p.copy(unit = "\u00B0C") },
        "MotorTempRestore"  to { p: ParamDef -> p.copy(unit = "\u00B0C") },
        "MosTempRestore"    to { p: ParamDef -> p.copy(unit = "\u00B0C") },
        // --- section placement requested by the user ---
        "ReCurrRatio"       to { p: ParamDef -> p.copy(section = SECTION_PRODUCT) },
        // --- official factory-app display names (source: docs/fardriver_menu_map.md, H/M rows) ---
        "ThrottleLow"         to { p: ParamDef -> p.copy(name = "Throttle Low") },
        "ThrottleHigh"        to { p: ParamDef -> p.copy(name = "Throttle High") },
        "MidSpeed"            to { p: ParamDef -> p.copy(name = "MiddleSpeed") },
        "BattRatedCap"        to { p: ParamDef -> p.copy(name = "Batt RatedCapacity") },
        "BstTime"             to { p: ParamDef -> p.copy(name = "BoostTime") },
        "BstRelease"          to { p: ParamDef -> p.copy(name = "BoostRelease") },
        "PC13Config"          to { p: ParamDef -> p.copy(name = "PC13") },
        "SpeedPulse"          to { p: ParamDef -> p.copy(name = "Speed Pulses") },
        "SpeedMeterConfig1"   to { p: ParamDef -> p.copy(name = "SpeedoMeter") },
        "CANConfig"           to { p: ParamDef -> p.copy(name = "CAN") },
        "SpeedMeterConfig2"   to { p: ParamDef -> p.copy(name = "Step") },
        "SpecialCode"         to { p: ParamDef -> p.copy(name = "SpecialFrame") },
        "WheelRadius"         to { p: ParamDef -> p.copy(name = "WheelR") },
        "HighVolProtect"      to { p: ParamDef -> p.copy(name = "HigiVolProtect") },
        "HighVolRestore"      to { p: ParamDef -> p.copy(name = "HigiVolRestore") },
        "ZeroBattCoeff"       to { p: ParamDef -> p.copy(name = "0 BattCoeff") },
        "FullBattCoeff"       to { p: ParamDef -> p.copy(name = "Full BattCoeff") },
        "BackPTime"           to { p: ParamDef -> p.copy(name = "BackP_Time") },
        // --- add confirmed scales/units for newly-decoded fields here as the sniffer reveals them ---
    )

    /** Physical-location identity of a field: same address + byte + bit = same field. */
    private fun physicalKey(p: ParamDef): String =
        "${p.addr}:${p.isLoByte}:${p.isHiByte}:${p.bitMask}:${p.bitShift}"

    /**
     * Effective, complete parameter set.
     *  - Curated defs that have a real address are authoritative (names, sections, units, bounds).
     *  - Curated placeholders with addr == null are dropped (generated provides the real address).
     *  - Generated defs are added ONLY for physical fields not already covered by a curated def.
     *  - OVERRIDES applied last.
     */
    val ALL_PARAMS: List<ParamDef> = run {
        val curatedReal = curatedParams.filter { it.addr != null }
        val coveredKeys = curatedReal.map { physicalKey(it) }.toSet()
        val generatedExtra = GeneratedParamDefs.all.filter { physicalKey(it) !in coveredKeys }
        (curatedReal + generatedExtra)
            // Guard: collapse any residual physical-location duplicates so no field appears
            // twice. Curated entries are listed first so curated wins over generated, and the
            // first-listed section wins among curated-internal dupes. (A handful of curated
            // entries map two names to the same word — see commit notes.)
            .distinctBy { physicalKey(it) }
            .map { def -> OVERRIDES[def.name]?.invoke(def) ?: def }
    }

    // ---- Public accessors (now backed by the complete generated set) ----

    val allParams: List<ParamDef> = ALL_PARAMS

    val bySection: Map<String, List<ParamDef>> =
        allParams.groupBy { it.section }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.addr ?: Int.MAX_VALUE }, { it.bitShift })) }

    fun forSection(section: String): List<ParamDef> = bySection[section] ?: emptyList()

    /** Read-only live state/telemetry params for the Diagnostics screen (never writable). */
    val diagnosticsParams: List<ParamDef> =
        allParams.filter { it.isReadOnly || it.section == SECTION_DIAGNOSTICS }
}
