# SpoolPainter v2.0 — Integration Test Instructions

## Purpose

Validate end-to-end interactions between SpoolPainter's units (NFC ↔ Spoolman ↔ ViewModel ↔ UI) plus the external systems they integrate with (Spoolman server, OpenSpool-format NFC tags, Snapmaker U1 firmware via the spoollink agent).

**Scope decision**: SpoolPainter v2.0 has **no automated integration test suite**. Integration validation is **manual, on-device** because:
- NFC requires physical tag taps (Robolectric / Espresso can't simulate the tag-presence intent fully)
- Spoolman is a self-hosted external service per user; CI doesn't have a fixture
- Snapmaker U1 round-trip requires actual printer hardware
- Per Q-T2=B (units-of-work), per-unit install gates are not a hard requirement after U6; the U10 milestone install gate is the integration gate.

Future v2.1 work may add Robolectric coverage for NFC intent dispatch (FR-IT-1) and a containerised Spoolman fixture for CI (FR-IT-2). Out of scope for v2.0.

---

## Manual Integration Test Suite

### Master Checklist
**Source of truth**: `aidlc-docs/operations/manual-nfc-checklist.md` — 50+ scenarios across 10 sections, doubles as the U10 install gate spec.

**Sections**:
1. Read flow (ambient tap, Read FAB, blank vs vendor vs OpenSpool classification)
2. Create-and-pair (form fill, write, UID append)
3. Pair another + move-on-bind (two-tag flow, ambiguity sheet)
4. Side modes (Raw-Write, Vendor UID-only)
5. Pickers + custom entries (Material/Brand/Color)
6. Settings + theming + sort + currency
7. UI polish (logo, IME-aware snackbar, "Other"/"Color Wheel" affordances, status pills)
8. Spoolman gating (URL config, reachability, offline banner)
9. Snapmaker U1 round-trip (write tag → U1 reads via openspool_tag_processor → spoollink resolves UID → Fluidd shows full spool data)
10. Release smoke (signed APK install, NFR-5 logcat zero D/I/W from app code)

---

## Setup

### Prerequisites
- Android device or emulator with NFC (physical device required for sections 1–4, 9)
- USB debugging enabled (Settings → Developer Options → USB debugging)
- `adb` on PATH (Homebrew Android command-line tools, Android Studio platform-tools, etc.)
- Reachable Spoolman server (≥ v0.20 recommended; supports `extra` custom fields)
- (For section 9) Snapmaker U1 printer with `paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware` PR #491 build flashed + spoollink agent enabled via Fluidd `Snapmaker Components > Spoolman Integration` toggle.

### Install
```bash
# Build + install debug
./gradlew :app:installDebug
# OR build + sideload release
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Configure Spoolman URL
Settings → "Spoolman URL" → enter `http://<host>:7912/` (or your configured port) → Save → expect "Connected" status. If "Unreachable", check device + host on same network and `usesCleartextTraffic="true"` in AndroidManifest (already set).

### Test Tags
Have these physical tags ready:
- **Blank NTAG215/216** (factory-fresh, no NDEF) — for write-from-scratch flows
- **OpenSpool NTAG** previously written by SpoolPainter v1 — for read flows
- **Vendor MifareClassic1k** (Bambu / Creality / similar) — for vendor classification
- **Snapmaker MifareClassic1k** with their proprietary spool data — for U1 round-trip baseline (your existing Snapmaker spools)

---

## Key Integration Scenarios

For the full 50-scenario list see `manual-nfc-checklist.md`. Below are the high-leverage scenarios that exercise the most cross-unit integrations.

### Scenario A: Read-and-pair (existing OpenSpool tag)
**Description**: User taps a previously-written tag; SpoolPainter reads the OpenSpool JSON, extracts `spool_id`, looks up the spool in Spoolman, populates the form, surfaces the UID.

**Setup**: Spoolman has a spool whose `extra.card_uids` already includes the test tag's UID (i.e. previously paired).

**Test steps**:
1. Open app on main screen.
2. Tap **Read** FAB.
3. Tap the OpenSpool tag.
4. **Verify**: Form populates with material/brand/color/temps; spool dropdown auto-selects the matching spool; no snackbar (vendor reads no snackbar; OpenSpool with payload prefills silently).

**Expected**: `MainViewModel.applyResult` → `Success.OpenSpool` branch fires; `FormMapping.fromSpoolman` resolves filament + spool from the cached Spoolman lists.

**Cross-unit coverage**: U3 (SpoolmanRepository) + U4 (NfcRepository) + U5 (ReadAndPairUseCase) + U6a (form prefill).

---

### Scenario B: Create-and-pair (blank tag → new spool)
**Description**: User fills in filament details (Material, Brand, Color, temps), taps Save & Write, taps a blank tag. SpoolPainter resolves-or-creates vendor + filament + spool in Spoolman, writes the OpenSpool JSON to the tag, PATCHes `extra.card_uids` with the captured UID.

**Setup**: Tag is factory-blank or formatted to empty NDEF. Spoolman is reachable.

**Test steps**:
1. Settings → confirm Spoolman URL is configured.
2. Main screen → expand "Filament" section.
3. Fill: Material = PLA, Brand = Polymaker, Color = #FFE2DEDB, Variant = Matte. Hotend 200–230, Bed 60.
4. Tap **Save & Write**.
5. Tap the blank tag.
6. **Verify**: Snackbar "Saved." (or PairAnotherTagSheet if Q-U6b-3=A is hit). Tag is written. Spoolman now has a new spool linked to the new filament under Polymaker; `extra.card_uids` includes the tag's UID; `extra.variant` on the filament is `"Matte"`.

**Cross-unit coverage**: U2 (codecs) + U3 (resolve-or-create) + U4 (write+verify) + U6a (CreateAndPairUseCase) + U8 (custom-entry persistence).

**Cleanup**: Delete the test spool/filament/vendor in Spoolman if you don't want it persisted.

---

### Scenario C: Move-on-bind
**Description**: User saves a spool whose UID is already on a *different* spool in Spoolman. The repair-confirm sheet asks the user to confirm; on Confirm, the UID is removed from the original spool's `extra.card_uids` and added to the new one (atomic from the user's perspective).

**Setup**: Two spools in Spoolman, both with non-overlapping `extra.card_uids`. A test tag whose UID is on Spool #A but the user wants on Spool #B.

**Test steps**:
1. Pick Spool #B in the dropdown.
2. Tap **Save & Write**.
3. Tap the tag (UID currently on #A).
4. **Verify**: RepairConfirmSheet appears, lists Spool #A as the previous owner.
5. Tap "Move it".
6. **Verify**: Spoolman: #A's `extra.card_uids` no longer contains the UID; #B's `extra.card_uids` now contains the UID.

**Cross-unit coverage**: U6b (MoveOnBindUseCase + RepairConfirmSheet).

---

### Scenario D: Snapmaker U1 round-trip
**Description**: SpoolPainter writes an OpenSpool tag → U1 detects on AMS slot → spoollink resolves UID → Fluidd shows full spool metadata.

**Setup**: U1 firmware = paxx12 PR #491 build with `Snapmaker Components > Spoolman Integration` toggled on (Fluidd Settings). Spoolman URL configured in U1. U1 + SpoolPainter point to the same Spoolman host.

**Test steps**:
1. Run Scenario B (create-and-pair) to write a fresh tag.
2. Insert the spool with the tag attached into a U1 AMS slot.
3. Watch U1 firmware log: `tail -f /var/log/u1.log` (or via Moonraker logs in Mainsail/Fluidd).
4. **Verify in firmware log**:
   - `Detected tag type MifareUltralight with UID <hex>`
   - `Successfully read tag with UID <hex> on reader slot_N_reader`
   - `POST /printer/filament_detect/set HTTP/1.1 200`
5. **Verify in spoollink log** (or via the urllib3 connection log if spoollink runs in the same process):
   - `Starting new HTTP connection: <spoolman host>`
   - `GET /api/v1/spool?limit=1000&allow_archived=true HTTP/1.1 200`
6. **Verify in Fluidd Spool Manager UI**: AMS slot N shows the full spool name, brand, variant, weight, full temps from Spoolman (not just the bare openspool_tag_processor data).

**Known printer-side gotchas** (see `aidlc-docs/ui-followups.md` UI-33):
- (a) Tags wiped with NFC Tools may carry malformed `D8 00 00 00` empty NDEF that blocks U1 detection — workaround: Save & Write a full payload to overwrite.
- (b) `Snapmaker Components > Spoolman Integration` toggle must be ON or spoollink doesn't fire.

**Cross-unit coverage**: U2 (OpenSpool codec, ColorHexCodec, ExtraCardUidsCodec) + U3 (extra.card_uids PATCH) + U4 (NDEF MIME format) + U6a (write path).

---

### Scenario E: Settings + theming + sort
**Description**: User configures URL, switches theme, changes sort order; persists across app restart.

**Test steps**:
1. Settings → set Spoolman URL → Save → verify "Connected".
2. Top-app-bar theme cycle icon → tap repeatedly → verify cycle: System → Light → Dark → System.
3. Settings → Currency → € (Euro) → back to main → expand any spool with a price → verify € prefix.
4. Settings → Spool sort = LastUsed Desc → back → verify dropdown order.
5. Force-stop app, relaunch.
6. **Verify**: All preferences persisted (URL, theme, currency, sort).

**Cross-unit coverage**: U9 (Settings DataStore + ThemeCycleIconButton + sort comparators).

---

## Run-the-Suite Workflow

### 1. Smoke (5 min)
Scenarios A + B only. Catches catastrophic regressions.

### 2. Standard (30 min)
A + B + C + E. Skips U1 round-trip if printer not handy.

### 3. Full install gate (60–90 min)
The complete 50-scenario `manual-nfc-checklist.md`, including release smoke (Section 10) and Snapmaker U1 round-trip (Section 9).

---

## Logs & Diagnosis

```bash
# Watch app-side logs during a test
adb logcat | grep -E "spoolpainter|nfc|Spoolman"

# Watch only fatal crashes
adb logcat *:E | grep -i "fatal\|exception"

# Capture full log for triage
adb logcat -d > /tmp/spoolpainter-trace.txt

# Verify NFR-5 release log strip (zero D/I/W from app code)
adb logcat -c
# (exercise the app for 1–2 minutes)
adb logcat -d | grep " $(adb shell pidof com.spoolpainter.app) " | grep -E " [DIW] "
# Expect: only system-side classes (InsetsController, ImeTracker, etc.); zero from com.spoolpainter.app.*
```

---

## Cleanup

Test runs may leave:
- Spool / filament / vendor records in Spoolman (delete via Spoolman UI or `curl -X DELETE`)
- Tag content (rewrite with another spool or wipe with NFC Tools — but see UI-33(a))
- App preferences (`adb shell pm clear com.spoolpainter.app` to reset, or uninstall)

For the U1, simply re-flash a different spool's tag to swap slot data; spoollink updates Fluidd within ~1s.
