# Unit of Work Plan — SpoolPainter v2

**Stage**: INCEPTION → Units Generation (Part 1: Planning)
**Source**:
- Requirements: `aidlc-docs/inception/requirements/requirements.md`
- User stories: `aidlc-docs/inception/user-stories/stories.md` (32 v2.0 + 5 v2.1)
- Application design: `aidlc-docs/inception/application-design/`
- Execution plan: `aidlc-docs/inception/plans/execution-plan.md` (U1..U10 v2.0 + U11..U12 v2.1 preview)

**Project shape (locked)**:
- **Single Gradle module** (`:app`) — no microservices, no multi-module split.
- **Solo developer** — no team-alignment / parallelisation concerns.
- **Brownfield** — existing package layout under `app/src/main/java/com/spoolpainter/app/` is the home for all units.
- **Two release waves** — v2.0 ships first; v2.1 starts after v2.0 ships.

**How this plan works**: Every step has a checkbox `[ ]`. The Questions
section uses `[Answer]:` tags. Fill each in, reply "answered" / "done",
and I will validate, ask any follow-ups, and then run Part 2 (generate
the unit artifacts).

---

## Plan Steps

### A. Analyse Context (rule-Step 3 prep)
- [x] A.1 — Re-read v2.0 + v2.1 stories to confirm story IDs and
  release-wave tags.
- [x] A.2 — Re-read application-design components + use-cases + Hilt
  module groupings.
- [x] A.3 — Cross-reference execution-plan.md's preview decomposition
  (U1..U10 + U11..U12) against the application-design artifacts.

### B. Embed Clarifying Questions (rule-Steps 3–5)
- [x] B.1 — Generated questions across applicable categories:
  Story Grouping, Dependencies, Technical Considerations, Business
  Domain, Release Wave. Skipped Team Alignment (solo) and greenfield
  code-org (brownfield, established structure).
- [x] B.2 — Saved questions in this plan file with `[Answer]:` tags.
- [x] B.3 — Received user answers.

### C. Validate Answers (rule-Steps 7–8)
- [x] C.1 — All 12 original `[Answer]:` tags filled; no blanks.
- [x] C.2 — Ambiguity check: Q-T2 free-text comment flagged →
  Q-FU2=A confirmed B-as-written (AS dev runs not a gate).
- [x] C.3 — Contradiction Q-SG4=B vs Q-RW1=C resolved by Q-FU1=C
  (hard gate; v2.1 parked in docs only).
- [x] C.4 — All answers crisp; no remaining ambiguities.

### D. Approval Gate (rule-Steps 9–11)
- [ ] D.1 — Surface approval prompt: "Unit of work plan complete.
  Ready to proceed to generation?"
- [ ] D.2 — Log prompt + user response in audit.md.
- [ ] D.3 — Mark Units Planning complete in aidlc-state.md.

### E. Generate Unit Artifacts (rule-Steps 12–15, Part 2)
- [ ] E.1 — Generate
  `aidlc-docs/inception/application-design/unit-of-work.md` —
  unit definitions: name, scope, components, source FRs/stories,
  release wave (v2.0 / v2.1), entry/exit criteria.
- [ ] E.2 — Generate
  `aidlc-docs/inception/application-design/unit-of-work-dependency.md`
  — dependency matrix between units; recommended construction
  order; parallelisation opportunities (n/a — solo, but logical
  ordering matters for prerequisites).
- [ ] E.3 — Generate
  `aidlc-docs/inception/application-design/unit-of-work-story-map.md`
  — every story (32 v2.0 + 5 v2.1) assigned to exactly one unit.
- [ ] E.4 — Render the dependency graph as Mermaid + PNG/SVG via
  `mermaid-cli` (consistent with the workflow + component
  diagrams).
- [ ] E.5 — Validate: every story assigned, every component
  assigned, no orphan dependencies.

### F. Final Approval (rule-Steps 16–19)
- [ ] F.1 — Present completion message with summary.
- [ ] F.2 — Wait for explicit user approval.
- [ ] F.3 — Log approval in audit.md.
- [ ] F.4 — Mark Units Generation [x] in aidlc-state.md; advance
  Current Stage to **CONSTRUCTION PHASE**.

---

## Candidate Unit Decomposition (preview from `execution-plan.md`)

This is the working preview. Your answers will tighten or alter it.

### v2.0 (10 units, recommended dependency order)

| # | Unit | Scope (preview) | Stories (preview) |
|---|---|---|---|
| **U1** | Architecture & DI scaffold | Hilt setup; per-layer modules (`NetworkModule`, `RepositoryModule`, `DataStoreModule`, `NfcModule`); per-screen ViewModels skeleton; `StateFlow<UiState>` pattern; DataStore (Settings) wired; sealed `NfcResult`/`NfcIntent` skeleton. | NFR-1, NFR-2, NFR-3 (settings only), S-15.1 |
| **U2** | Domain primitives | `CardUid` canonicalisation (S-1.2), `CardUidEncoding` decode/encode (S-2.1, S-2.2); `OpenSpoolPayload` cleanup; `TagClassification`. | S-1.2, S-2.1, S-2.2 |
| **U3** | Spoolman client overhaul | Lot-nr-filtered list (S-3.1), vendor / filament / spool create chain (S-7.1, S-7.2, S-7.3), PATCH `lot_nr` (S-4.5), error surfacing (sealed `SpoolmanOutcome<T>`), in-memory cache, connectivity StateFlow + `probe()`. | S-3.1, S-7.1, S-7.2, S-7.3, S-4.5, S-10.2, NFR-7 |
| **U4** | NFC repository + state | `NfcRepository` over `NfcAdapterWrapper`; sealed `NfcResult` + `lastSeenTag` TTL buffer (Q-CM1=D); UID extraction (S-1.1); write-then-verify (S-4.4); tag classification. | S-1.1, S-4.4, NFR-1.4, NFR-6 |
| **U5** | Read-and-Pair flow | `ReadAndPairUseCase`, `MainViewModel.onReadTapped`, `SpoolmanDropdown` prefill (S-3.6), passive `OfflineBanner`. | S-3.1–S-3.6, S-10.2 (banner) |
| **U6** | Create-and-Pair + Two-tag | `CreateAndPairUseCase` (Spoolman-first sequencing per FR-4.3), `MoveOnBindUseCase` (S-5.2), `TwoTagUseCase` + `PairAnotherTagSheet` + `RepairConfirmSheet`. | S-4.1, S-4.2, S-4.3, S-4.5, S-5.1, S-5.2, S-6.1, S-6.2, S-6.3, S-6.4 |
| **U7** | Side modes | `RawWriteUseCase` (S-4.7), `VendorUidOnlyPairUseCase` + `VendorUidOnlyOptInSheet` (S-4.8), NDEF-write boundary (S-4.6). | S-4.6, S-4.7, S-4.8 |
| **U8** | Pickers + custom entries | `MaterialPicker`, `BrandPicker`, presets merge (S-8.1, S-8.2), "Add custom" sheets + `MaterialBrandLocalStore` (DataStore Proto, Q-CD2=A) for S-8.3, S-8.4. | S-8.1, S-8.2, S-8.3, S-8.4 |
| **U9** | Settings + theming | `SettingsScreen` URL/sort/theme (S-9.1, S-9.2, S-9.3), Settings-owned **Test connection** (Q-CD1.1=A), dynamic colour + system follow (S-12.1), UI shell (S-13.1, S-13.2). | S-9.1, S-9.2, S-9.3, S-12.1, S-13.1, S-13.2 |
| **U10** | v2.0 release polish | NFR-5 release-build log stripping; manual-NFC verification checklist; **Play Store testing-track release prep** (versionCode 100 / versionName 2.0, signed APK/AAB, tester release notes, no production-track promotion). | NFR-5, NFR-9 (testing-track) |

### v2.1 (deferred — starts after v2.0 ships)

| # | Unit | Scope (preview) | Stories (preview) |
|---|---|---|---|
| **U11** | Vendor decode engine + GPL-3.0 | Port OpenRFID parsers (Bambu / Creality / Anycubic / Elegoo / Qidi / Snapmaker / OpenSpool / TigerTag) to Kotlin; introduce `VendorTagDecoder` interface (Q-CI4 deferred to v2.1); re-licence project; baked vendor data (NFR-12). | S-1.4, S-3.5, NFR-11, NFR-12 |
| **U12** | Vendor key Settings + encrypted storage | Per-vendor key list with Keystore-backed encryption (`EncryptedSharedPreferences` or Tink); UID-only fallback when keys missing. | S-9.4.1, S-9.4.2, NFR-3.4 |

---

## Questions for User Input

> Fill in each `[Answer]:` tag with the letter of your choice. For
> "Other", choose `X` and write your description after the colon. When
> all questions are answered, say "answered" / "done" and I will
> validate.

### Story Grouping

#### Q-SG1 — Decomposition strategy

A) **Layer-then-flow** (preview default): U1=infra/DI, U2=domain
   primitives, U3=Spoolman, U4=NFC, then U5..U7 are user-facing flows
   that depend on U1..U4. Feels natural for a brownfield rewrite —
   foundation first, behaviour after.
B) **Flow-then-layer**: each unit is a vertical slice of one user
   flow (e.g., U-Read = NFC + Spoolman + UI for Read), foundation
   built incrementally per flow. More "vertical slice"; risks
   churning the same files multiple times.
C) **By release-wave only** (single big v2.0 unit + single big v2.1
   unit): too coarse for tracking progress.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

#### Q-SG2 — U6 size / split

The preview's **U6** bundles three flows into one unit (Create-and-Pair,
Move-on-bind, Two-tag) because they share the same VM entry points and
write-then-verify scaffolding. That's also the single biggest unit.

A) **Keep U6 bundled** as `Create-and-Pair + Two-tag`. Pro: avoids
   re-touching `MainViewModel` three separate times. Con: bigger unit
   (~7-10 stories).
B) **Split U6 into U6a (Create-and-Pair) + U6b (Move-on-bind +
   Two-tag)**. Pro: smaller, ships incrementally. Con: U6a and U6b
   both touch `MainViewModel` and share helpers; need a small
   intermediate seam.
C) **Split U6 into U6a (Create-and-Pair) + U6b (Move-on-bind alone)
   + U6c (Two-tag)**. Pro: each unit single-flow. Con: most ceremony;
   shared helpers move twice.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

#### Q-SG3 — U10 (release polish) — own unit or fold into U9?

A) **Own unit (U10)** as previewed — release prep is independent of
   functional changes; signing + release notes + testing-track upload
   has different exit criteria than feature work.
B) **Fold into U9 (Settings + theming + release polish)** — saves a
   unit; release prep typically lives next to "the last functional
   unit."
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

#### Q-SG4 — Are v2.1 units (U11, U12) decomposed in this stage?

A) **Yes, fully** — U11 and U12 get the same treatment as U1..U10.
   Pro: complete unit map up front. Con: details may shift between
   v2.0 ship and v2.1 start.
B) **Yes, but lightweight** — list U11 and U12 with scope + source
   stories only; full decomposition deferred to a separate Units
   Generation pass after v2.0 ships.
C) **No** — out of scope for this stage; v2.1 units are documented
   only at the execution-plan-preview level.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

### Dependencies

#### Q-D1 — Inter-unit communication

A) **Direct Kotlin types** — units are organised package-by-package;
   inter-unit communication is plain class/function calls. No
   service-locator, no event bus. (Recommended for single-module
   monolith.)
B) **Per-unit interfaces** — each unit defines a public interface
   (e.g., `NfcRepository` is the public surface of U4) that other
   units depend on, hiding the implementation. Pro: cleaner unit
   boundary. Con: ceremony for a single-module app.
C) **Hybrid** — primary cross-unit boundaries (Repository ↔ VM ↔
   UseCase) use interfaces; everything else is plain classes.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C

#### Q-D2 — Order of construction (U1..U10 within v2.0)

A) **Recommended dependency order** (preview): U1 → U2 → U3 → U4 →
   U5 → U6 → U7 → U8 → U9 → U10. Each unit can build only after its
   prerequisites are merged.
B) **Foundation parallel, flows sequential**: U1, U2, U3, U4 can be
   tackled in any interleaving (foundation work, low risk of
   conflict); then U5..U10 strictly sequential. (Useful only if more
   than one developer is involved — n/a for solo.)
C) **Strict sequential** — even foundation units done one at a time,
   no interleaving.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

#### Q-D3 — Where do shared helpers live (e.g., between U5 and U6 — read-and-pair vs create-and-pair both touch SpoolmanRepository.findSpoolsByCardUid)?

A) **Promote to U3 (Spoolman client)** — any helper that talks to
   Spoolman is part of U3 by definition; U5/U6 only orchestrate.
B) **Per-flow file in U5/U6 with a `// TODO promote if reused` note**
   — let the second consumer trigger the promotion.
C) **Use-case base class** in a fifth-layer "common-flow" mini-unit.
   Adds a unit just to hold shared scaffolding.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

### Technical Considerations

#### Q-T1 — Definition of Done per unit

A) **Strict** — code merged, unit tests for that unit's
   testable surface (per NFR-4.1) passing, manual NFC verification
   for that unit's flow (where applicable), debug build of the
   running app exercises the unit's happy path. (Recommended.)
B) **Lighter** — code merged + unit tests passing only.
C) **Per-unit decision** — set DoD inside each unit's Functional
   Design.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

#### Q-T2 — Should each unit produce a release-able debug build, or only the final v2.0 unit?

A) **Each unit** — build + install on device after every unit;
   regression-test by hand. Pro: catches integration issues fast.
   Con: time per unit goes up.
B) **Major-milestone units only** — install after U1 (skeleton),
   U5 (read works), U6 (write works), U10 (release polish). Other
   units verified by tests + visual inspection.
C) **Only the final v2.0 unit (U10)** — biggest "big bang" risk.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B, i will be building app using android studio to test at the spot

#### Q-T3 — Per-unit test surface (NFR-4.1)

A) **Pure unit tests only** for that unit's logic (matches NFR-4.1
   minimum bar — `OpenSpoolData`, `CardUidEncoding`, `CardUid`
   canonicalisation, `SpoolmanRepository` against fake API). No
   ViewModel / Compose / instrumented NFC tests.
B) **Add ViewModel tests where feasible** — especially U5 / U6 / U7
   where flow logic is non-trivial. Goes beyond NFR-4.1 minimum.
C) **Defer test scope to per-unit Functional Design** — let each
   unit decide.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

### Business Domain

#### Q-BD1 — Domain boundaries reflected in the unit map?

The application design has 5 logical domains: **NFC**, **Spoolman**,
**Settings**, **Pickers/Local-data**, **UI shell**. The preview U-map
mostly aligns to these domains.

A) **Keep alignment as-is** — units mirror the domains. (Recommended.)
B) **Re-cut units around user-facing flows** — domains aren't visible
   to the user; flows are. (Same as Q-SG1=B "Flow-then-layer".)
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

### Release Wave

#### Q-RW1 — Hard gate between v2.0 (U1..U10) and v2.1 (U11..U12)?

A) **Hard gate** — v2.1 units cannot start until v2.0 has shipped to
   the testing track per execution-plan.md (and per the user's
   "starting v2 with test track" decision). v2.1 units are
   documented but not built.
B) **Soft gate** — v2.0 ships to testing track first, then v2.1
   construction can start in parallel with v2.0 testing-track
   feedback. Risk: v2.0 bug-fixes and v2.1 feature work share the
   same package.
C) **No gate** — interleave v2.0 and v2.1 work freely.
X) Other (please describe after `[Answer]:` tag below)

[Answer]:  C

---

## After You Answer

When all `[Answer]:` tags are filled in, reply with **"answered"** /
**"done"**. I will:
1. Validate each answer (no blanks, no obvious ambiguity).
2. Append follow-ups for any vague answer; loop until crisp.
3. Surface the Part-1 approval prompt: "Unit of work plan complete.
   Ready to proceed to generation?"
4. On approval, run Part 2 — generate `unit-of-work.md`,
   `unit-of-work-dependency.md`, `unit-of-work-story-map.md`, plus the
   rendered dependency diagram (Mermaid + PNG/SVG).
5. Surface the Units Generation final approval gate before advancing
   to **CONSTRUCTION PHASE**.

---

## Validation Pass — 2026-05-24

All 12 `[Answer]:` tags filled. Two items need confirmation before
proceeding to the approval gate.

### Contradiction — Q-SG4 ↔ Q-RW1

**Q-SG4 = B**: "lightweight v2.1 decomposition; full decomposition
deferred to a **separate Units Generation pass after v2.0 ships**."

**Q-RW1 = C**: "**No gate** — interleave v2.0 and v2.1 work freely."

These conflict: if v2.0 and v2.1 work can interleave freely, v2.1 work
could begin **before v2.0 ships**, but Q-SG4=B postpones full v2.1
decomposition **until after v2.0 ships**. You can't start v2.1
construction without a decomposition.

#### Q-FU1 — Resolve the contradiction

A) **Keep Q-RW1=C, upgrade Q-SG4 to A** — fully decompose v2.1 (U11,
   U12) now, so v2.1 work can start any time without waiting on a
   second Units Generation pass. (Most flexibility, most upfront work
   on v2.1 details that may shift.)
B) **Keep Q-SG4=B, soften Q-RW1 to B (soft gate)** — v2.1 stays
   lightweight now; v2.0 must ship to testing track first; once
   shipped, a quick Units Generation top-up decomposes v2.1 fully and
   then v2.0 testing-track bug-fixes and v2.1 feature work can run
   in parallel. (Recommended for solo developer with shifting v2.1
   scope.)
C) **Keep Q-SG4=B, harden Q-RW1 to A (hard gate)** — v2.1 stays
   lightweight, can't begin in any form until v2.0 ships to testing
   track; cleanest separation, slowest cadence.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C — "i dont really care about v2.1 yet, so let it park in
documentation for now". Resolves Q-SG4=B + Q-RW1=A (hard gate). v2.1
units U11/U12 are documented lightweight only (scope + source stories);
no v2.1 construction begins until v2.0 ships to testing track.

### Confirmation — Q-T2 commentary

**Q-T2 = "B, i will be building app using android studio to test at
the spot"**

Reading this as: **Q-T2 = B is the workflow rule** (formal install +
manual regression checks at U1 / U5 / U6 / U10 milestones), and the
"test at the spot" note is just normal Android Studio Run-on-device
behaviour during development, not an additional gate. Confirm
[Answer] = "yes" if that matches your intent, or pick A / X otherwise.

#### Q-FU2 — Confirm Q-T2 reading

A) **Yes — B as written** — milestone install gates at U1/U5/U6/U10;
   ad-hoc AS runs in between are dev-flow, not a workflow gate.
B) **Actually closer to A** — install on device after **every** unit;
   the "test at the spot" comment was the real intent.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A — "app on phone will be dev work and i am good with that,
no need to build etc". Confirms Q-T2=B as the workflow rule; Android
Studio Run-on-device during dev is not a workflow gate. Note: with
Q-FU1=C, U10 is the v2.0 testing-track release prep unit, so the U10
install gate doubles as the testing-track release validation step.

### Final Answer Set (all crisp)

| Q | Answer | Notes |
|---|---|---|
| Q-SG1 | A | Layer-then-flow decomposition. |
| Q-SG2 | B | Split U6 → U6a (Create-and-Pair) + U6b (Move-on-bind + Two-tag). |
| Q-SG3 | A | U10 stays its own unit. |
| Q-SG4 | B → constrained by Q-FU1=C | v2.1 parked: U11/U12 documented lightweight only (scope + source stories); no full decomposition until v2.0 ships. |
| Q-D1 | C | Hybrid: interfaces at primary cross-unit boundaries (Repository ↔ VM ↔ UseCase), plain classes elsewhere. |
| Q-D2 | A | Strict dependency order: U1 → U2 → U3 → U4 → U5 → U6a → U6b → U7 → U8 → U9 → U10. |
| Q-D3 | A | Spoolman-touching helpers live in U3. |
| Q-T1 | B | DoD = code merged + unit tests passing. |
| Q-T2 | B (per Q-FU2=A) | Milestone install gates at U1 / U5 / U6 / U10; ad-hoc AS runs in between are dev-flow, not workflow gates. With Q-FU1=C, U10's install gate doubles as the testing-track release validation. |
| Q-T3 | B | Add ViewModel tests (especially U5 / U6a / U6b / U7). Goes beyond NFR-4.1 minimum. |
| Q-BD1 | A | Units mirror the 5 application-design domains. |
| Q-RW1 | C → revised to A by Q-FU1=C | **Hard gate**: v2.1 cannot start until v2.0 ships to testing track. |

### Approval Gate (rule-Step 9)

> **Unit of work plan complete. Review the plan in
> `aidlc-docs/inception/plans/unit-of-work-plan.md`. Ready to proceed
> to generation?**

On approval I will run Part 2 and generate:
1. `aidlc-docs/inception/application-design/unit-of-work.md` — full
   definitions for U1, U2, U3, U4, U5, **U6a, U6b** (Q-SG2=B),
   U7, U8, U9, U10 + lightweight stubs for U11, U12.
2. `aidlc-docs/inception/application-design/unit-of-work-dependency.md`
   — dependency matrix in strict order U1→U2→U3→U4→U5→U6a→U6b→U7→U8→U9→U10
   with hard gate before U11/U12.
3. `aidlc-docs/inception/application-design/unit-of-work-story-map.md`
   — every v2.0 story (32) assigned to exactly one unit; every v2.1
   story (5) parked under U11/U12 stubs.
4. Mermaid + PNG + SVG dependency diagram via `mermaid-cli`.
