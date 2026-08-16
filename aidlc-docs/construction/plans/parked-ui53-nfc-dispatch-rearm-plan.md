# U22 — NFC dispatch re-arm reliability + stale-form refresh

**Unit type**: bugfix unit
**Per-unit gate**: Functional Design / NFR Requirements / NFR Design /
Infrastructure Design **SKIP** — no new components, no new data model, no
infrastructure. Design folded into this Code Generation plan (same shape as
U14c / U16 / U19).
**Opened**: 2026-08-15 (session resume, "aidlc continue")
**Baseline**: versionCode 113 / versionName 2.3.1; tests **565 / 565 ✅**
(verified this session, 0 failures, 69 test classes).
**Status**: Code Gen Part 1 — **awaiting stage-gate approval**.

---

## §0 Why this unit, and why now

v2.3.0 went to the Open testing track and a tester ran a deliberate n=10 tag
round-trip pass against Spoolman. The single highest-severity thing that came
back is **UI-53: the app stops seeing tags after 1-2 reads/writes until the app
is closed and reopened.** That breaks the core loop of the product, is trivially
repeatable for the reporter (5-6 times), and there is currently no in-app
recovery — the user has to kill the app.

UI-53 is therefore the whole reason this unit exists. UI-54 is bundled because
it is small, already fully root-caused, and lives in the same "state that should
have refreshed but didn't" family.

**Deliberately NOT in scope**: UI-50 (multi-color hex) and UI-55 (colorless /
transparent). Those two are one feature — a change to how many colors the form
can model — and they need their own unit with real design work (Spoolman
`multi_color_hexes` wire field, `ColorPicker` multi-entry UI, OpenSpool
`additional_color_hexes` on the tag). Folding a data-model feature into a
reliability bugfix would delay the fix that testers are actually blocked on.

---

## §1 Scope

### F1 — UI-53: foreground dispatch can be permanently lost (HIGH)

**Symptom** (tester, v2.3.0 Open testing): after 1-2 successful reads/writes the
app no longer reacts to a tag on the coil. The **phone still vibrates**, so the
OS NFC stack sees the tag; only the app is deaf. Closing and reopening the app
restores it, every time.

**Two code-visible defects explain exactly that symptom.** Neither needed an
on-device repro to find; both were traced this session.

#### Defect 1a — `attach()` latches `attached` even when arming silently failed

`NfcRepository.attach` (`NfcRepository.kt:61-66`):

```kotlin
fun attach(activity: ComponentActivity) {
    if (attached === activity) return        // <-- the trap
    attached?.let { wrapper.disableForegroundDispatch(it) }
    attached = activity                      // <-- set unconditionally
    wrapper.enableForegroundDispatch(activity)
}
```

and `NfcAdapterWrapper.enableForegroundDispatch` (`NfcAdapterWrapper.kt:26-38`)
**silently no-ops** in two cases:

```kotlin
val nfc = adapter ?: return
if (!nfc.isEnabled) return
```

So if `enableForegroundDispatch` is reached at a moment when the NFC adapter
reports `!isEnabled` — which the Android NFC stack does transiently, including
while it recovers after tag I/O errors and around adapter resets — then:

1. dispatch is **not** armed, but
2. `attached` is already set to this activity, so
3. every subsequent `attach(sameActivity)` hits `if (attached === activity)
   return` and **never tries to arm again**.

Dispatch stays dead for the entire lifetime of that activity instance. A new
activity instance (app reopened) takes the `attached !== activity` path and arms
correctly. **That is precisely the reported "reopening fixes it every time."**

The same silent-no-op applies to `disableForegroundDispatch`, so `attached` can
also drift out of sync with the real adapter state in the other direction.

#### Defect 1b — no manifest NFC intent filters, so a lost dispatch is total silence

`app/src/main/AndroidManifest.xml` declares only `MAIN` / `LAUNCHER` on
`MainActivity` — there is **no** `ACTION_NDEF_DISCOVERED` /
`ACTION_TECH_DISCOVERED` / `ACTION_TAG_DISCOVERED` filter anywhere. The app is
100% dependent on `enableForegroundDispatch` being armed. There is no fallback
path: when dispatch drops, the OS has nowhere to route the tag inside this app,
so the user gets the OS-level buzz and nothing else. `MainActivity` already has
`tryDispatchNfcIntent` wired from both `onCreate` and `onNewIntent`, so the
receiving half of a manifest-filter fallback is **already written** — only the
manifest declaration is missing.

#### Fix direction (F1)

- **F1.1 (core, safe)** — make `attach()` idempotent: drop the
  `attached === activity` early-return and always (re-)arm. Re-arming an
  already-armed dispatch is cheap and safe on Android; the adapter replaces the
  registration. This alone removes the permanent-deafness trap.
- **F1.2 (core, safe)** — have `NfcAdapterWrapper.enableForegroundDispatch`
  return `Boolean` (armed / not armed) and only record `attached` when arming
  actually happened, so state can never claim "armed" when it isn't.
- **F1.3 (core, safe)** — guard `attach()` / `detach()` against
  `IllegalStateException` from the platform (`enableForegroundDispatch` throws
  if the activity is not resumed) so a lifecycle race degrades to "not armed,
  will retry next resume" instead of a crash.
- **F1.4 (belt-and-braces, behaviour change — see Q-U22-2)** — add manifest
  NFC intent filters as a fallback route, so even a dropped foreground dispatch
  still reaches `tryDispatchNfcIntent`.
- **F1.5 (diagnosis, keep permanently)** — `Log.d` markers on every
  attach/detach with the outcome (armed / adapter-null / adapter-disabled /
  threw), so the next field report can be settled from a logcat instead of
  theorising. Stripped from release by the existing
  `-assumenosideeffects android.util.Log` ProGuard rule (NFR-5), so this costs
  nothing in the shipped build.

**Honest confidence statement**: 1a + 1b together are a complete and sufficient
explanation of the reported symptom, and the fixes above are safe to ship
without a repro (they only ever *add* arming attempts). They are **not proven**
to be the reporter's actual trigger, because no device is connected this session
and this is a transient-timing bug. F1.5 exists so that if a tester still sees
it on the next build, the logcat answers it immediately. The install gate (§4
S7) must include a deliberate n=10 read/write soak.

### F2 — UI-54: selected spool doesn't re-derive after a Spoolman refresh (MEDIUM)

Already root-caused in `ui-followups.md` (traced 2026-07-30) and re-confirmed
this session:

- `onSpoolSelected` runs `FormMapping.fromSpoolman(...)` **once** and copies into
  `FormState`. The form is a one-time snapshot.
- `onPullToRefresh` → `spoolman.refreshIfStale(force = true)` refreshes the
  spool-list **cache** but never re-projects the fresh entry onto the
  already-selected form.
- Compounded by the same-id early-return in `onSpoolSelected`
  (`if (spool.id == _state.value.form.selectedSpoolId) return`), so re-picking
  the same spool is a no-op — only clearing the selection first works.

**Fix direction (F2)**: after a refresh completes, if a spool is selected,
re-derive the form from the fresh cache entry — **respecting the existing
stale-prefill guard** (`prefilledRemainingWeightG` / `prefilledPriceMajor` /
`prefilledEmptySpoolWeightG`) so a refresh can never clobber edits the user has
in flight. Exact re-derive trigger is Q-U22-3.

### F3 — UI-56 (NEW, found this session): write-tap poisons the ambient buffer

Not previously logged; found while tracing UI-53. In `NfcRepository.handleTag`:

- On a write tap, `isWriting = true`, so `raw` is synthesized with
  **`records = null`** (`NfcRepository.kt:139-144`) — deliberately, to keep the
  on-tag window short.
- `classify(raw)` with `records == null` and an NTAG techList
  (`NfcA, MifareUltralight, Ndef`) falls to the `isNdef -> Blank` branch, so the
  freshly-written tag is classified **`Blank`**.
- `_lastSeenTag.value = TagBuffer(uid, Blank, now)` is set for write taps too
  (`NfcRepository.kt:206`), and unlike the `Reading` branch
  (`NfcRepository.kt:242`) the `Writing` branch **never clears the buffer**.
- So for the next `TTL_MS_DEFAULT = 5_000` ms, a pressed Read calls
  `consumeLastSeen` (which accepts terminal `Success`) and gets back
  `Success(uid, Blank)` — a tag we *just wrote full OpenSpool JSON to*, reported
  as blank, with no physical tap.

**Blast radius is narrower than it first looks**: `ReadAndPairUseCase` then does
`findSpoolsByCardUid(uid)`, and because the write path appends the UID to
Spoolman, that lookup usually returns 1 match and prefills correctly from
Spoolman. The bug only surfaces user-visibly when Spoolman can't answer (URL not
configured, server unreachable, or raw-write mode) — then it lands on
`BlankForm` and the user is told "Blank tag detected." right after a successful
write.

**Fix direction (F3)**: clear `_lastSeenTag` on the write branch the same way
the read branch does (a consumed tap is spent), **or** don't buffer at all when
`isWriting`. Either way a post-write Read requires a real tap and gets a real
classification. Small, self-contained, same file as F1 — cheap to carry here.

---

## §2 File impact (estimate)

| File | Change |
|---|---|
| `hardware/nfc/NfcRepository.kt` | F1.1-F1.3, F1.5 (attach/detach), F3 (buffer clear) |
| `hardware/nfc/NfcAdapterWrapper.kt` | F1.2 (`enableForegroundDispatch: Boolean`), F1.3, F1.5 |
| `ui/activity/MainActivity.kt` | F1.5 log markers only (attach already wired in `onResume`) |
| `AndroidManifest.xml` | F1.4 only, **if** Q-U22-2 = yes |
| `ui/screens/main/MainViewModel.kt` | F2 re-derive after refresh |
| `app/build.gradle.kts` | version bump (Q-U22-4) |

**Tests** (target Δ +14 to +20 over the 565 baseline):

- `NfcRepositoryAttachTest` (new) — re-arm on repeated `attach(sameActivity)`;
  no latch when the adapter reports disabled; recovery on a later attach once
  the adapter is enabled; `IllegalStateException` from the platform is swallowed
  and leaves state un-armed; detach/attach cycle.
- `NfcRepositoryTest` (extend) — F3: write branch clears `_lastSeenTag`, so a
  post-write `consumeLastSeen(Read)` returns null.
- `MainViewModelRefreshTest` (new or extend) — F2: refresh re-derives the
  selected spool's form; in-flight user edits survive per the stale-prefill
  guard; no spool selected = no-op.

`NfcAdapterWrapper` is already `open` with `open fun`s and `NfcRepository` has an
`internal` constructor taking a wrapper, so all of the above is unit-testable
with a fake wrapper — no instrumentation needed.

---

## §3 Invariants this unit must not break

1. **No behaviour change to tag parsing / classification / vendor decode.** F3
   touches only buffer lifetime, not `classify`.
2. **No change to the write payload or MIME** (`application/json`, FR-U6b-Δ-3 —
   Snapmaker U1 firmware filters on it).
3. **The stale-prefill guard stays authoritative** (F2). A background refresh may
   never overwrite a value the user is currently editing.
4. **`detach()` must still not error-out an in-flight Write/Verify** — the
   existing comment at `NfcRepository.kt:72-77` documents why (Android 14+
   singleTop cycles onPause/onResume on every dispatched NFC intent). Adding
   re-arm logic must not reintroduce a spurious "paused mid-write".
5. Release logging stays stripped (NFR-5) — F1.5 uses `Log.d`/`Log.w` only.

---

## §4 Steps

- [ ] **S1** — F1.2: `NfcAdapterWrapper.enableForegroundDispatch` returns
      `Boolean`; add an `ArmOutcome`-style reason for the log line; same
      treatment for `disableForegroundDispatch`.
- [ ] **S2** — F1.1 + F1.3: rewrite `NfcRepository.attach` to always re-arm,
      record `attached` only on a successful arm, and swallow
      `IllegalStateException`. Keep `detach()`'s in-flight-write contract.
- [ ] **S3** — F1.5: log markers on attach/detach outcomes.
- [ ] **S4** — F3: clear `_lastSeenTag` on the write branch of `handleTag`.
- [ ] **S5** — F1.4 (**only if Q-U22-2 = yes**): manifest intent filters +
      verify `tryDispatchNfcIntent` handles the cold-start launch path.
- [ ] **S6** — F2: re-derive the selected spool's form after a refresh,
      honouring the stale-prefill guard.
- [ ] **S7** — tests per §2; full matrix `compileDebugKotlin` +
      `testDebugUnitTest` + `assembleDebug`; `assembleRelease` / `bundleRelease`
      only if this unit ships (Q-U22-4).
- [ ] **S8** — **on-device install gate (needs the phone; no device connected
      as of this session)**: deliberate n=10 read/write soak hunting UI-53;
      NFC-toggle-mid-session recovery check; post-write Read shows real data not
      "Blank" (F3); Spoolman-edit → pull-to-refresh reflects on the selected
      spool (F2).
- [ ] **S9** — docs: `ui-followups.md` (UI-53 / UI-54 → fixed-pending-verify,
      UI-56 logged), `aidlc-state.md` U22 entry, unit summary, audit.
- [ ] **S10** — close-out commit. Push / tag / GitHub Release / Play Store per
      standing direction: **outward-facing release ops wait for explicit go.**

---

## §5 Open questions for Part 2 (Q-U22-*)

**Q-U22-1 — Unit scope.** Ship F1 (UI-53) alone as a fast reliability patch, or
F1 + F2 + F3 together?
Recommended: **F1 + F2 + F3.** F2 is already root-caused, F3 is in the same file
as F1, and all three are things testers can feel.

**Q-U22-2 — Manifest NFC intent filters (F1.4)?** Adding them gives a real
fallback when foreground dispatch is not armed, but it is a **user-visible
behaviour change**: with a filter declared, tapping a tag can offer/launch
SpoolPainter when it isn't in the foreground.
Recommended: **yes, `TECH_DISCOVERED` only** (narrow tech-list, not the greedy
`TAG_DISCOVERED`) — recovers the failure without turning the app into the
default handler for every tag.

**Q-U22-3 — F2 re-derive trigger.** (a) Auto re-derive the selected spool's form
on every completed refresh; (b) only drop the same-id early-return in
`onSpoolSelected` so re-picking the same spool re-derives (user-driven);
(c) both.
Recommended: **(c)** — (a) fixes the reported "pull-to-refresh does nothing",
(b) is a one-line safety valve, and the stale-prefill guard makes (a) safe.

**Q-U22-4 — Version + release.** v2.3.1 / 113 is committed but **never tagged,
never released** (last tag is `v2.3.0`; the Play Store Open testing track is on
2.3.0). So U22 can ride 113 / 2.3.1 the way U14c+U15 rode 2.1.2 and U18 rode
2.2.0, or take a fresh 114 / 2.3.2.
Recommended: **ride 113 / 2.3.1** — nothing public carries 2.3.1 yet, and UI-53
is exactly the kind of fix that should be in testers' first 2.3.1 build.

---

## §6 Housekeeping carried into this unit

Found during state detection this session, independent of F1-F3:

1. **`aidlc-state.md` is stale.** Its `Current Stage` stops at U21 / 112 / 2.3.0.
   Not recorded: the UI-50 Ask 2 variant-cap fix (`0110078`), the v2.3.1 CameraX
   1.4.2 16 KB page-size fix (`2521177`, which bumped 112 → 113 / 2.3.0 → 2.3.1),
   the UI-53/54/55 follow-up log (`cc79049`), and the README/referral/store-track
   doc commits (`435b8c4`, `f197de3`). Sync as part of S9.
2. **Uncommitted docs on `v2`**: `aidlc-docs/play-store-listing.md` has a +4-line
   "OPEN SOURCE" section (GPL-3.0 + GitHub link) and
   `aidlc-docs/reddit-launch-post.md` is untracked. Both are marketing copy that
   prior sessions deliberately left uncommitted; leaving them alone unless told
   otherwise.
3. **v2.3.1 is unreleased**: versionCode 113 is committed but there is no
   `v2.3.1` tag, no GitHub Release, and no Play Store upload. Whether U22 folds
   into that release is Q-U22-4.
