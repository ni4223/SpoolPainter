# SpoolPainter v2 — Clarification Round 2

You introduced three significant changes to v2. I'm capturing them here as
discrete questions before updating `requirements.md`. Please answer each
with a letter (or "Other" + description) after `[Answer]:`. Tell me when
done.

---

## What you said (my reading)

1. **UID lookup may not be a server-side filter.** The
   `GET /v1/spool?lot_nr=card_uid:<uid>` pattern came from the firmware doc
   I read; you're not sure Spoolman actually supports filtering on
   `lot_nr` substrings. Needs verification before we lock it in.
2. **Two tags per spool**, one on each side. The user fills details once
   then writes **two** tags end-to-end. That's why `lot_nr` is a
   comma-separated list — both UIDs accumulate on one Spoolman spool.
3. **Branded pre-printed tags** (e.g. Bambu, Creality, Anycubic, Elegoo,
   Qidi, Snapmaker, plus open formats OpenSpool / TigerTag) ship with
   encoded vendor data. v2 should be able to read them, ideally re-using
   [OpenRFID](https://github.com/suchmememanyskill/OpenRFID) (Python,
   GPL-3.0) format/decoder definitions. Some formats are encrypted
   (Mifare Classic with vendor keys); v2 needs a way for the user to
   provide keys in Settings (we don't ship them).

The flow you described:

> **Read flow**
> 1. User opens app, taps **Read Spool**
> 2. Tag is read (any supported format — Bambu, OpenSpool, …)
> 3. App matches the decoded data to a Spoolman spool by tag UID
> 4. If not found, app offers to create the Filament profile + Spool
> 5. App prompts the user to **scan the second tag** on the other side
>    of the spool, attaches its UID to the same `lot_nr`
>
> **Create flow** (blank tag or OpenSpool tag, i.e., write-capable)
> 1. App offers to create a new tag/spool
> 2. User picks an existing Spoolman filament from dropdown OR types
>    own details (same as v1)
> 3. App saves: writes the OpenSpool payload to the tag AND creates the
>    Spoolman spool with `lot_nr = card_uid:<uid-of-tag-1>`
> 4. App prompts the user to scan and write the second tag on the
>    other side — that UID appended to the same `lot_nr`

---

## Question 1 — UID lookup strategy (revisited)

Given uncertainty about Spoolman's server-side filter:

A) **Client-side scan, primary** — fetch all spools (paginated, cached),
   search `lot_nr` locally for the UID. Simple, always works, slow only on
   very large inventories.
B) **Server-side filter, primary** — assume Spoolman supports
   `?lot_nr=…` substring; document the requirement; fail loudly if it
   doesn't.
C) **Try server-side, fall back to client-side scan** — best UX, more code
D) **Do more research first** — I'll fetch Spoolman's OpenAPI spec from a
   live instance / source code and report back before we decide
X) Other (please describe)

[Answer]:D

---

## Question 2 — Two-tags-per-spool flow (NEW REQUIREMENT)

Confirm the desired behavior:

A) **Mandatory two-tag flow** — every Read or Create completes with a
   prompt "Scan the second tag." The user can dismiss/skip but the
   default is two tags, two UIDs in `lot_nr`
B) **Optional two-tag flow** — after the first tag is paired, app shows a
   "Pair another tag with this spool?" button. Skipping is the same as
   confirming
C) **Always offer, never auto-prompt** — users initiate the second pairing
   manually from the same screen by tapping Read/Write again
X) Other (please describe)

[Answer]: B

---

## Question 2A — When the second-tag flow is interrupted

The user pairs tag 1 successfully but then walks away / app crashes / phone
runs out of battery. On next launch:

A) **No persistence** — second tag is forgotten; user can pair it later by
   simply scanning it (the system handles it via Q1 lookup)
B) **Resume prompt** — app remembers "spool X is mid-pairing, second tag
   pending" and offers to resume next launch
C) **Don't care for v2** — A is fine
X) Other (please describe)

[Answer]: C

---

## Question 3 — Multi-vendor tag READ scope

Which tag formats does v2 read?

A) **OpenSpool only** — same as v1; defer multi-vendor to v3
B) **OpenSpool + plain UID for any tag** — for non-OpenSpool tags, just
   capture the UID, ignore the encoded payload (simplest path that still
   handles Bambu/Creality/etc. for the **mapping** purpose, even if we
   don't decode their proprietary fields)
C) **OpenSpool + multi-vendor decode** — port OpenRFID's parsers for
   Bambu / Creality / Anycubic / Elegoo / Qidi / Snapmaker / TigerTag
   and decode material/color/brand/temps from each format. Pre-fill the
   Create-Spool form from decoded data
D) **Same as C, plus Settings UI for keys** — for vendor formats that
   require Mifare Classic keys (Bambu primarily), Settings exposes a
   key store; without keys, those formats fall back to "UID only"
X) Other (please describe)

[Answer]: D 

---

## Question 3A — OpenRFID licensing

OpenRFID is **GPL-3.0 and Python**. SpoolPainter is Kotlin/Android.

A) **Use as reference only** — re-implement in Kotlin from the OpenRFID
   source as a guide; SpoolPainter stays under its current license; **GPL
   does not apply** because we're not linking the GPL code
B) **Port OpenRFID's parser code directly to Kotlin** — meaning the ported
   code carries OpenRFID's GPL-3.0 obligation; SpoolPainter would need to
   be GPL-3.0 (or a compatible license) end-to-end. Confirm you're OK
   with this
C) **Wait** — we'll ship v2 with OpenSpool-only, and add multi-vendor as
   a v2.x follow-up
X) Other (please describe)

[Answer]: C also B

---

## Question 3B — Where OpenRFID's vendor data lives

OpenRFID's parsers are code (Python). Some vendor knowledge is data
(material codes, color tables, byte offsets, etc.).

A) **Bake the data into the app** — ship a JSON/Kotlin asset of vendor
   format definitions; updates require an app release
B) **Bake + allow user override in Settings** — power users can supply a
   custom JSON to override / add vendors (small power-user feature)
C) **Bake + remote config** — fetch vendor definitions from a curated URL
   on launch, fall back to baked. (Adds a network dep)
X) Other (please describe)

[Answer]: A

---

## Question 4 — Vendor key management (decryption)

For tag formats that need keys (Bambu Mifare Classic etc.):

A) **No keys in v2** — Bambu and similar encrypted formats are
   read-as-UID-only. v2 ships with no key UI; user can pair them and
   v2 still works (just doesn't auto-fill the form from those tags)
B) **Settings UI: per-vendor key list** — user enters Mifare keys in
   Settings; persisted in DataStore (or Room if list-shape). Keys are
   sensitive — store them on EncryptedSharedPreferences or
   DataStore-with-Tink
C) **Settings UI: import a key file** — user imports a JSON/text file of
   keys; same storage rules as B
D) **Both B and C**
X) Other (please describe)

[Answer]: B

---

## Question 5 — Tag-write content for branded tags

If the user pairs a Bambu (or other proprietary) tag whose payload v2
**decoded** but did **not** write:

A) **Never overwrite a non-OpenSpool payload** — preserve the vendor's
   encoded data; only the UID is used for mapping. (Safer; default)
B) **Offer to overwrite** — give the user a checkbox "also write OpenSpool
   payload to this tag" with a warning that it destroys vendor data
C) **Always overwrite** — every paired tag gets v2's OpenSpool payload
X) Other (please describe)

[Answer]: A, very important we will never overite branded tag

---

## Question 6 — Re-pair (move-on-bind) with two-tag spools

If the user re-pairs tag X (currently in `lot_nr` of spool A, possibly
alongside tag Y) to spool B:

A) **Move only X** — remove `card_uid:X` from A; add to B; tag Y stays on
   A. (Each tag is paired independently)
B) **Move all** — assume tags X+Y belong together; move both to B
C) **Ask the user** — if A has more than one UID in `lot_nr`, prompt:
   "Move just this tag, or move all tags from spool A?"
X) Other (please describe)

[Answer]: A

---

## Question 7 — Spoolman now mandatory?

You said the new flow is "Spoolman-centric, if Spoolman is configured."
But several flows write to Spoolman as part of pairing.

A) **Spoolman is required** — first-run shows a "configure your Spoolman
   server" wall; no functionality before that. The app is a Spoolman
   tagger, period.
B) **Spoolman is optional** — without it, the app still reads tags
   (UID + decoded data shown read-only), but cannot pair / create. UI
   surfaces a "connect Spoolman to enable pairing" banner
C) **Spoolman is optional** AND a "raw write" mode lets the user write
   OpenSpool payload to a tag without any Spoolman interaction (i.e.,
   keep v1's untethered write capability as a side mode)
X) Other (please describe)

[Answer]: C

---

## Question 8 — UI shape revisited

Last round Q15/Clarification 7 said "single screen, two buttons (Read /
Write), like v1." But your new Read flow has multiple steps (read → match
→ maybe-create-form → second-tag prompt) and the Create flow does too
(form → save → second-tag prompt).

A) **Stay on one screen; expand vertically** — same screen handles the
   multi-step flow with progressive disclosure
B) **Bottom-sheet steps** — single main screen launches a modal
   bottom sheet for the two-tag-pairing step ("Scan the second tag")
   and any "Create profile?" prompts
C) **Two screens** — Home (Read Spool / Create Spool buttons), then a
   detail screen per flow that handles the multi-step UI
D) **Wizard / step-counter** at the top of one screen ("Step 1 of 2
   — pair second tag")
X) Other (please describe)

[Answer]:B
---

## Question 9 — Naming

You used "SpoolmanTagger" and "SpoolPainter" interchangeably. v2 is:

A) **SpoolPainter** — keep the name; the `com.spoolpainter.app` package id
   makes the rename a Play Store concern anyway
B) **Rename to SpoolmanTagger** in user-visible places (app name, listing,
   etc.); package id stays `com.spoolpainter.app` for the in-place update
C) **SpoolmanTagger powered by SpoolPainter** / dual-name
X) Other (please describe)

[Answer]:A

---

## Question 10 — Scope impact: ship v2 in one wave, or split?

These changes are larger than the v1→v2 plan we previously sized. Last
round Q24=A (one big v2.0 release). Confirm:

A) **Still one v2.0** — ship the full multi-vendor + two-tag + Spoolman-
   centric overhaul together
B) **v2.0 minimal** — pivot + two-tag flow + OpenSpool-only reads.
   **v2.1** adds multi-vendor decoding + key UI
C) **v2.0 minimal** as in B, **v2.1** adds two-tag flow too,
   **v2.2** adds multi-vendor
X) Other (please describe)

[Answer]: B
