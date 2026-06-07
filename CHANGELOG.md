# FD Tuner — Changelog

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
