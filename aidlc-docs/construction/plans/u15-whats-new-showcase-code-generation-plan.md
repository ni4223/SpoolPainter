# U15 — "What's New" First-Run Showcase — Code Generation Plan

**Unit**: U15 (What's New showcase)
**Opened**: 2026-06-14, after U14c close-out
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** (small, well-scoped UI
feature; design decisions captured here per the convention used since U10).
Code Generation **EXECUTE**.

## 1. Scope

A one-time, version-aware "What's new in v2" showcase that appears as a
ModalBottomSheet the first time a user opens a build whose `versionCode` is
newer than the one they last saw — with a fresh-install suppression so a
brand-new user isn't shown a "what's new" for features they never had an
"old" version of.

Locked decisions (2026-06-14):
- **Trigger** — version-aware. Persist `lastSeenWhatsNewVersion: Int` in
  `Settings`. Show when the showcase has content newer than last seen, except
  on a truly-fresh install (detected via `PackageManager` install vs update
  time).
- **Presentation** — `ModalBottomSheet`, matching `PairAnotherTagSheet.kt`.
- **Content** — feature highlights grounded in `README.md` "What's new in
  v2.1" + key v2.0 capabilities, framed against v1 (what's new since the v1
  the user came from).

### 1.1 Trigger logic (the one non-obvious invariant)

`lastSeenWhatsNewVersion` defaults to `0`. On every cold start, compute
`shouldShow` once:

```
isFreshInstall = packageInfo.firstInstallTime == packageInfo.lastUpdateTime
last = settings.lastSeenWhatsNewVersion
current = BuildConfig.VERSION_CODE      // 106

shouldShow =
    if (last == 0) {
        // Never recorded. Show ONLY if this install has been updated at
        // least once (i.e. a v1 -> v2 in-place updater). A genuinely fresh
        // v2 install (firstInstallTime == lastUpdateTime) is suppressed.
        !isFreshInstall
    } else {
        last < current        // a v2.x -> newer updater
    }
```

After the decision is made (shown OR suppressed), persist
`lastSeenWhatsNewVersion = current` so neither path re-triggers for this
version. Persisting on suppression is what stops a fresh-install user from
seeing the sheet on their *second* launch.

This means: v1->v2 updaters see it once; v2.1.1->2.1.2 updaters see it once;
fresh v2 installers never see it; nobody sees it twice for the same version.

## 2. File impact

### New files (4)
1. `app/src/main/java/com/spoolpainter/app/ui/components/sheets/WhatsNewSheet.kt`
   — the `ModalBottomSheet` composable. Title, scrollable `Column` of
   highlight rows (leading icon in primary tint + bold headline + body line),
   single "Got it" `Button`. Stateless: takes `visible: Boolean`,
   `highlights: List<WhatsNewHighlight>`, `onDismiss: () -> Unit`. Matches
   `PairAnotherTagSheet` style (16dp/12dp padding, `spacedBy(12.dp)`,
   `testTag`s).
2. `app/src/main/java/com/spoolpainter/app/ui/components/sheets/WhatsNewContent.kt`
   — `data class WhatsNewHighlight(icon, title, body)` + the static
   `whatsNewV2Highlights` list (the copy). Keeping copy out of the composable
   makes it unit-testable and easy to edit per release.
3. `app/src/main/java/com/spoolpainter/app/ui/whatsnew/WhatsNewController.kt`
   — `@Singleton` injectable that owns the trigger decision. Holds
   `shouldShow(currentVersion, isFreshInstall, lastSeen): Boolean` (pure, the
   §1.1 logic) so it's unit-testable without Android. Exposes a
   `StateFlow<Boolean>` the activity observes, and a `markSeen()` suspend that
   writes `lastSeenWhatsNewVersion`.
4. `app/src/test/java/com/spoolpainter/app/ui/whatsnew/WhatsNewControllerTest.kt`
   — table tests over the pure `shouldShow` (fresh install, v1 updater,
   v2.x updater, same-version relaunch, already-seen).

### Modified files (4)
5. `app/src/main/java/com/spoolpainter/app/data/local/Settings.kt`
   — add `val lastSeenWhatsNewVersion: Int = 0`. (Serializer already
   `ignoreUnknownKeys` + `coerceInputValues`, so old payloads read back as
   default 0 — forward/backward safe, no migration.)
6. `app/src/main/java/com/spoolpainter/app/data/local/SettingsRepository.kt`
   — add `suspend fun setLastSeenWhatsNewVersion(version: Int)` to the
   interface + impl (`store.updateData { it.copy(...) }`).
7. `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt`
   — compute `isFreshInstall` from `packageManager.getPackageInfo` once;
   inject `WhatsNewController`; inside `setContent`, collect its
   `StateFlow<Boolean>` and render `WhatsNewSheet(...)` over `MainScreen`
   (sibling to the existing `showSettings` branch — the sheet floats above
   whatever screen is shown). `onDismiss` calls `markSeen()` via
   `lifecycleScope`.
8. `aidlc-docs/ui-followups.md` — note U15 shipped; no new followups expected.

### Decision: where the trigger lives
`WhatsNewController` (not `MainViewModel`) because the decision is
app-lifecycle scoped, depends on `PackageManager` (an Activity/Context
concern), and must not couple to the NFC/Spoolman-heavy `MainViewModel`.
`@Singleton` + injected `SettingsRepository`.

## 3. Content (copy) — draft, grounded in README

Title: **"What's new in SpoolPainter v2"**
Subtitle/body lead: "You've been updated from v1. Here's what changed."

Highlights (icon / title / body):
1. **Read vendor spool tags** (`Icons.Filled.Nfc` or `Sensors`) — "Bambu Lab,
   Snapmaker, Creality, QIDI, Anycubic, and Elegoo tags now decode and prefill
   the form, alongside OpenSpool tags."
2. **Save and Write, split** (`Icons.Filled.SaveAlt`) — "Save commits the form
   to Spoolman; Write pairs the tag in a separate tap. Edit weight, price, and
   color on spools you've already paired."
3. **Rebuilt interface** (`Icons.Filled.Palette`) — "A refreshed v2 UI with a
   light / dark theme toggle, 22-currency support, and per-section sorting."
4. **Same tags, same Spoolman** (`Icons.Filled.CheckCircle`) — "Everything v1
   did still works: read / write OpenSpool tags, create-and-pair, and sync
   with your self-hosted Spoolman. Your tags and inventory carry over with no
   migration."

Copy rules: **no em dashes in user-facing strings** (period or comma only) per
project convention. Final wording confirmable at install-gate; the list is
data so it's a one-line edit.

## 4. Step-by-step

- [x] 4.1 Add `lastSeenWhatsNewVersion` to `Settings` + setter to repo
- [x] 4.2 `WhatsNewContent.kt` — data class + `whatsNewV2Highlights` (5 items;
      Spoolman-companion lead added per user direction)
- [x] 4.3 `WhatsNewController.kt` — pure `shouldShow` + StateFlow + `markSeen`
- [x] 4.4 `WhatsNewSheet.kt` — ModalBottomSheet composable
- [x] 4.5 Wire into `MainActivity` (compute isFreshInstall, inject, host sheet)
- [x] 4.6 `WhatsNewControllerTest` — 8 tests over `shouldShow` + `onColdStart`
      (also extended `FakeSettingsRepository` with the new setter)
- [x] 4.7 Build matrix: `compileDebugKotlin` ✅ / `testDebugUnitTest` ✅ **496/496**
      / `assembleDebug` ✅ 65 MB. **Version NOT bumped** — stays 106 / 2.1.2 per
      user direction "dont need to update the version since we never released
      last one"; U15 rides the existing unreleased version.
- [x] 4.8 Install-gate on moto g stylus 2025: sheet renders correctly (all 5
      rows + "Got it", scrolls cleanly) on a cleared-data launch. Trigger
      verified firing on the updated-package path; suppression-on-already-seen
      verified earlier (second launch after dismiss did not re-show).

## 7. Final copy (locked via on-device iteration 2026-06-14)

5 rows. All sentence-case titles, no em dashes. Save/Write-split row dropped
(v2-internal UI detail, meaningless to a v1 user); "Everything v1 did still
works" row dropped (changelog filler). Two genuinely-new feature rows added:
U1-firmware tag-serial mapping + pairing/move-on-bind.

1. **Your Spoolman companion** (Sync icon) — companion-app framing; Spoolman
   support itself is NOT new (v1 had it), so framed as scene-setter not a new
   feature.
2. **Vendor tag support** (Nfc icon) — Bambu/Snapmaker/Creality/QIDI/Anycubic/
   Elegoo. OpenSpool deliberately excluded (not a vendor tag).
3. **Built for U1 firmware** (Link icon) — tag built-in serial number linked to
   a spool in Spoolman; latest Snapmaker U1 firmware resolves the spool,
   including spools on their original vendor tags.
4. **Pairing made easy** (Nfc icon) — pair both tags (two sides of a spool)
   without repeating setup; move-on-bind re-links automatically.
5. **Fresh new look** (Palette icon) — ground-up redesign; light/dark themes,
   improved sorting.

Copy was user-authored via ChatGPT prompts + on-device review across ~10
iterations; final strings live in `WhatsNewContent.kt`.

## 5. Test target

488 -> ~493 (Δ +5 from `WhatsNewControllerTest`: fresh-install, v1-updater,
v2.x-updater, same-version-relaunch, already-seen-higher). No existing test
assertions touched (purely additive field + new files).

## 6. Open questions for Part 2 (if any)
- Q-U15-1: versionName bump — 2.1.3 (patch) assumed. Confirm at gate.
- Q-U15-2: exact icons — listed above are best-guess from
  `material-icons-extended` (already on classpath since U9). Substituted if any
  aren't present.
- Q-U15-3: final copy wording — draft above; editable as data.
