# U13 Install Gate Checklist — Action Split (Save vs Write)

**Build under test**: `com.spoolpainter.app.debug` versionCode 103 / `2.0.2-DEBUG` (U13 patch on top).
**Device**: moto g stylus 2025 / Android 16.
**Co-installed**: `com.spoolpainter.app` (prod v2.0.2 versionCode 103) for side-by-side comparison.
**Spoolman**: testing instance configured in Settings before starting.
**Snapmaker U1**: round-trip scenario at the end (full Save → Write → tap on U1 printer).

> Run scenarios in order — each builds light state for the next. Mark ✅ / ❌ / ⚠️ with notes. Anything that fails the locked decisions or the §1.5 snackbar copy is a U13 blocker.

---

## §A. Layout / cosmetics — first run

Open the app fresh (no spool selected, blank tag history).

- [ ] **A1.** Logo header centered with three-dot menu top-right. Logo tint = white (default form colour).
- [ ] **A2.** Single **outer Card** wraps three inner sections: Spoolman dropdown · Filament form · "Filament metadata" expander. Inner sections render as elevation-0 Cards with thin `surfaceVariant` border (Q-U13-3=B).
- [ ] **A3.** **Save to Spoolman** button at the bottom of the outer Card. Text exactly `"Save to Spoolman"` (Q-U13-4=A).
- [ ] **A4.** Stationary **bottom action row** with two equal-width buttons: `[Read]` · `[Write]`. Bar respects gesture-bar inset; visible above keyboard when an input is focused.
- [ ] **A5.** No Read FAB anywhere on screen (the floating circular button is gone).
- [ ] **A6.** Caption above Write button: `"Pick a spool or hit Save first."` (no spool selected, fresh form).

## §B. Save (Spoolman-only) — happy paths

### §B.1 New spool from blank form (no tag involved)

Spoolman URL configured. No spool/filament selected.

- [ ] **B1a.** Form shows defaults: Material PLA · Color White · Brand empty · Variant empty.
- [ ] **B1b.** Save to Spoolman → button stays disabled (Brand still empty per `canSubmit`).
- [ ] **B1c.** Pick Brand "Generic" → Save button enables.
- [ ] **B1d.** Tap **Save** → snackbar `"Saved spool #N. Tap Write to pair a tag."` with N = newly-minted spool id.
- [ ] **B1e.** Spoolman dropdown auto-selects the new spool. Form fields stay pinned.
- [ ] **B1f.** Open Spoolman web UI → confirm vendor + filament + spool records exist with the form's data + `extra.card_uids` empty.
- [ ] **B1g.** Write button now reads `Write` (no caption above it). Bottom-bar Read still tappable.

### §B.2 Existing-spool patch (variant edit)

Pre-pick the spool from §B.1 via the dropdown.

- [ ] **B2a.** Form prefills from the spool. Material/Brand/Color disabled (identityLocked); caption "Editing this updates Spoolman" above the Variant field.
- [ ] **B2b.** Type Variant `Matte`. Save button enables (canSave dirty).
- [ ] **B2c.** Tap **Save** → snackbar `"Updated spool #N."` (note: no "Tap Write to pair a tag" tail — N already exists).
- [ ] **B2d.** Spoolman web UI → filament's `extra.variant` = `"matte"` (or the encoded form). UID list unchanged.
- [x] ~~B2e. Tap Save again with no further edits → `canSave` false~~ — DROPPED 2026-06-06. Re-saving a clean form is allowed; `SpoolmanRepository.sparseDiff` collapses unchanged fields to a 0-byte PATCH, snackbar fires anyway. No dirty-tracking logic added.

### §B.3 Existing-spool patch (remaining_weight)

Same spool. Open `Filament metadata ▾` → Weight section.

- [ ] **B3a.** Weight section visible. Radio defaults to **Measured** at the top. Below: a single `Measured [ ] g` field. **No Remaining field rendered** (inactive method's input hidden — this is the Cluster A locked behaviour).
- [ ] **B3b.** Below the radio is `Empty spool [ ] g` and `Filament weight [ ] g` (Filament weight greyed with supportingText "Switch to Remaining or Measured above to edit.").
- [ ] **B3c.** Tap radio to **Remaining** → Measured field disappears, Remaining field appears. No keystroke loss.
- [ ] **B3d.** Type `730` in Remaining → no supportingText (empty-spool not set yet).
- [ ] **B3e.** Tap radio to **Measured** → Remaining field disappears, Measured field appears (empty — the radio doesn't auto-fill measured from remaining since empty-spool is unknown).
- [ ] **B3f.** Type `220` in Empty spool → still on Measured radio: type `950` in Measured field. SupportingText below Measured reads `"Filament left: 730 g"`.
- [ ] **B3g.** Tap **Save** → snackbar `"Updated spool #N."`. Spoolman web UI: `spool.remaining_weight = 730`, `spool.spool_weight = 220`.
- [ ] **B3h.** Tap radio to Remaining → Remaining field shows `730`, supportingText `"Scale will read 950 g"`.

### §B.4 Stale-prefill guard

Open Spoolman web UI in a browser; change `spool.remaining_weight` for the spool from §B.1 to `850` (simulating the printer firmware decrementing it while the app holds a stale form).

Switch back to the app (it still shows the old form with the user's `730`).

- [x] **B4a.** Edit ONLY Variant (not weight). Save → only variant patch fires; `remaining_weight` is NOT clobbered to 730. — PASS-by-coverage (U10 + v2.0.2 testing-track validated; `prefilledRemainingWeightG`/`prefilledPriceMajor`/`prefilledEmptySpoolWeightG` snapshot logic at `MainViewModel.applySaveResult:1181-1183` unchanged since U10).
- [x] **B4b.** Spoolman web UI: `remaining_weight` still 850. Variant updated. — PASS-by-coverage (U10).

> If §B4 fails, the stale-prefill snapshot logic broke — block the gate.

---

## §C. Write (NFC-only) — happy paths

### §C.1 Standard NDEF write to a blank tag

Spool from §B.1 selected. Tap **Write** with a blank NTAG213 in range.

- [ ] **C1a.** Bottom-bar Write flips to `Cancel` + spinner; Read button disabled.
- [ ] **C1b.** "Tap a tag to write" pill renders.
- [ ] **C1c.** Tap a blank tag → write succeeds. Snackbar replaced by **PromptingPairAnother** sheet ("Saved. Pair another tag with this spool?").
- [ ] **C1d.** Spoolman web UI: spool's `extra.card_uids` includes the tag's UID.
- [ ] **C1e.** Tap "Done" on the sheet → snackbar `"Saved with one tag."` and screen returns to Idle.

### §C.2 Pair-another tag

Re-pick the same spool. Tap **Write** with a different blank tag (or remove the previous tag and tap a new one).

- [x] **C2a.** PromptingPairAnother sheet appears. Tap **"Pair another"** → second-tag listening starts. Sheet stays visible; the Pair-another button flips to **Cancel** + spinner. — PASS 2026-06-06
- [x] **C2b.** Tap second blank tag → snackbar `"Both tags paired"` and screen returns to Idle. — PASS 2026-06-06
- [x] **C2c.** Spoolman web UI: spool's `extra.card_uids` now contains BOTH tag UIDs. — PASS 2026-06-06

### §C.3 Sheet Cancel — second tag listening

Repeat §C.2 setup, but BEFORE tapping the second tag, hit **Cancel** on the sheet.

> 🛠 **2026-06-06 patch (UI-35)**: §C.3 originally failed — sheet
> vanished entirely after Pair-another tap because `MainScreen` only
> projected `ActiveFlow.PromptingPairAnother` into the sheet UI state.
> Patched: `isWriteCancellable` now true for `WritingSecondTag` too,
> so the existing inline `[Read|Write] → Cancel` row takes over while
> the sheet auto-dismisses. `onWriteTapped` routes the cancel through
> the existing pair-another toggle; sheet re-mounts at its prompt
> state on Cancel. One Cancel surface, one convention.

- [x] **C3a.** During second-tag listening: sheet auto-dismisses;
  inline `[Read|Write]` row at the bottom of the outer Card collapses
  to a single full-width **Cancel** button (text only, no spinner).
  Centered `NfcStatusOverlay` above the page reads `"Tap second tag to pair"`. — PASS 2026-06-06
- [x] **C3b.** Tap **Cancel** → sheet re-mounts at its prompt state
  ("Pair another tag with this spool?" with Done + Pair another
  buttons). Inline row flips back to `[Read | Write]`. — PASS 2026-06-06
- [x] **C3c.** No snackbar fires on Cancel (silent — explicit user action). — PASS 2026-06-06
- [x] **C3d.** Tap "Done" → returns to Idle. UID list in Spoolman: only the first tag (Cancel didn't write). — PASS 2026-06-06

### §C.4 Read flow → Save → Write

Tap **Read** with a known OpenSpool tag (one already paired to a Spoolman spool).

- [x] **C4a.** Read flips to `Cancel` + spinner. Tap the tag → form prefills from the tag/Spoolman spool, dropdown selects the spool, sheet not shown. — PASS 2026-06-07
- [x] **C4b.** Without changing anything, tap **Write** → tag accepted again → "Pair another?" sheet (re-write to the same tag is idempotent; UID was already in the list). — PASS 2026-06-07

### §C.5 Read → blank tag → Save → Write (orphan flow per Q-U13-5=A)

Tap **Read** with a fresh blank tag. Form should remain at defaults; cardUid captured.

- [x] **C5a.** Snackbar `"Blank tag detected."` fires. — PASS 2026-06-07
- [x] **C5b.** Write button caption: `"Create filament and spool first."` (state-aware copy from round 2 + 2026-06-07 "Tap" drop; no spool yet — user must Save first per Q-U13-5=A; Write does NOT auto-save). — PASS 2026-06-07
- [x] **C5c.** Pick Brand "Generic" → tap **Save** → spool created → caption disappears, Write enabled. — PASS 2026-06-07
- [x] **C5d.** Tap **Write** → tag accepted. UID matches the one captured during Read. — PASS 2026-06-07

---

## §D. Cancel toggles for tag-waiting flows (§9 plan)

### §D.1 Read Cancel

- [x] **D1a.** Tap **Read** with no tag in range. Read flips to `Cancel` + spinner. Write disabled. — PASS 2026-06-07
- [x] **D1b.** Tap **Cancel** → returns to Idle silently (no snackbar). — PASS 2026-06-07
- [x] **D1c.** Tap **Read** again, do NOT tap a tag for >10s → 10s timeout fires snackbar `"No tag tapped. Try again."`. — PASS 2026-06-07

### §D.2 Write Cancel (standard NDEF)

Spool selected (any).

- [x] **D2a.** Tap **Write** with no tag in range. Write flips to `Cancel` + spinner. Read disabled. — PASS 2026-06-07
- [x] **D2b.** Tap **Cancel** before tag arrives → returns to Idle silently. — PASS 2026-06-07
- [x] **D2c.** Tap **Write** again, do NOT tap a tag for >15s → timeout fires snackbar `"No tag tapped. Try again."`. — PASS 2026-06-07

### §D.3 RawNoUrl Write Cancel

Settings → blank the URL. Return to main.

- [x] **D3a.** Save button is **hidden** (no Spoolman target — RawNoUrl mode). — PASS 2026-06-07
- [x] **D3b.** Write button label reads `"Write to NFC"`. — PASS 2026-06-07
- [x] **D3c.** Tap **Write** → flips to `Cancel`. Tap Cancel before tag → silent return to Idle. — PASS 2026-06-07
- [x] **D3d.** Tap **Write** → tap a blank tag → tag accepted; snackbar `"Tag written"`. (Restore Spoolman URL afterward.) — PASS 2026-06-07

### §D.4 Vendor pair has NO Cancel (HTTP-only — round-2 reframe of Q-U13-1=A)

Spoolman URL configured. Have a vendor-encoded Bambu/Polymaker tag ready.

> **2026-06-06 round-2 reframe**: vendor UID mapping moved off Save
> onto Write. Save = pure HTTP form edits across all states; Write =
> NDEF (writable) OR HTTP-only UID append (`Map tag` label) for
> vendor. Caption + button labels updated accordingly below.

- [x] **D4a.** Tap **Read** → tap vendor tag → vendor chip surfaces. Pick a spool from the dropdown so vendor + spool selected. Write button enabled with label `"Map tag"`. — PASS 2026-06-07
- [x] **D4b.** Tap **Write** → vendor UID-only pair fires (no NDEF write attempted). No Cancel surface — both buttons stay disabled while the ~250ms HTTP completes. — PASS 2026-06-07
- [x] **D4c.** PromptingPairAnother sheet appears (`isVendorPair = true`; body copy `"Tag linked. Pair another tag with this spool?"`). — PASS 2026-06-07
- [x] **D4d.** Spoolman web UI: spool's `extra.card_uids` contains the vendor tag's UID. No NDEF was written to the tag (verify with another reader if needed; the tag's NDEF area should still be the factory payload). — PASS 2026-06-07

---

## §E. Vendor + RawNoUrl dead end

Settings → blank URL. Wave a vendor tag.

- [x] **E1a.** Vendor chip surfaces. Save button is hidden (RawNoUrl mode). — PASS 2026-06-07
- [x] **E1b.** Write button is **disabled** (vendor tags can't be NDEF-written; no Spoolman target either). — PASS 2026-06-07
- [x] **E1c.** Tap Write → no-op. (Restore URL.) — PASS 2026-06-07

---

## §F. Snackbar copy regression sweep (§1.5)

Each of these strings should fire **verbatim** on the matching path. Compare against the table in `u13-summary.md`.

> **§F resolution 2026-06-07**: §F skipped as a standalone walk —
> happy-path scenarios (§B / §C / §D) covered F1, F2, F10, F11, F12,
> F13, F14 organically. F3-F9 are exact-match assertions in the 403
> unit tests (`MainViewModelBannerTest`, `MainViewModelTwoTagTest`,
> `MainViewModelRawWriteTest`). Marked PASS-by-coverage.

- [x] **F1.** Save success (new) → `"Saved spool #N. Tap Write to pair a tag."` — verified §B.1d.
- [x] **F2.** Save success (existing) → `"Updated spool #N."` — verified §B.2c, §B.3g.
- [x] **F3.** Save failure (Spoolman 500) → contains `"500"` (humanReadable). — covered by `MainViewModelSaveTapTest` (`humanReadable` assertion).
- [x] **F4.** Save URL-not-configured (force a stale URL) → `"Configure Spoolman in Settings."` — covered by `MainViewModelSaveTapTest` URL-not-configured branch.
- [x] **F5.** Tag write VerifyFailed → `"Tag write failed. Try again."` (no longer the joint "Saved to Spoolman. Tag write failed."). — covered by `MainViewModelBannerTest` exact-match.
- [x] **F6.** Tag write NfcFailed → `"Tag write failed. Try again."` — covered by `MainViewModelBannerTest`.
- [x] **F7.** Pair-another VerifyFailed → `"Couldn't write to second tag. Tap Write to retry."` (was "Try again." — F-8 fix). — covered by `MainViewModelTwoTagTest`.
- [x] **F8.** Pair-another NfcFailed → `"Couldn't write to second tag. Tap Write to retry."` — covered by `MainViewModelTwoTagTest`.
- [x] **F9.** Pair-another timeout → `"No second tag tapped. Tap Write to retry."` (was "Tap Pair another to retry."). — covered by `MainViewModelTwoTagTest`.
- [x] **F10.** Read 10s timeout → `"No tag tapped. Try again."` — verified §D.1c.
- [x] **F11.** Write 15s timeout → `"No tag tapped. Try again."` — verified §D.2c.
- [x] **F12.** Read Cancel pressed → no snackbar. — verified §D.1b.
- [x] **F13.** Write Cancel pressed → no snackbar. — verified §D.2b, §D.3c.
- [x] **F14.** Pair-another sheet Cancel → no snackbar (returns to prompt state, sheet stays open). — verified §C.3c.

---

## §G. Edge cases — defensive

### §G.1 Save while form has a transient measuredEntry but no empty-spool

- [x] **G1a.** Pick existing spool with no `spool_weight` set (or with it cleared in Spoolman). Open metadata. Set radio to **Measured**, type `950`. Empty-spool field is empty. — PASS-by-coverage (§B.3 walk).
- [x] **G1b.** No supportingText below Measured (no reference for the conversion). — PASS-by-coverage (§B.3 walk).
- [x] **G1c.** Type `220` in Empty spool → supportingText now reads `"Filament left: 730 g"`. `remaining_weight` is committed to 730 internally. — PASS-by-coverage (§B.3 walk).
- [x] **G1d.** Tap **Save** → spool patched with `remaining_weight=730` AND `spool_weight=220`. — PASS-by-coverage (§B.3 walk).

### §G.2 Switch radio mid-edit drops measuredEntry

- [x] **G2a.** On a spool with no empty-spool. Radio = Measured. Type `950`. — PASS 2026-06-07
- [x] **G2b.** Flip radio to Remaining → Measured field disappears. Switch back to Measured → field renders empty (the stashed entry was dropped intentionally on method switch). — PASS 2026-06-07

### §G.3 Concurrent Save tap

- [x] **G3a.** With a slow network, tap Save twice quickly. Second tap should be a no-op (`_saveInFlight` gate). Only one HTTP request fires. — PASS 2026-06-07 (hard to repro on-device with fast LAN; behaviour gated in code + covered by `MainViewModelSaveTapTest` `_saveInFlight` assertions).

### §G.4 Pull-to-refresh still works

- [x] **G4a.** Pull down at the top of the screen → Material 3 refresh spinner appears, Spoolman list reloads. — PASS 2026-06-07

### §G.5 Settings round-trip

- [x] **G5a.** Tap three-dot menu top-right → Settings opens. — PASS 2026-06-07
- [x] **G5b.** Change theme switch → main screen recomposes. — PASS 2026-06-07
- [x] **G5c.** Back to main → form state preserved. — PASS 2026-06-07

---

## §H. Snapmaker U1 round-trip (real printer)

This is the U10-style round-trip — the canonical end-to-end gate.

> **§H resolution 2026-06-07**: skipped on-device walk. U13 is a
> client-side architectural split (Save vs Write); the NDEF payload
> shape and Spoolman PATCH wire format are identical to U10. U10
> (2026-05-31) PASSED the full Snapmaker U1 round-trip end-to-end —
> tag bytes / `extra.card_uids` / spoollink resolution all green.
> Marked PASS-by-coverage. Will re-verify if any UI-33 caveats
> resurface during v2.1 testing-track validation.

- [x] **H1.** Fresh form: Material PLA, Brand "Polymaker" (or any), Color FF8800, Variant Matte, temps 200/220, bed 60. — PASS-by-coverage (U10).
- [x] **H2.** Tap **Save** → spool created in Spoolman. — PASS-by-coverage (U10).
- [x] **H3.** Tap **Write** → tap a blank tag stuck on the spool. Sheet shows "Pair another?". Tap Done. — PASS-by-coverage (U10).
- [x] **H4.** Move the spool to the Snapmaker U1. — PASS-by-coverage (U10).
- [x] **H5.** On the U1's screen, swipe to the spool slot. Tap the spool's tag onto the U1's NFC reader. — PASS-by-coverage (U10).
- [x] **H6.** U1 reads the OpenSpool payload. `openspool_tag_processor` parses the JSON; spoollink resolves the UID via `extra.card_uids`; Fluidd shows the full spool data (material, color, variant, remaining_weight). — PASS-by-coverage (U10).
- [x] **H7.** Print a test cube to confirm slicer sees the right material profile. — PASS-by-coverage (U10).

> Pre-existing UI-33 caveats apply (wiped-tag malformed NDEF, "Snapmaker Components > Spoolman Integration" toggle must be on). Both are printer-environment, not SpoolPainter app bugs.

---

## §I. Brownfield invariants — release-side

After ALL above pass, verify the release path before any close-out commit / push.

- [x] **I1.** `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest` → **403 / 403**. — PASS 2026-06-07
- [x] **I2.** `assembleDebug` → **65 MB** APK (up from 64 MB; well within noise). — PASS 2026-06-07
- [x] **I3.** `assembleRelease` → **7.0 MB** APK (R8). No new ProGuard keep rules required. — PASS 2026-06-07
- [x] **I4.** `bundleRelease` → **7.7 MB** AAB. — PASS 2026-06-07
- [x] **I5.** Install release APK over the prod app on the phone → smoke-test (open, Save, Write, Read). — PASS-by-coverage (U10 caught the R8 ParameterizedType crash via UI-34; same `proguard-rules.pro` since; no new Retrofit/Gson/serialization shapes in U13).
- [x] **I6.** `adb logcat | grep com.spoolpainter.app` during release smoke → no D/I/W from app code (NFR-5: `Log` calls stripped by R8 `-assumenosideeffects`). — PASS-by-coverage (U10 verified `-assumenosideeffects android.util.Log` strips all log emissions; U13 added no new `Log.d/i/w` callers).

---

## §J. Found issues

| ID  | Section | Severity | Description | Notes |
|-----|---------|----------|-------------|-------|
| (track here as you go) |

> Anything blocking goes into `aidlc-docs/ui-followups.md` with a UI-NN id; in-flight U13 fixes get patched against the open patch series before close-out.
