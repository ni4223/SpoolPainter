# U16 — Write-fail UX + orphan-cleanup removal + pairing-count (summary)

**Type**: bugfix unit (v2.1.x). Per-unit gate FD / NFR-R / NFR-D / Infra-D SKIP.
**Scope**: UI-42 + UI-43 + UI-44 (UI-45 camera color-picker parked for v2.2+).
**Version**: versionCode 108 → 109, versionName 2.1.4 → 2.1.5.
**Tests**: 499 → **506** (Δ +7). **APK 7.41 MB R8 / AAB 8.19 MB** (v2 signature).
**Install gate PASSED** on-device (moto g stylus 2025 / Android 16).

## What shipped

### UI-43 — Write never deletes a spool (orphan machinery removed entirely)
The orphan chain-delete was a fossil from v1's single-action flow (one tap
created *and* wrote a tag, so a failed write left a dangling spool). The
Save/Write split killed that scenario: **every Write path targets an
already-existing spool** (Spoolman path → `launchCreateAndPair(isNewSpool=false)`;
vendor Write → `canWrite` requires `selectedSpoolId != null` → `existingSpoolPath`).
The `newSpoolPath` orphan branch is unreachable from Write. Removed:
- `MainViewModel.fireOrphanCleanup` + all 5 call sites (NfcFailed, Cancelled,
  vendor SpoolmanFailed/MoveOnBindPartial/Cancelled) + the `= null` clear on
  WrittenAndPaired success. NfcFailed/Cancelled now always pin the spool +
  filament selection so a retry Write appends instead of duplicating.
- `SaveToSpoolmanUseCase.lastResolvedOrphan` field + `ResolvedSpool.Created.orphan`
  payload + its construction (both create branches).
- `VendorUidOnlyPairUseCase.lastResolvedOrphan` field + assignments.
- `SpoolmanRepository.chainDeleteOrphan` + the 3 now-unused private cache
  helpers (`removeSpoolFromCache` / `removeFilamentFromCache` /
  `removeVendorFromCache`).
- `OrphanSpool` data class. The file `OrphanSpool.kt` also held `NewSpoolBundle`
  + `Resolved<T>` (both still needed) — extracted to new `SpoolmanCreateModels.kt`;
  `OrphanSpool.kt` deleted.
- `FakeSpoolmanRepository.chainDeleteOrphan` override + `chainDeleteOrphanCalls`.

The `deleteSpool`/`deleteFilament`/`deleteVendor` **API interface methods**
(`SpoolmanApi`) are kept — generic REST verbs, plausibly reused by the logged
UI-36 archive feature; no unused-code warning (interface members).

### UI-42 — "Too small" write copy acknowledges the mapping succeeded
`CreateAndPairUseCase` step 3 appends the UID to Spoolman **before** deciding
the write outcome, so on an NTAG213-too-small failure the tag IS mapped by
serial even though its OpenSpool payload didn't fit. `NfcAdapterWrapper`
already tags that IOException `"tag too small: …"`, which survives into
`NfcFailed.reason`. Added a snackbar branch (before the generic fallback).
Final copy (user-authored during install-gate iteration):
> "Paired only. This tag is too small to write full data."

### UI-44 — End-of-pairing count reflects actual UIDs in Spoolman
New `MainViewModel.pairedTagCount(spoolId)` reads `extra.card_uids` via
`ExtraCardUidsCodec.decode(...).size` from `spoolman.spools.value` (the repo
StateFlow, updated synchronously by `appendCardUidToSpool` →
`replaceSpoolInCache` right after the PATCH — unlike the async VM mirror).
`pairedMessage(spoolId, written)` builds the whole line: prefix + count clause
(count clause dropped at 0 so we never say "0 tags"). The prefix is
**write-aware** — vendor tags are UID-mapped only (no NDEF payload), so they
must not claim a write:
- written tag → "Tag written and paired. This spool now has N tag(s)."
- vendor tag  → "Vendor tag linked. This spool now has N tag(s)."

Wired into: `onPairAnotherTagDismissed` (`written = !isVendorPair`),
`SecondTagPaired` (`written = true`), vendor two-tag `UidPaired`
(`written = false`). The old "Both tags paired" / "Saved with one tag" strings
are retired in favour of the unified write-aware copy.

## Files
**Prod modified**: `MainViewModel.kt`, `SaveToSpoolmanUseCase.kt`,
`VendorUidOnlyPairUseCase.kt`, `CreateAndPairUseCase.kt` (comment),
`SpoolmanRepository.kt`, `app/build.gradle.kts`.
**Prod created**: `SpoolmanCreateModels.kt`. **Prod deleted**: `OrphanSpool.kt`.
**Test modified**: `MainViewModelTest.kt` (+3: UI-42 too-small, UI-43 fail,
UI-43 cancelled), `MainViewModelTwoTagTest.kt` (+3 UI-44 count cases; "Both
tags paired." period fix), `FakeSpoolmanRepository.kt` (dropped orphan support).

## Build matrix
`compileDebugKotlin` ✅ / `testDebugUnitTest` ✅ **506/506** /
`assembleDebug` ✅ 67.6 MB / `assembleRelease` ✅ **7.41 MB R8** /
`bundleRelease` ✅ **8.19 MB AAB** (v2 signature verified).

## Install gate (on-device) — PASSED
moto g stylus 2025 / Android 16. User verified all three:
1. Save a new spool → Write → fail the write → spool survives + stays selected. ✅
2. NTAG213 (too small) → "Paired only." copy; tag resolves by serial. ✅
3. Pair a tag → snackbar shows correct total count (written + vendor prefixes). ✅
Copy refined on-device across two rounds (too-small + count wording, then the
write-aware written/vendor prefix split).

## Close-out
Close-out commit lands on `v2`. Push / tag / GitHub Release / Play Store upload
are outward-facing release ops — await explicit user go per
[[feedback_aidlc_unit_close_out_commit]].
