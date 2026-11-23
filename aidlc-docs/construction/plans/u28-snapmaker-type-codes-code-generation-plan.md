# U28 — Code Generation Plan

**Unit**: U28 — Snapmaker tags with an unknown type code fail the whole read (UI-70)
**Opened**: 2026-11-22 (planning only, Part 1). **Part 2 done 2026-11-22**
(plan approved; build matrix green). On-device S10 at the combined install gate.
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP**. One parser file plus
tests. Design decided by the maintainer 2026-11-22 in `ui-followups.md` UI-70. No
new components, no data model, no infrastructure. Same convention as U20-U27.
**Source**: GitHub #10, external PR #13 (studiofuga).
**This plan is the single source of truth for U28 Code Generation.**

---

## §1 Scope

- **F1**: land PR #13 (sub types 9-16) and add sub types 17 Starlight and 18 Aero.
- **F2**: add the missing main types from Snapmaker's firmware.
- **F3**: fail soft on unknown codes once the signature verifies.
- **F4**: the "Hold still" copy, error tests, and a source-of-truth comment.

Code location: `app/src/main/java/com/spoolpainter/app/hardware/nfc/SnapmakerFormat.kt`
(tables at `:38-42`, lookups at `:250-254`). Tests under
`app/src/test/java/com/spoolpainter/app/hardware/nfc/`.

## §2 Verified, not assumed (2026-11-22)

- `parseSnapmakerTag` returns null on an unknown main or sub type **after** the
  RSA signature passes (`SnapmakerFormat.kt:250-254`). `TagFormatParser` then
  returns null and the user sees "Couldn't read the tag. Hold still and press Read
  again." (`MainViewModel.kt:1342-1345`).
- Firmware table read from Snapmaker/u1-klipper
  `klippy/extras/filament_protocol.py`. PR #13's codes 9-16 match it exactly.
  17 = Starlight, 18 = Aero.
- **Unknown material names already work.** `FormMapping.fromOpenSpool`
  (`FormMapping.kt:111-122`) calls `MaterialPresetSource.lookup(payload.type)` and,
  when it misses, `synthesiseMaterial(name = payload.type, ...)` with the tag's own
  temps. So a new main type such as "PA-CF" does not need mapping onto the app's
  material list (PLA ABS PETG TPU ASA PC Nylon PVA HIPS Other). To confirm in S3:
  that the Snapmaker `parsedHint` reaches this same function.
- `payload.subtype` becomes the variant unless it is "Basic" or blank
  (`FormMapping.kt:126`).
- No test covers the type tables. Decoding needs a validly signed tag, so a real
  fixture cannot be edited to carry a new code.

## §3 Decisions (all DECIDED 2026-11-22 by the maintainer, the recommendations)

- **D1 — unknown sub type → blank variant** (same as "Basic"). The raw code is
  recorded in the read diagnostics so "Share last NFC scan" carries it.
- **D2 — unknown main type → Material empty**, everything else kept from the tag
  (colour, temps, weight, vendor), and the read shows "Unknown Snapmaker material
  (code N). Pick the material."
- **D3 — PR #13 is retargeted to `v2` and merged on GitHub**, so studiofuga keeps
  authorship; U28 commits go on top. **GitHub action, done only at commit time
  with the maintainer's OK.** Until then its 3 lines are applied to the working
  tree, uncommitted (identical hunk, so it reconciles cleanly when the merge is
  pulled).
- **PR #14 order — U28 first.** PR #14 (U30) is rebased over U28 when U30 runs.
  Its `SnapmakerFormat.kt` change is 2 lines (`sku` on the returned payload),
  which move into `decodeSnapmakerPayload`.

### Re-verified 2026-11-22 (session 2)

- Lookups that return null after the signature check: `SnapmakerFormat.kt:251-254`.
  Null → "Couldn't read the tag. Hold still…" at `MainViewModel.kt:1358-1361`.
- A Snapmaker read reaches `FormMapping.fromOpenSpool` (`MainViewModel.kt:1322`),
  so new main type names synthesise a material (S3 confirmation done).
- PR #13 head still `e8725fa` (studiofuga); PR #14 head `774c18f` (kpaaro, base
  `v2`, 31 files).
- Type codes sit at bytes 66 and 68, inside the first 256 bytes, but the read
  log records "uid only", not raw bytes (`NfcRepository.kt:221-231`). So D1 needs
  an explicit diagnostic.

### Implementation design

- `OpenSpoolPayload` gets two read-side-only fields (never encoded, like
  `lotNr`): `decodeNotes: List<String>` (diagnostics, e.g. "Snapmaker sub type
  19 unknown") and `readNotice: String?` (one user-facing line shown after the
  read).
- D1: unknown sub type → `subtype = "Basic"` + a note.
- D2: unknown main type → `type = ""` + a note + `readNotice`.
  `FormMapping.fromOpenSpool` maps a blank type to `material = null`, keeping
  the tag's temps.
- `NfcRepository`'s read log appends `decodeNotes` to the outcome line; the VM
  shows `readNotice` as the read snackbar.

## §4 Steps

- [x] **S0** — Prerequisite: Android SDK installed on this Mac (none found
  2026-11-22: `local.properties` points at a missing
  `/opt/homebrew/share/android-commandlinetools`). Baseline: full unit suite green
  at **643** before any change.
- [x] **S1** — Apply PR #13's 3 lines (sub types 9-16) to the working tree,
  uncommitted; the GitHub retarget + merge (D3) happens at commit time with
  approval. PR #14 waits for U30 and is rebased over U28 then (merge order
  decided 2026-11-22).
- [x] **S2** — Add `17 to "Starlight", 18 to "Aero"` to `SUB_TYPES`.
- [x] **S3** — Add the missing main types with the firmware's exact names: ASA 6,
  PA 9, PA-CF 10, PA-GF 11, PC 12, PETG-CF 17, PLA-CF 20, PEBA 22, TPE 23,
  PAHT-CF 24, PA6-CF 25, PA6-GF 26, ASA-CF 27. Confirm the vendor `parsedHint`
  path goes through `FormMapping.fromOpenSpool` so these synthesise a material.
- [x] **S4** — Split `parseSnapmakerTag` into the signature check plus an
  `internal fun decodeSnapmakerPayload(data)`. No behaviour change. This makes
  the decode testable without a signed tag.
- [x] **S5** — Fail soft per D1 and D2: after the signature verifies, an unknown
  code no longer returns null.
- [x] **S6** — Copy. Confirm that after S5 a signature-verified Snapmaker tag can
  no longer reach the "Hold still" branch. If a decode-only failure path remains,
  give it its own message instead of blaming the tap.
- [x] **S7** — Comment on the tables pointing at the firmware file, so the next
  edit is checked against the source.
- [x] **S8** — Tests:
  - table test pinned to the firmware list (all 18 sub types, all main types);
  - decode tests on synthetic buffers via `decodeSnapmakerPayload`: 12 →
    Translucent, 17 → Starlight, 18 → Aero, unknown sub type → D1, unknown main
    type → D2;
  - the 16 real fixtures still pass (`VendorFixtureParseTest`,
    `SnapmakerNotBambuRegressionTest`);
  - non-vacuity: revert S5 alone and confirm the unknown-code tests fail.
- [x] **S9** — Build matrix: `compileDebugKotlin`, `testDebugUnitTest`,
  `assembleDebug`.
- [x] **S10** — **Combined install gate after U28** (maintainer, 2026-11-22).
  Install gate on device: a known Snapmaker tag still reads. The
  maintainer has no PETG Translucent spool, so ask the #10 reporter to confirm on
  the next test build.
- [x] **S11** — Docs: release notes, UI-70 state, `aidlc-state.md`.
- [x] **S12** — GitHub, 2026-11-22: PR #13 retargeted to `v2` and merged (merge commit `f7233ca`, studiofuga's `e8725fa` kept); replied on #10 (short version, approved; issue left open until the reporter confirms on 2.4.2).

## §4a Implementation record (2026-11-22)

- **Firmware re-read** at the source (Snapmaker/u1-klipper `main`, file last
  changed `7ac453b` 2026-11-04): the main and sub type tables match §2 exactly.
  The firmware **rejects** unknown codes (`fp.py:391-402`), so D1/D2 make the
  app deliberately more lenient than the printer.
- **S0**: baseline 676 green (643 + U33 + U32).
- **S1**: PR #13's hunk (`e8725fa`) applied to the working tree, uncommitted.
- **S2/S3**: 17 Starlight, 18 Aero; the 13 missing main types. Main type 0
  ("Reserved" in the firmware) is left out on purpose so it reads as unknown.
  The tables are now `internal` so the test can pin them.
- **S4/S5**: `parseSnapmakerTag` = size + signature check, then
  `internal fun decodeSnapmakerPayload(data)`, which never returns null. Unknown
  sub type → `"Basic"` (blank variant) + a note (D1). Unknown main type →
  `type = ""` + a note + `readNotice` (D2).
  `OpenSpoolPayload.decodeNotes` / `readNotice` are read-side only (the codec
  never encodes them). `FormMapping.fromOpenSpool` maps a blank type to
  `material = null`, keeping the tag's temps; Save then says "Pick a material."
  The VM shows `readNotice` after the read; the read log appends the notes to
  the outcome line.
- **S6**: after S5, `parseSnapmakerTag` returns null only for a wrong size or a
  failed signature (`SnapmakerFormat.kt:261-262`), which is what "Hold still and
  press Read again" is for. No decode-only failure remains, so no new copy.
- **S7**: source comment above the tables.
- **S8**: `SnapmakerTypeCodesTest` (8): tables pinned to the firmware; decoding
  past the RSA check on a **real** dump with its type bytes rewritten (12, 17,
  18, 10, 27, unknown 19 / 30, Reserved 0); the untouched dump decodes the same
  as the signed parse; a rewritten dump still fails the signed parse. The real
  Snapmaker fixtures still pass. **Non-vacuity**: with S5 reverted, the 3
  unknown-code tests fail.
- **S9**: `compileDebugKotlin testDebugUnitTest assembleDebug` green, **684**.

### Draft reply to #10 (not posted; needs approval)

> Thanks for the report! The new PETG Translucent spool uses a sub type code
> (12) the app didn't know yet, so the whole read was thrown away and the
> message wrongly blamed the tap. The next test build knows all of Snapmaker's
> current type codes (thanks to @studiofuga's PR #13 for 9-16), and a tag with
> a code we don't know yet will still read. Could you try it on that spool
> when it's out?

## §5 Release

**Version: 2.4.2 / versionCode 117** (decided 2026-11-22). The next Play upload needs **versionCode 117 or higher**
(116 is consumed, see `aidlc-state.md` 2026-08-28).
