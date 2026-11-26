package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.FilamentPatchResult
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.ui.screens.main.FormState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    fun `new spool Saved carries the matched filament's article number fill`() = runTest {
        val fill = CompletableDeferred<SpoolmanOutcome<SpoolmanFilament>>()
        spoolman.nextCreateSpoolBundleResult = SpoolmanOutcome.Success(
            com.spoolpainter.app.data.remote.spoolman.NewSpoolBundle(
                spool = sampleSpool,
                filamentWasFresh = false,
                vendorWasFresh = false,
                filamentId = 7,
                vendorId = null,
                articleNumberFill = fill,
            ),
        )

        val result = useCase.invoke(input())

        assertSame(fill, (result as SaveToSpoolmanResult.Success.Saved).articleNumberFill)
    }

    // U32 B2: an unchanged Save made no HTTP write, so it must not say "Updated".
    @Test
    fun `existingSpool with no edits returns NoChanges when nothing was written`() = runTest {
        spoolman.nextApplyOverridesToFilamentOfSpoolResult =
            SpoolmanOutcome.Success(FilamentPatchResult(sampleSpool.filament, wrote = false))
        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))
        assertTrue("got $result", result is SaveToSpoolmanResult.NoChanges)
        assertEquals(42, (result as SaveToSpoolmanResult.NoChanges).spoolId)
        assertEquals(1, spoolman.applyOverridesToFilamentOfSpoolCalls)
        assertEquals(0, spoolman.patchSpoolFieldsCalls)
    }

    @Test
    fun `existingSpool returns Saved when the filament PATCH wrote`() = runTest {
        spoolman.nextApplyOverridesToFilamentOfSpoolResult =
            SpoolmanOutcome.Success(FilamentPatchResult(sampleSpool.filament, wrote = true))
        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))
        assertTrue("got $result", result is SaveToSpoolmanResult.Success.Saved)
    }

    @Test
    fun `existingSpool weight PATCH failure surfaces Failed, not NoChanges`() = runTest {
        spoolman.nextApplyOverridesToFilamentOfSpoolResult =
            SpoolmanOutcome.Success(FilamentPatchResult(sampleSpool.filament, wrote = false))
        spoolman.nextPatchSpoolFieldsResult = SpoolmanOutcome.HttpError(400, "initial_weight not set")
        val result = useCase.invoke(
            input(baseForm.copy(selectedSpoolId = 42, remainingWeightG = 500f, prefilledRemainingWeightG = 800f)),
        )
        assertTrue("got $result", result is SaveToSpoolmanResult.Failed)
    }

    @Test
    fun `existingSpool filament PATCH failure surfaces Failed`() = runTest {
        spoolman.nextApplyOverridesToFilamentOfSpoolResult = SpoolmanOutcome.HttpError(422, "bad")
        val result = useCase.invoke(input(baseForm.copy(selectedSpoolId = 42)))
        assertTrue("got $result", result is SaveToSpoolmanResult.Failed)
    }

    @Test
    fun `existingSpool with variant edit fires applyOverridesToFilamentOfSpool`() = runTest {
        spoolman.nextApplyOverridesToFilamentOfSpoolResult =
            SpoolmanOutcome.Success(FilamentPatchResult(sampleSpool.filament, wrote = true))
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

    // U32 B3: Spoolman caps remaining at the spool's initial_weight.
    @Test
    fun `remaining above initial weight raises the spool initial weight in the same PATCH`() = runTest {
        spoolman.nextPatchSpoolFieldsResult = SpoolmanOutcome.Success(sampleSpool)
        useCase.invoke(
            input(
                baseForm.copy(
                    selectedSpoolId = 42,
                    initialWeightG = 1000f,
                    remainingWeightG = 1050f,
                    prefilledRemainingWeightG = 800f,
                ),
            ),
        )
        val body = spoolman.lastPatchSpoolFields?.second
        assertEquals(1050f, body?.remaining_weight)
        assertEquals(1050f, body?.initial_weight)
    }

    @Test
    fun `remaining at or below initial weight sends remaining only`() = runTest {
        spoolman.nextPatchSpoolFieldsResult = SpoolmanOutcome.Success(sampleSpool)
        for (rem in listOf(1000f, 600f)) {
            useCase.invoke(
                input(
                    baseForm.copy(
                        selectedSpoolId = 42,
                        initialWeightG = 1000f,
                        remainingWeightG = rem,
                        prefilledRemainingWeightG = 800f,
                    ),
                ),
            )
            val body = spoolman.lastPatchSpoolFields?.second
            assertEquals(rem, body?.remaining_weight)
            assertEquals(null, body?.initial_weight)
        }
    }

    // U32 FN2: Spoolman rejects remaining_weight (400) when the spool's
    // initial_weight is null, so Save sends one in the same PATCH.
    private suspend fun bodyForNullInitial(fullSpoolWeightG: Float?, usedWeightG: Float?, rem: Float) =
        spoolman.let {
            it.nextPatchSpoolFieldsResult = SpoolmanOutcome.Success(sampleSpool)
            useCase.invoke(
                input(
                    baseForm.copy(
                        selectedSpoolId = 42,
                        initialWeightG = null,
                        fullSpoolWeightG = fullSpoolWeightG,
                        usedWeightG = usedWeightG,
                        remainingWeightG = rem,
                        prefilledRemainingWeightG = 800f,
                    ),
                ),
            )
            it.lastPatchSpoolFields?.second
        }

    @Test
    fun `null initial weight sends the filament weight as initial`() = runTest {
        val body = bodyForNullInitial(fullSpoolWeightG = 1000f, usedWeightG = 300f, rem = 650f)
        assertEquals(650f, body?.remaining_weight)
        assertEquals(1000f, body?.initial_weight)
    }

    @Test
    fun `null initial weight with no filament weight keeps used history`() = runTest {
        val body = bodyForNullInitial(fullSpoolWeightG = null, usedWeightG = 300f, rem = 650f)
        assertEquals(650f, body?.remaining_weight)
        assertEquals(950f, body?.initial_weight)
    }

    @Test
    fun `null initial weight is never below the new remaining`() = runTest {
        val body = bodyForNullInitial(fullSpoolWeightG = 1000f, usedWeightG = 0f, rem = 1050f)
        assertEquals(1050f, body?.initial_weight)
    }

    @Test
    fun `null initial weight is not sent when remaining is unchanged`() = runTest {
        spoolman.nextPatchSpoolFieldsResult = SpoolmanOutcome.Success(sampleSpool)
        useCase.invoke(
            input(
                baseForm.copy(
                    selectedSpoolId = 42,
                    remainingWeightG = 800f,
                    prefilledRemainingWeightG = 800f,
                    priceMajor = 20f,
                ),
            ),
        )
        val body = spoolman.lastPatchSpoolFields?.second
        assertEquals(20f, body?.price)
        assertEquals(null, body?.initial_weight)
        assertEquals(null, body?.remaining_weight)
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
