package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.support.FakeCreateAndPairUseCase
import com.spoolpainter.app.support.FakeMoveOnBindConfirmer
import com.spoolpainter.app.support.FakeMoveOnBindUseCase
import com.spoolpainter.app.support.FakeNfcRepository
import com.spoolpainter.app.support.FakeRawWriteUseCase
import com.spoolpainter.app.support.FakeSettingsRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.support.FakeTwoTagUseCase
import com.spoolpainter.app.support.FakeVendorUidOnlyPairUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelMoreDetailsExpanderTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val nfc = FakeNfcRepository()
    private val spoolman = FakeSpoolmanRepository()
    private val settings = FakeSettingsRepository()
    private val createAndPair = FakeCreateAndPairUseCase(nfc = nfc, spoolman = spoolman)
    private val twoTag = FakeTwoTagUseCase(nfc = nfc, spoolman = spoolman)
    private val confirmer = FakeMoveOnBindConfirmer()
    private val moveOnBind = FakeMoveOnBindUseCase()
    private val rawWrite = FakeRawWriteUseCase(nfc = nfc)
    private val vendorUidOnlyPair = FakeVendorUidOnlyPairUseCase(spoolman = spoolman, moveOnBind = moveOnBind)
    private val materialBrandRepo = FakeMaterialBrandRepository()

    @Before fun setUp() = Dispatchers.setMain(testDispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun newVm(): MainViewModel = MainViewModel(
        nfc = nfc,
        spoolman = spoolman,
        settings = settings,
        materialBrandRepo = materialBrandRepo,
        readAndPair = ReadAndPairUseCase(nfc, spoolman),
        createAndPair = createAndPair,
        twoTag = twoTag,
        confirmer = confirmer,
        rawWrite = rawWrite,
        vendorUidOnlyPair = vendorUidOnlyPair,
    )

    @Test fun `default state — collapsed, weight diameter density prefilled, spool weight & price null`() = runTest {
        val form = newVm().state.value.form
        assertFalse(form.moreDetailsExpanded)
        // Spoolman requires density/diameter/weight gt 0; these are pre-filled
        // with the same defaults the call site would send, so the user sees
        // exactly what gets persisted. Empty-spool weight + price stay null
        // because they vary too much to default.
        assertNull(form.emptySpoolWeightG)
        assertNull(form.priceMajor)
        assertEquals(1000f, form.fullSpoolWeightG)
        assertEquals(1.75f, form.diameterMm)
        assertEquals(1.24f, form.densityGPerCm3)
    }

    @Test fun `toggle flips moreDetailsExpanded`() = runTest {
        val vm = newVm()
        vm.onMoreDetailsToggled()
        assertTrue(vm.state.value.form.moreDetailsExpanded)
        vm.onMoreDetailsToggled()
        assertFalse(vm.state.value.form.moreDetailsExpanded)
    }

    @Test fun `value parsing — empty string sets null override`() = runTest {
        val vm = newVm()
        vm.onPriceChanged("19.99")
        vm.onPriceChanged("")
        assertNull(vm.state.value.form.priceMajor)
    }

    @Test fun `value parsing — valid decimal sets Float`() = runTest {
        val vm = newVm()
        vm.onDensityChanged("1.21")
        assertEquals(1.21f, vm.state.value.form.densityGPerCm3)
    }

    @Test fun `value parsing — invalid input keeps prior value`() = runTest {
        val vm = newVm()
        vm.onDiameterChanged("1.75")
        vm.onDiameterChanged("abc")
        assertEquals(1.75f, vm.state.value.form.diameterMm)
    }

    @Test fun `prefill from filament — onFilamentSelected with density 1_30 → form densityGPerCm3 == 1_30f`() = runTest {
        val filament = SpoolmanFilament(
            id = 1,
            name = "PLA Red",
            material = "PLA",
            vendor = SpoolmanVendor(id = 1, name = "Polymaker"),
            color_hex = "FF0000",
            density = 1.30f,
        )
        spoolman.setFilaments(listOf(filament))
        val vm = newVm()
        vm.onFilamentSelected(filament)
        assertEquals(1.30f, vm.state.value.form.densityGPerCm3)
    }
}
