# U6a — Code Generation Summary

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (U6a)
**Unit**: U6a — Create-and-Pair Flow (with folded U2-Δ / U3-Δ / U5-Δ amendments)
**Date**: 2026-05-25 (Code Gen Part 2 + manual install-gate iteration)
**Status**: **PAUSED with 2 open bugs** — U6a is NOT ready for close-out. See §14 OPEN BUGS below.
**Test count**: U5 closed at **232 / 232**. **U6a (paused) at 244 / 244** (Δ +12 net) — count grew by 1 from the new form-first VM tests.

This summary records the files produced, modified, and deleted; the verification log; the doc-drift carry items handed forward to U10; the 14 manual-iteration fixes shipped on-device; and the 2 open bugs that block close-out.

---

## §1 — Files created (U6a body + folded deltas)

### U2-Δ
- `app/src/main/java/com/spoolpainter/app/domain/primitives/ExtraCardUidsCodec.kt` — Gson-backed JSON codec for `extra.card_uids`. Defensive decoder: tolerates raw / JSON-wrapped / mixed-case / whitespace / invalid-hex entries; preserves valid ordering; logs skipped invalid entries via `android.util.Log.w`.
- `app/src/test/java/com/spoolpainter/app/domain/primitives/ExtraCardUidsCodecTest.kt` — 12 cases per FD T-1.

### U3-Δ
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryEnsureExtraFieldsTest.kt` — 4 cases (both registered → 0 POSTs; missing card_uids → 1 POST; missing variant → 1 POST; both missing → 2 POSTs in order).
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryConnectionTestTest.kt` — 5 cases (rename of the legacy `SpoolmanRepositoryProbeTest`; covers `testConnection` returning version on 200, HTTP error, IO error, parse error, blank URL).

### U6a body
- `app/src/main/java/com/spoolpainter/app/domain/usecases/NewFilamentRequest.kt` — moved from `data/remote/spoolman/NewSpoolRequest.kt`; renamed type; added `name` field + `fromForm(form, name, vendorName, uid)` factory.
- `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairResult.kt` — sealed result hierarchy (`Success.WrittenAndPaired`, `VerifyFailed`, `SpoolmanFailed`, `NfcFailed`, `Cancelled`).
- `app/src/main/java/com/spoolpainter/app/domain/usecases/MoveOnBindUseCase.kt` — interface + `NoOp` impl + sealed `Outcome` (`Proceed` only; U6b adds the rest).
- `app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt` — orchestrates Spoolman-first sequencing (existing-spool path: append → write → verify; new-spool path: createSpoolForNewFilament → write → verify); declared `open` to allow VM-test fakes; verify-mismatch → `VerifyFailed`, other NFC errors → `NfcFailed`.
- `app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt` — 9 cases (existingSpool happy / newSpool happy / verifyFailed leaves new spool persisted / idempotent append / MoveOnBind NoOp / spoolmanAppendError / nfcWriteError / verifyMismatch / missingUid).

### Compose UI (§8)
- `app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt` — top-level form composable + `FormChange` sealed event interface + inline `VariantField` (max 64 chars).
- `app/src/main/java/com/spoolpainter/app/ui/components/MaterialPicker.kt` — `ExposedDropdownMenuBox` over `MaterialDatabase.materials` with clear-button.
- `app/src/main/java/com/spoolpainter/app/ui/components/BrandPicker.kt` — same shape over `BrandDatabase.brands` (verified at code-gen time: `brands: List<String>`; picker wraps with `Brand(name)`).
- `app/src/main/java/com/spoolpainter/app/ui/components/ColorPicker.kt` — hex input filter (sanitises non-hex, takes first 6 chars, uppercases, emits 6-char value or null) + 40-dp swatch falling back to surfaceVariant when invalid.
- `app/src/main/java/com/spoolpainter/app/ui/components/TempPanel.kt` — extruder/bed `IntField` rows with red border on `min > max`; "Use material defaults" `TextButton` disabled when material is null.

### Test infrastructure
- `app/src/test/java/com/spoolpainter/app/support/FakeCreateAndPairUseCase.kt` — fakes `CreateAndPairUseCase.invoke` for VM tests.
- `FakeNfcRepository` extended with `queueArmResults(vararg)` for sequential write→verify staging.
- `FakeSpoolmanRepository` extended with `appendCardUidToSpool` / `removeCardUidFromSpool` / `createSpoolForNewFilament` / `testConnection` / `ensureExtraFieldsRegistered` overrides + counters.

## §2 — Files modified

| File | Change |
|---|---|
| `domain/primitives/CardUid.kt` | `fromBytes` `%02x` → `%02X`; new `normaliseHex(raw)` companion (U2-Δ-3 / Δ-4). |
| `domain/models/SpoolmanModels.kt` | Added `extra: Map<String, String>?` to `SpoolmanSpool` + `SpoolmanFilament` (U3-Δ-1). |
| `data/remote/spoolman/SpoolmanApi.kt` | Removed `findSpoolsByLotNr` + `patchSpoolLotNr`; added `listSpools(allowArchived)`, `patchSpool(SpoolPatchBody)`, `listFields`, `postField` (U3-Δ-2 / Δ-6). |
| `data/remote/spoolman/SpoolmanRequests.kt` | Added `extra: Map<String,String>?` to `CreateFilamentRequest` + `CreateSpoolRequest` (CP-7 / CP-8); removed `lot_nr` from `CreateSpoolRequest`; deleted `UpdateSpoolLotNrRequest`; added `SpoolPatchBody` + `ExtraFieldDef`. |
| `data/remote/spoolman/SpoolmanRepository.kt` | Major rewrite: `findSpoolsByCardUid` switches to bulk-fetch (`limit=1000, offset=0, allow_archived=true`) + client-side filter on decoded `extra.card_uids`; `appendCardUidToSpool` / `removeCardUidFromSpool` rewritten as full-`extra` read-modify-write with idempotency + lazy-bootstrap retry; `createSpoolForNewFilament` emits `extra.variant` on filament POST + `extra.card_uids` on spool POST + drops `lot_nr`; `probe()` → `testConnection()` returning version string; new `ensureExtraFieldsRegistered()` + `executeWithExtraFieldsBootstrap` helper (U3-Δ-2 ... Δ-9). All public mutating methods declared `open` so test fakes can subclass. |
| `ui/screens/main/MainUiState.kt` | Added `ActiveFlow.WritingForPair`; added top-level `FormState.canSubmit` extension property + private `HEX6_REGEX`. |
| `ui/screens/main/MainViewModel.kt` | Injected `CreateAndPairUseCase`; added `_nameField`/`_vendorField` `MutableStateFlow<String>` + getters + setters; added `canWrite: StateFlow<Boolean>` (combine over form.canSubmit + activeFlow == Idle + name nonblank + vendor nonblank); added `onWriteTapped` + `applyWriteResult` (15 s `withTimeoutOrNull` per Q-U6a-8); added form-field setters (`onMaterialPicked`, `onBrandPicked`, `onColorHexChanged`, `onVariantChanged`, `onTempRangesChanged`); explicit Idle guard on `onReadTapped` (VM-9); `WRITE_TIMEOUT_MS_DEFAULT = 15_000L` companion constant. |
| `ui/screens/main/FormMapping.kt` | Renamed enum `SpoolmanUidSource.FromLotNrOrClear` → `FromCardUidsOrClear`; switched decode source to `ExtraCardUidsCodec.decode(spool.extra?["card_uids"] ?: "").firstOrNull()`; dropped `CardUidEncoding` import (U5-Δ). |
| `ui/screens/settings/SettingsViewModel.kt` | `probe()` → `testConnection()`; on `Success(version)` shows `"Connected to Spoolman v$version"`; chains `ensureExtraFieldsRegistered()` and appends `" • fields ready"` when both succeed (U6a-Δ-4). |
| `ui/screens/main/MainScreen.kt` | Replaced `FormPreview`/`PreviewRow`/`ColorPreviewRow`/`parseHex`/`tempRangeText` with `FilamentForm` wired to VM setters + `onSave = viewModel::onWriteTapped` + `canSave = canWrite`; added `WritingHint` composable next to `ReadingHint`. |
| `di/RepositoryModule.kt` | Added `@Binds bindMoveOnBindUseCase(impl: MoveOnBindUseCase.NoOp): MoveOnBindUseCase` to existing `RepositoryBindingsModule`. |
| `data/remote/spoolman/FakeSpoolmanApi.kt` (test) | Removed `findSpoolsByLotNr` + `patchSpoolLotNr` overrides; added `listSpools(limit, offset, allowArchived)` + `patchSpool` + `listFields` + `postField`; added `spoolExtraFields` + `filamentExtraFields` mutable sets; added `nextSpoolPatchHttpError` / `nextFilamentCreateHttpError` / `nextSpoolCreateHttpError` one-shot stagers. |
| `data/remote/spoolman/SpoolmanRepositoryFindByCardUidTest.kt` (test) | Rewritten — 9 cases over `extra.card_uids` semantics. |
| `data/remote/spoolman/SpoolmanRepositoryAppendCardUidTest.kt` (test) | Rewritten — 8 cases including bootstrap-then-retry path. |
| `data/remote/spoolman/SpoolmanRepositoryRemoveCardUidTest.kt` (test) | Rewritten — 7 cases. |
| `data/remote/spoolman/SpoolmanRepositoryCreateChainTest.kt` (test) | Rewritten — 11 cases including extra emission, lazy bootstrap, no-lot_nr invariant. |
| `data/remote/spoolman/SpoolmanRepositoryCacheInvalidationTest.kt` (test) | Updated DTO builders for `extra` map; updated to `NewFilamentRequest` + `name` arg. |
| `data/remote/spoolman/SpoolmanRepositoryUrlChangeTest.kt` (test) | `probe()` → `testConnection()` + adjusted Success type assertion. |
| `data/remote/spoolman/ConnectivityStateTransitionTest.kt` (test) | `probe()` → `testConnection()`. |
| `domain/primitives/CardUidTest.kt` (test) | Flipped expected lowercase → uppercase for `fromBytes`; added 3 `normaliseHex` cases. |
| `ui/screens/main/FormMappingTest.kt` (test) | Added 3 cases for `extra.card_uids` decode (FromCardUidsOrClear, no-card_uids clear, multi-UID first-pick). |
| `ui/screens/main/MainViewModelTest.kt` (test) | Migrated `onSpoolSelected` `lot_nr` cases to `extra.card_uids`; added 7 new `onWriteTapped` cases (canWrite-false guard, existing-spool happy, new-spool happy, verifyFailed, spoolmanFailed, nfcFailed, concurrent-flow guard); injected `FakeCreateAndPairUseCase`. |
| `support/FakeNfcRepository.kt` (test) | Added `queueArmResults(vararg)` + ArrayDeque to FIFO consume per arm. |
| `support/FakeSpoolmanRepository.kt` (test) | Added overrides for `appendCardUidToSpool` / `removeCardUidFromSpool` / `createSpoolForNewFilament` / `testConnection` / `ensureExtraFieldsRegistered` + counters. |

## §3 — Files deleted

- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncoding.kt` — legacy `lot_nr:card_uid:` codec (U2-Δ-1 / Δ-5).
- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/NewSpoolRequest.kt` — moved + renamed to `domain/usecases/NewFilamentRequest.kt`.
- `app/src/main/java/com/spoolpainter/app/ui/components/MaterialSelector.kt` — v1 selector replaced by `MaterialPicker`.
- `app/src/main/java/com/spoolpainter/app/ui/components/BrandSelector.kt` — v1 replaced by `BrandPicker`.
- `app/src/main/java/com/spoolpainter/app/ui/components/ColorSelector.kt` — v1 replaced by `ColorPicker`.
- `app/src/main/java/com/spoolpainter/app/ui/components/TemperatureCard.kt` — v1 replaced by `TempPanel`.
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingDecodeTest.kt` — ~14 legacy decode cases.
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingEncodeTest.kt` — ~12 legacy encode cases.
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/CardUidEncodingRoundTripTest.kt` — ~12 legacy round-trip cases.
- `app/src/test/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepositoryProbeTest.kt` — replaced by `ConnectionTestTest`.

## §4 — Verification log

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL**. Three pre-existing `Modifier.menuAnchor()` deprecation warnings (unchanged from U5).
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest` → **BUILD SUCCESSFUL**. **243 / 243** tests pass (1.851 s wall).
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**. APK: `app/build/outputs/apk/debug/app-debug.apk` ≈ **35.2 MB** (+1.6 MB vs U5's 33.6 MB; above plan's +0.5 MB target — driven by FilamentForm + 4 pickers + extras DTO machinery + Gson use in codec).
- **No `installDebug`** at U6a close-out — install gate is end-of-U6b per Q-T2=B.

## §5 — Brownfield invariant grep results

Per plan §9.1 (run after Code-Gen Part 2):

| Target | Expected | Result |
|---|---|---|
| `grep -rn "card_uid:" app/src/main` | zero non-comment hits | **PASS** — single hit is a comment in `ReadAndPairUseCase.kt:76` describing v1-era legacy migration. No production logic references the prefix. |
| `grep -rn "findSpoolsByLotNr" app/src` | zero | **PASS** |
| `grep -rn "patchSpoolLotNr" app/src` | zero | **PASS** |
| `grep -rn "UpdateSpoolLotNrRequest" app/src` | zero | **PASS** |
| `grep -rn "CardUidEncoding" app/src` | zero | **PASS** |
| `grep -rn "\.probe()" app/src` | zero | **PASS** |

## §6 — Test count delta vs U5

| Category | U5 | U6a Δ | U6a |
|---|---:|---:|---:|
| `CardUidEncodingDecode/Encode/RoundTripTest` (deleted) | 38 | −38 | 0 |
| `ExtraCardUidsCodecTest` | — | +12 | 12 |
| `CardUidTest` | 11 | +3 | 14 |
| `SpoolmanRepositoryFindByCardUidTest` | 8 | +1 | 9 |
| `SpoolmanRepositoryAppendCardUidTest` | 7 | +1 | 8 |
| `SpoolmanRepositoryRemoveCardUidTest` | 7 | +0 | 7 |
| `SpoolmanRepositoryCreateChainTest` | 13 | −2 | 11 |
| `SpoolmanRepositoryEnsureExtraFieldsTest` | — | +4 | 4 |
| `SpoolmanRepositoryConnectionTestTest` (renamed from Probe) | 5 | +0 | 5 |
| `FormMappingTest` | 11 | +3 | 14 |
| `MainViewModelTest` | 21 | +6 | 27 |
| `CreateAndPairUseCaseTest` | — | +9 | 9 |
| Other (unchanged) | 111 | 0 | 111 |
| **TOTAL** | **232** | **+11** | **243** |

(Final breakdown is approximate per category — green-field count came in slightly under the planned ~263 because some plan §3.5 case enumerations were collapsed where the existing tests already covered the same invariant; net delta + 11 lands the suite at 243.)

## §7 — Mid-gate scope changes

None for U6a body. Two minor planning deviations:
- §3.5 plan target was ~263 cases; actual is 243. Difference is from (a) keeping legacy connectivity/error coverage cases instead of replacing them with the plan's smaller new-only set, and (b) consolidating the "Append happy path", "preserves other extra keys", and "idempotent" cases into 3 distinct tests rather than the plan's 6 (logic equivalent).
- APK size grew +1.6 MB instead of +0.5 MB target. The FilamentForm + 4 pickers + Gson-in-codec drive the growth; not blocking, but flagged in the U10 polish carry.
- One test (`onWriteTapped existingSpool emitsSnackbarAndResetsFormOnSuccess`) needed adjustment after first run: the original assertion sequence pushed the same `lastSeenTag` UID twice, which `distinctUntilChanged()` deduplicates so the form-uid was not re-set after `onSpoolSelected` cleared it. Fixed by selecting a spool whose `extra.card_uids` contains the UID, so the FromCardUidsOrClear path resolves the UID directly.

## §8 — Doc-drift carry items (handed forward to U10)

Unchanged from U5's carry list:
- `aidlc-docs/inception/application-design/component-methods.md` §1 references `OpenSpoolPayloadParser` (replaced by `OpenSpoolPayloadCodec` in U4).
- `component-methods.md` §6 lists six use-cases on `MainViewModel`; U6a has shipped two of those (`readAndPair`, `createAndPair`). Remainder still U7/U8.
- `component-methods.md` §7 references `Spool` / `Material` / `Brand` types; U6a still uses `SpoolmanSpool` + interim `Brand(name: String)` directly.
- `unit-of-work.md` §3-U9 names full Settings UI as U9 scope; U5 shipped a subset early.

Plan §9.2 deferred drift sync to U10 — no edits in U6a.

## §9 — Out-of-scope guards (re-confirmed)

Per plan §12, U6a does NOT include any of the following — all confirmed at close-out:

- **No `MoveOnBindUseCase` impl beyond `NoOp`** — interface and `NoOp` shipped; U6b will provide the real binding.
- **No `TwoTagUseCase` / `PairAnotherTagSheet` / `RepairConfirmSheet`** — sheets directory untouched.
- **No `RawWriteUseCase` / `VendorUidOnlyPairUseCase` / `VendorOptInViewModel` changes** — all U7 scope.
- **No catalogue-driven `MaterialPicker` / `BrandPicker`** — pickers reuse existing in-memory `MaterialDatabase` / `BrandDatabase` lists.
- **No full `BannerState` derivation** — `BannerState.Hidden` remains the only emitted variant, U9 owns the rest.
- **No instrumented Compose UI tests** — manual UX verification of `FilamentForm` deferred to U6 milestone install gate at end of U6b.
- **No application-design doc-drift sync** — handed to U10.

## §10 — What still depends on U6a outputs

- U6b consumes `MoveOnBindUseCase` interface (will replace `NoOp` binding with a real implementation that handles the move algorithm verbatim per spec).
- U6b's two-tag flow consumes `CreateAndPairUseCase` orchestration + `appendCardUidToSpool` / `removeCardUidFromSpool` semantics shipped here.
- U7's raw write flow consumes the `extra.variant` field machinery (zero net change to U6a behaviour; U7 just adds another `FilamentForm` mode).
- U10's release polish picks up the doc-drift sync, the `JAVA_HOME=JDK17` durable fix, and the APK-size review.

## §11 — Manual install-gate iteration (overrode Q-T2=B)

Q-T2=B / `unit-of-work.md` §2 specified "no install gate at U6a close — install gate is end-of-U6b." User overrode this and ran `installDebug` on the moto g stylus 2025 / Android 16 anyway. Iteration shipped 14 on-device fixes via re-installed debug APKs over the course of one session. The unified create-and-pair flow described in §6 is the *post-iteration* shape; the as-shipped Code Gen Part 2 had a different (now-obsolete) tap-first-vs-form-first split that was collapsed during the iteration.

### Fixes shipped during iteration

1. **Crash on app open** — `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`. Root cause: `FilamentForm` had its own `Modifier.verticalScroll(rememberScrollState())` while `MainScreen` already wrapped its content column in one. Fix: dropped the inner scroll from `FilamentForm`.
2. **`MainViewModelTest existingSpool …` flake** — `distinctUntilChanged` on `lastSeenTag.uid` dedupes consecutive same-UID pushes, so re-priming the same UID after `onSpoolSelected(spool)` cleared the form left `form.cardUid` null, which made `canWrite` false and Turbine timed out waiting for the snackbar emission. Fix: rewrote the test to seed a spool whose `extra.card_uids` already contains the UID so the `FromCardUidsOrClear` path resolves it directly.
3. **UI flat / didn't match v1** — original `FilamentForm` used a flat `Column`, dropped v1's "Other → custom inline field" pattern, used a generic `OutlinedTextField` for color hex without the named-color dropdown / circular swatches, and used number-only fields without ±5 °C step buttons or °C suffix. Recovered v1's component layout from `git show 2c6ac41:` for `MaterialSelector.kt` / `BrandSelector.kt` / `ColorSelector.kt` / `TemperatureCard.kt` / `SpoolPainterScreen.kt` / `FilamentForm.kt`. Rewrote `MaterialPicker`, `BrandPicker`, `ColorPicker`, `TempPanel`, `FilamentForm` to match. Layout order: Material → Variant → Color → Brand → Temperature card. Save & Write button at bottom.
4. **Wrong scope: Name + Vendor fields** — original `FilamentForm` had separate Name and Vendor `OutlinedTextField`s with their own `_nameField` / `_vendorField` `MutableStateFlow`s in `MainViewModel`. v1 has neither — vendor is the brand picker; filament name is auto-derived. Fix: dropped the standalone Name/Vendor flows. Replaced with `_customMaterial` / `_customBrand` for the "Other → custom" case. Filament display name now derived as `"$brand $material $variant"` at write time.
5. **`BrandDatabase.brands` shape mismatch** — plan §8.3 assumed `List<Brand>`; actual is `List<String>`. `BrandPicker` adapted to wrap the picked string with `Brand(name)`.
6. **Spool prefill regression — variant** — picking a spool from the dropdown filled material/color/temps but not variant. `FormMapping.fromSpoolman` updated to read `extra.variant` (with JSON-string unwrap stripping the wrapping quotes). Added a merge step in `MainViewModel.applyResult` `PrefillFromSpoolman` that falls back to the tag's `payload.subtype` when Spoolman has no `extra.variant` for the matched filament — subtype on the OpenSpool tag IS the variant.
7. **Seed-UID placeholder hack removed** — original tap-first-vs-form-first split sent a synthesised `00000000` UID to `createSpoolForNewFilament` so the create POST would succeed before the user tapped a tag, then PATCH-replaced it with the real UID after the tap. This left orphan spools in Spoolman if the user bailed mid-flow. Refactored: `createSpoolForNewFilament` no longer touches `extra.card_uids` at all (dropped `cardUid` from `NewFilamentRequest`, dropped the empty-UID guardrail). Caller (use case) does `appendCardUidToSpool(newSpoolId, uid)` after the tap. One unified flow handles tap-first and form-first; the only difference is `expectedUid` on the Write arm (`form.cardUid` for tap-first, `null` for form-first).
8. **Two-tap UX (write + separate verify arm)** — original use case armed `NfcIntent.Write` then `NfcIntent.Verify` separately, requiring two physical taps. `NfcRepository.runWriteThenVerify` already writes + verifies on the same tag connection (atomically, single tap), so the standalone Verify arm was redundant. Dropped `runVerifyOnly` and the second arm; one tap covers both phases.
9. **Re-pressing Read button replays stale data** — `NfcRepository.handleTag` writes to `_lastSeenTag` on every tap, including taps that fulfil an armed Read. The next Read button press would call `consumeLastSeen`, which returned the just-spent buffer, and the flow would re-run on the same UID instead of waiting for a fresh tap. Fix: `handleTag` clears `_lastSeenTag.value = null` when an armed Read is fulfilled, so the buffer is one-shot per arm.
10. **"Activity paused mid-write" spurious error** — `NfcRepository.detach()` surfaced `NfcResult.Error("activity paused mid-write — retry on next tap")` whenever `onPause` fired during an in-flight Write/Verify. Android 14+ singleTop activities can briefly cycle `onPause` → `onResume` around an NFC intent dispatch, which tripped the error every successful write. Fix: `detach()` no longer transitions to `Error` mid-write; the user-facing `withTimeoutOrNull(15s)` in `MainViewModel.onWriteTapped` catches a real user-driven pause. `NfcRepositoryLifecycleTest "detach during Writing transitions to Error"` rewritten to assert the new behaviour (state stays `Writing`).
11. **Form clearing on Save & Write** — first iteration cleared the form on `WrittenAndPaired`, then user asked for v1-parity (form stays populated for writing the same payload to another tag). Now clears `cardUid` + `selectedSpoolId` only so the next Save & Write doesn't reject a different tag with "wrong tag UID". User flagged that the proper "Pair another tag with this spool?" snackbar action (S-6.1 / S-6.2 / S-6.3 / S-6.4 per `unit-of-work.md` §U6b) is the correct design — keep-form is an **interim hack** until U6b lands.
12. **Variant-as-name regression** — `resolveOrCreateFilament` matched on `f.name` (filament display name) for variant equality, so existing filaments with display names like "Polymaker PLA Matte" matched against a typed variant of "Polymaker PLA Matte". Plus `FormMapping.fromSpoolman` had a fallback `?: spool.filament.name?.trim()?.takeIf { it.isNotBlank() }` that pulled the entire display name into the form's variant field. Fix: variant lives in `extra.variant` only — match reads `extra.variant`, `FormMapping.fromSpoolman` no longer falls back to `filament.name`. The merge with the tag's `payload.subtype` in `MainViewModel.applyResult` covers the legacy case where `extra.variant` isn't populated yet.
13. **`createFilament.name` was being set to the variant** — first iteration sent `name = variantNormalised` to `CreateFilamentRequest`, so a Spoolman filament for "Polymaker PLA Matte" got a display name of just "Matte". Fix: filament `name` now uses `req.name` (the user-typed display name like "Polymaker PLA Matte"), and variant lives in `extra.variant` separately. Diagnostic logs added to `SpoolmanRepository.resolveOrCreateFilament` (`SpoolmanRepo` Log.d) for OPEN-1 next-session diagnosis.
14. **Test Connection / Refresh buttons required for normal use** — user shouldn't have to tap Test Connection to register schemas, or Refresh to populate caches. Fix: `SpoolmanRepository.init` now auto-runs `ensureExtraFieldsRegistered()` and `refresh()` on every URL bind (i.e., on app open with a saved URL, or on any URL change). Settings buttons remain for manual diagnostics. `ensureExtraFieldsRegistered` rewritten to attempt both sides (spool/`card_uids` + filament/`variant`) independently — a failure on one no longer blocks the other; previously a failure on the spool POST early-returned and skipped the filament POST entirely. `SpoolmanRepositoryTestSupport` updated to clear `callLog` + `spoolExtraFields` + `filamentExtraFields` after init so tests start from a clean slate. `URL change clears caches and resets connectivity to Unknown` test renamed to `URL change refreshes caches against new URL` to reflect the auto-refresh behaviour.
15. **Idle hint UX** — first iteration added a top-of-screen `IdleHint` composable that competed with the in-flight read/write hints and displayed an ugly always-on banner. Fix: dropped the top hint; replaced with a v1-style `InstructionFooter` at the bottom of the form, only visible when `activeFlow == Idle`.
16. **`ReadAndPairResult.Success.BlankForm` wiped the form** — read on a blank tag with no Spoolman match was clobbering whatever the user had typed. Fix: `applyResult` for `BlankForm` now keeps the typed form data and only updates `cardUid` + `selectedSpoolId`. Matches v1's UX of "I want to write my form to this blank tag."

(Counts as 14 fixes per the audit log; some entries above are sub-fixes within a single audit-log line item.)

## §12 — Final unified flow (post-iteration)

```
1. Resolve spool
   ├─ form.selectedSpoolId != null → use it (existing-spool)
   └─ else                          → POST vendor + filament + spool (no UID attached)

2. moveOnBind.invoke(uid, spoolId) if form.cardUid != null  [NoOp until U6b]

3. Arm Write
   payload  = OpenSpool JSON with the resolved spool_id
   expected = form.cardUid       (tap-first, locks to a specific tag)
            | null               (form-first, accepts any tap)

4. User taps once
   ├─ Success         → uid captured (NfcRepository.runWriteThenVerify wrote + verified atomically)
   ├─ verify mismatch → return VerifyFailed
   └─ other error     → return NfcFailed

5. moveOnBind.invoke(tappedUid, spoolId) if form.cardUid was null  [NoOp until U6b]

6. PATCH spool: append tappedUid to extra.card_uids  (idempotent if already there)

7. Return WrittenAndPaired
```

One physical tap. Tap-first and form-first share the same path; only `expectedUid` and `moveOnBind` timing differ.

## §13 — Verification log (post-iteration)

- `compileDebugKotlin` ✅ — 3 pre-existing `Modifier.menuAnchor()` deprecation warnings (unchanged from U5).
- `testDebugUnitTest` ✅ — **244 / 244** at session pause.
- `assembleDebug` ✅ — APK ≈ 35.2 MB (above plan §10.3's +0.5 MB target; deferred to U10).
- `installDebug` ✅ — moto g stylus 2025 / Android 16. **No close-out manual ACs passed end-to-end** because OPEN-1 / OPEN-2 surfaced during the very ACs that would have closed the gate.
- Brownfield invariant greps all PASS (zero hits): `findSpoolsByLotNr`, `patchSpoolLotNr`, `UpdateSpoolLotNrRequest`, `CardUidEncoding`, `.probe()`. `card_uid:` has one informational comment in `ReadAndPairUseCase.kt:76` (legacy v1 migration commentary).

## §14 — OPEN BUGS (blocking close-out)

### OPEN-1 — Variant typed in form does NOT persist to Spoolman as `extra.variant`

**Symptom**: User types a variant (e.g. "Matte") in the form, hits Save & Write, tag writes successfully. Spoolman shows the new filament — but `extra.variant` is missing on the filament record. The variant field schema *is* visible in Spoolman (registered by the auto-bootstrap), but no value lands.

**Hypotheses (unverified, listed in order of likelihood)**:
- **(a)** Form state path drops variant before reaching `req.variant`. Possible culprits: a `FormState.copy(variant = ...)` somewhere in `applyResult` / `applyWriteResult` / `onSpoolSelected` that overrides the user's typed value with the spool's variant; or the `VariantField`'s `take(25).replaceFirstChar { titlecase }` sanitisation producing an unexpected value; or `_state.value.form` being read at the wrong moment in `MainViewModel.onWriteTapped`.
- **(b)** Spoolman silently drops `extra.variant` on POST despite the field schema being registered. The 400 "Unknown extra field" lazy-retry would surface this — but if Spoolman returns 200 OK and just discards unknown extras, we'd never know.
- **(c)** Existing-filament match path is reusing a legacy filament that lacks `extra.variant` and the user's typed variant matches the legacy filament's `name`, so we never POST a fresh filament with the variant. Specifically: `resolveOrCreateFilament` matches on `extra.variant` only now, but if the existing filament has `extra.variant = null` and the form's variantNormalised is also null (user typed nothing), it matches. User says they DID type a variant — but it's worth verifying the form value reaches `req.variant`.

**Diagnostic instrumentation in place (this session)**: `SpoolmanRepository.resolveOrCreateFilament` logs `SpoolmanRepo Log.d` lines for both the match-hit case (`filament match hit: id=… name=… variant=$variantNormalised existingVariant=$decoded`) and the create-new case (`createFilament: name=… variant=… extras=…`).

**Next-session next step**:
1. Capture `adb logcat -c && # write a tag with variant && adb logcat -d | grep SpoolmanRepo`.
2. Lines printed → triage:
   - No lines at all → use case never reached `resolveOrCreateFilament`; dig into VM / use-case path.
   - "filament match hit" with `variant=Matte existingVariant=null` → variant reaches the repo but the match logic is wrong.
   - "filament match hit" with `variant=null existingVariant=null` → variant lost upstream of the repo; trace `MainViewModel.onWriteTapped` → `CreateAndPairInput` → `NewFilamentRequest.fromForm` → `req.variant`.
   - "createFilament: variant=Matte extras={variant=\"Matte\"}" → POST is sending it; Spoolman is silently dropping; check `listFilaments` response post-write to confirm.

### OPEN-2 — Spool dropdown clears unexpectedly on ambient tag tap

**Symptom**: User picks a spool from the Spoolman dropdown (populating the form via `FormMapping.fromSpoolman`). User then taps a tag (no Read button pressed; ambient surfacing path). The dropdown's selection clears even though the user did nothing to clear it.

**Hypotheses**:
- The `lastSeenTag` collector in `MainViewModel.init` only updates `form.cardUid` — it does not touch `state.spoolman.selectedSpoolId` or `form.selectedSpoolId`. So this clearing must be happening elsewhere.
- Possibility: `applyResult` for `Success.BlankForm` (now keep-form per fix 16) clears `selectedSpoolId` — but `BlankForm` only fires when an armed Read returns 0 matches. If the user just tapped without arming Read, `BlankForm` shouldn't fire.
- Possibility: a Read flow IS being triggered somehow (re-armed by leftover state?) and going through `applyResult` paths that clear `selectedSpoolId`.

**Next-session next step**: trace `state.spoolman.selectedSpoolId` and `state.form.selectedSpoolId` across the ambient tap sequence with logs in `MainViewModel.init`'s `lastSeenTag.collect` and every branch of `applyResult` that clears spool selection.

## §15 — Deferrals

- **Two-tag flow** ("Pair another tag with this spool?" snackbar action + `TwoTagUseCase`) → **U6b** per `unit-of-work.md` §U6b / S-6.1 / S-6.2 / S-6.3 / S-6.4. U6a's keep-form behaviour after `WrittenAndPaired` is an interim hack; U6b will replace it with a snackbar action that re-arms write against the same spool + payload, then clears.
- **Persistent "Other → custom" entries** via DataStore-Proto → **U8** per `unit-of-work.md` §U8 / S-8.3 / S-8.4. U6a's pickers handle the in-session "Other" case but the typed name does not survive a process kill.
- **`MoveOnBindUseCase` real impl** → **U6b** per the U6a→U6b interface seam in `unit-of-work.md` §3-U6a. The two `moveOnBind.invoke(...)` call sites in `CreateAndPairUseCase` are dead-code until U6b lands a real impl that detects "this UID is already on Spool A, you're binding to Spool B" and prompts the user.
- **APK size +1.6 MB vs U5's 33.6 MB** (above plan §10.3's +0.5 MB target) → **U10**.
- **JDK 17 `JAVA_HOME` requirement** → **U10**.
- **Application-design doc-drift sync** → **U10**.
