# U31 — Code Generation Plan

**Unit**: U31 — NFC flow hardening from external PR #15, split and fixed (UI-73)
**Opened**: 2026-11-22 (planning only, Part 1). **Not started.**
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP**. The code is an external
contribution; this unit is: ask for a split and one fix, review, verify, merge.
**Source**: GitHub #18 (printing-enthusiast), external **PR #15** (same author),
branch `fix/nfc-wedged-flows-hung-tag-io`, head `0363733`, targeting `main`.
**Maintainer decision 2026-11-22**: *"we ask them to fix it"*.
**This plan is the single source of truth for U31.**

---

## §1 What PR #15 is: three layers, as described in the PR

- **Layer 1, crash guard.** `runNfcFlowSafely` on all six flow launches (returns
  to Idle, disarms, honest snackbar; rethrows `CancellationException`).
  `MoveOnBindConfirmerImpl` supersedes an overlapping request instead of
  throwing. `PairingVendorUidOnly` becomes cancellable. The `_lastSeenTag` buffer
  is cleared on write-path terminal transitions, which fixes [[UI-56]].
- **Layer 2, split timeouts.** `NfcRepository.awaitTerminal(tapWaitMs,
  ioGraceMs)`: the UX budget bounds only the wait for the tap, and tag I/O gets a
  separate 30 s grace. Fixes "tag written, Spoolman never patched" (the outer 15 s
  `withTimeoutOrNull` at `MainViewModel.kt:679` covers the whole use case today).
- **Layer 3, watchdog + re-arm.** `TagIoWatchdog` force-closes a tech after
  8 s / 12 s; after a grace blowout the repository re-registers foreground
  dispatch.

The PR body offers: *"Happy to split this into separate PRs per layer if you
prefer."*

## §2 Verified (2026-11-22, static review, not run)

- **Layer 1 regression (verified by code read):** the PR clears `_lastSeenTag`
  *before* the `NfcResult.Error` transition (PR `NfcRepository.kt:366/377/384`).
  The use cases read the UID from that buffer *after* the Error
  (`CreateAndPairUseCase.kt:173-179`; also `TwoTagUseCase.kt:60-69`,
  `RawWriteUseCase.kt:53`). Effects:
  - a failed write on an unread vendor tag no longer pairs its UID;
  - a second-tag vendor reroute sends an empty UID, which Spoolman rejects;
  - the NTAG213 "too small" case says "Paired only" though nothing was paired.
- **Layer 3 does not do what it claims (verified by code read):**
  - `withTimeoutOrNull { runInterruptible { ... } }` waits for the block to
    return, so `closer.closeRegistered()` runs only *after* the blocking call has
    finished. It cannot abort a hung `connect()`.
  - A write that finishes after the budget is reported as failed.
  - The tests use a fake that unblocks on interrupt, so they never model a real
    hang.
  - The re-arm fires only after a consumed tap plus an I/O timeout, which is not
    [[UI-53]]'s symptom. It also runs off the main thread, so it may crash
    (inferred).
- **Base:** `main` at `f197de3` (2.3.1), 19 commits behind `v2`. One mechanical
  conflict in `MainViewModel.kt` (U27 `canClear`, UI-54 `TAG_REDERIVE`).
- **Layer 2 limit (not a bug):** the tap/I-O split covers writes only. On Read, the
  vendor decode still counts against the tap wait.
- No 2.3.2-2.4.1 commit touched `hardware/` or `domain/usecases/`, so every bug
  PR #15 targets is still present on `v2`.
- **Stacking:** PR #17 and PR #20 are stacked on PR #15, so they will need
  rebasing once PR #15 is split.

## §3 Decisions

- **D1 — fallback if there's no reply.** Recommended: wait **3 weeks** after
  posting S1, then take Layers 1-2 into our own commits with a `Co-authored-by`
  trailer for printing-enthusiast.

## §4 Steps

- [ ] **S0** — Prerequisite: Android SDK on this Mac (see U28 S0).
- [x] **S1** — Posted 2026-11-22 at the maintainer's instruction ("tell em about the issues now"):
  https://github.com/ni4223/SpoolPainter/pull/15#issuecomment-7085706466. Every claim
  re-verified against the code before posting. D1's 3-week fallback starts now
  (until about 2026-12-13).
- [ ] **S2** — Wait for the split PRs, both targeting `v2`:
  - **PR-A, Layer 1**, with the UID fix (carry the UID in `NfcResult.Error`) and
    a regression test against the real `NfcRepository` with a realistic NTAG
    techList (`NfcA`, `MifareUltralight`, `Ndef`), not the `[Ndef,
    NdefFormatable]` fixtures that [[UI-64]] flags;
  - **PR-B, Layer 2**, as-is.
- [ ] **S3** — Review each against `v2` (static first): UID survives a failed
  write; `runNfcFlowSafely` rethrows `CancellationException`; no Layer 3 code
  slipped in; no Gradle build files changed.
- [ ] **S4** — On each PR branch: `testDebugUnitTest`, `lintDebug`,
  `assembleDebug`.
- [ ] **S5** — On-device checks:
  - [ ] a failed write on a vendor tag still pairs the UID;
  - [ ] a slow or marginal write ends with Spoolman patched, not "No tag tapped";
  - [ ] a Read right after a Write is not reported as blank ([[UI-56]]);
  - [ ] normal read, write, pair, and second-tag pair are unchanged.
- [ ] **S6** — GitHub (approval needed): merge PR-A and PR-B into `v2`.
- [ ] **S7** — Docs: UI-73 and UI-56 state, release notes, `aidlc-state.md`.
  Reply on #18: Layers 1-2 are fixed; the "dead until restart" symptom is
  [[UI-53]], still open with its own plan
  (`construction/plans/parked-ui53-nfc-dispatch-rearm-plan.md`).

## §5 Request posted on PR #15 2026-11-22 (text as posted)

> Thanks for this, and for the detailed write-up. Yes please, split it per layer,
> and base both PRs on `v2`. That's the development branch; `main` is the release
> line and is 19 commits behind.
>
> **Layer 1**: keep, with one fix. Clearing `_lastSeenTag` before the
> `NfcResult.Error` transition breaks UID pairing on failed writes, because
> `CreateAndPairUseCase`, `TwoTagUseCase` and `RawWriteUseCase` read the UID from
> that buffer after the Error. Could you carry the UID in `NfcResult.Error`
> instead, and add a regression test against the real `NfcRepository` with a
> realistic NTAG techList (`NfcA`, `MifareUltralight`, `Ndef`)?
>
> **Layer 2**: keep as is.
>
> **Layer 3**: please leave it out for now. `withTimeoutOrNull` waits for the
> `runInterruptible` block to return, so `closeRegistered()` only runs after the
> blocking call has already finished; it can't abort a hung `connect()`. It also
> reports a write that completes after the budget as failed. The dispatch
> re-registration doesn't match the restart-only symptom I've traced (dispatch
> silently not re-armed, with no consumed tap), and I have a separate fix planned
> for that.
>
> PR #17 and #20 are stacked on this one, so they'll need rebasing onto the new
> base too.

## §5a Not in scope

- Layer 3 (watchdog + dispatch re-arm).
- [[UI-53]]: separate unit, parked plan.

## §6 Release

No version decided. The next Play upload needs **versionCode 117 or higher**.
