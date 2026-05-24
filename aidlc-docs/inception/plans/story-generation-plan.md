# SpoolPainter v2 — Story Generation Plan

**Status**: Answers received — awaiting explicit plan approval before Part 2.
**Date**: 2026-05-23
**Source requirements**: `aidlc-docs/inception/requirements/requirements.md`
**Assessment**: `aidlc-docs/inception/plans/user-stories-assessment.md`

This file has two parts:
- **Section A — Story Plan** (checklisted execution plan for Part 2 of the
  User Stories stage). Decisions locked from Section B answers are inlined
  here. Boxes will be checked as Part 2 executes (after approval).
- **Section B — Clarifying Questions** (`[Answer]:` tags). Filled in by
  user; preserved for audit trail.

---

## Decisions Locked (from Section B answers)

| # | Question | Answer | Lock |
|---|---|---|---|
| 1 | Personas | A + B + C, **D excluded**; E folded into A. C carries the new "branded-tag user can finally track usage in Spoolman via UID-only mapping" value prop. | 3 personas: Connected Hobbyist (incl. two-tag flow), Offline Tinkerer, Branded-Tag Reader. No Vendor-Key Power User persona — v2.1 vendor-key Settings stories attributed to Branded-Tag Reader. |
| 2 | Persona-E granularity | B | Two-tag flow is a behaviour mode of Connected Hobbyist; FR-6 stories tagged with that persona. |
| 3 | Story format | A | Classic Connextra ("As a … I want … so that …") for every story. |
| 4 | Story granularity | A | Small / unit-of-work-granular; FR-4 / FR-5 / FR-6 will split into multiple ≤2-day stories. |
| 5 | Breakdown | A | Feature-Based, sections mirror FR-1..FR-15. |
| 6 | AC style | **B** | **Bullet checklist** AC (overrides A.2's earlier GWT default). |
| 7 | Error/edge paths | B | No standalone error stories — folded into parent story as additional AC bullets. |
| 8 | Priority | B | Release-wave tag only (v2.0 / v2.1); no MoSCoW. |
| 9 | NFR-as-stories | B | NFRs stay in requirements.md only; no platform-behaviour stories. |
| 10 | File scope | C | Single `stories.md`, two top-level sections `## v2.0` and `## v2.1`. |
| 11 | DoD | A | Code merged + unit tests passing (matches NFR-4.1). |
| 12 | Anything missing | (left blank) | Treated as "none" — preserved for traceability. |

---

## Section A — Story Plan (checklist for Part 2)

### A.1 Persona drafting
- [x] Draft `aidlc-docs/inception/user-stories/personas.md` with **3
      personas** (Q1, Q2):
  - **Connected Hobbyist** — Spoolman on LAN; OpenSpool tags; primary
    v2.0 persona; **two-tag flow folded in as a behaviour mode**
    (Q2=B), so FR-6 stories carry this persona.
  - **Offline Tinkerer** — no Spoolman; raw-write side mode (FR-4.7)
    only; v2.0.
  - **Branded-Tag Reader** — Bambu / Creality / etc. pre-encoded
    tags; **value prop: can finally track spool usage in Spoolman
    via UID-only mapping** (per Q1 freeform addition); v2.0 = read
    UID + protect tag; v2.1 = decode payload + Settings vendor keys.
    No standalone "Vendor-Key Power User" persona — those v2.1
    Settings stories attribute to this persona.
- [x] Each persona includes: name, archetype, environment (Spoolman /
      offline / branded tags), goals, frustrations with v1, success
      criteria for v2.
- [x] Cross-reference each persona to the v2.0 / v2.1 release wave it
      lives in.

### A.2 Story authoring
- [x] Author `aidlc-docs/inception/user-stories/stories.md` with
      INVEST-compliant stories (Independent, Negotiable, Valuable,
      Estimable, Small, Testable).
- [x] Use **Connextra format** (Q3=A): "As a `<persona>`, I want
      `<capability>`, so that `<outcome>`."
- [x] Group **Feature-Based** (Q5=A): sections mirror FR-1..FR-15 in
      `requirements.md`.
- [x] **Granularity = Small / unit-of-work** (Q4=A): split FR-4 (4–6
      stories), FR-5, FR-6, FR-7 into independently buildable ≤2-day
      stories.
- [x] Tag each story with: persona(s), **release wave (v2.0 / v2.1)**
      (Q8=B; no MoSCoW), source FR id(s) for traceability.
- [x] Each story SHALL include acceptance criteria as a **bullet
      checklist** (Q6=B). Named non-happy paths from FR-4.4, FR-4.6,
      FR-5.2, FR-7.4, FR-10.2 are folded into the parent story as
      additional AC bullets (Q7=B).
- [x] Single `stories.md` with two top-level sections `## v2.0` and
      `## v2.1` (Q10=C).
- [x] **No standalone NFR stories** (Q9=B); NFRs stay in
      `requirements.md`. (Two v2.1 NFR stubs retained — S-NFR11
      licensing + S-NFR12 vendor-data baking — flagged in story body
      as exceptions tightly coupled to the v2.1 release cut.)

### A.3 Coverage check
- [x] Map every FR (FR-1.1 .. FR-15.1) to at least one story; flag any
      uncovered FR in a "Coverage gaps" appendix. (See "Coverage map"
      table at end of stories.md — no v2.0 gaps; v2.1 narrowness is
      intentional per requirements §3.)
- [x] Map every NFR that is user-observable (NFR-6, NFR-7, NFR-9,
      NFR-11) — folded as AC bullets across stories (NFR-6 / NFR-7) or
      retained as platform-behaviour note (NFR-9) or v2.1 stub (NFR-11).
- [x] Flag any story that exceeds INVEST "Small" — none flagged at
      authoring; FR-4 / FR-5 / FR-6 / FR-7 each split per Q4=A.

### A.4 Persona-to-story matrix
- [x] Append a matrix to stories.md showing which personas care about
      which stories. (Persona ↔ story matrix is at end of stories.md.)

### A.5 Acceptance-criteria → test seed
- [x] For every acceptance criterion, annotate `[unit]` (NFR-4.1
      target) or `[manual]` (manual / out-of-scope) — done inline in
      every story.

### A.6 Audit + state update
- [x] Append "Stories generated" entry to `aidlc-docs/audit.md` with
      timestamp and artifact paths.
- [x] Update `aidlc-docs/aidlc-state.md` to mark User Stories stage
      complete and Current Stage = Workflow Planning.

### A.7 Approval gate
- [x] Present standardized completion message per
      `inception/user-stories.md` Step 20.
- [x] Wait for "Request Changes" or "Approve & Continue". → Approved
      2026-05-23 ("looks good") after four revision passes (P3
      vendor-tag flow + opt-in copy + dropdown prefill / always-spool_id
      + temp-`lot_nr` framing & extras research).

---

## Section B — Clarifying Questions

Please fill each `[Answer]:` tag with the letter that matches your choice.
Use **X) Other** with a short freeform note if none of the lettered options
fit. When all are answered, say **"done"** in chat.

### Question 1 — Persona set

Which personas should be modelled? (Multi-select: list all letters that
apply, comma-separated.)

A) **Connected Hobbyist** — runs Spoolman on LAN, mostly OpenSpool tags,
   wants fast pairing of fresh spools and quick re-pair when reusing
   tags. (Primary v2.0 persona.)
B) **Offline Tinkerer** — has no Spoolman or doesn't want to use it; uses
   the raw-write side mode (FR-4.7) to put OpenSpool data on tags for
   the printer firmware only. (v2.0.)
C) **Branded-Tag Reader** — owns Bambu / Creality / etc. spools whose
   tags ship pre-encoded by the vendor; wants the app to *read* them
   without overwriting and (v2.1) decode them to pre-fill the form.
   (v2.0 = read UID + protect tag; v2.1 = decode payload.)
D) **Vendor-Key Power User** — willing to source per-vendor decryption
   keys (e.g., Bambu Mifare keys), enters them in Settings to unlock
   v2.1 decoding. (v2.1 only.)
E) **Two-Tag Workflow User** — runs the optional "pair the second tag"
   flow end-to-end on every fresh spool. (Cuts across A/B/C — model
   as its own persona, or fold into A?)
X) Other (please describe after [Answer]: tag below)

[Answer]:  A, B, C(also thing to consider, now branded tag reader can finally track usage of spool using spoolman),E- fold in A

---

### Question 2 — Persona-E granularity

If you chose E above (or selected "all"), should "Two-Tag Workflow User"
be modelled as a **distinct persona** or as a **behaviour mode** within
Connected Hobbyist?

A) Distinct persona — gets its own goals + AC; clearer mapping for
   FR-6 stories.
B) Behaviour mode of Connected Hobbyist — folded in; FR-6 stories
   simply tag both personas. (Simpler personas.md.)
C) N/A — I did not include E.
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

### Question 3 — Story format

Which format should each user story use?

A) **Classic Connextra**: "As a `<persona>`, I want `<capability>`, so
   that `<outcome>`." + bulleted acceptance criteria in
   Given / When / Then.
B) **Job Story**: "When `<situation>`, I want to `<motivation>`, so I
   can `<expected outcome>`." + AC.
C) Mix — Connextra by default; Job Story for stories where the
   situation/trigger matters more than the persona (e.g., move-on-bind).
X) Other (please describe after [Answer]: tag below)

[Answer]:  A

---

### Question 4 — Story granularity

How small should each story be?

A) **Small / unit-of-work granular** — every story is independently
   buildable in roughly ≤ 2 days; FR-4 (Write/Create-and-Pair) splits
   into 4–6 stories. Higher count, easier per-unit slicing.
B) **Capability-grain** — one story per FR section (FR-3, FR-4, FR-5,
   FR-6, …). Lower count, broader stories; child tasks emerge in
   Workflow Planning instead. (Closer to v1's likely working style.)
C) Mix — capability-grain for read/write/pair flows; small-grain for
   error paths (write-then-verify mismatch, vendor-tag protection,
   PATCH failure mid-move-on-bind).
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

### Question 5 — Story breakdown / grouping

How should stories be organised in `stories.md`?

A) **Feature-Based** — sections mirror requirements.md FR-1..FR-15.
   Easy traceability.
B) **User Journey-Based** — grouped by end-to-end journeys
   (e.g., "Pair a fresh spool", "Re-pair a reused tag", "Write a tag
   without Spoolman").
C) **Persona-Based** — grouped by persona; a story may be repeated
   per persona if the AC differ.
D) **Hybrid** — primary grouping = Feature-Based (A); each story tagged
   with journey + persona for cross-reference. (Defaults to A's
   traceability while preserving journey/persona views.)
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

### Question 6 — Acceptance-criteria style

What style and depth of AC should each story carry?

A) **Given / When / Then** — one AC per branch (happy path + named
   failure modes). Verbose but testable.
B) **Bullet checklist** — short imperative bullets
   ("UID is canonical lowercase hex", "PATCH fails ⇒ banner shown,
   no commit"). Compact.
C) Mix — Given/When/Then for multi-branch flows (FR-3, FR-4, FR-5,
   FR-6); bullet checklist for single-fact rules
   (FR-1.2 UID format, FR-14.1 payload shape).
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

### Question 7 — Coverage of error / edge paths

Which non-happy paths must have their own stories (vs. being a single
"error handling" AC bullet on the happy-path story)?

A) Each named failure gets its own story:
   - Write-then-verify mismatch (FR-4.4)
   - Re-pair partial commit failure (FR-5.2)
   - Spoolman PATCH failure during create chain (FR-7.4)
   - Vendor-tag write attempt (FR-4.6)
   - Spoolman unreachable banner + retry (FR-10.2)
B) Folded into the parent happy-path story as additional AC bullets.
C) Mix — parent story carries the AC; the named failures listed under
   A get a one-line "error story" stub for traceability/test mapping
   only.
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

### Question 8 — Priority labels

How should story priority be expressed?

A) **MoSCoW** — Must / Should / Could / Won't (v2.0 vs v2.1 implied
   via release-wave tag).
B) **Release-wave only** — v2.0 vs v2.1, no further priority.
C) Both — every story carries a release wave AND a MoSCoW label.
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

### Question 9 — Persona-to-FR coverage

Should I include explicit "platform behaviour" stories for NFRs that the
user observes indirectly (e.g., "all writes are verified"; "no partial
state on Spoolman failure"; "v2.1 ships under GPL-3.0 with source
offer")?

A) Yes — one story per user-observable NFR cluster, persona = "all".
B) No — NFRs stay in requirements.md only; stories are FR-only.
C) Yes for reliability/error NFRs (NFR-6, NFR-7); no for distribution
   / licensing (NFR-9, NFR-11).
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

### Question 10 — Story file scope (release-wave split)

Should v2.0 and v2.1 stories live in:

A) A single `stories.md` with stories tagged by wave. (Easier to read
   together; one file Workflow Planning consumes.)
B) Two files: `stories-v2.0.md` and `stories-v2.1.md`. (Clear scope
   boundary; downstream stages opt into one or both.)
C) Single file with two top-level sections "## v2.0" and "## v2.1".
   (Compromise.)
X) Other (please describe after [Answer]: tag below)

[Answer]: C

---

### Question 11 — Definition of Done at story level

What does "done" mean for an individual story when v2 ships?

A) **Code merged + unit tests passing** (matches NFR-4.1 minimum bar).
B) **Code merged + unit tests + manual smoke on a real Android device
   with NFC** (closer to release-grade).
C) **Code merged + unit tests + repository tests against a local
   Spoolman instance** (covers FR-3.2, FR-5, FR-7 flows end-to-end).
D) Mix — A by default; C for stories that touch the Spoolman create
   chain or move-on-bind; B for stories that touch NFC write paths.
X) Other (please describe after [Answer]: tag below)

[Answer]: A
[story-generation-plan.md](story-generation-plan.md)[user-stories-assessment.md](user-stories-assessment.md)
---

### Question 12 — Anything missing?[user-stories-assessment.md](user-stories-assessment.md)

Is there a flow, persona, or scenario the requirements doc captures but
that you specifically want emphasised (or de-emphasised) in stories?
(Free-form. If "none", say so after the [Answer]: tag.)

[Answer]: 

---

## After answering

When all `[Answer]:` tags are filled, say **"done"** in chat. I will:
1. Read this file and extract answers.
2. Analyse them for ambiguity / contradictions per the AIDLC rules; if
   any are found, I'll create
   `aidlc-docs/inception/plans/story-planning-clarification-questions.md`
   and ask you to fill those in too.
3. Otherwise, log the approval prompt in `audit.md` and ask for explicit
   approval of this plan, which gates Part 2 (story generation).
