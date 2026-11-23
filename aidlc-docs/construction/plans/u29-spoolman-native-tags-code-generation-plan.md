# U29 — Code Generation Plan

**Unit**: U29 — Spoolman 0.27 native tags, with a user-chosen migration (UI-71)
**Opened**: 2026-11-22 (planning only, Part 1). **Not started.**
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP**. The design was decided
by the maintainer 2026-11-22 and is recorded in `ui-followups.md` UI-71. This
plan implements that design and does not re-open it.
**Source**: GitHub #19 (sibaar), firmware coordination on
paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware #805 (firmware #867 merged
2026-11-04), external PR #20 (printing-enthusiast).
**This plan is the single source of truth for U29 Code Generation.**

---

## §1 Design (from UI-71)

1. **Spoolman older than 0.27**: no store option shown; `card_uids` only, as today.
2. **Spoolman 0.27+, Both (default)**: new pairs write `card_uids` and a native
   tag. Existing tags get a native tag only on re-pair or move, **never on a
   plain Read**. Lookup checks both; **a native tag wins**.
3. **Switch to native tags**: shown only on 0.27+. Migrates everything behind a
   clear message: copies every `card_uids` UID, **archived spools included**;
   **keeps `card_uids`**; skips a UID found on more than one spool and **lists
   which spools**; tells the user to switch the printer in firmware-config.
4. **After the switch**: **stop writing `card_uids`**; lookup reads native tags
   only. `card_uids` becomes a frozen snapshot (accepted).

## §2 Verified, not assumed (2026-11-22)

- **No "remove tag" action exists.** `removeCardUidFromSpool`
  (`SpoolmanRepository.kt:341`) has one caller, move-on-bind
  (`MoveOnBindUseCaseImpl.kt:46`). The only native unlink is inside a move.
- Today's lookup downloads up to 1000 spools and scans `extra.card_uids` on the
  phone (`SpoolmanRepository.kt:93-102`). `card_uids` is a comma-joined string,
  JSON-encoded (`ExtraCardUidsCodec.kt:10-11`).
- The version probe already exists: `GET api/v1/info` (`SpoolmanApi.kt:18-19`)
  through `testConnection()` (`SpoolmanRepository.kt:86-91`), returning
  `SpoolmanInfo.version`.
- `performHttp` maps a null body to `ParseError`. Spoolman's tag unlink returns
  **204 with no body**, which Retrofit hands back as null. So an unlink must be
  allowed an empty body (this is PR #20's blocking bug).
- Two matches become `Ambiguous` (`ReadAndPairUseCase.kt:81`), so "native wins"
  has to be applied **before** the result reaches that branch.
- `Settings` (`data/local/Settings.kt`) holds one Spoolman `url`, so the store
  choice is one app setting.

## §3 Decisions needed before Part 2

- **D1 (open in UI-71)** — Switching back from native to Both: allowed? If yes,
  what happens to `card_uids` that went stale while native was on?
- **D2 (open in UI-71)** — Spoolman downgraded below 0.27, or unreachable, after
  the switch: fall back to `card_uids`, or show an error?
- **D3** — Build on PR #20 or start fresh on `v2`? Recommended: **fresh on
  `v2`**, reusing PR #20's endpoint definitions where the review found them
  correct, with a `Co-authored-by` trailer for printing-enthusiast. PR #20 is
  based on 2.3.1-era `main`, conflicts in 7 files, and its migration, move path
  and lookup all differ from the design.
- **D4** — No "`card_uids` only" option on 0.27+. Both already serves a printer on
  `card_uids`. Confirm.

## §4 Steps

- [ ] **S0** — Prerequisite: Android SDK on this Mac (see U28 S0). Baseline suite
  green.
- [ ] **S1** — Model: `Settings.tagStore` with values Both (default) and Native.
  Old settings payloads read back as Both.
- [ ] **S2** — Version gate: parse `SpoolmanInfo.version`; native tags are
  supported at **0.27.0 or newer**. Unknown or older means `card_uids` only,
  whatever the setting says (subject to D2).
- [ ] **S3** — API: native tag link (`POST api/v1/spool/{id}/tag` with
  `{"uid": ...}`), unlink (`DELETE api/v1/spool/{id}/tag/{uid}`), the `tag` query
  on the spool list, and `tags` on the spool model. Allow an empty body on 204.
- [ ] **S4** — Writes: `appendCardUidToSpool` (`:315`) and
  `removeCardUidFromSpool` (`:341`) follow the effective store. Both:
  `card_uids` plus native. Native: native only. Older server: `card_uids` only.
  Define how a partial failure (one store written, the other not) is reported.
- [ ] **S5** — Lookup: `findSpoolsByCardUid` (`:93`). Both: if a native tag holds
  the UID, return that spool only (native wins); otherwise fall back to
  `card_uids`. Native: server-side `?tag=` only.
- [ ] **S6** — Move-on-bind: no flow change. Holders come from S5, removal and
  adding go through S4. Spoolman's 409 is a safety net that re-enters the same
  confirm flow; no second move path that skips the confirmation.
- [ ] **S7** — No write on Read: check every Read path and pin it with a test.
- [ ] **S8** — Migration use case, run only when the user switches: count,
  confirm, copy all UIDs (archived included), skip UIDs held by more than one
  spool, keep `card_uids`, and report the result with the skipped spools.
  Re-running it must be safe (already-linked UIDs are not errors).
- [ ] **S9** — Settings UI: the store section appears only on 0.27+. The switch
  message says to switch the printer in firmware-config and that `card_uids`
  will no longer be updated.
- [ ] **S10** — Tests:
  - unlink with a real 204 shape (`Response` with code 204 and a null body), not
    a fake that returns a body;
  - native wins over a stale `card_uids` entry, so no `Ambiguous`;
  - duplicate UIDs are skipped and listed;
  - archived spools are migrated;
  - the version gate hides native tags below 0.27;
  - Native mode stops `card_uids` writes;
  - a Read writes nothing;
  - non-vacuity: break S5's native-wins rule and confirm its tests fail.
- [ ] **S11** — Build matrix: `compileDebugKotlin`, `testDebugUnitTest`,
  `assembleDebug`.
- [ ] **S12** — Install gate against a real Spoolman 0.27 server; a U1 in both
  firmware store modes if available.
- [ ] **S13** — Docs: release notes, README, What's new, UI-71 state,
  `aidlc-state.md`.
- [ ] **S14** — GitHub (approval needed): respond on PR #20 and #19, and tell
  paxx12 on #805 when it ships so the apps table and dates can be agreed.

## §5 Release

No version decided. The next Play upload needs **versionCode 117 or higher**.
