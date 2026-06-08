# U14b — Session Resume (autonomous mode)

**Read this top-to-bottom on resume. Then start work without asking for clarification.** The user is away ≥1 hour; time is **not** the constraint. Quality + completeness are.

**No phone is connected during this session.** Don't run `adb install`. Don't try to verify on-device. The install gate + per-vendor tag round-trip happen **when the user is back**. Stop at the build matrix.

## TL;DR for new session

1. Verify working-tree fingerprint (below).
2. Read the plan: `aidlc-docs/construction/plans/u14b-vendor-expansion-code-generation-plan.md`.
3. Execute the **full plan** — all 4 vendors + registry refactor + Settings rename + chip row + adapter parity tests + NOTICE updates. Don't shortcut.
4. End state: source code complete, **debug APK built and sitting at `app/build/outputs/apk/debug/app-debug.apk`**, **release APK + AAB built and signed** (`app/build/outputs/apk/release/app-release.apk` + `app/build/outputs/bundle/release/app-release.aab`), all unit tests green. Don't commit, don't push, don't install.
5. Update `aidlc-docs/construction/u14b-vendor-expansion/code/u14b-progress.md` after each milestone so the user can read what happened.
6. The new session inherits the same context, plan, and locked Q&A. Don't re-ask the user anything.

## Operating mode

- **Autonomous**: don't ask the user clarifying questions during this run. Every Q-U14b-* is locked (see ledger below). If you hit a real blocker (broken environment, missing keystore, conflicting state), document it in `u14b-progress.md` and stop — do not destructive-fix.
- **No time pressure**: do every step properly. Write tests for each new vendor processor. Run the test suite after each milestone — don't pile new work on a red build. Verify on-device that the chip row renders correctly before declaring it done.
- **Permission posture**: `.claude/settings.local.json` was expanded for this run. You have wildcard allow on `./gradlew *`, `git status/diff/log/show/rev-*/fetch/ls-files/branch *`, `curl -sL *`, common shell utils (grep/awk/sed/ls/find/cat/tail/head/tee/printf/echo/test/mkdir -p/touch/python3/diff/file/wc/xargs/stat/du/basename/dirname/realpath/unzip -l), `rm /tmp/*` only, Read/Edit/Write under the project tree. `adb *` is technically allowed but **don't use it — phone not connected**. Hard `deny`: `git push`, `git commit`, `git reset --hard`, `git rebase`, `git checkout --`, `git restore`, `git clean -f`, `adb uninstall com.spoolpainter.app` (prod app safety), `WebFetch(forms.gle)`, `rm -rf` against the project tree. **You will not commit during this session.**
- **Build commands**: always wrap with `JAVA_HOME=$(/usr/libexec/java_home -v 17)`. Keystore password lives at `~/spoolpainter-keystore.pwd`; release builds use `KEYSTORE_PASSWORD=$(cat ~/spoolpainter-keystore.pwd)`.
- **No phone connected**: `adb devices` will show no devices. Do NOT run `adb install` / `adb logcat` / any on-device command. Build artefacts (APKs, AAB) are the deliverable. The user wires up the phone + runs the full install gate when they're back.

## Q-U14b-* answer ledger (LOCKED — do not re-ask)

| Q | Answer | What it means |
|---|---|---|
| 1 | A | Chip row alphabetical: Anycubic, Bambu Lab, Creality, Elegoo, OpenSpool, QIDI, Snapmaker. |
| 2 | A | Include OpenSpool as a chip. Always lit. |
| 3 | **B** | Pin Elegoo lookup table to a specific OpenRFID commit SHA. Capture the SHA when you fetch and cite it in NOTICE. |
| 4 | A | Creality encrypted-tag with no enc key configured → silent log warn + null parsedHint. |
| 5 | A | One happy-path parity test per Bambu/Snapmaker adapter. |
| 6 | A | MifareUltralight: try 36 pages, fall back to chip-reported. |
| 7 | **B** | Settings field copy: short labels + ONE preface line above all 3 fields. No per-field supportingText. |
| 8 | **B** | Drop "U1" from Snapmaker display name. Update `VendorTagHint` chip copy too. |

## Working-tree fingerprint (verify on resume)

```
HEAD            d885ac25ad6d4ef05d5b2eb9862d2c53306ae75c
origin/v2       dadf6f4d813f6eb3cccdfd8d7e02143429614c7b
ahead by        1   (the v2.1.0 docs close-out commit `d885ac2`, unpushed)

git status --short:
 M aidlc-docs/aidlc-state.md
 M aidlc-docs/audit.md
?? aidlc-docs/construction/plans/u14b-vendor-expansion-code-generation-plan.md
?? aidlc-docs/construction/u14b-vendor-expansion/
```

`.claude/settings.local.json` was modified during prep but is in the user's global gitignore (`~/.config/git/ignore`), so it doesn't show in `git status`. Permissions persist on disk regardless. **Don't worry about it not showing up.**

If `git status --short` doesn't match → STOP. Document the drift in `u14b-progress.md` and ask the user. Don't try to "fix" by reverting.

## Execution order — full Slice C, milestone-checkpointed

Each milestone ends green-or-stop. Don't pile new work on a red build. After each, append a short entry to `u14b-progress.md`.

### Milestone 1 — Refactor seam

Goal: Bambu + Snapmaker behind `VendorTagProcessor` + `VendorTagRegistry`. Zero behaviour change.

Steps from plan:
- Step 1 (interface + value classes — `VendorId` enum, `VendorAuth`, `VendorSettings`)
- Step 7 (BambuProcessor + SnapmakerProcessor adapters wrapping existing top-level functions)
- Step 8 (VendorTagRegistry + dispatch algorithm — MifareClassic branch implemented, Ultralight branch returns null until M4)
- Step 9 (MifareClassicReader generalised: `tryReadRawCountedMulti(tag, vendorAuths: Map<VendorId, VendorAuth>)`)

Verify:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ — hold at **421/421** (no behaviour change)

**Checkpoint**: working tree compiles + all existing tests green. Bambu + Snapmaker still parse the way they always did.

### Milestone 2 — QIDI

Goal: smallest new vendor lands.

Steps:
- Step 3 (QidiProcessor + QidiTables — port `MATERIALS` + `COLORS` from `OpenRFID/src/tag/qidi/constants.py`)
- Add `QidiProcessor` to `VendorTagRegistry.processors`
- New test `QidiProcessorTest`: at least one good case per material family (PLA, PETG, ABS, ASA, PA, TPU); rejection cases for bad codes and non-zero trailing bytes

Verify:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ — target **427/427** (Δ +6)

**Checkpoint 2**: QIDI parsing works in tests.

### Milestone 3 — Settings rename + chip row + Creality fields

Goal: full Settings UI lands now so it's available even if a downstream parser breaks.

Steps:
- Step 11 (SettingsRepository — add `crealitySalt: Flow<String>` + `crealityEncKey: Flow<String>` DataStore keys)
- Step 12 (SettingsViewModel + SettingsUiState extensions; two new setters)
- Step 13 (VendorTagChipRow composable — FlowRow of small surfaces, lit/dimmed by config status)
- Step 14 (SettingsVendorSection rework — rename "Advanced" → "Vendor tag support"; preface line; 3 fields stacked)
- Q-U14b-8 fold-in: `VendorTagHint` "Snapmaker U1" → "Snapmaker"
- New test `VendorTagChipRowTest`: chip count = 7; Bambu + Creality dimmed when their keys are empty
- (Optional but nice) `SettingsVendorSectionTest` if straightforward

Verify:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ — target **429/429** or higher (Δ +2 minimum: VendorTagChipRowTest)
- `assembleDebug` ✅ (APK at `app/build/outputs/apk/debug/app-debug.apk`)

**Checkpoint 3 (fall-back known-good):** debug APK builds. Settings rename + chip row + 3 key fields all in source. Bambu/Snapmaker regressions intact in tests. **Do not install — phone not connected.**

### Milestone 4 — Anycubic + Elegoo (Ultralight chip family)

Goal: two more vendors via the new chip family.

Steps:
- Step 2 (MifareUltralightReader — try 36 pages + fallback per Q-U14b-6=A)
- Step 4 (AnycubicProcessor — header check `0x10..0x14 == 0x7B 0x00 0x65 0x00`, ASCII fields, ARGB color, weight-by-length)
- Step 5 (ElegooProcessor + ElegooTables — fetch OpenRFID at HEAD, capture SHA in NOTICE per Q-U14b-3=B; EE marker, big-endian uint16 reads, subtype lookup)
- Step 8 — wire up Ultralight branch of dispatcher
- Step 10 (NfcRepository classifier extended to Ultralight: `MifareUltralight` in techList → vendor candidate)
- Add both processors to registry
- New tests: `AnycubicProcessorTest` (PLA happy path, header mismatch, "+" suffix), `ElegooProcessorTest` (happy path, EE marker mismatch, unknown subtype rejection), `MifareUltralightReaderTest` (full read, short read, IOException mid-read)

Verify:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ — target **441/441** (Δ +12)
- `assembleDebug` ✅ (don't install)

**Checkpoint 4**: 3 of 4 vendors live (QIDI, Anycubic, Elegoo).

### Milestone 5 — Creality

Heaviest one. AES-ECB key derivation, encrypted/plaintext detection, AES-256 decrypt, lookup table.

Steps:
- Step 6 (CrealityProcessor + CrealityTables)
  - HKDF-shaped: `derivedKey = AES-256-ECB-encrypt(saltKey, uid ⨉ 4)`, take first 6 bytes → `keys_a[1] = derivedKey`, all other keys default `0xFF`. Document in a Kotlin comment that this isn't RFC 5869 HKDF — match upstream OpenRFID naming.
  - Sector 1 read (offset 64..112). Encrypted vs plaintext check: `data[3] == 0x32` AND `data[17] in {0x30, 0x23}` → plaintext. Else AES-256-ECB decrypt with `crealityEncKey`. If no enc key → log warning, return null (Q-U14b-4=A).
  - Parse ASCII fields: batch (0..3), date (3..8), supplier (8..12), material (12..17), color (17..24), length (24..28), serial (28..34).
  - Material lookup → temps via `CREALITY_FILAMENT_CODE_TO_DATA`.
  - Weight by length: 330m=1000g, 165m=500g, 80m=250g, else 1000g.
- Add to registry
- New test `CrealityProcessorTest`: plaintext-tag good case, encrypted-tag good case (with key), encrypted-no-key graceful skip, unknown material code rejection

Verify:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ — target **451/451** (Δ +10)
- `assembleDebug` ✅ (don't install)

**Checkpoint 5**: all 4 vendors live. Tag dispatch works end-to-end in tests.

### Milestone 6 — Adapter parity tests + NOTICE + README

Steps:
- `BambuProcessorAdapterTest` + `SnapmakerProcessorAdapterTest` — one happy-path each per Q-U14b-5=A
- `NfcRepositoryVendorParseTest` extended with QIDI/Anycubic/Elegoo/Creality cases (registry-dispatch end-to-end)
- Step 15 (NOTICE attribution block citing OpenRFID at the SHA captured in M4; README "What's new" snippet adding QIDI/Anycubic/Elegoo/Creality to the supported-tags list)

Verify:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ — target **~455/455** or higher (Δ +4 from M5)
- `assembleDebug` ✅ (don't install)

**Checkpoint 6**: source code complete + debug APK built. NOTICE attributes OpenRFID. README mentions new vendors.

### Milestone 7 — Full build matrix (release + AAB)

User's policy is debug + release + AAB always pass green before they touch the install gate. Run the full matrix now:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) KEYSTORE_PASSWORD=$(cat ~/spoolpainter-keystore.pwd) \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease \
  > /tmp/sp-builds.log 2>&1
```

Then summarise:
- `compileDebugKotlin` ✅
- `testDebugUnitTest` ✅ — final count
- `assembleDebug` ✅ + size (`stat -f %z app/build/outputs/apk/debug/app-debug.apk` → divide by 1MB)
- `assembleRelease` ✅ + size (target ~7 MB after R8 minify)
- `bundleRelease` ✅ + size (target ~7.7 MB AAB)

If `assembleRelease` fails on R8 / ProGuard rules, log the failure to `u14b-progress.md` and stop — user will look at it. Do **not** loosen ProGuard rules speculatively; the fix usually needs a one-line `-keep` (see UI-34 in `aidlc-docs/ui-followups.md` for the Retrofit-shape fix that landed during U10). The new vendor processors are pure Kotlin (no reflection-via-annotations) so R8 should leave them alone — but flag it if it doesn't.

**Checkpoint 7**: full build matrix green. Source code complete. APK + AAB sitting in `app/build/outputs/`. Ready for user's install gate.

### Things to NOT do this session

- ❌ `adb install` / `adb logcat` / any on-device command — phone not connected.
- ❌ Close-out commit + push — user owns the commit per `[[feedback_aidlc_unit_close_out_commit]]`.
- ❌ Mark `aidlc-state.md` U14b checkbox `[x]` — status stays open until user signs off.
- ❌ Update `MEMORY.md` — only the user adds memories.
- ❌ Push to `origin/v2` — `d885ac2` is unpushed; user owns the push timing.
- ❌ Bump `versionCode` / `versionName` — that goes with the close-out commit, user-driven.

## Reference inventory

| What | Where |
|---|---|
| Plan | `aidlc-docs/construction/plans/u14b-vendor-expansion-code-generation-plan.md` |
| Existing Bambu impl | `app/src/main/java/com/spoolpainter/app/hardware/nfc/BambuFormat.kt` |
| Existing Snapmaker impl | `app/src/main/java/com/spoolpainter/app/hardware/nfc/SnapmakerFormat.kt` |
| Existing dispatcher | `app/src/main/java/com/spoolpainter/app/hardware/nfc/TagFormatParser.kt` |
| Existing MifareClassic reader | `app/src/main/java/com/spoolpainter/app/hardware/nfc/MifareClassicReader.kt` |
| Settings screen | `app/src/main/java/com/spoolpainter/app/ui/screens/settings/` |
| OpenRFID upstream (GPL-3.0) | `https://github.com/suchmememanyskill/OpenRFID/tree/main/src/tag/{qidi,anycubic,elegoo,creality}` |
| Test fixtures | `app/src/test/java/com/spoolpainter/app/hardware/nfc/` (BambuFormatTest, SnapmakerFormatTest as references) |
| keystore password | `~/spoolpainter-keystore.pwd` |
| keystore file | `~/spoolpainter-release-key.jks` |
| adb | `/opt/homebrew/share/android-commandlinetools/platform-tools/adb` (NOT used this session — phone not connected) |
| Java 17 | `/usr/libexec/java_home -v 17` |

## Build commands (copy/paste-ready)

```bash
# from project root — fast iteration
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug

# end-of-session full matrix (Milestone 7)
JAVA_HOME=$(/usr/libexec/java_home -v 17) KEYSTORE_PASSWORD=$(cat ~/spoolpainter-keystore.pwd) \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease

# DO NOT run during this session (no phone connected):
# /opt/homebrew/share/android-commandlinetools/platform-tools/adb devices
# /opt/homebrew/share/android-commandlinetools/platform-tools/adb install -r ...
```

For long compile/test, write to a log file and tail:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest > /tmp/sp-tests.log 2>&1
tail -n 30 /tmp/sp-tests.log
grep -E "Tests run|FAILED|BUILD" /tmp/sp-tests.log
```

## Progress log convention

After each milestone, append to `aidlc-docs/construction/u14b-vendor-expansion/code/u14b-progress.md`:

```
## Milestone N — title (timestamp)
- compile: ✅ / ❌ (link to error if failed)
- tests: ✅ N/N total / ❌ failures
- APK: ✅ + size / ❌
- install: ✅ / ❌ / skipped
- notes: anything surprising
```

The user reads this artefact when they're back. Be terse — bullets only.

## Memory references applicable

- `[[feedback_no_em_dash]]` — never use `—` in user-facing copy (Settings preface line, chip row labels, snackbars). Code comments + plan files OK.
- `[[feedback_aidlc_unit_close_out_commit]]` — close-out commit is per-unit, by user, after install gate.
- `[[feedback_no_offset_modifier]]` — don't use `Modifier.offset` in Compose. Use Spacer/Arrangement/Alignment instead.
- `[[reference_adb_path]]` — `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`, on PATH via `~/.zshrc`.

## Hard guards (REPEAT — these are deny rules in settings.local.json)

- ❌ `git commit` — user owns the commit
- ❌ `git push` — same
- ❌ `git reset --hard` — destructive
- ❌ `git rebase` — destructive
- ❌ `git checkout --` / `git restore` — destructive
- ❌ `git clean -f` — destructive
- ❌ `adb uninstall com.spoolpainter.app` — would nuke user's prod app (debug variant fine)

If you hit a permission denial that's NOT in the deny list, it's likely an allow-list miss. Document it in `u14b-progress.md` and work around with allowed commands.

## What success looks like when user comes back

**Goal state**:
- Source code complete: 4 new vendor processors + registry + adapter ports + Settings UI rename + chip row
- ~455/455 unit tests green
- `app-debug.apk` built (size ~65 MB — debug isn't minified)
- `app-release.apk` built + signed (target ~7 MB after R8)
- `app-release.aab` built + signed (target ~7.7 MB)
- NOTICE updated with OpenRFID attribution at a captured commit SHA
- README "What's new" / supported-vendors snippet updated
- `u14b-progress.md` shows ✅ for milestones 1–7 with terse bullets per milestone
- Working tree dirty against `origin/v2` — no commits, no pushes, no `versionCode` bump

User can immediately:
1. Plug phone in → `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Open Settings → confirm "Vendor tag support" header + chip row
3. Tap a QIDI/Anycubic/Elegoo/Creality tag → confirm prefill
4. Walk the §5.1 install-gate scenarios from the plan
5. Bump versionCode + author the close-out commit

**Stop and document if**:
- Any milestone goes red (compile, test, or build)
- Environment broken (no keystore, no Java 17, network out for OpenRFID curl)
- Working-tree fingerprint doesn't match on resume
- An unexpected file is present that you didn't create
- A test fails and the cause isn't obvious from one read of the failure

In all those cases, append an entry to `u14b-progress.md` describing what's wrong and what you tried, then stop. Do not destructive-fix. The user will look at the log when they're back.

## What landed in the prep session (before reset)

- `aidlc-docs/aidlc-state.md` — U14b unchecked entry; Current Stage refers to this resume artefact
- `aidlc-docs/audit.md` — three new entries (open / Q&A locked / session reset)
- `aidlc-docs/construction/plans/u14b-vendor-expansion-code-generation-plan.md` — full Part 1 plan with locked Q&A
- `aidlc-docs/construction/u14b-vendor-expansion/code/u14b-session-resume.md` — this file
- `.claude/settings.local.json` — permission expansion + deny list

No code changes. No commits. Working tree dirty per fingerprint above. Ready for autonomous execution on resume.
