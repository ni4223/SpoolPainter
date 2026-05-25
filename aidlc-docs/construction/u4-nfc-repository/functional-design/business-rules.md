# U4 — Business Rules

**Stage**: CONSTRUCTION → Functional Design (U4)
**Companion**: `domain-entities.md`, `business-logic-model.md`

---

## BR-U4-CLS — Tag classifier rules

| ID | Rule | Source |
|---|---|---|
| BR-U4-CLS-1 | If `RawTagRead.ndef == null` → `TagClassification.Blank`. | FR-1.1 / S-1.1 |
| BR-U4-CLS-2 | NDEF message has zero records → `TagClassification.Vendor("non-OpenSpool NDEF")`. | FR-4.7 |
| BR-U4-CLS-3 | Find the first NDEF record whose `tnf == NdefRecord.TNF_MIME_MEDIA` and whose MIME type (lowercased) equals `application/vnd.openspool+json` **or** `application/json`. If none, classify as `Vendor("non-OpenSpool NDEF")`. | Q-U4-1=A (Q-U4-2=C coupling) |
| BR-U4-CLS-4 | Empty / zero-length payload bytes on the matched record → `Vendor("empty NDEF payload")`. | Q-U4-1=A |
| BR-U4-CLS-5 | Decode the matched record's payload bytes as UTF-8 → call `OpenSpoolPayloadCodec.fromJson(text)`. Map `Decoded(payload)` → `OpenSpool(payload)`. Map `NotOpenSpool` → `Vendor("not OpenSpool JSON")`. Map `Malformed(reason)` → `Vendor("malformed JSON: <reason>")`. | FR-3.4 / FR-4.7 / S-4.6 |
| BR-U4-CLS-6 | UTF-8 decode failure (record bytes are not valid UTF-8) → `Vendor("non-UTF-8 NDEF payload")`. | FR-4.7 |

---

## BR-U4-UID — UID extraction rules

| ID | Rule | Source |
|---|---|---|
| BR-U4-UID-1 | `Tag.getId()` is canonicalised via `CardUid.fromBytes(bytes)` before any other processing. Lowercase hex, no separators, raw bytes (variable length). | FR-1.2 / S-1.1 |
| BR-U4-UID-2 | Zero-length UID byte array → emit `NfcResult.Error("zero-length UID — non-NFC-A tag?")`; do NOT proceed to classification. | Defensive |

---

## BR-U4-SM — State machine transitions

| ID | Current state | Event | Next state | Notes |
|---|---|---|---|---|
| BR-U4-SM-1 | `Idle` | `arm(Read)` | `Reading` | — |
| BR-U4-SM-2 | non-`Idle` | `arm(Read)` | `Reading` | implicit `disarm` first |
| BR-U4-SM-3 | `Idle` | `arm(Write(p, expectedUid))` | `Writing` | — |
| BR-U4-SM-4 | non-`Idle` | `arm(Write(...))` | `Writing` | implicit `disarm` first |
| BR-U4-SM-5 | `Idle` | `arm(Verify(p))` | `Verifying` | — |
| BR-U4-SM-6 | non-`Idle` | `arm(Verify(...))` | `Verifying` | implicit `disarm` first |
| BR-U4-SM-7 | `Reading | Writing | Verifying | Success | Error` | `disarm()` | `Idle` | clears terminal state too |
| BR-U4-SM-8 | `Idle` | `disarm()` | `Idle` | no-op |
| BR-U4-SM-9 | `Reading` | `onTagDiscovered(tag)` | `Success(uid, classification)` | classifier runs first; populates `lastSeenTag` regardless |
| BR-U4-SM-10 | `Writing` | `onTagDiscovered(tag)` | runs §BR-U4-WV-* | terminal `Success` or `Error` |
| BR-U4-SM-11 | `Verifying` | `onTagDiscovered(tag)` | runs §BR-U4-VRF-* | terminal `Success` or `Error` |
| BR-U4-SM-12 | `Idle | Success | Error` | `onTagDiscovered(tag)` | unchanged | classify + populate `lastSeenTag` only |
| BR-U4-SM-13 | `Writing | Verifying` | second `onTagDiscovered(tag)` while in flight | unchanged (drop) | only the first tap resolves the current intent |

---

## BR-U4-CL — `consumeLastSeen` rules

| ID | Rule | Source |
|---|---|---|
| BR-U4-CL-1 | `consumeLastSeen(Read)` — if `state ∈ {Idle, Success, Error}` AND `lastSeenTag != null` AND `(clock.now - capturedAtEpochMs) ≤ TTL` → emit `Success(uid, classification)` based on the buffered tap; clear `lastSeenTag` (one-shot); return the result. (Revised 2026-05-25 — terminal states accept consumption; original rule rejected anything non-`Idle` and broke the tag-first flow after a prior successful read.) | services.md §2 (tag-first flow) |
| BR-U4-CL-2 | `consumeLastSeen(Read)` — if `state ∈ {Reading, Writing, Verifying}` → return `null` (an armed handler is in flight; consuming the buffer would race). | Q-U4-3=A; revised 2026-05-25 |
| BR-U4-CL-3 | `consumeLastSeen(Read)` — if buffer expired (TTL exceeded) → return `null`; do NOT clear the buffer (let the next tap overwrite). | §2.5.2 |
| BR-U4-CL-4 | `consumeLastSeen(Write(...))` → return `null` always. | Q-U4-3=A |
| BR-U4-CL-5 | `consumeLastSeen(Verify(...))` → return `null` always. | Q-U4-3=A |
| BR-U4-CL-6 | TTL is checked at read time; no background coroutine evicts. | §2.5.2 |
| BR-U4-CL-7 | Multi-tap during `Idle` overwrites `lastSeenTag` (latest wins). | §2.5.4 |

---

## BR-U4-WV — Write-then-verify protocol (NFR-6 / FR-4.4 / FR-4.5)

| ID | Rule | Source |
|---|---|---|
| BR-U4-WV-1 | On `onTagDiscovered` while `state == Writing` (with armed `Write(payload, expectedUid)`): step (a) `RawTagRead = wrapper.read(tag)`; step (b) classify; step (c) populate `lastSeenTag`. | §2.4.1 |
| BR-U4-WV-2 | If `expectedUid != null` and `RawTagRead.uid != expectedUid` → emit `Error("wrong tag UID — expected ${expectedUid.value}, got ${RawTagRead.uid.value}")`; do NOT advance to write. | FR-4.4 (UID match) |
| BR-U4-WV-3 | If classification is `Vendor(reason)` → emit `Error("vendor-tag protected (FR-4.7): $reason")`; do NOT advance to write. | FR-4.7 / S-4.6 / FR-14.2 |
| BR-U4-WV-4 | Encode payload: `OpenSpoolPayloadCodec.toJson(payload)` → wrap as `NdefMessage(arrayOf(NdefRecord.createMime("application/vnd.openspool+json", json.toByteArray(UTF_8))))`. Single record per message. | Q-U4-2=C / FR-14.1 |
| BR-U4-WV-5 | Call `wrapper.writeNdef(tag, message)`. Catch any `Throwable` → emit `Error("write failed: ${t.message}", t)`; do NOT advance to verify. | FR-4.4 |
| BR-U4-WV-6 | On successful write: transition `state` to `Verifying`. | NFR-6 |
| BR-U4-WV-7 | Verify: `wrapper.readNdef(tag)` → byte-compare `readback.toByteArray()` against `message.toByteArray()` for exact equality. Mismatch → emit `Error("verify mismatch")`. Equality → emit `Success(RawTagRead.uid, OpenSpool(payload))`. | Q-U4-4=A / FR-4.5 / NFR-6 |
| BR-U4-WV-8 | Verify call throws (e.g., tag removed mid-verify) → emit `Error("verify mismatch", cause)`; treat as mismatch from the user's perspective (recoverable via re-tap). | Defensive |

---

## BR-U4-VRF — Standalone Verify protocol

| ID | Rule | Source |
|---|---|---|
| BR-U4-VRF-1 | On `onTagDiscovered` while `state == Verifying` (with armed `Verify(expectedPayload)`): same as BR-U4-WV-7 but compare against the encoded `expectedPayload` (re-encode via `toJson` → MIME wrap → `toByteArray()`) — no write step is performed. | Q-U4-7=A |
| BR-U4-VRF-2 | `Verify` against a `Vendor` tag → `Error("vendor-tag protected (FR-4.7): $reason")` (defensive — Verify on a vendor tag is meaningless because no v2 write would ever produce that payload). | FR-4.7 |

---

## BR-U4-LF — Foreground-dispatch lifecycle

| ID | Rule | Source |
|---|---|---|
| BR-U4-LF-1 | `attach(activity)` — store reference; if `wrapper.isAvailable()` → `wrapper.enableForegroundDispatch(activity)`. Else silent no-op. | Q-U4-9=A |
| BR-U4-LF-2 | `attach(activity)` is idempotent: same activity twice → no-op. Different activity → `wrapper.disableForegroundDispatch(prior); wrapper.enableForegroundDispatch(activity)`. | §2.6.1 |
| BR-U4-LF-3 | `detach()` — if attached AND available → `wrapper.disableForegroundDispatch(activity)`; clear stored reference. Idempotent. | §2.6.2 |
| BR-U4-LF-4 | If `state in { Writing, Verifying }` at `detach()` time → emit `Error("activity paused mid-write — retry on next tap")`. | §2.6.4 |
| BR-U4-LF-5 | `arm(...)` while `wrapper.isAvailable() == false` → emit `Error("NFC not available")` synchronously; do NOT change `state` to `Reading`/`Writing`/`Verifying` (the user pressed Read/Write but there's no chip; surface the constraint immediately). | Q-U4-9=A |

---

## BR-U4-ERR — Error reason vocabulary

The `NfcResult.Error.reason` field uses one of the following stable
strings. New reasons require a doc + plan update.

| Reason | Trigger | Cause carried? |
|---|---|---|
| `"NFC not available"` | `arm(...)` called while wrapper has no enabled adapter (BR-U4-LF-5). | no |
| `"vendor-tag protected (FR-4.7): <detail>"` | Write or Verify against a `Vendor`-classified tag (BR-U4-WV-3 / BR-U4-VRF-2). | no |
| `"wrong tag UID — expected <hex>, got <hex>"` | Write with `expectedUid != null` and tap UID differs (BR-U4-WV-2). | no |
| `"write failed: <detail>"` | `wrapper.writeNdef` throws (BR-U4-WV-5). | yes |
| `"verify mismatch"` | Byte-compare fails OR `wrapper.readNdef` throws during verify (BR-U4-WV-7 / BR-U4-WV-8). | yes when readNdef threw |
| `"zero-length UID — non-NFC-A tag?"` | `Tag.getId()` returned an empty array (BR-U4-UID-2). | no |
| `"activity paused mid-write — retry on next tap"` | `detach()` while `Writing | Verifying` (BR-U4-LF-4). | no |

Logging hygiene (BR-U4-LOG-1 below) handles `cause` separately.

---

## BR-U4-LOG — Logging hygiene

| ID | Rule | Source |
|---|---|---|
| BR-U4-LOG-1 | Errors carrying a `cause` log via `android.util.Log.w("NfcRepository", reason, cause)` and only when `BuildConfig.DEBUG`. Mirrors U3's `SpoolmanApiFactory` pattern. Never `println`; never `Log.e` (which would survive ProGuard log-stripping in release per NFR-5). | NFR-5 / NFR-1.1 |
| BR-U4-LOG-2 | `cause` is never echoed into UI-facing strings — `reason` is the only UI-facing surface. | NFR-1.1 |

---

## BR-U4-TTL — TTL constants

| ID | Rule |
|---|---|
| BR-U4-TTL-1 | `TTL_MS = 5_000L` (Q-U4-5=A). |
| BR-U4-TTL-2 | TTL is overridable via `NfcRepository`'s constructor parameter (test-only convenience; production code uses default). |

---

## Traceability summary

| Requirement / Story | Rules covering it |
|---|---|
| FR-1.1 / S-1.1 (UID extraction) | BR-U4-UID-1 |
| FR-1.2 / FR-1.3 (UID canonical form) | BR-U4-UID-1 |
| FR-3.4 / S-3.4 (OpenSpool prefill) | BR-U4-CLS-1, BR-U4-CLS-5 |
| FR-3.4 / FR-11.2 (blank/unparseable form) | BR-U4-CLS-3, BR-U4-CLS-5 |
| FR-4.4 / S-4.4 (write OpenSpool) | BR-U4-WV-1..BR-U4-WV-6 |
| FR-4.5 / NFR-6 (write-then-verify) | BR-U4-WV-7, BR-U4-WV-8 |
| FR-4.7 / FR-14.2 / S-4.6 (vendor protection) | BR-U4-WV-3, BR-U4-VRF-2 |
| FR-6.2 / S-6.2 (two-tag identical bytes) | BR-U4-WV-4 (single canonical encoding) + BR-U4-WV-7 (byte equality) |
| NFR-1.4 (NFC state observability) | BR-U4-SM-* |
| NFR-1.1 (no exception propagation) | BR-U4-ERR, BR-U4-LOG-2 |
| NFR-3.3 (no NFC tag content persistence) | TagBuffer is in-memory; not covered by a rule because nothing writes it to disk. |
| NFR-5 (release log stripping) | BR-U4-LOG-1 |
