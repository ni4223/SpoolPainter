package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.spoolpainter.app.data.local.SettingsRepository
import com.spoolpainter.app.di.AppScope
import com.spoolpainter.app.di.IoDispatcher
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
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
            }
            .launchIn(scope)
    }

    suspend fun probe(): SpoolmanOutcome<Unit> {
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("probe") { api.getInfo() }.map { }
    }

    open suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>> {
        if (uid.hex.isEmpty()) return SpoolmanOutcome.Success(emptyList())
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("findSpoolsByCardUid") {
            api.findSpoolsByLotNr("${CardUidEncoding.PREFIX}${uid.hex}")
        }
    }

    open suspend fun getSpool(spoolId: Int): SpoolmanOutcome<SpoolmanSpool> {
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("getSpool") { api.getSpool(spoolId) }
    }

    suspend fun appendCardUidToSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool> {
        if (uid.hex.isEmpty()) return invalidArg("uid is empty")
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("getSpool") { api.getSpool(spoolId) }
            .flatMap { spool ->
                val decoded = CardUidEncoding.decode(spool.lot_nr ?: "")
                val newUids = (decoded.uids + uid).distinct()
                val encoded = CardUidEncoding.encode(newUids, decoded.opaque)
                performHttp("patchSpoolLotNr") {
                    api.patchSpoolLotNr(spoolId, UpdateSpoolLotNrRequest(encoded))
                }
            }
            .also { outcome -> if (outcome is SpoolmanOutcome.Success) replaceSpoolInCache(outcome.data) }
    }

    suspend fun removeCardUidFromSpool(spoolId: Int, uid: CardUid): SpoolmanOutcome<SpoolmanSpool> {
        if (uid.hex.isEmpty()) return invalidArg("uid is empty")
        val api = cachedApi ?: return urlNotConfigured()
        return performHttp("getSpool") { api.getSpool(spoolId) }
            .flatMap { spool ->
                val decoded = CardUidEncoding.decode(spool.lot_nr ?: "")
                val newUids = decoded.uids.filterNot { it == uid }
                val encoded = CardUidEncoding.encode(newUids, decoded.opaque)
                performHttp("patchSpoolLotNr") {
                    api.patchSpoolLotNr(spoolId, UpdateSpoolLotNrRequest(encoded))
                }
            }
            .also { outcome -> if (outcome is SpoolmanOutcome.Success) replaceSpoolInCache(outcome.data) }
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
        val spoolsOutcome = performHttp("listSpools") { api.listSpools() }
        if (spoolsOutcome !is SpoolmanOutcome.Success) {
            @Suppress("UNCHECKED_CAST")
            return spoolsOutcome as SpoolmanOutcome<Unit>
        }
        _vendors.value = vendorsOutcome.data
        _filaments.value = filamentsOutcome.data
        _spools.value = spoolsOutcome.data
        return SpoolmanOutcome.Success(Unit)
    }

    suspend fun createSpoolForNewFilament(req: NewSpoolRequest): SpoolmanOutcome<SpoolmanSpool> {
        if (req.cardUid.hex.isEmpty()) return invalidArg("cardUid is empty")
        val vendorName = req.vendorName.trim().takeIf { it.isNotEmpty() }
            ?: return invalidArg("vendorName is empty")
        val materialName = req.materialName.trim().takeIf { it.isNotEmpty() }
            ?: return invalidArg("materialName is empty")
        val api = cachedApi ?: return urlNotConfigured()

        return resolveOrCreateVendor(api, vendorName)
            .flatMap { vendor -> resolveOrCreateFilament(api, vendor, materialName, req) }
            .flatMap { filament -> createSpoolStep(api, filament, req.cardUid) }
            .also { outcome -> if (outcome is SpoolmanOutcome.Success) prependSpool(outcome.data) }
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
        req: NewSpoolRequest,
    ): SpoolmanOutcome<SpoolmanFilament> {
        val vendorId = vendor.id ?: return SpoolmanOutcome.ParseError(
            IllegalStateException("vendor.id missing for ${vendor.name}"),
        )
        val variantNormalised = req.variant?.trim()?.takeIf { it.isNotEmpty() }
        return performHttp("listFilaments") { api.listFilaments() }.flatMap { list ->
            val match = list.firstOrNull { f ->
                f.vendor?.id == vendorId &&
                    (f.material ?: "").equals(materialName, ignoreCase = true) &&
                    (f.color_hex ?: "") == req.colorHex &&
                    (f.name?.trim()?.takeIf { it.isNotEmpty() } == variantNormalised)
            }
            if (match != null) {
                SpoolmanOutcome.Success(match)
            } else {
                performHttp("createFilament") {
                    api.createFilament(
                        CreateFilamentRequest(
                            name = variantNormalised,
                            vendor_id = vendorId,
                            material = materialName,
                            color_hex = req.colorHex,
                            settings_extruder_temp = req.tempRanges.extruderMin,
                            settings_bed_temp = req.tempRanges.bedMin,
                        ),
                    )
                }.also { outcome -> if (outcome is SpoolmanOutcome.Success) prependFilament(outcome.data) }
            }
        }
    }

    internal suspend fun createSpoolStep(
        api: SpoolmanApi,
        filament: SpoolmanFilament,
        uid: CardUid,
    ): SpoolmanOutcome<SpoolmanSpool> {
        return performHttp("createSpool") {
            api.createSpool(
                CreateSpoolRequest(
                    filament_id = filament.id,
                    lot_nr = "${CardUidEncoding.PREFIX}${uid.hex}",
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
}

class UrlNotConfiguredException : IOException("Spoolman URL not configured")
