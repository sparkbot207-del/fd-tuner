# FarDriver Menu Map — official app layout ↔ HPP address

Reconciles the factory FarDriver app menu (authoritative names/sections/order) with HPP word
addresses. **H** = high confidence, **M** = verify, **R** = RESOLVE via Config Diff (change it
in the official app, diff the snapshot, see which address moves).

> Reference only — not compiled. Used to inform friendly names, sections, ordering, and units
> for the curated parameter set (`ParamDefinitions.kt`) and the `OVERRIDES` map. Do NOT change
> any write address from an M/R row without confirming it first (Config Diff / Guided Capture).

## Parameters
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| AngleDetect | ? | — | R | Hall angle detect — HPP field unconfirmed (AngleLearn?) |
| PhaseOffset | PhaseOffset | 0x0C | H | /10° |
| Motor Direction | Direction | 0x0B hi bit5 | H | 0x0B hi bit7 |
| RatedVoltage | RatedVoltage | 0x17 | H | /10 V |
| MaxSpeed | MaxSpeed | 0x15 | H | RPM |
| MaxLineCurr | MaxLineCurr | 0x19 | H | /4 A |
| ThrottleResponse | ThrottleResponse | ThrottleResponse | H | 0/1/2 |
| BoostLineCurr | CustomMaxLineCurr | 0x26 | H | /4 A (HPP CustomMaxLineCurr) |
| PhaseExchange | PhaseExchange | 0x0B lo bit0 | H | bit |
| Weak Character | ? | — | R | field-weakening characteristic — HPP field unconfirmed |
| Throttle Dec Step | Max_Dec | 0x2B | M | verify (default 224) |
| Throttle Low | ThrottleLow | 0x08 lo | H | /20 V |
| TempSensor | TempSensor | TempSensor | H | 0x0B lo bits4-6 |
| PolePairs | PolePairs | 0x14 lo | H | count |
| RatedSpeed | RatedSpeed | 0x18 | H | RPM |
| RatedPower | RatedPower | 0x16 | H | W |
| BackSpeed | BackSpeed | 0x28 | H | RPM |
| MaxPhaseCurr | MaxPhaseCurr | 0x2D | H | /4 A |
| Throttle Acc Step | Max_Acc | 0x2F | M | verify (default 220) |
| BoostPhaseCurr | CustomMaxPhaseCurr | 0x27 | H | /4 A (HPP CustomMaxPhaseCurr) |
| ECOAccCoeff | ? | — | R | eco accel coeff — HPP field unconfirmed |
| WeakResponse | ? | — | R | FW response 0-7 (default 7=off) — HPP field unconfirmed (WeakA is 2-bit, mismatch) |
| Release Throttle | ? | — | R | throttle release behaviour — HPP field unconfirmed |
| Throttle High | ThrottleHigh | 0x08 hi | H | /20 V |

## Ratios in Speed
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| FW curve pts 1-18 (RPM/% pairs) | Ratio500..Ratio9000 | 0x88 hi … | M | 0x88-0x91 (18 vals). NOTE: HPP comment calls these 'energy regen %' — CONFLICT with official app. Confirm FW-vs-regen via Config Diff: change a FW point in official app, see if Ratio500.. or nratio.. moves. |
| LD | LD | 0x12 | H | 0x12 D-axis inductance |
| FAIF | FAIF | 0x09 | H | 0x09 flux |
| LQ | LQ | 0x1B | H | 0x1B Q-axis inductance |
| LimitSpeed | LmtSpeed | 0x69 | H | FW start RPM |

## Ratios in Gear
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| LowSpeedLineRatio | LowSpeedLineCurr | 0x32 lo | H | 0x32 lo |
| LowSpeedPhaseRatio | LowSpeedPhaseCurr | 0x33 lo | H | 0x33 lo |
| LowSpeed | LowSpeed | 0x29 | H | RPM |
| MidSpeedLineRatio | MidSpeedLineCurr | 0x32 hi | H | 0x32 hi |
| MidSpeedPhaseRatio | MidSppedPhaseCurr | 0x33 hi | H | 0x33 hi (HPP typo MidSpped) |
| MiddleSpeed | MidSpeed | 0x2A | H | RPM |

## Energy Regenerate
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| StopBackCurr | StopBackCurr | 0x30 | H | /4 A |
| MaxBackCurr | MaxBackCurr | 0x31 | H | /4 A |
| Batt RatedCapacity | BattRatedCap | 0x1C | H | Ah |
| FreeThrottle | FreeThrottle | 0x2C lo | H | 0x2C lo |
| Regen curve pts 1-18 (RPM/% pairs) | nratio_0..nratio_19 | 0x92 lo … | M | 0x92+ (20 vals). See FW-vs-regen note above — confirm which table is regen via Config Diff. |

## Functions (pins & toggles)
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| Park | park (state) | park (state) | R | live state bit — find via Guided Capture park step |
| Brake | brake | 0xE3 hi bit7 | H | 0xE3 hi bit7 (live state) |
| Gear | gear | 0xE2 lo bit2-3 | H | 0xE2 lo bits2-3 (live state) |
| Cruise Pin | cruise (state) | cruise (state) | R | live state bit — find via Guided Capture cruise step |
| PC13 | PC13Config | 0x0B hi bit3 | M | 0x0B hi |
| SlowDown | SlowDown | 0x0B hi bit0-2 | M | 0x0B hi |
| Follow | FollowConfig | FollowConfig | M | 0x1A lo |
| (other pins: Boost/SideStand/Forward/Highspeed/Charge/Seat/SwitchV/Backward/LowSpeed/Anti-theft/SpeedLimit/Repair/Pause) | ? | — | R | named in HPP Addr1E/0x21 bitfields and 0xC4 flags — map individually via Config Diff |
| BoostTime | BstTime | 0xBF | M | 0xBE |
| BoostRelease | BstRelease | 0xC0 | M | 0xBE |

## Display
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| Speed Pulses | SpeedPulse | 0x1A lo bit2-6 | M | 0x1A hi |
| SpdPulseNum | SpdPulseNum | 0x35 | H | 0x35 |
| SpeedoMeter | SpeedMeterConfig1 | 0x7C hi bit0 | M | 0x7C |
| CAN | CANConfig | 0x86 lo bit0-5 | H | 0x86 lo bits0-5 |
| Step | SpeedMeterConfig2 | 0x7C hi bit1 | M | 0x7C verify |
| SpecialFrame | SpecialCode | 0x6B hi | M | verify |
| WheelRatio | WheelRatio | 0xD2 lo | H | 0xD2 lo |
| WheelWidth | WheelWidth | 0xD3 hi | H | 0xD3 hi |
| WheelR | WheelRadius | 0xD2 hi | H | 0xD2 hi |
| GearRatio | nratio? / RateRatio | nratio? / RateRatio | R | 0xD4 RateRatio candidate — verify |
| TorqueCoeff | TorqueCoeff | 0xC3 | M | 0xBE |
| ByteOption | ByteOption | 0xD5 lo bit0-3 | M | 0xD5 |

## Protect
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| HigiVolProtect | HighVolProtect | 0x25 | H | /10 V |
| LowVolProtect | LowVolProtect | 0x1F | H | /10 V |
| MotorTempProtect | MotorTempProtect | 0x84 lo | H | °C |
| MosTempProtect | MosTempProtect | 0x85 lo | H | °C |
| HigiVolRestore | HighVolRestore | 0x83 | H | /10 V |
| LowVolRestore | (derived) | (derived) | H | = LowVolProtect/10 + 2.0 (no stored addr) |
| MotorTempRestore | MotorTempRestore | 0x84 hi | H | °C |
| MosTempRestore | MosTempRestore | 0x85 hi | H | °C |
| 0 BattCoeff | ZeroBattCoeff | 0x0D | H | 0x0D |
| Full BattCoeff | FullBattCoeff | 0x0E | H | 0x0E |
| IntRes | IntRes | 0x1D | H | 0x1D mΩ |
| BackP_Time | BackPTime | 0xBC lo bit0-4 | M | 0xBC ×10 s |
| ParkTime | ParkTime | 0xC1 | M | 0xBE |
| ReleaseToSeat | ReleaseToSeat | 0xBC lo bit5-7 | M | 0xBC |
| ThrottleLost | ThrottleLost | 0xCB lo bit3 | M | 0xCA |
| LowVol Way | LowVolWay | LowVolWay | M | 0x82-area enum |

## PID Paras
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| AN | AN | 0x9C lo bit0-3 | M | 0x9C (demo had it) |
| LM | LM | 0x9C hi bit0-4 | M | 0x9C |
| StartKI | StartKI | 0x0F lo | H | 0x0F lo |
| MidKI | MidKI | 0x0F hi | H | 0x0F hi |
| MaxKI | MaxKI | 0x10 lo | H | 0x10 lo |
| StartKP | StartKP | 0x10 hi | H | 0x10 hi |
| MidKP | MidKP | 0x11 lo | H | 0x11 lo |
| MaxKP | MaxKP | 0x11 hi | H | 0x11 hi |
| SpeedKI | SpeedKI | 0x07 lo | H | 0x07 lo |
| SpeedKP | SppedKP | 0x07 hi | H | 0x07 hi (HPP typo Spped) |
| CurveTime | CurveTime | 0x0A | H | 0x0A |
| MOE | moe_current_protect? | moe_current_protect? | R | over-excitation — verify |

## Product
| Official name | HPP field | Address | Conf | Note |
|---|---|---|---|---|
| ReCurrRatio | ReCurrRatio | 0xC9 hi | H | 0xC9 hi |
| FwReRatio | FwReRatio | 0x1E lo | H | 0x1E lo |
| WeakCurrCoeff | ? | — | R | EXISTS in official app (Product R#2) — HPP name unknown; resolve via Config Diff |
| CruiseEnable | CruiseEnable | 0x21 lo bit4 | H | 0x21 lo bit4 |
| EABSEnable | EABSEnable | 0x21 lo bit5 | H | 0x21 lo bit5 |
| BackEnable | BackEnable | 0x21 hi bit6 | M | 0x21 verify |
| AlarmDelay | AlarmDelay | 0x13 | H | 0x13 |
| OverSpeedAlarm | OverSpeedAlarm | 0x21 hi bit0 | M | 0x21 |
| RelayDelay | RelayDelay | RelayDelay | M | 0x21 |
| AutoBackPEnable | AutoBackPEnable | 0x21 lo bit3 | M | bit |
| (Re Acc / VolSelectRatio / BCEnable / PEnable / PushEnable / RememberGear / StartIs / Temp70 / LearnThrottle / LearnVolHigh / DeepWeak / SlowDownCoeff / Curr-Anti-theft / InverseTime) | ? | — | R | named in HPP 0x1E/0x21/0xC4/0xCA bitfields — map individually via Config Diff |
