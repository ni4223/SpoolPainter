package com.spoolpainter.app.support

import com.spoolpainter.app.domain.usecases.SaveToSpoolmanInput
import com.spoolpainter.app.domain.usecases.SaveToSpoolmanResult
import com.spoolpainter.app.domain.usecases.SaveToSpoolmanUseCase

class FakeSaveToSpoolmanUseCase(
    spoolman: FakeSpoolmanRepository = FakeSpoolmanRepository(),
) : SaveToSpoolmanUseCase(spoolman) {

    var nextResult: SaveToSpoolmanResult = SaveToSpoolmanResult.Success.Saved(
        spoolId = 1,
        isNewSpool = true,
    )

    var invokeCalls: Int = 0
        private set
    var lastInput: SaveToSpoolmanInput? = null
        private set

    override suspend fun invoke(snapshot: SaveToSpoolmanInput): SaveToSpoolmanResult {
        invokeCalls++
        lastInput = snapshot
        return nextResult
    }
}
