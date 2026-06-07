# U13 Session Resume Note — 2026-06-06

Saved before a session reset. Pick up here in the next "aidlc continue".

## Current state

- **Stage**: U13 (Action Split: Save vs Write) — Code Gen Part 1 + Part 2 **EXECUTED**.
- **Tests**: **403 / 403** ✅. Build matrix all green: `assembleDebug` 64 MB, `assembleRelease` 7.0 MB R8, `bundleRelease` 7.7 MB AAB.
- **Working tree**: dirty against `origin/v2`. 17 modified + 7 untracked files (see `git status`). NOT committed yet — close-out commit waits on install-gate sign-off per [[feedback_aidlc_unit_close_out_commit]].
- **Phone**: U13 debug APK installed as `com.spoolpainter.app.debug` versionCode 103 / `2.0.2-DEBUG` on the moto g stylus 2025. Prod `com.spoolpainter.app` v2.0.2 still alongside for comparison.
- **Branch**: `v2` (up to date with `origin/v2` at last push of `2add547`; U13 patches sit uncommitted on top).

## What was decided + locked this session

- **Plan approved**: `aidlc-docs/construction/plans/u13-action-split-code-generation-plan.md`.
- **Q&A locked**:
  - Q-U13-1 = **A** — Save handles vendor pair (no NDEF). Write button stays disabled with "Vendor tag — tap Save to pair." caption.
  - Q-U13-2 = **drop Gross** (carried locked from plan §1.4).
  - Q-U13-3 = **B** — inner Cards = elevation 0 + `BorderStroke(1.dp, surfaceVariant)`.
  - Q-U13-4 = **A** — button label "Save to Spoolman".
  - Q-U13-5 = **A** — orphan-Read goes Save → Write (no auto-Save inside Write).

## What ships in U13

- `Save & Write` → two top-level buttons: **Save to Spoolman** (HTTP) at the bottom of the outer Card + **Write** in a stationary `[Read | Write]` bottom action row.
- Both bottom-bar buttons toggle to **Cancel** during tag-waiting flows (Read, NDEF Write standard, NDEF Write raw, sheet's Pair-another). HTTP-only flows stay disabled-while-in-flight without a Cancel surface.
- Bidirectional Remaining/Measured row replaced by Spoolman-parity radio: top segmented row picks active method; only that method's input field renders below — inactive method's field is hidden entirely.
- Outer Card wraps three inner sections (each as elevation-0 Card with thin border).
- Snackbar copy delta per plan §1.5: joint-state strings dropped.

## What WASN'T touched this session (deferrals — still post-v2.1)

- F-4 erase tag completely → v2.1+ alongside U11/U12.
- F-15 multi-vendor decode → U11/U12.
- APK size review — release stayed at 7.0 MB; nothing to do.

## Next steps for the resumed session

1. **Run the install-gate checklist**: `aidlc-docs/construction/u13-action-split/code/u13-install-gate-checklist.md` — 9 sections, ~55 scenarios. Mark ✅/❌/⚠️ with notes; track new issues in §J table.
2. Any failures → patch + re-run the relevant section. Add UI-NN entries to `aidlc-docs/ui-followups.md` for non-blocking polish.
3. **Close-out commit** once gate is green: single commit with message shape `feat(v2.1): U13 — action split (Save/Write) + radio weight picker` covering all modified + untracked files. Push to `origin/v2`.
4. Update `aidlc-docs/aidlc-state.md` U13 entry from "awaiting stage-gate approval" → DONE with the close-out commit SHA.
5. Decide v2.1 release window: bump versionCode 103 → 104 (or higher), versionName 2.0.3 → 2.1.0; build release AAB; upload to Play Console Open testing track.

## Where to resume from cold

- `aidlc-docs/aidlc-state.md` — current stage line + "Current Stage" paragraph.
- `aidlc-docs/audit.md` (tail) — last entry is "U13 Code Gen Part 2 — EXECUTED".
- `aidlc-docs/construction/u13-action-split/code/u13-summary.md` — ground truth on what shipped, file inventory, brownfield invariants.
- `aidlc-docs/construction/u13-action-split/code/u13-install-gate-checklist.md` — the test list.
- This file — TL;DR of all of the above.

## Session-state fingerprint

```
git status (modified):
  aidlc-docs/aidlc-state.md
  aidlc-docs/audit.md
  app/src/main/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCase.kt
  app/src/main/java/com/spoolpainter/app/ui/components/FilamentForm.kt
  app/src/main/java/com/spoolpainter/app/ui/components/MoreDetailsExpander.kt
  app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt
  app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt
  app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt
  app/src/test/java/com/spoolpainter/app/domain/usecases/CreateAndPairUseCaseTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelBannerTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelCurrencyTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelFilamentPickerTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelMoreDetailsExpanderTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelRawWriteTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelRefreshTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSortTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelTwoTagTest.kt

git status (untracked):
  aidlc-docs/construction/plans/u13-action-split-code-generation-plan.md
  aidlc-docs/construction/u13-action-split/
  app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanResult.kt
  app/src/main/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCase.kt
  app/src/main/java/com/spoolpainter/app/ui/components/WeightMethodRadio.kt
  app/src/test/java/com/spoolpainter/app/domain/usecases/SaveToSpoolmanUseCaseTest.kt
  app/src/test/java/com/spoolpainter/app/support/FakeSaveToSpoolmanUseCase.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelSaveTapTest.kt
  app/src/test/java/com/spoolpainter/app/ui/screens/main/MainViewModelWeightMethodTest.kt
```
