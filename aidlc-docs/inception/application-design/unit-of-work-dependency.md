# Unit of Work — Dependency Matrix

**Stage**: INCEPTION → Units Generation (Part 2: Generation)
**Companion**: `unit-of-work.md` (definitions), `unit-of-work-story-map.md` (story → unit), `unit-of-work-dependency-diagram.{mmd,png,svg}` (rendered graph).

---

## 1. Construction Order (Q-D2=A — Strict)

```
U1 → U2 → U3 → U4 → U5 → U6a → U6b → U7 → U8 → U9 → U9b → U10
                                                       │
                                                       ▼  HARD GATE (Q-FU1=C)
                                                       │  (v2.0 must ship to Play Store testing track)
                                                       ▼
                                                      U11, U12  (lightweight stubs only — full decomposition deferred)
```

- **No parallelism**. Solo developer, single Gradle module — even if foundation units (U1..U4) are independent in principle, Q-D2=A locks linear order to keep the work coherent.
- **Hard gate** before any v2.1 work. v2.1 units (U11/U12) are documented lightweight only and parked until v2.0 ships to the testing track.

---

## 2. Dependency Matrix (v2.0)

Rows: dependent unit (`needs ↓`).
Columns: depended-on unit (`provides →`).
`✓` = direct dependency; `(✓)` = interface-only seam (declares interface in earlier unit, impl in later unit).

| Needs ↓ \ Provides → | U1 | U2 | U3 | U4 | U5 | U6a | U6b | U7 | U8 | U9 | U9b | U10 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **U1** | — | | | | | | | | | | | |
| **U2** | ✓ | — | | | | | | | | | | |
| **U3** | ✓ | ✓ | — | | | | | | | | | |
| **U4** | ✓ | ✓ | | — | | | | | | | | |
| **U5** | ✓ | ✓ | ✓ | ✓ | — | | | | | | | |
| **U6a** | ✓ | ✓ | ✓ | ✓ | ✓ | — | (✓) | | | | | |
| **U6b** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | | | | | |
| **U7** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | | | | |
| **U8** | ✓ | | ✓ | | | ✓ | | | — | | | |
| **U9** | ✓ | | ✓ | | | | | | ✓ | — | | |
| **U9b** | ✓ | | | | | | | | ✓ | ✓ | — | |
| **U10** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |

**Interface seam (U6a ↔ U6b)**: U6a's `CreateAndPairUseCase` invokes a move-on-bind precheck through a `MoveOnBindUseCase` interface that U6a declares. U6b ships the implementation. Until U6b lands, U6a uses a no-op default (proceed without precheck) — fine because the U6 install gate (Q-T2=B) covers U6a + U6b together.

---

## 3. Cross-Unit Public Interfaces (Q-D1=C — Hybrid)

| Interface | Producing unit | Consuming units | Notes |
|---|---|---|---|
| `SettingsRepository` | U1 | U9, U3 (read URL via observe) | Read surface created in U1; full settings UI in U9. |
| `CardUid`, `CardUidEncoding`, `TagClassification`, `OpenSpoolPayload` | U2 | U3, U4, U5, U6a, U6b, U7, U8 (vendors via brand picker), U9 (theme is unrelated; no consumption), U10 | Domain primitives; consumed broadly. |
| `SpoolmanRepository`, `SpoolmanOutcome<T>`, `ConnectivityState` | U3 | U5, U6a, U6b, U7, U8 (via brand merge), U9 (probe + connectivity for banner) | Spoolman domain. **Q-D3=A**: any helper that talks to Spoolman lives in U3, not in flow units. |
| `NfcRepository`, `NfcResult`, `NfcIntent` | U4 | U5, U6a, U6b, U7, U10 (manual checklist) | NFC domain. |
| `ReadAndPairUseCase` | U5 | `MainViewModel` (within U5) | Single consumer; interface still typed for boundary clarity. |
| `CreateAndPairUseCase` | U6a | `MainViewModel` | |
| `MoveOnBindUseCase` (interface) | U6a (declared) | U6a (precheck), U6b (consumes) | **Interface declared in U6a; impl in U6b** — see seam note above. |
| `MoveOnBindUseCase` (impl) | U6b | (binds U6a's interface) | |
| `TwoTagUseCase` | U6b | `MainViewModel` | |
| `RawWriteUseCase`, `VendorUidOnlyPairUseCase` | U7 | `MainViewModel` | |
| `MaterialBrandRepository` | U8 | `MainViewModel` (write form) — consumer wired in U6a, real impl swapped in at U8 | |

**Plain-class boundaries (no interface)**: ViewModels, Compose composables, Hilt modules, sealed result types. Plain classes are fine within a unit and wherever interface-typing would add ceremony without payoff.

---

## 4. Communication Pattern Reminders (from application design)

- **UI → ViewModel**: Compose collects `StateFlow<UiState>` via `collectAsStateWithLifecycle`; emits user events via method calls (Q-DP2=C).
- **ViewModel → Use-case / Repository**: suspend calls inside `viewModelScope`. Use-cases are stateless orchestrators; repositories own all `withContext(IO)` boundaries (Q-DP4=A).
- **ViewModel → UI (transient)**: `Channel<UiEffect>` (Q-DP3=C) for snackbars / toasts.
- **Sheet VM → MainVM**: result event surfaces back through a `MainViewModel.on<...>Result(...)` method (Q-CM4=C hybrid).
- **Connectivity propagation**: `SpoolmanRepository.connectivity: StateFlow<ConnectivityState>` is observed by `SettingsViewModel` (for refresh control) and by `MainViewModel` (for banner state) — both via the same StateFlow; no cross-VM communication required.

---

## 5. Forbidden Patterns (re-asserted)

From `component-dependency.md` — these are **blocking** at code-review time:

- UI components calling data sources directly (NFR-1.2 — fixes the v1 `SpoolmanFilamentDropdown` → `SpoolmanService` wart).
- ViewModels injecting `Activity` / `Context` types into the Hilt graph.
- Use-cases holding state.
- Service locator / event bus / global singletons that bypass Hilt.
- Repository instances created outside Hilt's `SingletonComponent`.
- Raw `withContext(IO)` calls inside ViewModels (the IO boundary belongs in repositories).

---

## 6. Hard Gate (Q-FU1=C)

```
┌──────────────────────────────────────────────┐
│   v2.0  (U1..U10)                            │
│   ─────────                                  │
│   Built end-to-end                           │
│   U10 produces signed APK + AAB              │
│           │                                  │
│           ▼                                  │
│   Upload to Play Store testing track         │
│   (closed/internal/open — TBD by user)       │
│           │                                  │
│           ▼                                  │
│   ╔══════════════════════════════════════╗   │
│   ║  HARD GATE — owned by user           ║   │
│   ║  v2.1 work blocked until ship event  ║   │
│   ╚══════════════════════════════════════╝   │
│           │                                  │
│           ▼                                  │
│   v2.1 Units Generation top-up               │
│   (full decomposition of U11/U12)            │
│           │                                  │
│           ▼                                  │
│   v2.1  (U11..U12)                           │
└──────────────────────────────────────────────┘
```

- The gate sits **outside the AIDLC workflow**. AIDLC closes Build & Test at U10's exit criteria; the testing-track upload + post-ship monitoring + eventual production-track promotion are project-owned events.
- Per the `project-playstore-testing` memory: same package id, in-place update, `versionCode 100` for v2.0 (leaves room for v1.x patches at versionCode 9–99).

---

## 7. Parallelisation Opportunities

**N/A — solo developer (Q-D2=A locks strict order).**

If a second contributor were added later (out of scope here), the only safe parallel pairs given the dependency matrix would be:
- U2 || U1's tail (after U1's Hilt skeleton compiles).
- U3 || U4 (both depend only on U1+U2).
- U8 || U9 (after U6a/U6b ship; both touch different surfaces and depend on U3 read-only).

These are documented for completeness, not because they will run.
