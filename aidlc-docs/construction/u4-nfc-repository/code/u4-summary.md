# U4 — NFC Repository: Code Generation Summary

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation Part 2 (executed)
**Source plans**:
- `aidlc-docs/construction/plans/u4-nfc-repository-functional-design-plan.md`
- `aidlc-docs/construction/plans/u4-nfc-repository-code-generation-plan.md`
**Functional design**:
- `aidlc-docs/construction/u4-nfc-repository/functional-design/{domain-entities,business-rules,business-logic-model}.md`

---

## 1. Files

### Created (12)

#### Source (5)
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcAdapterWrapper.kt` — thin wrapper around `android.nfc.NfcAdapter`. `read` / `writeRecords` / `readRecords` exchange `RawTagRead` / `List<NdefRecordView>` so the repository never touches `NdefMessage` directly. `withContext(@IoDispatcher)` for blocking calls.
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt` — `@Singleton`. Public surface: `state`, `lastSeenTag`, `attach`, `detach`, `arm`, `consumeLastSeen`, `disarm`, `onTagDiscovered`. Internal `handleTag` runs the full classifier + write-then-verify protocol. Mutex-serialised state transitions; `kotlinx.datetime.Clock` for TTL; `@AppScope` for fire-and-forget tap handling.
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NdefRecordView.kt` — pure data view of an NDEF record (`tnf`, `type`, `payload`). The `TNF_MIME_MEDIA` constant is duplicated as a literal so JVM unit tests don't load the Android stub.
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/RawTagRead.kt` — `(uid: CardUid, records: List<NdefRecordView>?)`.
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/TagBuffer.kt` — TTL-keyed last-seen buffer.

#### Test (7)
- `app/src/test/java/com/spoolpainter/app/hardware/nfc/FakeNfcAdapterWrapper.kt` — hand-rolled fake (subclass of `NfcAdapterWrapper`) with `simulateRead`, `simulateReadThrow`, `simulateWriteFailure`, `simulateReadback`, `simulateReadbackEchoesWritten`, `simulateReadbackThrow`.
- `app/src/test/java/com/spoolpainter/app/hardware/nfc/MutableClock.kt` — controllable `kotlinx.datetime.Clock` for TTL tests.
- `app/src/test/java/com/spoolpainter/app/hardware/nfc/NfcTestSupport.kt` — sample payloads, MIME-record builders, repository factory.
- `NfcRepositoryStateMachineTest.kt` (10 cases) — BR-U4-SM-* coverage.
- `NfcRepositoryClassifierTest.kt` (10 cases) — BR-U4-CLS-* coverage.
- `NfcRepositoryWriteVerifyTest.kt` (10 cases) — BR-U4-WV-* + FR-6.2 byte-identical-second-write invariant.
- `NfcRepositoryConsumeLastSeenTest.kt` (7 cases) — BR-U4-CL-* coverage.
- `NfcRepositoryLifecycleTest.kt` (7 cases) — BR-U4-LF-* coverage.
- `NfcRepositoryStandaloneVerifyTest.kt` (4 cases) — BR-U4-VRF-* coverage.
- `NfcRepositoryUidExtractionTest.kt` (2 cases) — BR-U4-UID-* coverage.

Total: **50 new test cases** (cumulative: 4 U1 + 64 U2 + 64 U3 + 50 U4 = **182**).

### Modified (5)
- `app/src/main/java/com/spoolpainter/app/domain/primitives/NfcResult.kt` — finalised: added `Success(uid, classification)` and `Error(reason, cause?)`; dropped U1 forward-reference comment.
- `app/src/main/java/com/spoolpainter/app/domain/primitives/NfcIntent.kt` — finalised: added `Write(payload, expectedUid?)` and `Verify(expectedPayload)`; dropped U1 forward-reference comment.
- `app/src/main/java/com/spoolpainter/app/di/NfcModule.kt` — replaced stub with `@Provides @Singleton` for `NfcAdapter?`, `NfcAdapterWrapper`, `Clock` (kotlinx.datetime).
- `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt` — closed U1 `TODO`s: `attach(this)` / `detach()` in resume/pause; added `onNewIntent` + `onCreate` intent dispatch to `nfcRepository.onTagDiscovered(tag)`.
- `app/build.gradle.kts` — `testOptions.unitTests.isReturnDefaultValues = true` so `android.util.Log.w` no-ops in JVM unit tests; added `implementation(libs.kotlinx.datetime)`.
- `gradle/libs.versions.toml` — added `kotlinxDatetime = "0.6.1"` and `kotlinx-datetime` library entry.

### Deleted (3) — Q-U4-6=A big-bang delete
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcManager.kt`.
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcController.kt`.
- `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcHandler.kt`.

---

## 2. Story / requirement coverage

| ID | Code surface | Test surface |
|---|---|---|
| FR-1.1 / S-1.1 | `NfcAdapterWrapper.read` → `CardUid.fromBytes(tag.id)` | `NfcRepositoryUidExtractionTest::Success_carries_canonical_lowercase_hex_UID_via_fromBytes_contract` |
| FR-1.2 / FR-1.3 | (delegated to `CardUid` from U2) | (covered in U2) |
| FR-3.4 / S-3.4 | `NfcRepository.classify` returns `OpenSpool(payload)` | `NfcRepositoryClassifierTest` |
| FR-4.4 / S-4.4 | `NfcRepository.runWriteThenVerify` | `NfcRepositoryWriteVerifyTest::write_happy_path_*` |
| FR-4.5 / NFR-6 | byte-equality verify in `runWriteThenVerify` | `NfcRepositoryWriteVerifyTest::verify_mismatch_*`, `::verify_throw_*` |
| FR-4.7 / FR-14.2 / S-4.6 | vendor-classification rejection in `runWriteThenVerify` / `runStandaloneVerify` | `NfcRepositoryWriteVerifyTest::write_rejects_vendor_*`, `NfcRepositoryStandaloneVerifyTest::arm_Verify_against_vendor_*` |
| FR-6.2 / S-6.2 | single canonical encoding via `encodePayloadRecords` | `NfcRepositoryWriteVerifyTest::two_consecutive_writes_produce_identical_NDEF_bytes` |
| FR-11.1 / S-11.1 | `OpenSpool(payload)` classification | `NfcRepositoryClassifierTest::application_vnd_openspool+json_*` |
| FR-11.2 / S-11.2 | `Vendor` / `Blank` classification | `NfcRepositoryClassifierTest::null_NDEF_*`, `::text_plain_*`, `::malformed_*` |
| NFR-1.1 | `Error` enum-of-strings (BR-U4-ERR) | (asserted in every error test) |
| NFR-1.4 | `state: StateFlow<NfcResult>` | `NfcRepositoryStateMachineTest` |
| NFR-3.3 | TagBuffer in-memory only — no `DataStore` writes | (no persistent storage; code review only) |
| NFR-5 | `Log.w` guarded by `BuildConfig.DEBUG` (BR-U4-LOG-1) | (release-build verified at U10) |
| NFR-6 | write-then-verify in `arm(Write)` | `NfcRepositoryWriteVerifyTest` |

---

## 3. Public interfaces produced (cross-unit boundary)

- `NfcRepository` — primary cross-unit boundary (Q-D1=C). Consumed by U5 / U6a / U6b / U7.
- `NfcResult` — final sealed type. UI consumes via `state.collectAsState`.
- `NfcIntent` — final sealed type. Use cases construct `Read` / `Write(payload, expectedUid?)` / `Verify(expectedPayload)`.
- `TagClassification` — final shape unchanged from U2 (`Blank` / `OpenSpool(payload)` / `Vendor(reason)`).
- `TagBuffer` — read-side only (consumers observe via `lastSeenTag`).
- `RawTagRead` / `NdefRecordView` — public for the wrapper's I/O surface; not consumed outside `hardware/nfc/`.

---

## 4. Build verification

| Command | Result |
|---|---|
| `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` | ✅ (only pre-existing v1 Compose deprecation warnings) |
| `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest` | ✅ **182 / 182** tests pass (4 U1 + 64 U2 + 64 U3 + 50 U4) |
| `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug` | ✅ APK = 34 895 593 B (≈ 33.3 MB; +0.2 MB from U3 baseline due to kotlinx-datetime) |

Brownfield invariants:
- `grep -rn "TODO U4" app/src` → 0 matches (U1 placeholders all closed).
- `grep -rn "NfcManager\|class NfcController\|class NfcHandler" app/src/main` → 0 matches.
- `grep -rn "OpenSpoolData\|class SpoolmanService" app/src/main` → 0 matches (carried over from U2/U3).

---

## 5. Exit criteria checklist

- [x] App compiles.
- [x] Unit tests for `NfcRepository.state` transitions against a fake adapter pass — `NfcRepositoryStateMachineTest` covers 10 cases.
- [x] TTL behaviour on `lastSeenTag` — `NfcRepositoryConsumeLastSeenTest::consume_Read_with_expired_buffered_tap_*`.
- [x] Write-then-verify mismatch yields `Error` and does not advance to `Success` — `NfcRepositoryWriteVerifyTest::verify_mismatch_*`.
- [x] No milestone install gate required for U4 (gates at U1/U5/U6/U10 per `unit-of-work.md` §2).
- [x] Hilt graph compiles with `NfcRepository` injected into `MainActivity`.

---

## 6. Forbidden-patterns audit

| Pattern | U4 status |
|---|---|
| UI calls data sources directly (NFR-1.2) | N/A — U4 has no UI; `MainActivity` only forwards `Tag` extras. |
| ViewModels inject Hilt-scoped Activity types | N/A — U4 ships no ViewModels. |
| Use-cases hold state | N/A — no use cases in U4. |
| Service locator / event bus | None — `NfcRepository` is the single observed surface (Q-CD4=A). |
| Mocked Android types | Tests use a hand-rolled `FakeNfcAdapterWrapper`. `mockk<Tag>` is used as an opaque token only — no behaviour asserted on it. |

---

## 7. Functional-design rule-coverage spot map

| Rule | Where implemented |
|---|---|
| BR-U4-CLS-1..6 | `NfcRepository.classify` |
| BR-U4-UID-1 | `NfcAdapterWrapper.read` (`CardUid.fromBytes`) |
| BR-U4-UID-2 | `NfcAdapterWrapper` (throws on empty bytes via `CardUid.fromBytes` joinToString — empty array → empty hex; mapped to `Error` by `handleTag`'s catch) |
| BR-U4-SM-1..13 | `NfcRepository.arm` / `disarm` / `handleTag` |
| BR-U4-CL-1..7 | `NfcRepository.consumeLastSeen` |
| BR-U4-WV-1..8 | `NfcRepository.runWriteThenVerify` |
| BR-U4-VRF-1..2 | `NfcRepository.runStandaloneVerify` |
| BR-U4-LF-1..5 | `NfcRepository.attach` / `detach` / `arm` |
| BR-U4-ERR | error reasons enumerated in `companion object` strings + literal `Error` constructor calls |
| BR-U4-LOG-1..2 | `NfcRepository.logCause` |
| BR-U4-TTL-1..2 | `companion object TTL_MS_DEFAULT` + secondary `@Inject` constructor |

---

## 8. Forward references (for downstream units)

- U5 (Read-and-Pair): consumes `arm(Read)` / `consumeLastSeen(Read)` / `state` / `lastSeenTag`.
- U6a (Create-and-Pair): consumes `arm(Write(payload, expectedUid))` / `state`.
- U6b (Move-on-bind + Two-tag): consumes `arm(Write(samePayload))` for the second tag; may use `arm(Verify(expected))` defensively.
- U7 (Raw write + Vendor UID-only): consumes `arm(Write(payload))` (raw write); never invokes `arm(Write)` for the vendor UID-only path.
- U9 (Settings + banner): may surface "NFC unavailable" copy by observing `state` — not yet wired (lazy reporting per Q-U4-9=A).

---

## 9. JDK note

Builds require `JAVA_HOME = JDK 17`. Durable fix deferred to U10 per `aidlc-state.md`.

---

## 10. Documentation drift recorded

`component-methods.md` §1 references `OpenSpoolPayloadParser` as a constructor parameter of `NfcRepository`. U2 shipped `OpenSpoolPayloadCodec` (an `object`), which U4 uses directly. This is a doc-drift item, not a behavioural deviation. Sync `component-methods.md` during U10 release polish (Q-U4-11=A).
