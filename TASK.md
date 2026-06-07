# FD Tuner - Android App Build Spec

## Summary
Android app that mirrors the factory Fardriver app's full functionality — same menus, same params — but with zero login friction. Scan → connect → tune.

## Project Details
- **App name:** FD Tuner
- **Package:** com.bretthalliday.fdtuner
- **Min SDK:** 26 | **Target/Compile SDK:** 34
- **Language:** Kotlin
- **Architecture:** MVVM + StateFlow
- **Theme:** Dark (Material3), orange accent #FF6600
- **Speed units:** mph (default, togglable)
- **BLE device name filter:** starts with "FD-" (e.g. FD-72680, FD-72450) OR service UUID match

## BLE Protocol

### UUIDs
```
SERVICE_UUID      = "0000ffe0-0000-1000-8000-00805f9b34fb"
CHARACTERISTIC_UUID = "0000ffec-0000-1000-8000-00805f9b34fb"
```

### Status Packets (controller → phone, 16 bytes)
```
B0:    0xAA (magic)
B1:    id[5:0] = message id (0–54), flags[7:6] = usually 2
B2-13: 12 bytes of data
B14-15: CRC
```
If id < 55, use flashReadAddr[id] to get the byte address of data[0..11].

### flashReadAddr array (id → byte address, 55 entries):
```kotlin
val flashReadAddr = intArrayOf(
    0xE2, 0xE8, 0xEE, 0x00, 0x06, 0x0C, 0x12,
    0xE2, 0xE8, 0xEE, 0x18, 0x1E, 0x24, 0x2A,
    0xE2, 0xE8, 0xEE, 0x30, 0x5D, 0x63, 0x69,
    0xE2, 0xE8, 0xEE, 0x7C, 0x82, 0x88, 0x8E,
    0xE2, 0xE8, 0xEE, 0x94, 0x9A, 0xA0, 0xA6,
    0xE2, 0xE8, 0xEE, 0xAC, 0xB2, 0xB8, 0xBE,
    0xE2, 0xE8, 0xEE, 0xC4, 0xCA, 0xD0,
    0xE2, 0xE8, 0xEE, 0xD6, 0xDC, 0xF4, 0xFA
)
```
Each 12-byte data block has 6 words. Word at offset i within the block = byte address (flashReadAddr[id] + i).
Store all received words in a Map<Int, Int> (word address → value).

### Write Packet (phone → controller, 8 bytes)
```
B0: 0xAA
B1: 0x46 (compute_length=6 in bits[5:0], flags=1 in bits[7:6] → 0x40 | 0x06 = 0x46)
B2: addr (word address)
B3: addr (repeated)
B4-5: value (little-endian int16)
B6-7: CRC (computed over B0-B5 with length=8)
```

### CRC Algorithm
```kotlin
val crcTableHi = intArrayOf(
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40,0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,
    0x00,0xC1,0x81,0x40,0x01,0xC0,0x80,0x41,0x01,0xC0,0x80,0x41,0x00,0xC1,0x81,0x40
)
val crcTableLo = intArrayOf(
    0x00,0xC0,0xC1,0x01,0xC3,0x03,0x02,0xC2,0xC6,0x06,0x07,0xC7,0x05,0xC5,0xC4,0x04,
    0xCC,0x0C,0x0D,0xCD,0x0F,0xCF,0xCE,0x0E,0x0A,0xCA,0xCB,0x0B,0xC9,0x09,0x08,0xC8,
    0xD8,0x18,0x19,0xD9,0x1B,0xDB,0xDA,0x1A,0x1E,0xDE,0xDF,0x1F,0xDD,0x1D,0x1C,0xDC,
    0x14,0xD4,0xD5,0x15,0xD7,0x17,0x16,0xD6,0xD2,0x12,0x13,0xD3,0x11,0xD1,0xD0,0x10,
    0xF0,0x30,0x31,0xF1,0x33,0xF3,0xF2,0x32,0x36,0xF6,0xF7,0x37,0xF5,0x35,0x34,0xF4,
    0x3C,0xFC,0xFD,0x3D,0xFF,0x3F,0x3E,0xFE,0xFA,0x3A,0x3B,0xFB,0x39,0xF9,0xF8,0x38,
    0x28,0xE8,0xE9,0x29,0xEB,0x2B,0x2A,0xEA,0xEE,0x2E,0x2F,0xEF,0x2D,0xED,0xEC,0x2C,
    0xE4,0x24,0x25,0xE5,0x27,0xE7,0xE6,0x26,0x22,0xE2,0xE3,0x23,0xE1,0x21,0x20,0xE0,
    0xA0,0x60,0x61,0xA1,0x63,0xA3,0xA2,0x62,0x66,0xA6,0xA7,0x67,0xA5,0x65,0x64,0xA4,
    0x6C,0xAC,0xAD,0x6D,0xAF,0x6F,0x6E,0xAE,0xAA,0x6A,0x6B,0xAB,0x69,0xA9,0xA8,0x68,
    0x78,0xB8,0xB9,0x79,0xBB,0x7B,0x7A,0xBA,0xBE,0x7E,0x7F,0xBF,0x7D,0xBD,0xBC,0x7C,
    0xB4,0x74,0x75,0xB5,0x77,0xB7,0xB6,0x76,0x72,0xB2,0xB3,0x73,0xB1,0x71,0x70,0xB0,
    0x50,0x90,0x91,0x51,0x93,0x53,0x52,0x92,0x96,0x56,0x57,0x97,0x55,0x95,0x94,0x54,
    0x9C,0x5C,0x5D,0x9D,0x5F,0x9F,0x9E,0x5E,0x5A,0x9A,0x9B,0x5B,0x99,0x59,0x58,0x98,
    0x88,0x48,0x49,0x89,0x4B,0x8B,0x8A,0x4A,0x4E,0x8E,0x8F,0x4F,0x8D,0x4D,0x4C,0x8C,
    0x44,0x84,0x85,0x45,0x87,0x47,0x46,0x86,0x82,0x42,0x43,0x83,0x41,0x81,0x80,0x40
)

fun computeCRC(data: ByteArray, length: Int) {
    var a = 0x3C
    var b = 0x7F
    for (pos in 0 until length - 2) {
        val i = (a xor data[pos].toInt()) and 0xFF
        a = (b xor crcTableHi[i]) and 0xFF
        b = crcTableLo[i] and 0xFF
    }
    data[length - 2] = a.toByte()
    data[length - 1] = b.toByte()
}
```

### Triggering Data Output
Send SysCmd 0x06 to start data streaming. Write value 0x8806 to word address 0xA0:
```kotlin
fun buildDataGatherPacket(): ByteArray {
    return buildWritePacket(0xA0, 0x8806)
}
```

### Live Telemetry Decoding
From addr 0xE2, 0xE8, 0xEE (sent repeatedly in status packets, id < 55):

These are real-time values. From the ESP32 project and reverse engineering:
- **Voltage**: word at 0xE2 → voltage = value / 10.0 (V)  
- **LineCurrent**: word at 0xE3 → current = value.toShort() / 10.0 (A, signed, negative = regen)
- **RPM**: word at 0xE5 → rpm = value (unsigned)
- **Gear**: word at 0xE6 → gear = value.toLowByte() (0=N, 1=L, 2=M, 3=H)
- **ControllerTemp**: word at 0xE8 → temp = value.toLowByte().toSByte() (°C)
- **MotorTemp**: word at 0xE9 → temp = value.toLowByte().toSByte() (°C)
- **SOC**: word at 0xEA → soc = value.toLowByte() (0-100%)
- **Speed**: calculated: (RPM / polePairs) * wheelCircumference * 60 / 1000 → km/h → * 0.621371 for mph

## App Navigation & UI

### Screen Flow
1. **Scan Screen** (start) — BLE scanner, device list, connect button
2. **Main Screen** (after connect) — Bottom nav with 3 tabs:
   - 📊 Dashboard — live telemetry
   - ⚙️ Parameters — menu of 10 sections (mirrors factory app)
   - 🔧 Settings — wheel circumference, pole pairs, speed units

### Parameter Menu Sections (match factory app order exactly)
1. Parameters
2. Ratios in Speed
3. Ratios in Gear
4. Energy Regenerate
5. Functions
6. Display
7. Protect
8. PID Paras
9. Product
10. FixedParas

Each section → shows list of params with current values → tap to edit → dialog with number input → write to controller.

## Param Address Map (from fardriver.hpp)

### PARAMETERS SECTION
| Name | Addr | Scale | Unit | Min | Max | Notes |
|------|------|-------|------|-----|-----|-------|
| PhaseOffset | 0x0C | /10 | ° | -1800 | 1800 | Send(0x0A,0x07) |
| Motor Direction | 0x0B | bit7 | - | 0 | 1 | In addr06 word |
| RatedVoltage | 0x17 | /10 | V | 0 | 1000 | |
| MaxSpeed | 0x15 | 1 | RPM | 0 | 9999 | Send(0x12,0x02) |
| MaxLineCurr | 0x19 | /4 | A | 0 | 999 | Send(0x12,0x1B) |
| ThrottleResponse | 0x1A | bits[3:2] | - | 0 | 2 | 0=Line,1=Sport,2=ECO |
| BoostLineCurr | 0x26 | /4 | A | 0 | 999 | CustomMaxLineCurr |
| PhaseExchange | 0x0B | bit0 hi-byte | - | 0 | 1 | In addr06 hi-byte |
| WeakCharacter | 0x1A | bits[5:4] | - | 0 | 3 | WeakA |
| ThrottleLow | 0x08 | /20 | V | 0 | 255 | Low byte of addr06 word at 0x08 |
| TempSensor | 0x0B | bits[5:3] | - | 0 | 7 | 0=None,1=PTC,2=NTC230K,3=KTY84,4=CACU,5=KTY83,6=NTC10K,7=NTC100K |
| PolePairs | 0x14 | low byte | - | 1 | 50 | Send(0x12,0x01) |
| RatedSpeed | 0x18 | 1 | RPM | 0 | 9999 | Send(0x12,0x05) |
| RatedPower | 0x16 | 1 | W | 0 | 99999 | Send(0x12,0x03) |
| BackSpeed | 0x28 | 1 | RPM | 0 | 9999 | Send(0x11,0x03) |
| MaxPhaseCurr | 0x2D | /4 | A | 0 | 999 | Send(0x12,0x1A) |
| BoostPhaseCurr | 0x27 | /4 | A | 0 | 999 | CustomMaxPhaseCurr |
| ThrottleHigh | 0x08 | hi-byte /20 | V | 0 | 255 | High byte of addr06 word at 0x08 |

### PID PARAS SECTION
| Name | Addr | Scale | Unit | Notes |
|------|------|-------|------|-------|
| AN | 0x9C | bits[3:0] | - | Wave type, 0-16 |
| LM | 0x9C | bits[7:4] hi-byte? | - | Wave interval, 0-31 |
| StartKI | 0x0F | low byte | - | |
| MidKI | 0x0F | hi byte | - | |
| MaxKI | 0x10 | low byte | - | |
| StartKP | 0x10 | hi byte | - | |
| MidKP | 0x11 | low byte | - | |
| MaxKP | 0x11 | hi byte | - | |
| SpeedKI | 0x07 | low byte | - | |
| SpeedKP | 0x07 | hi byte | - | |
| CurveTime | 0x0A | 1 | - | |
| MOE | 0x12 | 1 | - | LD field |

### PROTECT SECTION
| Name | Addr | Scale | Unit | Notes |
|------|------|-------|------|-------|
| HighVolProtect | 0x25 | /10 | V | HighVolProtect |
| HighVolRestore | — | — | V | HighVolProtect + 2V approx |
| LowVolProtect | 0x1F | /10 | V | |
| LowVolRestore | — | — | V | LowVolProtect + 2V |
| MotorTempProtect | — | — | °C | in addr at 0x82 area |
| MosTempProtect | — | — | °C | in addr at 0x7C area |
| ZeroBattCoeff | 0x0D | 1 | - | |
| FullBattCoeff | 0x0E | 1 | - | |
| IntRes | 0x1D | 1 | mΩ | Send(0x0F,0x08) |
| BattRatedCap | 0x1C | 1 | Ah | |

### ENERGY REGENERATE SECTION
| Name | Addr | Scale | Unit | Notes |
|------|------|-------|------|-------|
| StopBackCurr | — | /4 | A | in 0x63 area |
| MaxBackCurr | — | /4 | A | in 0x63 area |
| BattRatedCapacity | 0x1C | 1 | Ah | |
| FreeThrottle | 0x2C | low byte | - | |

### RATIOS IN SPEED SECTION
18 RPM/Percentage curve points — stored in addr 0x30 area, 0x5D, 0x63, 0x69
| Name | Addr | Notes |
|------|------|-------|
| Ratio 1-9 RPM | 0x30-0x38 | RPM set points |
| Ratio 1-9 % | 0x39-0x41 (approx) | Percentage |
| LD | 0x12 | |
| LQ | 0x1B | |
| FAIF | 0x09 | int16 |
| LimitSpeed | — | Controls FW start RPM |

### RATIOS IN GEAR SECTION
| Name | Addr | Notes |
|------|------|-------|
| LowSpeedLineRatio | — | in addr 0x7C area |
| LowSpeedPhaseRatio | — | in addr 0x7C area |
| LowSpeed | 0x29 | |
| MidSpeedLineRatio | — | in addr 0x82 area |
| MidSpeedPhaseRatio | — | in addr 0x82 area |
| MidSpeed | 0x2A | |

### FUNCTIONS SECTION
All the pin assignments and boolean features:
| Name | Addr | Notes |
|------|------|-------|
| BoostPin | — | PIN enum |
| CruisePin | — | PIN enum |
| SideStandPin | — | PIN enum |
| ForwardPin | — | PIN enum |
| BackwardPin | — | PIN enum |
| HighSpeedPin | — | PIN enum |
| LowSpeedPin | — | PIN enum |
| ChargePin | — | PIN enum |
| AntiTheftPin | — | PIN enum |
| SeatPin | — | PIN enum |
| SpeedLimitPin | — | PIN enum |
| SwitchVoltagePin | — | PIN enum |
| PausePin | — | PIN enum |
| RepairPin | — | PIN enum |
| BoostTime | — | ms |
| BoostRelease | — | |
| HighLowSpeed | — | bool |
| EmptyRun | — | bool |
| Gear | 0x1A | GearConfig bits[7:5] |
| Brake | 0x0B | BrakeConfig bits[3:0] |
| Park | 0x0B | ParkConfig bits[6:5] |
| Cruise | 0x21 | CruiseEnable bit |
| Push | 0x21 | PushEnable bit |
| EABS | 0x21 | EABSEnable bit |
| Follow | 0x1A | FollowConfig bits[1:0] |

### PRODUCT SECTION
| Name | Addr | Notes |
|------|------|-------|
| FwReRatio | 0x1E | low byte |
| ReCurrRatio | — | |
| VolSelectRatio | 0x9F | low byte |
| WeakCurrCoeff | — | |
| BCEnable | 0x21 | BCState bit |
| SeatEnable | 0x21 | bit |
| PEnable | 0x21 | P gear bit |
| AutoBackPEnable | 0x21 | bit |
| CruiseEnable | 0x21 | bit |
| EABSEnable | 0x21 | bit |
| PushEnable | 0x21 | bit |
| OverSpeedAlarm | 0x22 | bit (in addr1E area) |
| RememberGear | 0x22 | bit |
| BackEnable | 0x22 | bit |
| RelayDelay1S | 0x22 | bit |
| RelayOut | 0x9C | bit |
| AlarmDelay | 0x13 | |

### DISPLAY SECTION
| Name | Addr | Notes |
|------|------|-------|
| SpeedPulses | 0x1A | SpeedPulse bits[4:0] hi-byte |
| SpeedoMeter | — | |
| CAN | — | |
| WheelRatio | — | |
| GearRatio | — | |
| WheelWidth | — | |
| WheelR | — | |
| Step | — | |
| SpecialFrame | — | |
| SQH | 0xBB | low byte |
| Pulse | 0xBB | hi byte? |

## Note on Unknown Addresses
For params where the exact address isn't confirmed, store them as "address unknown" in the UI — show a placeholder that says "Connect to read" but don't show a write field. This is better than writing to wrong addresses.

## Implementation Approach for Bit-Packed Params
For params that share a word address (byte-packed), the app must:
1. Read the current word value from the rawParams map
2. Mask in the new bits
3. Write the modified word back

Example for ThrottleResponse (bits [3:2] of addr 0x1A low byte):
```kotlin
val current = rawParams[0x1A] ?: 0
val newVal = (current and 0xFF03.inv()) or ((newResponse and 0x3) shl 2)
bleManager.writeParam(0x1A, newVal)
```

## Project File Structure
```
fardriver-app/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/bretthalliday/fdtuner/
│           ├── MainActivity.kt
│           ├── ble/
│           │   ├── FardriverProtocol.kt
│           │   ├── FardriverBleManager.kt
│           │   └── ConnectionState.kt
│           ├── model/
│           │   ├── TelemetryData.kt
│           │   └── ParamDef.kt
│           ├── data/
│           │   └── ParamDefinitions.kt
│           ├── ui/
│           │   ├── scan/
│           │   │   ├── ScanFragment.kt
│           │   │   └── ScanViewModel.kt
│           │   ├── dashboard/
│           │   │   ├── DashboardFragment.kt
│           │   │   └── DashboardViewModel.kt
│           │   └── params/
│           │       ├── ParamsMenuFragment.kt
│           │       ├── ParamsSectionFragment.kt
│           │       ├── ParamsViewModel.kt
│           │       └── ParamEditDialog.kt
│           └── settings/
│               └── SettingsFragment.kt
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── fragment_scan.xml
│           │   ├── item_scan_device.xml
│           │   ├── fragment_dashboard.xml
│           │   ├── fragment_params_menu.xml
│           │   ├── item_params_section.xml
│           │   ├── fragment_params_section.xml
│           │   ├── item_param.xml
│           │   └── fragment_settings.xml
│           ├── navigation/nav_graph.xml
│           ├── menu/bottom_nav_menu.xml
│           └── values/
│               ├── colors.xml
│               ├── strings.xml
│               ├── themes.xml
│               └── dimens.xml
```

## Dependencies (app/build.gradle.kts)
```kotlin
dependencies {
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
}
```

## Key Implementation Notes
1. BLE ops must be serialized with a Mutex — no concurrent GATT calls
2. Request MTU 512 after connect
3. Enable notifications on characteristic after MTU negotiation
4. Send data gather command (0x8806 to 0xA0) after notifications enabled
5. All UI updates via StateFlow on main thread
6. Dark theme throughout — riders use this in bright sun and at night
7. Disconnection should show the scan screen again automatically
8. Show connection status in toolbar at all times
9. Error codes screen (decode error byte from telemetry data) — nice to have
10. Auto-reconnect if connection drops

## Build This
Create every file listed above. Make it real, working, production-quality Kotlin. No stubs. The BLE protocol and CRC are the most critical — get them exactly right. The param definitions can have "addr=UNKNOWN" for ones not yet confirmed — just don't allow writing to those.

After all files are written, run:
find /home/bhalliday/projects/fardriver-app -type f -name "*.kt" -o -name "*.xml" -o -name "*.gradle.kts" -o -name "*.properties" | sort

to verify all files exist, then report done with the file list.
