# U10 — Release Polish + Play Store Testing-Track Release Prep — Code Generation Plan (Part 1)

**Unit**: U10 — v2.0 Release Polish + Play Store Testing-Track Release Prep
**Per-unit gate**: FD SKIP / NFR-R SKIP / NFR-D SKIP / Infra-D SKIP / Code Gen EXECUTE
**Predecessor close-out commits**: `4995ca9` (U9b) + `a9d7a3b` (post-U9b Spoolman-section gating)
**Branch**: `v2`, up to date with `origin/v2`

---

## 0. Scope locks (your answers)

- **U10 FD/NFR/Infra**: SKIP (release-polish unit, no new business logic).
- **APK shrink (U10-Δ-1)**: R8 minify on `release` build only + ProGuard keep rules. `material-icons-extended` stays.
- **Doc-drift sync**: fold into U10.
- **Debug UID surface**: drop entirely.
- **U10-Δ-2 (`sortOrder` JSON migration)**: **DROPPED** — no users on any prior v2 build, no preferences to migrate. Closed as not-needed.
- **Editing (UI-13/14/15)**: parked post-v2.0 release.
- **v2.1 (U11/U12)**: hard-gated behind v2.0 testing-track ship.

---

## 1. Build-config bumps

### 1.1 — `app/build.gradle.kts` versionCode + versionName
- [x] `versionCode = 8` → `versionCode = 100` (per memory: v2.0 starts at 100, leaves room for v1.x patches)
- [x] `versionName = "1.7"` → `versionName = "2.0"`
- [x] Debug variant `versionNameSuffix = "-DEBUG"` retained → debug builds report `2.0-DEBUG`

### 1.2 — JDK 17 portability note
- [x] No code change — document the requirement explicitly in `aidlc-docs/operations/v2.0-tester-release-notes.md` under a **Build prerequisites** section (`JAVA_HOME` must point to JDK 17 to build locally; CI/Gradle wrapper unaffected).

---

## 2. R8 minify + ProGuard keep rules (U10-Δ-1)

### 2.1 — `app/build.gradle.kts` release block
- [x] `isMinifyEnabled = false` → `isMinifyEnabled = true`
- [x] Add `isShrinkResources = true`
- [x] `proguardFiles(...)` already references `proguard-android-optimize.txt` + `proguard-rules.pro` — leave as-is.

### 2.2 — `app/proguard-rules.pro` keep rules
File is currently empty (defaults only). Add keep rules grouped by library:

- [x] **Compose** — `-keep class androidx.compose.** { *; }` is NOT needed (Compose has its own consumer rules), but keep `@Composable` reflection-safe by adding:
  ```
  -keepclasseswithmembers class * { @androidx.compose.runtime.Composable <methods>; }
  ```
- [x] **Hilt + KSP-generated DI**:
  ```
  -keep class dagger.hilt.** { *; }
  -keep class hilt_aggregated_deps.** { *; }
  -keep class * extends androidx.lifecycle.ViewModel
  -keepclassmembers class * { @javax.inject.Inject <init>(...); }
  -keep @dagger.hilt.android.HiltAndroidApp class *
  -keep @dagger.hilt.android.AndroidEntryPoint class *
  ```
- [x] **Retrofit + OkHttp** (consumer rules ship with the libs but be explicit about our service interface):
  ```
  -keep,allowobfuscation,allowshrinking interface retrofit2.Call
  -keep,allowobfuscation,allowshrinking class retrofit2.Response
  -keepclasseswithmembers,allowobfuscation interface * { @retrofit2.http.* <methods>; }
  -keep class com.spoolpainter.app.data.remote.spoolman.SpoolmanApi { *; }
  ```
- [x] **Gson DTOs** — Gson uses reflection on `SpoolmanModels.kt`. Keep all DTO fields:
  ```
  -keep class com.spoolpainter.app.data.remote.spoolman.dto.** { *; }
  -keepclassmembers class com.spoolpainter.app.data.remote.spoolman.dto.** { <fields>; }
  ```
  (If DTO package differs, walk `data/remote/spoolman/` and adjust the glob — verify in §2.4.)
- [x] **kotlinx-serialization** — used for `Settings`, `CustomMaterials`, `CustomBrands` DataStore. Use the official keep rules:
  ```
  -keepattributes *Annotation*, InnerClasses
  -dontnote kotlinx.serialization.AnnotationsKt
  -keep,includedescriptorclasses class com.spoolpainter.app.**$$serializer { *; }
  -keepclassmembers class com.spoolpainter.app.** {
      *** Companion;
  }
  -keepclasseswithmembers class com.spoolpainter.app.** {
      kotlinx.serialization.KSerializer serializer(...);
  }
  ```
- [x] **Domain enums** referenced in serialised JSON (e.g. `ThemeOverride`, `Currency`, `SpoolSortKey`, `FilamentSortKey`, `SortDirection`):
  ```
  -keepclassmembers enum com.spoolpainter.app.domain.** { *; }
  ```
- [x] **Crash log readability** — keep line-number info even after minification:
  ```
  -keepattributes SourceFile,LineNumberTable
  -renamesourcefileattribute SourceFile
  ```

### 2.3 — Log-stripping (NFR-5)
- [x] In `app/proguard-rules.pro`, add R8 assumeNoSideEffects rules to strip `android.util.Log` calls from release:
  ```
  -assumenosideeffects class android.util.Log {
      public static *** v(...);
      public static *** d(...);
      public static *** i(...);
      public static *** w(...);
      public static *** e(...);
  }
  ```
  This removes the 9 production call-sites in `MainViewModel`, `NfcRepository`, `SpoolmanRepository`, `ExtraCardUidsCodec`, `CreateAndPairUseCase` from the release APK only. Debug builds keep them.
- [x] After build, verify by `./gradlew :app:assembleRelease` then `apkanalyzer dex packages app/build/outputs/apk/release/*.apk | grep "android.util.Log"` — should return zero references in production code paths (Android framework refs from libs are unavoidable but inert).

### 2.4 — Verify DTO package globs
- [x] Walk `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/` to confirm DTO package paths used in §2.2 globs. Adjust if `dto/` subpackage doesn't exist (likely DTOs live directly in `domain/models/SpoolmanModels.kt` — keep that whole file).

---

## 3. UI-07 broader snackbar copy review — finalise

Verification done in this plan:
- ✅ UI-05 `"Couldn't write to tag. Try again."` already shipped (5 sites in `MainViewModel.kt:712, 723, 845, 875, 879`).
- ✅ AmbiguousOwnership friendly copy already shipped via `humanReadable.ParseError` (`MainViewModel.kt:946-973`).
- ❌ **MoveOnBindPartial** still developer-y at line 837-840.
- ❌ **TwoTagResult.Cancelled** still leaks raw reason at line 852.

### 3.1 — `MainViewModel.applyTwoTagResult.MoveOnBindPartial`
Current (lines 836-841):
```kotlin
"Partial state in Spoolman. UID was removed from spool " +
    "#${result.partiallyModifiedSpoolId}. Restore manually if needed."
```
- [x] Replace with friendlier copy. Suggested:
  > `"Couldn't finish moving the tag. Spool #${result.partiallyModifiedSpoolId} already released the tag — re-add it in Spoolman if needed."`

  Note: `MoveOnBindUseCase.Outcome.Failed.partiallyModifiedSpoolIds` is plural in the post-U6b polish (per state line 95). Verify what `TwoTagResult.MoveOnBindPartial` carries — single `partiallyModifiedSpoolId: Int` or list. If list, format as comma-separated.

### 3.2 — `MainViewModel.applyTwoTagResult.Cancelled`
Current (line 852):
```kotlin
"Second-tag pairing cancelled (${result.reason})"
```
- [x] Replace with reason-aware copy:
  > `"No second tag tapped. Tap Pair another to retry."`

  Drop the raw reason from user-visible text. Keep it via `Log.d` at debug for tester reports.

### 3.3 — Test updates
- [x] `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt` — update any assertions that match on the old strings. Locate via `grep -n "Partial state in Spoolman\|Second-tag pairing cancelled" app/src/test/`. Update to match new copy.

---

## 4. UI-05 NDEF write-failure copy — verification only
- [x] Grep `MainViewModel.kt` for `"Tag write failed"` — should be zero matches (already replaced with `"Couldn't write to tag. Try again."` in U9b §7).
- [x] If any match → fix in same pattern. If zero → mark UI-05 closed.
- [x] `ui-followups.md` UI-05 entry: flip **State** from `open` → `fixed` (state mismatch — already shipped, follow-up not yet updated).

---

## 5. Doc-drift sync

Per `aidlc-state.md` line 88 + your "fold into U10" answer.

### 5.1 — `aidlc-docs/inception/application-design/component-methods.md`
- [x] §1 line 22: `OpenSpoolPayloadParser` → `OpenSpoolPayloadCodec` (renamed in U4 / U2).
- [x] §1 line 37: same rename in prose.
- [x] §6 (`MainViewModel` use-case list): replace the original 6-use-case list with the actually-shipped 5: `ReadAndPairUseCase`, `CreateAndPairUseCase`, `TwoTagUseCase`, `RawWriteUseCase`, `VendorUidOnlyPairUseCase`. Confirm by reading current `MainViewModel.kt` injection list before editing.
- [x] §7: `Spool` / `Material` / `Brand` type refs — replace with the actually-shipped types: `SpoolmanSpool`, `Brand(name: String)` (interim), and remove the standalone `Material` reference (we use `MaterialPreset` + `Material`-string fields on the form).

### 5.2 — `aidlc-docs/inception/application-design/unit-of-work.md`
- [x] §3-U9 wording: subset of Settings UI shipped early in U5 (URL field + Save + Test + Refresh) is no longer accurate post-U9 (Test connection removed; Save full-width; sort/theme/currency added). Replace the parenthetical with: "U5 shipped a minimal subset (URL field + Save + Refresh); U9 reshaped the screen — see §3-U9 for the canonical scope."

### 5.3 — README rewrite (`README.md`)

Current `README.md` is 43 lines, written for v1 (no NFC flows beyond Read/Write, no architecture detail, no Play Store reference, no v2.1 roadmap, generic getting-started). Full rewrite to ship-ready v2 form:

- [x] **H1 + tagline** — `# SpoolPainter` + one-line tagline (e.g. "Manage 3D printer filament spools via NFC + Spoolman, in OpenSpool format.")
- [x] **Project status badge line** — version (`v2.0`), platform (`Android 10+ / API 29+`), license (current is none in repo — flag this; v2.1 transitions to GPL-3.0 per NFR-11; for v2.0 carry whatever's currently in the repo or note "All rights reserved" if none).
- [x] **Screenshots / hero image** — placeholder section for tester to add 1-2 screenshots after install (don't auto-generate; mark as TODO with explicit instruction "drop PNGs in `docs/screenshots/` and link here").
- [x] **§ What's new in v2.0** — reuse the bullet list from `v2.0-tester-release-notes.md` (§7.1) so the two stay in sync. Items:
  - Cleaner architecture (Hilt DI, MVVM, repositories — single-Activity Compose-only)
  - Read-and-pair flow with vendor tag classification (Bambu / Creality / Anycubic / Snapmaker / TigerTag)
  - Create-and-pair flow with `extra.variant` + `extra.card_uids` round-trip to Spoolman
  - PairAnotherTagSheet — pair multiple tags with one spool
  - Move-on-bind — taking a tag from spool A → spool B sweeps source spools, no partial state
  - Two-tag pairing flow — second tag prompted after first write
  - Side modes — Raw write, Vendor UID-only pair
  - Pickers + filament metadata expander with PATCH-on-edit
  - Settings: sort spools/filaments by Material/Brand/ID/Last Used; theme (Light/Dark/System dynamic colour); currency selector ($/€/¤); Spoolman URL config
  - In-place v1 → v2 update — same package id (`com.spoolpainter.app`), no migration needed (data lives on tags + Spoolman, not in the app)
- [x] **§ How to get it** — three sections in this order:
  1. **Play Store testing track** (recommended) — description: "v2.0 ships first to a Play Store testing track. Join the testing program with the link below to get over-the-air updates as new builds land." Link **TBD** until upload happens — author the section as `[Join the testing program](TBD-after-upload)` with a `<!-- TODO: replace after testing-track upload -->` HTML comment marker. User fills in after Play Console upload.
  2. **Sideload signed release APK** — link to GitHub Releases page (`https://github.com/ni4223/SpoolPainter/releases`); steps: enable Install Unknown Apps for browser/file manager; download APK; tap to install; first launch grants NFC permission.
  3. **Build from source** — clone the repo; `JAVA_HOME` must point to JDK 17; `./gradlew :app:assembleDebug` for sideload-and-iterate, `./gradlew :app:bundleRelease` for AAB. Note signing-key requirements (`~/spoolpainter-release-key.jks` + `KEYSTORE_PASSWORD` env or `~/spoolpainter-keystore.pwd`).
- [x] **§ How to install** — short numbered list:
  1. Android 10+ device with NFC
  2. Install from Play Store testing track *or* sideload APK
  3. Open app, grant NFC permission on first prompt
  4. (Optional) Open Settings → enter your Spoolman server URL → Save
  5. Tap a filament tag to read; or fill out the form + tap a blank tag to write + pair
- [x] **§ Spoolman setup** — pointer to upstream Spoolman repo (`https://github.com/Donkie/Spoolman`); note that SpoolPainter expects HTTP (LAN, self-hosted) — `usesCleartextTraffic="true"` is intentional for v2.0; HTTPS support is not blocked but not explicitly tested.
- [x] **§ NFC compatibility** — NDEF-formattable tags (NTAG213/215/216 most common); minimum payload capacity ~140 B for OpenSpool subset. Vendor tags (Bambu / Creality / Anycubic / Snapmaker / TigerTag) are detected and classified; vendor-tag *decoding* is v2.1 (U11).
- [x] **§ What's coming next** — v2.1 preview:
  - Vendor tag decoding — read Bambu / Creality / Anycubic / Snapmaker / TigerTag content directly without re-pairing (U11)
  - Per-vendor key list in Settings with keystore-backed encrypted storage (U12)
  - Edit a paired spool (UI-14) — change material/brand/colour/temps after pairing
  - Archive a spool from the app (UI-15)
  - Filament-metadata edit-on-existing-spool (UI-13) — diff-and-PATCH flow with confirmation
  - GPL-3.0 license transition (NFR-11; atomic with first U11 release)
- [x] **§ Tech stack** — Kotlin (JVM 11), Jetpack Compose + Material 3, Hilt DI, Retrofit + Gson + OkHttp, kotlinx-serialization (DataStore), kotlinx-coroutines, Android NFC API, R8 minify on release. Min SDK 29, target SDK 36.
- [x] **§ Architecture** — three-line summary: single-Activity Compose-only MVVM with per-screen ViewModels and Hilt-bound repositories. Layers: `ui/` (Compose) → `domain/` (use-cases + primitives) → `data/` (`SpoolmanRepository` + `NfcRepository`). NFC isolated under `hardware/nfc/`; Spoolman API under `data/remote/spoolman/`. Detail diagram pointer: `aidlc-docs/inception/application-design/application-design-component-diagram.png`.
- [x] **§ Privacy / data** — single-user app; no auth; no analytics; no cloud account. Spoolman URL is the only network destination.
- [x] **§ Contributing** — bug reports / testing feedback link (TBD by user — likely a GitHub Issue template or testing-track feedback form). Pull requests welcome on the v2 branch; mention AIDLC workflow under `aidlc-docs/` for major changes (link to `CLAUDE.md` for project-internal context).
- [x] **§ License** — current state. v2.0 keeps whatever the repo currently carries (verify before writing — `find . -maxdepth 2 -iname "license*"`). v2.1 plan: GPL-3.0 transition (NFR-11). Document the transition explicitly so testers know.
- [x] **§ Acknowledgements** — Spoolman (Donkie); OpenSpool spec; Snapmaker U1 firmware team (lot_nr / card_uid format reference); v1 contributors if any.

Carve-out: don't include build hashes, version-specific commit SHAs, or anything that goes stale immediately. The README is intended to live in main between v2.0 ship and v2.1; treat it as user-facing surface, not a changelog.

### 5.4 — Don't touch
- `aidlc-state.md` — already current.
- `unit-of-work-story-map.md` — coverage is correct; only U10 references appear and they describe future state not past.
- `CLAUDE.md` — project-internal entry point; not user-facing. README links to it under Contributing.

---

## 6. Manual-NFC verification checklist

Create `aidlc-docs/operations/manual-nfc-checklist.md`. The `aidlc-docs/operations/` directory does not yet exist — `mkdir -p` it first.

### 6.1 — Structure
- [x] H1: "v2.0 Manual NFC Checklist"
- [x] Header: device under test, build SHA, build variant (debug + release), Spoolman version, Snapmaker U1 firmware (if used)
- [x] Sections grouped by flow:
  - **§1. Read flow (U5 carries)** — passive ambient tap surfaces UID; OpenSpool tag prefills form; vendor-only tag (Bambu / Creality / Anycubic / Snapmaker / TigerTag) classified correctly; blank tag prompts form-first; UID-match auto-selects spool; `spool_id` fallback when card_uids miss; dropdown clear on UID-only ambient tap; 10s read timeout
  - **§2. Create-and-pair flow (U6a carries)** — fresh form + blank tag → filament + spool created in Spoolman with `extra.variant` + `extra.card_uids` populated; identical-form double-tap creates 1 filament + 2 spools; Spoolman 422 on missing density/diameter doesn't happen (defaults applied); colour-hex normalisation symmetric on read + write; partial create rolled back when subsequent steps fail
  - **§3. Pair another tag + Move-on-bind (U6b carries)** — PairAnotherTagSheet shown after first pair; second tag writes; both UIDs land in Spoolman; identical-tap on different-spool surfaces RepairConfirmSheet; Move it sweeps all source spools; multi-source case (UID on 2 spools) handled correctly; Cancel emits no misleading "No tag tapped"; vendor tag during second tag prompt → friendly rejection; NDEF write failure mid-flow surfaces friendly copy (UI-05)
  - **§4. Side modes (U7 carries)** — Raw-write mode writes payload to a blank tag; Vendor UID-only pair attaches UID to selected spool without writing payload
  - **§5. Pickers + custom entries (U8 carries — 12 scenarios)**:
    - [ ] Add custom material → survives app restart
    - [ ] Add custom brand → survives app restart
    - [ ] Material picker dedup (`pla` + preset `PLA` → one row)
    - [ ] Brand merge across Spoolman vendors + presets
    - [ ] Filament picker — 0-spool filament + 1+-spool deliberate-2nd-spool add
    - [ ] Expander prefill from existing filament metadata
    - [ ] Expander PATCH idempotency (no HTTP call when nothing changed)
    - [ ] Expander PATCH applied (changed field rides on wire)
    - [ ] Both expanders independent (Filament + More details)
    - [ ] Default form layout byte-identical to U7 when both expanders collapsed
    - [ ] Custom-material dedup vs preset
    - [ ] Add-custom auto-select (Q-U8-15=A)
  - **§6. Settings + theming + sort + currency (U9 carries — 10 scenarios)**:
    - [ ] Spool sort: Material / Brand / ID / Last Used reorder dropdown; Asc/Desc segmented row flips ordering live
    - [ ] Filament sort: Material / Brand / ID (no Last Used option)
    - [ ] Sort independence: spool sort change doesn't affect filament sort and vice versa
    - [ ] Last Used Desc puts most-recently-consumed spool first; never-consumed spools sort last
    - [ ] Theme Switch on Settings TopAppBar: Light ↔ Dark applies live without recreate; persists across cold-start
    - [ ] Currency segmented row: `$ Dollar` / `€ Euro` / `¤ Money` flips price suffix in `MoreDetailsExpander`
    - [ ] Banner only when URL configured AND Spoolman unreachable
    - [ ] Material You dynamic color visible on Android 12+ device
    - [ ] No Test connection button on Settings; Save button full-width
    - [ ] TalkBack reads "Theme: Light (tap to switch to Dark)" / vice versa on Switch
  - **§7. UI polish (U9b carries)**:
    - [ ] Logo + Settings cog overlay on main screen (no Material 3 TopAppBar)
    - [ ] Logo tint follows form colour
    - [ ] Per-section Cards (Spoolman / FilamentForm / MoreDetailsExpander)
    - [ ] Save & Write lifted out of FilamentForm into top-level
    - [ ] Temp folded into MoreDetailsExpander as a labelled section
    - [ ] IME-aware snackbar (keyboard up: Save and Test connection messages remain visible)
    - [ ] Once-per-session passive-tap hint emits
    - [ ] "Other" / "Color Wheel" affordances styled with Add / Palette icons in primary tint
  - **§8. Spoolman gating (U9b post-close-out fix)**:
    - [ ] No Spoolman URL → Filament Form expander + More Details expander hidden
    - [ ] URL configured + reachable → both visible + enabled
    - [ ] URL configured + unreachable → both visible + disabled
    - [ ] Temperature section visible in all three states
    - [ ] Offline banner: titleSmall "Spoolman unreachable" + bodySmall detail line
  - **§9. Snapmaker U1 round-trip** — write a tag from SpoolPainter → read it back on Snapmaker U1 printer → lot_nr lookup works (`GET /v1/spool?lot_nr=card_uid:XXXX`); printer surfaces correct material/brand/colour; second-tag-on-same-spool also recognised
  - **§10. Release build smoke test** — install signed release APK; one read, one create-and-pair, one Spoolman gate observation; confirm no `Log.*` output in `adb logcat` from `com.spoolpainter.app` (NFR-5 verified live)

### 6.2 — Result table
- [x] After each section, a results table with columns: Scenario / Pass-Fail / Tester / Date / Notes.

---

## 7. Tester release notes

Create `aidlc-docs/operations/v2.0-tester-release-notes.md`.

### 7.1 — Structure
- [x] H1: "SpoolPainter v2.0 — Tester Release Notes"
- [x] **What's new** (bullet list — written for testers, not developers):
  - In-place update over v1 — same package id, no migration needed
  - Cleaner architecture (Hilt DI, MVVM, repositories)
  - New Settings screen — sort spools/filaments by Material / Brand / ID / Last Used; theme switch (Light/Dark/System dynamic); currency selector ($/€/¤)
  - Inline expanders for filament metadata and "More details" — collapsed by default
  - Two-tag pairing flow — pair multiple tags with one spool via PairAnotherTagSheet
  - Move-on-bind — taking a tag from one spool to another now sweeps source spools instead of leaving partial state
  - Vendor tag handling — Bambu/Creality/Anycubic/Snapmaker/etc. tags classified and surfaced rather than silently failing
  - Spoolman-only sections gated by URL + reachability
- [x] **Known issues / parked**:
  - Editing a paired spool (UI-14) — post-v2.0
  - Archiving a spool from the app (UI-15) — post-v2.0
  - Filament-metadata edit-on-existing-spool (UI-13) — post-v2.0
  - Vendor-tag decode (Bambu / Creality content readable) — v2.1 (U11)
  - Vendor key settings (U12) — v2.1
- [x] **Build prerequisites** (for testers building locally): JDK 17 (`JAVA_HOME` must point to JDK 17); Android Studio Iguana+ recommended; Gradle wrapper handles the rest.
- [x] **How to provide feedback**: GitHub issue link or testing-track feedback form (TBD by user before upload).

---

## 8. Testing-track upload checklist

Create `aidlc-docs/operations/testing-track-upload-checklist.md`. Pure procedural doc — actual upload is gated on user confirmation.

### 8.1 — Structure
- [x] **Pre-flight**:
  - [ ] All §1-§5 plan steps complete and committed
  - [ ] `./gradlew :app:testDebugUnitTest` ✅ 362/362
  - [ ] `./gradlew :app:lintRelease` ✅ no errors
  - [ ] Manual NFC checklist (`manual-nfc-checklist.md`) run end-to-end ✅
  - [ ] Release build smoke-tested on device
- [x] **Build artefacts**:
  - [ ] `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  - [ ] `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
  - [ ] Verify signing: `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`
  - [ ] APK size sanity check (target: significantly below the 65 MB debug build; expect ~25-35 MB after R8 minify)
- [x] **Testing-track choice** (TBD by user): Internal testing (≤100 testers, no review) / Closed testing (named testers, no review) / Open testing (anyone with link, review)
- [x] **Console upload steps** (manual):
  1. Play Console → Testing → [chosen track] → Create new release
  2. Upload `app-release.aab`
  3. Paste tester release notes from `v2.0-tester-release-notes.md`
  4. Save → Review → Start rollout to [chosen track]
- [x] **Post-upload**:
  - [ ] Tester invite link distributed
  - [ ] Crash dashboard monitored for 48h before declaring release-validated

---

## 9. Build verification

Run end-to-end before close-out:

- [x] `./gradlew :app:compileDebugKotlin` ✅
- [x] `./gradlew :app:testDebugUnitTest` ✅ — expect **362 / 362** (test count delta from §3 copy edits should be 0; only assertion strings change)
- [x] `./gradlew :app:assembleDebug` ✅ (sanity)
- [x] `./gradlew :app:assembleRelease` ✅ — first time R8 runs in this branch; expect possible keep-rule iteration
- [x] `./gradlew :app:bundleRelease` ✅
- [x] APK size check: `du -h app/build/outputs/apk/release/*.apk` (target: ~25-35 MB; if > 50 MB after R8, ProGuard rules need tightening)
- [ ] APK content audit: `apkanalyzer files list app/build/outputs/apk/release/app-release.apk | head -50` — confirm no debug-only resources made it in
- [ ] Log-strip verification: `apkanalyzer dex code --class com.spoolpainter.app.ui.screens.main.MainViewModel app/build/outputs/apk/release/app-release.apk | grep -c "Log\\\\."` should be **0**

---

## 10. Brownfield invariants

- [x] No `*_modified.kt` / `*_new.kt` / `*.bak` files in the diff
- [x] No production code added (this is release polish — only build-config + ProGuard + 2 string edits + 5 docs)
- [x] `aidlc-docs/operations/` is created (didn't exist before)
- [x] No edits to `.kiro/steering/aws-aidlc-rules/**` or `.kiro/aws-aidlc-rule-details/**` (vendored)

---

## 11. U10 install gate

Per `unit-of-work.md` §U10 exit criteria — milestone install gate is mandatory for U10 (it doubles as testing-track release validation):

- [ ] **Debug build** sideloaded on moto g stylus 2025 / Android 16; smoke test (read + create-and-pair + write); no obvious regression.
- [ ] **Manual NFC checklist** run end-to-end on debug build (§6.1).
- [ ] **Release build** signed APK sideloaded on the same device; same smoke test; **`adb logcat | grep com.spoolpainter.app`** shows zero `D/` `I/` `W/` entries from app code (NFR-5 live verification).
- [ ] **Snapmaker U1 round-trip** verified (write from SpoolPainter, read on printer, lot_nr lookup).

If any scenario fails, fix-iterate-rerun loop just like prior install gates. Failures land as `U10-Δ-N` deltas applied in-session.

---

## 12. Files impacted (estimate)

**Modified** (~7):
- `app/build.gradle.kts` — versionCode/Name + R8 flags
- `app/proguard-rules.pro` — keep rules (was empty)
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` — 2 string edits (§3.1 + §3.2)
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt` — assertion string updates
- `README.md` — full rewrite per §5.3 (v1 → v2.0 ship-ready)
- `aidlc-docs/inception/application-design/component-methods.md` — doc drift
- `aidlc-docs/inception/application-design/unit-of-work.md` — doc drift (§3-U9 wording)
- `aidlc-docs/ui-followups.md` — flip UI-05 state to `fixed`

**Created** (~3):
- `aidlc-docs/operations/manual-nfc-checklist.md`
- `aidlc-docs/operations/v2.0-tester-release-notes.md`
- `aidlc-docs/operations/testing-track-upload-checklist.md`

**Deferred fill-in** (after testing-track upload):
- `README.md` Play Store testing-track link — placeholder + TODO HTML comment marker. User pastes the join link after Play Console upload completes.

**Deleted**: 0

**Tests**: 0 net (assertion strings updated in-place; no new test methods, no removals).

---

## 13. Out of scope (parked, explicit)

- ~~U10-Δ-2 sortOrder JSON migration~~ — **DROPPED** (no users on prior v2 build).
- UI-13 / UI-14 / UI-15 — post-v2.0 release per "add editing for something later after release".
- Optional debug UID surface — dropped per session direction.
- v2.1 units (U11 vendor decode + GPL-3.0 transition; U12 vendor key settings) — hard-gated behind v2.0 ship.

---

## 14. Approval gate (Code Gen Part 1)

Per `core-workflow.md` CONSTRUCTION → Per-Unit Loop → Code Generation Part 1:

🔧 **Request Changes** — modify the plan; re-author and re-present.
✅ **Continue to Next Stage** — approve plan; execute Code Gen Part 2 (§1 → §11) end-to-end.
