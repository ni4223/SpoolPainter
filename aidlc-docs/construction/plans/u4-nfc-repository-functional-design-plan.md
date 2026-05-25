# U4 — Functional Design Plan

**Stage**: CONSTRUCTION → Per-Unit Loop → Functional Design (U4)
**Unit**: U4 — NFC Repository + State
**Source artefacts**:
- `aidlc-docs/inception/application-design/unit-of-work.md` §3-U4
- `aidlc-docs/inception/application-design/components.md` §2.1 (`NfcRepository`), §2.4 (cross-component contracts), §3 (interface signatures), §2.7 (`NfcModule`)
- `aidlc-docs/inception/application-design/component-methods.md` §1 (`NfcRepository`), §13 (`OpenSpoolPayloadParser`)
- `aidlc-docs/inception/application-design/services.md` §2 (Read flow), §3 (Create-and-Pair / write-then-verify), §5 (Two-tag), §6 (Vendor UID-only), §7 (Raw write)
- `aidlc-docs/inception/requirements/requirements.md` FR-1.1 / FR-1.2 / FR-1.3 / FR-3.1 / FR-3.4 / FR-4.2 / FR-4.4 / FR-4.5 / FR-4.7 / FR-4.9 / FR-6.2 / FR-11.1 / FR-11.2 / FR-14.1 / FR-14.2 / NFR-1.1 / NFR-1.4 / NFR-3.3 / NFR-6
- `aidlc-docs/inception/user-stories/stories.md` S-1.1 / S-1.3 / S-3.4 / S-4.4 / S-4.6 / S-4.7 / S-4.8 / S-6.2 / S-11.1 / S-11.2

---

## 1. Unit Context (Step 1)

### 1.1 Scope (locked by Units Generation)

- **`NfcAdapterWrapper`** — thin wrapper around `android.nfc.NfcAdapter` (lives in `hardware/nfc`):
  - `fun isAvailable(): Boolean` — adapter present + enabled.
  - `fun enableForegroundDispatch(activity: ComponentActivity)`.
  - `fun disableForegroundDispatch(activity: ComponentActivity)`.
  - `suspend fun read(tag: Tag): RawTagRead` — extracts UID + (if NDEF) raw NDEF message bytes off the main thread.
  - `suspend fun writeNdef(tag: Tag, message: NdefMessage)` — writes NDEF; throws on failure.
  - `suspend fun readNdef(tag: Tag): NdefMessage?` — re-read for verify.
  - The wrapper owns blocking-API → coroutine bridging (`withContext(Dispatchers.IO)`) so the repository never wraps blocking calls itself (per `component-methods.md` convention).
- **`NfcRepository`** (`@Singleton`, Hilt) — single point of access for all NFC interactions:
  - `val state: StateFlow<NfcResult>` — sealed: `Idle | Reading | Writing | Verifying | Success(uid, classification) | Error(reason, cause?)` (NFR-1.4).
  - `val lastSeenTag: StateFlow<TagBuffer?>` — TTL-cleared buffer of the most recent tap (any state). `TagBuffer(uid: CardUid, classification: TagClassification, capturedAtEpochMs: Long)`. TTL default 5 s (matches v1's `TAG_MEMORY_DURATION`).
  - `fun attach(activity: ComponentActivity)` — called from `MainActivity.onResume`. Enables foreground dispatch and stores a typed reference for use during writes.
  - `fun detach()` — called from `MainActivity.onPause`. Disables foreground dispatch and clears the activity reference.
  - `suspend fun arm(intent: NfcIntent)` — declares intent for the next tap (Read / Write / Verify); transitions `state` to `Reading` / `Writing` accordingly.
  - `suspend fun consumeLastSeen(intent: NfcIntent): NfcResult?` — if a fresh buffered tap exists (within TTL), execute `intent` against it now and return the result. Otherwise return `null` and caller falls back to `arm`.
  - `suspend fun disarm()` — cancel any armed intent; transition `state` back to `Idle`.
  - `fun onTagDiscovered(tag: Tag)` — called by `MainActivity` from `onNewIntent`. Branches on currently-armed `NfcIntent` and on whether classification or write is required.
  - **Tag classifier** — internal helper that consumes raw NDEF bytes + `Tag.getId()` and produces `(CardUid, TagClassification)`:
    - `Tag.getId()` → `CardUid.fromBytes(...)` (FR-1.1 / FR-1.2 / S-1.1).
    - No NDEF message → `TagClassification.Blank`.
    - NDEF parses as OpenSpool JSON via `OpenSpoolPayloadCodec.fromJson` → `TagClassification.OpenSpool(payload)`.
    - NDEF present but not OpenSpool → `TagClassification.Vendor(reason)`.
  - **Write-then-verify** — implemented inside `arm(Write(...))` (FR-4.4 / FR-4.5 / NFR-6 / S-4.4):
    1. classify the just-tapped tag.
    2. if classification is `Vendor(...)` → emit `Error("vendor-tag protected (FR-4.7)")` and DO NOT write.
    3. encode `OpenSpoolPayload` via `OpenSpoolPayloadCodec.toJson` → wrap as NDEF `application/vnd.openspool+json` MIME record → `wrapper.writeNdef(...)`.
    4. transition `state` to `Verifying`.
    5. re-read NDEF via `wrapper.readNdef(...)` and byte-compare against the message just written; on mismatch emit `Error("verify mismatch")`; on equality emit `Success(uid, classification = OpenSpool(payload))`.
    6. if `expectedUid != null` and the just-tapped UID differs from `expectedUid`, emit `Error("wrong tag UID")` *before* attempting the write.
- **Sealed type completion** — finalise placeholder skeletons from U1:
  - `NfcResult.Success(uid: CardUid, classification: TagClassification)`.
  - `NfcResult.Error(reason: String, cause: Throwable? = null)`.
  - `NfcIntent.Write(payload: OpenSpoolPayload, expectedUid: CardUid? = null)`.
  - `NfcIntent.Verify(expectedPayload: OpenSpoolPayload)`.
- **`MainActivity` lifecycle wiring** — close the U1 `TODO`s:
  - `onResume` → `nfcRepository.attach(this)`.
  - `onPause` → `nfcRepository.detach()`.
  - `onNewIntent(intent)` (and `onCreate`'s launch intent) → forward `Tag` extra to `nfcRepository.onTagDiscovered(tag)`.

### 1.2 Cross-unit consumers (locked by `unit-of-work-dependency.md`)

- **U5 (Read-and-Pair)** consumes `arm(Read)`, `consumeLastSeen(Read)`, `state`, `lastSeenTag` for tag-first vs button-first flows (services.md §2).
- **U6a (Create-and-Pair)** consumes `arm(Write(payload, expectedUid))`, `state` for the write-then-verify path (services.md §3).
- **U6b (Move-on-bind + Two-tag)** consumes `arm(Write(samePayload))` for the second-tag write (services.md §5) — second-tag path reuses the same `arm(Write)` API.
- **U7 (Raw write + Vendor UID-only)** consumes `arm(Write(payload))` for the raw-write side mode (services.md §7) and **never** consumes `arm(Write)` in the vendor UID-only path (services.md §6 — UID is captured via `consumeLastSeen(Read)` only; no NDEF write fires).
- **U9 (Settings + banner)** does **not** consume `NfcRepository` directly — Q-U4-9=A says `arm` reports availability lazily.

### 1.3 Out-of-scope for U4 (deferred)

- `MainViewModel` integration (calling `arm` / `consumeLastSeen`) — U5 / U6a / U6b / U7.
- Vendor decoding (FR-1.4 / FR-3.5) — v2.1 / U11. U4 surfaces vendor tags as `TagClassification.Vendor(reason)` only.
- Per-vendor key handling (FR-9.4 / NFR-3.4) — v2.1 / U12.
- Banner UI for `NfcAdapter == null` or NFC disabled — Settings copy lands in U9.
- Instrumented Android tests against real hardware — manual verification at U5 milestone install gate (per `unit-of-work.md` §3-U4 exit criteria).

---

## 2. Plan Steps (checkboxes)

### 2.1 Wire-level types & sealed-type completion
- [x] 2.1.1 Lock `NfcResult` sealed-type final shape: `Idle | Reading | Writing | Verifying | Success(CardUid, TagClassification) | Error(reason: String, cause: Throwable? = null)`.
- [x] 2.1.2 Lock `NfcIntent` sealed-type final shape: `Read | Write(payload: OpenSpoolPayload, expectedUid: CardUid? = null) | Verify(expectedPayload: OpenSpoolPayload)`.
- [x] 2.1.3 Lock `TagBuffer(uid: CardUid, classification: TagClassification, capturedAtEpochMs: Long)` shape and TTL semantics.
- [x] 2.1.4 Lock `RawTagRead(uid: CardUid, ndef: NdefMessage?)` shape (internal; passed from `NfcAdapterWrapper` to `NfcRepository`).

### 2.2 Tag classifier business rules
- [x] 2.2.1 Define classifier rules: `ndef == null` → `Blank`; `OpenSpoolPayloadCodec.fromJson(text)` returns `Decoded(payload)` → `OpenSpool(payload)`; returns `NotOpenSpool` or `Malformed(reason)` → `Vendor(reason)`. (Source: `OpenSpoolDecodeResult` from U2.)
- [x] 2.2.2 Define MIME-record acceptance (Q-U4-1=A modified per Q-U4-2=C coupling): prefer the first record whose MIME type is `application/vnd.openspool+json`; accept `application/json` as a forward-compat alias. No `text/plain` fallback. Records that match neither → `Vendor("non-OpenSpool NDEF")`. Empty/zero-length payload bytes → `Vendor("empty NDEF payload")`.
- [x] 2.2.3 Define empty-UID handling: `Tag.getId()` returning a zero-length array → emit `Error("zero-length UID — non-NFC-A tag?")`. Should not happen on real Android NFC-A/B/F/V hardware but defensive.

### 2.3 State machine transitions
- [x] 2.3.1 Document allowed transitions from each state on each event (`arm`, `consumeLastSeen`, `disarm`, `onTagDiscovered`). Table form, exhaustive (lands in `business-logic-model.md`).
- [x] 2.3.2 Define `arm(Read)` from `Idle`: → `Reading`.
- [x] 2.3.3 Define `arm(Read)` from non-`Idle`: implicit `disarm()` first → `Idle` → `Reading`. (Idempotent for callers that re-arm rapidly.)
- [x] 2.3.4 Define `arm(Write(...))` from `Idle`: → `Writing`.
- [x] 2.3.5 Define `consumeLastSeen(Read)`: if `lastSeenTag` is fresh (within TTL) AND `state == Idle` → execute Read against the buffered tag, transition `state` to `Success(uid, classification)`, **clear** the buffer (one-shot consumption), return the result. If `state != Idle` (already armed) → return `null`.
- [x] 2.3.6 Define `consumeLastSeen(Write(...))` (Q-U4-3=A): NOT supported — writes always require a fresh tap. Returns `null`.
- [x] 2.3.7 Define `consumeLastSeen(Verify(...))` (Q-U4-3=A): same as Write — not supported; returns `null`. Verify only fires inline with a Write protocol.
- [x] 2.3.8 Define `disarm`: from `Reading | Writing | Verifying` → `Idle`; from `Success | Error` → `Idle` (clears terminal state); from `Idle` → no-op.
- [x] 2.3.9 Define `onTagDiscovered` while in `Reading`: classify → emit `Success(uid, classification)`. Always populates `lastSeenTag` first (TTL bookkeeping), regardless of armed state.
- [x] 2.3.10 Define `onTagDiscovered` while in `Writing`: run write-then-verify protocol (§2.4 below).
- [x] 2.3.11 Define `onTagDiscovered` while in `Idle | Success | Error`: classify and populate `lastSeenTag` only; do NOT change `state`. (This enables the tag-first flow in services.md §2.)
- [x] 2.3.12 Define `onTagDiscovered` reentrancy: a second tap arriving while `Writing | Verifying` is in flight is dropped silently — only the first tap's outcome resolves the current intent.

### 2.4 Write-then-verify protocol (NFR-6 / FR-4.4 / FR-4.5)
- [x] 2.4.1 Define `arm(Write(payload, expectedUid))` precondition check: classify the tag first. If `expectedUid != null` and tag UID ≠ `expectedUid` → emit `Error("wrong tag UID — expected <hex>, got <hex>")`. Do NOT advance.
- [x] 2.4.2 Define vendor-tag protection (FR-4.7 / S-4.6 / FR-14.2): if classification is `Vendor(reason)` → emit `Error("vendor-tag protected (FR-4.7): <reason>")`. Do NOT advance.
- [x] 2.4.3 Define payload encoding (Q-U4-2=C): `OpenSpoolPayloadCodec.toJson(payload)` → wrap as NDEF MIME record `application/vnd.openspool+json`. Single record per message.
- [x] 2.4.4 Define write-call: `wrapper.writeNdef(tag, message)` — throws on failure. Catch → emit `Error("write failed: <message>", cause)`. Do NOT advance to verify.
- [x] 2.4.5 Define verify-call: transition `state` to `Verifying`; `wrapper.readNdef(tag)` → byte-compare against `message`. Mismatch → emit `Error("verify mismatch")`. Equality → emit `Success(uid, OpenSpool(payload))`.
- [x] 2.4.6 Define byte-comparison granularity (Q-U4-4=A): exact `NdefMessage.toByteArray()` equality. No padding tolerance.
- [x] 2.4.7 Define `consumeLastSeen` interaction with verify: a buffered tap from before `arm(Write)` MUST NOT short-circuit a write — writes always require a fresh tap (§2.3.6).

### 2.5 TTL & buffer hygiene
- [x] 2.5.1 Lock TTL = 5000 ms (Q-U4-5=A — matches v1 `NfcController.TAG_MEMORY_DURATION`). Configurable via constructor for tests.
- [x] 2.5.2 Define eviction strategy: TTL is checked **only** at read time (`consumeLastSeen` and read-side `lastSeenTag.value`). No background coroutine clears the buffer — avoids leaking a clock dependency across the repository surface. The `StateFlow` may temporarily expose a stale value; consumers must check `capturedAtEpochMs` against the injected `Clock`.
- [x] 2.5.3 Define `consumeLastSeen` one-shot semantics: a successful consumption clears the buffer (sets `lastSeenTag.value = null`). A subsequent fresh tap repopulates.
- [x] 2.5.4 Define multi-tap during `Idle`: each tap overwrites `lastSeenTag` (latest wins); old buffer is discarded.

### 2.6 Foreground-dispatch lifecycle
- [x] 2.6.1 Define `attach(activity)`: store typed reference; call `wrapper.enableForegroundDispatch(activity)`. Idempotent — repeated `attach` with the same activity is a no-op; with a different activity, the old one is detached first.
- [x] 2.6.2 Define `detach()`: if attached → `wrapper.disableForegroundDispatch(activity)`; clear reference. Idempotent.
- [x] 2.6.3 Define handling of NFC-disabled / no-adapter device (Q-U4-9=A): `wrapper.isAvailable()` returns false → `enableForegroundDispatch` is a silent no-op. `state` reports `Idle`; `arm(...)` in this state synchronously emits `Error("NFC not available")`.
- [x] 2.6.4 Define detach-while-writing: `state == Writing | Verifying` at detach time → transition to `Error("activity paused mid-write — retry on next tap")`. Protects against zombie write-then-verify sessions across config changes / process death.

### 2.7 Error mapping (NFR-1.1 / NFR-1.4)
- [x] 2.7.1 Define error reasons enum-of-strings: `"NFC not available"`, `"vendor-tag protected (FR-4.7): <detail>"`, `"wrong tag UID — expected <hex>, got <hex>"`, `"write failed: <detail>"`, `"verify mismatch"`, `"zero-length UID — non-NFC-A tag?"`, `"activity paused mid-write — retry on next tap"`. Free-form `cause: Throwable?` carries underlying detail for logs.
- [x] 2.7.2 Define logging hygiene: errors with a `cause` log via `android.util.Log.w` (debug-only via `BuildConfig.DEBUG` guard, mirroring `SpoolmanApiFactory` pattern from U3) — never via `println` — and do not surface stack traces to the UI.

### 2.8 Hilt wiring
- [x] 2.8.1 Define `NfcModule` providers:
  - `@Provides @Singleton fun provideNfcAdapter(@ApplicationContext ctx: Context): NfcAdapter? = NfcAdapter.getDefaultAdapter(ctx)`.
  - `@Provides @Singleton fun provideNfcAdapterWrapper(adapter: NfcAdapter?, @IoDispatcher dispatcher: CoroutineDispatcher): NfcAdapterWrapper`.
  - `@Provides @Singleton fun provideClock(): Clock = Clock.System` (Q-U4-8=A).
- [x] 2.8.2 Define `NfcRepository` injection: `@Inject constructor(wrapper: NfcAdapterWrapper, @AppScope scope: CoroutineScope, @IoDispatcher dispatcher: CoroutineDispatcher, clock: Clock)` — reuses qualifiers from `Qualifiers.kt` (U1/U3).
- [x] 2.8.3 Define `Clock` injection (Q-U4-8=A): `kotlinx.datetime.Clock` provided via `NfcModule` so tests can swap in a fixed clock.

### 2.9 Test plan
- [x] 2.9.1 Define test surface: `NfcRepository` against a `FakeNfcAdapterWrapper` (test-only support fake exposing `simulateTap(uid, ndef)` / `simulateWriteFailure(...)` / `simulateVerifyMismatch(...)`). Uses `kotlinx.coroutines.test.TestScope` + `StandardTestDispatcher` (NfcAdapterWrapper is suspend-only; no Robolectric needed for repository-level tests).
- [x] 2.9.2 Test cases — state-machine transitions: `Idle → Reading → Success(uid, Blank | OpenSpool | Vendor) | Error`; `Idle → Writing → Verifying → Success | Error`; reentrant tap during write; `disarm` from each state.
- [x] 2.9.3 Test cases — TTL: buffered tap consumed within TTL → returns result + clears buffer; tap consumed after TTL → returns null (buffer ignored); multi-tap overwrites buffer.
- [x] 2.9.4 Test cases — write-then-verify: happy path with byte-equal readback; verify mismatch; vendor-tag rejection; wrong-UID rejection; write throw → Error.
- [x] 2.9.5 Test cases — classifier: blank tag (no NDEF); OpenSpool JSON in `application/vnd.openspool+json`; OpenSpool JSON in `application/json` forward-compat; non-OpenSpool MIME (`text/plain`); malformed JSON; empty NDEF payload bytes.
- [x] 2.9.6 Test cases — `attach` / `detach` lifecycle: idempotent reattach; detach during write transitions to `Error`; arm on no-adapter device → `Error("NFC not available")`.
- [x] 2.9.7 Out-of-scope tests: real Android NFC instrumented tests (deferred to U5 milestone install gate).

### 2.10 Brownfield migration (Q-U4-6=A)
- [x] 2.10.1 Disposition of v1 `NfcManager`, `NfcController`, `NfcHandler`: **delete** in U4 Code Generation. v1 surfaces are not consumed by v2 code (verified — only intra-package references remain).
- [x] 2.10.2 Verify no references to v1 NFC types from outside `hardware/nfc/`. (Done — grep confirmed only intra-package use.)
- [x] 2.10.3 Document explicit non-actions: v1 `FilamentSpool.fromOpenSpool` was already removed in U2; v1 `MainScreen` Compose surfaces are owned by U5/U6a (not touched in U4).
- [x] 2.10.4 Document doc-drift fix-up (Q-U4-11=A): `component-methods.md` §1 references `OpenSpoolPayloadParser`, but U2 shipped `OpenSpoolPayloadCodec` (an `object`). U4 uses the codec directly; the doc reference is recorded as a forward fix-up note in `business-logic-model.md` (no separate refactor in U4).

---

## 3. Open Questions (answered)

All 11 questions answered via "Go Go Go!!" → recommended option for each. See §4 Decision Records below.

### Q-U4-1 — Tag classifier MIME-record preference
[Answer]: **A — modified per Q-U4-2=C coupling.** Accept `application/vnd.openspool+json` (primary, what v2 writes) and `application/json` (forward-compat). `text/plain` v1 tags classify as `Vendor`.

### Q-U4-2 — Write payload MIME type
[Answer]: **C — `application/vnd.openspool+json`.**

### Q-U4-3 — `consumeLastSeen` for Write / Verify
[Answer]: **A — Write/Verify always require fresh tap.**

### Q-U4-4 — Verify byte-comparison strictness
[Answer]: **A — exact `NdefMessage.toByteArray()` equality.**

### Q-U4-5 — TTL value
[Answer]: **A — 5000 ms (matches v1).**

### Q-U4-6 — v1 NFC types migration
[Answer]: **A — big-bang delete in U4.**

### Q-U4-7 — `NfcIntent.Verify` shipping in U4
[Answer]: **A — ship as data class with full impl in U4.**

### Q-U4-8 — Clock injection
[Answer]: **A — inject `kotlinx.datetime.Clock`.**

### Q-U4-9 — `NFC not available` surfacing
[Answer]: **A — lazy via `state` `Error` on `arm`.**

### Q-U4-10 — `MainActivity.onNewIntent` wiring
[Answer]: **A — `MainActivity` calls `nfcRepository.onTagDiscovered(tag)` directly.**

### Q-U4-11 — `OpenSpoolPayloadParser` vs `OpenSpoolPayloadCodec`
[Answer]: **A — use `OpenSpoolPayloadCodec` directly; record doc-drift fix-up note.**

---

## 4. Decision Records

User approval token: **"Go Go Go!!"** — locks in the **Recommended** option for every question.

| Question | Selected option | Rationale |
|---|---|---|
| Q-U4-1 | **A** (modified per coupling note with Q-U4-2=C) | Classifier accepts the OpenSpool MIME family — `application/vnd.openspool+json` (primary, what v2 writes) plus `application/json` (forward-compat for any tooling that emits generic JSON). `text/plain` v1 tags classify as `Vendor("non-OpenSpool NDEF")` — strict v2 contract. |
| Q-U4-2 | **C** | `application/vnd.openspool+json` is the canonical OpenSpool MIME type; explicit, future-proof, no clash with generic JSON tools. Aligns with FR-14.1's canonical-format intent. |
| Q-U4-3 | **A** | Writes always require a fresh, deliberate tap. Stale buffer reuse risks writing to the wrong tag. |
| Q-U4-4 | **A** | Exact `NdefMessage.toByteArray()` equality — strictest; surfaces tag-firmware quirks instead of silently masking them. Errors are recoverable via re-tap. |
| Q-U4-5 | **A** | TTL = 5000 ms; matches v1 `TAG_MEMORY_DURATION` so existing tap-first muscle memory survives. |
| Q-U4-6 | **A** | Big-bang delete `NfcManager.kt`, `NfcController.kt`, `NfcHandler.kt` — only intra-package references remain (verified by grep); same precedent as U3's `SpoolmanService` deletion. |
| Q-U4-7 | **A** | Ship `NfcIntent.Verify(expectedPayload)` with full impl — small marginal cost, locks the public surface for U6b without re-opening U4. |
| Q-U4-8 | **A** | Inject `kotlinx.datetime.Clock` — testable; matches U3's pattern of injecting state-bearing abstractions (`@AppScope` / `@IoDispatcher` already in `Qualifiers.kt`). |
| Q-U4-9 | **A** | Lazy reporting via `state` `Error("NFC not available")` on `arm`; `attach`/`detach` are silent no-ops. Aligns with services.md §2/§3 result-flow expectations. |
| Q-U4-10 | **A** | `MainActivity.onNewIntent` extracts `Tag` extra and calls `nfcRepository.onTagDiscovered(tag)` directly. No router class for one method. |
| Q-U4-11 | **A** | Use `OpenSpoolPayloadCodec` directly — already an `object`, stateless and dependency-free. The `OpenSpoolPayloadParser` reference in `component-methods.md` §1 is doc-drift; record it inline as a forward fix-up. |

---

## 5. Approval Gate (Step 7-8)

After answers are recorded:
1. Generate Functional Design Part 2 artefacts at `aidlc-docs/construction/u4-nfc-repository/functional-design/{business-logic-model,business-rules,domain-entities}.md`. **(in progress)**
2. Present the "Functional Design Complete" workflow message.
3. Wait for explicit user approval before advancing to Code Generation.
