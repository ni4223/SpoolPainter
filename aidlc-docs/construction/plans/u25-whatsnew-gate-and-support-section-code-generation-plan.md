# U25 — Code Generation Plan

**Unit**: U25 — What's-new content gate + "Support the project" section
**Opened / closed**: 2026-08-22 (same session as U24)
**Per-unit gate**: FD / NFR-R / NFR-D / Infra-D **SKIP** — one bugfix plus one
small UI addition; design folded into this plan (same convention as U20-U24).
**Version**: rides **115 / 2.4.0** alongside U22 + U23 + U24 (user: "fold into
2.4.0").

---

## §1 Scope

### F1 — What's-new sheet re-opened on every version bump (BUG)

**Trigger vs content were keyed to different things.** `MainActivity` passed
`BuildConfig.VERSION_CODE`, `shouldShow` compared `lastSeenVersion < currentVersion`,
and the sheet's copy is a static list (`whatsNewV2Highlights`). So every version
bump satisfied the comparison and re-opened the sheet on copy the user had
already dismissed.

Measured, not assumed: `git log --follow` shows the copy edited **twice** (created
at 106 / U15, camera row added at 110 / U17) against **seven** released
versionCodes (106, 108, 109, 110, 111, 112, 115). Five of seven showings had
nothing new in them.

**Why it survived**: UI-41 fixed a louder bug in the same place (the sheet
appearing on *every cold launch*, from reading `settings.value` before DataStore's
first disk read). That fix produced "once per version", which matched the written
spec. The spec was the flaw: the intent was "once per new set of highlights", and
version is only a valid proxy for that if every release changes copy.

**Fix**: a `WHATS_NEW_CONTENT_VERSION` constant living next to the copy, compared
instead of the app's versionCode. Set to **110**, the last real copy change.

### F2 — "Support the project" section (referral links in-app)

Mirrors the README's Support section: Polymaker (link only) and Snapmaker's three
regional storefronts plus coupon code `ni42`.

---

## §2 Decisions (all user-directed this session)

- **D1 — placement**: Settings, bottom, below every real setting. Not the main
  screen, not the `⋮` menu, not the What's-new sheet. Never sits between the user
  and the tag workflow.
- **D2 — always visible**, not collapsed (user). First cut was an expander
  matching `SettingsVendorSection`; changed on request.
- **D3 — its own Card**, not another settings row (user: "make it a different
  section"). It is not a setting, and blending it into the list made it read like
  one.
- **D4 — no "referral link" wording** (user, asked twice). I flagged once that a
  one-line disclosure is the safer read under FTC endorsement guidance and kept a
  short marker on the first pass; the user reaffirmed, so it is removed. Their
  call, recorded here rather than re-litigated. The header "Support the project"
  carries the framing.
- **D5 — "coupon", not "referral", code** (user: it is a coupon code). Corrected
  in the app AND in the README, which carried the same factual error.
- **D6 — the coupon must be easy to copy** (user). It is a button: tap copies to
  the clipboard and the label swaps to "Copied" + checkmark for 2 s. Needed
  because minSdk is 29 and Android's own clipboard confirmation only appears on
  13+; on 13+ it also puts the feedback next to the thing you tapped.
- **D7 — real vendor logos** (user: "maybe use logo"). Polymaker's teal PNG from
  their own store CDN, untinted (brand colour reads on both themes); Snapmaker's
  monochrome SVG converted to a vector drawable and tinted to the theme
  foreground, which is faithful to the black/white variants they publish and
  serves both themes from one asset. Attribution + the not-affiliated statement
  are in `NOTICE`; neither logo is covered by the project's GPL-3.0.
- **D8 — brand colour on the outline, not the label.** Teal `108474` and cyan
  `00B2E3` lack contrast for text on a light background.

## §3 Verified, not assumed

- All four shop URLs resolve (`curl -I`): the myshopify hosts 301 to
  `us.snapmaker.com`, `eu.snapmaker.com`, `shop.snapmaker.com`, preserving `?ref`.
  **`test-snapmaker.myshopify.com` reads like a staging host but is the real global
  store** — I flagged it as a risk before checking, and the check cleared it.
- Polymaker link opens in Chrome at `shop.polymaker.com` with the referral params
  intact (on-device).
- Coupon copy works: the button showed "Copied", and Android's own clipboard chip
  showed `ni42`. The row then reverted, confirmed by comparing the scanline band
  against the pre-copy screenshot (50/50 identical).

## §4 Steps

- [x] **S1** — `WHATS_NEW_CONTENT_VERSION = 110` next to the copy, with the
      bump-it-when-you-edit rule and the never-move-backwards constraint stated.
- [x] **S2** — `shouldShow`/`onColdStart` take `contentVersion`; `MainActivity`
      passes the constant. Parameter renamed because the old name was the lie.
- [x] **S3** — Tests: an app-only bump does not re-show; a user behind the last
      copy change still gets it once; constant and highlight count are pinned
      together so editing copy without bumping fails loudly.
- [x] **S4** — `SettingsSupportSection` + `ReferralTarget` data, wired into
      `SettingsScreen`.
- [x] **S5** — Logo assets + `NOTICE` attribution.
- [x] **S6** — `ReferralTargetsTest`: referral marker present on every URL, https
      only, unique labels, coupon on every Snapmaker region, three distinct
      regional stores, non-transparent accent.
- [x] **S7** — README: "Referral Code" → "coupon code".
- [x] **S8** — On-device verification per §3.

## §5 Result

Tests **606 → 615** (Δ +9: 3 what's-new gate, 6 referral data). Ships in
115 / 2.4.0. **2.4.0 will NOT show the What's-new sheet**, because the copy did
not change; that is the fix working as intended, and it is why the release notes
carry the feature news instead.

## §6 Risk left open

- **R1** — Copy can still be edited without bumping the constant. S3's pinning
  test fails loudly if the highlight count changes, but not if copy is reworded
  in place. Accepted: a reworded highlight not re-announcing itself is a much
  smaller harm than the bug being fixed.
- **R2** — Vendor logos are trademarks. Nominative use for identifying a store you
  link to is standard affiliate practice and there is an active arrangement with
  both, but if either brand's guidelines require sign-off, `NOTICE` names the
  exact files to swap.
