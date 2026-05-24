# SpoolPainter v2 — Requirements

**Status**: Draft for approval (revised after Clarification Rounds 1 & 2)
**Date**: 2026-05-23
**Project type**: Brownfield rewrite, in-place update of v1.7
**Scope**: Comprehensive (major behavioral pivot + architecture overhaul,
delivered as v2.0 + v2.1)

---

## 1. Intent Analysis

- **User request**: "Using AI-DLC, I want to plan v2 of SpoolPainter."
- **Request type**: Major rewrite — behavioral pivot + architecture
  cleanup + multi-vendor tag-decode roadmap.
- **Scope estimate**: System-wide — touches every layer (UI, ViewModel,
  data, NFC); also introduces a new vendor-tag decoding subsystem in
  v2.1.
- **Complexity estimate**: Complex. Comprehensive depth chosen.

---

## 2. The behavioral pivot (the "why")

**v1 (today)**: app writes rich filament JSON (color, material, temps,
optional `spool_id`) onto NFC NDEF tags. Printer firmware reads that JSON
to know what filament is loaded.

**v2 (target)**: printer firmware identifies a spool by the **NFC tag's
hardware UID**, not the tag's payload. The mapping `tag UID ↔ Spoolman spool`
lives **in Spoolman** — specifically, in the spool's `lot_nr` field, formatted
as one or more comma-separated entries of the form `card_uid:<lowercase-hex>`.

Because spools physically have a tag on **both sides** of the spool body, a
single Spoolman spool typically holds **two** UIDs in `lot_nr`. The user
fills details once, then writes both tags end-to-end as a single workflow.

This model is consistent with the [SnapmakerU1-Extended-Firmware AFC-lite
spec](https://github.com/paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware/blob/afc-spoolman-auto-register/docs/afc-lite.md)
that the printer-side flow targets.

The tag itself **continues to carry an OpenSpool JSON payload** (color,
material, temps, brand, spool_id, …) for OpenSpool-format tags as a
backup/legacy datapath; v2 keeps writing this payload for older firmware
and other tools. The payload is **not** the authoritative source of truth in
the v2 model — `lot_nr` ↔ UID is.

---

## 3. Release Strategy

### v2.0 (this requirements document)
- Behavioral pivot to UID-keyed mapping.
- Two-tag-per-spool flow.
- One-shot vendor → filament → spool creation chain in Spoolman.
- Architecture cleanup (per-screen ViewModels + Repositories + Hilt).
- DataStore for settings (Room only if needed).
- Single v2.0 release; sideload + Play Store.
- **Reads OpenSpool tags only** (same format-coverage as v1).

### v2.1 (planned, scoped here for traceability — design starts after v2.0)
- Multi-vendor tag READ support: Bambu, Creality, Anycubic, Elegoo, Qidi,
  Snapmaker, OpenSpool, TigerTag — porting OpenRFID's parsers to Kotlin
  under **GPL-3.0** (SpoolPainter as a whole adopts GPL-3.0 for v2.1).
- Settings UI for per-vendor decryption keys (Bambu Mifare Classic etc.),
  encrypted at rest.
- Branded vendor tags are **never overwritten** — v2.1 only **reads** them
  and uses their UID for mapping; the OpenSpool write path stays
  reserved for blank/OpenSpool tags.

(Source: Q10=B → split scope; Q3A=C-then-B → defer multi-vendor + GPL
adoption to v2.1.)

---

## 4. Functional Requirements

### FR-1 NFC tag identity (UID) is the primary key

- **FR-1.1** (v2.0)  When a tag is read, the app SHALL capture both:
  - the tag's **hardware UID** from `android.nfc.Tag.getId()`, and
  - the tag's **NDEF payload** (if any) — parsed as OpenSpool JSON via
    the existing `OpenSpoolData.fromJson` path.
- **FR-1.2** (v2.0)  The app SHALL canonicalise the UID as **lowercase
  hex**, no separators, no length-padding (raw UID bytes only — variable
  length is expected). Example: `04a1b2c3d4e580`.
- **FR-1.3** (v2.0)  The app SHALL display the UID in the same canonical
  form.
- **FR-1.4** (v2.1)  When OpenRFID-style decoding is enabled, the app
  SHALL also extract the vendor's encoded fields (material, brand,
  color, temperatures where available) for use as form pre-fill data.

### FR-2 `lot_nr` ↔ UID mapping format

> **Important — `lot_nr` is a temporary home for the UID mapping.**
> Spoolman's data model already supports per-spool **`extra`** key/value
> fields (EAV-backed; `spoolman/database/models.py` defines a `SpoolField`
> table with composite PK `(spool_id, key)` and a `value: Text` column),
> and the public API accepts `extra` on create/update payloads. The
> reason v2 stores `card_uid:<hex>` inside `lot_nr` rather than
> `extra.card_uid` is **not** that `extra` is unsuitable — it is that
> Spoolman's `GET /api/v1/spool` does **not currently support
> server-side filtering by `extra` keys**. The find handler
> (`spoolman/database/spool.py`) only joins on filament/vendor/location
> /lot_nr today.
>
> Upstream activity tracks both halves of this gap:
> - Issue [#716](https://github.com/Donkie/Spoolman/issues/716)
>   "Search spools in API and more by extra fields and/or add field
>   for UID" — directly cites NFC tag UID as the motivating use case.
> - Issue [#427](https://github.com/Donkie/Spoolman/issues/427)
>   "selective search for extra fields".
> - PR [#773](https://github.com/Donkie/Spoolman/pull/773) "Add
>   filtering and sorting for custom fields" — open implementation.
>
> Once a server-side filter for `extra` (or a dedicated UID field)
> ships upstream, v2's plan is to migrate to `extra.card_uid` (or the
> new field) — see FR-2.4 below. Until then, `lot_nr` is the only
> field on which Spoolman can server-side substring-search, which is
> why FR-3.2's lookup uses it.

- **FR-2.1** (v2.0)  The app SHALL store the UID inside Spoolman's spool
  `lot_nr` field using the convention:
  - one entry per UID, prefixed `card_uid:` followed by the lowercase-hex
    UID;
  - multiple entries on a single spool are comma-separated;
  - example value: `card_uid:aabbccdd112233,card_uid:001122334455`.
- **FR-2.2** (v2.0)  The app SHALL preserve any non-`card_uid:` content
  already in `lot_nr` (treat unrecognised entries as opaque user data, do
  not delete).
- **FR-2.3** (v2.0)  The chosen field is **`lot_nr`** for v2 because it
  is the only spool field Spoolman can server-side filter today. This
  is a **temporary** placement — see FR-2.4 for the migration plan.
- **FR-2.4** (deferred — post-v2.1)  When Spoolman supports server-side
  filtering of `extra` keys (PR #773) **or** ships a dedicated UID
  field (issue #716), SpoolPainter SHALL migrate the storage of
  `card_uid` away from `lot_nr` into the appropriate field. The
  migration SHALL:
  - Use a separate Spoolman version-detection step (e.g., probe the
    new query parameter against a benign value) so old Spoolman
    instances continue to work with `lot_nr`-based behaviour.
  - For each spool the app sees with `card_uid:` entries in `lot_nr`,
    move those entries into the new field and remove them from
    `lot_nr` (preserving any non-`card_uid:` content per FR-2.2).
  - Be staged carefully — a v2.x point release adds dual-write +
    dual-read; a later release drops the `lot_nr` writes.
  - Detailed design is **out of scope for v2.0 / v2.1** and is
    deferred to a subsequent release once the upstream change has
    landed.

### FR-3 Read-and-Pair flow

When the user taps **Read Spool** and presents a tag:

- **FR-3.1** (v2.0)  The app SHALL extract the UID.
- **FR-3.2** (v2.0)  The app SHALL look up "is this UID already paired?"
  by calling `GET /api/v1/spool?lot_nr=card_uid:<uid>`.
  This is supported by Spoolman: the `lot_nr` query parameter performs a
  *partial, case-insensitive substring match* (verified in
  `spoolman/api/v1/spool.py`). To minimise false positives in the
  unlikely case of a UID being a substring of another, v2 SHALL include
  the `card_uid:` prefix in the search term.
- **FR-3.3** (v2.0)  If the lookup returns one or more matching spools:
  - If exactly one: the app SHALL pre-select that spool in the Spoolman
    dropdown and pre-fill the form from that spool's filament metadata.
  - If more than one (data anomaly): app SHALL show a clear error and
    let the user resolve it.
- **FR-3.4** (v2.0)  If the lookup returns no matching spool:
  - and the tag's NDEF payload is **valid OpenSpool JSON**: the app
    SHALL pre-fill the form from the tag's payload (matches v1
    `OpenSpoolData → FilamentSpool` mapping). The form remains
    editable.
  - and the tag is **blank or unparseable**: the app SHALL leave the
    form empty so the user can type details. (Same single-screen form,
    same Save action — only difference is whether it's pre-filled.)
- **FR-3.5** (v2.1)  If the tag is recognised by an OpenRFID-style
  vendor parser (Bambu / Creality / Anycubic / Elegoo / Qidi / Snapmaker
  / TigerTag), the app SHALL pre-fill the form from the decoded fields
  in addition to the UID.
- **FR-3.6** (v2.0 — dropdown-driven prefill)  Whenever the user
  selects a Spoolman spool from the dropdown — independent of how
  they got there (blank tag scanned, OpenSpool tag scanned, vendor
  tag scanned, or no tag scanned at all) — the app SHALL pre-fill
  the form (material, brand, color_hex, variant, min/max extruder
  and bed temps) from that spool's filament metadata. Subsequent
  edits remain possible. Re-selecting a different spool overwrites
  the form again. Clearing the selection empties the form back to
  the blank state. This is the single canonical "selection ⇒
  prefill" rule; FR-3.3 (UID-match auto-select) is a special case
  that triggers the same behaviour.

### FR-4 Write/Create-and-Pair flow

When the user taps **Create Spool** (or, in v1-compatible terms, "Write
to NFC"), the flow branches on the tag's NDEF state:

- **blank or OpenSpool-format tag** → write OpenSpool JSON, then pair
  in Spoolman (the v1-equivalent path);
- **vendor/foreign tag** (non-blank, non-OpenSpool — e.g., Bambu /
  Creality / Anycubic pre-encoded tags) → **skip the NDEF write
  entirely** but still pair the tag's UID into Spoolman (FR-4.9). This
  is what allows v2.0 to give branded-spool users the same Spoolman
  spool-usage tracking as OpenSpool users, without ever touching the
  vendor tag's payload.

- **FR-4.1** (v2.0)  Pre-conditions:
  - the form has a selected Spoolman spool (from the dropdown), **or**
  - the form has user-entered material / brand / color / variant /
    temps.
- **FR-4.2** (v2.0)  The app SHALL extract the tag's UID.
- **FR-4.3** (v2.0 — Spoolman-first sequencing)  Before any NDEF
  write, the app SHALL resolve the **Spoolman spool id** to put on
  the tag:
  - **Existing-spool path** (a spool was selected from the
    dropdown): the spool id is the selected spool's id; no Spoolman
    write is performed yet.
  - **New-spool path** (user-entered details, no spool selected):
    the app SHALL run the FR-7 create chain (resolve-or-create
    vendor / filament; POST a new spool with
    `lot_nr=card_uid:<uid>`) **first**, and use the resulting new
    spool's id. This means the Spoolman record exists *before* the
    NDEF write — and because FR-7.3 commits `lot_nr` to it, the
    UID-↔spool mapping is durable even if the NDEF write
    subsequently fails.
  - **Move-on-bind ordering**: FR-5 move-on-bind SHALL run *before*
    the new-spool POST in FR-4.3, so that a tag whose UID is
    already paired to an existing spool routes to the
    existing-spool path (PATCH the matched spool's `lot_nr`)
    instead of creating a duplicate.
- **FR-4.4** (v2.0)  When the tag is **blank or OpenSpool**, the app
  SHALL write the **OpenSpool JSON payload** to the tag (NDEF
  `application/json` MIME record) with **all v1 fields including
  `spool_id`** (the id resolved in FR-4.3). The on-payload `lot_nr`
  field remains reserved/unused by v2 (see FR-14.1). When the tag
  is vendor/foreign (FR-4.9), the NDEF write step does NOT execute.
- **FR-4.5** (v2.0)  When FR-4.4 executed, the app SHALL **read the
  tag back and verify** byte-equality of the NDEF payload. On
  mismatch, surface a clear error. The Spoolman side may already
  have a spool created from FR-4.3 (new-spool path); that spool
  remains and is recoverable on a retry — the user simply taps
  again, and the existing-spool path will then take over (UID
  lookup finds the just-created spool by its `lot_nr`, and the
  PATCH path runs idempotently). For vendor/foreign tags (FR-4.9),
  this step does NOT apply — there is no NDEF write to verify.
- **FR-4.6** (v2.0)  After FR-4.4 + FR-4.5 succeed (or for
  vendor/foreign tags, immediately), the app SHALL pair the UID
  with the Spoolman spool:
  - **Existing-spool path**: app SHALL PATCH that spool's `lot_nr`,
    appending `card_uid:<uid>` if not already present, and SHALL
    ensure no other spool keeps the same UID (see FR-5 — "move on
    bind").
  - **New-spool path**: pairing was already committed by FR-4.3
    (the POST set `lot_nr=card_uid:<uid>`), so this step is a
    no-op for the new-spool path.
  - This step runs whether or not FR-4.4 executed (i.e., it runs
    for blank, OpenSpool, **and** vendor/foreign tags).
- **FR-4.7** (v2.0 — vendor-tag NDEF protection)  v2 SHALL NEVER
  overwrite the NDEF payload of a tag whose payload is **not**
  OpenSpool. (Defensive: protect branded pre-printed tags. Q5 = A —
  strict.) This rule restricts the **NDEF write** only; the
  Spoolman pairing chain (FR-4.3 / FR-4.6 / FR-7) MAY still proceed
  against the same tag's UID — see FR-4.9.
- **FR-4.8** (v2.0 — "raw write" mode, Q7 = C)  The app SHALL
  provide a side mode that lets the user write an OpenSpool payload
  to a tag without any Spoolman interaction (preserves a v1-style
  untethered flow for users without Spoolman). This mode SHALL skip
  the FR-4.3 Spoolman-first step and the FR-4.6 PATCH/POST, and
  SHALL still respect FR-4.7 (no overwrite of vendor tags). In raw
  mode the OpenSpool payload SHALL omit `spool_id`.
- **FR-4.9** (v2.0 — UID-only pair for vendor/foreign tags)  When the
  tag is classified as **non-blank, non-OpenSpool** (FR-4.7
  classification), the app SHALL still allow the user to pair its UID
  into Spoolman, **subject to explicit user opt-in**:
  - **Read time**: the user SHALL see the **same blank-form path as
    for unparseable tags** (FR-3.4 / FR-11.2): UID is captured, form
    is empty, user can either pick an existing Spoolman spool from
    the dropdown or type details for a new spool. No prompt is shown
    at this point — Read is non-destructive.
  - **Save / Write opt-in prompt**: when the user presses
    **Save / Write** with a vendor/foreign-classified tag staged, the
    app SHALL present a **modal bottom sheet** (FR-13.2 pattern) with
    copy on the order of:
    > "This tag is encoded and we can't read its contents — but we
    > can still map its UID to a Spoolman spool. Would you like to
    > pair the UID only?"
    > Actions: **Pair UID only** / **Cancel**
  - On **Cancel**: no NDEF write, no Spoolman call; user returns to
    the main screen with their form state intact.
  - On **Pair UID only**: the app SHALL execute the **Spoolman
    pairing chain** (FR-4.6: PATCH for existing spool, FR-7 create
    chain for user-entered details — note FR-4.3's Spoolman-first
    sequencing applies here too) using the tag's UID — and SHALL
    **skip the NDEF write step (FR-4.4) and verify (FR-4.5)**
    entirely.
  - **Move-on-bind (FR-5)** still applies — a vendor tag's UID
    already paired to another spool prompts the same re-pair
    confirmation as any other tag (after the opt-in is confirmed).
  - The UI SHALL surface throughout this flow that the tag is
    unreadable / pre-encoded and that no NDEF data will be written
    (so the user knows the operation is a UID-only pair, not a
    content write).
  - This path is the v2.0 mechanism for **Branded-Tag Reader (P3)**
    spool-usage tracking; v2.1 adds vendor decoding (FR-1.4 /
    FR-3.5) but the write rule (no NDEF overwrite) and the opt-in
    prompt do not change.

### FR-5 Move-on-bind (re-pair / tag reuse)

- **FR-5.1** (v2.0)  Before pairing a UID with spool **B**, the app
  SHALL search for any other spool **A** whose `lot_nr` already contains
  `card_uid:<uid>` (same FR-3.2 lookup).
- **FR-5.2** (v2.0)  If such spool A exists and A ≠ B:
  - The app SHALL prompt with a confirmation: **"This tag is already
    paired with another spool — re-pair to this one?"** showing the
    other spool's display name when available.
  - On confirm: the app SHALL atomically remove **only** the matched
    `card_uid:<uid>` token from spool A's `lot_nr` (PATCH A) and add
    it to spool B's `lot_nr` (PATCH or POST B). Other UIDs already in
    A's `lot_nr` (i.e., the second tag of a two-tag pair) SHALL NOT
    be moved (Q6 = A — each tag is paired independently).
  - On any HTTP failure during the two-step move, surface the error;
    do not partially commit (Q11 = A).
  - On user cancel: no changes are made.

### FR-6 Two-tag-per-spool flow (NEW)

- **FR-6.1** (v2.0)  After a successful first pairing (Read or Create),
  the app SHALL surface an **optional** action: a "Pair another tag with
  this spool?" button. Skipping it leaves the spool with one UID in
  `lot_nr`; tapping it begins the second-tag pairing flow.
  (Q2 = B — optional.)
- **FR-6.2** (v2.0)  When pairing the second tag, the app SHALL:
  - **Write the same OpenSpool NDEF payload** to the second tag as was
    written to the first (identical bytes), subject to the same
    write-then-verify rule (FR-4.5) and the same vendor-tag protection
    (FR-4.7 — never overwrite a non-OpenSpool tag).
  - **Append** `card_uid:<uid2>` to the **same** Spoolman spool's
    `lot_nr` (PATCH).
  - End state: both physical tags carry identical OpenSpool JSON, and
    the Spoolman spool's `lot_nr` holds both UIDs.
- **FR-6.3** (v2.0)  Each individual tag pairing SHALL still respect
  FR-5 (move-on-bind) — i.e., if the second tag's UID is already paired
  to a different Spoolman spool, the user is prompted to re-pair.
- **FR-6.4** (v2.0)  An interrupted second-tag flow is NOT persisted
  across app launches (Q2A = C). The user can simply scan the second
  tag at any later point: FR-3 will find the spool by UID and FR-6.1
  will offer to pair the additional tag (re-deriving the OpenSpool
  payload to write from the spool's filament metadata).

### FR-7 Spool / filament / vendor creation (one-shot)

When the user-entered-details path requires creating a new Spoolman
spool:

- **FR-7.1** (v2.0)  The app SHALL look up whether a matching **vendor**
  (brand) exists in Spoolman (case-insensitive name match against
  `GET /api/v1/vendor`). If not, it SHALL POST a new vendor.
- **FR-7.2** (v2.0)  The app SHALL look up whether a matching
  **filament** exists in Spoolman, matched by (vendor, material,
  color_hex, variant). If not, it SHALL POST a new filament.
- **FR-7.3** (v2.0)  The app SHALL POST a new **spool** with:
  - `filament` = the vendor/filament resolved or created above,
  - `lot_nr` = `card_uid:<uid>`.
- **FR-7.4** (v2.0)  On any HTTP error during this chain, the app SHALL
  surface the error and not partially commit (Q11 = A; user retries).
- **FR-7.5** (v2.0)  Reuse rule: an existing Spoolman vendor / filament
  SHALL be reused before creating a new one (Clarification 6 = X).

### FR-8 Material & brand presets

- **FR-8.1** (v2.0)  The app SHALL ship hardcoded **material presets**
  (PLA, ABS, PETG, TPU, ASA, PC, Nylon, PVA, HIPS, "Other") with default
  extruder/bed temperature ranges (same as v1).
- **FR-8.2** (v2.0)  The app SHALL ship a hardcoded **brand starter
  list** (the v1 list).
- **FR-8.3** (v2.0)  When Spoolman is reachable, the app SHALL fetch:
  - the vendor list (`GET /api/v1/vendor`) — merged into the brand
    dropdown, deduplicated case-insensitively against the hardcoded
    list;
  - the filament list (`GET /api/v1/filament`) — used by FR-7.2 to find
    matching filaments and (optionally) to surface existing filaments
    in the picker.
- **FR-8.4** (v2.0)  Materials remain hardcoded as the on-screen list
  of selectable types; Spoolman's filament list is consulted when the
  user is building a new spool (FR-7).
- **FR-8.5** (v2.0 — user-defined entries)  The material and brand
  pickers SHALL each include an **"Add custom"** entry. Selecting it
  prompts the user to type a free-form name (and, for materials,
  default extruder/bed temp ranges).
  - The custom entry SHALL be usable immediately for the current
    create-and-pair flow.
  - When the create chain runs (FR-7), the custom material is recorded
    on the new Spoolman filament's `material` field and the custom
    brand triggers a vendor lookup-or-create (FR-7.1) by that name —
    so user-added entries propagate to Spoolman automatically as a
    side effect of saving the spool.
  - User-added entries SHALL also be persisted locally (DataStore /
    Room) so they appear in the picker on subsequent runs even before
    the next Spoolman fetch refreshes the merged list.
  - On the next successful Spoolman fetch, server-side entries take
    precedence (the local-only entry is deduplicated against the
    Spoolman vendor/filament list, case-insensitively).

### FR-9 Settings

- **FR-9.1** (v2.0)  Settings SHALL allow the user to set the **Spoolman
  server URL** (free-text, validated by attempting a fetch).
- **FR-9.2** (v2.0)  Settings SHALL allow the user to set a **sort
  order** for the Spoolman dropdown (None / Brand A-Z / Material A-Z /
  Last Used).
- **FR-9.3** (v2.0)  Settings SHALL allow the user to **override the
  theme**: System / Light / Dark.
- **FR-9.4** (v2.1)  Settings SHALL include a **Vendor Keys** section
  where the user can add per-vendor decryption keys (Mifare Classic A/B
  keys, primarily for Bambu). Keys SHALL be persisted at rest **using
  Android Keystore-backed encryption** (e.g.,
  `EncryptedSharedPreferences` or Tink-wrapped DataStore). The app
  SHALL ship **no keys**. Without keys, encrypted vendor formats
  fall back to UID-only behaviour (Q4 = B).

### FR-10 Spoolman optionality and offline behaviour

- **FR-10.1** (v2.0)  Spoolman is **optional** (Q7 = C):
  - If Spoolman is **not configured** or **unreachable**, the app
    SHALL still:
    - read tags (UID + decoded payload),
    - allow the user to enter "raw write" mode (FR-4.8) to write an
      OpenSpool payload to a blank/OpenSpool tag without any Spoolman
      side effects;
  - and SHALL disable:
    - the Read-and-Pair lookup (FR-3.2),
    - the create-and-pair commit (FR-7),
    - the Spoolman dropdown contents.
- **FR-10.2** (v2.0)  When Spoolman is unreachable, a **visible banner**
  SHALL appear with a Retry control (Q12 = B). Cached vendor / filament
  / spool data MAY remain visible read-only.
- **FR-10.3** (v2.0)  No queueing or write-deferral is implemented
  (Q11 = A — clear error + retry).

### FR-11 Existing-tag display (v1 OpenSpool tags)

- **FR-11.1** (v2.0)  When a tag is read whose payload parses as
  OpenSpool JSON, the app SHALL pre-fill the form (FR-3.4 — same as v1's
  read flow).
- **FR-11.2** (v2.0)  When a tag is read whose payload does **not**
  parse, the app SHALL still show the UID and proceed with the
  blank-form / new-spool flow.

### FR-12 Theming

- **FR-12.1** (v2.0)  The app SHALL support **dark mode following
  system**, **light mode**, and **dynamic color (Material You)** on
  Android 12+ (Q16 = B).
- **FR-12.2** (v2.0)  The app SHALL provide an explicit **theme
  override** in Settings (System / Light / Dark) — derived from FR-9.3.

### FR-13 UI shape

- **FR-13.1** (v2.0)  The app SHALL present a **single main screen**
  with two primary actions: "Read NFC Tag" and "Write to NFC", a
  Spoolman dropdown, a filament form, and a temperature panel —
  visually similar to v1 (Clarification 7 = "mostly A").
- **FR-13.2** (v2.0)  Multi-step flows that don't fit on the main
  screen (e.g., "Scan the second tag", "Create profile?",
  "Tag already paired — re-pair?") SHALL appear as **modal bottom
  sheets** rather than new top-level screens (Q8 = B).
- **FR-13.3** (v2.0)  Settings SHALL be a separate screen reachable
  from the gear icon (same as v1).
- **FR-13.4** (v2.0)  No new top-level destinations are introduced in
  v2.

### FR-14 Tag write content

- **FR-14.1** (v2.0)  When writing the NDEF payload, the app SHALL emit
  the same OpenSpool JSON shape as v1 (Clarification 4 = A): `protocol`,
  `version`, `type`, `color_hex`, `brand`, `min_temp`, `max_temp`,
  `bed_min_temp`, `bed_max_temp`, `subtype`, and `spool_id`.
  `spool_id` SHALL be populated for **every** non-raw write — for
  the existing-spool path it is the selected spool's id; for the
  new-spool path it is the id assigned by Spoolman after the FR-7
  POST runs first per FR-4.3. The **only** path where `spool_id` is
  omitted is FR-4.8 raw-write mode (no Spoolman interaction at
  all). The on-payload `lot_nr` field is reserved/unused by v2
  (UID lives on the Spoolman side; the tag's hardware UID is what
  matters).
- **FR-14.2** (v2.0)  The app SHALL **never write** the NDEF payload of
  a non-blank, non-OpenSpool tag (FR-4.7 — protects branded vendor
  tags). The Spoolman side of the pairing chain (FR-4.6 / FR-7) is
  unaffected by this rule — see FR-4.9 (UID-only pair).

### FR-15 Naming

- **FR-15.1** (v2.0)  The app retains the user-visible name
  **SpoolPainter** (Q9 = A). Package id remains
  `com.spoolpainter.app`.

---

## 5. Non-Functional Requirements

### NFR-1 Architecture
- **NFR-1.1**  Layered architecture: UI → ViewModel → **Repository** →
  data source (NFC / Spoolman / Settings).
- **NFR-1.2**  Repositories: `SpoolmanRepository`, `NfcRepository`,
  `SettingsRepository`. UI components SHALL NOT call data sources
  directly (eliminates the v1 wart where `SpoolmanFilamentDropdown`
  called `SpoolmanService` itself).
- **NFR-1.3**  Per-screen ViewModels (`MainViewModel`,
  `SettingsViewModel`); state exposed as `StateFlow<UiState>`; events
  flow in via methods.
- **NFR-1.4**  The NFC layer SHALL replace v1's two-boolean state
  machine with an explicit sealed `NfcResult` (or equivalent):
  `Idle | Reading | Writing | Verifying | Success | Error`.

### NFR-2 Dependency injection
- **NFR-2.1**  v2 SHALL adopt **Hilt** for DI. Repositories and the
  Spoolman client SHALL be `@Singleton`.

### NFR-3 Persistence
- **NFR-3.1**  Settings SHALL move from `SharedPreferences` to **Jetpack
  DataStore (Preferences)**.
- **NFR-3.2**  v2 SHALL use **Room** if and only if a list-shape local
  store is needed. FR-8.5 (user-added materials/brands persisted
  locally) is a list-shape store and is now expected to require Room
  unless a simpler DataStore-Proto schema is acceptable. Final choice
  deferred to Application Design (OD-2).
- **NFR-3.3**  No migration of v1 `SharedPreferences` is required.
- **NFR-3.4**  v2.1 vendor keys (FR-9.4) SHALL be stored using
  **Android Keystore-backed encryption** (e.g.,
  `EncryptedSharedPreferences`, or Tink-wrapped DataStore).

### NFR-4 Testing
- **NFR-4.1**  v2 SHALL ship with at minimum:
  - Unit tests for `OpenSpoolData` JSON encode/decode (incl. the
    language-prefix quirk and round-trip).
  - Unit tests for `lot_nr` parsing/serialisation (`card_uid:` prefix,
    comma-separated, multi-UID, preservation of unrecognised entries).
  - Unit tests for `SpoolmanRepository` against a fake API (paging,
    find-by-UID, vendor/filament/spool create chain, PATCH
    move-on-bind, two-tag append).
  - Unit tests for UID hex canonicalisation and equality.
  (Q22 = A.)
- **NFR-4.2**  ViewModel tests, Compose UI tests, and instrumented NFC
  tests are out of scope for v2's minimum bar but MAY be added if
  cheap.

### NFR-5 Logging
- v2 SHALL not emit `Log.d`/`Log.e` calls in release builds. Either
  strip or wrap behind a debug-only logger.

### NFR-6 NFC reliability
- **NFR-6.1**  After every write (FR-4), the app SHALL re-read the tag
  and verify byte-equality of the NDEF payload. On mismatch, surface
  a clear error and abort.
- **NFR-6.2**  The app SHALL not perform an automatic write retry; the
  user retries by tapping again.

### NFR-7 Network
- **NFR-7.1**  Spoolman calls (GET, POST, PATCH) SHALL surface clear
  errors to the UI on any non-2xx or transport failure (Q11 = A). No
  silent swallowing.
- **NFR-7.2**  A short-lived in-memory cache MAY be retained for the
  filament/spool list to keep UI snappy; pull-to-refresh invalidates
  it.
- **NFR-7.3**  `usesCleartextTraffic` remains true (LAN HTTP).
- **NFR-7.4**  No Spoolman authentication in v2 (Q9 = A).

### NFR-8 Platform
- **NFR-8.1**  `minSdk = 29`, `targetSdk = 36`, `compileSdk = 36` —
  unchanged from v1.
- **NFR-8.2**  Kotlin, Jetpack Compose, Material 3.
- **NFR-8.3**  Same package id `com.spoolpainter.app`. `debug` variant
  retains `.debug` suffix for side-by-side install during development.

### NFR-9 Distribution
- **NFR-9.1**  v2 SHALL be released as **two waves**:
  - **v2.0** — pivot, two-tag flow, OpenSpool-only reads, raw-write
    mode, architecture overhaul. Single release on sideload + Play
    Store.
  - **v2.1** — multi-vendor decoding + key UI. Layered atop v2.0.
- **NFR-9.2**  Distribution: **sideload + Google Play Store** for both
  v2.0 and v2.1.

### NFR-10 Localisation / accessibility
- v2 remains English-only; basic Compose-default accessibility is the
  expected bar. (Not explicitly opted into — defaults stand.)

### NFR-11 Licensing (NEW for v2.1)
- **NFR-11.1**  v2.0 retains the project's current licence (no GPL
  obligations introduced).
- **NFR-11.2**  v2.1 introduces a port of OpenRFID parser code, which
  is **GPL-3.0**. SpoolPainter as a whole SHALL re-license to GPL-3.0
  starting at the v2.1 release. Distribution channels (Play Store,
  GitHub Releases) SHALL include the appropriate GPL-3.0 source-code
  offer per §6 of the licence. (Q3A = "C also B" — defer to v2.1, then
  port and accept GPL-3.0.)

### NFR-12 Vendor data packaging (NEW for v2.1)
- **NFR-12.1**  Vendor format definitions and lookup tables (material
  codes, colour palettes, byte offsets) SHALL be **baked into the app
  as static assets**. Updates require an app release. (Q3B = A — no
  user-overrides, no remote config in v2.1.)

---

## 6. AIDLC Extension Configuration

| Extension | Enabled | Source |
|---|---|---|
| Security Baseline | **No** | Q25 = B |
| Property-Based Testing | **No** | Q26 = C |

These extensions are NOT enforced as blocking constraints. Sensible
defensive coding still applies — in particular, vendor key storage
(NFR-3.4 / FR-9.4) must use Keystore-backed encryption regardless of
the extension being off, because keys are user-supplied secrets.

---

## 7. Out of Scope (v2 — both 2.0 and 2.1)

- Multi-server / multi-instance Spoolman support.
- HTTPS / Spoolman authentication (basic / bearer / API key).
- Writing **anything** to a non-blank, non-OpenSpool tag — branded
  vendor tags are read-only (FR-4.7).
- Writing only-UID / minimal markers to OpenSpool tags (we keep writing
  full OpenSpool payload).
- Migrating v1 `SharedPreferences`.
- Multi-language localisation.
- Property-based or Compose UI testing.
- Auto-update channel.
- Persisting interrupted-second-tag state across launches.
- Remote config / over-the-air vendor-format updates.
- **Migrating UID storage off `lot_nr`** into Spoolman's `extra`
  fields or a dedicated UID field (FR-2.4) — deferred until the
  upstream Spoolman change lands (PR #773 / issue #716).

---

## 8. Open Items Deferred to Design

- **OD-1**  Whether a "currently paired with" status row appears under
  the Spoolman dropdown showing the live UID-→spool mapping
  (FR-13).
- **OD-2**  Whether `Room` is actually needed (NFR-3.2). If no
  list-shape local store is required, drop the dependency at
  code-generation time.
- **OD-3** (post-v2.1)  Watch upstream Spoolman PR #773 + issue #716;
  when either lands, design and ship FR-2.4 (UID storage migration
  off `lot_nr`).

---

## 9. Traceability

| Source | Section / Question | Reflected in |
|---|---|---|
| v1 codebase | `OpenSpoolData`, `MaterialDatabase`, `BrandDatabase`, `SpoolmanService` | FR-8, FR-14 |
| User vision (chat) | Tag UID is the firmware key; Spoolman holds the mapping | FR-1, FR-2 |
| Q1 round-1 (corrected) | Tag-data prefills form, then create spool | FR-3, FR-7 |
| Q2 round-1 + Clarification 4 | Keep writing full OpenSpool payload incl. spool_id | FR-14 |
| Q3 round-1 + Clarification 2 | Lowercase hex, no separators, `card_uid:` prefix, multi-UID | FR-1.2, FR-2 |
| Q4 round-1 + Clarification 5 | `lot_nr` field for v2 | FR-2.3 |
| Q5 round-1 | Pre-fill from existing tag data | FR-3.4 |
| Q6 round-1 + Clarification 1A | Re-pair with confirmation; move-on-bind | FR-5 |
| Q7 round-1 | Auto-create filament + brand if missing; reuse Spoolman | FR-7 |
| Q9 round-1 | No Spoolman auth | FR-6 / NFR-7.4 |
| Q11 round-1 + Clarification 8 | Clear error + retry; no partial commit | FR-5, FR-7, NFR-7.1 |
| Q12 round-1 + Clarification 8 | Read-only when offline; Write disabled | FR-10 |
| Q13 round-1 | Write-then-verify | NFR-6 |
| Q14 round-1 + Clarification 6 | Hardcoded presets + dedupe-merge with Spoolman | FR-8 |
| **Chat (after round 2)** | **User-added custom material/brand propagates to Spoolman + persisted locally** | **FR-8.5** |
| Q15 round-1 + Clarification 7 | Single screen, two buttons, like v1 | FR-13 |
| Q16 round-1 | Dark + dynamic color + override | FR-12 |
| Q17 round-1 | DataStore + Room | NFR-3 |
| Clarification 9 | Per-screen VMs + Repositories; Hilt | NFR-1, NFR-2 |
| Q20 round-1 | minSdk/targetSdk unchanged | NFR-8 |
| Q21 round-1 | Sideload + Play Store | NFR-9 |
| Q22 round-1 | Unit tests for models + Spoolman client | NFR-4 |
| Q23 round-1 | No migration | NFR-3.3 |
| Q25 round-1 | Security extension OFF | §6 |
| Q26 round-1 | PBT extension OFF | §6 |
| Clarification 1B + firmware doc | Server-side `lot_nr` filter for UID lookup | FR-3.2, FR-5.1 |
| Tag-reuse chat req (round 1) | Move-on-bind | FR-5 |
| **Q1 round-2 (D + Spoolman source)** | **Spoolman supports `lot_nr` substring match — verified in `spoolman/api/v1/spool.py`; `card_uid:` prefix included to disambiguate** | **FR-3.2** |
| **Q2 round-2** | **Two-tag flow optional, button-driven** | **FR-6.1** |
| **Q2 round-2 + chat** | **Second tag gets same NDEF write as first; both tags identical at end** | **FR-6.2** |
| **Q2A round-2** | **No persistence of interrupted second-tag state** | **FR-6.4** |
| **Q3 round-2 (D)** | **Multi-vendor decode + Settings keys** | **FR-1.4, FR-3.5, FR-9.4** |
| **Q3A round-2 (C → B)** | **Defer multi-vendor to v2.1; v2.1 adopts GPL-3.0** | **§3, NFR-11** |
| **Q3B round-2 (A)** | **Bake vendor data into app** | **NFR-12** |
| **Q4 round-2 (B)** | **Per-vendor key list in Settings, encrypted at rest** | **FR-9.4, NFR-3.4** |
| **Q5 round-2 (A)** | **Never overwrite branded vendor tags** | **FR-4.7, FR-14.2** |
| **User-Stories-stage P3 gap** | **Vendor tags get UID-only pair (no NDEF write) — branded spools tracked in Spoolman** | **FR-4.9** |
| **User-Stories-stage spool_id-on-tag req** | **Always write spool_id (incl. blank-tag → new-spool path); Spoolman create runs first to provide the id** | **FR-4.3, FR-4.4, FR-7.3** |
| **User-Stories-stage dropdown-prefill req** | **Selecting a Spoolman spool from the dropdown prefills the form, regardless of how the user got there** | **FR-3.6** |
| **User-Stories-stage temp-`lot_nr` framing + Spoolman extras research** (PR #773, issues #716 / #427 — `extra` fields exist on the model + API today; server-side filtering is in flight, not landed) | **`lot_nr` storage is temporary; migration plan deferred until upstream filtering ships** | **FR-2 preamble, FR-2.3, FR-2.4 (NEW), §7 out-of-scope, §8 OD-3** |
| **Q6 round-2 (A)** | **Re-pair moves only the matched UID, not the second tag** | **FR-5.2** |
| **Q7 round-2 (C)** | **Spoolman optional + raw-write side mode** | **FR-4.8, FR-10.1** |
| **Q8 round-2 (B)** | **Bottom-sheet steps for multi-step flows** | **FR-13.2** |
| **Q9 round-2 (A)** | **Keep "SpoolPainter" name** | **FR-15** |
| **Q10 round-2 (B)** | **Split into v2.0 + v2.1** | **§3, NFR-9.1** |

---

## 10. Summary

- **What v2 is**: a major behavioral pivot — tag's hardware UID becomes
  the printer's primary key for a spool; Spoolman holds the UID↔spool
  mapping in `lot_nr` using `card_uid:<lowercase-hex>` entries
  (comma-separated, supporting two tags per spool).
- **What the app does**:
  - **v2.0** — fast pairing with read-then-prefill and create-then-PATCH
    flows, an optional second-tag flow, move-on-bind semantics, an
    untethered raw-write mode for users without Spoolman, and a strict
    "never overwrite a vendor-branded tag" rule. Reads OpenSpool tags
    only.
  - **v2.1** — multi-vendor decode (Bambu / Creality / Anycubic /
    Elegoo / Qidi / Snapmaker / OpenSpool / TigerTag) via a Kotlin port
    of OpenRFID; Settings UI for vendor keys, encrypted at rest;
    project re-licensed to GPL-3.0.
- **Architecture**: per-screen ViewModels + Repository layer + Hilt;
  `StateFlow<UiState>`; explicit NFC state model.
- **Persistence**: DataStore (+ Room only if list-shape local state is
  needed). v2.1 keys via Keystore-backed encryption.
- **Distribution**: two release waves (v2.0 then v2.1), sideload + Play
  Store; in-place update; no migration.
- **Quality bar**: unit tests for models and the Spoolman client (incl.
  `lot_nr` UID logic); Security Baseline and PBT extensions OFF.
