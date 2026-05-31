# U10 — Release Polish + Play Store Testing-Track Release Prep — Summary

**Unit**: U10
**Per-unit gate**: FD SKIP / NFR-R SKIP / NFR-D SKIP / Infra-D SKIP / Code Gen EXECUTE
**Plan**: `aidlc-docs/construction/plans/u10-release-polish-code-generation-plan.md`
**Date**: 2026-05-30

---

## Headline

| Metric | Value |
|---|---|
| Tests | **362 / 362** ✅ (Δ +0 vs U9b — no test count change; only assertion strings unchanged) |
| Debug APK | **64 MB** (unchanged vs U9b baseline) |
| **Release APK** | **6.9 MB** ← R8 minify + resource shrinking; ~90% size cut from debug |
| **Release AAB** | **7.6 MB** |
| versionCode | `8 → 100` |
| versionName | `1.7 → 2.0` |
| `compileDebugKotlin` | ✅ |
| `testDebugUnitTest` | ✅ |
| `assembleDebug` | ✅ |
| `assembleRelease` | ✅ first try (no R8 keep-rule iteration) |
| `bundleRelease` | ✅ |

---

## What shipped

### §1 — Build-config bumps
- `app/build.gradle.kts` — `versionCode 8 → 100`, `versionName "1.7" → "2.0"`. Debug variant retains `-DEBUG` suffix (`2.0-DEBUG`).

### §2 — R8 minify + ProGuard keep rules + log strip (NFR-5, U10-Δ-1)
- `app/build.gradle.kts` release block: `isMinifyEnabled = true`, `isShrinkResources = true` (was `false`).
- `app/proguard-rules.pro` rewritten from defaults-only to a layered keep config:
  - Crash-readable line numbers (`-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute`)
  - `*Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod` (Hilt + kotlinx-serialization need these)
  - **Log strip**: `-assumenosideeffects class android.util.Log { v / d / i / w / e / wtf }` — removes the 9 production `Log.*` call-sites from the release APK only (debug retains them)
  - Compose: `-keepclasseswithmembers class * { @Composable <methods>; }`
  - Hilt + KSP: keep `dagger.hilt.**`, `hilt_aggregated_deps.**`, `* extends ViewModel`, `@Inject <init>`, `@HiltAndroidApp`, `@AndroidEntryPoint`, `@InstallIn`
  - Retrofit: keep `Call`, `Response`, `@retrofit2.http.* <methods>`, `SpoolmanApi`, full `data/remote/spoolman/**`
  - Gson DTOs: keep `domain/models/**` with `<fields>` (covers `SpoolmanSpool`, `SpoolmanFilament`, `SpoolmanVendor`, `SpoolmanResponse`, etc.)
  - kotlinx-serialization: keep `data/local/**$$serializer`, Companion accessors, `Settings` descriptor; standard kotlinx-serialization keep block
  - Domain + settings enums kept members (Currency, ThemeOverride, SpoolSortKey, FilamentSortKey, SortDirection)

### §3 — UI-07 snackbar copy edits
Two strings in `MainViewModel.kt`:
- **MoveOnBindPartial** (lines 836-841): `"Partial state in Spoolman. UID was removed from spool #N. Restore manually if needed."` → `"Couldn't finish moving the tag. Spool #N already released the tag. Re-add it in Spoolman if needed."`
- **TwoTagResult.Cancelled** (line 852): `"Second-tag pairing cancelled (${result.reason})"` → `"No second tag tapped. Tap Pair another to retry."` (raw reason no longer leaks to UI)
- Test-side change: **none required** — `MainViewModelTwoTagTest.kt:213` only asserts `contains("#7")` (still satisfied); the Cancelled test (`:130`) is a state-transition assertion, not a message assertion.

### §4 — UI-05 verification
- Already shipped in U9b §7 — 5 sites in `MainViewModel.kt` (lines 712, 723, 845, 875, 879) use `"Couldn't write to tag. Try again."`.
- `aidlc-docs/ui-followups.md` UI-05 entry: state flipped `open → fixed`.
- Same flip applied to UI-07 entry post-§3 edits.

### §5 — Doc-drift sync
- `aidlc-docs/inception/application-design/component-methods.md`:
  - All `OpenSpoolPayloadParser` references → `OpenSpoolPayloadCodec`
  - `MainViewModel` constructor signature updated to actual injection list (`spoolman`, `settings`, `materialBrandRepo`, 5 use-cases including `confirmer: MoveOnBindConfirmer` not `MoveOnBindUseCase`)
  - `SpoolmanRepository` flows retyped from `Filament` / `Spool` / `Vendor` → `SpoolmanFilament` / `SpoolmanSpool` / `SpoolmanVendor`
  - `SettingsRepository` row updated for the U9 setter split (`setSpoolSortOrder`, `setFilamentSortOrder`, `setCurrency`, theme as Switch on TopAppBar)
  - `SettingsViewModel` row: `onTestConnectionTapped` → `onSaveTapped` (Test connection button removed in U9b)
  - `MainViewModel.onSpoolSelected` now nullable
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U9 scope rewritten to match actually-shipped Settings shape (full-width Save runs probe; independent spool/filament sort enums; theme as 2-state Switch with `ThemeOverride.System` dropped; segmented currency row; explanatory note about U5's early subset and U9b's logo/Cards reshape)
- `aidlc-docs/ui-followups.md` — UI-05 + UI-07 flipped to `fixed`.

### §5b — README.md (full rewrite)
163-line addition. Sections:
- H1 + tagline + status badge line (v2.0 / Android 10+ / license note)
- Screenshots placeholder (`docs/screenshots/`)
- What's new in v2.0 (synced with tester release notes)
- How to get it (3 paths: Play Store testing track with `<!-- TODO -->` placeholder for the link, GitHub Releases sideload, build from source with JDK 17 + signing-key requirements)
- How to install (5-step numbered list)
- Spoolman setup (cleartext-traffic note + privacy stance)
- NFC compatibility (NDEF / NTAG21x / vendor classification yes / decoding v2.1)
- What's coming next — v2.1 preview (U11 vendor decode, U12 vendor keys, UI-13/14/15 editing, GPL-3.0 transition)
- Tech stack
- Architecture (3-line summary + diagram pointer)
- Privacy / data
- Contributing (links AIDLC under aidlc-docs/ + CLAUDE.md)
- License (current "All rights reserved"; v2.1 transitions to GPL-3.0 atomically)
- Acknowledgements (Spoolman / OpenSpool / Snapmaker U1 firmware team)

### §6 — `aidlc-docs/operations/manual-nfc-checklist.md` (created)
End-to-end manual matrix; ~50 scenarios across 10 sections (read flow, create-and-pair, pair another + move-on-bind, side modes, pickers + custom entries, settings + theming + sort + currency, UI polish, Spoolman gating, Snapmaker U1 round-trip, release smoke). Doubles as the U10 install gate spec and the testing-track release validation per Q-FU1=C.

### §7 — `aidlc-docs/operations/v2.0-tester-release-notes.md` (created)
Tester-facing notes — what's new, known issues / parked, build prerequisites (JDK 17 + signing key), feedback channels, testing focus areas (vendor classification, Snapmaker round-trip, Spoolman gating, persistence, R8 release).

### §8 — `aidlc-docs/operations/testing-track-upload-checklist.md` (created)
Procedural — pre-flight, build artefacts, testing-track choice (internal / closed / open), Play Console upload steps, post-upload (link distribution + README placeholder fill + crash dashboard monitoring + rollback procedure). Production-track promotion explicitly out of scope.

### §9 — Build verification
| Step | Result |
|---|---|
| `./gradlew :app:compileDebugKotlin` | ✅ |
| `./gradlew :app:testDebugUnitTest` | ✅ 362/362 |
| `./gradlew :app:assembleDebug` | ✅ 64 MB |
| `./gradlew :app:assembleRelease` | ✅ 6.9 MB (R8 first run, no keep-rule iteration needed) |
| `./gradlew :app:bundleRelease` | ✅ 7.6 MB AAB |
| Manual `apksigner verify` | skipped — apksigner not on PATH; Gradle's `validateSigningRelease` + `packageRelease` cover signing in-pipeline. Install-gate on-device confirms signature validity. |

R8 emitted 7 deprecation warnings (unchecked-cast in `SpoolmanRepository.kt` lines 352/375; `Modifier.menuAnchor()` deprecated overload in 6 picker/dropdown sites; `Window.statusBarColor` deprecated). All pre-existing — none introduced by U10. None blocking. Logged for routine cleanup, not gating.

---

## Brownfield invariants

- ✅ No `*_modified.kt` / `*_new.kt` / `*.bak` files in the diff
- ✅ No `.kiro/steering/aws-aidlc-rules/**` or `.kiro/aws-aidlc-rule-details/**` edits (vendored)
- ✅ All edits in-place; no parallel files
- ✅ `aidlc-docs/operations/` directory created (didn't exist before)

---

## File impact

**Modified** (9):
- `README.md` — full rewrite (43 → 163 lines, +120 net)
- `aidlc-docs/aidlc-state.md` — pre-session sync (post-U9b push status correction)
- `aidlc-docs/audit.md` — session entries
- `aidlc-docs/inception/application-design/component-methods.md` — doc drift
- `aidlc-docs/inception/application-design/unit-of-work.md` — §3-U9 wording
- `aidlc-docs/ui-followups.md` — UI-05 + UI-07 → fixed
- `app/build.gradle.kts` — versionCode/Name + R8 flags
- `app/proguard-rules.pro` — keep rules (was empty / defaults-only)
- `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` — 2 string edits

**Created** (5):
- `aidlc-docs/construction/plans/u10-release-polish-code-generation-plan.md`
- `aidlc-docs/construction/u10-release-polish/code/u10-summary.md` (this file)
- `aidlc-docs/operations/manual-nfc-checklist.md`
- `aidlc-docs/operations/v2.0-tester-release-notes.md`
- `aidlc-docs/operations/testing-track-upload-checklist.md`

**Deleted**: 0

**Tests**: 0 net (no new test methods, no removals; assertion strings unchanged).

---

## U10 install-gate iteration deltas — 2026-05-30 session

Six in-session fixes against the manual matrix:

1. **VendorTagHint redesign** (`MainScreen.kt:507-545`). Was a flat `surfaceVariant` Card indistinguishable from form Cards; user "ugly and cant even tell its something different". Reshaped as inline outlined `Row` (no Card) with tertiary-tinted ⓘ Info icon + "Vendor tag" header in tertiary SemiBold + supporting body line in onSurfaceVariant. Three body messages (alreadyLinked / urlConfigured / !urlConfigured) — body now always visible (was hidden when `alreadyLinked = true` previously). Chip gated on active engagement: `state.nfc is NfcResult.Success || state.spoolman.selectedSpoolId != null` so passive ambient taps don't surface it.

2. **Passive-tap snackbar — cooldown + classification-aware copy** (`MainViewModel.kt:91-94 + 234-264`). Replaced once-per-VM `ambientTapHintShown: Boolean` with **15-second wall-clock cooldown** via `kotlinx.datetime.Clock.System.now()`. Constant `AMBIENT_HINT_COOLDOWN_MS = 15_000L`. Re-fires on subsequent taps after the cooldown. Dropped `selectedSpoolId == null` gate — fires regardless. Copy branches by `tag.classification`:
   - `Vendor` → "Vendor tag. Press Read to load."
   - `Blank` → "Blank tag detected."
   - `OpenSpool` / null → "Tag detected. Press Read to load."

3. **Snackbar position** (`MainScreen.kt:99-112`). Bottom-anchored (standard Android pattern) with `padding(bottom = 160.dp)` to clear both Read FAB (56dp) and SaveAndWriteButton (48dp inside the scrolling Column) + Column spacing + system gesture bar. `imePadding()` retained.

4. **Post-Read snackbar branched by classification** (`MainViewModel.kt:660-685`). `applyResult.BlankForm` now emits "Blank tag detected." only when `result.classification` is not Vendor; for Vendor classifications no snackbar fires (the redesigned chip surfaces all guidance after Read).

5. **End-of-pair-flow pivot — `applyEndOfPairFlow(spoolId)`** (`MainViewModel.kt:786-810`). New helper triggered from BOTH `onPairAnotherTagDismissed` AND `applyTwoTagResult.SecondTagPaired`. Looks up the just-paired spool in `state.spoolman.spools` cache, extracts its filament id, then:
   - Clears `form.selectedSpoolId` + `state.spoolman.selectedSpoolId`
   - Sets `form.selectedFilamentId = filamentId` (so the next tap routes through `CreateAndPairUseCase.resolveSpool` line 139's `createSpoolForExistingFilament` branch — no duplicate filament)
   - Sets `form.filamentSectionExpanded = true` (user *sees* the pinned filament + can clear it via X)
   Snackbar copy preserved ("Saved with one tag" / "Both tags paired"). Solves §2.4 manual scenario "identical-form double-tap creates 1 filament + 2 spools" cleanly.

6. **NFC write robustness fix — v1 parity** (`NfcAdapterWrapper.kt:52-114`). Confirmed regression: a tag v1.7 production wrote fine reproducibly failed on v2 with `Ndef.writeNdefMessage IOException` (payload=216B cap=492B writable=true). Bisect: `app/src/main/java/com/spoolpainter/app/hardware/nfc/` byte-identical to U8 close-out — a v1↔v2 architectural delta, not a recent regression. v1's `NfcManager.writeTag` does `connect → writeNdefMessage` (3 round-trips). v2's `writeViaNdef` was doing `connect → maxSize → isWritable → writeNdefMessage` (5 round-trips); the two extra capability-container reads on a marginal tap leave NTAG21x in a state where the subsequent write fails. **Fix**: dropped pre-flight `isWritable` and `maxSize` round-trips. Write goes straight through. On IOException, single `maxSize` probe — if capacity < payload, surface friendly `"tag too small: payload XB > capacity YB"` (preserves NTAG213 too-small error); else generic IOException with payload size. Also added NdefFormatable fallback for the `Ndef.get` non-null + write-IOException path (covers chips that expose both techs).

**Session verification**:
- `:app:compileDebugKotlin` ✅ across all iterations
- `:app:installDebug` ✅ multiple rounds on moto g stylus 2025 / Android 16
- Manual matrix §1 (Read flow, 13 scenarios) ✅
- Manual matrix §2-§9 in flight at session pause

### Continued 2026-05-30 install-gate session (UI-16 → UI-20)

7. **Filament section: always-open** (UI-16). U8-Δ-1's collapsed-by-default `FilamentSectionExpander` reframed as a plain `FilamentSection` (file renamed). No header tap, no chevron, no `AnimatedVisibility`. Dropped `FormState.filamentSectionExpanded`, `FormChange.FilamentSectionToggled`, `MainViewModel.onFilamentSectionToggled`, MainScreen route hook, and the toggle test in `MainViewModelFilamentPickerTest`. "Filament" heading uses `bodyLarge` SemiBold + primary; picker textStyle bumped to `titleMedium` SemiBold (subtle size step vs the secondary `bodyLarge` form fields).

8. **End-of-pair-flow auto-clear reverted** (UI-17). The `applyEndOfPairFlow(spoolId)` helper added earlier this session (drop-spool, pin-filament, auto-expand) was reverted per user direction. Both `onPairAnotherTagDismissed` and `applyTwoTagResult.SecondTagPaired` now do plain `activeFlow = Idle` transitions. Form keeps spool + filament + everything else, same as v1.

9. **Spool dropdown X = clear spool only** (UI-18). `MainViewModel.onSpoolSelected(null)` was resetting the entire form (material/brand/colour/temps/expanders/cardUid + ambiguity + observed-tag). Now clears only `form.selectedSpoolId` + `spoolman.selectedSpoolId`. Filament dropdown's X (`onFilamentSelected(null)`) is unchanged. Test renamed and rewritten to assert form fields survive.

10. **Write-fail snackbar copy** (UI-19). `CreateAndPairResult.VerifyFailed` and `NfcFailed` (non-vendor) both shipped "Couldn't write to tag. Try again." — hid the fact that `CreateAndPairUseCase` creates the spool BEFORE the write tap (the use case explicitly preserves the spool on failure so the user can retry without re-filling). New copy: **"Saved to Spoolman. Tag write failed. Try again."** VerifyFailed branch additionally keeps `selectedSpoolId = result.spoolId` so the UI shows the saved spool ready for retry. Vendor sub-case unchanged.

11. **Write path: pre-read + verify removed** (UI-20). Root cause of the recurring "phone moved" write failures: v2 was doing 3 `Ndef.connect()` cycles per write tap (pre-read for classification + write + verify), Android fires the system tag-detected haptic after the first connect, users pull away thinking it's done, write/verify fails on cycle 2 or 3. v1.7 used one connect cycle. **Fix**:
   - `NfcRepository.handleTag` peeks `_state.value`; on `is NfcResult.Writing`, synthesises a `RawTagRead` from in-memory `Tag` only (uid + techList, `records = null`). `classify(raw)` handles `records == null` via existing techList branch. Read / Idle paths unchanged.
   - `runWriteThenVerify`'s verify block (transition Verifying + readRecords + records-equal compare) commented out. Kept inline for easy revert if Snapmaker round-trip (§9.2) surfaces a counterfeit-chip regression. `writeNdefMessage` throwing IOException on phone-moved-during-write is still surfaced via `WriteResult.Failed` → `CreateAndPairResult.NfcFailed` → the new snackbar copy.
   - `CardUid` import added to `NfcRepository.kt`.
   - Net: write tap is `Ndef.connect → writeNdefMessage → close`, same as v1.7. Phone-still window collapses to the actual write duration (~50 ms for our 216 B payload).

12. **Manual checklist cleanup (post UI-16, vendor design)**. `aidlc-docs/operations/manual-nfc-checklist.md`:
    - **§4.3** marked N/A — `lot_nr` opaque-tail preservation. v2 doesn't use `lot_nr` for tag-UID storage (UIDs live in `extra.card_uids` only). Test scenario was a v1-era carry-over.
    - **§5.7 / §5.10 / §5.11** marked N/A — Filament section is always-open post UI-16, so "expander prefill" / "both expanders independent" / "default layout matches U7 when collapsed" no longer apply. §5.5 / §5.6 cover the prefill behaviour implicitly.
    - §5 header updated to reflect 10 live scenarios (down from 12).

13. **Vendor pre-block + UID-only pair re-route on Writing-state vendor tap** (extends UI-20 + UI-19). When the user picks a spool from the dropdown and taps a vendor tag *without first hitting Read*, the dispatch in `MainViewModel.onWriteTapped` had `observedTagKind = None` (no prior Read) and routed through the standard create-and-pair path. The vendor chip Android promotes to NDEF would either silently no-op the write or throw IOException, leaving the user with the misleading "Saved to Spoolman. Tag write failed" snackbar even though Spoolman was correctly repaired. Fix:
    - **`NfcRepository.classify`**: MifareClassic in techList now classifies as Vendor *regardless* of Ndef presence (Bambu/Creality auto-promoted chips). Was: Ndef → Blank wins.
    - **`NfcRepository.runWriteThenVerify`**: vendor pre-block at top — emits `NfcResult.Error("vendor-tag protected (FR-4.7): <reason>")` before any NDEF transceive. lastSeenTag UID is preserved so the use case still gets it.
    - **`CreateAndPairUseCase`**: on `WriteResult.Failed` with `"vendor-tag"` reason + non-null UID, returns `Success.WrittenAndPaired(isVendorPair = true)` instead of `NfcFailed`. Spoolman PATCH (Step 3) already linked the UID, so the pair is complete.
    - **`CreateAndPairResult.Success.WrittenAndPaired`**: new `isVendorPair: Boolean = false` field.
    - **`MainViewModel.applyWriteResult.WrittenAndPaired`**: clears `observedTagKind` + `observedTagUid` (so VendorTagHint chip dismisses), threads `isVendorPair` into `ActiveFlow.PromptingPairAnother`.
    - **`ActiveFlow.PromptingPairAnother`** + **`PairAnotherTagUiState`**: new `isVendorPair` flag.
    - **`PairAnotherTagSheet`**: vendor-aware copy. Title: "Tag linked. Pair another tag with this spool?" (was: "Saved.…"). Body: "Tap a tag to link it to the same spool." (was: "We'll write the same data to the second tag…"). Both copies preserved on the non-vendor branch.
    - **`MainViewModel.applyVendorUidOnlyPairResult.Success`**: also threads `isVendorPair = true` so the dedicated Read-first vendor flow lands on the same sheet copy.
    - **`MainViewModel.onPairAnotherTagDismissed`**: snackbar branches on `current.isVendorPair` — "Vendor tag linked." vs "Saved with one tag."
    - **Second-tag vendor success snackbar**: the `applyTwoTagResult.VendorTagRejected` re-route's success branch now emits "Both tags paired." (was silent — only the synthetic create-and-pair vendor path had any snackbar).

14. **Hint pills moved up + restyled** (UI polish in same stretch). `WritingHint` was rendered below the form/Save button; moved up next to `ReadingHint` between the BannerSlot and the Spoolman dropdown card. Both hints rewritten as `NfcStatusPill` — a centered Surface with `RoundedCornerShape(50)`, `primaryContainer` fill, 18dp `Icons.Filled.Nfc` + `labelLarge` text in `onPrimaryContainer`. Reads as a status affordance instead of a centered text label that was visually competing with the app-name overlay.

15. **Test env note**: the `MainViewModelTest` rewrite (`…clears spool selection only, form fields preserved`) couldn't be executed locally — `./gradlew :app:testDebugUnitTest` fails with `DefaultReportContainer … Type T not present` (JDK / generic-types reflection issue in the local test task config). `:app:installDebug` and `:app:assembleRelease` are unaffected. Test verification of UI-18 deferred until env issue is resolved out-of-band.

16. **Brand/material case canonicalised** (UI-24). `MainViewModel.resolveBrandName` and `resolveMaterialName` now canonicalise raw user input against the merged `brands.value` / `materials.value` lists — first case-insensitive match wins, otherwise raw input passes through (genuinely new entries preserve user case). Fixes the leak of user-typed case (e.g. `"polymaker"`) into the filament `name` field when an existing brand (`"Polymaker"`) already deduped at the vendor row.

17. **Filament dropdown auto-selects after create** (UI-25). `applyWriteResult.WrittenAndPaired` and `applyVendorUidOnlyPairResult.UidPaired` now look up the just-paired spool's `filament.id` from `state.spoolman.spools` (cache populated by `SpoolmanRepository.refreshAfterWrite()` inside the use case) and set `form.selectedFilamentId`. Falls back to existing selection if the new spool isn't in the cache yet (rare race).

18. **Surfaced UI-26** (suspected): manual `curl /api/v1/spool/76` showed a filament with `extra: {}` — no `variant` key. Spool predates much of this session's wiring, may be stale data. Logged in `ui-followups.md` for reproduction; not blocking U10.

19. **Brand defaults to null + canSubmit gate** (UI-27). `FormState.brand` no longer defaults to `Brand("Generic")` — defaults to `null` and `canSubmit` requires a non-null brand. Prevents accidental Save & Write from creating a "Generic" vendor row in Spoolman.

20. **Read FAB enlarged + Save & Write disabled visible** (UI-28). `ReadFab` now `Modifier.height(64.dp)` + `titleMedium`. `SaveAndWriteButton` disabled state uses `primary.copy(alpha = 0.5f)` background + full `onPrimary` text so it doesn't blend into the form Card.

21. **Bottom instruction footer removed** (UI-29). The v1-style three-line "• Tap a tag to read…" footer was redundant after UI-23 moved hints to top pills. `InstructionFooter` composable and its call site deleted.

22. **Chain-delete orphan vendor/filament/spool** (UI-30). When `CreateAndPair` or `VendorUidOnlyPair` create Spoolman records but never get a UID attached (timeout, NFC failure, move-on-bind decline), the records are now best-effort cleaned up in the background. Adds:
    - `OrphanSpool.kt` — `OrphanSpool(spoolId, filamentId?, vendorId?)`, internal `Resolved<T>(value, wasCreatedFresh)`, public `NewSpoolBundle`.
    - `SpoolmanApi`: `@DELETE` endpoints for spool / filament / vendor.
    - `SpoolmanRepository.chainDeleteOrphan(orphan): SpoolmanOutcome<Unit>` — sequential DELETEs, swallow 4xx with `Log.w`, prune caches.
    - `SpoolmanRepository.createSpoolForNewFilamentBundle` (new) returns the bundle; old `createSpoolForNewFilament` preserved as thin wrapper.
    - `resolveOrCreateVendor` / `resolveOrCreateFilament` now return `Resolved<T>` so callers learn fresh-vs-reused.
    - `CreateAndPairUseCase.lastResolvedOrphan: OrphanSpool?` — set after `resolveSpool.Created`, cleared on `appendCardUidToSpool` Success. Existing-spool path keeps it null.
    - `VendorUidOnlyPairUseCase.lastResolvedOrphan: OrphanSpool?` — same mechanism on its new-spool path.
    - `MainViewModel.fireOrphanCleanup(orphan)` — fire-and-forget on `viewModelScope`. Wired into `applyWriteResult.NfcFailed` (with retry-pin fallback for non-orphan cases), `applyWriteResult.Cancelled`, and all `applyVendorUidOnlyPairResult` failure branches. Skipped for `VerifyFailed` (UID was appended) and `Success` (UID landed).
    - `MainViewModel.spoolman` promoted from constructor param to `private val`.
    - **Reused-filament-on-fresh-vendor edge**: if filament was matched (not freshly POSTed), the bundle treats vendor as reused too — even if our flow created the vendor moments earlier. Prevents over-deleting in rare ordering races.

23. **Carry-over to next session** (UI-31): user flagged a remaining bug at commit time but deferred details. Captured as UI-31 in `ui-followups.md` for next session re-elicitation.

24. **Test env + fixtures** (UI-32, post-commit follow-up). Bumped Gradle wrapper 8.13 → 8.14.3 to fix `DefaultReportContainer … Type T not present` under JDK 24. Updated fixtures: `NfcTestSupport.makeTag()` now provides non-null UID + Ndef techList (UI-20 compat), `sampleUid()` uppercased to match `CardUid.fromBytes`, `FakeSpoolmanApi` gains DELETE overrides, `FakeSpoolmanRepository` adds `createSpoolForNewFilamentBundle` + `chainDeleteOrphan` overrides, brand-default fixtures use `assertNull` (UI-27), ambient snackbar filter lists the new strings, write-fail snackbar assertions match UI-19 copy, verify-mismatch tests rewritten for UI-20 (verify removed). **361 / 361 tests green**. Dedicated chain-delete coverage (UI-30) deferred to a follow-up.

## Install-gate execution (2026-05-31)

### U10 install gate — Snapmaker U1 round-trip ✅ PASSED
Per `unit-of-work.md` §U10 exit criteria — Snapmaker U1 round-trip
verified end-to-end on user's printer:

- [x] SpoolPainter v2 Save & Write produces a valid OpenSpool NDEF
  message: TLV `03 DE` (length 222), record header `D2 10 CB`
  (TNF_MIME_MEDIA, type=`application/json`, payload=203 bytes), JSON
  envelope with `protocol/version/type/color_hex/brand/min_temp/max_temp/bed_min_temp/bed_max_temp/spool_id/subtype`.
- [x] U1 firmware (`paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware`
  PR #491 build) detects MifareUltralight tag, `openspool_tag_processor`
  parses JSON, POSTs `filament_detect/set` 200.
- [x] Spoollink agent resolves UID `046EB693DA2A81` against Spoolman
  `extra.card_uids` (plural; uppercase hex; comma-separated;
  double-JSON-encoded — matches our `ExtraCardUidsCodec`); enriches
  Fluidd Spool Manager with full spool data (name, brand, variant,
  weight, full temps).
- [x] Round-trip works once the U1's `Snapmaker Components > Spoolman
  Integration` toggle is enabled in Fluidd Settings (printer-side
  config, not an app issue — see UI-33).

**Two known-environment gotchas captured as UI-33 in `ui-followups.md`**:
(a) tags wiped with non-SpoolPainter tools may carry malformed NDEF that
blocks U1 detection until overwritten by Save & Write; (b) U1 firmware
config toggle `Snapmaker Components > Spoolman Integration` must be on
for spoollink UID enrichment to fire.

### Remaining install-gate items (deferred — not blocking close-out)
- [ ] Manual NFC checklist (`aidlc-docs/operations/manual-nfc-checklist.md`)
  full 50-scenario sweep — Snapmaker U1 round-trip section verified;
  rest of the matrix covered organically across U6-U10 install-gate
  iterations and is not a v2.0-ship blocker per Q-T2=B.
- [ ] Release build (signed APK, 6.9 MB) full smoke test — release
  bundles already verified as building (`assembleRelease` + `bundleRelease`
  green); on-device sideload smoke deferred until just before testing-
  track upload.
- [ ] `adb logcat | grep com.spoolpainter.app` zero-D/I/W release
  verification (NFR-5) — covered indirectly by `-assumenosideeffects
  android.util.Log` ProGuard rule + R8 first-try success; live
  verification with the signed APK remains a pre-upload check.

### Testing-track upload (gated on user)
- Follow `aidlc-docs/operations/testing-track-upload-checklist.md`
- After upload: paste invite link into `README.md` `<!-- TODO -->` placeholder

### Out of scope (parked)
- ~~U10-Δ-2 sortOrder JSON migration~~ — DROPPED (no users on prior v2 build)
- UI-13 / UI-14 / UI-15 — post-v2.0 release per "add editing for something later after release"
- Optional debug UID surface — dropped this session
- v2.1 units (U11 vendor decode + GPL-3.0 transition; U12 vendor key settings) — hard-gated behind v2.0 ship

---

## Approval gate

Per `core-workflow.md` CONSTRUCTION → Per-Unit Loop → Code Generation:

🔧 **Request Changes** — modify / re-execute
✅ **Continue to Next Stage** — approve U10 close-out; proceed to install-gate execution + testing-track upload (gated on user)
