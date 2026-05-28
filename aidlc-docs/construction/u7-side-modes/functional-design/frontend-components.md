# U7 — Frontend Components

**Stage**: CONSTRUCTION → Functional Design (U7 — Side Modes)
**Source plan**: `aidlc-docs/construction/plans/u7-side-modes-functional-design-plan.md`
**Reflects**: Q-U7-1..15 outcomes (locked 2026-05-27) + D-U7-1..5

---

## 1. UI surfaces touched by U7

| Surface | Type | Change |
|---|---|---|
| `MainScreen` | Compose screen | Reads new `WriteMode` + `ObservedTagKind`; renders banner, chip, helper text; updates Save button label. |
| `FilamentForm` | Compose component | New helper-text slot above material picker (rendered conditionally). |
| `MainViewModel` | ViewModel | Two new use-case dependencies; new derived flows (`writeMode`, `observedTagKind`); new `applyRawWriteResult` + `applyVendorUidOnlyPairResult`. |
| `VendorOptInViewModel` | ViewModel | **Deleted** (U1 placeholder, never used). |
| `BottomSheetHost` | Compose | **No new branch** — vendor flow has no sheet. |

---

## 2. New / modified components

### 2.1 `MainScreen` — Save button

**Existing** Save button reads from `canWrite`. U7 extends the rendering
to switch label by `(writeMode, observedTagKind)`:

| `writeMode` | `observedTagKind` | Button label |
|---|---|---|
| `Spoolman` | None / Blank / OpenSpool | **"Save & Write"** (unchanged) |
| `Spoolman` | Vendor | **"Save"** |
| `RawNoUrl` / `RawDisconnected` | None / Blank / OpenSpool | **"Write to NFC"** |
| `RawNoUrl` / `RawDisconnected` | Vendor | **"Save"** (button disabled — see [[2.4]]) |

Driven by a `derivedStateOf` in the screen body.

### 2.2 `MainScreen` — Top banner

A new `RawWriteBanner` Compose function renders above the form Card when
`writeMode != Spoolman`.

| `writeMode` | Banner copy |
|---|---|
| `Spoolman` | (banner hidden — return early) |
| `RawNoUrl` | **"Writing tag only — Spoolman not configured"** |
| `RawDisconnected` | **"Writing tag only — not connected to Spoolman"** |

Visual treatment: same `Surface` style as the existing offline banner
(secondary container colour); single line; non-actionable.

> **Layering note**: the existing offline banner from U5/U9 is a separate
> surface (it currently fires on `connectivity == Unreachable`). To avoid
> two stacked banners saying the same thing in `RawDisconnected`, the
> existing banner SHALL be suppressed when this raw-write banner is
> visible. Implementation suggestion: extend the existing banner's
> visibility predicate to require `writeMode == Spoolman`.

### 2.3 `MainScreen` — Vendor-tag chip + helper text

When `state.observedTagKind == Vendor`, the screen SHALL render two
elements just below the spoolman dropdown / above the FilamentForm:

```
┌────────────────────────────────────────────────────┐
│  [chip] Vendor tag — content unreadable           │
│  Fill in the details below to link this tag.      │
└────────────────────────────────────────────────────┘
```

Components:

- `AssistChip(label = "Vendor tag — content unreadable", icon = WarningAmberIcon)`.
- `Text(...)` with `style = MaterialTheme.typography.bodySmall` for the
  helper line.

Both clear when `state.observedTagKind != Vendor` or `form.cardUid == null`.

### 2.4 `MainScreen` — Save button enable predicate

Existing `canWrite` predicate stays. U7 adds a *visibility* / *enabled*
gate on top:

| Condition | Save button state |
|---|---|
| `canWrite == false` | Disabled (existing) |
| `writeMode != Spoolman` AND `observedTagKind == Vendor` | Disabled with helper text "Configure Spoolman to pair vendor tags." |
| Otherwise | Enabled |

The disabled+vendor state is rare (vendor tap on a no-Spoolman device).
Helper text appears beside or below the disabled button.

### 2.5 `FilamentForm` — no structural change

Only the new helper-text slot in [[2.3]] sits *above* the form, hosted by
`MainScreen` directly. `FilamentForm` itself is untouched.

### 2.6 `MainViewModel` — new derived flows

```kotlin
val writeMode: StateFlow<WriteMode> = ...        // [[BR-U7-1]]
val observedTagKind: StateFlow<ObservedTagKind> = ...
```

Both surfaced into `MainUiState` (or kept as separate `StateFlow`s
collected by the screen — implementation pick at code-gen).

### 2.7 `MainViewModel` — new handlers

| Handler | Trigger |
|---|---|
| `applyRawWriteResult(result)` | After `RawWriteUseCase` returns. Emits snackbar; clears `activeFlow`. |
| `applyVendorUidOnlyPairResult(result)` | After `VendorUidOnlyPairUseCase` returns. On success, transitions to `PromptingPairAnother`; otherwise emits snackbar + clears flow. |

### 2.8 `MainViewModel` — modified handlers

| Handler | Change |
|---|---|
| `onSaveAndWriteTapped()` | New branching per [[BR-U7-4]]. Three internal launches: raw / vendor / standard. |

### 2.9 Removed UI

| Component | Reason |
|---|---|
| `ui/components/sheets/VendorOptInViewModel.kt` | No sheet; placeholder is dead. Delete in U7 code-gen. |
| (would-have-been) `VendorUidOnlyOptInSheet.kt` | Never created. |
| (would-have-been) Overflow-menu raw-write toggle | Reframed away — raw-write is automatic. |

---

## 3. Component layout — annotated wireframe

```
┌───────────────────────────────────────────────────────┐
│   SpoolPainter                                  ⋮     │  ← TopAppBar (no overflow toggle in U7)
├───────────────────────────────────────────────────────┤
│  ⚠  Writing tag only — Spoolman not configured        │  ← RawWriteBanner (only if writeMode != Spoolman)
├───────────────────────────────────────────────────────┤
│   ┌─ Spool: ── (dropdown) ──────────────────────┐ ▼  │  ← SpoolmanDropdown (existing; disabled in raw modes)
│   └────────────────────────────────────────────┘     │
│                                                       │
│   [⚠] Vendor tag — content unreadable                 │  ← AssistChip (only if observedTagKind == Vendor)
│   Fill in the details below to link this tag.         │  ← helper text (same condition)
│                                                       │
│   ┌── FilamentForm ───────────────────────────────┐  │
│   │  Material:  [ PLA  ▼ ]                        │  │
│   │  Variant:   [ Matte    ]                      │  │
│   │  Color:     [ ●  C0FFEE ]                     │  │
│   │  Brand:     [ eSun ▼ ]                        │  │
│   │  Temps:     [ 200 .. 220 °C ]                 │  │
│   └───────────────────────────────────────────────┘  │
│                                                       │
│   ┌─────────┐    ┌──────────────────────────────┐    │
│   │  Read   │    │   Save & Write / Save /      │    │  ← Save button label varies by mode + classification
│   │  tag    │    │   Write to NFC               │    │
│   └─────────┘    └──────────────────────────────┘    │
└───────────────────────────────────────────────────────┘
```

`PairAnotherTagSheet` and `RepairConfirmSheet` (U6b) overlay as before.

---

## 4. Visual states by scenario

| # | Scenario | Banner | Chip + helper | Save button label | Save enabled? |
|---|---|---|---|---|---|
| 1 | Spoolman OK, no tag observed | hidden | hidden | "Save & Write" | `canWrite` |
| 2 | Spoolman OK, blank tag observed | hidden | hidden | "Save & Write" | `canWrite` |
| 3 | Spoolman OK, OpenSpool tag observed | hidden | hidden | "Save & Write" | `canWrite` |
| 4 | Spoolman OK, vendor tag observed | hidden | shown | "Save" | `canWrite` |
| 5 | URL blank, no tag | "Writing tag only — Spoolman not configured" | hidden | "Write to NFC" | `canWrite` |
| 6 | URL blank, blank tag | "Writing tag only — Spoolman not configured" | hidden | "Write to NFC" | `canWrite` |
| 7 | URL blank, vendor tag | "Writing tag only — Spoolman not configured" | shown | "Save" | **disabled** + "Configure Spoolman to pair vendor tags." |
| 8 | URL set, unreachable, no tag | "Writing tag only — not connected to Spoolman" | hidden | "Write to NFC" | `canWrite` |
| 9 | URL set, unreachable, vendor tag | "Writing tag only — not connected to Spoolman" | shown | "Save" | **disabled** |

---

## 5. Form behaviour during use-case execution

| `activeFlow` | Form fields | Spool dropdown | Save button | Read button |
|---|---|---|---|---|
| `Idle` | enabled | enabled (per UI-11 polish — `urlConfigured && Idle`) | enabled iff `canWrite` | enabled |
| `WritingRaw` | disabled | disabled | disabled | disabled |
| `PairingVendorUidOnly` | disabled | disabled | disabled | disabled |
| `AwaitingRepairConfirmation` | disabled | disabled | disabled | disabled (sheet visible) |
| `PromptingPairAnother` | preserved + disabled | preserved + disabled | disabled | disabled (sheet visible) |

Same predicate as U6b polish — extended to cover the two new flow states.

---

## 6. Snackbar palette (U7)

| Trigger | Copy | Notes |
|---|---|---|
| RawWrite success | "Tag written" | Non-Spoolman context — "paired" would be misleading. |
| RawWrite vendor rejected | "Vendor tag — content unreadable" | Should rarely reach user (raw + vendor scenario is gated upstream). |
| RawWrite verify failed | (carry from U6b copy review) | UI-07 / U10. |
| RawWrite nfc failed | (carry from U6b copy review) | UI-05 / U10. |
| Vendor pair success | "Tag paired" | No "UID" in user-facing copy. |
| Vendor + no Spoolman URL | "Spoolman needed to save vendor tag — connect and try again." | D-U7-2. |
| Vendor + Spoolman unreachable | "Spoolman not reachable — try again when connected." | D-U7-1. |
| Vendor pair `MoveOnBindPartial` | (carry from U6b — UI-07 / U10) | |
| Vendor pair `Cancelled('repair declined')` | suppressed | UI-12 — already shipped. |

---

## 7. Accessibility / a11y notes

- Chip uses `Modifier.semantics { contentDescription = "Vendor tag detected. Content cannot be read. Fill in the form to save the tag." }` to merge the chip + helper text into one announcement for screen readers.
- Banner uses `Modifier.semantics { contentDescription = ... }` matching the visible text.
- Save button label change is announced automatically by Compose since
  the `Text` content changes.
- Color contrast for the chip and banner SHALL meet AA per existing
  Material 3 theming. (No special tokens introduced in U7.)
