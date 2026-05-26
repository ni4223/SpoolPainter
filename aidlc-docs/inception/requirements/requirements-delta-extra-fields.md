# SpoolPainter v2 — Requirements Delta: Spoolman `extra` Fields (`card_uids` + `variant`)

**Status**: Approved-pending — drafted 2026-05-25 during U6a entry-gate, supersedes FR-2 in
[requirements.md](./requirements.md). Folds into U2 / U3 / U5 / U6a / U6b
construction scope. v2.0 only.

**Author of external spec**: `paxx12-snapmaker-u1/spool-link/docs/SPOOLMAN.md` ([source][SPEC]).
This delta normalises SpoolPainter v2 to that spec.

[SPEC]: https://github.com/paxx12-snapmaker-u1/spool-link/blob/main/docs/SPOOLMAN.md

---

## 1. Why this delta exists

The original v2 design (FR-2 in `requirements.md`) chose `lot_nr` as a
**temporary** carrier for the UID mapping because Spoolman's
`GET /api/v1/spool` endpoint did not support server-side filtering on
`extra` keys. FR-2.4 anticipated migrating to a dedicated field once
upstream filtering shipped (PR #773 / issue #716).

Two events change the picture:

1. **The user (firmware-side workstream owner) has decided** that
   server-side filtering on `extra` is **not going to be implemented**
   in this app (Q-F = drop). The permanent design is **client-side
   substring filter on `extra.card_uids` over a single bulk fetch**.
   Same cost shape as today's `lot_nr` substring filter, no perf
   regression, no upstream dependency.
2. **The reference protocol** (`paxx12-snapmaker-u1/spool-link/docs/SPOOLMAN.md`)
   pins exact field names, wire formats, bootstrap requirements, and a
   move-on-bind algorithm — all of which Spoolman's source confirms are
   **mandatory** (see §6 below). Aligning to this spec lets the firmware
   workstream and the app integrate without per-side reverse-engineering.

This delta is a **hard cutover**: `lot_nr:card_uid:` encoding is
**deleted**, not deprecated. v2 has not reached real testers yet — only
the developer's own dev installs hold legacy data — so a one-shot break
is acceptable (Q-B = A).

## 2. Supersession of FR-2

**FR-2 ("`lot_nr` ↔ UID mapping format") is fully superseded.** All
clauses (FR-2.1 / FR-2.2 / FR-2.3 / FR-2.4) become **N/A** in v2.0.
Existing v1.x data using the legacy `lot_nr:card_uid:` encoding **is not
read** by v2. v1.x continues to ship on the production track unchanged
(see [project-playstore-testing memory] / `requirements.md` §3 release
strategy).

[project-playstore-testing memory]: ../../../memory/project_playstore_testing.md

## 3. New / revised functional requirements

### FR-2-EXT.1 — `extra.card_uids` is the canonical UID storage on Spool

The app SHALL store and look up tag UIDs in the Spool's
`extra.card_uids` custom field, following the wire format defined in §4.
`lot_nr` SHALL NOT be read or written by v2 for UID purposes.

> Trace: replaces FR-2.1 / FR-2.2 / FR-2.3 / FR-2.4. Touches FR-3.2
> (read-side lookup), FR-4.3 (Spoolman-first sequencing), FR-4.6 (PATCH
> append), FR-5 (move-on-bind), FR-7 (create chain).

### FR-2-EXT.2 — `extra.variant` round-trips on Filament

The app SHALL persist the user-entered `variant` value (already a v1
UI field; never previously sent to Spoolman) into the Filament's
`extra.variant` field on filament create, and SHALL read it back when
loading filament details. The wire format follows §4.

> Trace: closes a long-standing v1 gap. Touches FR-7 (filament create),
> FR-8 (form rendering), and the Filament read path in FR-3.

### FR-2-EXT.3 — Custom-fields bootstrap (eager + lazy fallback)

The app SHALL ensure Spoolman has the two custom fields registered
before any write that uses them:

- **Eager** — On Settings → "Test connection" success (Q-A = B), the
  app SHALL `GET /api/v1/field/spool` and `GET /api/v1/field/filament`,
  and `POST /api/v1/field/spool/card_uids` / `POST /api/v1/field/filament/variant`
  for any missing fields, with the parameter shape defined in §5.
- **Lazy fallback** — If any subsequent write to a Spool's
  `extra.card_uids` or a Filament's `extra.variant` returns HTTP 400
  with body matching `"Unknown extra field"`, the app SHALL POST the
  missing field definition and retry the write **once**. A second
  failure is surfaced as a normal error.

> **Why both:** Spoolman validates `extra` keys against its registered
> custom-field set on every POST/PATCH. Without registration, every
> write fails with 400 (verified at `spoolman/extra_fields.py:134-144`).
> Eager bootstrap covers normal flow; lazy fallback covers the case
> where a server admin deletes the field out from under us.

### FR-2-EXT.4 — Bulk-fetch with archived for UID lookup

The app SHALL implement `findSpoolsByCardUid(uid)` as a single
`GET /api/v1/spool?limit=1000&allow_archived=true` followed by
client-side filtering of the results' `extra.card_uids` (decoded per
§4) for substring match on the target UID.

> **Why include archived:** the move-on-bind algorithm (FR-5 / FR-2-EXT.6)
> needs to detect archived spools that still hold a UID. Excluding them
> would leak duplicates into the system.

### FR-2-EXT.5 — Read-modify-write of full `extra` map on every PATCH

When the app PATCHes a Spool or Filament with `extra`, it SHALL first
GET the entity, mutate the relevant key in the returned `extra` map
client-side, and PATCH the **complete** `extra` map back. The app
SHALL NOT send a partial `extra` PATCH.

> **Why:** Spoolman's `PATCH /api/v1/spool/{id}` doc string is explicit
> (`spool.py:432`):
>
> > *"If extra is set, all existing extra fields will be removed and
> > replaced with the new ones."*
>
> Partial PATCHes silently clobber any unrelated `extra` key (e.g. a
> filament's `variant` would be wiped by a `card_uids` write that
> didn't echo it back). Always read-modify-write.

### FR-2-EXT.6 — Move-on-bind algorithm follows the spec verbatim

`MoveOnBindUseCase` SHALL implement the "Add to current, remove from
others" algorithm exactly as specified in [SPEC] §"Sync Algorithm":

1. Fetch the target spool.
2. Append the UID to `extra.card_uids` if not already present.
3. PATCH the target spool (full `extra` map per FR-2-EXT.5).
4. Bulk-fetch all spools (`limit=1000&allow_archived=true`) and locate
   any other spools holding the same UID.
5. PATCH each duplicate-holder to remove the UID (full `extra` map
   per FR-2-EXT.5).

Partial-failure semantics (FR-5.2) still apply — the per-spool error
path SHALL identify which spool was partially modified.

### FR-2-EXT.7 — Connection test = `GET /api/v1/info`

`SettingsViewModel.onTestConnectionTapped` SHALL call
`GET /api/v1/info` (was: a generic probe). The returned `version` field
MAY be surfaced in Settings; storing it is not required.

### FR-2-EXT.8 — UID hex casing: uppercase, no separators

All UID hex emitted by the app SHALL be **uppercase** with no
separators. `CardUid.fromBytes(bytes)` SHALL be
`bytes.joinToString("") { "%02X".format(it) }` (current impl uses
`%02x` — fix required, see §7 U2 delta). UID hex received from
external sources (Spoolman `extra.card_uids` payloads, NFC tag
payloads) SHALL be normalised to uppercase before comparison.

> **Why:** The firmware-side implementation uses `%02X`. Mismatched
> casing would break substring lookup silently.

## 4. Wire format (normative — copied from [SPEC])

### `extra.card_uids` (Spool)

- **Conceptual value**: a list of uppercase hex UIDs, e.g.
  `["AABBCCDD", "11223344"]`.
- **Encoded value** (the string stored in `extra.card_uids`): a
  comma-joined list, then `jsonEncode`'d.
  - 2-tag spool example: the value sent over the wire is the
    JSON-string `"\"AABBCCDD,11223344\""` — i.e. the bytes
    `0x22 0x41 0x41 ... 0x22`.
  - 1-tag spool example: `"\"AABBCCDD\""`.
  - 0-tag spool: `extra.card_uids` is absent OR equals `"\"\""`.
    Decoder treats both equivalently.
- **Decoder** (defensive): strip surrounding double quotes if present
  (handles both encoded `"\"X\""` and raw `X` forms), split on `,`,
  trim each entry, drop empties, normalise to uppercase.
- **Encoder**: comma-join uppercase entries; wrap in `JSONEncoder`.
  iOS reference falls back to manual `"\"" + s + "\""` on
  encoder failure — Kotlin equivalent uses `Gson().toJson(s)` (already
  a project dep) which never throws on a `String`, so no fallback is
  needed.

### `extra.variant` (Filament)

- **Conceptual value**: a single string, e.g. `"Silk"`, `"Matte"`,
  `"Sparkle"`, or whatever the user typed.
- **Encoded value**: `jsonEncode` of the raw string. Wire example:
  `"\"Silk\""`.
- **Empty / unknown**: omit `extra.variant` from create payloads
  entirely (do not send `"\"\""` — sending empty would round-trip back
  as a non-null empty `variant` and confuse the form).

### Why JSON-string-wrap?

Spoolman's validator (`extra_fields.py:60-66`,
`validate_extra_field_value`) parses `value` as JSON first and then
checks the parsed result is a string for `field_type=text`. The wire
format is a consequence of the validator: a `text` extra field stores
*JSON-encoded text*, not raw text.

## 5. Custom-field definition parameters (normative)

When the app POSTs the field definitions during bootstrap (FR-2-EXT.3),
the body SHALL be:

### `POST /api/v1/field/spool/card_uids`

```json
{
  "name": "Card UIDs",
  "field_type": "text",
  "order": 1,
  "default_value": "\"\""
}
```

### `POST /api/v1/field/filament/variant`

```json
{
  "name": "Variant",
  "field_type": "text",
  "order": 1,
  "default_value": "\"\""
}
```

The endpoint is **idempotent** — re-POSTing the same definition is a
no-op (`add_or_update_extra_field` upserts). Re-POSTing with a different
`field_type` returns 400 `"Field type cannot be changed."` — this
SHOULD NOT happen since the app only ever POSTs `text`, but the lazy
fallback (FR-2-EXT.3) MUST treat that 400 as terminal (not retried).

## 6. Spoolman strictness — verified from source

- `spoolman/api/v1/spool.py:392-398` — `POST /spool` calls
  `validate_extra_field_dict(...)` on `body.extra`. Unknown key →
  HTTP 400.
- `spoolman/api/v1/spool.py:454-457` — `PATCH /spool` calls the same
  validator. Unknown key → HTTP 400.
- `spoolman/extra_fields.py:134-144` — `validate_extra_field_dict`
  raises `ValueError(f"Unknown extra field {key}.")` for any key not
  in the registered set.
- `spoolman/api/v1/field.py:45-72` — `POST /field/{entity_type}/{key}`
  is the registration endpoint, idempotent via
  `add_or_update_extra_field`.
- `spoolman/api/v1/spool.py:432` — PATCH-replaces-extra contract:
  *"If extra is set, all existing extra fields will be removed and
  replaced with the new ones."*

## 7. Construction-unit deltas

### U2 — Domain Primitives (already DONE 2026-05-26)

**Status:** **Re-open under amendment** — U2 needs to be re-touched
during U6a's per-unit loop (no separate stage gate; folded into the
U6a Functional Design plan as a U2-amendment section).

- **U2-Δ-1** — Delete legacy methods on `CardUidEncoding`:
  `decode(lotNr)` / `encode(uids)` and the `Decoded` data class. Tests
  for these methods (`CardUidEncodingDecodeTest`,
  `CardUidEncodingEncodeTest`, `CardUidEncodingRoundTripTest`) are
  deleted in the same change.
- **U2-Δ-2** — Add `ExtraCardUidsCodec` in
  `domain/primitives/ExtraCardUidsCodec.kt`:
  - `encode(uids: List<CardUid>): String` — comma-join uppercase hex,
    `Gson().toJson(...)` wrap.
  - `decode(value: String): List<CardUid>` — defensive: strip surrounding
    `"`, split `,`, trim, drop empties, validate hex, uppercase.
  - Returns `List<CardUid>` (not the previous `Decoded(uids, residue)`
    shape — there is no residue concept in `extra.card_uids`).
- **U2-Δ-3** — Fix `CardUid.fromBytes` casing: `%02x` → `%02X`. Update
  affected unit tests.
- **U2-Δ-4** — Add a `CardUid.normaliseHex(raw: String): String` helper
  for incoming hex from Spoolman / NFC payloads (uppercase + validate).

> Test count impact: -3 test files (legacy encoding tests deleted), +1
> test file (`ExtraCardUidsCodecTest`, ~12 cases covering encode,
> decode, defensive parsing, casing, empty, single, multi). Net
> reduction; running total drops accordingly.

### U3 — Spoolman Repository (already DONE 2026-05-24)

**Status:** **Re-open under amendment** — U3 needs significant rework;
folded into U6a Functional Design (the work bottlenecks U6a's write
flow).

- **U3-Δ-1** — Wire DTO: `SpoolmanSpool` (and `SpoolmanFilament`) gain
  an `extra: Map<String, String>?` property. Existing code that builds
  `SpoolmanSpool` instances must thread `extra` through.
- **U3-Δ-2** — `findSpoolsByCardUid(uid)` rewrite: switches from
  paginated fetch + `lot_nr` filter to a single
  `GET /spool?limit=1000&allow_archived=true` fetch + client-side
  filter on `ExtraCardUidsCodec.decode(spool.extra["card_uids"]).contains(uid)`.
  Returns `Success(emptyList())` for empty/unset `card_uids` per
  Q-U3-1.
- **U3-Δ-3** — `appendCardUidToSpool(spoolId, uid)` rewrite: GET spool
  → decode `extra.card_uids` → idempotently add `uid` (no-op if
  present) → re-encode → PATCH **full `extra` map** (preserving every
  other key including `variant` if set on a copy of the parent
  filament's extras — NOTE: filament extras live on the filament, not
  the spool, so spool's `extra` only carries `card_uids` for now;
  still, future-proof by always echoing back the full map).
- **U3-Δ-4** — `removeCardUidFromSpool(spoolId, uid)` rewrite: same
  read-modify-write shape as U3-Δ-3.
- **U3-Δ-5** — `createSpoolForNewFilament(req)` rewrite: instead of
  emitting `lot_nr = "card_uid:<hex>"`, emit
  `extra = { "card_uids": ExtraCardUidsCodec.encode(listOf(uid)) }`
  on the spool POST. The filament POST emits
  `extra = { "variant": jsonEncode(req.variant) }` if `req.variant`
  is non-blank (FR-2-EXT.2).
- **U3-Δ-6** — New surface: `ensureExtraFieldsRegistered()` —
  `GET /field/spool` + `GET /field/filament`, computes diff against
  `{card_uids, variant}`, POSTs missing definitions per §5.
  Idempotent. Called by Settings → "Test connection" (FR-2-EXT.3
  eager half).
- **U3-Δ-7** — Lazy retry: `appendCardUidToSpool`,
  `removeCardUidFromSpool`, `createSpoolForNewFilament`, and the
  filament create path SHALL detect HTTP 400 with body matching
  `"Unknown extra field"`, call `ensureExtraFieldsRegistered()`, and
  retry **once**. Implementation lives in a single shared helper.
- **U3-Δ-8** — Connection test rewrite:
  `SpoolmanRepository.testConnection()` switches to `GET /api/v1/info`
  per FR-2-EXT.7. Returned `version` is exposed via the existing
  `SpoolmanOutcome.Success` channel; storage of version is not in
  scope.
- **U3-Δ-9** — Remove all `lot_nr` references from the U3 surface.
  `SpoolmanSpool` keeps the `lot_nr` field for fidelity with
  Spoolman's wire format (other apps may write to it), but no
  SpoolPainter code reads or writes it.

> Test count impact: ~+25 cases (extras encoding/decoding fidelity,
> bulk-fetch-with-archived, full-extras read-modify-write,
> ensureExtraFieldsRegistered idempotency, lazy retry, connection-test
> via /info), ~-15 cases (lot_nr-related tests retired). Net positive.

### U5 — Read-and-Pair Flow (already DONE 2026-05-25)

**Status:** **Re-open under amendment** — U5 amendment is small and
ships as part of U6a (the U6a Functional Design plan will list it
under "U5 carry-fixups").

- **U5-Δ-1** — `MainViewModel.onSpoolSelected(spool)` `cardUid`
  derivation switches from
  `CardUidEncoding.decode(spool.lot_nr).uids.firstOrNull()` to
  `ExtraCardUidsCodec.decode(spool.extra["card_uids"] ?: "").firstOrNull()`.
  The `FormMapping.SpoolmanUidSource.FromLotNrOrClear` enum value is
  renamed to `FromCardUidsOrClear`; the read-flow's
  `PreserveCurrent` case is unchanged.
- **U5-Δ-2** — The parked U5 multi-UID `lot_nr` dropdown auto-select
  bug **retires** with no further work. Root cause was the legacy
  encoding's nested `card_uid:` prefix combined with comma-separation
  of multiple entries. With `extra.card_uids` (single comma-separated
  list, no nested prefix), decode is unambiguous regardless of UID
  count.

> Test count impact: ~+2 cases (multi-UID spool selection now produces
> correct UID), -2 cases retired (the `lot_nr`-decode tests on
> `FormMapping`).

### U6a — Create-and-Pair Flow (about to open)

**Status:** **Stage gate now incorporates this delta.**

- **U6a-Δ-1** — `FilamentForm` keeps its existing `variant` text input
  (v1 component already has it; the v1 `FilamentForm` will be replaced
  by the v2 component during U6a anyway, and v2's component preserves
  the field). The `variant` value flows into `NewFilamentRequest`
  and reaches Spoolman via U3-Δ-5.
- **U6a-Δ-2** — `CreateAndPairUseCase` write-flow path uses
  `appendCardUidToSpool` (existing-spool path) and
  `createSpoolForNewFilament` (new-spool path) — both now
  `extra.card_uids`-backed via the U3-Δ rewrites. No use-case-level
  logic change.
- **U6a-Δ-3** — `MoveOnBindUseCase` interface contract aligns with
  FR-2-EXT.6's algorithm — the U6a interface stub MAY include the
  `findOtherOwners(uid: CardUid): List<SpoolmanSpool>` method (used
  by U6b's impl) without losing U6a→U6b ordering: U6a's
  `MoveOnBindUseCase` no-op default still proceeds without the move
  branch (per `unit-of-work.md` §3-U6a ordering note).
- **U6a-Δ-4** — Settings "Test connection" wires
  `ensureExtraFieldsRegistered()` (U3-Δ-6) into the success path.
  Settings UI surfaces a "Spoolman fields ready" indicator on success.

> Test count impact: ~+8 cases (variant round-trip via
> CreateAndPairUseCase + MainViewModel.onWriteTapped + filament-form
> binding).

### U6b — Move-on-Bind + Two-Tag Flow (pending)

- **U6b-Δ-1** — `MoveOnBindUseCase` impl follows FR-2-EXT.6 verbatim.
  Bulk-fetch with archived (U3-Δ-2) is the lookup primitive →
  "find other owners" step is essentially `findSpoolsByCardUid(uid)
  filterNot { it.id == targetSpoolId }`. No additional Spoolman
  surface needed.

### U7, U8, U9, U10 — unchanged

The delta has no scope impact on these units beyond the wire-format
normalisation everyone inherits from U2/U3. U8's "Custom Entries" for
material/brand are independent of `variant` (variant is a per-spool
filament attribute, not a catalogue entry).

## 8. Migration policy

**Hard cutover** (Q-B = A). v2.0 ships with **no `lot_nr` decode
support**. Any v1.x or pre-cutover v2.x dev install that left
`card_uid:`-prefixed entries in spool `lot_nr` fields:

- Will **not** match read-flow UID lookups (those entries are invisible
  to v2.0).
- Will be **silently preserved** in Spoolman's `lot_nr` field (v2 doesn't
  delete it — see U3-Δ-9). Users who want clean state can clear `lot_nr`
  via Spoolman's web UI manually.
- Re-pairing affected spools through v2.0 produces correct
  `extra.card_uids` entries on those spools.

The developer accepts this break for their own dev install. v2 has not
reached real testers.

## 9. Non-goals (explicit)

- **Server-side filter on `extra` (Q-F)** — will not be implemented.
  The client-side bulk-fetch + substring filter is the **permanent**
  design for v2.x and beyond. PR #773 / issue #716 references in
  FR-2.4 are obsolete; the FR-2.4 migration plan is cancelled.
- **Migration UI for legacy `lot_nr:card_uid:` entries** — out of
  scope. Hard cutover per §8.
- **`variant` as a searchable field** — `variant` is store-and-display
  only. No UI/UX surface for filtering or matching by variant.

## 10. Trace summary

| Source spec | Replaces | New FR | U2 | U3 | U5 | U6a | U6b |
|---|---|---|---|---|---|---|---|
| §"`card_uids` Custom Field" | FR-2.* | FR-2-EXT.1 | Δ-1, Δ-2 | Δ-1, Δ-2, Δ-3, Δ-4, Δ-5, Δ-9 | Δ-1, Δ-2 | Δ-2 | Δ-1 |
| §"`variant` Custom Field (Filament)" | (none) | FR-2-EXT.2 | — | Δ-1, Δ-5 | — | Δ-1 | — |
| §"Custom fields" / spec author's iOS pattern | (none) | FR-2-EXT.3 | — | Δ-6, Δ-7 | — | Δ-4 | — |
| §"Sync Algorithm" | FR-5 (impl detail) | FR-2-EXT.6 | — | Δ-2 | — | Δ-3 | Δ-1 |
| `spool.py:432` source-of-truth | (none) | FR-2-EXT.5 | — | Δ-3, Δ-4 | — | — | Δ-1 |
| §"Connection" | (none) | FR-2-EXT.7 | — | Δ-8 | — | Δ-4 | — |
| §"UID Format" | (none) | FR-2-EXT.8 | Δ-3 | — | — | — | — |
| §"Spools" `limit=1000&allow_archived=true` | (none) | FR-2-EXT.4 | — | Δ-2 | — | — | — |

## 11. Approval gate

This delta is **pending user approval**. Once approved:

1. The U6a stage gate is re-posed with this delta folded into U6a's
   Functional Design Part 1 plan — specifically, the plan SHALL include
   sections for U2 / U3 / U5 amendment work that ships **inside**
   U6a's per-unit loop (U2 / U3 / U5 are not re-opened as separate
   per-unit loops; they are amended within the U6a unit boundary).
2. `aidlc-state.md` records the delta in its summary alongside the
   U6a stage entry.
3. `audit.md` gets a new top-level entry capturing the delta and its
   approval.
4. The close-out commit at the end of U6a bundles: U6a code + tests +
   AIDLC artefacts + the U2/U3/U5 amendment code + tests + this delta
   document. (Same DoD #6 / §2.1 close-out commit shape.)
