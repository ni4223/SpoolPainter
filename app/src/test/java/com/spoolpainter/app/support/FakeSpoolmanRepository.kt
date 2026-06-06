package com.spoolpainter.app.support

import com.spoolpainter.app.data.remote.spoolman.ConnectivityState
import com.spoolpainter.app.data.remote.spoolman.ExpanderOverrides
import com.spoolpainter.app.data.remote.spoolman.NewSpoolBundle
import com.spoolpainter.app.data.remote.spoolman.OrphanSpool
import com.spoolpainter.app.data.remote.spoolman.PatchFilamentBody
import com.spoolpainter.app.data.remote.spoolman.SpoolPatchBody
import com.spoolpainter.app.data.remote.spoolman.SpoolmanApi
import com.spoolpainter.app.data.remote.spoolman.SpoolmanApiFactory
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.usecases.NewFilamentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSpoolmanRepository(
    private val settings: FakeSettingsRepository = FakeSettingsRepository(),
) : SpoolmanRepository(
    settings = settings,
    apiFactory = NoopApiFactory,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ioDispatcher = Dispatchers.Unconfined,
) {

    private val _spools = MutableStateFlow<List<SpoolmanSpool>>(emptyList())
    override val spools: StateFlow<List<SpoolmanSpool>> = _spools.asStateFlow()

    private val _vendors = MutableStateFlow<List<SpoolmanVendor>>(emptyList())
    override val vendors: StateFlow<List<SpoolmanVendor>> = _vendors.asStateFlow()

    private val _filaments = MutableStateFlow<List<SpoolmanFilament>>(emptyList())
    override val filaments: StateFlow<List<SpoolmanFilament>> = _filaments.asStateFlow()

    private val _connectivity = MutableStateFlow<ConnectivityState>(ConnectivityState.Unknown)
    override val connectivity: StateFlow<ConnectivityState> = _connectivity.asStateFlow()

    fun setConnectivity(state: ConnectivityState) {
        _connectivity.value = state
    }

    var nextFindSpoolsByCardUidResult: SpoolmanOutcome<List<SpoolmanSpool>> =
        SpoolmanOutcome.Success(emptyList())

    var nextGetSpoolResult: SpoolmanOutcome<SpoolmanSpool> =
        SpoolmanOutcome.HttpError(404, "Not found")

    var nextAppendCardUidResult: SpoolmanOutcome<SpoolmanSpool>? = null
    var nextRemoveCardUidResult: SpoolmanOutcome<SpoolmanSpool>? = null
    var nextCreateSpoolResult: SpoolmanOutcome<SpoolmanSpool>? = null
    var nextTestConnectionResult: SpoolmanOutcome<String>? = null
    var nextEnsureExtraFieldsResult: SpoolmanOutcome<Unit>? = null
    var nextGetFilamentResult: SpoolmanOutcome<SpoolmanFilament>? = null
    var lastGetFilamentId: Int? = null
        private set
    var getFilamentCalls: Int = 0
        private set

    var lastFindUid: CardUid? = null
        private set

    var lastGetSpoolId: Int? = null
        private set

    var getSpoolCalls: Int = 0
        private set

    var appendCalls: Int = 0
        private set
    var lastAppend: Pair<Int, CardUid>? = null
        private set

    var removeCalls: Int = 0
        private set

    var createSpoolCalls: Int = 0
        private set
    var lastCreateRequest: NewFilamentRequest? = null
        private set

    var nextCreateSpoolForExistingFilamentResult: SpoolmanOutcome<SpoolmanSpool>? = null
    var createSpoolForExistingFilamentCalls: Int = 0
        private set
    var lastCreateForExisting: Pair<Int, ExpanderOverrides>? = null
        private set

    var nextPatchFilamentResult: SpoolmanOutcome<SpoolmanFilament>? = null
    var patchFilamentCalls: Int = 0
        private set
    var lastPatchFilament: Pair<Int, PatchFilamentBody>? = null
        private set

    override suspend fun findSpoolsByCardUid(uid: CardUid): SpoolmanOutcome<List<SpoolmanSpool>> {
        lastFindUid = uid
        return nextFindSpoolsByCardUidResult
    }

    override suspend fun getSpool(spoolId: Int): SpoolmanOutcome<SpoolmanSpool> {
        lastGetSpoolId = spoolId
        getSpoolCalls++
        return nextGetSpoolResult
    }

    override suspend fun getFilament(filamentId: Int): SpoolmanOutcome<SpoolmanFilament> {
        lastGetFilamentId = filamentId
        getFilamentCalls++
        return nextGetFilamentResult
            ?: SpoolmanOutcome.HttpError(404, "not found")
    }

    override suspend fun appendCardUidToSpool(
        spoolId: Int,
        uid: CardUid,
    ): SpoolmanOutcome<SpoolmanSpool> {
        appendCalls++
        lastAppend = spoolId to uid
        return nextAppendCardUidResult
            ?: SpoolmanOutcome.ParseError(IllegalStateException("nextAppendCardUidResult not set"))
    }

    override suspend fun removeCardUidFromSpool(
        spoolId: Int,
        uid: CardUid,
    ): SpoolmanOutcome<SpoolmanSpool> {
        removeCalls++
        return nextRemoveCardUidResult
            ?: SpoolmanOutcome.ParseError(IllegalStateException("nextRemoveCardUidResult not set"))
    }

    override suspend fun createSpoolForNewFilament(
        req: NewFilamentRequest,
    ): SpoolmanOutcome<SpoolmanSpool> {
        createSpoolCalls++
        lastCreateRequest = req
        return nextCreateSpoolResult
            ?: SpoolmanOutcome.ParseError(IllegalStateException("nextCreateSpoolResult not set"))
    }

    /** Mirror of [createSpoolForNewFilament]; pretends the vendor and filament
     *  were both freshly POSTed. Tests that need to assert the reused-record
     *  branch can override [nextCreateSpoolBundleResult] directly. */
    override suspend fun createSpoolForNewFilamentBundle(
        req: NewFilamentRequest,
    ): SpoolmanOutcome<NewSpoolBundle> {
        createSpoolBundleCalls++
        nextCreateSpoolBundleResult?.let { return it }
        return when (val outcome = createSpoolForNewFilament(req)) {
            is SpoolmanOutcome.Success -> SpoolmanOutcome.Success(
                NewSpoolBundle(
                    spool = outcome.data,
                    filamentWasFresh = true,
                    vendorWasFresh = true,
                    filamentId = outcome.data.filament.id,
                    vendorId = outcome.data.filament.vendor?.id,
                ),
            )
            is SpoolmanOutcome.HttpError -> outcome
            is SpoolmanOutcome.NetworkError -> outcome
            is SpoolmanOutcome.ParseError -> outcome
        }
    }

    var createSpoolBundleCalls: Int = 0
    var nextCreateSpoolBundleResult: SpoolmanOutcome<NewSpoolBundle>? = null

    override suspend fun chainDeleteOrphan(orphan: OrphanSpool): SpoolmanOutcome<Unit> {
        chainDeleteOrphanCalls += orphan
        return nextChainDeleteOrphanResult ?: SpoolmanOutcome.Success(Unit)
    }

    val chainDeleteOrphanCalls: MutableList<OrphanSpool> = mutableListOf()
    var nextChainDeleteOrphanResult: SpoolmanOutcome<Unit>? = null

    override suspend fun createSpoolForExistingFilament(
        filamentId: Int,
        expanderOverrides: ExpanderOverrides,
    ): SpoolmanOutcome<SpoolmanSpool> {
        createSpoolForExistingFilamentCalls++
        lastCreateForExisting = filamentId to expanderOverrides
        return nextCreateSpoolForExistingFilamentResult
            ?: SpoolmanOutcome.ParseError(IllegalStateException("nextCreateSpoolForExistingFilamentResult not set"))
    }

    override suspend fun patchFilament(
        filamentId: Int,
        body: PatchFilamentBody,
    ): SpoolmanOutcome<SpoolmanFilament> {
        patchFilamentCalls++
        lastPatchFilament = filamentId to body
        return nextPatchFilamentResult
            ?: SpoolmanOutcome.ParseError(IllegalStateException("nextPatchFilamentResult not set"))
    }

    var applyOverridesToFilamentOfSpoolCalls: Int = 0
        private set
    var lastApplyOverridesToFilamentOfSpool: Pair<Int, ExpanderOverrides>? = null
        private set
    var nextApplyOverridesToFilamentOfSpoolResult: SpoolmanOutcome<SpoolmanFilament>? = null

    override suspend fun applyOverridesToFilamentOfSpool(
        spoolId: Int,
        overrides: ExpanderOverrides,
    ): SpoolmanOutcome<SpoolmanFilament> {
        applyOverridesToFilamentOfSpoolCalls++
        lastApplyOverridesToFilamentOfSpool = spoolId to overrides
        // Default: pretend the patch succeeded with a stub filament so the
        // use case continues. Tests that need a specific result can set
        // nextApplyOverridesToFilamentOfSpoolResult.
        return nextApplyOverridesToFilamentOfSpoolResult
            ?: SpoolmanOutcome.Success(
                SpoolmanFilament(id = 0, vendor = null, color_hex = "FFFFFF", material = null)
            )
    }

    var applyVariantToFilamentOfSpoolCalls: Int = 0
        private set
    var lastApplyVariantToFilamentOfSpool: Pair<Int, String>? = null
        private set
    var nextApplyVariantToFilamentOfSpoolResult: SpoolmanOutcome<SpoolmanFilament>? = null

    override suspend fun applyVariantToFilamentOfSpool(
        spoolId: Int,
        variant: String,
    ): SpoolmanOutcome<SpoolmanFilament> {
        applyVariantToFilamentOfSpoolCalls++
        lastApplyVariantToFilamentOfSpool = spoolId to variant
        return nextApplyVariantToFilamentOfSpoolResult
            ?: SpoolmanOutcome.Success(
                SpoolmanFilament(id = 0, vendor = null, color_hex = "FFFFFF", material = null)
            )
    }

    // F-6 (v2.0.3) — refreshIfStale call counters for test assertions.
    // Tracks force=true vs force=false separately so PTR vs foreground/Read
    // paths can be verified independently without relying on the real
    // refresh() implementation (which needs a configured URL + factory).
    var refreshIfStaleCalls: Int = 0
        private set
    var refreshIfStaleForceCalls: Int = 0
        private set
    var nextRefreshIfStaleResult: SpoolmanOutcome<Unit> = SpoolmanOutcome.Success(Unit)

    override suspend fun refreshIfStale(force: Boolean): SpoolmanOutcome<Unit> {
        refreshIfStaleCalls++
        if (force) refreshIfStaleForceCalls++
        return nextRefreshIfStaleResult
    }

    var patchSpoolFieldsCalls: Int = 0
        private set
    var lastPatchSpoolFields: Pair<Int, SpoolPatchBody>? = null
        private set
    var nextPatchSpoolFieldsResult: SpoolmanOutcome<SpoolmanSpool>? = null

    override suspend fun patchSpoolFields(
        spoolId: Int,
        body: SpoolPatchBody,
    ): SpoolmanOutcome<SpoolmanSpool> {
        patchSpoolFieldsCalls++
        lastPatchSpoolFields = spoolId to body
        return nextPatchSpoolFieldsResult
            ?: SpoolmanOutcome.Success(
                SpoolmanSpool(
                    id = spoolId,
                    filament = SpoolmanFilament(id = 0, vendor = null, color_hex = "FFFFFF", material = null),
                ),
            )
    }

    override suspend fun testConnection(): SpoolmanOutcome<String> =
        nextTestConnectionResult ?: SpoolmanOutcome.Success("test")

    override suspend fun ensureExtraFieldsRegistered(): SpoolmanOutcome<Unit> =
        nextEnsureExtraFieldsResult ?: SpoolmanOutcome.Success(Unit)

    fun setSpools(spools: List<SpoolmanSpool>) {
        _spools.value = spools
    }

    fun setFilaments(filaments: List<SpoolmanFilament>) {
        _filaments.value = filaments
    }

    fun setVendors(vendors: List<SpoolmanVendor>) {
        _vendors.value = vendors
    }

    private object NoopApiFactory : SpoolmanApiFactory(
        okHttpClient = okhttp3.OkHttpClient(),
        gson = com.google.gson.Gson(),
    ) {
        override fun create(baseUrl: String): SpoolmanApi {
            error("FakeSpoolmanRepository must not call SpoolmanApiFactory.create")
        }
    }
}
