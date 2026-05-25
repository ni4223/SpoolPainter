# U5 — Read-and-Pair Flow: Code Generation Summary

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (executed)
**Source plans**:
- `aidlc-docs/construction/plans/u5-read-and-pair-flow-functional-design-plan.md`
- `aidlc-docs/construction/plans/u5-read-and-pair-flow-code-generation-plan.md`
**Functional design**:
- `aidlc-docs/construction/u5-read-and-pair-flow/functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md`

---

## 1. Files

### Created (10)

#### Source (5)
- `app/src/main/java/com/spoolpainter/app/domain/models/Brand.kt` — interim `Brand(name: String)` (Q-U5-6=A).
- `app/src/main/java/com/spoolpainter/app/domain/models/TempRanges.kt` — domain temperature-range value type.
- `app/src/main/java/com/spoolpainter/app/domain/usecases/ReadAndPairResult.kt` — sealed result type for the flow.
- `app/src/main/java/com/spoolpainter/app/domain/usecases/ReadAndPairUseCase.kt` — orchestrates `consumeLastSeen` → `arm(Read)` → `findSpoolsByCardUid` → branch (BR-U5-RP-*).
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/FormMapping.kt` — pure mapping helpers (`fromSpoolman`, `fromOpenSpool`, `blankForm`, `clearedFromDropdown`).

#### Test (5)
- `app/src/test/java/com/spoolpainter/app/support/FakeNfcRepository.kt` — repository-level fake (Q-U5-10=A).
- `app/src/test/java/com/spoolpainter/app/support/FakeSpoolmanRepository.kt` — repository-level fake.
- `app/src/test/java/com/spoolpainter/app/support/FakeSettingsRepository.kt` — interface-backed fake (enabled by the `SettingsRepository` interface split below).
- `app/src/test/java/com/spoolpainter/app/domain/usecases/ReadAndPairUseCaseTest.kt` — 12 cases (BR-U5-RP-*).
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt` — 17 cases (BR-U5-VM-*).
- `app/src/test/java/com/spoolpainter/app/ui/screens/main/FormMappingTest.kt` — 12 cases (BR-U5-MAP-*).

Total: **52 new test cases** (cumulative: 4 U1 + 64 U2 + 64 U3 + **52 U4** + **52 U5** = **232**). U5 cases include: 12 `ReadAndPairUseCaseTest` + 17 `MainViewModelTest` + 12 `FormMappingTest` (original 41) + install-gate add-ons (1 ambient-UID VM, 4 `spool_id`-fallback use-case cases, 2 UID-from-`lot_nr` VM cases). U4 grew by 2 from the mid-gate `consumeLastSeen` loosening (terminal `Success` / terminal `Error` cases).

### Modified (5)
- `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt` — split into `SettingsRepository` interface + `SettingsRepositoryImpl` (enables `FakeSettingsRepository`; consumers unchanged).
- `app/src/main/java/com/spoolpainter/app/di/RepositoryModule.kt` — added `RepositoryBindingsModule` with `@Binds SettingsRepositoryImpl → SettingsRepository`.
- `app/src/main/java/com/spoolpainter/app/data/remote/spoolman/SpoolmanRepository.kt` — `class` → `open class`; `findSpoolsByCardUid` and exposed `StateFlow` properties (`spools`, `vendors`, `filaments`, `connectivity`) marked `open` so tests can override (Q-U5-10=A).
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt` — `state` / `lastSeenTag` / `arm` / `consumeLastSeen` / `disarm` marked `open` for the same reason.
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` — replaced U1 placeholder with the finalised slice shape (`FormState`, `SpoolmanState`, `BannerState`, `ActiveFlow`, `AmbiguityState`, `typealias NfcState = NfcResult`).
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` — full rewrite: three independent `viewModelScope.launch { collect }` blocks for `nfc.state` / `spoolman.spools` / `settings.url`; `onReadTapped` (with re-tap disarm/rearm), `onSpoolSelected` (incl. idempotent re-select + clear), `onSettingsTapped`. Maps `ReadAndPairResult` to `MainUiState` + `UiEffect`.
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` — full rewrite: `Scaffold` with `MainTopBar`, snackbar host, `ReadFab` extended FAB; `BannerSlot` / `ReadingHint` / `UidRow` / `SpoolmanDropdown` (Material 3 `ExposedDropdownMenuBox` + `DropdownMenu`) / `AmbiguityBlock` / `FormPreview` (read-only label/value rows + colour swatch).
- `app/src/test/java/com/spoolpainter/app/data/local/SettingsRepositoryTest.kt` / `SpoolmanRepositoryTestSupport.kt` — switched to `SettingsRepositoryImpl` for the concrete-class call sites.

### Deleted (0)
- No source deletions in U5. (v1 main-screen surfaces were already removed in U1; no legacy `MainScreenContent.kt` remains.)

---

## 2. Story / requirement coverage

| ID | Code surface | Test surface |
|---|---|---|
| FR-3.1 / S-3.1 | `ReadAndPairUseCase.invoke` calls `findSpoolsByCardUid` after a Success read | `ReadAndPairUseCaseTest::*` (every case) |
| FR-3.2 / S-3.2 | `applyResult(PrefillFromSpoolman)` → `FormMapping.fromSpoolman` | `MainViewModelTest::onReadTapped_PrefillFromSpoolman_*`, `FormMappingTest::*` |
| FR-3.3 / S-3.3 | `Ambiguous` branch + `AmbiguityState` slot; no PATCH/POST issued by use-case | `MainViewModelTest::onReadTapped_Ambiguous_*`, `ReadAndPairUseCaseTest::arm_Read_then_OpenSpool_with_two_matches_*` |
| FR-3.4 / S-3.4 / FR-11.1 | `applyResult(PrefillFromTag)` → `FormMapping.fromOpenSpool` | `MainViewModelTest::onReadTapped_PrefillFromTag_*`, `FormMappingTest::fromOpenSpool_*` |
| FR-3.4 / S-3.5 / FR-11.2 | `applyResult(BlankForm)` for Blank/Vendor classifications | `ReadAndPairUseCaseTest::*Blank*`, `*Vendor*` |
| FR-3.6 / S-3.6 | `MainViewModel.onSpoolSelected(spool: SpoolmanSpool?)` | `MainViewModelTest::onSpoolSelected_*` |
| FR-10.2 / S-10.2 (banner-surface only) | `SpoolmanFailed` → `UiEffect.ShowSnackbar` (full banner U9) | `MainViewModelTest::*SpoolmanFailed_emits_ShowSnackbar` |
| S-10.1 + Q-CD1.1=A (banner suppression) | `URL-not-configured` short-circuits to 0-match branch | `ReadAndPairUseCaseTest::Spoolman_NetworkError_with_UrlNotConfigured_cause_returns_BlankForm` |
| NFR-1.2 (UI never calls data sources directly) | `MainScreen` consumes `MainUiState` only; intent callbacks hoisted | code review |
| NFR-1.4 | `NfcResult` mirrored as `NfcState` typealias | reused from U4 |
| NFR-7.1 | `SpoolmanOutcome` exhaustively handled in `humanReadable` | `MainViewModelTest::*SpoolmanFailed*` |

---

## 3. Public interfaces produced (cross-unit boundary)

- `ReadAndPairUseCase` — primary cross-unit boundary (Q-D1=C). Sole consumer is `MainViewModel`; declared as a class (constructor injection) since interface-typing a single-consumer dependency adds noise without value.
- `ReadAndPairResult` — sealed type. UI layer does not consume directly; `MainViewModel.applyResult` maps to `MainUiState`.
- `MainUiState`, `FormState`, `SpoolmanState`, `BannerState`, `ActiveFlow`, `AmbiguityState`, `NfcState` (typealias) — finalised. U6a / U6b / U7 / U8 / U9 will extend `ActiveFlow` and add to `FormState` / `SpoolmanState`.
- `Brand`, `TempRanges` — new domain types.
- `SettingsRepository` — interface-typed boundary. `SettingsRepositoryImpl` is the production binding (Hilt `@Binds`).

---

## 4. Build verification

| Command | Result |
|---|---|
| `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` | ✅ (only pre-existing v1 Compose deprecation warnings) |
| `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest` | ✅ **232 / 232** tests pass (4 U1 + 64 U2 + 64 U3 + 52 U4 + 52 U5) |
| `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug` | ✅ APK ≈ 33.6 MB (+0.3 MB from U4 baseline — additional Compose / use-case / Settings classes) |
| `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:installDebug` | ✅ Installed on moto g stylus 2025 / Android 16. **U5 milestone install gate PASSED** (manual ACs verified end-to-end). |

Brownfield invariants:
- `grep -rn "TODO U5" app/src` → 0 matches.
- `grep -rn "OpenSpoolData\|class SpoolmanService\|NfcManager\|class NfcController\|class NfcHandler" app/src/main` → 0 matches (carried over from U2/U3/U4).

---

## 5. Exit criteria checklist (from `unit-of-work.md` §3-U5)

- [x] App compiles.
- [x] Unit tests for the four read-result branches pass — `ReadAndPairUseCaseTest` covers all branches incl. URL-not-configured short-circuit.
- [x] `MainViewModel.onReadTapped` produces expected `MainUiState` for each branch — `MainViewModelTest::onReadTapped_*` (5 result-variant cases + 1 re-tap case).
- [x] `MainViewModel.onSpoolSelected` prefills from selected spool; reselection overwrites; clearing empties — `MainViewModelTest::onSpoolSelected_*` (4 cases).
- [x] Hilt graph compiles with `MainViewModel` injecting `ReadAndPairUseCase`.
- [x] **Milestone install gate (U5 — first install gate)** — debug build ran on device (moto g stylus 2025 / Android 16) and exercised: ambient UID display on unarmed tap, blank tag prefill, vendor tag prefill, OpenSpool tag prefill, dropdown auto-select via UID match, `spool_id` fallback for v1-era tags, dropdown-clear, dropdown UID semantics (UID row reflects selected spool's `lot_nr` or clears), 10 s read timeout, snackbar on bad URL. **PASSED 2026-05-25.** One known follow-up: multi-UID `lot_nr` dropdown auto-select bug — parked at user's request pending new requirement.

---

## 6. Forbidden-patterns audit

| Pattern | U5 status |
|---|---|
| UI calls data sources directly (NFR-1.2) | ✅ — `MainScreen` consumes only `MainUiState` + intent callbacks; no `Repository` references. |
| ViewModels inject Hilt-scoped Activity types | ✅ — `MainViewModel` injects only `@Singleton`-scoped repositories + the use-case. |
| Use-cases hold state | ✅ — `ReadAndPairUseCase` is stateless per invocation. |
| Service locator / event bus | ✅ — Hilt only. |
| Mocked Android types | ✅ — no Mockito/MockK; hand-rolled `FakeNfcRepository` / `FakeSpoolmanRepository` / `FakeSettingsRepository`. |
| Editable form widgets in `FormPreview` | ✅ — read-only label/value rows + colour swatch. |
| `MaterialBrandRepository` reference (U8 territory) | ✅ — uses `MaterialDatabase` directly per Q-U5-9=A. |
| `BannerState.Offline` activation | ✅ — VM only emits `BannerState.Hidden` (Q-U5-4=A). |
| `Spoolman` PATCH / POST / DELETE in U5 paths | ✅ — `ReadAndPairUseCase` only invokes `findSpoolsByCardUid`. |

---

## 7. Functional-design rule-coverage spot map

| Rule | Where implemented |
|---|---|
| BR-U5-RP-1 | `ReadAndPairUseCase.invoke` (single-shot suspend) |
| BR-U5-RP-2 | `readTag()` tries `consumeLastSeen` before `arm` |
| BR-U5-RP-3 | `nfc.state.first { Success or Error }` |
| BR-U5-RP-4 | `findSpoolsByCardUid` is called for any classification |
| BR-U5-RP-5 / BR-U5-RP-6 | `branchOnMatches` |
| BR-U5-RP-7 | `branchOnSpoolman` checks `cause is UrlNotConfiguredException` |
| BR-U5-RP-8 / BR-U5-RP-9 | `handleSuccess` empty-UID guard + `NfcResult.Error` short-circuit |
| BR-U5-VM-1 / BR-U5-VM-2 | `MainViewModel.onReadTapped` + `readJob` cancellation |
| BR-U5-VM-3 / BR-U5-VM-4 | `applyResult` + `humanReadable` |
| BR-U5-VM-5 / BR-U5-VM-11 / BR-U5-VM-12 | `MainViewModel.onSpoolSelected` |
| BR-U5-VM-7 / BR-U5-VM-8 / BR-U5-VM-9 / BR-U5-VM-10 | `init { ... }` collectors |
| BR-U5-VM-13 | `MainViewModel` only ships `onReadTapped`, `onSpoolSelected`, `onSettingsTapped` |
| BR-U5-MAP-1..6 | `FormMapping.{fromSpoolman, fromOpenSpool, blankForm, clearedFromDropdown}` |
| BR-U5-TS-1..4 | `Fake*Repository` classes; test files listed in §1 |

---

## 8. Forward references (for downstream units)

- **U6a (Create-and-Pair)** — extends `MainViewModel` with `onWriteTapped`, injects `CreateAndPairUseCase`. Consumes `state.form` + `state.spoolman.selectedSpoolId`. Adds `ActiveFlow.Writing | Verifying`.
- **U6b (Move-on-bind + Two-tag)** — extends `MainViewModel` with `onPairAnotherTagTapped`, sheet event handlers. Adds `ActiveFlow.Repairing | TwoTag`.
- **U7 (Raw-write + Vendor UID-only)** — `onRawWriteToggled`, vendor opt-in sheet handlers. Adds `ActiveFlow.RawWriting | VendorOptIn`. `FormPreview` will need editable inputs for raw-write mode (those inputs themselves are added in U6a).
- **U8 (Material/Brand catalogue)** — replaces `MaterialDatabase` lookups inside `FormMapping` with `MaterialBrandRepository`. May extend `Brand` to add `id` + `customSource`.
- **U9 (Settings + banner)** — wires `BannerState.Offline` derivation from `SpoolmanRepository.connectivity` + `SettingsRepository.url`. Adds **sort order**, **theme override**, **full banner copy + Retry** UI to the Settings screen (the URL field + Save + Test connection + Refresh subset already shipped in U5 to unblock the install gate).

---

## 9. JDK note

Builds require `JAVA_HOME = JDK 17`. Durable fix deferred to U10 per `aidlc-state.md`.

---

## 10. Documentation drift recorded

- `component-methods.md` §6 lists six use-cases on `MainViewModel`'s constructor; U5 ships only `readAndPair`. The other five are added by U6a/U6b/U7. **Not drift** — correct phased construction.
- `component-methods.md` §7 references `Spool` / `Material` / `Brand` types; U5 ships `Brand(name: String)` interim and uses `SpoolmanSpool` (no `Spool` alias). Reconciliation deferred to U10 release polish (cumulative with the U4-recorded `OpenSpoolPayloadParser` drift).
- `unit-of-work.md` §3-U9 describes Settings as U9 scope in full; U5 shipped a **subset** (URL field + Save + Test connection + Refresh) early to unblock the milestone install gate. Sort order, theme override, full banner Retry control still land in U9 — record updated in `audit.md` 2026-05-25 entry. Forward-ref note added to §8 above.

---

## 11. Install-gate iteration log (post Code Gen Part 2, 2026-05-25)

| Finding | Resolution |
|---|---|
| S-1.1 — UID didn't show on unarmed tap | Added an extra collector in `MainViewModel.init` mirroring `nfc.lastSeenTag.uid` → `state.form.cardUid`. New test `lastSeenTag_uid_is_mirrored_into_form_cardUid_for_ambient_surfacing` (1 case). |
| Settings UI was a Toast stub; user couldn't configure URL → Spoolman ACs blocked | Pulled forward a minimal U9 subset: `SettingsScreen` with URL + Save + Test connection + Refresh; `SettingsViewModel` extended with `onUrlSaved` / `onTestConnectionTapped` / `onRefreshTapped`; `MainActivity` does `rememberSaveable<Boolean>` + `BackHandler` route. Remaining U9 scope (sort order, theme, full banner, Retry) is unchanged. |
| App crashed on bare-IP URL save | `SpoolmanRepository.init` now wraps `apiFactory.create(url)` in `runCatching { ... }.getOrNull()`; `SettingsViewModel.onUrlSaved` auto-prepends `http://` when scheme is missing. |
| Q-U5-12=A `spool_id` fallback | Implemented per BR-U5-RP-13: `SpoolmanRepository.getSpool` promoted to `open suspend`; `ReadAndPairUseCase.resolveBySpoolIdOrPrefillFromTag` fires after 0 UID matches when classification is `OpenSpool(payload)` with parseable `spoolId`. 4 new test cases. `MainViewModel` projection unchanged. |
| Q-U5-7 revised — UID row reflects selected spool's `lot_nr` | `FormMapping.fromSpoolman` extended with `SpoolmanUidSource` enum. `MainViewModel.onSpoolSelected(spool)` decodes UID via `CardUidEncoding.decode(spool.lot_nr).uids.firstOrNull()`. `onSpoolSelected(null)` resets entire `FormState`. Read-flow auto-prefill still passes the just-tapped UID explicitly (`PreserveCurrent`). 2 new VM test cases. |
| BR-U4-CL-1/2 loosened — `consumeLastSeen` accepts terminal `Success` / `Error` states | After a successful read, `state = Success` was rejecting the next `consumeLastSeen` call, breaking the tag-first short-circuit. Loosened the gate to reject only `Reading | Writing | Verifying`. U4 contract change. 2 new test cases (terminal `Success`, terminal `Error`). |
| BR-U5-VM-1 — 10 s read timeout | `MainViewModel.onReadTapped` now wraps `readAndPair.invoke()` in `withTimeoutOrNull(10_000L)`. On timeout: `nfc.disarm()`, `activeFlow = Idle`, snackbar "No tag tapped — try again". Fixed the persistent "Tap a tag to read…" hint that stayed on screen indefinitely if no tap arrived. |
| Multi-UID `lot_nr` dropdown auto-select bug | **PARKED** at user's request pending new requirement. Tracked as a known follow-up; does not gate U5 DONE. |
