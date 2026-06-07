# FD Tuner — Changelog

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
