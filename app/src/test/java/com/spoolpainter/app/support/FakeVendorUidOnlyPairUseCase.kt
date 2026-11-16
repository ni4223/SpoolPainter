package com.spoolpainter.app.support

import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.usecases.MoveOnBindUseCase
import com.spoolpainter.app.domain.usecases.VendorUidOnlyPairInput
import com.spoolpainter.app.domain.usecases.VendorUidOnlyPairResult
import com.spoolpainter.app.domain.usecases.VendorUidOnlyPairUseCase

class FakeVendorUidOnlyPairUseCase(
    spoolman: SpoolmanRepository,
    moveOnBind: MoveOnBindUseCase,
) : VendorUidOnlyPairUseCase(spoolman, moveOnBind) {
    var nextResult: VendorUidOnlyPairResult = VendorUidOnlyPairResult.Cancelled("not configured")
    var invokeCalls: Int = 0
        private set
    var lastInput: VendorUidOnlyPairInput? = null
        private set
    var nextDelayMs: Long = 0L

    /** When non-null, invoke() throws this instead of returning [nextResult] —
     *  drives the runNfcFlowSafely wedge-regression tests. */
    var nextThrowable: Throwable? = null

    override suspend fun invoke(input: VendorUidOnlyPairInput): VendorUidOnlyPairResult {
        invokeCalls++
        lastInput = input
        if (nextDelayMs > 0L) {
            kotlinx.coroutines.delay(nextDelayMs)
        }
        nextThrowable?.let { throw it }
        return nextResult
    }
}
