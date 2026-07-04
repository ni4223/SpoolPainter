# U17 — Camera Color Picker — Code Generation Plan

**Unit**: U17 (Camera Color Picker)
**Source follow-up**: UI-45 (`aidlc-docs/ui-followups.md`)
**Release window**: v2.2 (testing-track patch)
**Per-unit gate**: Functional Design / NFR-Requirements / NFR-Design / Infrastructure-Design **SKIP** (feature unit; design folded into this plan, matching the U14–U16 cadence). Code Generation executes.
**This document is the single source of truth for U17 Code Generation.**

---

## Section 1 — Scope & Design Decisions

Let the user point the phone camera at a physical spool and pull an approximate
color hex from a live preview, dropping it into the existing Color field. This
avoids hand-matching a hex to the real filament color.

### Locked decisions (user, 2026-07-04)
- **D1 — Capture approach = CameraX live preview.** In-app live preview with a
  fixed center reticle; a live hex readout updates as the user aims; tap
  "Use this color" (or the reticle) to lock the sample and return it. Chosen
  over the lighter photo-snap intent for the smoother UX.
- **D2 — Sampling = averaged center patch.** Sample the mean R/G/B of an N×N
  block of pixels at the preview center (default N = 20), not a single pixel —
  more robust to sensor noise and single-pixel outliers.

### Consequences accepted with the user
- **CAMERA permission** added to the manifest — a visible new ask on the Play
  Store listing and a runtime permission prompt. App was NFC + INTERNET only.
- **androidx.camera:\*** dependencies added — APK-size increase over the current
  7.41 MB R8 release. Actual delta measured in Step 8's build matrix; if it is
  unacceptably large, fall back to the photo-snap approach (recorded as the
  documented alternative, not this plan's path).
- **Color accuracy is approximate.** White balance and lighting dominate raw
  camera pixels. The sampled hex is a *starting point the user tweaks*, not a
  spectrophotometer reading. Copy sets this expectation.

### Non-goals (explicitly out of scope for U17)
- No flash/torch control, no exposure lock, no white-balance correction.
- No multi-point sampling / palette extraction.
- No camera for anything except color sampling (still NFC-only for tags).
- No landscape support (app is `screenOrientation="portrait"`).

---

## Section 2 — Stories & Traceability

| ID | Story | Covered by |
|---|---|---|
| S-U17-1 | As a user, from the Color field I can open a camera sampler. | Step 5 (ColorPicker menu item) |
| S-U17-2 | As a user, I see a live preview with a center reticle and a live hex readout. | Step 4 (CameraColorSampler composable) |
| S-U17-3 | As a user, tapping "Use this color" returns the sampled hex into the Color field. | Steps 4 + 5 (result wiring via `onChange`) |
| S-U17-4 | As a user who denies camera permission, I get a clear message and the sampler closes without crashing. | Step 4 (permission-denied branch) |
| S-U17-5 | As a user, the sampled color is understood to be approximate and editable. | Step 5 (copy) + returns into the same field the Color Wheel already drives |

Design surface locked by D1 + D2 above (per-unit FD SKIP).

---

## Section 3 — File Impact

**Build / config (modified):**
1. `gradle/libs.versions.toml` — add `camera` version + `androidx-camera-core`,
   `androidx-camera-camera2`, `androidx-camera-lifecycle`, `androidx-camera-view`
   library aliases.
2. `app/build.gradle.kts` — add the four CameraX `implementation` deps.
3. `app/src/main/AndroidManifest.xml` — add `<uses-permission CAMERA>` and
   `<uses-feature android.hardware.camera.any required="false">` (feature not
   required so NFC-only-camera-less devices can still install; the sampler is an
   optional convenience).
4. `app/proguard-rules.pro` — CameraX ships its own consumer rules, but add a
   defensive `-dontwarn`/keep only if the Step 8 `assembleRelease` surfaces a
   need. (No speculative rules; gated on the build.)

**Application code (new):**
5. `app/src/main/java/com/spoolpainter/app/domain/primitives/ColorSampling.kt` —
   pure sampling math (no Android UI types). `averageHex(pixels: IntArray, ...)`
   / `patchBounds(...)` returning a 6-char uppercase hex. Unit-testable on the
   JVM.
6. `app/src/main/java/com/spoolpainter/app/ui/components/CameraColorSampler.kt` —
   full-screen `Dialog` composable: CameraX `PreviewView` + center reticle
   overlay + live hex readout chip + "Use this color" / "Cancel" buttons +
   runtime-permission handling via `rememberLauncherForActivityResult`.

**Application code (modified):**
7. `app/src/main/java/com/spoolpainter/app/ui/components/ColorPicker.kt` — add a
   "From camera" menu item (Icon `PhotoCamera`, primary tint, sibling to
   "Color Wheel"); wire `showCamera` state; on sampler confirm call `onChange`.

**Tests (new):**
8. `app/src/test/java/com/spoolpainter/app/domain/primitives/ColorSamplingTest.kt`
   — JVM unit tests for the sampling math (uniform block, mixed block averaging,
   clamped patch at edges, hex formatting/padding, grayscale/black/white).

> **Test scope note (no silent gap):** `CameraColorSampler.kt` binds real
> `androidx.camera` + `PreviewView` (Android/hardware types) and is **not**
> JVM-unit-testable without instrumentation. Per the U14–U16 convention, camera
> preview + permission flow are verified in the **on-device install gate**
> (Step 9), not in `testDebugUnitTest`. All *pure* logic (the sampling math) is
> extracted into `ColorSampling.kt` precisely so it can be unit-tested. Expected
> unit-test delta: **+~7** (506 → ~513).

---

## Section 4 — Numbered Steps

- [x] **Step 1 — Dependencies.** CameraX 1.3.4 + 4 library aliases added to
  `libs.versions.toml`; 4 `implementation` lines added to `app/build.gradle.kts`.
  `compileDebugKotlin` ✅ resolves against compileSdk 36 / minSdk 29.
- [x] **Step 2 — Manifest.** Added `CAMERA` uses-permission and
  `camera.any` uses-feature (`required="false"`).
- [x] **Step 3 — Sampling math (business logic).** Created `ColorSampling.kt`:
  `averageHex` over an N×N center patch with edge-clamped bounds, `%06X`
  uppercase hex out. Fully JVM-pure (manual `>> 16 & 0xFF` extraction, no
  Android imports).
- [x] **Step 4 — Sampling math tests.** Created `ColorSamplingTest.kt` (8 cases:
  uniform patch, averaged mixed patch, edge-clamped patch, hex zero-padding,
  pure black, pure white, channel clamp, patchBounds centering). ✅ 8/8.
- [x] **Step 5 — CameraColorSampler composable.** Created `CameraColorSampler.kt`:
  full-screen `Dialog`; `rememberLauncherForActivityResult` for
  `RequestPermission(CAMERA)`; on grant binds CameraX `Preview` + `ImageAnalysis`
  (RGBA_8888, KEEP_ONLY_LATEST) to the lifecycle; center reticle overlay;
  `sampleCenterHex` runs `ColorSampling` on the center patch every ~200 ms
  (Q-U17-3=A); live hex chip + swatch; "Use this color" returns current hex;
  "Cancel" dismisses; permission-denied branch shows message + Close (S-U17-4).
  testTags: `camera-sampler`, `camera-sampler-use`, `camera-sampler-cancel`,
  `camera-sampler-hex`.
- [x] **Step 6 — ColorPicker wiring.** Added "From camera" `DropdownMenuItem`
  (`Icons.Default.PhotoCamera`, primary tint, SemiBold) above the
  `HorizontalDivider`; `showCamera` state; renders `CameraColorSampler` when
  true; on confirm calls `onChange(hex)` + closes.
- [x] **Step 7 — Copy pass.** Helper line "Point at the spool. This is a
  starting point you can tweak." No em dash ([[feedback_no_em_dash]]); no
  `Modifier.offset` ([[feedback_no_offset_modifier]]).
- [x] **Step 8 — Build matrix.** `compileDebugKotlin` ✅ / `testDebugUnitTest`
  ✅ **514/514** / `assembleDebug` ✅ 67.10 MB / `assembleRelease` ✅ **7.53 MB**
  R8 / `bundleRelease` ✅ **8.31 MB** AAB. `versionCode` 109→110, `versionName`
  2.1.5→2.2.0 bumped. **CameraX size impact = +0.12 MB only** (7.41→7.53 MB
  release; R8 strips the unused CameraX surface) — no proguard keep-rule needed,
  fallback path NOT triggered.
- [x] **Step 9 — Install gate (on-device, moto g stylus 2025 / Android 16).**
  PASSED. All scenarios verified: permission prompt → grant → live preview +
  reticle + hex; "Use this color" → hex in Color field, swatch matches; deny →
  clean message, no crash; Cancel → unchanged; Color Wheel / named colors /
  No Color regression clean; NFC read/write unaffected. Iteration covered
  sampler inset clipping (fixed via activity-view insets), button label
  ("Scan color"), in-view copy, and dropdown open-direction ordering. New
  follow-up **UI-46** (long-list dropdown action placement) logged, not blocking.
- [x] **Step 10 — Docs + summary.** `u17-summary.md` written; `ui-followups.md`
  UI-45 → fixed + UI-46 logged; README bumped to v2.2 + "What's new in v2.2"
  section + `10-camera-color.png` added to the grid; What's New showcase row
  added; `aidlc-state.md` updated. Close-out commit per
  [[feedback_aidlc_unit_close_out_commit]] (push/tag/release/Play Store await
  explicit user go).

---

## Section 5 — Open Questions for Part 2 (Q-U17-\*)

Answer at Part 2 kickoff (defaults chosen so "go" = recommended path):

- **Q-U17-1 — CameraX artifacts.** Bind via `ImageAnalysis` (analyze frames off
  the preview stream; no capture) [**A, recommended**] vs `PreviewView` +
  one-shot `ImageCapture` on tap [B]. A gives the live readout D1 wants.
- **Q-U17-2 — Reticle sample size N.** 20×20 px [**A, recommended**] vs 40×40 [B]
  vs user-adjustable [C, out of scope].
- **Q-U17-3 — Readout throttle.** Sample ~every 200 ms / ~5 fps [**A**] vs every
  analyzed frame [B, more churn].
- **Q-U17-4 — Permission-denied UX.** Inline message + auto-dismiss after a beat
  [**A**] vs a "Open settings" deep-link button [B].
- **Q-U17-5 — Menu label.** "From camera" [**A**] vs "Camera" [B] vs
  "Sample from photo" [C].

---

## Section 6 — Resume / Rollback

- Resume: re-read this plan, find the first `[ ]`, continue.
- Rollback (if CameraX APK-size delta is rejected at Step 8): revert Steps 1–2 +
  6, keep `ColorSampling.kt` + tests, and re-implement Step 5 as the photo-snap
  `ACTION_IMAGE_CAPTURE` intent (no CAMERA permission, near-zero size). The
  sampling math and the ColorPicker seam are unchanged, so the pivot is local.

---

## Section 7 — Test Target

| Baseline (U16) | New (U17) | Target |
|---|---|---|
| 506 | +~7 (ColorSamplingTest) | **~513 / ~513** |

Camera preview + permission flow verified in the Step 9 install gate (not JVM
unit tests) — see the Section 3 test-scope note.
