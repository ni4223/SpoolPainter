package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.di.AppScope
import com.spoolpainter.app.di.IoDispatcher
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec
import com.spoolpainter.app.domain.usecases.NewFilamentRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
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
                        if (o is SpoolmanOutcome.Success) replaceSpoolInCache(o.data)
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
                        if (o is SpoolmanOutcome.Success) replaceSpoolInCache(o.data)
                    }
                }
            }
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
    open suspend fun createSpoolForNewFilament(req: NewFilamentRequest): SpoolmanOutcome<SpoolmanSpool> {
        val vendorName = req.vendorName.trim().takeIf { it.isNotEmpty() }
            ?: return invalidArg("vendorName is empty")
        val materialName = req.materialName.trim().takeIf { it.isNotEmpty() }
            ?: return invalidArg("materialName is empty")
        val api = cachedApi ?: return urlNotConfigured()

        return resolveOrCreateVendor(api, vendorName)
            .flatMap { vendor -> resolveOrCreateFilament(api, vendor, materialName, req) }
            .flatMap { filament -> createSpoolStep(api, filament) }
            .also { outcome -> if (outcome is SpoolmanOutcome.Success) prependSpool(outcome.data) }
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
    ): SpoolmanOutcome<SpoolmanVendor> {
        return performHttp("listVendors") { api.listVendors() }.flatMap { list ->
            val match = list.firstOrNull { it.name.equals(name, ignoreCase = true) && it.id != null }
            if (match != null) {
                SpoolmanOutcome.Success(match)
            } else {
                performHttp("createVendor") { api.createVendor(CreateVendorRequest(name)) }
                    .also { outcome -> if (outcome is SpoolmanOutcome.Success) prependVendor(outcome.data) }
            }
        }
    }

    internal suspend fun resolveOrCreateFilament(
        api: SpoolmanApi,
        vendor: SpoolmanVendor,
        materialName: String,
        req: NewFilamentRequest,
    ): SpoolmanOutcome<SpoolmanFilament> {
        val vendorId = vendor.id ?: return SpoolmanOutcome.ParseError(
            IllegalStateException("vendor.id missing for ${vendor.name}"),
        )
        val variantNormalised = req.variant?.trim()?.takeIf { it.isNotEmpty() }
        return performHttp("listFilaments") { api.listFilaments() }.flatMap { list ->
            val match = list.firstOrNull { f ->
                if (f.vendor?.id != vendorId) return@firstOrNull false
                if (!(f.material ?: "").equals(materialName, ignoreCase = true)) return@firstOrNull false
                if ((f.color_hex ?: "") != req.colorHex) return@firstOrNull false
                // Match variant from extra.variant only. filament.name is the
                // full display name ("Polymaker PLA Matte"), not the variant.
                val existingVariant = decodeJsonString(f.extra?.get("variant"))
                existingVariant == variantNormalised
            }
            if (match != null) {
                android.util.Log.d(
                    "SpoolmanRepo",
                    "filament match hit: id=${match.id} name=${match.name} variant=$variantNormalised existingVariant=${decodeJsonString(match.extra?.get("variant"))}",
                )
                SpoolmanOutcome.Success(match)
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
                                density = densityFor(materialName),
                                diameter = DEFAULT_DIAMETER_MM,
                                weight = DEFAULT_WEIGHT_G,
                                extra = extras,
                            ),
                        )
                    }.also { outcome -> if (outcome is SpoolmanOutcome.Success) prependFilament(outcome.data) }
                }
            }
        }
    }

    internal suspend fun createSpoolStep(
        api: SpoolmanApi,
        filament: SpoolmanFilament,
    ): SpoolmanOutcome<SpoolmanSpool> {
        return performHttp("createSpool") {
            api.createSpool(CreateSpoolRequest(filament_id = filament.id, extra = null))
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

        // 3D-printing consumer standard. Bambu, Prusa, etc. all ship 1.75 mm.
        // Spoolman requires diameter > 0; user can edit in Spoolman UI later.
        const val DEFAULT_DIAMETER_MM: Float = 1.75f

        // 1 kg net weight — typical consumer spool. User can correct in
        // Spoolman web UI for partial / 750 g / 5 kg / etc. Surfacing in the
        // app form is U8/U9 scope.
        const val DEFAULT_WEIGHT_G: Float = 1000f

        // g/cm³ averages by material. Required by Spoolman (gt=0). Sourced
        // from filament-vendor datasheets — close enough for spool tracking.
        fun densityFor(materialName: String): Float = when (materialName.uppercase()) {
            "PLA" -> 1.24f
            "ABS" -> 1.04f
            "PETG", "PET" -> 1.27f
            "TPU" -> 1.20f
            "ASA" -> 1.07f
            "PC" -> 1.20f
            "NYLON", "PA" -> 1.14f
            "PVA" -> 1.19f
            "HIPS" -> 1.04f
            else -> 1.24f
        }
    }
}

class UrlNotConfiguredException : IOException("Spoolman URL not configured")
