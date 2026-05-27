package com.spoolpainter.app.support

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
