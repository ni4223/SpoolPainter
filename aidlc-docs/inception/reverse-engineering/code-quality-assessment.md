# Code Quality Assessment

## Test Coverage
- **Overall**: None
- **Unit Tests**: None — `app/src/test/` is empty
- **Integration Tests**: None — `app/src/androidTest/` contains only `.DS_Store`
- **Test deps declared**: JUnit 4, AndroidX JUnit, Espresso, Compose UI Test
  (declared in `app/build.gradle.kts` but not exercised)

## Code Quality Indicators
- **Linting**: Default Android Lint only; no detekt / ktlint configured
- **Code Style**: Generally consistent Kotlin idioms
- **Documentation**:
  - `README.md` is short and slightly out of date
    (says "API level 21+" — `minSdk` is actually 29 in the current build)
  - `CLAUDE.md` and `.kiro/steering/*.md` are well-curated and current
  - In-source documentation is minimal; reasonable for the size

## Technical Debt

### State management
- **`MainViewModel` uses raw `mutableStateOf`** rather than `StateFlow` /
  `UiState` data class. Form-field state (material, variant, color, brand,
  temps) lives in `SpoolPainterScreen` via `remember`/`mutableStateOf`,
  decoupled from the ViewModel. A read-tag flow uses a `dataVersion` int as
  a tripwire to push values from VM → screen via `LaunchedEffect`. The
  whole "form state" should be a single `StateFlow<FormUiState>` in the
  ViewModel.
- **Two parallel sources of truth** for selected filament: `selectedSpool`
  and `readData`. `handleFilamentSelection` reconciles them with an ad-hoc
  rule ("preserve existing non-Basic subtype"), which is the kind of
  reconciliation the form-state model should make explicit.

### Layering
- **`SpoolmanFilamentDropdown` instantiates `SpoolmanService` directly** in
  a `LaunchedEffect` to look up by spool id. A UI component should not own
  network access; this belongs in the ViewModel (or a repository).
- **`SpoolmanService` is created on every settings save / spool lookup**
  rather than being a singleton — caching benefits are partly defeated.

### NFC layer
- **Sealed `NfcResult` is unused.** The Controller surfaces results via
  two raw lambdas (`onTagDetected`, `onStatusUpdate`) — losing the type
  safety the sealed class would provide.
- **Read mode flag (`isReadingEnabled`) and `pendingWriteData`** plus the
  "recent tag memory" 5-second window form an implicit state machine that
  isn't modelled explicitly. Easy to misorder.
- **No write verification** — after `writeNdefMessage`, the app does not
  read back to verify, and there is no handling for `IsoDep` / non-NDEF
  tags or for tags that report `isWritable = false`.
- **`Toast` import & `showToast` declared but unused** in `NfcController`.

### Error handling
- **Network errors are silently swallowed** in `SpoolmanService.getFilaments`
  (returns empty list / cached). The UI shows nothing — no
  "Spoolman unreachable" indicator.
- **`OpenSpoolData.fromJson` returns null on any exception** — non-OpenSpool
  tag types and corrupted payloads can't be distinguished from each other
  in the UI.

### Logging
- **`android.util.Log.d/e` calls are sprinkled through production code paths**
  (ViewModel, Screen, Dropdown, Service). For v2 these should be gated by
  a debug-only logger or removed.

### UI
- **`ColorSelector.kt` is 686 lines** — by far the largest component;
  candidate for refactor.
- **`SpoolPainterScreen` mixes layout, state, and event handling** in one
  long composable.
- **Manual offsets** (`offset(y = -60.dp)`, etc.) for the settings icon are
  layout fragile.
- **`NfcStatusCard.kt` appears unused** — likely dead code.

### Dependencies / hygiene
- **Some lib versions are older** than peers (e.g., `lifecycle-runtime-ktx 2.6.1`,
  `activity-compose 1.8.0`) — fine, but candidates for refresh in v2.
- **`OkHttp logging-interceptor` is declared but not installed** on the OkHttp
  client builder.
- **`com.google.android.material:material 1.12.0`** likely unnecessary if
  Compose Material 3 is the only UI surface; verify on v2.

### Security / privacy
- **`usesCleartextTraffic="true"`** is intentional (LAN HTTP Spoolman) but
  global. v2 could constrain it via a network-security config to
  RFC1918 ranges only.
- **No allowlist/validation of the user-entered Spoolman URL** beyond
  "non-empty and not equal to placeholder."
- **No basic-auth or reverse-proxy auth support** — fine for the current
  single-user scope; flag if scope changes.

### Misc
- **Unused models** (`NfcResult`, `NfcTag`, `AppState`) — delete or wire in.
- **`MainViewModel` reads `Context` for SharedPreferences** — passing
  Context into the ViewModel methods is acceptable but a `SettingsRepository`
  with `DataStore` would be cleaner for v2.

## Patterns and Anti-patterns

### Good patterns
- Clear package separation (`ui` / `domain` / `hardware` / `data`).
- Companion-object factories for model conversions.
- Single Activity + Compose-only.
- Build-variant strategy (debug suffix `.debug`) supports side-by-side install
  during the rewrite.

### Anti-patterns
- UI components reaching into network layer (`SpoolmanFilamentDropdown` →
  `SpoolmanService`).
- Implicit NFC state machine via two booleans + nullable strings.
- Form state split between ViewModel and screen, reconciled with a
  `dataVersion` tripwire.
- Sealed result type defined but ignored.
- Silent error swallowing in the Spoolman client.
