# SpoolPainter v2.0 — Manual NFC Checklist

End-to-end manual verification matrix run as part of the U10 install gate. This checklist doubles as the testing-track release validation per Q-FU1=C.

## Test session header

| Field | Value |
|---|---|
| Date | _yyyy-mm-dd_ |
| Tester | _name_ |
| Device under test | moto g stylus 2025 / Android 16 (primary); _other_ |
| Build SHA | _git rev-parse --short HEAD_ |
| Build variant | debug / release |
| versionCode / versionName | 100 / 2.0 |
| Spoolman version | _e.g. 0.20.0_ |
| Spoolman URL | http://_lan-ip_:7912 |
| NFC tag types used | NTAG213 / NTAG215 / NTAG216 / vendor (Bambu / Creality / Snapmaker / etc.) |
| Snapmaker U1 firmware | _branch / version, if used_ |

---

## §1. Read flow (U5 carries)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 1.1 | Passive ambient tap on any tag surfaces the UID (no Read armed) | | |
| 1.2 | Once-per-session passive-tap hint snackbar emits on first ambient tap (UI-02) | | |
| 1.3 | OpenSpool-formatted tag prefills the form on Read | | |
| 1.4 | Vendor tag (Bambu) classified as vendor on Read | | |
| 1.5 | Vendor tag (Creality) classified as vendor on Read | | |
| 1.6 | Vendor tag (Anycubic) classified as vendor on Read | | |
| 1.7 | Vendor tag (Snapmaker) classified as vendor on Read | | |
| 1.8 | Vendor tag (TigerTag) classified as vendor on Read | | |
| 1.9 | Blank tag prompts form-first flow (write the form to this blank tag) | | |
| 1.10 | UID-match auto-selects the spool from Spoolman dropdown | | |
| 1.11 | `spool_id` extra-field fallback when card_uids miss | | |
| 1.12 | Dropdown clear on UID-only ambient tap (no spurious selection) | | |
| 1.13 | Read times out at 10s on no-tap; idle hint visible during arm | | |

---

## §2. Create-and-pair flow (U6a carries)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 2.1 | Fresh form + blank tag → filament + spool created in Spoolman | | |
| 2.2 | Created spool has `extra.variant` populated | | |
| 2.3 | Created spool has `extra.card_uids` populated with the tapped UID | | |
| 2.4 | Identical-form double-tap creates 1 filament + 2 spools (not 2 filaments × 1 spool) | | |
| 2.5 | No 422 on missing density/diameter (per-material defaults applied) | | |
| 2.6 | Color-hex normalisation symmetric on read + write (case-insensitive equality, leading `#` ignored) | | |
| 2.7 | Partial create rolled back when subsequent steps fail | | |
| 2.8 | Save & Write keeps the form populated for the next tag | | |

---

## §3. Pair another tag + Move-on-bind (U6b carries)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 3.1 | PairAnotherTagSheet shown after first-pair success | | |
| 3.2 | Second tag writes; both UIDs land in Spoolman `extra.card_uids` | | |
| 3.3 | Identical-tap on a different-spool surfaces RepairConfirmSheet (not silent overwrite) | | |
| 3.4 | RepairConfirmSheet "Move it" sweeps all source spools | | |
| 3.5 | Multi-source case (UID on 2 spools) handled correctly | | |
| 3.6 | RepairConfirmSheet Cancel emits no misleading "No tag tapped" | | |
| 3.7 | Vendor tag during second-tag prompt → friendly "Vendor tag — write blocked" | | |
| 3.8 | NDEF write-failure mid-flow surfaces friendly copy `"Couldn't write to tag. Try again."` (UI-05) | | |
| 3.9 | MoveOnBindPartial surfaces friendly copy with `#spoolId` (UI-07) | | |
| 3.10 | Second-tag-cancelled timeout surfaces `"No second tag tapped. Tap Pair another to retry."` (UI-07) | | |

---

## §4. Side modes (U7 carries)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 4.1 | Raw write writes payload to a blank tag (no Spoolman binding) | | |
| 4.2 | Vendor UID-only pair attaches UID to selected spool without writing payload | | |
| 4.3 | ~~Vendor UID-only pair preserves opaque tail of `lot_nr`~~ | N/A | v2 doesn't use `lot_nr` for tag-UID storage — UIDs live in `extra.card_uids` only. Test scenario carried over from v1 doc; dropped 2026-05-30. |

---

## §5. Pickers + custom entries (U8 carries — 10 live scenarios after UI-16; 5.7 / 5.10 / 5.11 N/A)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 5.1 | Add custom material → survives app restart | | |
| 5.2 | Add custom brand → survives app restart | | |
| 5.3 | Material picker dedup (`pla` + preset `PLA` → one row) | | |
| 5.4 | Brand merge across Spoolman vendors + presets (no duplicates) | | |
| 5.5 | Filament picker — 0-spool filament selectable | | |
| 5.6 | Filament picker — 1+-spool deliberate-2nd-spool add works | | |
| 5.7 | ~~Expander prefill from existing filament metadata~~ | N/A | Stale post UI-16 — Filament section is no longer an expander. The "prefill from existing filament" behaviour itself is still tested implicitly by 5.5 / 5.6. Item dropped 2026-05-30. |
| 5.8 | Expander PATCH idempotency (no HTTP call when nothing changed) | | Applies to MoreDetails (Filament metadata) section — Filament picker doesn't PATCH. |
| 5.9 | Expander PATCH applied (changed field rides on wire) | | Same scope as 5.8. |
| 5.10 | ~~Both expanders independent (Filament + More details)~~ | N/A | Stale post UI-16 — only one expander (MoreDetails) remains; Filament is always-open. Dropped 2026-05-30. |
| 5.11 | ~~Default form layout byte-identical to U7 when both expanders collapsed~~ | N/A | Stale post UI-16 — layout deliberately diverges from U7 (no Filament expander). Dropped 2026-05-30. |
| 5.12 | Custom-material dedup vs preset | | |
| 5.13 | Add-custom auto-select after creation (Q-U8-15=A) | | |

---

## §6. Settings + theming + sort + currency (U9 carries — 10 scenarios)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 6.1 | Spool sort (Material / Brand / ID / Last Used) reorders dropdown live | | |
| 6.2 | Spool sort Asc/Desc segmented row flips ordering live | | |
| 6.3 | Filament sort (Material / Brand / ID — no Last Used) reorders picker live | | |
| 6.4 | Sort independence — flipping spool sort doesn't change filament sort | | |
| 6.5 | Last Used Desc puts most-recently-consumed spool first; never-consumed sort last | | |
| 6.6 | Theme Switch on Settings TopAppBar: Light ↔ Dark applies live without recreate | | |
| 6.7 | Theme persists across cold-start | | |
| 6.8 | Currency segmented row: `$ Dollar` / `€ Euro` / `¤ Money` flips price suffix in MoreDetailsExpander | | |
| 6.9 | Banner only appears when URL configured AND Spoolman unreachable | | |
| 6.10 | Material You dynamic colour visible on Android 12+ device | | |
| 6.11 | No "Test connection" button on Settings; Save button is full-width | | |
| 6.12 | TalkBack reads "Theme: Light (tap to switch to Dark)" / vice versa on Switch | | |

---

## §7. UI polish (U9b carries)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 7.1 | Logo + Settings cog (three-dot) overlay on main screen (no Material 3 TopAppBar) | | |
| 7.2 | Logo tint follows form colour | | |
| 7.3 | Per-section Cards: Spoolman / FilamentForm / MoreDetailsExpander | | |
| 7.4 | Save & Write lifted out of FilamentForm into top-level button | | |
| 7.5 | Temp folded into MoreDetailsExpander as a labelled section (Temperature / Weight / Others) | | |
| 7.6 | IME-aware snackbar — Save and Test connection messages remain visible with keyboard up | | |
| 7.7 | Once-per-session passive-tap hint emits | | |
| 7.8 | "Other" affordance styled with Add icon in primary tint | | |
| 7.9 | "Color Wheel" affordance styled with Palette icon in primary tint | | |
| 7.10 | Snackbar copy review applied — no developer-y leaks (`(timeout)`, raw exception messages, etc.) | | |

---

## §8. Spoolman gating (U9b post-close-out fix)

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 8.1 | No Spoolman URL → FilamentForm expander + MoreDetailsExpander hidden | | |
| 8.2 | URL configured + reachable → both visible + enabled | | |
| 8.3 | URL configured + unreachable → both visible + disabled | | |
| 8.4 | Temperature section visible in all three states (lives on the tag, not Spoolman) | | |
| 8.5 | Offline banner: titleSmall "Spoolman unreachable" + bodySmall detail line, no mid-sentence wrap | | |

---

## §9. Snapmaker U1 round-trip

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 9.1 | Write a tag from SpoolPainter | | |
| 9.2 | Read it back on a Snapmaker U1 printer; lot_nr lookup works (`GET /v1/spool?lot_nr=card_uid:XXXX`) | | |
| 9.3 | Printer surfaces correct material / brand / colour | | |
| 9.4 | Pair a second tag (PairAnotherTagSheet flow); printer recognises both | | |

---

## §10. Release build smoke test

| # | Scenario | Pass / Fail | Notes |
|---|---|---|---|
| 10.1 | Signed release APK installs cleanly (after uninstalling debug variant) | | |
| 10.2 | One read flow on release | | |
| 10.3 | One create-and-pair on release | | |
| 10.4 | Spoolman gating (§8) observed on release | | |
| 10.5 | `adb logcat` shows zero `D/` `I/` `W/` entries from `com.spoolpainter.app` (NFR-5 verified live) | | |
| 10.6 | APK size sanity check (target: ≤ 35 MB after R8) | | |
| 10.7 | Cold-start time on release feels snappy (no obvious R8-induced regression) | | |

---

## Sign-off

- [ ] All scenarios above marked Pass *or* explicit deferral note recorded in `aidlc-docs/ui-followups.md`
- [ ] Snapmaker U1 round-trip completed
- [ ] Release smoke (§10) clean
- [ ] U10 close-out gate met
