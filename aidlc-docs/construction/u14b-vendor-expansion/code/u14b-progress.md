# U14b — Progress log

Autonomous run started 2026-06-07.

## Pre-flight (resume verify)
- HEAD: d885ac25ad6d4ef05d5b2eb9862d2c53306ae75c ✅
- origin/v2: dadf6f4d813f6eb3cccdfd8d7e02143429614c7b ✅
- working tree fingerprint matches resume artefact ✅
- phone: not connected (deliberate); no adb during this run

## Reference data
- OpenRFID HEAD SHA at port time: `ddd1609e9abe9cd37c4b8fa1a0e4307b976d5fd4` (used in NOTICE per Q-U14b-3=B)

## Milestone 1 — Refactor seam ✅
- compile: ✅
- tests: ✅ 421/421 (no behaviour change)
- new files: VendorTagProcessor.kt, BambuProcessor.kt, SnapmakerProcessor.kt, VendorTagRegistry.kt, MifareUltralightReader.kt
- modified: TagFormatParser.kt, MifareClassicReader.kt, NfcRepository.kt, Settings.kt, SettingsRepository.kt, FakeSettingsRepository.kt
- notes: added crealitySalt + crealityEncKey to Settings + repo (UI lands M3); MifareUltralightReader stub landed in M1 so dispatcher compiles cleanly (real impl with tests in M4)

## Milestone 2 — QIDI ✅
- compile: ✅
- tests: ✅ 432/432 (Δ +11 — 11 tests in QidiProcessorTest)
- new files: QidiTables.kt, QidiProcessor.kt, QidiProcessorTest.kt
- modified: VendorTagRegistry.kt (+QidiProcessor)

## Milestone 3 — Settings rename + chip row + Creality fields ✅
- compile: ✅
- tests: ✅ 438/438 (Δ +6 — 6 tests in VendorTagChipRowTest)
- assembleDebug: ✅
- new files: VendorTagChipRow.kt, VendorTagChipRowTest.kt
- modified: SettingsVendorSection.kt (header rename, chip row, 3 fields), SettingsViewModel.kt (+ 2 setters), SettingsUiState.kt (+ 2 fields), SettingsScreen.kt (wired)
- notes: Q-U14b-7=B preface line above all 3 fields, no per-field supportingText, no em dashes; Q-U14b-1=A alphabetical chip order; Q-U14b-2=A OpenSpool chip included; Q-U14b-8=B Snapmaker (not "U1") in chip displayName

## Milestone 4 — Anycubic + Elegoo (Ultralight) ✅
- compile: ✅
- tests: ✅ 455/455 (Δ +17 — 4 Ultralight reader, 6 Anycubic, 7 Elegoo)
- assembleDebug: ✅
- new files: AnycubicProcessor.kt, ElegooProcessor.kt, ElegooTables.kt, AnycubicProcessorTest.kt, ElegooProcessorTest.kt, MifareUltralightReaderTest.kt
- modified: VendorTagRegistry.kt (+ 2), NfcRepository.kt (Ultralight classifier branch)

## Milestone 5 — Creality ✅
- compile: ✅
- tests: ✅ 463/463 (Δ +8 — 8 Creality)
- new files: CrealityTables.kt, CrealityProcessor.kt, CrealityProcessorTest.kt
- modified: VendorTagRegistry.kt (+ Creality)
- notes: HKDF-shaped derive comment cites non-RFC nature; AES-256-ECB encrypted tag round-trip tested with real cipher

## Milestone 6 — Adapter parity tests + NOTICE + README ✅
- compile: ✅
- tests: ✅ 468/468 (Δ +5 — 1 Bambu adapter, 2 Snapmaker adapter, 2 NfcRepositoryVendorParseTest cases for new chip families)
- new files: BambuProcessorAdapterTest.kt, SnapmakerProcessorAdapterTest.kt
- modified: NfcRepositoryVendorParseTest.kt (+2 cases), NOTICE (OpenRFID attribution at SHA `ddd1609e9abe9cd37c4b8fa1a0e4307b976d5fd4` per Q-U14b-3=B), README.md (v2.1 What's new + Vendor tag support section bullet)

## Milestone 7 — Full build matrix ✅
- compile: ✅
- tests: ✅ 468/468
- assembleDebug: ✅ 65.26 MB (debug not minified)
- assembleRelease: ✅ 7.05 MB (under target ~7 MB ish)
- bundleRelease: ✅ 7.78 MB AAB (target was ~7.7 MB)
- artefacts:
  - `app/build/outputs/apk/debug/app-debug.apk`
  - `app/build/outputs/apk/release/app-release.apk`
  - `app/build/outputs/bundle/release/app-release.aab`
- notes: no R8 / ProGuard rule changes needed; new vendor processors are pure Kotlin (no reflection-via-annotations) so R8 left them alone as predicted

## Final state for user
- 4 new vendor processors live (QIDI, Anycubic, Elegoo, Creality) + registry refactor + adapter ports for Bambu / Snapmaker
- Settings: header renamed to "Vendor tag support"; chip row showing 7 chips alphabetically; 3 key fields with one preface line
- Q-U14b-8=B: Snapmaker chip displayName is "Snapmaker" (no U1)
- Tests: 468/468 (Δ +47 vs U14's 421)
- NOTICE attributes OpenRFID; README mentions all six vendors
- No commits, no pushes, no versionCode bump, no install — all per resume instructions
- Working tree dirty against `origin/v2`; user owns the close-out commit
