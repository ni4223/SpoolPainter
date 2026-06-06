package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.data.local.presets.MaterialPresetSource
import com.spoolpainter.app.di.AppScope
import com.spoolpainter.app.di.IoDispatcher
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.ColorHexCodec
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec
import com.spoolpainter.app.domain.usecases.NewFilamentRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.Volatile

@Singleton
open class SpoolmanRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val apiFactory: SpoolmanApiFactory,
    @AppScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val _connectivity = MutableStateFlow<ConnectivityState>(ConnectivityState.Unknown)
    open val connectivity: StateFlow<ConnectivityState> = _connectivity.asStateFlow()

    private val _vendors = MutableStateFlow<List<SpoolmanVendor>>(emptyList())
    open val vendors: StateFlow<List<SpoolmanVendor>> = _vendors.asStateFlow()

    private val _filaments = MutableStateFlow<List<SpoolmanFilament>>(emptyList())
    open val filaments: StateFlow<List<SpoolmanFilament>> = _filaments.asStateFlow()

    private val _spools = MutableStateFlow<List<SpoolmanSpool>>(emptyList())
    open val spools: StateFlow<List<SpoolmanSpool>> = _spools.asStateFlow()

    @Volatile
    private var cachedApi: SpoolmanApi? = null

    init {
        settings.settings
            .map { it.url }
            .distinctUntilChanged()
            .onEach { url ->
                cachedApi = if (url.isBlank()) {
                    null
                } else {
                    runCatching { apiFactory.create(url) }.getOrNull()
                }
                _vendors.value = emptyList()
                _filaments.value = emptyList()
                _spools.value = emptyList()
                _connectivity.value = ConnectivityState.Unknown
                // Auto-register extra-field schemas + populate caches on every
                // URL bind. The user shouldn't have to tap Test Connection or
                // Refresh just to use the app — first reachable Spoolman wins.
                if (cachedApi != null) {
                    runCatching { ensureExtraFieldsRegistered() }
                    runCatching { refresh() }
                }
            }
            .launchIn(scope)
    }

    open suspend fun testConnection(): SpoolmanOutcome<String> {
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("getInfo") { api.getInfo() }.map { info ->
            info.version ?: "unknown"
        }
    }

    open suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>> {
        if (uid.hex.isEmpty()) return SpoolmanOutcome.Success(emptyList())
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("listSpools") {
            api.listSpools(limit = 1000, offset = 0, allowArchived = true)
        }.map { all ->
            all.filter { spool ->
                ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").contains(uid)
            }
        }
    }

    open suspend fun getSpool(spoolId: Int): SpoolmanOutcome<SpoolmanSpool> {
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("getSpool") { api.getSpool(spoolId) }
    }

    open suspend fun getFilament(filamentId: Int): SpoolmanOutcome<SpoolmanFilament> {
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("getFilament") { api.getFilament(filamentId) }
    }

    /**
     * Idempotent PATCH (Q-U8-13=A): reads the cache, builds a sparse body
     * containing only fields whose stored value differs from the requested
     * value. If nothing differs, returns Success(currentFilament) without an
     * HTTP call. On Success, swaps the new filament into the cache.
     */
    open suspend fun patchFilament(
        filamentId: Int,
        body: PatchFilamentBody,
    ): SpoolmanOutcome<SpoolmanFilament> {
        val api = cachedApi ?: return urlNotConfigured()
        val current = _filaments.value.find { it.id == filamentId }
        val sparse = if (current == null) body else sparseDiff(current, body)
        if (current != null && sparse.isEmpty()) {
            return SpoolmanOutcome.Success(current)
        }
        return performHttp("patchFilament") { api.patchFilament(filamentId, sparse) }
            .also { o ->
                if (o is SpoolmanOutcome.Success) {
                    replaceFilamentInCache(o.data)
                    refreshAfterWrite()
                }
            }
    }

    /**
     * Existing-filament path (Q-U8-12=A). Skips the matcher / no-filament POST.
     *   1. Fresh getFilament (defensive — cache may be stale).
     *   2. If overrides differ from stored → patchFilament (idempotency-skipped if equal).
     *   3. createSpool (filament_id) — caller appends UID via appendCardUidToSpool.
     */
    open suspend fun createSpoolForExistingFilament(
        filamentId: Int,
        expanderOverrides: ExpanderOverrides,
    ): SpoolmanOutcome<SpoolmanSpool> {
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("getFilament") { api.getFilament(filamentId) }
            .flatMap { filament -> applyOverridesIfNeeded(filament, expanderOverrides) }
            .flatMap { filament ->
                createSpoolStep(
                    api,
                    filament,
                    spoolPrice = expanderOverrides.spoolPrice,
                    spoolWeight = expanderOverrides.spoolWeightForSpool,
                )
            }
            .also { outcome ->
                if (outcome is SpoolmanOutcome.Success) {
                    prependSpool(outcome.data)
                    refreshAfterWrite()
                }
            }
    }

    /**
     * Existing-spool variant edit (UI-13 partial). Narrow sibling of the
     * old applyOverridesToFilamentOfSpool — handles ONLY variant. Density
     * / weight / spool_weight / price are locked read-only on the
     * existing-spool path in v2.0.2 (decision J), so this is the single
     * legitimate filament-record edit from that path.
     *
     * Reuses the existing extra-merge encoding so other extra fields the
     * user (or another tool) put on the filament are preserved.
     */
    open suspend fun applyVariantToFilamentOfSpool(
        spoolId: Int,
        variant: String,
    ): SpoolmanOutcome<SpoolmanFilament> {
        val canon = canonVariant(variant)
            ?: return SpoolmanOutcome.ParseError(IllegalStateException("blank variant"))
        val api = cachedApi ?: return urlNotConfigured()
        val cachedSpool = _spools.value.find { it.id == spoolId }
        val filamentId = cachedSpool?.filament?.id
            ?: return performHttp("getSpool") { api.getSpool(spoolId) }
                .flatMap { spool ->
                    val fid = spool.filament?.id
                        ?: return@flatMap SpoolmanOutcome.ParseError(
                            IllegalStateException("no filament on spool $spoolId"),
                        )
                    performHttp("getFilament") { api.getFilament(fid) }
                        .flatMap { filament -> patchVariantIfChanged(filament, canon) }
                }
        return performHttp("getFilament") { api.getFilament(filamentId) }
            .flatMap { filament -> patchVariantIfChanged(filament, canon) }
    }

    private suspend fun patchVariantIfChanged(
        filament: SpoolmanFilament,
        canonVariant: String,
    ): SpoolmanOutcome<SpoolmanFilament> {
        val storedVariant = decodeJsonString(filament.extra?.get("variant"))
        if (storedVariant == canonVariant) return SpoolmanOutcome.Success(filament)
        val mergedExtra = (filament.extra ?: emptyMap()) +
            ("variant" to GSON.toJson(canonVariant))
        return patchFilament(filament.id, PatchFilamentBody(extra = mergedExtra))
    }

    /**
     * Existing-filament-new-spool path: resolve the filament from the cache
     * (or the network if not cached) and apply expander overrides before
     * spool create. Still used by [createSpoolForExistingFilament].
     */
    open suspend fun applyOverridesToFilamentOfSpool(
        spoolId: Int,
        overrides: ExpanderOverrides,
    ): SpoolmanOutcome<SpoolmanFilament> {
        if (overrides == ExpanderOverrides.EMPTY) {
            // Nothing to do — caller can ignore the result.
            return SpoolmanOutcome.ParseError(IllegalStateException("no overrides"))
        }
        val api = cachedApi ?: return urlNotConfigured()
        val cachedSpool = _spools.value.find { it.id == spoolId }
        val filamentId = cachedSpool?.filament?.id
            ?: return performHttp("getSpool") { api.getSpool(spoolId) }
                .flatMap { spool ->
                    val fid = spool.filament?.id
                        ?: return@flatMap SpoolmanOutcome.ParseError(
                            IllegalStateException("no filament on spool $spoolId"),
                        )
                    performHttp("getFilament") { api.getFilament(fid) }
                        .flatMap { filament -> applyOverridesIfNeeded(filament, overrides) }
                }
        return performHttp("getFilament") { api.getFilament(filamentId) }
            .flatMap { filament -> applyOverridesIfNeeded(filament, overrides) }
    }

    /**
     * Single seam for spool-level PATCH. Carries `remaining_weight` and
     * `price` together when both are dirty — one HTTP request.
     *
     * `extra: null` is safe — Spoolman PATCH treats omitted fields as
     * "don't change" and Gson omits null fields from the body.
     */
    open suspend fun patchSpoolFields(
        spoolId: Int,
        body: SpoolPatchBody,
    ): SpoolmanOutcome<SpoolmanSpool> {
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("patchSpool") { api.patchSpool(spoolId, body) }
            .also { o ->
                if (o is SpoolmanOutcome.Success) {
                    replaceSpoolInCache(o.data)
                    refreshAfterWrite()
                }
            }
    }

    private suspend fun applyOverridesIfNeeded(
        filament: SpoolmanFilament,
        overrides: ExpanderOverrides,
    ): SpoolmanOutcome<SpoolmanFilament> {
        // Variant is the only "extra"-bound override here. Merge it into the
        // existing extra map so we don't blow away other custom fields the
        // user (or another tool) put on the filament. Encoding uses the same
        // double-JSON-string convention as resolveOrCreateFilament line ~473
        // so reads via decodeJsonString round-trip cleanly.
        val variantNormalised = canonVariant(overrides.variant)
        val mergedExtra: Map<String, String>? = if (variantNormalised == null) {
            null
        } else {
            val current = filament.extra ?: emptyMap()
            current + ("variant" to GSON.toJson(variantNormalised))
        }
        val body = PatchFilamentBody(
            density = overrides.density,
            diameter = overrides.diameter,
            weight = overrides.weight,
            spool_weight = overrides.spoolWeight,
            price = overrides.price,
            extra = mergedExtra,
        )
        if (body.isEmpty()) return SpoolmanOutcome.Success(filament)
        return patchFilament(filament.id, body)
    }

    private fun sparseDiff(
        current: SpoolmanFilament,
        body: PatchFilamentBody,
    ): PatchFilamentBody = PatchFilamentBody(
        name = body.name?.takeIf { it != current.name },
        settings_extruder_temp = body.settings_extruder_temp?.takeIf { it != current.settings_extruder_temp },
        settings_bed_temp = body.settings_bed_temp?.takeIf { it != current.settings_bed_temp },
        density = body.density?.takeIf { it != current.density },
        diameter = body.diameter?.takeIf { it != current.diameter },
        weight = body.weight?.takeIf { it != current.weight },
        spool_weight = body.spool_weight?.takeIf { it != current.spool_weight },
        price = body.price?.takeIf { it != current.price },
        extra = body.extra?.takeIf { it != current.extra },
    )

    private fun PatchFilamentBody.isEmpty(): Boolean =
        name == null && settings_extruder_temp == null && settings_bed_temp == null &&
            density == null && diameter == null && weight == null &&
            spool_weight == null && price == null && extra == null

    open suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool> {
        if (uid.hex.isEmpty()) return invalidArg("uid is empty")
        val api = cachedApi ?: return urlNotConfigured()
        return executeWithExtraFieldsBootstrap {
            performHttp("getSpool") { api.getSpool(spoolId) }.flatMap { spool ->
                val current = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "")
                if (uid in current) {
                    replaceSpoolInCache(spool)
                    SpoolmanOutcome.Success(spool)
                } else {
                    val newUids = current + uid
                    val newExtra = (spool.extra ?: emptyMap()) +
                        ("card_uids" to ExtraCardUidsCodec.encode(newUids))
                    performHttp("patchSpool") {
                        api.patchSpool(spoolId, SpoolPatchBody(extra = newExtra))
                    }.also { o ->
                        if (o is SpoolmanOutcome.Success) {
                            replaceSpoolInCache(o.data)
                            refreshAfterWrite()
                        }
                    }
                }
            }
        }
    }

    open suspend fun removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool> {
        if (uid.hex.isEmpty()) return invalidArg("uid is empty")
        val api = cachedApi ?: return urlNotConfigured()
        return executeWithExtraFieldsBootstrap {
            performHttp("getSpool") { api.getSpool(spoolId) }.flatMap { spool ->
                val current = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "")
                if (uid !in current) {
                    replaceSpoolInCache(spool)
                    SpoolmanOutcome.Success(spool)
                } else {
                    val newUids = current.filterNot { it == uid }
                    val newExtra = (spool.extra ?: emptyMap()) +
                        ("card_uids" to ExtraCardUidsCodec.encode(newUids))
                    performHttp("patchSpool") {
                        api.patchSpool(spoolId, SpoolPatchBody(extra = newExtra))
                    }.also { o ->
                        if (o is SpoolmanOutcome.Success) {
                            replaceSpoolInCache(o.data)
                            refreshAfterWrite()
                        }
                    }
                }
            }
        }
    }

    // F-6 (v2.0.3): throttled refresh used by app-foreground hook + Read-arm
    // path + pull-to-refresh. Mutex serialises concurrent calls so a stale
    // foreground hook + a Read-tap landing within the same tick collapse
    // into one refresh. force=true bypasses the throttle for explicit
    // user-initiated PTR.
    private val refreshMutex = Mutex()
    @Volatile
    private var lastRefreshEpochMs: Long? = null
    private val MIN_REFRESH_INTERVAL_MS = 5_000L

    open suspend fun refreshIfStale(force: Boolean = false): SpoolmanOutcome<Unit> {
        // Without a configured URL there's nothing to refresh — bail before
        // taking the mutex so PTR / foreground / Read paths are all no-ops
        // when the user hasn't set up Spoolman yet.
        cachedApi ?: return urlNotConfigured()
        return refreshMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val last = lastRefreshEpochMs
            if (!force && last != null && now - last < MIN_REFRESH_INTERVAL_MS) {
                return@withLock SpoolmanOutcome.Success(Unit)
            }
            val outcome = refresh()
            if (outcome is SpoolmanOutcome.Success) {
                lastRefreshEpochMs = Clock.System.now().toEpochMilliseconds()
            }
            outcome
        }
    }

    suspend fun refresh(): SpoolmanOutcome<Unit> {
        val api = cachedApi ?: return urlNotConfigured()
        val vendorsOutcome = performHttp("listVendors") { api.listVendors() }
        if (vendorsOutcome !is SpoolmanOutcome.Success) {
            @Suppress("UNCHECKED_CAST")
            return vendorsOutcome as SpoolmanOutcome<Unit>
        }
        val filamentsOutcome = performHttp("listFilaments") { api.listFilaments() }
        if (filamentsOutcome !is SpoolmanOutcome.Success) {
            @Suppress("UNCHECKED_CAST")
            return filamentsOutcome as SpoolmanOutcome<Unit>
        }
        // limit=1000 to defeat Spoolman's default 100-cap (would silently
        // truncate freshly-created entries on instances >100 spools).
        // allow_archived=true so archived spools are present in the cache —
        // findSpoolsByCardUid + move-on-bind need them for UID lookup. The
        // dropdown filters them out at the UI layer.
        val spoolsOutcome = performHttp("listSpools") {
            api.listSpools(limit = 1000, offset = 0, allowArchived = true)
        }
        if (spoolsOutcome !is SpoolmanOutcome.Success) {
            @Suppress("UNCHECKED_CAST")
            return spoolsOutcome as SpoolmanOutcome<Unit>
        }
        _vendors.value = vendorsOutcome.data
        _filaments.value = filamentsOutcome.data
        _spools.value = spoolsOutcome.data
        return SpoolmanOutcome.Success(Unit)
    }

    /**
     * Resolves vendor + filament + creates a spool with no `extra.card_uids`.
     * The caller (use case) records the UID via [appendCardUidToSpool] once it
     * has the UID — same call shape for tap-first and form-first flows. This
     * keeps spool-creation honest: it never lies about a UID it doesn't yet
     * know about.
     */
    /**
     * Fire-and-forget refresh after a successful Spoolman mutation. Keeps
     * vendors/filaments/spools caches fresh so derived flows (e.g.
     * MaterialBrandRepository.brands/materials) reflect any side effects
     * the server made — without coupling write success to refresh success.
     */
    private fun refreshAfterWrite() {
        scope.launch { runCatching { refresh() } }
    }

    open suspend fun createSpoolForNewFilament(req: NewFilamentRequest): SpoolmanOutcome<SpoolmanSpool> =
        createSpoolForNewFilamentBundle(req).map { it.spool }

    /**
     * Like [createSpoolForNewFilament] but additionally surfaces whether the
     * vendor and filament were freshly POSTed (vs matched against existing
     * records). Callers use the wasFresh flags to drive chain-delete on
     * orphan-cleanup paths.
     */
    open suspend fun createSpoolForNewFilamentBundle(req: NewFilamentRequest): SpoolmanOutcome<NewSpoolBundle> {
        val vendorName = req.vendorName.trim().takeIf { it.isNotEmpty() }
            ?: return invalidArg("vendorName is empty")
        val materialName = req.materialName.trim().takeIf { it.isNotEmpty() }
            ?: return invalidArg("materialName is empty")
        val api = cachedApi ?: return urlNotConfigured()

        return resolveOrCreateVendor(api, vendorName)
            .flatMap { resolvedVendor ->
                resolveOrCreateFilament(api, resolvedVendor.value, materialName, req)
                    .flatMap { resolvedFilament ->
                        createSpoolStep(
                            api,
                            resolvedFilament.value,
                            spoolPrice = req.expanderOverrides.spoolPrice,
                            spoolWeight = req.expanderOverrides.spoolWeightForSpool,
                        ).map { spool ->
                            NewSpoolBundle(
                                spool = spool,
                                filamentWasFresh = resolvedFilament.wasCreatedFresh,
                                // Reused-filament-on-fresh-vendor edge: trust the
                                // filament's wasCreatedFresh. If filament was
                                // matched (false), the vendor it points to is
                                // by-definition not ours to clean up either.
                                vendorWasFresh = resolvedVendor.wasCreatedFresh && resolvedFilament.wasCreatedFresh,
                                filamentId = resolvedFilament.value.id,
                                vendorId = resolvedVendor.value.id,
                            )
                        }
                    }
            }
            .also { outcome ->
                if (outcome is SpoolmanOutcome.Success) {
                    prependSpool(outcome.data.spool)
                    refreshAfterWrite()
                }
            }
    }

    /**
     * Best-effort registration of both extra-field schemas. Each side (spool /
     * filament) is attempted independently — a failure on one does not block
     * the other. Returns Success if every needed POST landed; otherwise
     * returns the first non-Success outcome but ALL attempts have already run.
     */
    open suspend fun ensureExtraFieldsRegistered(): SpoolmanOutcome<Unit> {
        val api = cachedApi ?: return urlNotConfigured()
        val spoolResult = registerSpoolCardUids(api)
        val filamentResult = registerFilamentVariant(api)
        return when {
            spoolResult is SpoolmanOutcome.Success && filamentResult is SpoolmanOutcome.Success ->
                SpoolmanOutcome.Success(Unit)
            spoolResult !is SpoolmanOutcome.Success -> {
                @Suppress("UNCHECKED_CAST") spoolResult as SpoolmanOutcome<Unit>
            }
            else -> {
                @Suppress("UNCHECKED_CAST") filamentResult as SpoolmanOutcome<Unit>
            }
        }
    }

    private suspend fun registerSpoolCardUids(api: SpoolmanApi): SpoolmanOutcome<Unit> {
        val fields = performHttp("listFields:spool") { api.listFields("spool") }
        if (fields !is SpoolmanOutcome.Success) {
            @Suppress("UNCHECKED_CAST") return fields as SpoolmanOutcome<Unit>
        }
        if (fields.data.any { it.key == "card_uids" }) return SpoolmanOutcome.Success(Unit)
        val r = performHttp("postField:spool/card_uids") {
            api.postField(
                "spool",
                "card_uids",
                ExtraFieldDef(
                    name = "Card UIDs",
                    field_type = "text",
                    order = 1,
                    default_value = "\"\"",
                ),
            )
        }
        return if (r is SpoolmanOutcome.Success) SpoolmanOutcome.Success(Unit) else {
            @Suppress("UNCHECKED_CAST") r as SpoolmanOutcome<Unit>
        }
    }

    private suspend fun registerFilamentVariant(api: SpoolmanApi): SpoolmanOutcome<Unit> {
        val fields = performHttp("listFields:filament") { api.listFields("filament") }
        if (fields !is SpoolmanOutcome.Success) {
            @Suppress("UNCHECKED_CAST") return fields as SpoolmanOutcome<Unit>
        }
        if (fields.data.any { it.key == "variant" }) return SpoolmanOutcome.Success(Unit)
        val r = performHttp("postField:filament/variant") {
            api.postField(
                "filament",
                "variant",
                ExtraFieldDef(
                    name = "Variant",
                    field_type = "text",
                    order = 1,
                    default_value = "\"\"",
                ),
            )
        }
        return if (r is SpoolmanOutcome.Success) SpoolmanOutcome.Success(Unit) else {
            @Suppress("UNCHECKED_CAST") r as SpoolmanOutcome<Unit>
        }
    }

    private suspend inline fun <T> executeWithExtraFieldsBootstrap(
        crossinline block: suspend () -> SpoolmanOutcome<T>,
    ): SpoolmanOutcome<T> {
        val first = block()
        if (first is SpoolmanOutcome.HttpError &&
            first.code == 400 &&
            first.message.contains("Unknown extra field", ignoreCase = true)
        ) {
            val bootstrap = ensureExtraFieldsRegistered()
            if (bootstrap !is SpoolmanOutcome.Success) {
                @Suppress("UNCHECKED_CAST") return bootstrap as SpoolmanOutcome<T>
            }
            return block()
        }
        return first
    }

    internal suspend fun resolveOrCreateVendor(
        api: SpoolmanApi,
        name: String,
    ): SpoolmanOutcome<Resolved<SpoolmanVendor>> {
        return performHttp("listVendors") { api.listVendors() }.flatMap { list ->
            val match = list.firstOrNull { it.name.equals(name, ignoreCase = true) && it.id != null }
            if (match != null) {
                SpoolmanOutcome.Success(Resolved(match, wasCreatedFresh = false))
            } else {
                performHttp("createVendor") { api.createVendor(CreateVendorRequest(name)) }
                    .also { outcome -> if (outcome is SpoolmanOutcome.Success) prependVendor(outcome.data) }
                    .map { vendor -> Resolved(vendor, wasCreatedFresh = true) }
            }
        }
    }

    internal suspend fun resolveOrCreateFilament(
        api: SpoolmanApi,
        vendor: SpoolmanVendor,
        materialName: String,
        req: NewFilamentRequest,
    ): SpoolmanOutcome<Resolved<SpoolmanFilament>> {
        val vendorId = vendor.id ?: return SpoolmanOutcome.ParseError(
            IllegalStateException("vendor.id missing for ${vendor.name}"),
        )
        val variantNormalised = canonVariant(req.variant)
        val targetHex = ColorHexCodec.canonicalise(req.colorHex)
        return performHttp("listFilaments") { api.listFilaments() }.flatMap { list ->
            val match = list.firstOrNull { f ->
                if (f.vendor?.id != vendorId) return@firstOrNull false
                if (!(f.material ?: "").equals(materialName, ignoreCase = true)) return@firstOrNull false
                if (ColorHexCodec.canonicalise(f.color_hex) != targetHex) return@firstOrNull false
                // Match variant from extra.variant only. filament.name is the
                // full display name ("Polymaker PLA Matte"), not the variant.
                // Variant comparison treats null/blank as equivalent and is
                // case-insensitive (FR-U6b-Δ-4).
                val existingVariant = canonVariant(decodeJsonString(f.extra?.get("variant")))
                when {
                    existingVariant == null && variantNormalised == null -> true
                    existingVariant == null || variantNormalised == null -> false
                    else -> existingVariant.equals(variantNormalised, ignoreCase = true)
                }
            }
            if (match != null) {
                android.util.Log.d(
                    "SpoolmanRepo",
                    "filament match hit: id=${match.id} name=${match.name} variant=$variantNormalised " +
                        "existingVariant=${canonVariant(decodeJsonString(match.extra?.get("variant")))} " +
                        "colorHex(target=$targetHex existing=${ColorHexCodec.canonicalise(match.color_hex)})",
                )
                SpoolmanOutcome.Success(Resolved(match, wasCreatedFresh = false))
            } else {
                val extras = variantNormalised?.let { mapOf("variant" to GSON.toJson(it)) }
                val filamentName = req.name.trim().takeIf { it.isNotEmpty() }
                android.util.Log.d(
                    "SpoolmanRepo",
                    "createFilament: name=$filamentName variant=$variantNormalised extras=$extras",
                )
                executeWithExtraFieldsBootstrap {
                    performHttp("createFilament") {
                        api.createFilament(
                            CreateFilamentRequest(
                                name = filamentName,
                                vendor_id = vendorId,
                                material = materialName,
                                color_hex = req.colorHex,
                                settings_extruder_temp = req.tempRanges.extruderMin,
                                settings_bed_temp = req.tempRanges.bedMin,
                                density = req.expanderOverrides.density
                                    ?: MaterialPresetSource.densityFor(materialName),
                                diameter = req.expanderOverrides.diameter
                                    ?: MaterialPresetSource.DEFAULT_DIAMETER_MM,
                                weight = req.expanderOverrides.weight
                                    ?: MaterialPresetSource.DEFAULT_FULL_SPOOL_WEIGHT_G,
                                spool_weight = req.expanderOverrides.spoolWeight,
                                price = req.expanderOverrides.price,
                                extra = extras,
                            ),
                        )
                    }.also { outcome -> if (outcome is SpoolmanOutcome.Success) prependFilament(outcome.data) }
                        .map { filament -> Resolved(filament, wasCreatedFresh = true) }
                }
            }
        }
    }

    internal suspend fun createSpoolStep(
        api: SpoolmanApi,
        filament: SpoolmanFilament,
        spoolPrice: Float? = null,
        spoolWeight: Float? = null,
    ): SpoolmanOutcome<SpoolmanSpool> {
        return performHttp("createSpool") {
            api.createSpool(
                CreateSpoolRequest(
                    filament_id = filament.id,
                    price = spoolPrice,
                    spool_weight = spoolWeight,
                    extra = null,
                ),
            )
        }
    }

    private suspend inline fun <T> performHttp(
        label: String,
        crossinline block: suspend () -> Response<T>,
    ): SpoolmanOutcome<T> = withContext(ioDispatcher) {
        try {
            val response = block()
            when {
                !response.isSuccessful -> {
                    val message = runCatching { response.errorBody()?.string()?.take(200) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: response.message().ifBlank { "HTTP ${response.code()}" }
                    httpError(response.code(), message)
                }
                response.body() == null ->
                    SpoolmanOutcome.ParseError(IllegalStateException("empty body for $label"))
                else -> {
                    _connectivity.value = ConnectivityState.Reachable
                    SpoolmanOutcome.Success(response.body()!!)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: JsonSyntaxException) {
            SpoolmanOutcome.ParseError(e)
        } catch (e: JsonParseException) {
            SpoolmanOutcome.ParseError(e)
        } catch (e: IOException) {
            _connectivity.value = ConnectivityState.Unreachable(
                e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName
                    ?: "Network error",
            )
            SpoolmanOutcome.NetworkError(e)
        }
    }

    private fun httpError(code: Int, message: String): SpoolmanOutcome<Nothing> {
        _connectivity.value = ConnectivityState.Reachable
        return SpoolmanOutcome.HttpError(code, message)
    }

    private fun urlNotConfigured(): SpoolmanOutcome<Nothing> {
        _connectivity.value = ConnectivityState.Unknown
        return SpoolmanOutcome.NetworkError(UrlNotConfiguredException())
    }

    private fun invalidArg(reason: String): SpoolmanOutcome<Nothing> {
        return SpoolmanOutcome.NetworkError(IllegalArgumentException(reason))
    }

    private fun replaceSpoolInCache(spool: SpoolmanSpool) {
        val id = spool.id ?: return
        _spools.value = listOf(spool) + _spools.value.filter { it.id != id }
    }

    private fun prependSpool(spool: SpoolmanSpool) {
        val id = spool.id
        _spools.value = listOf(spool) +
            if (id == null) _spools.value else _spools.value.filter { it.id != id }
    }

    private fun prependVendor(vendor: SpoolmanVendor) {
        val id = vendor.id
        _vendors.value = listOf(vendor) +
            if (id == null) _vendors.value else _vendors.value.filter { it.id != id }
    }

    private fun prependFilament(filament: SpoolmanFilament) {
        _filaments.value = listOf(filament) + _filaments.value.filter { it.id != filament.id }
    }

    private fun replaceFilamentInCache(filament: SpoolmanFilament) {
        _filaments.value = _filaments.value.map { if (it.id == filament.id) filament else it }
    }

    private fun removeSpoolFromCache(id: Int) {
        _spools.value = _spools.value.filter { it.id != id }
    }

    private fun removeFilamentFromCache(id: Int) {
        _filaments.value = _filaments.value.filter { it.id != id }
    }

    private fun removeVendorFromCache(id: Int) {
        _vendors.value = _vendors.value.filter { it.id != id }
    }

    /**
     * Best-effort chain delete of an orphan spool plus the filament/vendor
     * created in the same transaction (if they're not referenced by any other
     * record). Called from create-and-pair / vendor-uid-only-pair failure
     * paths when no UID was ever attached to the spool.
     *
     * Spool DELETE is unconditional. Filament + vendor DELETEs are
     * best-effort: if Spoolman returns 4xx because they're still referenced,
     * we swallow and stop (no cascading attempt). The caller doesn't surface
     * these failures to the user — they care about the write that failed,
     * not about cleanup details.
     */
    open suspend fun chainDeleteOrphan(orphan: OrphanSpool): SpoolmanOutcome<Unit> {
        val api = cachedApi ?: return urlNotConfigured()
        val spoolDelete = performHttp("deleteSpool") { api.deleteSpool(orphan.spoolId) }
        if (spoolDelete is SpoolmanOutcome.Success) {
            removeSpoolFromCache(orphan.spoolId)
        } else {
            android.util.Log.w(
                "SpoolmanRepo",
                "chainDeleteOrphan: spool DELETE failed (id=${orphan.spoolId}); skipping filament/vendor",
            )
            @Suppress("UNCHECKED_CAST")
            return spoolDelete as SpoolmanOutcome<Unit>
        }
        if (orphan.filamentId != null) {
            val filamentDelete = performHttp("deleteFilament") { api.deleteFilament(orphan.filamentId) }
            if (filamentDelete is SpoolmanOutcome.Success) {
                removeFilamentFromCache(orphan.filamentId)
            } else {
                android.util.Log.w(
                    "SpoolmanRepo",
                    "chainDeleteOrphan: filament DELETE failed (id=${orphan.filamentId}); skipping vendor",
                )
                return SpoolmanOutcome.Success(Unit)
            }
        }
        if (orphan.vendorId != null) {
            val vendorDelete = performHttp("deleteVendor") { api.deleteVendor(orphan.vendorId) }
            if (vendorDelete is SpoolmanOutcome.Success) {
                removeVendorFromCache(orphan.vendorId)
            } else {
                android.util.Log.w(
                    "SpoolmanRepo",
                    "chainDeleteOrphan: vendor DELETE failed (id=${orphan.vendorId}); cleanup partial",
                )
            }
        }
        return SpoolmanOutcome.Success(Unit)
    }

    /**
     * Trims a variant string and treats blank as null. Used in the filament
     * matcher so `null`, `""`, and `"  "` all collapse to the same bucket.
     */
    private fun canonVariant(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Spoolman returns extra `text` fields as JSON-encoded strings (`"matte"`).
     * Strip the wrapping quotes if present and return null for empty values.
     */
    private fun decodeJsonString(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        val unwrapped = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
        return unwrapped.takeIf { it.isNotBlank() }
    }

    private companion object {
        val GSON = Gson()
    }
}

class UrlNotConfiguredException : IOException("Spoolman URL not configured")
