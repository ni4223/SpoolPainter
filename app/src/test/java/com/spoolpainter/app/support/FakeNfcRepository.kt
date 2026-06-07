package com.spoolpainter.app.support

import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.hardware.nfc.NfcAdapterWrapper
import com.spoolpainter.app.hardware.nfc.NfcRepository
import com.spoolpainter.app.hardware.nfc.TagBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

class FakeNfcRepository : NfcRepository(
    wrapper = noopWrapper,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ioDispatcher = Dispatchers.Unconfined,
    clock = Clock.System,
    settingsRepository = FakeSettingsRepository(),
    ttlMs = 5_000L,
) {

    private val _state = MutableStateFlow<NfcResult>(NfcResult.Idle)
    override val state: StateFlow<NfcResult> = _state.asStateFlow()

    private val _lastSeenTag = MutableStateFlow<TagBuffer?>(null)
    override val lastSeenTag: StateFlow<TagBuffer?> = _lastSeenTag.asStateFlow()

    private var nextConsumeLastSeen: NfcResult? = null

    var armCalls: Int = 0
        private set
    var disarmCalls: Int = 0
        private set
    var consumeLastSeenCalls: Int = 0
        private set
    var lastArmedIntent: NfcIntent? = null
        private set

    fun setNextRead(result: NfcResult) {
        // Push the result into the state flow when arm() is called.
        scheduledArmResult = result
    }

    fun queueArmResults(vararg results: NfcResult) {
        // FIFO queue of results pushed by successive arm() calls. If non-empty, dequeue takes
        // precedence over scheduledArmResult.
        armResultQueue.addAll(results.toList())
    }

    private var scheduledArmResult: NfcResult? = null
    private val armResultQueue: ArrayDeque<NfcResult> = ArrayDeque()

    fun setBufferedTap(result: NfcResult?) {
        nextConsumeLastSeen = result
    }

    override suspend fun arm(intent: NfcIntent) {
        armCalls++
        lastArmedIntent = intent
        _state.value = when (intent) {
            is NfcIntent.Read -> NfcResult.Reading
            is NfcIntent.Write -> NfcResult.Writing
            is NfcIntent.Verify -> NfcResult.Verifying
        }
        if (armResultQueue.isNotEmpty()) {
            _state.value = armResultQueue.removeFirst()
        } else {
            scheduledArmResult?.let { result ->
                _state.value = result
                scheduledArmResult = null
            }
        }
    }

    override suspend fun consumeLastSeen(intent: NfcIntent): NfcResult? {
        consumeLastSeenCalls++
        val result = nextConsumeLastSeen
        nextConsumeLastSeen = null
        if (result is NfcResult.Success) _state.value = result
        return result
    }

    override suspend fun disarm() {
        disarmCalls++
        _state.value = NfcResult.Idle
    }

    fun pushState(result: NfcResult) {
        _state.value = result
    }

    fun pushLastSeenTag(buffer: TagBuffer?) {
        _lastSeenTag.value = buffer
    }

    private companion object {
        val noopWrapper: NfcAdapterWrapper =
            NfcAdapterWrapper(adapter = null, dispatcher = Dispatchers.Unconfined)
    }
}
