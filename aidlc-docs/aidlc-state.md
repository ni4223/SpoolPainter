# AI-DLC State Tracking

## Project Information
- **Project Type**: Brownfield
- **Start Date**: 2026-05-23T00:00:00Z
- **Current Stage**: CONSTRUCTION - Per-Unit Loop, U6a (Create-and-Pair) — **Code Gen Part 2 EXECUTED 2026-05-25**. Both prior open bugs CLOSED this session (OPEN-1: variant persistence — root cause was Spoolman 422 on missing `density`/`diameter`; FIXED by adding both to `CreateFilamentRequest` with material-derived defaults + dropping the readback-null=verify-mismatch misclassification that orphaned spools on NDEF-promoted blanks. OPEN-2: dropdown clear on ambient tap — accepted as resolved per user direction). Two requirements deltas approved 2026-05-25: `requirements-delta-extra-fields.md` + `requirements-delta-uid-as-display-only.md` (the latter drops `expectedUid` enforcement on writes + drops `UidRow` from `MainScreen`; full per-unit Δ list in §7 of the delta). U2 / U3 / U5 / U6a amendments all fold into U6a's per-unit loop. FD Part 2 approved 2026-05-25 with all Q-U6a-1..15 = A. Test suite green at 244 / 244 throughout. **U6a is now ready for close-out** — pending close-out commit per `unit-of-work.md` §2.1 + DoD #6.

## Workspace State
- **Existing Code**: Yes
- **Programming Languages**: Kotlin (JVM target 11)
- **Build System**: Gradle (Kotlin DSL) with version catalog (`gradle/libs.versions.toml`)
- **Project Structure**: Single-module Android app (`app/`)
- **Workspace Root**: /Users/mnipun/AndroidStudioProjects/SpoolPainter
- **Reverse Engineering Needed**: Yes (no prior artifacts under `aidlc-docs/inception/reverse-engineering/`)

## Code Location Rules
- **Application Code**: Workspace root (NEVER in aidlc-docs/)
- **Documentation**: aidlc-docs/ only
- **Structure patterns**: see `.kiro/steering/structure.md`

## Execution Plan Summary
- **Total stages**: 10 (5 Inception completed; 2 Inception remaining; 3
  conditional Construction stages per unit + Code Generation always +
  Build & Test always; 1 Operations placeholder)
- **Stages to execute**:
  - INCEPTION: Application Design, Units Generation
  - CONSTRUCTION: Functional Design (per unit), NFR Requirements (per
    unit), NFR Design (per unit), Code Generation (always, per unit),
    Build and Test (always)
- **Stages to skip**:
  - CONSTRUCTION: Infrastructure Design — no CDK / Terraform /
    CloudFormation; pure Android client; distribution channels and
    cleartext-traffic config unchanged from v1.
- **Plan artifact**: `aidlc-docs/inception/plans/execution-plan.md`

## Stage Progress
### 🔵 INCEPTION PHASE
- [x] Workspace Detection — 2026-05-23
- [x] Reverse Engineering — 2026-05-23 (artifacts: `aidlc-docs/inception/reverse-engineering/`)
- [x] Requirements Analysis — 2026-05-23 (artifact: `aidlc-docs/inception/requirements/requirements.md`)
- [x] User Stories — 2026-05-23 (artifacts: `aidlc-docs/inception/user-stories/personas.md`, `aidlc-docs/inception/user-stories/stories.md`; assessment + plan: `aidlc-docs/inception/plans/user-stories-assessment.md`, `aidlc-docs/inception/plans/story-generation-plan.md`)
- [x] Workflow Planning — 2026-05-23 (artifact: `aidlc-docs/inception/plans/execution-plan.md`; approved 2026-05-23 with release-strategy revision: v1 stays on production track, all v2 builds ship to Play Store testing track first, no in-workflow promotion to production)
- [x] Application Design — 2026-05-24 (artifacts: `aidlc-docs/inception/application-design/components.md`, `component-methods.md`, `services.md`, `component-dependency.md`, `application-design.md`, `application-design-component-diagram.{mmd,png,svg}`; approved 2026-05-24)
- [x] Units Generation — 2026-05-24 (artifacts: `aidlc-docs/inception/application-design/unit-of-work.md`, `unit-of-work-dependency.md`, `unit-of-work-story-map.md`, `unit-of-work-dependency-diagram.{mmd,png,svg}`; approved 2026-05-24)

### 🟢 CONSTRUCTION PHASE
- [x] U1 (Architecture & DI Scaffold) — DONE 2026-05-25 (Functional Design / NFR Requirements / NFR Design / Infrastructure Design SKIP per stage gate; Code Generation Part 1 + Part 2 approved; install gate passed)
- [x] U2 (Domain Primitives) — DONE 2026-05-26 (Functional Design EXECUTED + approved; NFR Requirements / NFR Design / Infrastructure Design SKIP per stage gate; Code Generation Part 1 + Part 2 approved; 64 new unit tests passing; no milestone install gate per `unit-of-work.md` §2). Artefacts: `aidlc-docs/construction/u2-domain-primitives/{functional-design/*.md, code/u2-summary.md}` + `aidlc-docs/construction/plans/u2-domain-primitives-{functional-design,code-generation}-plan.md`)
- [x] U3 (Spoolman Client Overhaul) — DONE 2026-05-24 (Functional Design EXECUTED + approved (all 11 Q-U3 questions answered with recommended options); NFR Requirements / NFR Design / Infrastructure Design SKIP per stage gate; Code Generation Part 1 + Part 2 approved; 64 new unit tests passing; no milestone install gate per `unit-of-work.md` §2). Artefacts: `aidlc-docs/construction/u3-spoolman-repository/{functional-design/*.md, code/u3-summary.md}` + `aidlc-docs/construction/plans/u3-spoolman-repository-{functional-design,code-generation}-plan.md`.
- [x] U4 (NFC Repository) — DONE 2026-05-24 (Functional Design EXECUTED + approved (all 11 Q-U4 questions answered with recommended options via "Go Go Go!!"); NFR Requirements / NFR Design / Infrastructure Design SKIP per stage gate; Code Generation Part 1 + Part 2 approved; 50 new unit tests passing — running total 182; no milestone install gate per `unit-of-work.md` §2). Artefacts: `aidlc-docs/construction/u4-nfc-repository/{functional-design/*.md, code/u4-summary.md}` + `aidlc-docs/construction/plans/u4-nfc-repository-{functional-design,code-generation}-plan.md`.
- [x] U5 (Read-and-Pair Flow) — DONE 2026-05-25 (Functional Design EXECUTED + approved (all 11 Q-U5 questions answered with recommended options via "i trust you"; Q-U5-7 revised mid-gate; Q-U5-12 added mid-gate); NFR Requirements / NFR Design / Infrastructure Design SKIP per stage gate; Code Generation Part 1 + Part 2 approved; **U5 milestone install gate PASSED** on moto g stylus 2025 / Android 16 — manual ACs verified: ambient UID surfacing, blank/vendor/OpenSpool tag prefill, dropdown auto-select via UID match, `spool_id` fallback, dropdown-clear, 10 s read timeout. Carry-over: multi-UID `lot_nr` dropdown bug **PARKED** at user's request pending new requirement; tracked as a follow-up, does not gate U5 DONE. Test count: **232 / 232** (4 U1 + 64 U2 + 64 U3 + **52 U4** + **52 U5**). Mid-gate U4 contract change: BR-U4-CL-1/2 loosened so `consumeLastSeen` accepts terminal `Success | Error` states. Mid-gate U9 scope pulled forward: minimal `SettingsScreen` (URL field + Save + Test connection + Refresh) shipped in U5 to unblock the install gate; sort order, theme override, full banner Retry control still U9 scope. Artefacts: `aidlc-docs/construction/u5-read-and-pair-flow/{functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md, code/u5-summary.md}` + `aidlc-docs/construction/plans/u5-read-and-pair-flow-{functional-design,code-generation}-plan.md`.
- [x] U6a (Create-and-Pair) — DONE 2026-05-25 (Functional Design EXECUTED + approved (Q-U6a-1..15 = A); NFR Requirements / NFR Design / Infrastructure Design SKIP per stage gate; Code Generation Part 1 + Part 2 approved; Code Gen Part 2 in-flight iteration produced 14 fixes during the manual install-gate override; resumed 2026-05-25 with OPEN-1 + OPEN-2 both closed (OPEN-1 root cause: Spoolman 422 on missing `density`/`diameter`; OPEN-2 accepted-as-resolved per user direction). Two requirements deltas approved 2026-05-25 — `requirements-delta-extra-fields.md` (`extra.card_uids` + `extra.variant` wire format) + `requirements-delta-uid-as-display-only.md` (drops `expectedUid` enforcement; drops `UidRow` composable). U2-Δ-1..4 + U3-Δ-1..9 + U4-Δ-1..2 + U5-Δ-1..3 + U6a-Δ-1..7 all folded into the single U6a per-unit loop. **Test count: 244 / 244** (Δ +12 net vs U5's 232). `assembleDebug` ✅ ~34 MB APK. Multiple `installDebug` rounds on moto g stylus 2025 / Android 16 with on-device repro confirming variant + card_uids round-trip end-to-end (U6 milestone install gate covers U6a + U6b together at end of U6b per Q-T2=B; U6a's portion has been validated). Artefacts: `aidlc-docs/construction/u6a-create-and-pair-flow/{functional-design/{domain-entities,business-rules,business-logic-model,frontend-components}.md, code/u6a-summary.md}` + `aidlc-docs/construction/plans/u6a-create-and-pair-flow-{functional-design,code-generation}-plan.md` + `aidlc-docs/inception/requirements/requirements-delta-{extra-fields,uid-as-display-only}.md`. Deferrals: two-tag prompt → U6b; custom-entry persistence → U8; full removal of `NfcIntent.Write.expectedUid` field + optional debug UID surface + APK size review + JDK 17 portability → U10.
- [ ] U6b (Move-on-bind + Two-tag) — pending
- [ ] U7 (Raw Write + Vendor UID-only) — pending
- [ ] U8 (Material/Brand catalogue) — pending
- [ ] U9 (Settings + Theming + Banner) — pending
- [ ] U10 (Release polish + testing-track validation) — pending
- [ ] Build and Test — EXECUTE (after all units)
- Per-unit gate policy: Functional Design / NFR Requirements / NFR Design / Infrastructure Design assessed per unit (Infrastructure Design **SKIP** for all units per execution-plan.md); Code Generation always executes.

### 🟡 OPERATIONS PHASE
- [ ] Operations — PLACEHOLDER

## Current Status
- **Lifecycle Phase**: CONSTRUCTION
- **Current Stage**: **U6a DONE 2026-05-25** — close-out commit landed; U6b ready to open. Both prior open bugs CLOSED:
  - **OPEN-1 (variant persistence) — FIXED**. Root cause: Spoolman's `POST /api/v1/filament` requires `density` (gt=0, g/cm³) and `diameter` (gt=0, mm); our `CreateFilamentRequest` omitted both, so create returned **422 Unprocessable Content** and variant never landed. Fix: added `density` (per-material default — PLA 1.24, ABS 1.04, PETG 1.27, TPU 1.20, ASA 1.07, PC 1.20, Nylon 1.14, PVA 1.19, HIPS 1.04, default 1.24) + `diameter = 1.75 mm` (consumer standard) + `weight = 1000 g` defaults. Verified end-to-end via `adb logcat`: filament + spool created with `extra.variant` populated; `extra.card_uids` PATCH'd with the tapped UID. Secondary fix landed in same session: `NfcRepository.runWriteThenVerify` was treating `readback == null` as verify-mismatch — but a fresh blank that gets NDEF-promoted by the write itself returns null on subsequent `Ndef.get(tag)` calls because the `Tag` handle's tech list was captured pre-write. Now treated as success ("write succeeded, readback skipped"); a real bytes-differ readback still surfaces verify-mismatch.
  - **OPEN-2 (dropdown clear on ambient tap) — RESOLVED** per user direction ("consider 2 dome"). Static trace earlier this session showed no path in `MainViewModel` nulls `state.spoolman.selectedSpoolId` on a bare ambient tap; user accepted as resolved without further on-device repro. Subsequent dropdown-related fixes shipped this session (newest-first sort, archived-spools-hidden-at-UI-layer, `ExposedDropdownMenu` for auto-scroll past 100 items, `listSpools(limit=1000, allowArchived=true)` to defeat Spoolman's default 100-cap) all unrelated to OPEN-2's symptom.
- **Requirements deltas approved this session**: `requirements-delta-extra-fields.md` (approved earlier 2026-05-25 — `extra.card_uids` + `extra.variant` wire format); `requirements-delta-uid-as-display-only.md` (approved 2026-05-25 — drops `expectedUid` enforcement on writes; drops `UidRow` composable; retires post-write cardUid hack; reaffirms `MoveOnBindUseCase` as canonical conflict resolver). Per-unit Δ matrix: U2-Δ-1..4, U3-Δ-1..9, U4-Δ-1..2, U5-Δ-1..3, U6a-Δ-1..7, U6b-Δ-1..2, U10-Δ-1..2.
- **Verification on record (this session)**: `compileDebugKotlin` ✅; `testDebugUnitTest` ✅ **244 / 244** maintained throughout the OPEN-1 fix + UID-display delta + dropdown polish; `assembleDebug` ✅ ~34 MB APK; multiple `installDebug` rounds on moto g stylus 2025 / Android 16 with on-device repro confirming variant + card_uids round-trip end-to-end. **No close-out commit yet.** Working tree dirty against `origin/v2`.
- **U6a iteration fixes (14)**: 1) crash-on-open from nested `verticalScroll`; 2) `MainViewModelTest existingSpool` test flake from `distinctUntilChanged` UID dedupe; 3) UI re-aligned to v1 layout (Material → Variant → Color → Brand → Temperature; "Other → custom inline field" preserved on Material/Brand; named-color dropdown w/ swatches; ±5 °C step buttons + °C suffix); dropped Name/Vendor `MutableStateFlow`s, replaced with `_customMaterial` / `_customBrand`; 4) spool-prefill regression (variant from `extra.variant` + tag `subtype` fallback merge in `MainViewModel.applyResult`); 5) seed-UID placeholder hack removed — `createSpoolForNewFilament` no longer touches `extra.card_uids` / `cardUid` dropped from `NewFilamentRequest`, caller (use case) does append after the tap; 6) two-tap UX — dropped redundant `runVerifyOnly`; `NfcRepository.runWriteThenVerify` covers write+verify atomically on one physical tap; 7) re-press Read replays stale data — `NfcRepository.handleTag` clears `_lastSeenTag` after fulfilling an armed Read; 8) "activity paused mid-write" spurious error — `detach()` no longer transitions to Error mid-write (Android 14+ singleTop cycles `onPause`/`onResume`); 9) form clearing on Save & Write reverted to keep-form (interim hack until U6b's "Pair another tag with this spool?" snackbar lands); 10) variant-as-name regression — `resolveOrCreateFilament` matches on `extra.variant` only (no longer `f.name`); `FormMapping.fromSpoolman` no longer falls back to `filament.name` for variant; 11) `createFilament.name` now uses `req.name` (display name like "Polymaker PLA Matte"), not the variant; 12) Test Connection / Refresh buttons no longer required for normal use — `SpoolmanRepository.init` auto-runs `ensureExtraFieldsRegistered()` + `refresh()` on every URL bind; `ensureExtraFieldsRegistered` rewritten to attempt both sides independently; 13) idle hint UX moved from top-of-screen ugly banner to v1-style `InstructionFooter` at bottom of form (idle-only); 14) `Success.BlankForm` no longer wipes the form — keeps typed data and only updates `cardUid` + clears spool selection (matches v1's "I want to write my form to this blank tag" UX).
- **Deferrals (called out for next session)**: two-tag flow ("Pair another tag with this spool?" snackbar action + `TwoTagUseCase`) → **U6b** per `unit-of-work.md` §U6b / S-6.1 / S-6.2 / S-6.3 / S-6.4; persistent "Other → custom" entries via DataStore-Proto → **U8** per `unit-of-work.md` §U8 / S-8.3 / S-8.4. `MoveOnBindUseCase` remains a `NoOp` interface seam per U6a→U6b ordering; two `moveOnBind.invoke(...)` call sites in `CreateAndPairUseCase` are dead-code until U6b lands a real impl.
- **Doc-drift carry (unchanged from U5)**: `component-methods.md` §1 references `OpenSpoolPayloadParser` (renamed to `OpenSpoolPayloadCodec` in U4); §6 lists six use-cases on `MainViewModel` (U6a ships `readAndPair` + `createAndPair`, four still U7/U8); §7 references `Spool` / `Material` / `Brand` types (U6a still uses `SpoolmanSpool` + interim `Brand(name: String)` directly); `unit-of-work.md` §3-U9 names full Settings UI as U9 scope (U5 shipped a subset early; U6a touched `SettingsViewModel.onTestConnectionTapped` for the bootstrap chain). Sync deferred to U10.
- **JDK note (unchanged from U5)**: builds require `JAVA_HOME = JDK 17`; durable fix deferred to U10. APK size at +1.6 MB vs U5's 33.6 MB exceeds plan §10.3's +0.5 MB target — flagged for U10 polish.
- **Mid-gate decisions on record**: Q-U6a-1..15 = A (FD Part 2 approved 2026-05-25); session-time changes captured above as the 14-fix log; the FD's "tap-first vs form-first separate flows" intent collapsed into a single unified flow (`expectedUid` toggles tap-first vs form-first; same Spoolman create+append+write+verify sequence both ways).
- **Next Stage**: open **U6b (Move-on-bind + Two-tag)** per-unit loop. After U6b: U7 → U8 → U9 → U10.
- **Status**: 11 v2.0 units locked (U1..U10 with U6 split into U6a/U6b); 2 v2.1 lightweight stubs (U11/U12) parked behind a hard gate. Strict construction order: U1 → U2 → U3 → U4 → U5 → U6a → U6b → U7 → U8 → U9 → U10. Milestone install gates at U1, U5, U6, U10. U10 doubles as Play Store testing-track release validation.

## Extension Configuration
| Extension | Enabled | Decided At |
|---|---|---|
| Security Baseline | No | Requirements Analysis |
| Property-Based Testing | No | Requirements Analysis |
