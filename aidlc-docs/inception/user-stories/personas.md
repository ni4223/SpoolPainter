# SpoolPainter v2 — Personas

**Source**: `aidlc-docs/inception/plans/story-generation-plan.md` §A.1
**Decisions**: Q1=A,B,C; D excluded; E (two-tag flow) folded into A as
behaviour mode (Q2=B).

---

## P1 — Connected Hobbyist (Casey)

- **Archetype**: 3D-printing hobbyist running a Bambu / Voron / Prusa
  setup, self-hosts Spoolman on the LAN.
- **Environment**: Spoolman reachable at a stable LAN URL over HTTP;
  spools shipped with **blank or OpenSpool-format NFC tags** stuck on
  both sides of the spool body.
- **Goals**:
  - Pair a fresh spool to its Spoolman record in under a minute, ideally
    on first contact with the tag.
  - Re-pair tags pulled from consumed spools without manual cleanup in
    Spoolman.
  - Run the **two-tag-per-spool** flow end-to-end whenever bringing a
    new spool online — write both sides identically and have the
    spool's `lot_nr` carry both UIDs (FR-6 behaviour mode).
- **Frustrations with v1**:
  - v1 writes filament JSON onto the tag but the printer firmware now
    keys off UID, so v1's payload-only mental model is no longer
    sufficient.
  - No way to reuse a tag — moving it to a new spool means manual
    Spoolman edits.
  - Single combined screen makes re-pair confirmations awkward.
- **Success criteria for v2**:
  - Single-screen UX preserved; multi-step flows (re-pair, second tag,
    "Create new spool?") use bottom sheets (FR-13).
  - `lot_nr` ↔ UID mapping is the source of truth; pairing is one tap.
  - Move-on-bind happens automatically with confirmation.
- **Release wave**: **v2.0** (primary persona). Picks up v2.1 vendor
  decoding for free if they ever buy a branded-tag spool.

---

## P2 — Offline Tinkerer (Owen)

- **Archetype**: Hobbyist who runs printers without Spoolman — either
  doesn't want a server, prints from SD, or hasn't set up the LAN
  service.
- **Environment**: No reachable Spoolman; only the device, the app,
  and blank or OpenSpool-format NFC tags.
- **Goals**:
  - Write filament metadata (color, material, temps, brand) to a tag
    so the printer firmware can still read OpenSpool JSON — same
    untethered flow v1 supported.
  - Read existing OpenSpool tags to identify a spool quickly.
- **Frustrations with v1**:
  - No frustrations specific to the offline path — v1 already worked
    here. v2 must NOT regress this.
- **Success criteria for v2**:
  - **Raw write side mode** (FR-4.7) is available without a Spoolman
    URL configured; produces the same OpenSpool NDEF payload as the
    Spoolman path.
  - Reading a tag still surfaces UID + decoded payload even without
    Spoolman.
  - No nag screens about Spoolman; the absence of a configured URL is
    fine, not an error.
- **Release wave**: **v2.0**. v2.1 adds nothing for this persona
  unless they buy a branded-tag spool.

---

## P3 — Branded-Tag Reader (Bea)

- **Archetype**: Hobbyist who buys spools from vendors that ship NFC
  tags pre-encoded by the manufacturer (Bambu, Creality, Anycubic,
  Elegoo, Qidi, Snapmaker, TigerTag).
- **Environment**: Spoolman on LAN (typically). Tags **must not be
  overwritten** — they carry vendor data and (often) chip-level
  authentication.
- **Goals**:
  - **v2.0**: Pair a vendor-branded tag's UID into Spoolman **without
    touching its NDEF payload** — finally enabling spool-usage
    tracking on branded spools too. The mechanism: when the app
    can't read a tag's contents (because it's encoded), it surfaces
    the tag like a blank tag (form is empty, UID is captured); on
    Save/Write, the app shows a bottom-sheet — "This tag is encoded
    and we can't read its contents — but we can still map its UID
    to a Spoolman spool. Would you like to pair the UID only?"; on
    confirm, the app runs the Spoolman pairing chain (PATCH
    `lot_nr` or POST a new spool) **but skips the NDEF write step**
    that the OpenSpool path would do (FR-4.8). (Per Q1 freeform:
    "branded tag reader can finally track usage of spool using
    spoolman".)
  - **v2.1**: Have the app *decode* the vendor's payload and pre-fill
    the form (material, color, temps) before the user commits the
    Spoolman record. For encrypted formats (notably Bambu Mifare
    Classic), supply per-vendor decryption keys in Settings; without
    keys, fall back to UID-only behaviour (FR-9.4 / Q4=B).
- **Frustrations with v1**:
  - v1's write-anything behaviour is dangerous on branded tags; users
    have learned not to point v1 at a Bambu tag.
  - v1 can't help track usage of a branded spool — the user has to
    type everything into Spoolman manually.
- **Success criteria for v2**:
  - **v2.0**: app reads the UID, never writes to a branded tag
    (FR-4.6 strict; FR-14.2 hard rule), surfaces the tag like a blank
    tag, lets the user pick or create a Spoolman spool, and on
    Save/Write asks for explicit "Pair UID only?" confirmation
    before running the Spoolman pairing chain (FR-4.8) — i.e., PATCH
    `lot_nr` on the chosen spool or POST a new spool, with no NDEF
    write. Move-on-bind (FR-5) still applies.
  - **v2.1**: per-vendor decoding fills in the form; key entry UI in
    Settings; keys encrypted at rest (NFR-3.4 Keystore-backed).
- **Release wave**: **v2.0** for UID-only pairing; **v2.1** for
  decode + key UI. (No separate "Vendor-Key Power User" persona —
  vendor-key Settings stories attribute here.)

---

## Persona ↔ Release-wave matrix

| Persona | v2.0 stories | v2.1 stories |
|---|---|---|
| P1 Connected Hobbyist (Casey) | ✅ primary | (incidental — gains decode if they buy a branded spool) |
| P2 Offline Tinkerer (Owen) | ✅ raw-write path | — |
| P3 Branded-Tag Reader (Bea) | ✅ UID-only pair + tag protection | ✅ vendor decode + key UI |

---

## Out-of-scope users
- Multi-printer-farm operators with > 1 Spoolman instance — single-server
  v2 (out of scope, §7 of requirements.md).
- Authenticated Spoolman setups — no auth in v2 (NFR-7.4).
- Non-English speakers — English-only in v2 (NFR-10).
