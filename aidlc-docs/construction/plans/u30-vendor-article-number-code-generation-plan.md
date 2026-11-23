# U30 — Code Generation Plan

**Unit**: U30 — Vendor tag product codes saved as Spoolman `article_number` (UI-72)
**Opened**: 2026-11-22 (planning only, Part 1). **Not started.**
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP**. The code is an external
contribution, already written and reviewed. This unit is verify, decide, merge.
**Source**: GitHub #11 (Anycubic SKU into `article_number`), external **PR #14**
by kpaaro, branch `feat/article-number-from-vendor-tags`, targeting `v2`.
**This plan is the single source of truth for U30.**

---

## §1 What PR #14 does (from its description, checked against the diff)

- Reads the product code from three vendor formats: Anycubic SKU (e.g.
  `HPL18-106`), Bambu material variant ID (e.g. `A00-G1`), Snapmaker SKU. Qidi,
  Elegoo and Creality tags carry none.
- The form shows a read-only **Article number**: Spoolman's value when the
  filament has one, otherwise the tag's code with a hint.
- **Save** creating a new filament sends the code in the create request. Save
  matching an existing filament fills `article_number` only when empty. Save on a
  picked spool or filament never changes it.
- **Map tag** fills the paired filament's `article_number` only when empty
  (`fillArticleNumberIfEmpty`), in the background; a failed fill shows a snackbar.
- Garbled codes (U+FFFD, control characters, Snapmaker `0` / `0xFFFFFFFF`) are
  dropped. Editing material, brand, colour or variant drops the tag's code.
- `ObservedTagKind.Vendor` becomes a data class carrying the code.

## §2 Verified (2026-11-22, static review, not run)

- Based on the `v2` tip `032356b`; `git merge-tree` with `origin/v2` is clean, and
  also clean together with PR #13.
- 31 files, +917/-59, about 60% tests. The PR reports 688 tests passing (v2
  baseline 643) and lint clean, run by the contributor on JDK 21.
- **No silent overwrite**: an existing Spoolman `article_number` always wins and
  is shown read-only.
- No Gradle build files changed.
- Its four on-phone checks are **unticked**.
- Minor, from review:
  - the first code written sticks on a filament record reused across colours;
  - the matched-filament fill on Save is fire-and-forget, with no report if it
    fails (Map tag does report);
  - there is a tiny GET-then-PATCH race;
  - it fills empty fields without an opt-in.

## §3 Decisions needed before Part 2

- **D1 — scope.** Issue #11 asked only for Anycubic; the PR also does Bambu and
  Snapmaker. Keep all three (recommended: same "only when empty" rule, no
  overwrite risk), or put Bambu and Snapmaker behind a setting?
- **D2 — the minor items in §2.** Accept as-is (recommended), or ask kpaaro for a
  follow-up?
- **D3 — merge order with U28.** PR #14 also edits `SnapmakerFormat.kt` (4 lines,
  the SKU read). Recommended order: **PR #13 → PR #14 → U28 code**, so U28
  builds on both and its rebase stays trivial.

## §4 Steps

- [ ] **S0** — Prerequisite: Android SDK on this Mac (see U28 S0).
- [ ] **S1** — Fetch the PR branch and confirm the diff still matches the
  reviewed head `774c18f` (re-review anything new).
- [ ] **S2** — On the PR branch: `testDebugUnitTest` (expect about 688, 0
  failures), `lintDebug`, `assembleDebug`.
- [ ] **S3** — Apply D1 (and D2 if a follow-up is requested).
- [ ] **S4** — On-phone checks, the four from the PR's test plan:
  - [ ] Read an Anycubic / Bambu / Snapmaker tag: the form shows the tag's code
    with the hint.
  - [ ] Map tag to a spool whose filament has no article number: Spoolman gets
    the code.
  - [ ] Map tag to a filament that already has one: value unchanged.
  - [ ] Save a new filament from a vendor tag: the created filament has the code.
- [ ] **S5** — Regression on the same build: an OpenSpool tag and a blank tag
  still read, write and pair as before.
- [ ] **S6** — GitHub (approval needed): merge PR #14 into `v2` per D3; reply on
  #11.
- [ ] **S7** — Docs: release notes, README What's new, UI-72 state,
  `aidlc-state.md`.

## §5 Release

No version decided. The next Play upload needs **versionCode 117 or higher**.
