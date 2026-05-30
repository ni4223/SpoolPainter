# Requirements Delta — Passive OfflineBanner + Settings-Resident Test Connection

**Status**: APPROVED 2026-05-29
**Authority**: Q-U9-10=B (FD Part 1 plan for U9, "go go go" approval); reaffirms Q-CD1.1=A (Application Design carve-out, locked at U1).
**Scope**: Reframes S-10.2 acceptance criteria; closes the requirements-vs-implementation gap permanently.

---

## 1. Background

### 1.1 What S-10.2 originally said

Per `aidlc-docs/inception/user-stories/stories.md` (S-10.2):

> As a user, when Spoolman is unreachable, I want to see a clear banner with a Retry control so I can quickly recover.

ACs included:
- (a) Banner appears when URL is configured AND connectivity is `Unreachable`.
- (b) Banner shows "Spoolman unreachable" plus optional reason suffix.
- **(c) Banner has a Retry control.** ← This is the gap.

### 1.2 What was actually shipped

At Application Design (Q-CD1.1=A, locked 2026-05-24): the banner is **passive**. The "Test connection" action lives in Settings, not on the banner. Reasoning recorded in audit.md: a single canonical entry point for connectivity testing avoids fragmented navigation and keeps the banner read-only.

This was reaffirmed during U6b's polish patch (`71ecffc`, 2026-05-27) — UI-04/UI-09 explicitly removed any "active" affordance on the banner.

### 1.3 The implementation-vs-spec gap

S-10.2 still names a Retry control. U9 implements (a) and (b) verbatim, but (c) is **superseded** by Q-CD1.1=A. Without a delta, future readers of `stories.md` will hit the gap and re-litigate the decision.

---

## 2. The delta

### 2.1 New AC text for S-10.2

**Replaces** S-10.2 ACs (a)/(b)/(c) with:

- (a) Banner appears when URL is configured AND `SpoolmanRepository.connectivity == Unreachable`.
- (b) Banner shows "Spoolman unreachable" plus optional reason suffix (`": $reason"` when reason is non-null).
- **(c) Banner is passive (read-only).** No Retry control. The user retries connectivity from Settings → "Test connection" — the only Test-connection action in the app.
- (d) Banner is not clickable; it does not navigate to Settings on tap. Settings is reachable via the gear icon in the top app bar (Q-U9-9=A).

### 2.2 Why "passive banner + Settings-resident Test connection" (vs. "Retry on banner")

| Reason | Detail |
|---|---|
| Single canonical entry point | One Test-connection button in one place. Two locations would diverge in copy + behavior over time. |
| Banner stays out of the way | The banner is informational. Adding an action makes it harder to dismiss visually. |
| Settings is one tap away | Gear icon is permanently visible in the top app bar (existing `onNavigateToSettings`). |
| Reaffirmed under user feedback | UI-04/UI-09 polish patches at U6b explicitly chose Button vs. FilledTonalButton on primary actions; banner was deliberately left without an action. |
| Aligns with Material 3 banner guidance | Material 3 reserves "passive informational banners" as a distinct pattern from snackbars-with-actions. |

### 2.3 What this delta does NOT change

- Banner copy "Spoolman unreachable" + reason suffix — unchanged.
- Banner derivation logic — unchanged (FR-10.2, FR-10.3 preserved).
- Settings → "Test connection" behavior — unchanged.
- The gear-icon → Settings navigation — unchanged.

---

## 3. Forward-compat path (if user backlash arrives post-launch)

If install-time iteration ever surfaces "I wish I could Retry from the banner without going into Settings":

1. Add `BannerState.OfflineWithRetry(reason, onRetry: () -> Unit)` variant (additive — does not break existing matchers).
2. Wire the banner card's `onClick` to invoke `MainViewModel.onBannerRetryTapped()` which calls `SpoolmanRepository.testConnection()`.
3. Update this delta with a "v2" section noting the reversal.

The forward-compat path is **not** in U9 / U9b / U10 scope. Documented so reviewers don't re-derive it.

---

## 4. Traceability

- **Originated by**: Q-CD1.1=A (Application Design 2026-05-24); reaffirmed by Q-U9-9=A and Q-U9-10=B (U9 FD Part 1, 2026-05-29).
- **Stories affected**: S-10.2 only.
- **Components affected** (no code changes by this delta — purely a doc reconciliation):
  - `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainScreen.kt` (banner card — already passive)
  - `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainUiState.kt` (`BannerState` — already only `Hidden` / `Offline`)
  - `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt` (banner derivation — already passive)
- **Tests affected** (added by U9):
  - `MainViewModelBannerTest` — derivation matrix per BR-U9-24.

---

## 5. Approval gate

This delta was authored and approved as part of U9's FD Part 2 artefact generation (2026-05-29; user direction "start AIDLC for u9" → "go go go"). Per `core-workflow.md` audit-log requirements, the approval is recorded in `aidlc-docs/audit.md` under the "U9 — FD Part 2 (artefact generation)" entry.
