# U17 — Camera Color Picker — Code Generation Summary

**Unit**: U17 (Camera Color Picker) · **Release**: v2.2.0 (versionCode 110)
**Source**: UI-45 · **Status**: Code Gen DONE + install gate PASSED 2026-07-04
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D SKIP (feature unit; design folded into the Code Gen plan).

## What shipped
A camera color sampler reachable from the Color picker. The user taps **Scan
color** (an action sharing the "Color Wheel" row in the dropdown), points the
phone at a spool, and the app samples the color under a fixed center reticle
into the Color field.

Design decisions (user, 2026-07-04):
- **CameraX live preview** (not the lighter photo-snap intent) — live preview +
  center reticle + live `#HEX` chip + swatch; "Use this color" locks the sample.
- **Averaged center patch** sampling (mean RGB of an N×N block, default 20).

Q-U17-* resolved to recommended defaults: ImageAnalysis frame sampling (RGBA_8888,
KEEP_ONLY_LATEST), 20×20 patch, ~5 fps (200 ms) throttle, permission-denied →
inline message + Close.

## Files
**New (app):**
- `domain/primitives/ColorSampling.kt` — pure, JVM-testable sampling math
  (`averageHex`, `patchBounds`, `toHex`); manual bit extraction, no Android
  imports.
- `ui/components/CameraColorSampler.kt` — full-screen Dialog: runtime CAMERA
  permission via `rememberLauncherForActivityResult`; CameraX Preview +
  ImageAnalysis bound to the lifecycle; center reticle; live hex chip;
  `sampleCenterHex` reads the center patch off each RGBA frame and averages via
  `ColorSampling`; "Use this color" / "Cancel"; permission-denied branch.
- `ui/components/DropdownDirection.kt` — shared `rememberDropdownDirection()`
  helper (see below).

**New (test):**
- `domain/primitives/ColorSamplingTest.kt` — 8 cases (uniform, mixed average,
  edge-clamp, black, white, zero-pad, channel clamp, patchBounds centering).

**Modified (app):**
- `ui/components/ColorPicker.kt` — "Scan color" action on the Color Wheel row;
  `showCamera` state; renders `CameraColorSampler`; direction-aware row order.
- `ui/components/MaterialPicker.kt`, `ui/components/BrandPicker.kt` — use
  `rememberDropdownDirection()` so the "Other" action sits nearest the field.
- `ui/components/sheets/WhatsNewContent.kt` — new "Scan a color with the camera"
  showcase row (in-place updaters to v2.2 see it).

**Modified (build/config):**
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — CameraX 1.3.4 (core,
  camera2, lifecycle, view); versionCode 109→110, versionName 2.1.5→2.2.0.
- `AndroidManifest.xml` — `CAMERA` permission + `camera.any` uses-feature
  (`required="false"`, so camera-less devices still install).

## Install-gate iteration (on moto g stylus 2025 / Android 16)
- **Sampler layout clipping** (buttons under the nav bar): root cause was
  reading window insets from the *dialog's* view (reports zero bottom inset).
  Fixed by capturing system-bar + display-cutout insets from the **activity's**
  view in the outer composable and passing top/bottom dp into the sampler; extra
  `+40dp` bottom clearance above the nav-bar inset.
- **Button label** iterated to icon-only → visible text; final: "Scan color"
  sharing the Color Wheel row (user-authored via ChatGPT prompt).
- **In-view line** → "Center the circle on the filament. Tweak the color if
  needed." (user-authored).
- **Dropdown open direction**: action rows are placed nearest the anchor field
  (top when the menu opens down, bottom when it flips up) via the shared
  `rememberDropdownDirection()`. This fixed the short menus (Color, Material) but
  the long Brand list (30+) still requires scrolling when it flips up — logged
  as **UI-46** (needs a pinned-action menu; deferred, not blocking).

## Reuse note
The open-direction logic was initially copy-pasted into all three pickers, then
refactored into the single `DropdownDirection.kt` helper on user feedback (no
duplication). `ColorSampling` is deliberately extracted from the composable so
the math is unit-tested on the JVM.

## Verification
- `compileDebugKotlin` ✅ / `testDebugUnitTest` ✅ **514/514** (Δ +8 vs U16's 506;
  all 8 in ColorSamplingTest).
- `assembleDebug` ✅ 67.10 MB / `assembleRelease` ✅ **7.53 MB** R8 /
  `bundleRelease` ✅ **8.31 MB** AAB.
- **CameraX APK-size impact = +0.12 MB** (7.41 → 7.53 MB release; R8 strips the
  unused CameraX surface). The size concern that motivated a lighter fallback
  never materialised; fallback NOT triggered. No proguard keep-rule needed.
- Install gate PASSED: permission prompt → grant → live preview + reticle + hex;
  "Use this color" fills the Color field; deny → clean message, no crash;
  Cancel → field unchanged; Color Wheel / named colors / No Color regression
  clean; NFC read/write unaffected.

## Screenshots
- `screenshots/10-camera-color.png` — sampler over a yellow spool (`#D2A220`),
  added to the README grid.

## Memories
[[reference_adb_path]], [[feedback_no_em_dash]], [[feedback_no_offset_modifier]].

## Close-out
Close-out commit on `v2` per [[feedback_aidlc_unit_close_out_commit]].
Push / tag / GitHub Release / Play Store upload await explicit user go.
