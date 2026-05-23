# SpoolPainter v2 — Requirements Verification Questions

**Context (my current understanding — please correct via Q1 if wrong):**
v2 is a major behavioral pivot. v1 wrote rich filament JSON (color/material/temps)
to NFC tags so printer firmware could read it. **In v2, printer firmware uses the
NFC tag's hardware UID instead** — and the mapping between a tag UID and a
Spoolman spool is stored in a Spoolman field (proposed: `lot_nr`). The app's job
is to make that mapping fast.

**v2 user flows (my reading):**
- **Read-then-map**: tap tag → app captures UID → user either (a) picks an
  existing Spoolman spool from the dropdown → app PATCHes that spool's
  `lot_nr` with the UID, or (b) enters spool details → app POSTs a new
  Spoolman spool with `lot_nr` = UID.
- **Write**: (a) user picks a Spoolman spool → app writes the UID into
  that spool's `lot_nr`, or (b) user enters spool details → app creates the
  Spoolman spool with `lot_nr` = UID.

Please answer each question by filling in a letter after `[Answer]:`. Tell me
when you're done.

---

## Question 1 — Is my understanding of v2 correct?

A) Yes, exactly as described above
B) Mostly — minor corrections (describe in [Answer])
C) No — significantly different (describe in [Answer])

[Answer]:
B , this is wrong: Enters spool details → app POSTs a new
  Spoolman spool with `lot_nr` = UID. why wil user eneter the detail tag has the detail already, so we fill that like its in v1 and then we just create the sppol
---

## Question 2 — What is "write the tag" in v2?

In your description, the user "has the option to read or write the tag." Does the
app still write any payload **to the NFC tag itself** in v2 (in addition to the
Spoolman PATCH/POST)?

A) **No NDEF payload** — the tag is just a UID source; v2 never writes data to
   the tag, only reads UID. The Read/Write distinction in the UI is between
   "Read existing tag" vs "Pair a tag with a Spoolman spool"
B) **Yes — still write OpenSpool JSON** (color/material/temps) to the tag for
   backward compatibility with v1 firmware that reads it
C) **Yes — write a minimal marker** (e.g., just `{ "spool_id": <id> }` or just
   the URL of the Spoolman record), not the full filament metadata
D) **Optional** — user toggles in Settings whether to also write OpenSpool JSON
X) Other (please describe after [Answer]: tag below)

[Answer]: B, but writes everything for printer to use as backup except spool_id, this new firmware of printer do not use spook_id at all, maybe we write it too, as it do not matter from firmware side

---

## Question 3 — Tag UID format

`android.nfc.Tag.getId()` returns a `byte[]`. What canonical form should the
app use everywhere (UI display + write to Spoolman `lot_nr`)?

A) **Uppercase hex, no separators** — `04A1B2C3D4E580`
B) **Lowercase hex, no separators** — `04a1b2c3d4e580`
C) **Uppercase hex, colon-separated** — `04:A1:B2:C3:D4:E5:80`
X) Other (please describe after [Answer]: tag below)

[Answer]: Deduce from this  lot_nr Format
The lot_nr field in Spoolman supports multiple card UIDs as comma-separated values:

card_uid:aabbccdd112233,card_uid:001122334455
Each RFID card UID is stored with the prefix card_uid: followed by the hex string of the raw UID bytes.

---

## Question 4 — Which Spoolman field stores the UID?

You proposed `lot_nr`. Spoolman has both a built-in `lot_nr` on the Spool, and
`extra` (custom user-defined fields).

A) **`lot_nr`** — use the built-in field as you proposed
B) **A custom field in `extra`** (e.g., `extra.tag_uid`) — keep `lot_nr` for
   its intended purpose
C) **Configurable in Settings** (user picks `lot_nr` or a custom field name)
X) Other (please describe after [Answer]: tag below)

[Answer]:A consider it lot_nr but maybe some new feild we add in spoolman. Read spoolman documentation to get more context, search it in web

---

## Question 5 — Existing tag data on Read

When the user reads a tag that **already has** an OpenSpool/JSON payload from
v1 (or another tool), v2 should:

A) **Ignore it** — show only the UID; tag payload is irrelevant in v2
B) **Display read-only** — show "this tag has data: PLA red, brand X" so the
   user can confirm the right tag, but UID is what gets used for mapping
C) **Display + pre-fill** — populate the new-spool form with the existing data
   so creating a Spoolman spool is one tap
X) Other (please describe after [Answer]: tag below)

[Answer]: C

---

## Question 6 — Re-pairing a tag

What if the user reads a tag whose UID is **already in `lot_nr`** of a Spoolman
spool (i.e., already paired)?

A) **Auto-show that spool** — read flow ends "this tag is already paired with
   spool X — open it / unpair?"
B) **Allow re-pair to a different spool** — replacing the previous mapping
   silently (last-write-wins)
C) **Allow re-pair with a confirmation prompt** — "This tag is paired with
   spool X. Move it to spool Y?"
X) Other (please describe after [Answer]: tag below)

[Answer]: C, but how will we know where that uid belongs to, so cant say spool x, more like ask them they want to rewrite t.

---

## Question 7 — Spool-creation scope (when user enters details)

When v2 creates a new Spoolman spool, what does it create?

A) **Spool only** — user picks an existing Filament from the Spoolman
   filament catalogue (so the app needs `GET /api/v1/filament`); fail if none
   matches
B) **Spool + auto-create Filament if missing** — user enters material / brand
   / color; if no matching Filament exists in Spoolman, app POSTs a new
   Filament first
C) **Always create a new Filament alongside the Spool** — simpler model, but
   creates lots of Filament rows
X) Other (please describe after [Answer]: tag below)

[Answer]:B, also brand if that is missing, and also on drop down we show user brand list from their spoolman too, also UX of what will be shown will be decided late

---

## Question 8 — Required fields when creating a Spoolman spool from the app

For new-spool creation, which fields does the app collect from the user?

A) **Minimal** — material + color only (everything else from defaults / blank)
B) **Standard** — material, brand, color, variant/subtype
C) **Full** — material, brand, color, variant, extruder & bed temps, weight,
   location
X) Other (please describe after [Answer]: tag below)

[Answer]:B, similar to v1

---

## Question 9 — Spoolman authentication

v2 now writes to Spoolman (PATCH spool, POST spool, possibly POST filament).
What auth model should v2 support?

A) **None** — same as v1; assume Spoolman has no auth (LAN deployment)
B) **Optional API key / bearer token** in Settings
C) **Optional basic auth** (username + password) in Settings
D) **API key + basic auth — both supported**
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Question 10 — Spoolman version target

The newer Spoolman versions support tag-related features natively. v2 should
target:

A) **Latest stable Spoolman** — assume current API
B) **Latest stable + a minimum version pinned in Settings README**
C) **Pin a specific minimum version** (specify in [Answer])
X) Other (please describe after [Answer]: tag below)

[Answer]:A

---

## Question 11 — Network errors / write failures

When a Spoolman PATCH/POST fails (server down, validation error, conflict):

A) **Show a clear error and let the user retry** — no partial success
B) **Best-effort + write tag anyway** (if Q2 says we still write the tag)
C) **Queue the operation locally** and retry when connectivity returns
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Question 12 — Offline behavior

If Spoolman is unreachable, v2 should:

A) **Disable mapping flows entirely** — show "Spoolman required" banner
B) **Allow read-only tag scanning** (display UID + cached spool list) but
   block PATCH/POST
C) **Queue writes for later** (per Q11C)
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 13 — Tag verification

For NFC operations:

A) **Read-only** — never write to tag; per-Q2A model
B) **Write + read-back verify** — when we write to a tag, re-read and compare
C) **Write + verify + retry once** on mismatch
X) Other (please describe after [Answer]: tag below)

[Answer]:B

---

## Question 14 — Material & brand presets in v2

v1 hard-codes 10 materials and 12 brands. For v2's spool-creation form:

A) **Drop hard-coded presets** — use Spoolman's Filament list as the source
   of truth (no offline presets needed since we always need Spoolman)
B) **Keep hard-coded presets** as a fallback when Spoolman is unreachable
C) **User-extensible presets** stored in DataStore
X) Other (please describe after [Answer]: tag below)

[Answer]:X, we keep presets as we have, so its easier to user to select when writing tag, also we pull the brad from user spoolman and add to list or replace our hardcoded prests with their

---

## Question 15 — UI shape for v2

Given the new flow-centric model, what should v2's main UI look like?

A) **Single screen, mode toggle** — Read / Write toggle at top, form below
   (similar to v1)
B) **Two main screens, bottom-tab nav** — "Read & Map" tab, "Write & Pair"
   tab, plus Settings
C) **Three flows from a home screen** — "Scan to Map", "Pair Existing Spool",
   "Create New Spool", each its own screen
D) **Wizard-style** — start with tap-to-scan, app picks the right next screen
   based on tag state (UID present? data present? already paired?)
X) Other (please describe after [Answer]: tag below)

[Answer]: X lets keep it similar to what we have 1 screen with both read anr write button

---

## Question 16 — Theming

A) **Dark mode (system-following)** only
B) **Dark mode + Android 12+ dynamic color (Material You)**
C) **Both, plus user override** in Settings (system / light / dark)
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 17 — Persistence

v2 settings/local state should use:

A) **`SharedPreferences`** — same as v1
B) **Jetpack DataStore (Preferences)** — modern, async
C) **DataStore + Room** if there's any list-shape local state (e.g., recent
   pairings, queued writes)
X) Other (please describe after [Answer]: tag below)

[Answer]: C

---

## Question 18 — Architecture target

A) **Single ViewModel + StateFlow<UiState>** (refine v1)
B) **Per-screen ViewModels + StateFlow + Repository layer**
   (SpoolmanRepository, NfcRepository, SettingsRepository)
C) **MVI / unidirectional data flow** with explicit events and reducers
X) Other (please describe after [Answer]: tag below)

[Answer]:Whichever is best, lets discuss more

---

## Question 19 — Dependency injection

A) **None** — manual construction
B) **Hilt**
C) **Koin**
X) Other (please describe after [Answer]: tag below)

[Answer]: lets discuss idk

---

## Question 20 — Min/Target SDK

v1 is `minSdk 29`, `targetSdk 36`.

A) **Keep `minSdk 29`, `targetSdk 36`**
B) **Bump** (specify in [Answer])
C) **Lower** (specify in [Answer])
X) Other (please describe after [Answer]: tag below)

[Answer]:A

---

## Question 21 — Distribution

A) **Sideload only** — same as v1
B) **Sideload + Google Play Store** in this release cycle
C) **Sideload + GitHub Releases (with auto-update check)**
X) Other (please describe after [Answer]: tag below)

[Answer]: B

---

## Question 22 — Testing strategy

v1 has zero tests. v2 minimum bar:

A) **Unit tests for domain models + Spoolman client** (mappers, JSON, paging,
   PATCH/POST request shapes)
B) **Above + ViewModel tests** with a fake Spoolman repository
C) **Above + Compose UI tests** for Read-and-Map / Write-and-Pair flows
D) **Above + at least one instrumented NFC test** (manual fixture acceptable)
X) Other (please describe after [Answer]: tag below)

[Answer]:A

---

## Question 23 — Migration & first-run

Since v2 ships under the same package id as v1.7 (in-place update):

A) **Pure in-place** — no migration; tags + Spoolman are the only state
B) **Clear `SharedPreferences` on first run** (start clean)
C) **Migrate `SharedPreferences` → DataStore** preserving Spoolman URL/sort
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Question 24 — Timeline / shape of work

A) **One big v2.0 release** — full overhaul shipped in one wave
B) **Incremental** — v1.7 → 1.8 → 1.9 → … each shipping a slice
C) **Branch & rebuild** in the `.debug` variant; flip when ready
X) Other (please describe after [Answer]: tag below)

[Answer]: A

---

## Question 25 — Security extension (AIDLC opt-in)

Should security extension rules be enforced for this project?

A) Yes — enforce all SECURITY rules as blocking constraints (recommended for
   production-grade applications)
B) No — skip all SECURITY rules (suitable for PoCs, prototypes, and
   experimental projects)
X) Other (please describe after [Answer]: tag below)

[Answer]:B

---

## Question 26 — Property-based testing extension (AIDLC opt-in)

Should property-based testing (PBT) rules be enforced for this project?

A) Yes — enforce all PBT rules as blocking constraints (recommended for
   projects with business logic, data transformations, serialization, or
   stateful components)
B) Partial — enforce PBT rules only for pure functions and serialization
   round-trips (suitable for projects with limited algorithmic complexity)
C) No — skip all PBT rules (suitable for simple CRUD applications, UI-only
   projects, or thin integration layers with no significant business logic)
X) Other (please describe after [Answer]: tag below)

[Answer]: C
