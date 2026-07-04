# U16 — Write-fail UX + orphan-cleanup removal + pairing-count

**Unit type**: bugfix (v2.1.x patch track)
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP**; Code Generation executes.
**Scope**: UI-42 + UI-43 + UI-44 (UI-45 camera color-picker parked for v2.2+).
**Release window**: v2.1.5 (versionCode 108 → 109, versionName 2.1.4 → 2.1.5).
**Baseline**: 499/499 tests, release APK ~7.43 MB / AAB ~8.20 MB.

---

## 0. Root-cause summary (verified against code, 2026-07-03)

The three items overlap in the two-button Save/Write flow. Verified facts:

- **`CreateAndPairUseCase.kt:69-106`** — on *any* write outcome (Success,
  Verify, **Failed**), if a UID was observed during the tap, step 3 appends it
  to Spoolman's `extra.card_uids` **before** the outcome is translated. So on a
  failed write where the tap still landed (e.g. "tag too small"), the
  spool↔UID mapping is *already committed* — then the use case returns
  `NfcFailed` (`:141`).
- **`NfcAdapterWrapper.kt:117-131`** — the "too small" IOException is
  re-thrown with reason `"tag too small: payload {N}B > capacity {C}B"`.
  `NfcRepository.kt:291` wraps it as `NfcResult.Error("write failed: ${t.message}")`
  → carried verbatim into `CreateAndPairResult.NfcFailed.reason`.
- **`MainViewModel.kt:1126-1164` (`NfcFailed`)** and **`:1165-1196` (`Cancelled`)**
  — read `saveToSpoolman.lastResolvedOrphan` (set during the *prior* deliberate
  Save, never cleared by `applySaveResult`) and, if non-null,
  `fireOrphanCleanup` → `chainDeleteOrphan` → **deletes the spool + filament +
  vendor**. This is the "spool disappears when Write fails" report (UI-43).
- **Write is always `launchCreateAndPair(..., isNewSpool = false)`**
  (`MainViewModel.kt:572`) — the spool was deliberately created by a prior Save.
  The orphan concept is a leftover from the old single-action create+write flow.
- **Vendor path is different** — `VendorUidOnlyPairUseCase.newSpoolPath`
  (`:104-166`) creates a spool then appends the UID in the *same* call; if the
  append fails, `lastResolvedOrphan` stays set (`:149`, cleared only on success
  `:156`). That IS a genuine same-transaction orphan → its cleanup stays.
- **UID-count source (UI-44)** — `SpoolmanRepository.appendCardUidToSpool`
  (`:315-339`) PATCHes then calls `replaceSpoolInCache(o.data)` synchronously,
  so the repo's `spools` StateFlow (`spoolman.spools.value`) reflects the new
  UID immediately. The VM mirror `_state.value.spoolman.spools` updates via an
  async collector (`:244`) and may lag one tick — so count from
  `spoolman.spools.value`, not the VM mirror.

---

## 1. UI-43 — Write never deletes a spool: remove orphan chain-delete entirely

**Principle** (per user, 2026-07-03): Write's only Spoolman job is appending the
tag UID to the selected spool. Creating spools is Save's job; deleting them is
nobody's job during Write. Orphan chain-delete is a fossil from the old v1
single-action flow (one tap created *and* wrote, so a failed write left a
dangling spool). The Save/Write split killed that scenario. **Every Write path
now targets an already-existing spool** — verified:
- Spoolman path → `launchCreateAndPair(isNewSpool = false)` (`:572`), existing
  spool; the orphan it deletes is `saveToSpoolman.lastResolvedOrphan` left over
  from the *prior* Save, never cleared — so it deletes a deliberately-saved spool.
- Vendor path → `canWrite` requires `selectedSpoolId != null` (`:188`) and
  routes to `existingSpoolPath` (`isNewSpool = false`, `:98`), which never sets
  an orphan. The only orphan-setting branch (`newSpoolPath`, `selectedSpoolId
  == null`) is unreachable from Write.

So the cleanup guards nothing reachable. Strip it from **all** Write-triggered
failure paths, not a subset.

**Files**: `app/src/main/java/com/spoolpainter/app/ui/screens/main/MainViewModel.kt`

- [ ] **1.1** `applyWriteResult` → `CreateAndPairResult.NfcFailed` (`~:1126`):
      delete the `orphan != null` chain-delete arm; always pin the
      spool/filament selection (current `else` block `~:1136-1156`).
- [ ] **1.2** `applyWriteResult` → `CreateAndPairResult.Cancelled` (`~:1165`):
      same — drop the chain-delete arm, always pin.
- [ ] **1.3** `applyVendorUidOnlyPairResult` `SpoolmanFailed` / `MoveOnBindPartial`
      / `Cancelled` (`:1449 / :1454 / :1463`): drop the `fireOrphanCleanup(...)`
      calls. These only ever ran against `newSpoolPath` output, which Write
      can't reach. On failure, keep the spool (if one was somehow created it
      stays visible in Spoolman for the user to reuse/delete themselves —
      consistent with "Write never deletes").
- [ ] **1.4** Delete now-dead code: `fireOrphanCleanup` (`:1478`),
      `SaveToSpoolmanUseCase.lastResolvedOrphan` +
      `VendorUidOnlyPairUseCase.lastResolvedOrphan` fields and their assignments,
      `ResolvedSpool.Created.orphan` if it becomes unused, and
      `SpoolmanRepository.chainDeleteOrphan` + `OrphanSpool` if no caller
      remains. **Grep to confirm zero remaining references before deleting each.**
      If `OrphanSpool`/`chainDeleteOrphan` turn out to have any other caller,
      leave the type and note it; do not force the removal.
- [ ] **1.5** `FakeSpoolmanRepository.chainDeleteOrphan` +
      `chainDeleteOrphanCalls` (`:174-179`) — remove with the interface method,
      or keep as a no-op override only if the interface method survives.

**Verify**: `chainDeleteOrphan` has no production caller (grep clean); after any
Write failure the spool survives in Spoolman.

## 2. UI-42 — "Too small" write copy acknowledges the mapping succeeded

**Files**: `MainViewModel.kt`

- [ ] **2.1** In the `NfcFailed` snackbar `when` (`~:1157-1162`), add a branch
      *before* the generic fallback:
      `result.reason.contains("too small", ignoreCase = true)` →
      `"Tag mapped to this spool, but it's too small to store the full data."`
      Order: vendor-tag → too-small → generic "Tag write failed. Try again."
- [ ] **2.2** No period/comma-only per [[feedback_no_em_dash]] — copy already
      complies (no `—`).
- [ ] **2.3** No change needed in `CreateAndPairUseCase` / `NfcAdapterWrapper` —
      the reason string already carries "too small" through to `NfcFailed.reason`.

**Verify**: an NfcFailed with reason containing "tag too small" emits the
mapping-succeeded copy; a generic failure still emits "Tag write failed."

## 3. UI-44 — End-of-pairing count reflects actual UIDs in Spoolman

**Files**: `MainViewModel.kt`

- [ ] **3.1** Add a private helper
      `private fun pairedTagCount(spoolId: Int): Int` that reads
      `spoolman.spools.value.firstOrNull { it.id == spoolId }?.extra?.get("card_uids")`,
      decodes via `ExtraCardUidsCodec.decode(...)`, returns `.size`. Returns 0
      if not found (caller guards).
- [ ] **3.2** Add a copy helper
      `private fun pairedCountSuffix(count: Int): String` →
      `""` if count < 1, else
      `" This spool now has $count ${if (count == 1) "tag" else "tags"}."`
      (leading space so it appends cleanly to an existing sentence).
- [ ] **3.3** `onPairAnotherTagDismissed` (`~:1284`): append
      `pairedCountSuffix(pairedTagCount(current.spoolId))` to both
      "Saved with one tag." and "Vendor tag linked." (`PromptingPairAnother`
      carries `spoolId`).
- [ ] **3.4** `applyTwoTagResult.SecondTagPaired` (`~:1297`): the spool id is
      `_state.value.form.selectedSpoolId` (pinned earlier in the flow). Append
      the suffix to "Both tags paired". Guard null spoolId → no suffix.
- [ ] **3.5** vendor `UidPaired` in the two-tag re-route (`~:1347`) and in
      `applyVendorUidOnlyPairResult.UidPaired`… note: `:1347` goes straight to
      Idle with "Both tags paired." → append suffix using `result.spoolId` if
      available, else `state.form.selectedSpoolId`. `applyVendorUidOnlyPairResult.UidPaired`
      (`:1424`) transitions to `PromptingPairAnother` (no terminal count
      snackbar) — leave it; the count appears when that sheet is dismissed
      (covered by 3.3).
- [ ] **3.6** Source the count from `spoolman.spools.value` (repo StateFlow,
      synchronously current after the PATCH), **not** `_state.value.spoolman.spools`
      (async VM mirror, may lag one tick).

**Verify**: with a fake spool holding 2 card_uids, the dismiss/second-tag
snackbars include "This spool now has 2 tags."; a spool with 1 says "1 tag".

## 4. Tests

**Files**: `MainViewModelTest.kt`, `MainViewModelTwoTagTest.kt`,
`MainViewModelSaveTapTest.kt` (+ existing `FakeSpoolmanRepository` support).

- [ ] **4.1** UI-43: new test — Save creates a spool, then a Write returns
      `NfcFailed`; assert the spool survives (no delete) AND the spool selection
      stays pinned (`form.selectedSpoolId`). Add the mirror case for `Cancelled`.
      If `chainDeleteOrphanCalls` is removed with the method, drop assertions
      against it; otherwise assert it stays empty.
- [ ] **4.2** Delete/retire any existing test that asserted orphan
      chain-delete *fires* on a failure path (it now must not). Grep the test
      tree for `chainDeleteOrphan` and update every reference.
- [ ] **4.3** UI-42: `NfcFailed` with reason `"write failed: tag too small:
      payload 216B > capacity 144B"` emits the mapping-succeeded copy; a plain
      `"write failed: ..."` still emits "Tag write failed."
- [ ] **4.4** UI-44: seed `FakeSpoolmanRepository` spools so the paired spool
      has N card_uids; assert `onPairAnotherTagDismissed` and `SecondTagPaired`
      snackbars contain the correct "N tags" / "1 tag" suffix.
- [ ] **4.5** Update the two existing assertions that pin exact strings:
      `MainViewModelTwoTagTest.kt:163` ("Saved with one tag.") and `:182`
      ("Both tags paired") now carry a count suffix → switch to
      `.startsWith(...)` / `.contains(...)` or seed a known count and assert the
      full new string. `MainViewModelTest.kt:513/556` ("Tag write failed")
      already use `.contains` — verify still green.

**Test target**: ~499 → ~505 (Δ +6: 2 UI-43 + 1 UI-43-guard + 1 UI-42 + 2 UI-44),
minus any collapsed assertions. Net reported after Part 2.

## 5. Build matrix (Part 2 close)

- [ ] **5.1** `./gradlew compileDebugKotlin`
- [ ] **5.2** `./gradlew testDebugUnitTest` — all green
- [ ] **5.3** `./gradlew assembleDebug`
- [ ] **5.4** `./gradlew assembleRelease` (R8) + `bundleRelease`
- [ ] **5.5** versionCode 108 → 109, versionName 2.1.4 → 2.1.5 in
      `app/build.gradle.kts`.

## 6. Install gate (on-device, moto g stylus 2025 / Android 16)

Per Q-T2=B convention, manual verification is organic during iteration:
- [ ] **6.1** Save a new spool, tap Write, **fail the write** (yank the phone /
      NTAG213 too-small tag) → spool must **stay** in Spoolman; retry Write
      appends rather than duplicating.
- [ ] **6.2** NTAG213 (too-small) tap → mapping-succeeded copy appears; verify
      the spool resolves by serial in Spoolman.
- [ ] **6.3** Pair a second tag → snackbar shows the correct total count.

## 7. Non-goals / carry

- UI-45 (camera color-picker) — v2.2+ feasibility spike, not this unit.
- Orphan chain-delete is removed as dead code (Write never creates or deletes a
  spool now). If a stray spool is ever created, it stays in Spoolman for the
  user to manage — consistent with "Write only maps a UID."

## 8. Resume options after approval

- **A** — Execute Part 2 now (§1-§4 edits + tests + build matrix), report
  results, then close-out commit on user go per [[feedback_aidlc_unit_close_out_commit]].
- **B** — Request changes to this plan first.
