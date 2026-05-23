# API Documentation

## External APIs Consumed (Spoolman)

The app calls the Spoolman REST API. Base URL is operator-supplied at runtime via
Settings (e.g., `http://192.168.1.100:7912`).

### `GET api/v1/spool`
- **Method**: GET
- **Path**: `/api/v1/spool`
- **Purpose**: Paginate and list all filament spools.
- **Query**: `limit` (always 10), `offset` (page * 10), `sort` (optional;
  e.g., `filament.vendor.name:asc`, `filament.material:asc`, `last_used:desc`)
- **Request**: none (GET)
- **Response**: `List<SpoolmanSpool>` (Gson-decoded; pagination ends on a
  short page or empty array)

### `GET api/v1/spool/{id}`
- **Method**: GET
- **Path**: `/api/v1/spool/{id}`
- **Purpose**: Fetch a single spool by id when reverse-resolving a
  `spool_id` value read from a tag (used when the local cache misses).
- **Request**: none
- **Response**: `SpoolmanSpool` (a single object)

### Caching
- 30-second in-memory cache of the paginated result, keyed by service
  instance. `forceRefresh = true` bypasses the cache.

## Internal APIs

### `MainViewModel`
- `loadSpoolmanUrl(context: Context)` — read `spoolman_url`,
  `spoolman_sort` from `spoolpainter_prefs`; auto-fetch if URL valid.
- `handleNfcTagDetected(data: String?)` — parse JSON via
  `OpenSpoolData.fromJson`; update `readData`, `currentSpoolId`, bump
  `dataVersion`; clear `selectedSpool` if no `spool_id`.
- `handleSettingsSave(context, newUrl, newSort)` — persist + refetch.
- `handleFilamentSelection(filament: FilamentSpool?)` — pre-fill the form
  from a Spoolman spool, preserving any existing non-`Basic` `subtype`
  already on the read tag.
- `refreshSpools()` — pull-to-refresh.
- `showSnackbarMessage`, `dismissSnackbar`, `showSettings`, `hideSettings`.

### `NfcHandler` (façade in `hardware/nfc/`)
- `initialize()`
- `enableForegroundDispatch()` / `disableForegroundDispatch()`
- `handleIntent(intent)`
- `writeToCurrentTag(data: String)` — set pending write payload; tap-to-write
- `enableReading()` — set read mode; uses recent-tag (5s) shortcut if
  available
- Callbacks: `onTagDetected: (String?) -> Unit`, `onStatusUpdate: (String, Boolean) -> Unit`

### `SpoolmanService`
- `getFilaments(sortBy: String? = null, forceRefresh: Boolean = false): List<FilamentSpool>`
- `findFilamentBySpoolId(spoolId: String): FilamentSpool?`

## Data Models

### `OpenSpoolData` (NFC wire format, JSON via `org.json`)
| Field          | JSON key         | Type    | Notes                                              |
|----------------|------------------|---------|----------------------------------------------------|
| protocol       | `protocol`       | string  | Always `"openspool"`                               |
| version        | `version`        | string  | `"1.0"`                                            |
| type           | `type`           | string  | Material name (PLA, ABS, …)                       |
| colorHex       | `color_hex`      | string  | 6-char uppercase hex; `""` if absent               |
| brand          | `brand`          | string  |                                                    |
| minTemp        | `min_temp`       | string  | Stored as string in JSON                           |
| maxTemp        | `max_temp`       | string  | Stored as string in JSON                           |
| bedMinTemp     | `bed_min_temp`   | string? | Omitted when null                                  |
| bedMaxTemp     | `bed_max_temp`   | string? | Omitted when null                                  |
| subtype        | `subtype`        | string  | `"Basic"` if not specified; only emitted if non-empty |
| spoolId        | `spool_id`       | string? | Spoolman spool id, omitted when null               |
| lotNr          | `lot_nr`         | string? | `OpenSpoolData.generateLotNr()` available, not currently set |

**Quirk**: `fromJson` strips a leading non-JSON language prefix
(e.g., `"en{...}"` → `"{...}"`) before parsing — Android's NDEF Text records
include a language code prefix that some tags accidentally retain.

### `FilamentSpool` (in-app)
- `id: Int?`, `material: String`, `variant: String`, `brand: String`,
  `colorHex: String?`, `minTemp/maxTemp/bedMinTemp/bedMaxTemp: Int?`,
  `remainingWeight/usedWeight: Float?`, `location: String?`, `lotNr: String?`,
  `archived: Boolean`, `spoolmanName: String?`
- `displayName: String` — `"$material $variant"` (or just `material`)
- `fromSpoolman(spool)` — clamps Spoolman's single-value `extruder_temp` /
  `bed_temp` against the material's defaults: if within range, use defaults;
  otherwise use the value with `+20` (extruder) / `+10` (bed) for the max.
- `fromOpenSpool(data)` — reverse mapping; `subtype != "Basic"` becomes
  `variant`.

### `SpoolmanSpool` / `SpoolmanFilament` / `SpoolmanVendor`
Gson-deserialized DTOs — match Spoolman's REST shape:
- `SpoolmanSpool`: `id`, `filament`, `remaining_weight`, `used_weight`,
  `location`, `lot_nr`, `archived`
- `SpoolmanFilament`: `id`, `name`, `material`, `vendor`, `color_hex`,
  `settings_extruder_temp`, `settings_bed_temp`
- `SpoolmanVendor`: `name`

### `Material` (preset)
`name`, `defaultMinTemp`, `defaultMaxTemp`, `defaultBedMinTemp`,
`defaultBedMaxTemp` — 10 presets in `MaterialDatabase`.

### Unused models
`NfcResult`, `NfcTag`, `AppState` are defined but not threaded through any
active call path in v1.
