package com.spoolpainter.app.ui.screens.main

import app.cash.turbine.test
import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.domain.usecases.SaveToSpoolmanResult
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.domain.usecases.VendorUidOnlyPairResult
import com.spoolpainter.app.hardware.nfc.TagBuffer
import com.spoolpainter.app.support.FakeCreateAndPairUseCase
import com.spoolpainter.app.support.FakeMoveOnBindConfirmer
import com.spoolpainter.app.support.FakeMoveOnBindUseCase
import com.spoolpainter.app.support.FakeNfcRepository
import com.spoolpainter.app.support.FakeRawWriteUseCase
import com.spoolpainter.app.support.FakeSaveToSpoolmanUseCase
import com.spoolpainter.app.support.FakeSettingsRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.support.FakeTwoTagUseCase
import com.spoolpainter.app.support.FakeVendorUidOnlyPairUseCase
import com.spoolpainter.app.ui.common.UiEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * U13 — `MainViewModel.onSaveTapped` covers the new Spoolman-only Save path.
 * Vendor-tag Save routes to vendor UID-only pair (Q-U13-1=A); RawNoUrl Save
 * is a no-op (canSave is false in that mode).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSaveTapTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val nfc = FakeNfcRepository()
    private val spoolman = FakeSpoolmanRepository()
    private val settings = FakeSettingsRepository()
    private val createAndPair = FakeCreateAndPairUseCase(nfc = nfc, spoolman = spoolman)
    private val saveToSpoolman = FakeSaveToSpoolmanUseCase(spoolman = spoolman)
    private val twoTag = FakeTwoTagUseCase(nfc = nfc, spoolman = spoolman)
    private val confirmer = FakeMoveOnBindConfirmer()
    private val moveOnBind = FakeMoveOnBindUseCase()
    private val rawWrite = FakeRawWriteUseCase(nfc = nfc)
    private val vendorUidOnlyPair = FakeVendorUidOnlyPairUseCase(spoolman = spoolman, moveOnBind = moveOnBind)
    private val materialBrandRepo = FakeMaterialBrandRepository()

    private val sampleUid = CardUid("AA11BB22")

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun newVm(): MainViewModel = MainViewModel(
        nfc = nfc,
        spoolman = spoolman,
        settings = settings,
        materialBrandRepo = materialBrandRepo,
        readAndPair = ReadAndPairUseCase(nfc, spoolman),
        saveToSpoolman = saveToSpoolman,
        createAndPair = createAndPair,
        twoTag = twoTag,
        confirmer = confirmer,
        rawWrite = rawWrite,
        vendorUidOnlyPair = vendorUidOnlyPair,
    )

    private fun primeFormForSave(vm: MainViewModel) {
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        vm.onMaterialPicked(Material("PLA", 190, 220, 55, 65))
        vm.onBrandPicked(Brand("Bambu"))
        vm.onColorHexChanged("FF0000")
        vm.onTempRangesChanged(TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60))
    }

    @Test fun `new-spool save - auto-selects spool and emits Saved snackbar`() = runTest {
        val vm = newVm()
        primeFormForSave(vm)
        saveToSpoolman.nextResult = SaveToSpoolmanResult.Success.Saved(spoolId = 99, isNewSpool = true)

        vm.effects.test {
            vm.onSaveTapped()
            // Snackbar fires synchronously under UnconfinedTestDispatcher; the
            // first non-ambient emission is the Save outcome.
            val saved = awaitNonAmbient(this)
            assertTrue("got $saved", saved.message.contains("Saved spool #99"))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(99, vm.state.value.spoolman.selectedSpoolId)
        assertEquals(99, vm.state.value.form.selectedSpoolId)
    }

    @Test fun `existing-spool patch - emits Updated snackbar`() = runTest {
        val vm = newVm()
        // Pre-select a spool; onSpoolSelected fires FormMapping which fills
        // identity/spec from Spoolman.
        val existingSpool = SpoolmanSpool(
            id = 42,
            filament = SpoolmanFilament(
                id = 7,
                name = "PLA Red",
                material = "PLA",
                vendor = com.spoolpainter.app.domain.models.SpoolmanVendor(id = 1, name = "Bambu"),
                color_hex = "FF0000",
                settings_extruder_temp = 200,
                settings_bed_temp = 50,
            ),
        )
        spoolman.setSpools(listOf(existingSpool))
        // Ensure WriteMode.Spoolman before selecting.
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        vm.onSpoolSelected(existingSpool)
        assertEquals(true, vm.canSave.value)
        saveToSpoolman.nextResult = SaveToSpoolmanResult.Success.Saved(spoolId = 42, isNewSpool = false)

        vm.effects.test {
            vm.onSaveTapped()
            val updated = awaitNonAmbient(this)
            assertTrue("got $updated", updated.message.contains("Updated spool #42"))
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, saveToSpoolman.invokeCalls)
    }

    @Test fun `Spoolman failure surfaces humanReadable snackbar`() = runTest {
        val vm = newVm()
        primeFormForSave(vm)
        saveToSpoolman.nextResult = SaveToSpoolmanResult.Failed(
            SpoolmanOutcome.HttpError(500, "boom"),
        )

        vm.effects.test {
            vm.onSaveTapped()
            val failed = awaitNonAmbient(this)
            assertTrue("got $failed", failed.message.contains("500"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `vendor-tag observed does NOT divert Save to vendor pair (2026-06-06 reframe)`() = runTest {
        // 2026-06-06: vendor UID mapping moved off Save and onto Write so
        // each button has one job. Save stays a pure HTTP form-edit
        // action even when a vendor tag is observed. UID mapping happens
        // when the user taps Write (see MainViewModelTest vendor + Write).
        // Vendor state is reached via explicit Read (nfc.state.Success);
        // passive ambient taps no longer flip observedTagKind.
        val vm = newVm()
        primeFormForSave(vm)
        nfc.pushState(
            com.spoolpainter.app.domain.primitives.NfcResult.Success(
                sampleUid,
                TagClassification.Vendor(reason = "factory"),
            ),
        )
        assertEquals(ObservedTagKind.Vendor, vm.state.value.observedTagKind)

        saveToSpoolman.nextResult = SaveToSpoolmanResult.Success.Saved(spoolId = 50, isNewSpool = true)
        vm.onSaveTapped()
        // Save = HTTP form edits only. vendorUidOnlyPair is NOT invoked.
        assertEquals(0, vendorUidOnlyPair.invokeCalls)
        assertEquals(1, saveToSpoolman.invokeCalls)
    }

    @Test fun `RawNoUrl Save is a no-op (canSave false)`() = runTest {
        val vm = newVm()
        // No URL → WriteMode.RawNoUrl → canSave should be false.
        vm.onMaterialPicked(Material("PLA", 190, 220, 55, 65))
        vm.onBrandPicked(Brand("Bambu"))
        vm.onColorHexChanged("FF0000")
        vm.onTempRangesChanged(TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60))
        assertEquals(WriteMode.RawNoUrl, vm.state.value.writeMode)
        assertEquals(false, vm.canSave.value)
        vm.onSaveTapped()
        assertEquals(0, saveToSpoolman.invokeCalls)
    }

    @Test fun `URL not configured surfaces Configure-in-Settings snackbar`() = runTest {
        val vm = newVm()
        primeFormForSave(vm)
        saveToSpoolman.nextResult = SaveToSpoolmanResult.UrlNotConfigured

        vm.effects.test {
            vm.onSaveTapped()
            val msg = awaitNonAmbient(this)
            assertTrue("got $msg", msg.message.contains("Configure Spoolman"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun awaitNonAmbient(turbine: app.cash.turbine.ReceiveTurbine<UiEffect>): UiEffect.ShowSnackbar {
        val ambient = setOf(
            "Tag detected. Press Read tag to load.",
            "Tag detected. Press Read to load.",
            "Blank tag detected.",
            "Vendor tag. Press Read to load.",
        )
        while (true) {
            val effect = turbine.awaitItem() as UiEffect.ShowSnackbar
            if (effect.message in ambient) continue
            return effect
        }
    }
}
