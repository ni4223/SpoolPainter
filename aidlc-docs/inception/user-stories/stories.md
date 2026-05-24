# SpoolPainter v2 — User Stories

**Source requirements**: `aidlc-docs/inception/requirements/requirements.md`
**Personas**: `aidlc-docs/inception/user-stories/personas.md`
**Plan**: `aidlc-docs/inception/plans/story-generation-plan.md`

**Format**: Connextra (Q3=A) — "As a `<persona>`, I want `<capability>`,
so that `<outcome>`."
**AC style**: bullet checklist (Q6=B). Named non-happy paths from FR-4.5,
FR-4.7, FR-5.2, FR-7.4, FR-10.2 are folded into the parent story as AC
bullets (Q7=B).
**Granularity**: Small / unit-of-work-granular (Q4=A) — each story is
independently buildable in ≤ ~2 days.
**Priority**: release-wave tag only (v2.0 / v2.1) — no MoSCoW (Q8=B).
**DoD**: code merged + unit tests passing per NFR-4.1 (Q11=A). Each AC
bullet is annotated with a test-mapping hint: `[unit]` (NFR-4.1
unit-test target) or `[manual]` (manual / out-of-scope verification).
**Personas**: P1 = Connected Hobbyist (Casey); P2 = Offline Tinkerer
(Owen); P3 = Branded-Tag Reader (Bea). "All" = applies to every persona.

---

# v2.0

## FR-1 — NFC tag identity (UID)

### S-1.1 Capture UID + payload on every read
**As** P1, P2, P3
**I want** the app to capture both the tag's hardware UID and any NDEF
payload whenever I tap a tag,
**so that** UID is always available for pairing/lookup, and any
OpenSpool data on the tag can pre-fill my form.
- AC: After a tap, the UI shows the UID and any decoded payload (or
  "no payload"). [manual]
- AC: UID is read from `android.nfc.Tag.getId()` and surfaced to the
  ViewModel via the NFC repository. [unit]
- AC: If the payload parses as OpenSpool JSON, payload fields are
  available to the form layer; if not, only the UID is surfaced. [unit]
- **Source FRs**: FR-1.1.

### S-1.2 Canonicalise the UID as lowercase hex
**As** any persona
**I want** the UID rendered and stored in a single canonical form,
**so that** equality comparisons against `lot_nr` entries are
deterministic.
- AC: `canonicalise(uidBytes)` returns lowercase hex with no separators
  and no padding (e.g., `04a1b2c3d4e580`). [unit]
- AC: Round-trip `canonicalise → bytes` (where applicable) preserves
  bit-for-bit equality. [unit]
- AC: The UID displayed on screen matches the canonical form
  byte-for-byte. [manual]
- **Source FRs**: FR-1.2, FR-1.3.

---

## FR-2 — `lot_nr` ↔ UID format

### S-2.1 Parse a `lot_nr` value into UID entries + opaque tail
**As** the app (any persona)
**I want** a parser that splits a Spoolman spool's `lot_nr` value into
its `card_uid:` entries and any non-`card_uid:` content,
**so that** UID logic is isolated from arbitrary user-entered text and
the tail is preserved on writes.
- AC: `parse("card_uid:aabb,card_uid:ccdd,batch=42")` yields
  `[uid="aabb", uid="ccdd"]` and tail `"batch=42"` (or equivalent). [unit]
- AC: Empty input ⇒ empty entry list, empty tail. [unit]
- AC: Whitespace and case in `card_uid:` prefix tolerated on read;
  emitted in canonical lowercase form on write. [unit]
- AC: Unrecognised entries (no `card_uid:` prefix) are preserved
  verbatim and never deleted. [unit]
- **Source FRs**: FR-2.1, FR-2.2.

### S-2.2 Serialise UID entries back into a `lot_nr` value
**As** the app
**I want** to render the canonical UID list (plus preserved tail) back
into the comma-separated `lot_nr` string,
**so that** PATCH bodies are deterministic and round-trip through the
parser.
- AC: `serialise([uid="aabb", uid="ccdd"], tail="")` yields
  `"card_uid:aabb,card_uid:ccdd"`. [unit]
- AC: Tail preserved at the end with a single comma separator when
  present. [unit]
- AC: Round-trip `serialise(parse(x))` is idempotent. [unit]
- **Source FRs**: FR-2.1, FR-2.2.

---

## FR-3 — Read-and-Pair flow

### S-3.1 Look up an unknown tag by UID in Spoolman
**As** P1
**I want** the app to ask Spoolman whether a tapped UID is already
paired,
**so that** I don't accidentally re-create a spool record that already
exists.
- AC: On read, the app calls
  `GET /api/v1/spool?lot_nr=card_uid:<uid>`. [unit]
- AC: The `card_uid:` prefix is included in the search term to avoid
  substring false positives (FR-3.2 disambiguation). [unit]
- AC: HTTP errors / connection errors surface a clear banner with
  Retry; no silent swallowing. [unit] (FR-10.2 folded in)
- **Source FRs**: FR-3.1, FR-3.2.

### S-3.2 Pre-select + pre-fill when one match is found
**As** P1
**I want** a uniquely matching spool to be pre-selected in the dropdown
and have its filament metadata pre-fill the form,
**so that** I can confirm in one tap.
- AC: Single match ⇒ dropdown shows that spool selected. [manual]
- AC: Filament fields (material, brand, color_hex, variant, temps)
  pre-populate from the matched spool's filament. [unit]
- AC: Form remains editable. [manual]
- **Source FRs**: FR-3.3 (single-match branch).

### S-3.3 Surface ambiguity when multiple spools match
**As** P1
**I want** to see a clear error if more than one Spoolman spool claims
this UID,
**so that** I can fix the data anomaly before pairing.
- AC: Multiple matches ⇒ error UI lists the matching spool names/ids
  and blocks auto-pairing. [manual]
- AC: No PATCH/POST is issued in this state. [unit]
- **Source FRs**: FR-3.3 (multi-match branch).

### S-3.4 Pre-fill the form from a tag's OpenSpool payload when no
Spoolman match exists
**As** P1, P2
**I want** the form to fill itself from the tag's NDEF payload when
the UID isn't in Spoolman yet,
**so that** I save typing on tags that already carry OpenSpool data.
- AC: No Spoolman match + valid OpenSpool JSON on tag ⇒ form fields
  pre-fill from the payload. [unit]
- AC: Mapping equals v1's `OpenSpoolData → FilamentSpool`. [unit]
- AC: Form remains editable. [manual]
- **Source FRs**: FR-3.4 (OpenSpool-payload branch), FR-11.1.

### S-3.5 Leave the form empty for blank/unparseable tags
**As** P1, P2, P3
**I want** the form to stay empty (just showing the UID) when a tag
is blank or unparseable,
**so that** I can type details from scratch without v1's stale state.
- AC: Blank tag ⇒ form fields empty, UID visible. [manual]
- AC: Unparseable NDEF ⇒ form fields empty, UID visible, no error
  toast (it's a normal state, not a failure). [unit]
- **Source FRs**: FR-3.4 (blank/unparseable branch), FR-11.2.

### S-3.6 Selecting a Spoolman spool from the dropdown prefills the form
**As** P1, P3
**I want** the form to fill in from the chosen spool's filament
metadata whenever I pick a spool in the Spoolman dropdown,
**so that** I never have to re-type fields the spool already knows
about, no matter how I got to that selection (manual pick after a
blank tag, manual pick after a vendor tag, manual pick with no tag
scanned yet, or auto-pick from a UID match).
- AC: Dropdown selection event ⇒ form fields (material, brand,
  color_hex, variant, min/max extruder and bed temps) populate from
  the selected spool's filament metadata. [unit]
- AC: Behaviour is identical regardless of tag context (blank /
  OpenSpool / vendor / no tag scanned). [unit]
- AC: Re-selecting a different spool overwrites the form fields. [unit]
- AC: Clearing/deselecting the dropdown empties the form back to
  the blank state. [unit]
- AC: Form remains editable after prefill. [manual]
- AC: This is the same behaviour S-3.2 triggers automatically on
  UID match — S-3.2 is just a special case of this rule. [unit]
- **Source FRs**: FR-3.6 (NEW); applies across FR-4.6 / FR-4.9 flows.

---

## FR-4 — Write/Create-and-Pair flow

### S-4.1 Pre-conditions are explicit before write
**As** P1
**I want** the Write button to be enabled only when either a Spoolman
spool is selected or required form fields are filled,
**so that** I never start a write that can't pair on the back end.
- AC: Disabled state has clear hint copy. [manual]
- AC: ViewModel `canWrite` flag is true iff (selected spool != null)
  OR (material + brand + color + temps are valid). [unit]
- **Source FRs**: FR-4.1.

### S-4.2 Spoolman-first sequencing: resolve spool_id before NDEF write
**As** P1
**I want** the new-spool path to create the Spoolman spool *first*
and use its id when writing the tag,
**so that** the OpenSpool payload on the tag carries `spool_id` from
day one — even for spools I'm creating fresh — and so that the
UID-↔spool mapping in Spoolman is durable even if my NDEF write
fails.
- AC: Existing-spool path: spool id = selected spool's id; no
  Spoolman writes happen yet. [unit]
- AC: New-spool path: app runs FR-7 create chain (S-7.1 / S-7.2 /
  S-7.3) **before** the NDEF write; the resulting new spool's id is
  used for the tag write. [unit]
- AC: New-spool path: the POST in S-7.3 sets
  `lot_nr=card_uid:<uid>` so the mapping is committed before the
  NDEF write attempt. [unit]
- AC: Move-on-bind (S-5.1 / S-5.2) runs **before** the new-spool
  POST so that a UID already paired to an existing spool routes to
  the existing-spool path instead of creating a duplicate. [unit]
- **Source FRs**: FR-4.3.

### S-4.3 Emit OpenSpool NDEF payload on write (always with spool_id)
**As** P1, P2
**I want** the app to write a v1-shape OpenSpool JSON NDEF record to
the tag, including `spool_id` for every non-raw write,
**so that** firmware and OpenSpool-aware tools always have the spool
id directly from the tag — not just for spools I picked from the
dropdown.
- AC: NDEF record is `application/json` MIME with the v1 field set —
  `protocol`, `version`, `type`, `color_hex`, `brand`, `min_temp`,
  `max_temp`, `bed_min_temp`, `bed_max_temp`, `subtype`,
  `spool_id`. [unit]
- AC: `spool_id` is **always** populated on the existing-spool path
  (selected spool's id) and the new-spool path (id from S-4.2 /
  S-7.3 POST). [unit]
- AC: `spool_id` is omitted **only** in raw-write mode (S-4.8) where
  there is no Spoolman id to use. [unit]
- AC: The on-payload `lot_nr` field is reserved/unused. [unit]
- **Source FRs**: FR-4.4, FR-14.1.

### S-4.4 Write-then-verify, abort cleanly on mismatch
**As** P1, P2
**I want** every NDEF write to be read back and byte-compared,
**so that** a flaky write surfaces an explicit error and the user
can retry.
- AC: After write, app immediately re-reads the NDEF record and
  compares byte-for-byte. [unit]
- AC: On mismatch ⇒ visible error. The new-spool path's Spoolman
  spool (created in S-4.2) remains in Spoolman; on retry, the
  existing-spool path takes over because the UID lookup
  (FR-3.2 / S-3.1) finds the just-created spool, and the PATCH path
  runs idempotently. The user is not asked to clean up Spoolman by
  hand. [unit]
- AC: No automatic retry; user retries by tapping again. [manual]
  (NFR-6.2)
- **Source FRs**: FR-4.5, NFR-6.

### S-4.5 Pair UID into an existing Spoolman spool (PATCH path)
**As** P1
**I want** an existing-spool selection to result in `card_uid:<uid>`
being appended to that spool's `lot_nr`,
**so that** the UID is bound without my having to edit Spoolman by
hand.
- AC: PATCH body computed from `parse(existingLotNr)` ∪
  `card_uid:<uid>` (idempotent — already-present UIDs stay once). [unit]
- AC: Runs *after* a successful FR-4.4 NDEF write + FR-4.5 verify,
  for the existing-spool path. (For the new-spool path, pairing
  was already committed by S-4.2 / S-7.3.) [unit]
- AC: PATCH HTTP errors ⇒ visible banner, no silent swallow. [unit]
- AC: After success, UI reflects the spool now has the UID in its
  `lot_nr`. [manual]
- **Source FRs**: FR-4.6 (existing-spool branch).

### S-4.6 Never overwrite a non-OpenSpool tag (vendor-tag protection)
**As** P3
**I want** the app to refuse to write the NDEF payload of any
non-blank, non-OpenSpool tag,
**so that** branded vendor tags are safe even if I tap them by
mistake.
- AC: Detection: tag has a non-empty NDEF that fails OpenSpool parse
  ⇒ classified as vendor/foreign. [unit]
- AC: Vendor/foreign tag classification suppresses the **NDEF write
  step (FR-4.4)** and its verify (FR-4.5); no NDEF bytes are sent. [unit]
- AC: This rule applies to the **NDEF payload only** — the Spoolman
  pairing chain (FR-4.3 / FR-4.6 / FR-7) MAY still proceed against
  the same tag's UID via the UID-only pair flow (S-4.8). [unit]
- AC: There is no "force-overwrite" toggle. [unit]
- **Source FRs**: FR-4.7, FR-14.2.

### S-4.7 Raw-write side mode (Spoolman-free)
**As** P2
**I want** an explicit mode that writes an OpenSpool payload to a
blank/OpenSpool tag without any Spoolman call,
**so that** I can keep the v1 untethered workflow.
- AC: Mode entry: button or menu item; persists across the writing
  flow only (no global preference). [manual]
- AC: In raw-write mode, the FR-4.3 Spoolman-first step and the
  FR-4.6 PATCH/POST are skipped end-to-end. [unit]
- AC: In raw-write mode, the OpenSpool payload omits `spool_id`
  (no Spoolman id exists). [unit]
- AC: FR-4.7 (vendor-tag protection) is still enforced. [unit]
- AC: Write-then-verify (S-4.4) is still enforced. [unit]
- **Source FRs**: FR-4.8.

### S-4.8 UID-only pair for vendor/foreign tags (opt-in)
**As** P3
**I want** the app, when I press Save/Write on a tag it can't read,
to ask me whether to pair the tag's UID only — and on confirm, run
the Spoolman pairing chain without any NDEF write,
**so that** I can finally track usage of my branded spools in
Spoolman without risking the vendor's tag content, and I never get
silently committed to a Spoolman side-effect I didn't ask for.
- AC: At **Read time**, when S-4.6 classifies the tag as
  vendor/foreign, the form is surfaced empty (same UI state as
  S-3.5 unparseable / blank). UI clearly indicates the tag is
  unreadable / pre-encoded and that no NDEF data will be written.
  No prompt yet — Read is non-destructive. [manual]
- AC: User can fill the form by **either** picking an existing
  Spoolman spool from the dropdown **or** typing new-spool details
  (same controls as the blank-form path). [manual]
- AC: At **Save / Write press** on a vendor/foreign-classified tag,
  app shows a **modal bottom sheet** (FR-13.2) with copy similar to:
  "This tag is encoded and we can't read its contents — but we can
  still map its UID to a Spoolman spool. Would you like to pair the
  UID only?" Actions: **Pair UID only** / **Cancel**. [manual]
- AC: On **Cancel** ⇒ no NDEF write, no Spoolman call; user returns
  to the main screen with form state intact. [unit]
- AC: On **Pair UID only** with **existing spool selected**: app
  PATCHes the selected spool's `lot_nr` per S-4.5's PATCH semantics
  (append `card_uid:<uid>`, idempotent). No NDEF write executed. [unit]
- AC: On **Pair UID only** with **user-entered details**: app runs
  the FR-7 create chain (S-7.1 / S-7.2 / S-7.3) — vendor / filament
  / spool — and the new spool's `lot_nr` is `card_uid:<uid>`. No
  NDEF write executed. [unit]
- AC: **Move-on-bind** (S-5.1 / S-5.2) still applies — vendor tags
  participate in re-pair semantics like any other tag. The
  re-pair confirmation runs **after** the Pair-UID-only opt-in
  is confirmed (so the user sees the opt-in first, then the
  re-pair confirmation). [unit]
- AC: Write-then-verify (S-4.4) does **not** apply to this flow —
  there is no NDEF write to verify. [unit]
- AC: Spoolman-side errors (PATCH/POST failures) surface a clear
  banner; no partial commit (Q11=A). [unit]
- **Source FRs**: FR-4.9, FR-4.7 (NDEF-write boundary), FR-14.2,
  FR-5 (move-on-bind reuse), FR-13.2 (bottom-sheet UI).

---

## FR-5 — Move-on-bind (re-pair / tag reuse)

### S-5.1 Detect a UID already paired to a different spool
**As** P1
**I want** the app to recognise that the tapped UID is currently in
another spool's `lot_nr` before binding it to a new one,
**so that** I never end up with the same UID on two spools.
- AC: Before pairing UID with target spool B, app calls the same
  `GET /api/v1/spool?lot_nr=card_uid:<uid>` lookup. [unit]
- AC: If a different spool A is found (A ≠ B), proceed to S-5.2;
  otherwise pair directly. [unit]
- **Source FRs**: FR-5.1.

### S-5.2 Confirm + atomic move from spool A to spool B
**As** P1
**I want** a clear bottom-sheet asking me to confirm re-pairing,
**so that** moves are deliberate.
- AC: Bottom-sheet shows the other spool's display name when
  available; otherwise its id. [manual] (FR-13.2 folded in)
- AC: On confirm: PATCH A removes only the matched `card_uid:<uid>`
  from A's `lot_nr` (other UIDs in A — e.g., the second tag of a
  two-tag pair — are preserved). [unit]
- AC: Then PATCH/POST B adds `card_uid:<uid>`. [unit]
- AC: On HTTP failure of either step ⇒ visible error, no further
  step is issued; no partial commit beyond the already-applied
  PATCH (Q11=A). The error message includes which spool was
  partially modified so the user can clean up. [unit] (FR-5.2
  partial-commit handling folded in)
- AC: On user cancel ⇒ no changes are made. [unit]
- **Source FRs**: FR-5.2.

---

## FR-6 — Two-tag-per-spool flow (Connected Hobbyist behaviour mode)

### S-6.1 Offer "Pair another tag with this spool?" after first success
**As** P1 (in two-tag behaviour mode)
**I want** an optional button to pair a second tag right after the
first pairing succeeds,
**so that** I can do both sides of a fresh spool in one sitting.
- AC: After a successful read-and-pair or create-and-pair, an action
  appears with copy "Pair another tag with this spool?". [manual]
- AC: Skipping it leaves the spool with one UID. [unit]
- AC: Tapping it puts the app into "second-tag pairing" mode for that
  spool. [unit]
- **Source FRs**: FR-6.1.

### S-6.2 Write identical NDEF + append second UID
**As** P1
**I want** the second tag to receive byte-identical OpenSpool payload
to the first and to be appended to the same spool's `lot_nr`,
**so that** both physical tags are interchangeable on the printer.
- AC: Second-tag write payload bytes equal first-tag write payload
  bytes. [unit]
- AC: After write-then-verify success (S-4.4), PATCH appends
  `card_uid:<uid2>` to the same spool's `lot_nr`. [unit]
- AC: End state: both tags carry identical OpenSpool JSON; the spool
  has both UIDs in `lot_nr`. [unit]
- AC: FR-4.7 (vendor-tag protection) still enforced — if the second
  tag is a vendor tag, abort the second-tag flow with an explanation. [unit]
- **Source FRs**: FR-6.2.

### S-6.3 Move-on-bind applies independently to the second tag
**As** P1
**I want** the second-tag UID checked against existing pairings just
like the first,
**so that** I can never end up with the same physical tag bound to two
spools.
- AC: Before append, S-5.1 / S-5.2 run for `<uid2>`. [unit]
- AC: Move only affects the matched UID; other UIDs in the source
  spool are preserved. [unit] (FR-6.3, FR-5.2)
- **Source FRs**: FR-6.3.

### S-6.4 Re-deriving the second-tag write payload on resumed pairing
**As** P1
**I want** to come back later and pair a second tag without my
interrupted state being persisted,
**so that** I'm not maintaining ghost flows across app launches.
- AC: Interrupted second-tag flow is dropped on app close (no
  persistence). [unit]
- AC: When the user later scans either tag, FR-3 finds the spool by
  UID and FR-6.1 offers "Pair another tag with this spool?" again. [manual]
- AC: When that flow runs, the OpenSpool payload to write is
  re-derived from the spool's filament metadata. [unit]
- **Source FRs**: FR-6.4.

---

## FR-7 — Spool / filament / vendor creation (one-shot)

### S-7.1 Resolve-or-create vendor by name
**As** P1
**I want** the create-spool path to find a matching Spoolman vendor
case-insensitively, or POST a new one if none matches,
**so that** my custom-typed brand becomes a real Spoolman vendor.
- AC: Lookup uses `GET /api/v1/vendor` and case-insensitive name
  match. [unit]
- AC: On no match, POST `/api/v1/vendor` with the user-entered name. [unit]
- AC: HTTP errors ⇒ visible banner, no further step is issued
  (Q11=A). [unit]
- **Source FRs**: FR-7.1, FR-7.5.

### S-7.2 Resolve-or-create filament by (vendor, material, color_hex, variant)
**As** P1
**I want** the app to reuse an existing filament when the tuple matches
and POST a new one otherwise,
**so that** my filament library doesn't grow stale duplicates.
- AC: Equality on (vendor_id, material, color_hex, variant) — case-
  insensitive on material; exact match on color_hex; treat null/empty
  variant as equivalent. [unit]
- AC: On no match, POST `/api/v1/filament` with the resolved vendor. [unit]
- AC: HTTP errors ⇒ visible banner, abort chain. [unit]
- **Source FRs**: FR-7.2, FR-7.5.

### S-7.3 POST a new spool with `lot_nr = card_uid:<uid>`
**As** P1
**I want** the new spool to be created carrying the UID immediately,
**so that** a single failure can't leave me with an orphan spool that
still needs a follow-up PATCH.
- AC: POST body sets `filament` to the resolved filament id. [unit]
- AC: POST body sets `lot_nr` to exactly `card_uid:<uid>`. [unit]
- AC: HTTP errors ⇒ visible banner, no partial commit (Q11=A). [unit]
  (FR-7.4 folded in)
- **Source FRs**: FR-7.3, FR-7.4.

---

## FR-8 — Material & brand presets

### S-8.1 Ship hardcoded material + brand presets
**As** P1, P2, P3
**I want** the app to come with the v1 material and brand lists out of
the box,
**so that** I have something to choose from before any Spoolman fetch
runs.
- AC: Material list = PLA, ABS, PETG, TPU, ASA, PC, Nylon, PVA, HIPS,
  "Other" (with v1 default extruder/bed temperature ranges). [unit]
- AC: Brand list = the v1 starter list. [unit]
- AC: Presets are available offline. [unit]
- **Source FRs**: FR-8.1, FR-8.2.

### S-8.2 Merge Spoolman vendors into the brand picker
**As** P1
**I want** my Spoolman vendors to appear in the brand picker
deduplicated against the hardcoded list,
**so that** I see one entry per real-world brand.
- AC: On Spoolman fetch, `GET /api/v1/vendor` results merge into the
  picker. [unit]
- AC: Dedup is case-insensitive against hardcoded names. [unit]
- AC: Spoolman entries take precedence on dedupe. [unit]
- **Source FRs**: FR-8.3, FR-8.5.

### S-8.3 "Add custom" entry for material picker
**As** P1
**I want** to type a free-form material name from the picker,
**so that** I can use materials Spoolman doesn't list yet.
- AC: Picker shows a final "Add custom" entry. [manual]
- AC: Selecting it prompts for name + default extruder/bed temp
  ranges. [manual]
- AC: Custom material is usable immediately for the current
  create-and-pair flow. [unit]
- AC: When the create chain runs (S-7.2), the custom material is
  recorded on the new Spoolman filament's `material` field. [unit]
- AC: Custom material persists locally so it appears next launch
  before the next Spoolman fetch. [unit]
- AC: On the next successful fetch, server-side entries take
  precedence (case-insensitive dedupe). [unit]
- **Source FRs**: FR-8.5, FR-8.4.

### S-8.4 "Add custom" entry for brand picker
**As** P1
**I want** to type a free-form brand name from the picker,
**so that** I can use brands my Spoolman doesn't have yet.
- AC: Picker shows a final "Add custom" entry. [manual]
- AC: Selecting it prompts for the vendor name only. [manual]
- AC: Custom brand triggers a vendor lookup-or-create (S-7.1) when
  the create chain runs. [unit]
- AC: Custom brand persists locally so it appears next launch. [unit]
- AC: On next successful fetch, dedup against Spoolman vendors
  (case-insensitive). [unit]
- **Source FRs**: FR-8.5.

---

## FR-9 — Settings (v2.0 subset)

### S-9.1 Configure the Spoolman server URL
**As** P1
**I want** to set my Spoolman base URL in Settings and have it
validated by a fetch,
**so that** I find out about typos before I tap a tag.
- AC: Settings has a free-text URL field. [manual]
- AC: Save triggers a connectivity check (lightweight `GET`); failure
  shows an explicit error. [unit]
- AC: Valid URL is persisted via DataStore (NFR-3.1). [unit]
- **Source FRs**: FR-9.1.

### S-9.2 Choose dropdown sort order
**As** P1
**I want** to pick how the Spoolman dropdown is sorted,
**so that** I can find spools the way I think about them.
- AC: Options: None / Brand A-Z / Material A-Z / Last Used. [manual]
- AC: Choice persisted via DataStore. [unit]
- AC: Dropdown applies the chosen sort. [unit]
- **Source FRs**: FR-9.2.

### S-9.3 Override the theme
**As** any persona
**I want** to force light or dark mode regardless of system,
**so that** the app matches my preference.
- AC: Options: System / Light / Dark. [manual]
- AC: Choice persisted via DataStore. [unit]
- AC: App applies the override at startup and on change. [manual]
- **Source FRs**: FR-9.3, FR-12.2.

---

## FR-10 — Spoolman optionality and offline behaviour

### S-10.1 App is fully usable without a Spoolman URL
**As** P2
**I want** to use the app with no Spoolman configured,
**so that** an offline workflow is a first-class path, not a degraded
state.
- AC: With no URL configured: read tags works, raw-write mode (S-4.7)
  works. [manual]
- AC: With no URL configured: Read-and-Pair lookup, Spoolman dropdown,
  and create-and-pair commit are disabled with neutral copy. [unit]
- AC: No nag/banner pestering the user to configure Spoolman. [manual]
- **Source FRs**: FR-10.1.

### S-10.2 Visible banner with Retry when Spoolman is unreachable
**As** P1
**I want** a clearly visible banner with a Retry control when my
configured Spoolman is unreachable,
**so that** I notice and can recover with one tap.
- AC: Network failure on any Spoolman call ⇒ banner appears with
  Retry. [manual]
- AC: Cached vendor / filament / spool data may remain visible
  read-only. [unit]
- AC: Write-and-pair, dropdown contents, lookup are disabled while
  unreachable. [unit]
- AC: Retry re-attempts the most recent failed call. [manual]
- **Source FRs**: FR-10.2, FR-10.3.

---

## FR-12 — Theming

### S-12.1 Dark/light follow system + Material You dynamic color
**As** any persona
**I want** the app to follow system theme and use Material You dynamic
color on Android 12+,
**so that** the app feels at home on my device.
- AC: On Android 12+, dynamic color seeds the Material 3 theme. [manual]
- AC: Light/dark follows system unless overridden in Settings (S-9.3). [unit]
- AC: Pre-Android-12: falls back to a fixed Material 3 palette. [manual]
- **Source FRs**: FR-12.1.

---

## FR-13 — UI shape

### S-13.1 Single main screen with two primary actions
**As** any persona
**I want** the main screen to keep v1's two-button layout (Read NFC
Tag / Write to NFC) plus the dropdown, form, and temperature panel,
**so that** muscle memory carries over.
- AC: Main screen has Read and Write actions visible at all times. [manual]
- AC: Dropdown + form + temp panel are reachable without navigating. [manual]
- AC: No new top-level destinations. [manual]
- **Source FRs**: FR-13.1, FR-13.4.

### S-13.2 Multi-step prompts use modal bottom sheets
**As** any persona
**I want** flow-specific prompts ("Scan the second tag", "Create
profile?", "Tag already paired — re-pair?") to appear as bottom
sheets,
**so that** I never lose the main-screen context.
- AC: Each named multi-step prompt is implemented as a bottom sheet. [manual]
- AC: Bottom sheets do not push a new top-level destination. [manual]
- AC: Cancelling a sheet returns to the main screen with no state
  loss in the form. [manual]
- **Source FRs**: FR-13.2, FR-13.3, FR-13.4.

---

## FR-15 — Naming

### S-15.1 Keep the SpoolPainter name and package id
**As** any persona
**I want** the app to keep its name and package id in v2,
**so that** my installed app updates in place without re-pairing my
launcher / Play Store account.
- AC: User-visible name remains "SpoolPainter". [manual]
- AC: Package id remains `com.spoolpainter.app`. [unit] (build config)
- AC: `debug` variant retains `.debug` suffix for side-by-side install
  during development. [manual]
- **Source FRs**: FR-15.1, NFR-8.3.

---

# v2.1

## FR-1.4 / FR-3.5 — Multi-vendor decode

### S-1.4 Decode supported vendor-tag formats for form pre-fill
**As** P3
**I want** the app to decode a recognised vendor tag's encoded fields
(material, brand, color, temps where available) and pre-fill the form,
**so that** branded spools become as quick to onboard as OpenSpool
spools.
- AC: Vendor parsers cover Bambu, Creality, Anycubic, Elegoo, Qidi,
  Snapmaker, OpenSpool, TigerTag (per requirements §3 v2.1). [unit]
- AC: Unrecognised vendor formats fall through to UID-only behaviour
  (S-3.5 path). [unit]
- AC: Decoded fields pre-fill the form; user edits remain possible. [unit]
- AC: FR-4.7 vendor-tag protection still enforced — decoding does
  NOT enable writing. [unit]
- **Source FRs**: FR-1.4, FR-3.5.

---

## FR-9.4 — Vendor key Settings

### S-9.4.1 Add and store per-vendor decryption keys
**As** P3
**I want** to enter Mifare Classic A/B keys (or equivalent per-vendor
keys) in Settings,
**so that** my Bambu (and similar) tags can be decoded.
- AC: Settings has a "Vendor Keys" section listing supported vendors. [manual]
- AC: User can add / edit / delete per-vendor keys. [manual]
- AC: Keys are persisted with **Android Keystore-backed encryption**
  (e.g., `EncryptedSharedPreferences` or Tink-wrapped DataStore). [unit]
- AC: App ships with no keys; default state = empty. [unit]
- **Source FRs**: FR-9.4, NFR-3.4.

### S-9.4.2 Without keys, fall back to UID-only behaviour
**As** P3
**I want** an encrypted vendor format I haven't keyed to behave
exactly like v2.0 (UID-only pair),
**so that** the app remains useful even with partial key coverage.
- AC: Encrypted format detected + no key configured ⇒ form is empty
  pre-fill, UID is captured. [unit]
- AC: Pairing path (S-3.x / S-4.x) is unchanged for the UID. [unit]
- AC: No prompts to "go get keys" — the absence of keys is not an
  error state. [manual]
- **Source FRs**: FR-9.4 / Q4=B.

---

## NFR-12 — Vendor data packaging (v2.1)

### S-NFR12 Bake vendor format definitions into the app
**As** P3
**I want** vendor format definitions (material codes, colour palettes,
byte offsets) baked into the app,
**so that** decoding works completely offline and there's no remote
config for me to trust.
- AC: Vendor format tables ship as static assets in the APK. [unit]
- AC: Updates to format tables require an app release. [unit]
- AC: No network calls happen for vendor decoding. [unit]
- **Source NFRs**: NFR-12.1.

---

## NFR-11 — Licensing transition (v2.1)

### S-NFR11 Ship v2.1 under GPL-3.0 with source offer
**As** P3 (the v2.1 user benefiting from ported parsers)
**I want** v2.1 distribution to comply with GPL-3.0 since it includes
ported OpenRFID parser code,
**so that** the legal and source-availability obligations are met.
- AC: Project licence file updated to GPL-3.0 starting at v2.1 tag. [manual]
- AC: Play Store + GitHub Releases descriptions include a GPL-3.0
  source offer per §6 of the licence. [manual]
- AC: Build artefacts include a NOTICE / LICENCE bundle. [manual]
- **Source NFRs**: NFR-11.1, NFR-11.2.

> Per Q9=B, NFR-only stories are normally excluded; this v2.1 entry is
> kept as a stub because the v2.1 cut depends on the licence transition
> shipping atomically with the parser port — easier to cite from v2.1
> Workflow Planning if it lives next to the parser story.

---

# Coverage map (FR / NFR ↔ stories)

| Source | Stories | Notes |
|---|---|---|
| FR-1.1 | S-1.1 | |
| FR-1.2 | S-1.2 | |
| FR-1.3 | S-1.2 | |
| FR-1.4 (v2.1) | S-1.4 | |
| FR-2.1 | S-2.1, S-2.2 | |
| FR-2.2 | S-2.1, S-2.2 | |
| FR-2.3 | requirements-only | `lot_nr` is the temporary home for `card_uid`; dictated by Spoolman API's current server-side-search limitations |
| FR-2.4 | post-v2.1 (deferred) | migration to `extra.card_uid` or a dedicated UID field once upstream Spoolman supports filtering by extras (PR #773 / issue #716) — explicit out-of-scope for v2.0 / v2.1 |
| FR-3.1 | S-3.1 | |
| FR-3.2 | S-3.1 | |
| FR-3.3 | S-3.2, S-3.3 | |
| FR-3.4 | S-3.4, S-3.5 | |
| FR-3.5 (v2.1) | S-1.4 | |
| FR-3.6 | S-3.6 | NEW — dropdown selection prefills form regardless of how user got there |
| FR-4.1 | S-4.1 | |
| FR-4.2 | S-1.1, S-4.1 | UID extraction is shared with read |
| FR-4.3 | S-4.2 | Spoolman-first sequencing (so spool_id can land on every tag) |
| FR-4.4 | S-4.3 | NDEF write — always with spool_id for non-raw paths |
| FR-4.5 | S-4.4 | folded as AC (write-then-verify) |
| FR-4.6 | S-4.5 | existing-spool PATCH (new-spool path's pairing already committed by S-4.2) |
| FR-4.7 | S-4.6, AC bullets in S-4.7, S-6.2 | NDEF-write boundary only — UID-only pair allowed via S-4.8 |
| FR-4.8 | S-4.7 | raw-write mode (Spoolman-free) |
| FR-4.9 | S-4.8 | NEW — UID-only pair for vendor/foreign tags (P3 v2.0 path) |
| FR-5.1 | S-5.1 | |
| FR-5.2 | S-5.2 | partial-commit handling folded in as AC |
| FR-6.1 | S-6.1 | |
| FR-6.2 | S-6.2 | |
| FR-6.3 | S-6.3 | |
| FR-6.4 | S-6.4 | |
| FR-7.1 | S-7.1 | |
| FR-7.2 | S-7.2 | |
| FR-7.3 | S-7.3 | |
| FR-7.4 | S-7.3 | folded as AC |
| FR-7.5 | S-7.1, S-7.2 | |
| FR-8.1 | S-8.1 | |
| FR-8.2 | S-8.1 | |
| FR-8.3 | S-8.2 | |
| FR-8.4 | S-8.3 | |
| FR-8.5 | S-8.2, S-8.3, S-8.4 | |
| FR-9.1 | S-9.1 | |
| FR-9.2 | S-9.2 | |
| FR-9.3 | S-9.3 | |
| FR-9.4 (v2.1) | S-9.4.1, S-9.4.2 | |
| FR-10.1 | S-10.1 | |
| FR-10.2 | S-10.2 | folded as AC into S-3.1 too |
| FR-10.3 | S-10.2 | |
| FR-11.1 | S-3.4 | |
| FR-11.2 | S-3.5 | |
| FR-12.1 | S-12.1 | |
| FR-12.2 | S-9.3, S-12.1 | |
| FR-13.1 | S-13.1 | |
| FR-13.2 | S-13.2, S-5.2, S-6.1, S-4.8 | |
| FR-13.3 | S-13.2, S-9.x | settings-as-separate-screen |
| FR-13.4 | S-13.1, S-13.2 | |
| FR-14.1 | S-4.3 | always-spool_id rule incl. for new-spool path |
| FR-14.2 | S-4.6 | |
| FR-15.1 | S-15.1 | |
| NFR-3.4 (v2.1) | S-9.4.1 | |
| NFR-6 | S-4.4 | folded as AC; per Q9=B, no standalone story |
| NFR-7 | S-3.1, S-4.4, S-4.5, S-5.2, S-7.x, S-10.2 | folded as AC across stories |
| NFR-9 | requirements-only | distribution channels are platform behaviour (Q9=B) |
| NFR-11 (v2.1) | S-NFR11 | retained as stub, see story note |
| NFR-12 (v2.1) | S-NFR12 | |

**Coverage gaps**: none in v2.0. v2.1 is intentionally narrower
(decode + key UI + licensing) — gaps are out-of-scope per requirements
§3.

---

# Persona ↔ story matrix

| Story | P1 Casey | P2 Owen | P3 Bea |
|---|---|---|---|
| S-1.1 | ✅ | ✅ | ✅ |
| S-1.2 | ✅ | ✅ | ✅ |
| S-2.1 | (engine) | (engine) | (engine) |
| S-2.2 | (engine) | (engine) | (engine) |
| S-3.1 | ✅ |  | ✅ |
| S-3.2 | ✅ |  | ✅ |
| S-3.3 | ✅ |  | ✅ |
| S-3.4 | ✅ | ✅ |  |
| S-3.5 | ✅ | ✅ | ✅ |
| S-3.6 | ✅ |  | ✅ |
| S-4.1 | ✅ | ✅ |  |
| S-4.2 | ✅ | ✅ |  |
| S-4.3 | ✅ | ✅ |  |
| S-4.4 | ✅ |  |  |
| S-4.5 | ✅ |  |  |
| S-4.6 | ✅ | ✅ | ✅ |
| S-4.7 |  | ✅ |  |
| S-4.8 |  |  | ✅ |
| S-5.1 | ✅ |  | ✅ |
| S-5.2 | ✅ |  | ✅ |
| S-6.1 | ✅ |  |  |
| S-6.2 | ✅ |  |  |
| S-6.3 | ✅ |  |  |
| S-6.4 | ✅ |  |  |
| S-7.1 | ✅ |  | ✅ |
| S-7.2 | ✅ |  | ✅ |
| S-7.3 | ✅ |  | ✅ |
| S-8.1 | ✅ | ✅ | ✅ |
| S-8.2 | ✅ |  | ✅ |
| S-8.3 | ✅ | ✅ | ✅ |
| S-8.4 | ✅ | ✅ | ✅ |
| S-9.1 | ✅ |  | ✅ |
| S-9.2 | ✅ |  | ✅ |
| S-9.3 | ✅ | ✅ | ✅ |
| S-10.1 |  | ✅ |  |
| S-10.2 | ✅ |  | ✅ |
| S-12.1 | ✅ | ✅ | ✅ |
| S-13.1 | ✅ | ✅ | ✅ |
| S-13.2 | ✅ |  | ✅ |
| S-15.1 | ✅ | ✅ | ✅ |
| **v2.1** | | | |
| S-1.4 |  |  | ✅ |
| S-9.4.1 |  |  | ✅ |
| S-9.4.2 |  |  | ✅ |
| S-NFR12 |  |  | ✅ |
| S-NFR11 |  |  | ✅ |

`(engine)` = internal stories that don't surface directly to a persona
but are required by all persona-facing stories.
