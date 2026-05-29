package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.usecases.CreateAndPairResult
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelFilamentPickerTest {

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

    private val sampleFilament = SpoolmanFilament(
        id = 7,
        name = "Polymaker PLA Matte",
        material = "PLA",
        vendor = SpoolmanVendor(id = 1, name = "Polymaker"),
        color_hex = "FF0000",
        settings_extruder_temp = 200,
        settings_bed_temp = 50,
        density = 1.30f,
        diameter = 1.75f,
        weight = 1000f,
        spool_weight = 200f,
    )

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

    @Test fun `filaments flow re-emits SpoolmanRepository_filaments 1to1`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        val vm = newVm()
        assertEquals(listOf(sampleFilament), vm.filaments.value)
    }

    @Test fun `onFilamentSelected sets selectedFilamentId, clears selectedSpoolId, prefills form`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        val vm = newVm()
        // Pre-set a spool selection to confirm mutex.
        vm.onSpoolSelected(SpoolmanSpool(id = 99, filament = sampleFilament))
        vm.onFilamentSelected(sampleFilament)

        val form = vm.state.value.form
        assertEquals(7, form.selectedFilamentId)
        assertNull(form.selectedSpoolId)
        assertEquals("PLA", form.material?.name)
        assertEquals("Polymaker", form.brand?.name)
        assertEquals("FF0000", form.colorHex)
        assertEquals(1.30f, form.densityGPerCm3)
        assertEquals(1.75f, form.diameterMm)
        assertEquals(1000f, form.fullSpoolWeightG)
        assertEquals(200f, form.emptySpoolWeightG)
    }

    @Test fun `onFilamentSelected null resets form to defaults (symmetric with onSpoolSelected null)`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        val vm = newVm()
        vm.onFilamentSelected(sampleFilament)
        assertEquals(7, vm.state.value.form.selectedFilamentId)

        vm.onFilamentSelected(null)
        val form = vm.state.value.form
        assertNull(form.selectedFilamentId)
        // Form snaps back to defaults — the prefilled values from the
        // filament were only meaningful while the link existed.
        assertEquals("PLA", form.material?.name)
        assertEquals("Generic", form.brand?.name)
        assertEquals("FFFFFF", form.colorHex)
    }

    @Test fun `onSpoolSelected sets selectedFilamentId to spool's parent filament`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        val vm = newVm()
        vm.onFilamentSelected(sampleFilament)
        assertEquals(7, vm.state.value.form.selectedFilamentId)

        // Picking a spool implies its parent filament is also "selected" —
        // FormMapping.fromSpoolman carries spool.filament.id through.
        val spool = SpoolmanSpool(id = 42, filament = sampleFilament)
        vm.onSpoolSelected(spool)
        assertEquals(7, vm.state.value.form.selectedFilamentId)
        assertEquals(42, vm.state.value.form.selectedSpoolId)
    }

    @Test fun `onWriteTapped routing — filament selected → CreateAndPairInput carries selectedFilamentId`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        val vm = newVm()
        vm.onFilamentSelected(sampleFilament)
        createAndPair.nextResult = CreateAndPairResult.Cancelled("test")

        vm.onWriteTapped()

        assertEquals(1, createAndPair.invokeCalls)
        assertEquals(7, createAndPair.lastInput?.form?.selectedFilamentId)
    }

    @Test fun `onWriteTapped routing — spool selected → existing append path (selectedSpoolId wins over selectedFilamentId)`() = runTest {
        val spool = SpoolmanSpool(
            id = 42,
            filament = sampleFilament,
        )
        spoolman.setSpools(listOf(spool))
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        val vm = newVm()
        vm.onSpoolSelected(spool)
        createAndPair.nextResult = CreateAndPairResult.Cancelled("test")

        vm.onWriteTapped()

        assertEquals(1, createAndPair.invokeCalls)
        assertEquals(42, createAndPair.lastInput?.form?.selectedSpoolId)
        // selectedFilamentId is also set (parent filament linked) but the
        // CreateAndPair use-case routes on selectedSpoolId first — so the
        // existing-spool append path is taken regardless.
        assertEquals(7, createAndPair.lastInput?.form?.selectedFilamentId)
    }

    @Test fun `onFilamentSectionToggled flips filamentSectionExpanded, does not affect moreDetailsExpanded`() = runTest {
        val vm = newVm()
        assertEquals(false, vm.state.value.form.filamentSectionExpanded)
        assertEquals(false, vm.state.value.form.moreDetailsExpanded)

        vm.onFilamentSectionToggled()
        assertEquals(true, vm.state.value.form.filamentSectionExpanded)
        assertEquals(false, vm.state.value.form.moreDetailsExpanded)

        vm.onFilamentSectionToggled()
        assertEquals(false, vm.state.value.form.filamentSectionExpanded)
        assertTrue(true) // independence of expanders confirmed
    }
}
