# Application Design Plan — SpoolPainter v2

**Stage**: INCEPTION → Application Design
**Source**:
- Requirements: `aidlc-docs/inception/requirements/requirements.md`
- User stories: `aidlc-docs/inception/user-stories/stories.md`
- Reverse engineering: `aidlc-docs/inception/reverse-engineering/`
- Execution plan: `aidlc-docs/inception/plans/execution-plan.md`

**Purpose**: High-level component identification, interface shape, and
service-layer orchestration for SpoolPainter v2. Detailed business rules
are captured later in Functional Design (per-unit, CONSTRUCTION phase).

**How this plan works**: Every step has a checkbox `[ ]`. The Questions
section below uses `[Answer]:` tags. Please fill in each answer with the
letter of your choice (or `X` and a free-form description). When done,
say "answered" / "done" / similar and I will validate, ask any
follow-ups, and only then proceed to generate the Application Design
artifacts.

---

## Plan Steps

### A. Analyse Context (Step 1)
- [ ] A.1 — Re-read `requirements.md` (FR-1..FR-15, NFR-1..NFR-12) and
  identify the v2.0 functional surface.
- [ ] A.2 — Re-read `stories.md` (32 v2.0 stories + 5 v2.1 stories) and
  map them onto candidate components.
- [ ] A.3 — Re-read `reverse-engineering/component-inventory.md` and
  `code-structure.md` to identify which v1 packages survive vs. split.
- [ ] A.4 — Confirm scope: design covers v2.0 components in detail; v2.1
  vendor-decode subsystem captured at interface-level only (so v2.1 work
  has a hook to plug into).

### B. Embed Clarifying Questions (Steps 4–6)
- [ ] B.1 — Generate questions across the five categories (Component
  Identification / Component Methods / Service Layer / Component
  Dependencies / Design Patterns).
- [ ] B.2 — Save questions in this plan file with `[Answer]:` tags.
- [ ] B.3 — Ask user to fill in answers and wait.

### C. Validate Answers (Steps 7–9)
- [ ] C.1 — Read user-provided answers; confirm none are blank.
- [ ] C.2 — Detect ambiguities ("mix of", "depends", "not sure",
  combined options) and append follow-up questions to this file.
- [ ] C.3 — Detect contradictions across answers; append clarifications.
- [ ] C.4 — Loop until all answers are crisp.

### D. Generate Application Design Artifacts (Step 10)
- [ ] D.1 — Generate
  `aidlc-docs/inception/application-design/components.md`
  (component name + purpose + responsibilities + public interface
  shape).
- [ ] D.2 — Generate
  `aidlc-docs/inception/application-design/component-methods.md`
  (method signatures + input/output types + high-level purpose; no
  business rules — those go in Functional Design).
- [ ] D.3 — Generate
  `aidlc-docs/inception/application-design/services.md`
  (service definitions + orchestration patterns — Spoolman create
  chain, NFC write-then-verify, move-on-bind, two-tag flow,
  vendor-tag UID-only opt-in flow).
- [ ] D.4 — Generate
  `aidlc-docs/inception/application-design/component-dependency.md`
  (dependency matrix + communication patterns + data flow + Hilt
  module grouping).
- [ ] D.5 — Generate
  `aidlc-docs/inception/application-design/application-design.md`
  consolidating D.1–D.4 plus a component diagram (Mermaid + the
  same render-to-PNG/SVG fallback used for the workflow flowchart).
- [ ] D.6 — Validate Mermaid diagrams render cleanly via mermaid-cli
  before committing.

### E. Approval Gate (Steps 11–14)
- [ ] E.1 — Log approval prompt with timestamp in `audit.md`.
- [ ] E.2 — Present completion message with artifact summary.
- [ ] E.3 — Wait for explicit user approval (Request Changes / Approve
  & Continue → Units Generation).
- [ ] E.4 — Record user response in `audit.md`.

### F. Update Progress (Step 15)
- [ ] F.1 — Mark Application Design [x] in `aidlc-state.md`.
- [ ] F.2 — Set Current Stage → `Units Generation`.

---

## Candidate Component Map (preview — finalised in D.1 after answers)

This is the working sketch derived from requirements + stories. Your
answers below will tighten or change it.

| Layer | Component | Stories / FRs covered (preview) |
|---|---|---|
| **UI / Compose** | `MainScreen` (single screen, two primary actions per FR-13.1) | FR-13.1, FR-13.4, S-13.1 |
| | `SettingsScreen` (URL, sort, theme; v2.1 vendor keys section) | FR-9, S-9.1–9.3, S-9.4.1/9.4.2 |
| | Bottom-sheet flows (`PairAnotherTagSheet`, `RepairConfirmSheet`, `VendorUidOnlyOptInSheet`, `AddCustomMaterialSheet`, `AddCustomBrandSheet`) | FR-13.2, S-5.2, S-6.1, S-4.8, S-8.3, S-8.4 |
| | Components (`SpoolmanDropdown`, `FilamentForm`, `MaterialPicker`, `BrandPicker`, `ColorPicker`, `TempPanel`, `OfflineBanner`) | FR-3.6, FR-8, FR-10.2 |
| **ViewModel** | `MainViewModel` (read/write/pair flows + UID classification) | almost all v2.0 FRs |
| | `SettingsViewModel` | FR-9, S-9.1–9.3 |
| **Repository** | `NfcRepository` (sealed `NfcResult` state machine, write-then-verify, classification) | NFR-1.4, NFR-6, FR-1, FR-4 |
| | `SpoolmanRepository` (find-by-UID, vendor/filament/spool create chain, PATCH `lot_nr`, vendor/filament list cache) | FR-3.2, FR-5, FR-7, FR-8.3, NFR-7 |
| | `SettingsRepository` (DataStore-backed: URL, sort, theme; v2.1 EncryptedSharedPreferences for vendor keys) | FR-9, NFR-3.1, NFR-3.4 |
| | `MaterialBrandRepository` (presets + user-added; Room iff OD-2 picks it) | FR-8.5, NFR-3.2 |
| **Domain primitives** | `CardUid`, `OpenSpoolPayload`, `TagClassification`, `NfcResult` | NFR-1.4, FR-1.2 |
| **Data source — local** | `MaterialPresetSource` (hardcoded), `BrandPresetSource` (hardcoded), `MaterialBrandLocalStore` (DataStore-Proto or Room) | FR-8.1, FR-8.2, FR-8.5 |
| **Data source — remote** | `SpoolmanApi` (Retrofit interface — extended for vendor/filament/spool POST chain, `lot_nr`-filtered GET, PATCH `lot_nr`); `CardUidEncoding` (encode/decode rules for the `card_uid:<hex>,…,opaque` string Spoolman currently keeps in `lot_nr` — survives the FR-2.4 migration to `extra.card_uid`) | FR-7, FR-3.2, FR-5, FR-2.1, FR-2.2 |
| **Data source — hardware** | `NfcAdapter` wrapper (current `hardware/nfc/` cleaned up under `NfcRepository`) | FR-1, FR-4, FR-6 |
| **Service / orchestration** *(may collapse into Repositories or live as use-cases — see Q-S1/Q-S2)* | `ReadAndPairFlow`, `CreateAndPairFlow`, `MoveOnBindFlow`, `TwoTagFlow`, `VendorUidOnlyPairFlow`, `RawWriteFlow` | FR-3..FR-7 |
| **Cross-cutting / DI** | Hilt `@Singleton` modules: `NetworkModule`, `RepositoryModule`, `DataStoreModule`, `NfcModule` | NFR-2 |
| **v2.1 (interface-level only here)** | `VendorTagDecoder` interface; per-vendor implementations (Bambu / Creality / etc.) under `VendorDecodeRegistry` | FR-1.4, FR-3.5, FR-9.4 |

---

## Questions for User Input

> Fill in each `[Answer]:` tag with the letter of your choice. For
> "Other", choose `X` and write your description after the colon. When
> all questions are answered, say "answered" / "done" and I will
> validate.

### Component Identification

#### Q-CI1 — UI structure: how do the bottom-sheet flows live in the package tree?

A) `ui/screens/main/sheets/` — every bottom sheet lives next to the
   screen that hosts it (FilamentForm, two-tag prompt, re-pair, vendor
   opt-in, add-custom-material/brand all under `MainScreen`'s package).
B) `ui/sheets/` (flat) — all bottom sheets sibling to screens.
C) `ui/components/sheets/` — sheets are reusable Compose components.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C

#### Q-CI2 — Should v2 keep the v1 single `data/local/` package or split presets vs. user-added?

A) Single package `data/local/` with `MaterialDatabase`,
   `BrandDatabase` (presets) plus a new `MaterialBrandLocalStore` (user-
   added) — keeps v1 file naming.
B) Split into `data/local/presets/` (hardcoded) and
   `data/local/userdata/` (user-added) — clearer separation of
   read-only vs. mutable data.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

#### Q-CI3 — Where should `CardUid`, `TagClassification`, `NfcResult` live?

> **Note**: `CardUidEncoding` (the encode/decode rules for the
> `card_uid:<hex>,…,opaque` string Spoolman currently keeps in
> `lot_nr`) is **not** part of this question — it lives next to the
> Spoolman client at `data/remote/spoolman/CardUidEncoding.kt` because
> it only exists to talk to Spoolman. Naming is decoupled from the
> field name (`lot_nr`) so it survives the FR-2.4 migration to
> `extra.card_uid`. Shape:
> ```kotlin
> object CardUidEncoding {
>     data class Decoded(val uids: List<CardUid>, val opaque: String)
>     fun decode(raw: String): Decoded
>     fun encode(uids: List<CardUid>, opaque: String = ""): String
> }
> ```

The remaining three are truly cross-layer (NFC repo + VM + UI all
touch them). Where do they live?

A) `domain/models/` (alongside existing `OpenSpoolData`,
   `FilamentSpool`, etc.) — minimal disruption.
B) `domain/primitives/` (new package, stricter "value type" naming) —
   distinguishes pure value types from richer presentation models.
C) Split: value primitives → `domain/primitives/`; presentation models
   stay in `domain/models/`.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C

#### Q-CI4 — Is the v2.1 `VendorTagDecoder` plugin point modelled in v2.0?

A) **Yes** — define the interface and a no-op `OpenSpoolDecoder` in
   v2.0 so v2.1 just registers more decoders. Adds ~1 day to U4.
B) **No** — v2.0 keeps tag classification simple (blank / OpenSpool /
   foreign); v2.1 introduces the decoder abstraction when it ports
   OpenRFID. Less premature abstraction.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

### Component Methods

#### Q-CM1 — `NfcRepository` API surface

> **Two interaction paths must both work** (preserved from v1):
> 1. **Button-first** — user taps Read/Write → app waits for a tag tap.
> 2. **Tag-first** — user taps a tag *before* pressing a button →
>    the app holds that tag in a short-lived buffer (TTL ~3–5 s) and
>    if the user then taps Read/Write inside the window, the action
>    runs against the held tag. Window expires → buffer cleared.
>
> Whichever option we pick has to support both paths.

A) **Suspend functions only** — `suspend fun read(): NfcResult.Read`,
   `suspend fun write(payload, uid): NfcResult.Write`. Each call is
   one-shot, scoped to a tap.
   ❌ *Doesn't fit*: a suspend function can only wait for the **next**
   tag — it has no way to act on a tag the OS dispatched moments
   before the call. Tag-first path is impossible.

B) **`StateFlow<NfcResult>` only** — caller observes a single hot
   state stream that the NFC adapter pushes into.
   ❌ *Incomplete*: no way for the caller to tell the repo "next tap is
   a Read vs a Write," so neither path works in isolation.

C) **Hybrid arm/disarm** — `StateFlow<NfcResult>` for live state
   (Idle / Reading / Writing / Verifying / Success / Error) plus
   suspend `arm(intent)` / `disarm()` to control which intent
   (read / write+verify) the adapter is currently armed for.
   ⚠️ *Partial fit*: handles button-first cleanly, but doesn't model
   the tag-first buffer.

D) **Hybrid arm/disarm + last-seen-tag buffer (recommended)** —
   builds on C, adds a short-lived "last seen tag" buffer:
   ```kotlin
   class NfcRepository {
       val state: StateFlow<NfcResult>          // live: Idle, Reading, …
       val lastSeenTag: StateFlow<TagBuffer?>   // fresh-tag buffer, TTL-cleared
       suspend fun arm(intent: NfcIntent)       // "next tap = Read/Write"
       suspend fun consumeLastSeen(intent: NfcIntent): NfcResult?
           // if a fresh tag is buffered, act on it now and return the result
       suspend fun disarm()
   }
   ```
   - **Button-first**: `arm(Read)`, observe `state`.
   - **Tag-first**: on Read tap → call `consumeLastSeen(Read)`; if it
     returns non-null we used the buffered tag, otherwise fall through
     to `arm(Read)` and wait.
   - Buffer TTL is configurable (default ~3–5 s); buffer captures
     every tap regardless of armed state, so the button-first user
     never sees ghost tags from earlier tap-first attempts.
   ✅ *Best fit*: explicit model of both interaction paths, composes
   cleanly with write-then-verify (NFR-6) and two-tag flow (FR-6).

X) Other (please describe after `[Answer]:` tag below)

[Answer]: D

#### Q-CM2 — `SpoolmanRepository` — return shape on errors?

A) **`Result<T>` (kotlin.Result)** — every method returns
   `Result<Spool>`, etc. Caller handles `.onFailure { … }`.
B) **Sealed `SpoolmanOutcome<T>`** — `Success(data)`,
   `HttpError(code, message)`, `NetworkError(cause)`,
   `ParseError(cause)` — finer-grained for the banner + retry pattern
   (FR-10.2).
C) **Throw + caller catches** — idiomatic Retrofit; ViewModel wraps
   in try/catch + maps to UiState error.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

#### Q-CM3 — `lot_nr` parsing/serialising — where do these helpers live? — **SUPERSEDED**

> **Superseded by the `CardUidEncoding` extraction.** This question was
> written before Q-CI3's note moved `CardUidEncoding` to
> `data/remote/spoolman/CardUidEncoding.kt`. With that decision, all
> `lot_nr` parse/serialise logic now lives inside `CardUidEncoding` —
> there is no other call site. Treat this question as resolved; no
> separate `LotNr` primitive type is needed.
>
> **User confirmation (resolved)**: "This will be in that encoder in
> primitives? wont it be?" — yes, except it lives in
> `data/remote/spoolman/` not `domain/primitives/`, because it's the
> Spoolman wire-format encoder.

[Answer]: SUPERSEDED — see note above.

#### Q-CM4 — `MainViewModel` shape — one VM or per-flow VM?

A) **Single `MainViewModel`** — exposes one `StateFlow<MainUiState>`
   with sub-states for each flow (read, create, pair-second, re-pair,
   raw-write). Matches v1; one view-model boundary.
B) **Per-flow VMs** — separate `ReadAndPairViewModel`,
   `CreateAndPairViewModel`, etc., that all inject the same
   repositories. Each has its own `StateFlow`.
C) **Hybrid** — `MainViewModel` plus per-bottom-sheet VMs (e.g.,
   `RepairConfirmViewModel`, `VendorOptInViewModel`).
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C

### Service Layer Design

#### Q-S1 — Are use-case / interactor classes a layer between ViewModel and Repository?

A) **No** — VMs call Repositories directly. Orchestration logic lives
   in VM functions (`onWriteTapped`, etc.). Simpler call-graph;
   matches typical small-app MVVM.
B) **Yes — for multi-step flows only** — introduce
   `ReadAndPairUseCase`, `CreateAndPairUseCase`, `MoveOnBindUseCase`,
   `TwoTagUseCase`, `VendorUidOnlyPairUseCase`, `RawWriteUseCase`.
   VMs become thin (just orchestrate state + delegate to the use
   case).
C) **Yes — universally** — every Repository method is wrapped in a
   use-case (overkill for this app, but listed for completeness).
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

#### Q-S2 — Spoolman create chain (FR-7: vendor → filament → spool) — where does it live?

> **Validation note (Round 1)**: Q-S1=B means use-cases exist for
> multi-step flows. Two natural answers given that:
> - **A** — keep the chain *inside* `SpoolmanRepository` as one public
>   method (`createSpoolForNewFilament(...)`), and have the use-cases
>   (`CreateAndPairUseCase`, etc.) call it. Repository is "rich"
>   (encapsulates Spoolman's own multi-step semantics); use-cases stay
>   thin, just orchestrating Spoolman + NFC.
> - **B** — make `CreateSpoolChainUseCase` and have it call three
>   primitive repository methods (`findOrCreateVendor`,
>   `findOrCreateFilament`, `createSpool`). Repository stays thin
>   (one method = one HTTP call); use-cases own all multi-step logic.
>
> A is simpler if you'll only ever assemble these three calls in this
> one order. B is more flexible (e.g., if a future flow only needs to
> resolve a vendor without creating a spool).
>
> My recommendation: **A** — the FR-7 chain is Spoolman's own
> create-or-reuse semantics (FR-7.5 says "reuse existing before
> creating new"), which is a property of the Spoolman wire contract,
> not of any user flow. Keeping it next to the Retrofit interface
> means the rule lives where the wire format lives. The
> `CreateAndPairUseCase` from Q-S1=B then orchestrates
> `SpoolmanRepository.createSpoolForNewFilament(...)` +
> `NfcRepository.arm(Write)` — clean two-step composition.

A) Inside `SpoolmanRepository.createSpoolForNewFilament(...)` — one
   public method, internal sequencing private. **(recommended)**
B) In a dedicated `CreateSpoolChainUseCase` (only meaningful if Q-S1
   = B/C).
C) In the calling VM — VM resolves vendor, filament, then spool by
   calling three repository methods in sequence.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

#### Q-S3 — Move-on-bind two-PATCH flow (FR-5.2)

A) **Repository-level transaction** —
   `SpoolmanRepository.moveCardUid(fromSpool, toSpool, uid)` runs
   both PATCHes; partial failure surfaces as `MoveOnBindOutcome`.
B) **Two repository calls + VM orchestration** — VM sequences the
   PATCHes and decides what to do on partial failure.
C) **Use-case** (only if Q-S1 = B/C) — `MoveOnBindUseCase`.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C

#### Q-S4 — UI ↔ Spoolman caching — where does the in-memory cache (NFR-7.2) live?

A) Inside `SpoolmanRepository` — a `StateFlow<List<Filament>>` and
   `StateFlow<List<Spool>>` invalidated on PATCH/POST.
B) Per-VM — VMs hold their own local cache; refresh on screen
   re-entry.
C) No cache in v2.0 — every screen entry refetches; revisit only if
   real-world latency hurts.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

### Component Dependencies

#### Q-CD1 — `OfflineBanner` (FR-10.2) — where does the "Spoolman reachable?" signal originate?

A) `SpoolmanRepository` exposes `StateFlow<ConnectivityState>` which
   `MainViewModel` and `SettingsViewModel` collect. Banner is a
   passive Compose component reading from VM.
B) Dedicated `ConnectivityRepository` injected separately.
C) Connectivity is implicit — every VM action infers it from the
   most recent Spoolman call's outcome.
X) Other (please describe after `[Answer]:` tag below)

[Answer]:  A, also we do not want this to be too much for people who do not want to use spoolman, refersh can be in settings

##### Q-CD1.1 (follow-up) — Banner suppression when Spoolman is unconfigured + refresh location

> **Why this follow-up**: Your Q-CD1 answer is A, with two
> additional constraints I want to capture as design rules so the
> generated artifacts encode them correctly:
> 1. Users who don't use Spoolman shouldn't see "offline" noise.
> 2. The Retry/refresh control should live in **Settings**, not on
>    every screen as a banner action.
>
> Three interpretations possible — pick one.

A) **Suppress banner entirely when URL not set; banner appears only
   when URL is configured AND last call failed; "Test connection"
   button lives only in Settings (no Retry on banner).** The
   `MainScreen` banner is a passive read-only indicator
   ("Spoolman: unreachable") with no action; user goes to Settings
   to retry.

B) **Banner suppressed when URL not set; if URL is set and
   unreachable, banner appears WITH a Retry button on it;
   Settings also has a "Test connection" button.** Two retry paths
   (banner + Settings) — same effect, more discoverable.

C) **No banner ever on `MainScreen`; offline state surfaces only as
   per-action snackbars ("Spoolman unreachable — open Settings to
   reconnect"); Settings has the only Retry / "Test connection"
   button.** Most aggressive interpretation of "do not be too much
   for people who do not use Spoolman."

X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

#### Q-CD2 — DataStore vs. Room for `MaterialBrandLocalStore` (OD-2)

A) **DataStore Proto** — a simple typed list. Sufficient for FR-8.5
   (small, append-mostly, no queries beyond "list all").
B) **Room** — full table with indexes; future-proof for richer
   queries (e.g., "most-used brand").
C) **Defer to per-unit decision** — let Functional Design / NFR
   Design pick once we know whether richer queries are needed.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

#### Q-CD3 — Hilt module granularity

A) **Coarse** — single `AppModule` provides everything.
B) **Per-layer** — `NetworkModule`, `RepositoryModule`,
   `DataStoreModule`, `NfcModule` (recommended; matches the
   execution-plan unit split).
C) **Per-feature** — module per flow (overkill for this app).
X) Other (please describe after `[Answer]:` tag below)

[Answer]: B

#### Q-CD4 — `NfcRepository` ↔ `MainActivity` foreground-dispatch contract

A) `NfcRepository` exposes `attach(activity)` / `detach()` called
   from `MainActivity` lifecycle (`onResume`/`onPause`). Activity
   has no other NFC knowledge.
B) Keep v1's `NfcManager` + `NfcController` adapter intact under the
   new repository; repository only adds the sealed-state surface.
C) Move foreground-dispatch into a `LifecycleObserver` owned by the
   repository so `MainActivity` doesn't need explicit hooks.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

### Design Patterns

#### Q-DP1 — Compose state model

A) `StateFlow<UiState>` per VM, single state class (sealed sub-states
   for flow stages). Compose `collectAsStateWithLifecycle()`.
B) Multiple `StateFlow`s per VM (one per concern: form fields,
   spoolman dropdown, NFC state, banner).
C) MutableState (Compose-native) inside the VM, exposed as
   read-only `State<…>`.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

#### Q-DP2 — Flow naming / event handling

A) **MVI-lite** — VM exposes `StateFlow<UiState>` and a single
   `onEvent(UiEvent)` entry point; UiEvent is a sealed type.
B) **Method-per-action** — VM exposes `onReadTapped()`,
   `onWriteTapped()`, `onSpoolSelected(spool)`, etc. directly.
C) **Mix** — methods for primary actions; sealed events for
   bottom-sheet results.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C

#### Q-DP3 — Error/banner pattern

A) `UiState.error: String?` field — VM clears on user dismiss.
B) Separate `Channel<UiEffect>` for transient effects (toasts /
   banner triggers) on top of `StateFlow<UiState>` for persistent
   state.
C) Persistent banner state in `UiState` for connectivity (FR-10.2);
   transient errors via Snackbar host owned by the screen.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: C

#### Q-DP4 — Threading / coroutine scope

A) `viewModelScope` for VM operations; `Dispatchers.IO` only inside
   Repositories for network / DataStore / NFC; no explicit context
   switches in VM.
B) Inject a `DispatcherProvider` for testability — every Repository
   method takes its dispatcher from DI.
C) Skip explicit dispatcher discipline in v2; OkHttp + DataStore are
   already off the main thread.
X) Other (please describe after `[Answer]:` tag below)

[Answer]: A

---

## After You Answer

When all `[Answer]:` tags are filled in, reply with **"answered"** /
**"done"** / similar. I will:
1. Read the file back.
2. Validate each answer (no blanks, no obvious ambiguity).
3. If anything is vague, append follow-up `[Answer]:` questions to this
   file and ask you again.
4. Once crisp, generate the design artifacts under
   `aidlc-docs/inception/application-design/` and surface the approval
   gate.
