# FD Tuner — Changelog

## [1.2.0] - 2026-06-08 (Packet Sniffer + Telemetry Fixes)

### New Features

**Feature 1 — Packet Sniffer (Dev-Mode Diagnostics)**
- Read-only raw packet capture tool for decoding unknown words (per_mille, unk3/unk4, pin states, param banks)
- `SniffPacket` data class: timestampMs, id, baseAddr, rawHex (16 bytes), words (Map<wordAddr, uint16>)
- `FardriverBleManager.sniffPackets` SharedFlow (replay=0, 256-buffer) emits after every parseStatusPacket
- `SnifferViewModel`: latestByAddress StateFlow, changedAddresses SharedFlow, session ring buffer (5k cap), per-word session min/max, annotation list (timestamp+tag)
- `SnifferFragment`: RecyclerView of 34 block addresses, rows flash orange on word change, tap for detail, annotation bar with free-text + quick-tag buttons (brake, throttle 25/50/WOT, rolling, stopped)
- `SnifferDetailFragment`: 6-word detail view with session min/max per word, live hex flash, raw packet hex footer
- CSV export merges packet log + annotations (FileProvider via cacheDir, matches ProfilesFragment pattern)
- Demo mode: synthesises SniffPackets from DemoDataSource.rawParams so sniffer works offline
- Settings: Dev Mode SwitchMaterial (persists dev_mode, default false), "Packet Sniffer" button appears when on
- Zero BLE writes anywhere — strictly observational

### Bug Fixes

**Telemetry Address Corrections** (commit 199ba10)
- TelemetryData: added aPhaseCurrent, cPhaseCurrent, errorFlags fields
- FardriverProtocol.decodeTelemetry():
  - voltage: 0xE2 (flags) → 0xE8 (deci_volts)
  - lineCurrent: 0xE3 → 0xEA (int16 ÷4, not ÷10)
  - phase currents: 0xF0/0xF1/0xF2 (24-bit big-endian, formula 1.953125 × √raw24 = amps RMS)
  - speed formula verified correct (mechanicalRpm ÷4 × wheelCirc × 60/1000)
- DashboardFragment: relabeled "CURRENT" → "BATTERY A", added orange Phase A/C current cards
- ParamDefinitions.kt: RatioMin label corrected

**ParamDefinitions Reconciliation Against HPP Field Map** (commit eed01cc)
- Motor Direction: added `isHiByte=true` (was reading wrong bit entirely)
- TempSensor: `isLoByte=true`, bitShift 3→4
- HighVolRestore: addr null→0x83
- MotorTempProtect: addr null→0x84, `isLoByte=true`
- MosTempProtect: addr null→0x85, `isLoByte=true`
- Gear in FUNCTIONS: `isLoByte→isHiByte` (wrong byte), renamed GearConfig
- Cruise/EABS: added `isLoByte=true`, fixed bitShifts (0→4, 2→5)
- CruiseEnable/EABSEnable in PRODUCT: fixed bit positions
- MaxAcc, MaxDec, RateRatio: added to FUNCTIONS and DISPLAY sections

**ParamDef.isWritable Gate on isReadOnly** (commit 199ba10)
- `isWritable` now checks `addr != null && !isReadOnly`
- Params with `isReadOnly=true` are dim/no-tap (same as null-addr params)
- Added three read-only live-readout params: ThrottleVoltage (0x82), AVGPower (0xD1), AVGSpeed (0xD3)

## [1.1.0] - 2026-06-07

### New Features

**Feature 1 — Foreground BLE Service (`BleConnectionService`)**
- Persistent foreground notification showing connection status (Connected / Scanning / Disconnected / Demo)
- Auto-reconnect on unexpected drop: retries every 3s up to 10 attempts using the last-stored device address
- Skips auto-reconnect in demo mode
- Bound to `MainActivity`; saves last connected device address in SharedPreferences on each successful connect
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission added to manifest

**Feature 2 — Session Snapshot / One-Tap Restore**
- `FardriverBleManager.takeSessionSnapshot()` captures all raw params automatically before the very first write of any session
- "↩ Restore Session" in the overflow menu (only visible when a snapshot exists and not in demo mode)
- Confirmation dialog → `restoreSnapshot()` writes all params back → Snackbar confirms success or failure
- Snapshot cleared on `disconnect()` and `stopDemo()`

**Feature 3 — Param Change Log (Audit Trail)**
- `ChangeLogManager`: JSON-backed, newest-first, capped at 200 entries per SharedPreferences store
- Every param write (live and demo) logged with ISO-8601 timestamp, address, name, old raw, new raw, isDemo flag
- `ChangeLogBottomSheet`: date-grouped RecyclerView, demo entries shown in cyan with `[DEMO]` prefix
- "Clear Log" button with confirmation dialog
- History icon in the Parameters menu toolbar opens the bottom sheet

**Feature 4 — Profile Export / Import**
- `ProfileManager.exportToJson()`: serialises a profile to pretty-printed JSON
- Long-press on any profile card shows a popup menu (Export / Delete)
- Export fires `Intent.ACTION_SEND` via `FileProvider` (writes to cache dir first)
- Import FAB (↓) on Profiles screen → `ACTION_GET_CONTENT` for `application/json` → `importFromJson()` → name-collision dialog if needed
- `androidx.core.content.FileProvider` added to manifest with `res/xml/file_provider_paths.xml`

**Feature 5 — Param Search / Filter**
- Search field at the top of the Parameters menu screen
- Typing shows a flat, cross-section filtered list (all ~80+ params) in a second RecyclerView
- Result rows show param name, current value, unit, and section name as subtitle
- Tap opens the usual `ParamEditDialog`
- Back press clears search and returns to the section list

**Feature 6 — Telemetry Alert Thresholds**
- `AlertMonitor`: rising-edge only — fires once per threshold crossing, resets when value recovers
- Motor temp threshold (default 80°C) and battery low voltage threshold (default 60.0V)
- Both thresholds and an enable/disable toggle configurable in Settings
- Alert shown as a 5-second Snackbar on the Dashboard with haptic feedback (LONG_PRESS)
- Monitor resets on disconnect so the next connection starts fresh

### Build / Infrastructure
- `gradlew` + wrapper added to repo for CI and remote builds
- `.gitignore` added; build artifacts removed from tracking
- One Kotlin fix: companion object constants moved out of inner class to outer class

---

## [1.0.0] - 2026-06-07

### Initial Release
Complete Android app for controlling Fardriver e-bike motor controllers via BLE.

#### Features
- **BLE Connection**: Auto-scan and connect to Fardriver controllers (FD-72680, FD-72450, etc.)
- **Live Telemetry Dashboard**: Real-time voltage, current, RPM, speed, temps, SOC, gear, regen status
- **Parameter Control**: 10 menu sections mirroring the factory Fardriver app:
  - Parameters (PhaseOffset, Motor Direction, RatedVoltage, MaxSpeed, etc.)
  - Ratios in Speed (18 RPM/% curve points)
  - Ratios in Gear (low/mid speed ratios)
  - Energy Regenerate (regen current curve)
  - Functions (pin assignments, boost, cruise, etc.)
  - Display (CAN, telemetry, wheel/gear ratios)
  - Protect (voltage, temp, current limits)
  - PID Paras (wave type AN, wave interval LM, KP/KI gains)
  - Product (features, relay, seat, auto-back, EABS, push)
  - FixedParas
- **Safety-Critical Confirmation**: 
  - Two-step write confirmation for all params
  - Extra warning on 8 safety-critical params (MaxLineCurr, MaxPhaseCurr, BoostLineCurr, BoostPhaseCurr, MaxSpeed, RatedVoltage, HighVolProtect, LowVolProtect)
  - Shows Current → New value before write
- **Settings**: Wheel circumference, motor pole pairs, speed units (mph/km/h)
- **Dark Theme**: Material3 dark theme with orange accent (#FF6600)
- **No Login Required**: Direct BLE connection, no frustrating authentication

#### Technical
- **Language**: Kotlin
- **Architecture**: MVVM + StateFlow
- **BLE Protocol**: Full Fardriver binary protocol implementation with CRC tables and proper packet serialization
- **Auto-Telemetry**: Connects → sends data gather command → streams live values (voltage, current, RPM, temps, SOC, etc.)
- **Param Definitions**: 80+ parameters mapped to controller memory addresses with correct scaling, bit-packing, and validation
- **Min SDK**: 26 (Android 8.0) | **Target/Compile**: 34
- **Deps**: Material3, Navigation, Lifecycle, Coroutines, RecyclerView

#### Fixes (Protocol Session — 2026-06-08)
- [0d45602] Fix: protocol RPM scale, addresses, speed calc, unit suffix (7 fixes from reverse engineering)
  - Fix 1: RPM scale=4f on MaxSpeed, RatedSpeed, BackSpeed, LowSpeed, MidSpeed (raw = mechanical_RPM × 4)
  - Fix 2: Speed formula now uses raw/4 instead of raw/polePairs; divides by 4 first, then multiplies by circumference
  - Fix 3: Motor Direction bitShift=7 (already correct, no change needed)
  - Fix 4: formatDisplay() no longer appends unit (tvParamUnit handles it separately)
  - Fix 5: Ratios in Speed completely corrected: 20 percentage params at 0x88–0x91 lo/hi bytes (not 0x30–0x41)
  - Fix 6: Add/move missing params: LD(0x12), LQ(0x1B), FAIF(0x09), LimitSpeed(0x6C, scale=4f)
  - Fix 7: Ratios in Gear addresses: Line/Phase ratios at 0x32–0x33 (formula: pct = raw×100/128)
  - DemoDataSource: all RPM raw values scaled ×4 for protocol consistency
  - Validated against live ND72680_24_A_H9 (MaxSpeed 12000 raw → 3000 RPM ✓)
- [557ad61] Fix: remove BLE scan filters blocking FarDriver discovery
  - Removed service UUID pre-filter (HM-10/BT05 modules don't broadcast service UUID in ads)
  - Replaced hard name-reject with soft pattern matching (Ratio @ …RPM params now map to correct addresses and store percentages only)
  - Added likelyFardriver flag; devices sorted by likelihood then RSSI, with ⚡ badge in scan list
  - Tested with nRF Connect to verify actual advertised name

#### Fixes (Build Session)
- [015eef2] Fix: switch to NoActionBar theme to allow Toolbar setSupportActionBar
- [d99a323] Fix: getDefaultViewModelProviderFactory → property override for lifecycle 2.7+
- [88fc291] Fix: replace MDC1 dialog theme with Material3 equivalent
- [f083a85] Safety: require explicit 2-step write confirmation for all params

#### GitHub
https://github.com/sparkbot207-del/fd-tuner

#### Build Notes
- Compiles cleanly with AGP 8.x, Kotlin 1.9.x
- No external BLE libraries — uses native Android BluetoothLeGatt APIs
- CRC computation uses correct Modbus tables (crcTableHi/crcTableLo, 256 entries each)
- BLE ops serialized with Mutex to prevent concurrent GATT calls
- All UI updates via StateFlow on main thread
- Status packets parsed to extract live telemetry (voltage, current, RPM, gear, temps, SOC, regen)
- Parameters read via device data gather (SysCmd 0x06) and updated in real-time

#### Known Limitations
- Single connection at a time (no multi-bike switching yet)
- Param write requires explicit user confirmation (by design — safety first)
- Some param addresses marked "unknown" if not fully confirmed in reverse engineering
