# U4 — Code Generation Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Code Generation (U4)
**Unit**: U4 — NFC Repository + State
**Source artefacts**:
- `aidlc-docs/construction/plans/u4-nfc-repository-functional-design-plan.md` (Decision Records §4)
- `aidlc-docs/construction/u4-nfc-repository/functional-design/{domain-entities,business-rules,business-logic-model}.md`
- `aidlc-docs/inception/application-design/component-methods.md` §1
- `aidlc-docs/inception/application-design/components.md` §2.1, §2.7

**Convention reminder (from U2/U3 close-out)**: builds require
`JAVA_HOME = JDK 17`. Durable fix deferred to U10.

---

## §1 Build / dependency setup

- [x] 1.1 Add `kotlinx-datetime` to `gradle/libs.versions.toml` (`[versions]` + `[libraries]`):
  - `kotlinxDatetime = "0.6.1"`
  - `kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }`
- [x] 1.2 Wire the dep in `app/build.gradle.kts` under v2 dependencies block: `implementation(libs.kotlinx.datetime)`.
- [x] 1.3 No other runtime deps. Test deps already in place (`kotlinx-coroutines-test`, `turbine`, `mockk`, `org.json:json` from U3).

---

## §2 Sealed-type completion (U1 placeholder → U4 final)

- [x] 2.1 `app/src/main/java/com/spoolpainter/app/domain/primitives/NfcResult.kt`:
  - Add `data class Success(val uid: CardUid, val classification: TagClassification) : NfcResult`.
  - Add `data class Error(val reason: String, val cause: Throwable? = null) : NfcResult`.
  - Drop the U1 forward-reference comment.
- [x] 2.2 `app/src/main/java/com/spoolpainter/app/domain/primitives/NfcIntent.kt`:
  - Add `data class Write(val payload: OpenSpoolPayload, val expectedUid: CardUid? = null) : NfcIntent`.
  - Add `data class Verify(val expectedPayload: OpenSpoolPayload) : NfcIntent`.
  - Drop the U1 forward-reference comment.

---

## §3 Hardware NFC layer

- [x] 3.1 `app/src/main/java/com/spoolpainter/app/hardware/nfc/RawTagRead.kt` — `internal data class RawTagRead(uid: CardUid, ndef: NdefMessage?)`.
- [x] 3.2 `app/src/main/java/com/spoolpainter/app/hardware/nfc/TagBuffer.kt` — `data class TagBuffer(uid, classification, capturedAtEpochMs)`.
- [x] 3.3 `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcAdapterWrapper.kt`:
  - Class with `@Inject constructor(adapter: NfcAdapter?, @IoDispatcher dispatcher: CoroutineDispatcher)` (open for test fakes).
  - `fun isAvailable(): Boolean = adapter != null && adapter.isEnabled`.
  - `fun enableForegroundDispatch(activity: ComponentActivity)`. PendingIntent built with `FLAG_MUTABLE` (matches v1 NfcController).
  - `fun disableForegroundDispatch(activity: ComponentActivity)`.
  - `suspend fun read(tag: Tag): RawTagRead = withContext(dispatcher) { ... }`.
    - Build `CardUid` via `CardUid.fromBytes(tag.id)`; throw `IllegalStateException("zero-length UID")` if id is empty (caller maps to `Error`).
    - Acquire `Ndef.get(tag)`; if null → `RawTagRead(uid, ndef = null)`.
    - `connect()` → read `cachedNdefMessage`/`ndefMessage` → `close()`.
  - `suspend fun writeNdef(tag: Tag, message: NdefMessage) = withContext(dispatcher) { ... }`. Throws `IOException` / `FormatException` from `Ndef.writeNdefMessage`.
  - `suspend fun readNdef(tag: Tag): NdefMessage? = withContext(dispatcher) { ... }`. Same shape as the read but returns null when no NDEF.

- [x] 3.4 `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcRepository.kt`:
  - `@Singleton` `class NfcRepository @Inject constructor(wrapper, @AppScope scope, @IoDispatcher dispatcher, clock: Clock, ttlMs: Long = TTL_MS_DEFAULT)`.
  - `companion object { const val TTL_MS_DEFAULT = 5_000L }`.
  - `private val _state = MutableStateFlow<NfcResult>(NfcResult.Idle); val state = _state.asStateFlow()`.
  - `private val _lastSeenTag = MutableStateFlow<TagBuffer?>(null); val lastSeenTag = _lastSeenTag.asStateFlow()`.
  - `private val mutex = Mutex()` to serialise armed-intent transitions.
  - `private var armedIntent: NfcIntent? = null`.
  - `private var attached: ComponentActivity? = null`.
  - `fun attach(activity)` / `fun detach()` per BR-U4-LF-1..BR-U4-LF-4.
  - `suspend fun arm(intent: NfcIntent)` per BR-U4-SM-1..BR-U4-SM-6 + BR-U4-LF-5.
  - `suspend fun consumeLastSeen(intent: NfcIntent): NfcResult?` per BR-U4-CL-1..BR-U4-CL-7.
  - `suspend fun disarm()` per BR-U4-SM-7..BR-U4-SM-8.
  - `fun onTagDiscovered(tag: Tag)` — launches a coroutine on `scope` (IO context) to: read raw → classify → branch on `armedIntent`; per BR-U4-SM-9..BR-U4-SM-13, BR-U4-WV-*, BR-U4-VRF-*.
  - `private fun classify(raw: RawTagRead): TagClassification` — implements BR-U4-CLS-1..BR-U4-CLS-6.
  - `private fun encodePayloadMessage(payload: OpenSpoolPayload): NdefMessage` — implements BR-U4-WV-4 using `NdefRecord.createMime("application/vnd.openspool+json", json.toByteArray(UTF_8))`.
  - Logging via `android.util.Log.w("NfcRepository", reason, cause)` guarded by `BuildConfig.DEBUG` (BR-U4-LOG-1).

- [x] 3.5 Update `app/src/main/java/com/spoolpainter/app/di/NfcModule.kt`:
  - Replace stub with `@Provides @Singleton` for `NfcAdapter?`, `NfcAdapterWrapper`, `Clock` (kotlinx.datetime).
  - Drop the "lands in U4" comment.

- [x] 3.6 Brownfield deletion (Q-U4-6=A):
  - Delete `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcManager.kt`.
  - Delete `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcController.kt`.
  - Delete `app/src/main/java/com/spoolpainter/app/hardware/nfc/NfcHandler.kt`.

---

## §4 MainActivity wiring

- [x] 4.1 `app/src/main/java/com/spoolpainter/app/ui/activity/MainActivity.kt`:
  - Inject `NfcRepository` (`@Inject lateinit var nfcRepository: NfcRepository`).
  - Replace `// TODO U4: nfcRepository.attach(this)` with `nfcRepository.attach(this)`.
  - Replace `// TODO U4: nfcRepository.detach()` with `nfcRepository.detach()`.
  - Add `onNewIntent(intent)` override that extracts the `Tag` extra (TIRAMISU+ vs legacy) and calls `nfcRepository.onTagDiscovered(tag)`.
  - Also dispatch `onCreate`'s launch intent to `tryDispatchNfcIntent(intent)` so cold launches via `ACTION_NDEF_DISCOVERED` still feed the buffer.

---

## §5 Tests (`app/src/test/java/com/spoolpainter/app/`)

All tests use `kotlinx.coroutines.test.runTest` + `StandardTestDispatcher`
+ `mockk` where appropriate. `FakeNfcAdapterWrapper` is a hand-rolled
test fake (no Robolectric).

- [x] 5.1 `support/FakeNfcAdapterWrapper.kt` (`internal open class`):
  - Same surface as `NfcAdapterWrapper`.
  - `var available: Boolean`.
  - `var attachedActivity: ComponentActivity?`.
  - `simulateNextRead(uid: CardUid, ndef: NdefMessage?)` and `simulateReadThrow(t: Throwable)`.
  - `simulateNextWrite(throwable: Throwable? = null)`.
  - `simulateNextReadNdef(ndef: NdefMessage?, throwable: Throwable? = null)`.
- [x] 5.2 `support/MutableClock.kt` — `class MutableClock(var nowMs: Long = 0) : Clock { override fun now() = Instant.fromEpochMilliseconds(nowMs) }`.
- [x] 5.3 `support/NdefMessages.kt` — helpers to build NDEF messages with specific MIME types, payload bytes; build a real OpenSpool payload + canonical NDEF for round-trip tests.
- [x] 5.4 `hardware/nfc/NfcRepositoryStateMachineTest.kt` — BR-U4-SM transitions across `arm` / `consumeLastSeen` / `disarm` (≈10 cases).
- [x] 5.5 `hardware/nfc/NfcRepositoryClassifierTest.kt` — BR-U4-CLS-* cases: blank tag, OpenSpool MIME `application/vnd.openspool+json`, OpenSpool MIME `application/json` forward-compat, `text/plain` rejected as Vendor, malformed JSON, empty payload bytes, non-UTF-8 bytes (≈8 cases).
- [x] 5.6 `hardware/nfc/NfcRepositoryWriteVerifyTest.kt` — BR-U4-WV-1..BR-U4-WV-8 cases: happy path, vendor rejection, wrong UID rejection, write throw, verify mismatch, verify throw (≈8 cases).
- [x] 5.7 `hardware/nfc/NfcRepositoryConsumeLastSeenTest.kt` — BR-U4-CL-1..BR-U4-CL-7 cases: fresh buffer consumed → cleared, expired buffer ignored, multi-tap latest wins, Write/Verify return null, non-Idle state returns null (≈7 cases).
- [x] 5.8 `hardware/nfc/NfcRepositoryLifecycleTest.kt` — BR-U4-LF-1..BR-U4-LF-5 cases: idempotent attach, detach during write → Error, arm on no-adapter → Error("NFC not available"), reattach with different activity (≈6 cases).
- [x] 5.9 `hardware/nfc/NfcRepositoryStandaloneVerifyTest.kt` — BR-U4-VRF-1..BR-U4-VRF-2 cases (≈4 cases).
- [x] 5.10 `hardware/nfc/NfcRepositoryUidExtractionTest.kt` — BR-U4-UID-1..BR-U4-UID-2 cases (≈3 cases).

Estimate: ≈ 50 new tests (added on top of U1=4 + U2=64 + U3=64 → 132 baseline).

---

## §6 Verification

- [x] 6.1 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin --console=plain`.
- [x] 6.2 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --console=plain` — expect baseline + ~50 = ~182 tests pass.
- [x] 6.3 `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug --console=plain` — expect ~33 MB APK (kotlinx-datetime adds <300 KB).
- [x] 6.4 Brownfield invariant grep: `grep -rn "NfcManager\|NfcController\|NfcHandler" app/src/main` → expect 0 matches outside whatever U4 may import legitimately (none expected).
- [x] 6.5 Brownfield invariant grep: `grep -rn "OpenSpoolData\|class SpoolmanService" app/src/main` → expect 0 matches (carry-over invariant from U2/U3).
- [x] 6.6 `grep -rn "TODO U4" app/src` → expect 0 matches (U1 placeholders all closed).
- [x] 6.7 No milestone install gate for U4 per `unit-of-work.md` §2 (gates at U1/U5/U6/U10).

---

## §7 Story / requirement coverage

| ID | Code surface | Test surface |
|---|---|---|
| FR-1.1 / S-1.1 | `NfcAdapterWrapper.read` calls `CardUid.fromBytes(tag.id)`. | `NfcRepositoryUidExtractionTest`. |
| FR-1.2 / FR-1.3 | `CardUid.fromBytes` (U2). | (covered in U2 tests). |
| FR-3.4 / S-3.4 | `NfcRepository.classify` → `OpenSpool(payload)`. | `NfcRepositoryClassifierTest`. |
| FR-4.4 / S-4.4 | `NfcRepository.handleWrite`. | `NfcRepositoryWriteVerifyTest`. |
| FR-4.5 / NFR-6 | `NfcRepository.runVerify`. | `NfcRepositoryWriteVerifyTest`. |
| FR-4.7 / FR-14.2 / S-4.6 | `NfcRepository.handleWrite` rejection on `Vendor`. | `NfcRepositoryWriteVerifyTest::vendor_tag_rejected`. |
| FR-6.2 / S-6.2 | `arm(Write(samePayload))` produces byte-identical NDEF (single canonical encoding). | `NfcRepositoryWriteVerifyTest::two_consecutive_writes_produce_identical_bytes`. |
| FR-11.1 / S-11.1 | `OpenSpool(payload)` classification. | `NfcRepositoryClassifierTest`. |
| FR-11.2 / S-11.2 | `Vendor(reason)` / `Blank` classification. | `NfcRepositoryClassifierTest`. |
| NFR-1.1 | `Error` enum-of-strings. | (asserted in every error-path test). |
| NFR-1.4 | `state: StateFlow<NfcResult>`. | `NfcRepositoryStateMachineTest`. |
| NFR-3.3 | TagBuffer in-memory only. | (no persistent storage — code review only). |
| NFR-5 | `Log.w` guarded by `BuildConfig.DEBUG`. | (release-build verified at U10). |
| NFR-6 | Write-then-verify in `arm(Write)`. | `NfcRepositoryWriteVerifyTest`. |

---

## §8 Out-of-scope guards (do not modify in U4)

- v1 `MainScreen` Compose surfaces (owned by U5/U6a).
- `MainViewModel` flow methods (owned by U5/U6a/U6b/U7).
- Vendor decoding (U11).
- Settings / banner UI (U9).
- Real-hardware tests (manual at U5 milestone install gate).
- Spoolman code (no edits — only consumed by future units).
- v1 `FilamentSpool.fromOpenSpool` etc. (already removed in U2).
- Kotlin compile target / minSdk changes.
- ProGuard rules (U10).

---

## §9 Summary artefact

- [x] 9.1 Write `aidlc-docs/construction/u4-nfc-repository/code/u4-summary.md` covering: files created/modified/deleted, story coverage, public interfaces, build verification numbers, exit-criteria checklist, forbidden-patterns audit.

---

## §10 Approval gate

- [x] 10.1 Present "Code Generation Complete" workflow message → wait for explicit user approval.
- [x] 10.2 Mark U4 [x] in `aidlc-state.md`; append audit.md entries; create close-out commit per `unit-of-work.md` §2.1 (HEREDOC, no `--amend`, no `--no-verify`, no push).
