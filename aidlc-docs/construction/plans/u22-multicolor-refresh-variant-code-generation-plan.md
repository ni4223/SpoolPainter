# U22 — Multi-color hex + selected-spool refresh + variant `.`

**Unit type**: one feature (F1) + one bugfix (F2) + one one-liner (F3)
**Per-unit gate**: Functional Design / NFR Requirements / NFR Design /
Infrastructure Design **SKIP** — no new components or services, no
infrastructure; F1's data-model delta is a single Spoolman wire field plus its
form projection, small enough to lock in this Code Gen plan (same shape as U13's
currency work and U16).
**Opened**: 2026-08-15 (session resume, "aidlc continue"; scope set by user)
**Baseline**: versionCode 113 / versionName 2.3.1; tests **565 / 565 ✅**
(re-verified this session, 0 failures, 69 test classes).
**Status**: **F1 ON HOLD; F2 + F3 DONE 2026-08-15, install gate PASSED.**

> **Scope change 2026-08-15, mid-unit.** User direction: *"i dont think U1 can
> support it, lets put this on hold for now"* — **F1 (multi-color hex) is on
> hold** and was not implemented. §1 below is left intact and remains valid if it
> is ever picked up; note the flagged contradiction about whether the U1 firmware
> renders `multi_color_hexes` (see `ui-followups.md` UI-50, which needs resolving
> first). F2 and F3 were built and are green: **tests 565 → 579 ✅**
> (Δ +14, 0 failures), `compileDebugKotlin` ✅, `assembleDebug` ✅ 68.66 MB.
> Q-U22-1..4 and Q-U22-7 are moot for now; Q-U22-5 was resolved as **(c) both**
> and Q-U22-6 as **`.` only** (see §5 / §8 notes). What remains is the on-device
> install gate for F2, which needs the phone.

---

## §0 Scope, as directed

User direction 2026-08-15: *"Here are things i want to do, multi color hex, that
one bug where we were not refreshinhg when selected and we need to add . on
subtype"*.

| ID | Item | Follow-up | Size |
|---|---|---|---|
| **F1** | Multi-color hex | UI-50 Ask 1 | the real lift |
| **F2** | Selected spool doesn't refresh | UI-54 | small, already root-caused |
| **F3** | Allow `.` in the subtype/variant field | UI-50 Ask 2 extension | one-liner |

**Explicitly set aside this unit**: UI-53 (app goes deaf to tags after 1-2
reads/writes). It was U22's first-cut scope and got fully root-caused before the
re-scope; both causes and the fix direction are written up in `ui-followups.md`
under UI-53's "2026-08-15 code trace" section so nothing is lost. It remains the
highest-severity open tester bug and wants its own unit next. UI-56 (a write tap
poisons the ambient buffer) was found during that trace and is logged too.

**UI-55 (no color / transparent)** is adjacent to F1 — its own follow-up says
"fold into the UI-50 multi-color hex work". Whether it rides along is Q-U22-4;
the user did not name it.

---

## §1 F1 — Multi-color hex (UI-50 Ask 1)

### §1.1 Why this is feasible today

Traced 2026-07-26 and recorded in UI-50; re-confirmed here.

- **Spoolman has a native field**: `multi_color_hexes`, `String(128)`, added
  upstream in the `415a8f855e14_multi_colors` migration (2024-05-28). Wire format
  is a **single comma-separated string**, e.g. `"C49449,786BB0"`. Because it's a
  first-class column and not an extra field, it needs **no**
  `ensureExtraFieldsRegistered`-style registration (unlike `card_uids` and
  `variant`). Spoolman also carries `multi_color_direction`
  (`coaxial` / `longitudinal`) — see Q-U22-3.
- **The Snapmaker U1 renders it**: `spoollink.py` reads
  `filament.get("multi_color_hexes")`, splits on comma, uppercases, trims each to
  6 chars, pads to 5 slots, and pushes `RGB_1..RGB_5` to the printer. This is the
  path that actually matters, because it's the Spoolman link rather than the tag.
- **The OpenSpool tag has a different shape**: `additional_color_hexes`, a JSON
  **array** of up to 4 *additional* hexes (i.e. excluding the primary
  `color_hex`); firmware `filament_protocol_ndef.py` maps it to `RGB_1..RGB_N`
  with `COLOR_NUMS` derived. Per earlier user direction the tag is only a backup,
  so this half is secondary — Q-U22-2.

**Naming asymmetry to bridge** (the one real modelling trap): Spoolman's
`multi_color_hexes` is a comma string that **includes** the primary color;
OpenSpool's `additional_color_hexes` is an array of hexes **in addition to**
`color_hex`. Any conversion has to add/drop the primary accordingly. Handle this
in one place — extend `ColorHexCodec` — and never inline the split/join at call
sites.

### §1.2 Model shape

Keep the single `colorHex` as the **primary** color and add extra colors
alongside it, rather than replacing it with a list. That keeps every existing
path (tag write, `SpoolMatchScorer` RGB grading from U20, swatches, the camera
sampler) working unchanged, and makes multi-color purely additive.

- `FormState`: add `extraColorHexes: List<String>` (default empty). Primary stays
  `colorHex`.
- `ColorHexCodec` grows the canonical conversions, all pure and unit-tested:
  - `joinSpoolman(primary, extras): String?` → `"PRIMARY,EXTRA1,…"` (uppercased,
    `#` stripped, blanks dropped, de-duplicated, capped — see §1.3).
  - `splitSpoolman(raw): List<String>` → canonical list, primary first.
  - `toAdditional(primary, extras)` / `fromAdditional(primary, additional)` for
    the OpenSpool array form, if Q-U22-2 says yes.

  **Landmine found in the surface scan**: today's
  `ColorHexCodec.canonicalise("C49449,786BB0")` returns **`"786BB0"`** — the
  `if (it.length > 6) it.takeLast(6)` branch (written for 8-digit ARGB) silently
  swallows the primary color and keeps the *last* one. So the moment a
  `multi_color_hexes` string reaches any existing canonicalisation path (and
  `FormMapping` funnels all three Spoolman→form sites through
  `canonicaliseColorHex` at `FormMapping.kt:188`), it corrupts to the wrong
  single color with no error. `canonicalise` needs an explicit comma guard, and
  it needs a test, before anything else in this unit is safe.
- Wire layer: `SpoolmanFilament.multi_color_hexes: String?`
  (`SpoolmanModels.kt:23-38`, next to `color_hex` at `:27`),
  `CreateFilamentRequest.multi_color_hexes: String?` (`SpoolmanRequests.kt:7-23`),
  `PatchFilamentBody.multi_color_hexes: String?` (`:25-36`), and
  `ExpanderOverrides` (`:48-68`, `colorHex` at `:62`) gains the extras so
  `SpoolmanRepository.kt:279`'s `color_hex = overrides.colorHex` line has a
  sibling. Gson maps by field name (`SpoolmanApiFactory`), so declaring the field
  is all the read side needs — no adapter, no `@SerializedName`.

### §1.3 Caps and validation

- Spoolman's column is `String(128)`; the U1 pads to **5 slots**. So cap at
  **5 total** colors (primary + 4 extra), which also matches OpenSpool's "up to 4
  additional". Enforce the cap in `ColorHexCodec`, not in the UI, so both the
  form and any Spoolman-derived value are bounded.
- Each entry must canonicalise to exactly 6 hex digits or it's dropped.
- De-duplicate case-insensitively (a user who picks the same hex twice gets one).

### §1.4 Matching and diffing (the two places that bite)

1. **`SpoolmanRepository.resolveOrCreateFilament`** (`:592-660`) dedups on the
   identity tuple **(vendor, material, color, variant)** — the color leg is
   `ColorHexCodec.canonicalise(f.color_hex) != targetHex` at **`:607`**. If it
   ignores `multi_color_hexes`, two filaments differing *only* in their extra
   colors collide and the user's new multi-color filament silently resolves onto
   the existing single-color record. The multi-color value must join that
   predicate.
2. **`sparseDiff`** (`:293-307`, color leg at **`:298`**) must gain a
   `multi_color_hexes` case with **order-insensitive, case-insensitive**
   comparison, **and** `PatchFilamentBody.isEmpty()` (`:309-313`, an explicit
   field-by-field `== null` chain) must gain the new field. Miss either and a
   multi-color-only edit collapses to a no-op: `patchFilament` early-returns
   `Success(current)` when the sparse body is empty, so the user gets a silent
   success and Spoolman never changes.

Both are exactly the class of bug U6b-Δ-4 fixed for single color (the shared
`ColorHexCodec` canonicalisation), so the same discipline applies.

### §1.5 UI

`ColorPicker.kt` (528 lines) has one public entry point,
`ColorPicker(colorHex, enabled, onChange, modifier)` at `:71-78`, called from
`FilamentForm.kt:127-131`. It captures exactly one hex: the wheel dialog's hex
field filters to hex digits and `.take(6)` at `:312-335`, and `ColorWheel` takes a
single `seedColor` at `:351-357`. Three entry paths feed that one value — the
named-color menu (`COMMON_COLORS`, 8 entries at `:501-515`), the color wheel
(`:276-499`), and the U17 camera sampler (`CameraColorSampler(onPick)`, one hex,
`:247-255`).

Multi-color needs:

- **add/remove extra color slots**, with the primary keeping today's exact
  one-tap flow so single-color users see no change;
- **a split swatch**, at both of the two swatch sites: `ColorPicker`'s
  `leadingIcon` (`:112-125`, a single 24dp circle filled by `parseColor(current)`)
  and `PickerRow.ColorSwatch` (`:68-81`, with its own copy of the strict 6-char
  parser at `:83-93`). Note `parseColor`/`parseSwatchColor`/`FilamentSpool.kt:58-63`
  are **three** duplicated strict parsers — worth collapsing onto `ColorHexCodec`
  while here, per the U18 "dedup after review" precedent;
- **each slot reachable from all three entry paths**, not just the primary.

Exact interaction shape is **Q-U22-1** — the one genuine design choice here, and
the thing most likely to need on-device iteration. There is **no `androidTest/`
tree in this project**, so `ColorPicker` has no automated UI coverage at all
(`testTag("main-form-color")` is currently unused by any test); the install gate
carries more weight than usual for F1's UI half.

### §1.6 Two blockers for a "no color" state (matters for Q-U22-4)

The surface scan turned up two things that contradict UI-55's note that color
"is NOT a hard save gate":

1. **`FormState.colorHex` defaults to `DEFAULT_COLOR_HEX = "FFFFFF"`**
   (`MainUiState.kt:50` + `:193`), and `blankForm` / `clearedFromDropdown`
   construct a bare `FormState(...)` so both inherit it. So "no color" and
   "white" are the **same state** today — exactly UI-55's complaint, and it means
   a genuine "unset" needs a distinct representation, not just `null`.
2. **`canSubmit` *does* gate on a valid hex**: `MainUiState.kt:169-180` has
   `if (color.isNullOrBlank() || !color.matches(HEX6_REGEX)) return false`. UI-55
   claims `canSave` doesn't require color; whichever predicate the button actually
   uses, this one rejects a colorless form. Any "no color" option must be
   reconciled with it or the user picks "No color" and Save goes dead.
3. **`ColorPicker`'s KDoc is stale**: it documents a `"No Color"` menu entry that
   **does not exist in the code**. The only survivor is `NoColorIcon` (`:258-274`)
   used as the leading-icon fallback when `parseColor` returns null. So UI-55 is
   not "add an option back", it's "build it for the first time".

Also note `CreateFilamentRequest.color_hex` is **non-null** (`SpoolmanRequests.kt:11`)
and `NewFilamentRequest.fromForm` does `colorHex = form.colorHex ?: ""`, so a
colorless create currently posts an empty string.

---

## §2 F2 — Selected spool doesn't refresh (UI-54)

Root cause confirmed in code this session; the follow-up's 2026-07-30 trace is
accurate.

- `MainViewModel.onPullToRefresh` (`MainViewModel.kt:412-422`) only calls
  `spoolman.refreshIfStale(force = true)`. It refreshes the **cache** and never
  re-projects the fresh entry onto the already-selected form.
- `onSpoolSelected` (`MainViewModel.kt:662`) builds the form once via
  `FormMapping.fromSpoolman(...)`, so `FormState` is a **one-time snapshot**.
- The same-id early return at **`MainViewModel.kt:690`**
  (`if (spool.id == _state.value.form.selectedSpoolId) return`) means re-picking
  the same spool is a no-op, so the only user-reachable workaround is clear-then-
  reselect.

**Fix**: after a refresh completes, if a spool is selected, re-derive its form
from the fresh cache entry, **and** let re-selecting the same spool re-derive.
The **stale-prefill guard stays authoritative** — `prefilledRemainingWeightG`,
`prefilledPriceMajor`, `prefilledEmptySpoolWeightG` exist precisely so a
background value can't clobber an edit in flight (printer firmware writes
`remaining_weight` while the user holds a form). A refresh-driven re-derive must
not overwrite fields the user has touched since selecting. Trigger choice is
Q-U22-5.

---

## §3 F3 — Allow `.` in the variant field

One-line allowlist change at `FilamentForm.kt:194`:

```kotlin
.filter { it.isLetterOrDigit() || it in " -+()" }   // →  " -+().", see Q-U22-6
```

Cap stays 50 (raised in UI-50 Ask 2, commit `0110078`). Worth noting the variant
string flows into `SpoolMatchScorer`'s variant signal (added during U21) and into
the derived filament name, both of which are plain substring/concat operations —
so `.` needs no downstream escaping.

---

## §4 Invariants this unit must not break

1. **Single-color behaviour is byte-for-byte unchanged** when no extra colors are
   set: same `color_hex` on create/patch, same tag payload, same swatches, and
   `multi_color_hexes` must be **omitted** (not sent as `""`) so we don't write
   empty strings into Spoolman.
2. **No change to the write MIME** (`application/json`, FR-U6b-Δ-3 — Snapmaker U1
   firmware filters on it).
3. **`SpoolMatchScorer` (U20) keeps grading on the primary hex only** unless
   deliberately changed — its color weight (2.0) and the variant signal (1.0) are
   tuned, and quietly feeding it a multi-color string would change scan-time
   suggestion ranking.
4. **Stale-prefill guard stays authoritative** (F2).
5. **`ensureExtraFieldsRegistered` is NOT touched** — `multi_color_hexes` is a
   native Spoolman column, not an extra field.

---

## §5 Steps

- [~] **S1** — **ON HOLD (F1)** `ColorHexCodec` join/split + cap/de-dup.
- [~] **S2** — **ON HOLD (F1)** wire layer `multi_color_hexes`.
- [~] **S3** — **ON HOLD (F1)** `sparseDiff` + `isEmpty()` +
      `resolveOrCreateFilament` match.
- [~] **S4** — **ON HOLD (F1)** `FormState.extraColorHexes` + `FormMapping`.
- [~] **S5** — **ON HOLD (F1)** `ColorPicker` multi-hex entry + split swatch.
- [x] **S6** — **F3 DONE.** `.` added to the variant allowlist. The sanitiser was
      **extracted from the composable** into
      `internal fun sanitiseVariant(input: String): String?` in `FilamentForm.kt`
      first, because this module has no Compose UI test source set (Compose test
      deps are `androidTestImplementation` and there is no `androidTest/` tree),
      so the inline lambda was untestable. Call site is now
      `onValueChange = { input -> onChange(sanitiseVariant(input)) }`.
- [x] **S7** — **F2 DONE.** Both halves per Q-U22-5 = (c): new private
      `reDeriveSelectedSpoolForm(spools)` hooked into the **existing
      `spoolman.spools` collector** (so it covers the `MainActivity.onResume`
      refresh as well as pull-to-refresh, not just the gesture the report named),
      and the same-id early return at the old `MainViewModel.kt:690` removed.
      Clobber protection via a new private `selectionFormSnapshot: FormState?`
      compared through a new `FormState.dataFingerprint()` that nulls the
      view-state fields (`cardUid`, `rawWriteMode`, `moreDetailsExpanded`,
      `weightMethod`). Snapshot is set on **both** `onSpoolSelected` and the
      `PrefillFromSpoolman` read path — the reported scenario starts with a tag
      read, so setting it only on the picker path would have missed it — and
      cleared on deselect. Re-derive uses `SpoolmanUidSource.PreserveCurrent` so a
      background refresh can't disturb the in-hand UID.
- [~] **S8** — **ON HOLD (F1)** OpenSpool tag `additional_color_hexes`.
- [x] **S9** — tests + matrix. **565 → 579 ✅** (Δ +14: `SanitiseVariantTest` 8,
      `MainViewModelRefreshRederiveTest` 6), 0 failures.
      `compileDebugKotlin` ✅ / `testDebugUnitTest` ✅ 579/579 /
      `assembleDebug` ✅ **68.66 MB**. `assembleRelease` / `bundleRelease` not run
      — no release decision yet (Q-U22-7 deferred with F1).
- [x] **S10** — **on-device install gate PASSED 2026-08-15** (moto g stylus 2025 /
      Android 16, build 113 / 2.3.1-DEBUG). Test subject was a filament actively
      being consumed by the printer, giving a continuously changing server-side
      `remaining_weight`. A dedicated `SpoolRederive` logcat tag was added
      mid-gate because a first attempt was unverifiable without it (release-safe:
      stripped by the existing `-assumenosideeffects` Log rule).
      **Re-derive PASSED** — 3 events, weight tracked
      `556.43536 → 556.32776 → 556.0448 → 554.59595`, with
      `material PETG->PETG color ED2C2C->ED2C2C variant Basic->Basic` on every
      line confirming it updates only what genuinely changed.
      **No-clobber PASSED** — with an edit in the form and the printer still
      consuming, the next refresh logged
      `skip: form edited by user, not clobbering (spool 84)`.
      **Fully reconciled**: 7 cache updates = 3 pre-selection (silent early
      return) + 3 re-derived + 1 declined; 0 exceptions.
      Also confirmed incidentally: **nothing polls** — 20 minutes foregrounded and
      idle produced zero refreshes, so the form only follows Spoolman after a
      pull, an app resume, or a Read. Expected, but a testing trap worth knowing.
      F3's `.` was **not** eyeballed on-device (covered by `SanitiseVariantTest`).
- [x] **S11** — docs: `ui-followups.md` (UI-50 Ask 1 → on hold + the U1
      contradiction flagged, Ask 2 → extended, UI-54 → fixed-pending-verify),
      `aidlc-state.md` (U22 entry + the §7 stale-state sync), audit appended.
- [ ] **S12** — close-out commit. Push / tag / GitHub Release / Play Store
      **wait for explicit go** per standing direction.

---

## §6 Test targets (baseline 565)

Exact files, from the surface scan:

- **`ColorHexCodecTest.kt`** (8 tests today, all color) — extend with the comma
  guard from §1.2 (the `"C49449,786BB0"` → `"786BB0"` corruption), join/split
  round-trip, `#`/lowercase normalisation, blank+invalid dropped, de-dup, the
  5-color cap, primary-first ordering, and additional-vs-full conversion.
- **`SpoolmanRepositoryPatchFilamentTest.kt`** (6 tests exercising `sparseDiff`)
  — multi-color-only edit produces a non-empty PATCH; the **same colors reordered**
  produce **no** PATCH; single-color path unchanged and **omits** the field.
- **`ResolveOrCreateFilamentTest.kt`** (6 tests, owns the matcher's color
  contract) — two filaments differing only in extra colors must not collide.
- **`FormMappingTest.kt`** — both directions, including a legacy Spoolman record
  with `multi_color_hexes == null`.
- **`MainViewModelMoreDetailsExpanderTest.kt`** — `toExpanderOverrides()` carries
  the extras on the existing-spool path (it already asserts `overrides.colorHex`).
- **`OpenSpoolPayloadCodecTest.kt`** — only if Q-U22-2 = yes: the
  `additional_color_hexes` round-trip, plus confirming it is **not** emitted when
  empty (mirroring the existing `lot_nr`-never-emitted case).
- **New/extended `MainViewModel` refresh test** — F2: refresh re-derives the
  selected spool; in-flight edits survive the stale-prefill guard; no spool
  selected = no-op; re-selecting the same id re-derives.
- **`FilamentForm` variant coverage** — `.` survives sanitisation; cap still 50.

Compile-only ripple to expect: `FakeSpoolmanApi.kt:103` (call-log format string)
and `:120` (echo-back) need updating for any create-path multi-color assertion.
Everything else that merely *constructs* `SpoolmanFilament` /
`CreateFilamentRequest` keeps compiling because the new fields get defaults.

`SpoolMatchScorerTest.kt` should stay **green and untouched** — that's the
regression signal for invariant 3 (scan-time ranking still grades on the primary
hex only).

Rough Δ **+25 to +35**, so ~**590-600** total.

---

## §7 Housekeeping folded into S11

`aidlc-state.md` is **stale** — its `Current Stage` stops at U21 (112 / 2.3.0)
and does not record: `0110078` (UI-50 Ask 2 variant cap), `2521177` (v2.3.1
CameraX 1.4.2 for 16 KB page sizes, which bumped 112→113 / 2.3.0→2.3.1),
`cc79049` (UI-53/54/55 logged), `435b8c4` + `f197de3` (README referral links +
both-Play-tracks install section). Sync it as part of S11.

⚠️ **This assumption was WRONG and is corrected here.** The note originally read
"v2.3.1 (113) is committed but never released", inferred from there being no
`v2.3.1` git tag and no GitHub Release. Play Console then rejected the upload:
*"Version code 113 has already been used."* A versionCode can be consumed by a
Play upload without ever being tagged, GitHub-released, or rolled out, so **git
tags are not a record of what Play has seen**. Shipped as **114 / 2.3.2**
instead. Check Play Console for the highest used versionCode before choosing. And `aidlc-docs/play-store-listing.md` (+4 lines, an OPEN SOURCE /
GPL-3.0 + GitHub section) plus untracked `aidlc-docs/reddit-launch-post.md` are
marketing copy prior sessions deliberately left uncommitted — leaving both alone.

---

## §8 Open questions (Q-U22-*)

**Q-U22-1 — Multi-color entry UX.** The real design choice. Options: (a) the
Color field shows a row of slot chips, primary first, with a `+` chip to add up
to 4 more, each chip opening the existing picker (named / wheel / Scan color);
(b) a "Multi-color" toggle that reveals a second section listing extra colors
with add/remove rows; (c) keep one field but accept a comma-separated hex string.
Recommended **(a)** — it reuses every existing entry path per slot, keeps
single-color users on exactly today's one-tap flow, and the chip row doubles as
the display affordance. **(c) is actively hostile to the current code**: the wheel
dialog's hex field filter drops `,` outright (`ColorPicker.kt:312-335`) and
`canonicalise` mangles comma strings (§1.2), so it would mean fighting two
existing filters to build the worst UX of the three.

**Q-U22-2 — Also write `additional_color_hexes` to the tag?** Spoolman +
SpoolLink is the path the U1 actually uses, and the tag is a declared backup.
Recommended **yes, but as the last step (S8)** — cheap once `ColorHexCodec` has
the conversion, and it keeps a wiped-Spoolman recovery honest. Deferrable without
blocking F1 if it complicates the payload.

**Q-U22-3 — Model `multi_color_direction`?** Spoolman stores `coaxial` vs
`longitudinal` (side-by-side vs along-the-strand). Recommended **no for now** —
send/preserve nothing and let Spoolman keep its default; the U1's `RGB_1..RGB_5`
push doesn't depend on it. Revisit if a tester asks.

**Q-U22-4 — Fold in UI-55 (no color / transparent)?** Its own entry says to fold
it into this work. Recommended **yes** — the `ColorPicker` is already being
reworked here, so doing it later means touching the same file twice. But go in
knowing it is **bigger than UI-55 makes it sound**, per §1.6: the form defaults to
`"FFFFFF"` so "no color" and "white" are currently indistinguishable, `canSubmit`
actively rejects a blank/invalid hex, `CreateFilamentRequest.color_hex` is
non-null, and the "No Color" menu item the KDoc documents doesn't actually exist.
If you'd rather keep this unit tight, deferring UI-55 is defensible — just expect
a second pass through `ColorPicker`.

**Q-U22-5 — F2 re-derive trigger.** (a) auto re-derive on every completed
refresh; (b) only drop the same-id early return at `MainViewModel.kt:690` so
re-picking re-derives; (c) both. Recommended **(c)** — (a) is what the reporter
actually asked for ("pull-to-refresh does nothing"), (b) is a one-line safety
valve, and the stale-prefill guard makes (a) safe.

**Q-U22-6 — Variant allowlist: just `.` or a bit more?** Recommended **`.` plus
`/` and `#`** — `/` shows up in "PLA/PHA" and `#` in colour codes, and the same
issue that asked for `+ ( )` will ask for these next. Tight allowlist otherwise.

**Q-U22-7 — Version. RESOLVED: 114 / 2.3.2** (see the correction in §7 — 113 was
already consumed on Play despite having no git tag). Original framing, kept to
show why the first answer was wrong: ~~v2.3.1 / 113 is committed but never released, so U22 can
ride it (the U14c+U15 → 2.1.2 and U18 → 2.2.0 pattern) or take 114 / 2.4.0.
Recommended **114 / 2.4.0** — multi-color is a real user-facing feature, not a
patch, and 2.3.1's CameraX 16 KB fix deserves to ship in the same release rather
than be renamed.~~ With F1 on hold this became a patch release, so **2.3.2** was
the right name; the versionCode had to move to 114 regardless.
