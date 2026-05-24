# User Stories Assessment — SpoolPainter v2

## Request Analysis
- **Original Request**: "Using AI-DLC, I want to plan v2 of SpoolPainter."
- **User Impact**: Direct — major user-facing behavioral pivot (tag-UID-as-key,
  read-and-pair, write-and-pair, two-tag flow, re-pair, raw-write side mode,
  vendor-tag protection, custom material/brand picker, v2.1 multi-vendor
  decode + Settings keys UI).
- **Complexity Level**: Complex — system-wide rewrite touching every layer
  (UI, ViewModel, NFC, Spoolman client) plus a new vendor-decode subsystem
  in v2.1.
- **Stakeholders**: Single product owner / developer / primary user (3D
  printing hobbyists). No external stakeholders, but multiple distinct
  *user contexts* exist (Spoolman-connected vs. offline; OpenSpool-tag
  user vs. branded-tag user; first-time pair vs. re-pair vs. tag-reuse).

## Assessment Criteria Met
- [x] **High Priority — New User Features**: v2 introduces several new
  user-facing flows that don't exist in v1 (read-and-pair, two-tag
  flow, re-pair, raw-write mode, custom-add picker, v2.1 vendor keys).
- [x] **High Priority — User Experience Changes**: Single-screen UX is
  retained, but multi-step flows now appear as modal bottom sheets
  (FR-13.2) — interaction pattern changes meaningfully.
- [x] **High Priority — Multi-Persona Systems**: Distinct usage modes
  (Spoolman-connected vs. offline raw-writer vs. branded-tag-only reader)
  warrant explicit personas.
- [x] **High Priority — Complex Business Logic**: `lot_nr` parsing, the
  `card_uid:` convention, move-on-bind semantics, two-tag-per-spool
  identical-payload writes, vendor-tag protection, and the Spoolman
  vendor/filament/spool one-shot creation chain all benefit from
  acceptance-criteria-driven scenarios.
- [x] **Medium Priority — Integration Work**: Spoolman PATCH/POST chain
  + NFC NDEF write-then-verify chain interact in non-trivial ways
  (e.g., write fails ⇒ no Spoolman commit; PATCH fails after write ⇒
  visible error, no partial commit).
- [x] **Medium Priority — Testing**: Stories will directly drive the
  unit-test bar set in NFR-4 (OpenSpoolData round-trip, `lot_nr`
  parser, repository PATCH chain, UID canonicalisation).

## Decision
**Execute User Stories**: **Yes**
**Reasoning**:
1. The behavioral pivot in v2 is ultimately defined by *what the user
   can now do that they couldn't before*. Stories make that visible.
2. Several flows have multiple legitimate paths (Spoolman-connected
   vs. offline; existing-spool vs. new-spool; first-tag vs. second-tag;
   blank tag vs. OpenSpool tag vs. branded vendor tag). Stories +
   acceptance criteria are the cheapest way to nail these branches
   down before Workflow Planning chooses how to slice units of work.
3. v2.1's vendor-decode + key-UI scope is small but legally significant
   (GPL-3.0 transition). Persona-mapped stories will make the v2.0/v2.1
   split crisp at the requirements/design boundary.

## Expected Outcomes
- A persona set covering: Spoolman-connected primary user, offline
  raw-writer, branded-tag (vendor-tag) reader, and (v2.1) the
  vendor-key power user.
- INVEST-compliant stories grouped by feature area, each with explicit
  acceptance criteria that map cleanly back to FRs in
  `aidlc-docs/inception/requirements/requirements.md`.
- A persona-to-story map that flags which stories belong to v2.0 and
  which to v2.1 — directly feeding Workflow Planning's depth/sequence
  decisions.
- Test-criteria seed: each acceptance-criterion bullet is a candidate
  for a unit or repository-level test in NFR-4.
