# SpoolPainter v2 — Clarification Questions

I read your answers in `requirement-verification-questions.md`. Most are clear,
but a handful introduced ambiguities or new wrinkles I need to resolve before
producing the requirements document. Please answer these by filling in the
letter (or "Other" + description) after each `[Answer]:`. Tell me when done.

You also added a new requirement in chat:
> "Also one more req is being able to reuse tag, user can just pull the tag from
>  consumed filament, add to new one and redo mapping"

Q1A below confirms exactly how this should behave.

---

## Clarification 1A — Tag reuse mechanics (NEW REQUIREMENT)

You want to allow re-using a tag: pull it off a finished spool, stick it on a
new one, re-map. This is essentially the same path as Q6 in the original
questionnaire. Confirm the desired behavior:

A) **Re-pair always allowed with a confirmation** — when the user reads a tag
   whose UID is already in some Spoolman spool's `lot_nr`, app says "this tag
   is paired with **\<spool name\>** (id #N) — re-pair to a different spool?"
   On confirm, the UID is **removed** from the old spool's `lot_nr` and added
   to the new one (atomic move, last-write-wins on the user's side)
B) **Re-pair without confirmation** — silently move the UID; faster but riskier
C) **Re-pair as ADD, not MOVE** — leave the UID on the old spool, also add it
   to the new one (so multiple Spoolman spools can claim the same tag — only
   meaningful if the firmware/printer is fine with that)
X) Other (please describe)

[Answer]: A

---

## Clarification 1B — How do we look up "is this UID already paired?"

To implement A above, on tag read v2 needs to find any Spoolman spool whose
`lot_nr` contains `card_uid:<uid>`. Which strategy?

A) **Client-side scan** — fetch all spools (already paginated) and search
   their `lot_nr` for the UID locally
B) **Server-side filter** — call Spoolman with a query parameter on `lot_nr`
   if Spoolman supports substring matching (I'd need to verify this before
   relying on it)
C) **Try B, fall back to A** if the server doesn't support filtering
X) Other (please describe)

[Answer]: B, this is firmware whatever this decide to do https://github.com/paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware/blob/afc-spoolman-auto-register/docs/afc-lite.md this branch has logic for this

---

## Clarification 2 — Q3 (UID format) confirmation

You wrote:
> "card_uid:aabbccdd112233,card_uid:001122334455 — Each RFID card UID is stored
>  with the prefix `card_uid:` followed by the hex string of the raw UID bytes."

So my reading is:
- **Storage in `lot_nr`**: `card_uid:` + lowercase hex, no separators, multiple
  UIDs comma-separated (so a single Spoolman spool *can* have multiple paired
  tags).
- **UI display**: lowercase hex, no separators (matches storage).

Confirm:

A) **Yes, exactly as above** — storage and UI both lowercase-hex; multiple UIDs
   per spool supported (read all, write all comma-separated)
B) **Yes for storage; UI display uppercase** for readability
C) **Single UID per spool only** — even though `lot_nr` can hold a list,
   v2 enforces one UID per spool (overwrites instead of appends)
X) Other (please describe)

[Answer]: A

For context: I searched public Spoolman docs/wiki/github for the
"card_uid:" convention and could not find an authoritative source. If this
convention comes from a specific firmware project (Klipper plugin, OpenSpool
fork, etc.), please point me at it so I can pin the exact format in the
requirements. If you'd rather we just adopt this convention as **our** chosen
v2 standard, that's fine too — say so.
https://github.com/paxx12-snapmaker-u1/SnapmakerU1-Extended-Firmware/blob/afc-spoolman-auto-register/docs/afc-lite.md 
---

## Clarification 3 — Q1 + Q5 reconciliation (workflow when tag has data)

Q1 [B]: you said "tag has the detail already, so we fill that like its in v1
and then we just create the spool" — i.e., when reading a tag that already
has OpenSpool JSON, we **pre-fill the form from the tag** and create a
Spoolman spool.

Q5 [C]: same direction — pre-fill from existing tag data.

So for the **read-then-create-spool** flow, the source of pre-fill is the
tag's existing JSON. Confirm what happens when the **tag is blank** (a
brand-new sticker):

A) **User must type details into the form manually** (then we write OpenSpool
   JSON to the tag AND create the Spoolman spool with `lot_nr` = `card_uid:<uid>`)
B) **Read flow only supports tags that already have data** — for blank tags,
   user must use the "Write" flow which expects them to either pick a Spoolman
   spool from the dropdown or fill the form
C) **Both flows lead to the same form** — only difference is whether the form
   is pre-filled (from tag) or blank (user types)
X) Other (please describe)

[Answer]: C

---

## Clarification 4 — Q2 (tag write content)

You said:
> "B, but writes everything for printer to use as backup except spool_id, this
>  new firmware of printer do not use spool_id at all, maybe we write it too,
>  as it do not matter from firmware side"

Confirm exactly which fields v2 writes to the NFC tag (matching the v1
OpenSpool format wherever possible):

A) **All v1 fields including `spool_id`** — exactly the v1 payload (cheap, no
   harm if firmware ignores it; useful for older firmwares / future tools)
B) **All v1 fields except `spool_id`** — color/material/temps only; explicit
   "no spool_id" decision
C) **All v1 fields, `spool_id` only when a Spoolman spool was selected**
   (what v1 does today)
X) Other (please describe)
 
[Answer]: A

---

## Clarification 5 — Q4 (which Spoolman field stores UID)

You said:
> "A, consider it lot_nr but maybe some new field we add in spoolman."

I read this two ways. Which is right?

A) **Use `lot_nr` for v2** — if Spoolman ever adds a dedicated tag-UID field
   later, we'll migrate; not a v2 concern
B) **Make the field configurable in Settings** — default `lot_nr`, but the
   user can change it to any Spoolman field name (covers future custom fields)
C) **v2 will use `extra.<some_name>`** — we should not pollute `lot_nr`; we'll
   pick the custom-field name now
X) Other (please describe)

[Answer]: A, also i think we have option to write new feilds in Spoolman, so maybe we decide to use new feild, but tht ill decided before release

---

## Clarification 6 — Q14 (presets)

You said:
> "X, we keep presets as we have, so its easier to user to select when writing
>  tag, also we pull the brand from user spoolman and add to list or replace
>  our hardcoded presets with their"

Confirm the merge rule:

A) **Materials = hardcoded presets only** (PLA, ABS, PETG, …) PLUS Spoolman's
   filaments are shown when picking an existing filament; **Brands = union
   of hardcoded list + Spoolman vendors**, deduplicated
B) **Materials + Brands = union of hardcoded + Spoolman**, deduplicated
C) **Spoolman wins**: when Spoolman is reachable, lists come from Spoolman;
   hardcoded only shown when offline
X) Other (please describe)

[Answer]:X: We show all hardcoded presets and do duplication mathch and use spoolman when present, whole idea is creating a spool in spoolman requires brand, filament creation so we will have to do all three in one go, so we reuse wherver possible

---

## Clarification 7 — Q15 (UI shape)

You said:
> "X lets keep it similar to what we have 1 screen with both read and write button"

Confirm where each new flow lives:

A) **Single main screen, two buttons**: "Read NFC Tag" and "Write to NFC".
   Below is a form. The form behaves like v1 EXCEPT that on every successful
   read/write the app also performs the corresponding Spoolman action
   (PATCH `lot_nr` or POST spool). Settings is a separate screen via the gear
   icon. No new top-level destinations
B) **Same as A**, but with one additional UI element: a "paired with" status
   row under the Spoolman dropdown that shows the current UID-→spool mapping
   state for the active form
C) **Same as A**, but if a tag is read whose UID is already paired, push a
   small modal/sheet instead of just the snackbar
X) Other (please describe)

[Answer]: Mostly A, maybe B i am not getting what you mean in 

---

## Clarification 8 — Q11 + Q12 reconciliation (errors/offline)

Q11 [A]: "Show clear error and let user retry — no partial success"
Q12 [B]: "Allow read-only tag scanning (display UID + cached spool list) but
        block PATCH/POST"

These are consistent. Confirm one detail:

A) **If Spoolman is offline AND user reads a tag with data**, the app still
   pre-fills the form (no Spoolman call needed for that), but disables the
   "this will create a Spoolman spool" path until Spoolman is reachable
B) **If Spoolman is offline**, all Spoolman-touching flows are disabled
   (mapping to existing, creating new) AND the "Write to NFC" button is
   ALSO disabled because v2 always pairs to Spoolman as part of the write
C) **If Spoolman is offline**, "Write to NFC" still works (writes the JSON to
   the tag) and the Spoolman pairing is queued / skipped with a warning
X) Other (please describe)

[Answer]: B

---

## Clarification 9 — Q18/Q19 architecture & DI (you asked to discuss)

You asked to discuss architecture and DI. My recommendations, framed as
options so you can pick:

**Architecture (Q18):**

A) **Per-screen ViewModels + Repository layer** (RECOMMENDED)
   - `MainViewModel`, `SettingsViewModel`
   - Repos: `SpoolmanRepository`, `NfcRepository`, `SettingsRepository`
   - `StateFlow<UiState>` per screen
   - Why: v2 has 4-5 distinct flows (Read+pre-fill, Write+pair, Write+create,
     Settings, error/offline). The repository layer is the right place for
     "find spool by tag UID", "PATCH lot_nr", "create spool + filament",
     keeping the v1 issue (UI components calling Service directly) from
     reappearing.
B) **Single ViewModel + StateFlow + Repositories** — close to v1's pattern
   but cleaner; fine if v2 truly stays one screen
C) **MVI** — overkill for an app this size; not recommended

**DI (Q19):**

A) **Hilt** (RECOMMENDED)
   - Why: standard Google-blessed for Android; first-class with ViewModel +
     navigation; makes the test bar from Q22 easy because we can swap repo
     bindings.
B) **Koin** — lighter setup, no kapt, but smaller community for Android
C) **None / manual construction** — fine for v1 size, but if we adopt
   repositories we'll be wiring them by hand in many places.

Pick one for each:

**Architecture:**
[Answer]: A

**DI:**
[Answer]: A

---

## Clarification 10 — Anything else from your cut-off message?

Your last chat message ended with "done, also i a…" — was there another
requirement to capture?

A) **No, that was a typo / nothing follows**
B) **Yes** (describe in [Answer]; the tag-reuse requirement above is already
   captured separately)

[Answer]:A
