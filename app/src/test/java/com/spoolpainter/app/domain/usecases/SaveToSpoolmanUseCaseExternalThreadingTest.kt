package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SpoolmanDB import — SaveToSpoolmanInput.externalFilamentId / diameterMm
 * flow through fromForm into the repository's NewFilamentRequest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveToSpoolmanUseCaseExternalThreadingTest {

    private val spoolman = FakeSpoolmanRepository()
    private val useCase = SaveToSpoolmanUseCase(spoolman)

    private val form = FormState(
        material = Material("PLA", 190, 220, 55, 65),
        colorHex = "FF0000",
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
    )

    @Test
    fun `externalFilamentId and diameterMm ride the create request`() = runTest {
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(
            SpoolmanSpool(id = 5, filament = SpoolmanFilament(id = 9, material = "PLA")),
        )

        val result = useCase.invoke(
            SaveToSpoolmanInput(
                form = form,
                newFilamentName = "PolyLite PLA",
                newFilamentVendor = "Polymaker",
                externalFilamentId = "polymaker-polylite-pla",
                diameterMm = 2.85,
            ),
        )

        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
        val captured = spoolman.lastCreateRequest
        assertTrue("create request not captured", captured != null)
        assertEquals("polymaker-polylite-pla", captured!!.externalFilamentId)
        assertEquals(2.85, captured.diameterMm!!, 0.0)
    }

    @Test
    fun `without external fields request keeps null defaults`() = runTest {
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(
            SpoolmanSpool(id = 5, filament = SpoolmanFilament(id = 9, material = "PLA")),
        )

        useCase.invoke(
            SaveToSpoolmanInput(
                form = form,
                newFilamentName = "Test",
                newFilamentVendor = "Test Vendor",
            ),
        )

        assertNull(spoolman.lastCreateRequest?.externalFilamentId)
        assertNull(spoolman.lastCreateRequest?.diameterMm)
    }
}
