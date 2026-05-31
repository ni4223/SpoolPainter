# SpoolPainter v2.0 — Play Store Testing-Track Upload Checklist

Procedural checklist for the v2.0 testing-track upload. Actual upload is gated on the user — this document captures the steps so the upload is reproducible.

## Pre-flight

- [ ] All U10 plan steps complete and committed
- [ ] `./gradlew :app:testDebugUnitTest` ✅ (target: 362 / 362)
- [ ] `./gradlew :app:lintRelease` ✅ no errors
- [ ] Manual NFC checklist (`manual-nfc-checklist.md`) run end-to-end ✅
- [ ] Release build smoke-tested on device (release variant, signed APK)
- [ ] Snapmaker U1 round-trip ✅
- [ ] `aidlc-docs/operations/v2.0-tester-release-notes.md` finalised
- [ ] README's testing-track link placeholder ready to fill (see Post-upload §3)

## Build artefacts

- [ ] `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- [ ] `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
- [ ] Verify signing:
  ```
  apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
  ```
  Confirm: signed with v1+v2+v3 schemes; signer is the `spoolpainter` key alias.
- [ ] APK size sanity check (`du -h app/build/outputs/apk/release/*.apk`):
  - target: ≤ 35 MB after R8 minify
  - if > 50 MB, ProGuard rules need tightening or a dependency review
- [ ] Optional: `apkanalyzer dex packages app/build/outputs/apk/release/app-release.apk | head -30` to confirm shape

## Testing-track choice (TBD by user)

Pick one before uploading:

| Track | Limit | Review | Best for |
|---|---|---|---|
| Internal testing | ≤ 100 testers | none | fastest iteration; private testers only |
| Closed testing | named testers | none | broader feedback with curated list |
| Open testing | anyone with link | yes (review can take a few days) | public sign-up, larger reach |

For a first v2.0 release, **Closed testing** is usually the right call — wider than Internal, no review delay.

## Upload steps (Play Console — manual)

1. Sign in to [Play Console](https://play.google.com/console)
2. Select the SpoolPainter app
3. Testing → [chosen track] → Create new release
4. Upload `app-release.aab`
5. Release name: `2.0 (100)`
6. Paste tester release notes from `aidlc-docs/operations/v2.0-tester-release-notes.md` into the "What's new" / release notes field (Play Console limits release notes to 500 chars per locale — trim or summarise; full notes live in the repo)
7. Save → Review release → Start rollout to [chosen track]

## Post-upload

1. **Tester invite link** — copy from Play Console (Testing → [track] → Testers → Copy link)
2. **Distribute** — share the invite link with testers via your preferred channel
3. **Update README** — open `README.md`, find the `<!-- TODO: replace this placeholder after the testing-track upload -->` marker, replace `(TBD-after-upload)` with the actual invite link, commit + push
4. **Monitor** — Play Console crash dashboard for 48h before declaring release-validated; watch for any obvious R8-induced crashes that didn't surface in local smoke tests

## Rollback

If a critical bug surfaces post-upload:

- **Halt rollout** — Play Console → Testing → [track] → Halt rollout (testers stop receiving the new build)
- **Patch + rebuild** — fix locally, bump versionCode (101+), rebuild AAB, upload as a new release
- **Communicate** — let testers know via the same channel used for the original invite

## Explicitly not in scope

- **Production-track promotion** — out of scope for U10 / AIDLC v2.0. Promoting from testing → production is a manual step the user takes after testing-track validation completes.
