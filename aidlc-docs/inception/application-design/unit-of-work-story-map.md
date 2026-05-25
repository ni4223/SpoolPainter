# Unit of Work — Story Map

**Stage**: INCEPTION → Units Generation (Part 2: Generation)
**Source stories**: `aidlc-docs/inception/user-stories/stories.md` (32 v2.0 + 5 v2.1)
**Companion**: `unit-of-work.md`, `unit-of-work-dependency.md`

**Rule**: every story is assigned to **exactly one** unit. Where a story spans foundation + flow (e.g., S-4.4 write-then-verify is implemented by `NfcRepository` in U4 but exercised by U6a's `CreateAndPairUseCase`), the story is assigned to the unit that **owns the implementation**, with consuming units noted in the "Also exercised by" column.

---

## 1. v2.0 Story Map (32 stories)

| Story | Title | Unit | Also exercised by | Notes |
|---|---|---|---|---|
| **FR-1** | | | | |
| S-1.1 | Capture UID + payload on every read | U4 | U5 (read flow) | UID extraction + classification implemented in `NfcRepository`. |
| S-1.2 | Canonicalise UID as lowercase hex | U2 | All flow units | `CardUid.fromBytes`. |
| **FR-2** | | | | |
| S-2.1 | Parse `lot_nr` into UID entries + tail | U2 | U3, U6b | `CardUidEncoding.decode`. |
| S-2.2 | Serialise UID entries back into `lot_nr` | U2 | U3, U6b | `CardUidEncoding.encode`. |
| **FR-3** | | | | |
| S-3.1 | Look up unknown tag by UID in Spoolman | U3 | U5 (orchestration) | `findSpoolsByCardUid`; banner-on-error (S-10.2 AC) lands in U9. |
| S-3.2 | Pre-select + pre-fill on single match | U5 | — | View-model logic for the single-match branch. |
| S-3.3 | Surface ambiguity on multiple matches | U5 | — | View-model logic for multi-match branch. |
| S-3.4 | Pre-fill from OpenSpool payload (no Spoolman match) | U5 | — | OpenSpool prefill in `MainUiState`. |
| S-3.5 | Empty form for blank/unparseable | U5 | U7 (vendor-class fall-through reuses this state) | |
| S-3.6 | Dropdown selection prefills form | U5 | — | `onSpoolSelected` driver. |
| **FR-4** | | | | |
| S-4.1 | `canWrite` pre-conditions explicit | U6a | — | ViewModel state derivation. |
| S-4.2 | Spoolman-first sequencing | U6a | — | `CreateAndPairUseCase` orchestration. |
| S-4.3 | Emit OpenSpool NDEF payload (always with spool_id) | U6a | U7 (raw-write omits spool_id) | Payload composer in `CreateAndPairUseCase`. |
| S-4.4 | Write-then-verify | U4 | U6a, U6b, U7 | Implemented in `NfcRepository.arm(Write)`; consumers rely on `NfcResult.Success/Error`. |
| S-4.5 | Pair UID into existing Spoolman spool (PATCH) | U6a | U6b (move-on-bind reuses PATCH semantics), U7 (vendor opt-in) | `appendCardUidToSpool` lives in U3 (Q-D3=A); flow logic in U6a. |
| S-4.6 | Never overwrite a non-OpenSpool tag | U4 | U6a, U7 | Classifier produces `Vendor(reason)`; flow units consume the classification. |
| S-4.7 | Raw-write side mode | U7 | — | `RawWriteUseCase`. |
| S-4.8 | UID-only pair for vendor/foreign tags | U7 | — | `VendorUidOnlyPairUseCase` + opt-in sheet. |
| **FR-5** | | | | |
| S-5.1 | Detect UID already paired to different spool | U6b | U6a (precheck via interface) | Detection logic in `MoveOnBindUseCase`. |
| S-5.2 | Confirm + atomic move A → B | U6b | — | `RepairConfirmSheet` + two-PATCH orchestration. |
| **FR-6** | | | | |
| S-6.1 | Offer "Pair another tag with this spool?" | U6b | — | `PairAnotherTagSheet`. |
| S-6.2 | Write identical NDEF + append second UID | U6b | — | `TwoTagUseCase`. |
| S-6.3 | Move-on-bind applies independently to second tag | U6b | — | Reuses `MoveOnBindUseCase`. |
| S-6.4 | Re-derive payload on resumed pairing | U6b | — | No persistence; payload re-derived from spool filament. |
| **FR-7** | | | | |
| S-7.1 | Resolve-or-create vendor by name | U3 | U6a, U7 | Vendor step in `createSpoolForNewFilament`. |
| S-7.2 | Resolve-or-create filament | U3 | U6a, U7 | Filament step in `createSpoolForNewFilament`. |
| S-7.3 | POST a new spool with `lot_nr=card_uid:<uid>` | U3 | U6a, U7 | Spool step in `createSpoolForNewFilament`. |
| **FR-8** | | | | |
| S-8.1 | Hardcoded material + brand presets | U8 | All flow units (read presets) | `MaterialPresetSource`, `BrandPresetSource`. |
| S-8.2 | Merge Spoolman vendors into brand picker | U8 | — | `MaterialBrandRepository.brands` merge. |
| S-8.3 | "Add custom" entry for material picker | U8 | U6a (consumes new material) | `AddCustomMaterialSheet` + persistence. |
| S-8.4 | "Add custom" entry for brand picker | U8 | U6a (consumes new brand) | `AddCustomBrandSheet` + persistence. |
| **FR-9** | | | | |
| S-9.1 | Configure Spoolman server URL | U9 | U3 (probe) | `SettingsScreen` + `SettingsViewModel`. |
| S-9.2 | Choose dropdown sort order | U9 | U5 (consumes sort) | Setting persisted via DataStore. |
| S-9.3 | Override theme | U9 | — | Setting persisted via DataStore; applied in `Theme.kt`. |
| **FR-10** | | | | |
| S-10.1 | App fully usable without Spoolman URL | U9 | U5, U6a, U6b, U7 (gating logic) | Banner hidden when URL not configured; flows degrade gracefully. |
| S-10.2 | Visible banner with Retry when Spoolman unreachable | U9 | U3 (connectivity StateFlow) | `OfflineBanner` is passive (Q-CD1.1=A); the **Retry/refresh action lives only in Settings → Test connection**. |
| **FR-12** | | | | |
| S-12.1 | Dynamic color + system follow | U9 | — | `Theme.kt` Material 3 + dynamic color. |
| **FR-13** | | | | |
| S-13.1 | Single main screen with two primary actions | U9 | All UI units verify against the shell | Shape verified at U9; the actual composables ship across U1/U5/U6a/U6b/U7. |
| S-13.2 | Multi-step prompts use modal bottom sheets | U9 | U6b (sheets), U7 (sheets), U8 (sheets) | Sheet pattern verified at U9; sheets implemented across earlier units. |
| **FR-15** | | | | |
| S-15.1 | Keep SpoolPainter name + package id | U1 | U10 (release verification) | Build-config baseline; release artefacts in U10. |
| **NFR (assigned-as-stories)** | | | | |
| NFR-5 (release log stripping) | n/a | U10 | — | Release polish unit. |
| NFR-9 (testing-track distribution) | n/a | U10 | — | Release polish unit. |

**Coverage**: 32 / 32 v2.0 stories assigned. NFR-5 + NFR-9 (release-only NFRs) folded into U10. Other NFRs (NFR-1, NFR-2, NFR-3, NFR-4, NFR-6, NFR-7) are folded as cross-cutting concerns into U1 (architecture), U3/U4 (NFR-6 write-verify, NFR-7 networking outcome type), and per-unit DoD (NFR-4 unit tests).

---

## 2. v2.1 Story Map (5 stories — parked under U11/U12 stubs)

> **Hard gate**: no construction until v2.0 ships to the testing track. Listed for completeness only.

| Story | Title | Unit (stub) | Notes |
|---|---|---|---|
| S-1.4 | Decode supported vendor-tag formats for form pre-fill | U11 | Refines `TagClassification.Vendor(reason)` into `Vendor(decoded)`; needs `VendorTagDecoder` interface. |
| S-9.4.1 | Add and store per-vendor decryption keys | U12 | EncryptedSharedPreferences / Tink. |
| S-9.4.2 | Without keys, fall back to UID-only behaviour | U12 | Naturally extends U7's vendor UID-only pair. |
| S-NFR11 | Ship v2.1 under GPL-3.0 with source offer | U11 | Atomic with parser port. |
| S-NFR12 | Bake vendor format definitions into the app | U11 | Static assets. |

**Coverage**: 5 / 5 v2.1 stories parked. Full decomposition deferred to a post-ship Units Generation top-up.

---

## 3. Persona ↔ Unit Coverage (sanity check)

| Persona | Primary units exercising their stories |
|---|---|
| **P1 Casey** (Connected Hobbyist) | U5 (read happy-path), U6a (create-and-pair), U6b (two-tag), U8 (custom entries), U9 (settings) |
| **P2 Owen** (Offline Tinkerer) | U5 (read with no Spoolman), U7 (raw-write), U8 (custom entries), U9 (offline gating) |
| **P3 Bea** (Branded-Tag Reader) | U4 (vendor classification), U7 (vendor UID-only pair), U8 (brand merge), and v2.1 U11/U12 |

All three personas have at least one story in each major flow unit. P3 is intentionally the persona most affected by v2.1.

---

## 4. Story-coverage validation

- **No story is unassigned**: 32 v2.0 + 5 v2.1 = 37 / 37 stories accounted for.
- **No story is double-assigned**: each story has exactly one owning unit. "Also exercised by" denotes consumer units, not owners.
- **No orphan units**: every unit owns at least one story (U10 owns NFR-5 + NFR-9).
- **Coverage map** in `stories.md` (FR / NFR ↔ stories) is preserved end-to-end through this map.
