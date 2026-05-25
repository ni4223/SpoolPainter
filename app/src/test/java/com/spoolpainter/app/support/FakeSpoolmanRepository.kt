package com.spoolpainter.app.support

import com.spoolpainter.app.data.remote.spoolman.SpoolmanApi
import com.spoolpainter.app.data.remote.spoolman.SpoolmanApiFactory
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
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

    var lastFindUid: CardUid? = null
        private set

    var lastGetSpoolId: Int? = null
        private set

    var getSpoolCalls: Int = 0
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

    fun setSpools(spools: List<SpoolmanSpool>) {
        _spools.value = spools
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
