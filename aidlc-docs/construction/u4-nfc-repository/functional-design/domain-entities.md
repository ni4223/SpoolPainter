# U4 — Domain Entities

**Stage**: CONSTRUCTION → Functional Design (U4)
**Source plan**: `aidlc-docs/construction/plans/u4-nfc-repository-functional-design-plan.md`

---

## 1. Sealed-type completion

### 1.1 `NfcResult` (final, supersedes U1 placeholder)

```kotlin
sealed interface NfcResult {
    data object Idle : NfcResult
    data object Reading : NfcResult
    data object Writing : NfcResult
    data object Verifying : NfcResult
    data class Success(val uid: CardUid, val classification: TagClassification) : NfcResult
    data class Error(val reason: String, val cause: Throwable? = null) : NfcResult
}
```

- `Idle`, `Reading`, `Writing`, `Verifying` are transient transport states (NFR-1.4).
- `Success(uid, classification)` is terminal and surfaces both the UID (for Spoolman lookup) and the tag's classification (for branching).
- `Error(reason, cause?)` is terminal; `reason` is a stable enum-of-strings value (see `business-rules.md` §7); `cause` is an opaque carrier for debug logs.

### 1.2 `NfcIntent` (final, supersedes U1 placeholder)

```kotlin
sealed interface NfcIntent {
    data object Read : NfcIntent
    data class Write(val payload: OpenSpoolPayload, val expectedUid: CardUid? = null) : NfcIntent
    data class Verify(val expectedPayload: OpenSpoolPayload) : NfcIntent
}
```

- `Write.expectedUid` is non-null when a flow has already captured a UID (e.g., the user tapped, the form filled in, then the user pressed Write while still holding the same tag) and wants the repository to reject a different tag.
- `Verify` rides on the back of an in-progress write (Q-U4-7=A — full impl in U4) but is also exposed as a standalone intent so U6b's two-tag flow can defensively re-verify a tag that was just written.

### 1.3 `TagBuffer`

```kotlin
data class TagBuffer(
    val uid: CardUid,
    val classification: TagClassification,
    val capturedAtEpochMs: Long,
)
```

- Backs `NfcRepository.lastSeenTag: StateFlow<TagBuffer?>`.
- TTL semantics in `business-rules.md` §5.

### 1.4 `RawTagRead` (internal — `NfcAdapterWrapper` ↔ `NfcRepository`)

```kotlin
internal data class RawTagRead(
    val uid: CardUid,
    val ndef: NdefMessage?, // null = blank tag (no NDEF formatting / empty NDEF)
)
```

- Not part of the public surface.
- `uid` is canonicalised inside the wrapper via `CardUid.fromBytes(tag.id)`.

---

## 2. Existing entities consumed (no schema changes)

| Entity | Source | U4 use |
|---|---|---|
| `CardUid` | U2 — `domain/primitives/CardUid.kt` | UID canonicalisation; comparison key for `expectedUid`. |
| `TagClassification` | U2 — `domain/primitives/TagClassification.kt` | Classifier output; drives `Success(uid, classification)`. |
| `OpenSpoolPayload` | U2 — `domain/models/OpenSpoolPayload.kt` | Write payload; round-trip test target; verify equality target. |
| `OpenSpoolPayloadCodec` | U2 — `domain/primitives/OpenSpoolPayloadCodec.kt` | `fromJson` for classifier; `toJson` for write. |
| `OpenSpoolDecodeResult` | U2 — `domain/primitives/OpenSpoolDecodeResult.kt` | Branching for classifier MIME-record processing. |

---

## 3. New types in U4

| Type | Package | Visibility | Purpose |
|---|---|---|---|
| `NfcAdapterWrapper` | `hardware/nfc` | `class` (Hilt-injectable) | Thin wrapper around `android.nfc.NfcAdapter`; isolates blocking NFC API. |
| `NfcRepository` | `hardware/nfc` | `class` (`@Singleton`, Hilt-injectable) | Public NFC surface; sealed-state machine; consumed by every flow unit. |
| `RawTagRead` | `hardware/nfc` | `internal data class` | Wrapper → repository payload. |
| `TagBuffer` | `hardware/nfc` | `data class` | TTL-backed last-seen tag buffer (in-memory, not persisted). |

`NfcModule` providers are configuration, not domain entities — see `business-logic-model.md` §6.

---

## 4. Forward-fix-up note (Q-U4-11=A)

`aidlc-docs/inception/application-design/component-methods.md` §1 declares
`NfcRepository`'s constructor with an `OpenSpoolPayloadParser` collaborator.
U2 shipped `OpenSpoolPayloadCodec` (an `object`, not an injectable class)
that already covers parse + serialise. **U4 wires `OpenSpoolPayloadCodec`
directly** instead of introducing a wrapper class.

This is a documentation-drift item, not a behavioural change. Recorded
here so future readers of `component-methods.md` know the U4 source-of-
truth deviates by one constructor parameter.

---

## 5. Story coverage

| Story | Entity / contract surface |
|---|---|
| S-1.1 (UID extraction) | `CardUid.fromBytes` (U2) called inside `NfcAdapterWrapper.read` to populate `RawTagRead.uid` → `NfcResult.Success.uid`. |
| S-1.3 (UID display) | `Success.uid` exposes canonical hex; UI consumes via `CardUid.value`. |
| S-3.4 (read prefill — OpenSpool path) | `Success(uid, OpenSpool(payload))` carries the parsed payload for U5 form prefill. |
| S-4.4 (write-then-verify) | `Verifying` transient state + `Success` / `Error("verify mismatch")` terminal states. |
| S-4.6 (vendor-tag protection) | `Error("vendor-tag protected (FR-4.7): <detail>")` produced before any write call. |
| S-4.7 (raw write) | Same `arm(Write)` API; raw-write use case in U7 doesn't change U4's contract. |
| S-4.8 (vendor UID-only pair) | Read-only `Success(uid, Vendor(reason))` — U7 use case takes over from there with no NDEF write. |
| S-6.2 (two-tag identical payload) | `arm(Write(samePayload))` for second tag; verify enforces byte-equality with prior write. |
| S-11.1 / S-11.2 (existing-tag display) | Classifier surfaces `OpenSpool(payload)` vs `Vendor(reason)` so UI can branch. |

NFR coverage:
- NFR-1.1 — `Error` reasons are user-facing, no exception propagation.
- NFR-1.4 — `state: StateFlow<NfcResult>` is the single observable surface; no callbacks, no event bus.
- NFR-3.3 — no NFC tag content persisted on device (U4 scope; only in-memory `TagBuffer`).
- NFR-6 — write-then-verify is implemented inside `arm(Write(...))` (see `business-rules.md` §4).
