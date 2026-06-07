package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U13 — Save (Spoolman-only) use case. Covers spool resolution + variant +
 * spool-scope patches, plus failure / URL-not-configured paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveToSpoolmanUseCaseTest {

    private val spoolman = FakeSpoolmanRepository()
    private val useCase = SaveToSpoolmanUseCase(spoolman)

    private val sampleSpool = SpoolmanSpool(
        id = 42,
        filament = SpoolmanFilament(id = 7, material = "PLA"),
    )

    private val baseForm = FormState(
        material = Material("PLA", 190, 220, 55, 65),
        brand = Brand("Bambu"),
        colorHex = "FF0000",
        tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
    )

    private fun input(form: FormState = baseForm): SaveToSpoolmanInput =
        SaveToSpoolmanInput(form = form, newFilamentName = "Test", newFilamentVendor = "Test Vendor")

    @Test
    fun `existingSpool with no edits returns Saved (overrides flow on every Save - sparseDiff is the no-op gate)`() = runTest {
        // v2.1: existing-spool path now flows the full filament-record
        // override bag (color + density + diameter + weight + temps +
        // variant) on every Save. The actual sparse-vs-no-op check happens
        // inside SpoolmanRepository.patchFilament's sparseDiff. So the use
        // case always reports Saved when the overrides are non-empty
        // (the form has color/material/temps which are non-null defaults).
        spoolman.nextApplyOverridesToFilamentOfSpoolResult =
            SpoolmanOutcome.Success(sampleSpool.filament)
        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))
        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
        assertEquals(42, (result as SaveToSpoolmanResult.Success.Saved).spoolId)
        assertEquals(1, spoolman.applyOverridesToFilamentOfSpoolCalls)
        assertEquals(0, spoolman.patchSpoolFieldsCalls)
    }

    @Test
    fun `existingSpool with variant edit fires applyOverridesToFilamentOfSpool`() = runTest {
        spoolman.nextApplyOverridesToFilamentOfSpoolResult =
            SpoolmanOutcome.Success(sampleSpool.filament)
        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42, variant = "Matte")))

        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
        assertEquals(42, (result as SaveToSpoolmanResult.Success.Saved).spoolId)
        assertEquals(false, result.isNewSpool)
        assertEquals(1, spoolman.applyOverridesToFilamentOfSpoolCalls)
        assertEquals(42, spoolman.lastApplyOverridesToFilamentOfSpool?.first)
        assertEquals("Matte", spoolman.lastApplyOverridesToFilamentOfSpool?.second?.variant)
    }

    @Test
    fun `existingSpool with remaining edited fires patchSpoolFields`() = runTest {
        spoolman.nextPatchSpoolFieldsResult = SpoolmanOutcome.Success(sampleSpool)
        val result = useCase.invoke(
            input(
                baseForm.copy(
                    selectedSpoolId = 42,
                    remainingWeightG = 600f,
                    prefilledRemainingWeightG = 850f,
                ),
            ),
        )
        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
        assertEquals(1, spoolman.patchSpoolFieldsCalls)
        assertEquals(600f, spoolman.lastPatchSpoolFields?.second?.remaining_weight)
    }

    @Test
    fun `new-filament path POSTs createSpoolForNewFilamentBundle`() = runTest {
        val newSpool = sampleSpool.copy(id = 99)
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(newSpool)
        val result = useCase.invoke(input(baseForm))

        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
        val ok = result as SaveToSpoolmanResult.Success.Saved
        assertEquals(99, ok.spoolId)
        assertEquals(true, ok.isNewSpool)
        assertEquals(1, spoolman.createSpoolBundleCalls)
        assertEquals(99, useCase.lastResolvedSpoolId)
    }

    @Test
    fun `existing-filament + new-spool path POSTs createSpoolForExistingFilament`() = runTest {
        val newSpool = sampleSpool.copy(id = 77)
        spoolman.nextCreateSpoolForExistingFilamentResult = SpoolmanOutcome.Success(newSpool)
        val result = useCase.invoke(input(baseForm.copy(selectedFilamentId = 7)))

        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
        assertEquals(77, (result as SaveToSpoolmanResult.Success.Saved).spoolId)
        assertEquals(true, result.isNewSpool)
        assertEquals(1, spoolman.createSpoolForExistingFilamentCalls)
        assertEquals(0, spoolman.createSpoolBundleCalls)
    }

    @Test
    fun `vendor-tag Save creates spool same as a normal Save (vendor handled at VM layer)`() = runTest {
        // The Save use case itself doesn't know about vendor tags — that's a
        // ViewModel routing concern (Q-U13-1=A). Verify the pure HTTP path
        // still works regardless of what the form claims about a tag.
        val newSpool = sampleSpool.copy(id = 99)
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.Success(newSpool)
        val result = useCase.invoke(input(baseForm))

        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
        assertEquals(99, (result as SaveToSpoolmanResult.Success.Saved).spoolId)
    }

    @Test
    fun `Spoolman failure on new-filament path surfaces Failed(outcome)`() = runTest {
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.HttpError(503, "down")

        val result = useCase.invoke(input(baseForm))

        assertTrue("got $result", result is SaveToSpoolmanResult.Failed)
    }

    @Test
    fun `URL not configured surfaces UrlNotConfigured`() = runTest {
        spoolman.nextCreateSpoolResult = SpoolmanOutcome.NetworkError(UrlNotConfiguredException())

        val result = useCase.invoke(input(baseForm))

        assertEquals(SaveToSpoolmanResult.UrlNotConfigured, result)
    }
}
