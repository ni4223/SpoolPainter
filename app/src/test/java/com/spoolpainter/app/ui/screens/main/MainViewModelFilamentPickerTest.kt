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
import com.spoolpainter.app.support.FakeSaveToSpoolmanUseCase
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
    private val saveToSpoolman = FakeSaveToSpoolmanUseCase(spoolman = spoolman)
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
        saveToSpoolman = saveToSpoolman,
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
        assertEquals(1000f, form.fullSpoolWeightG)
        assertEquals(200f, form.emptySpoolWeightG)
    }

    /**
     * UI-57 — behaviour change. The X used to reset the form to defaults; it now
     * only unlinks, keeping every value so an already-configured "sister"
     * filament can be used as a template for a new one. The old expectation
     * (snap back to defaults) now belongs to onClearAll.
     */
    @Test fun `onFilamentSelected null unlinks but keeps every form value (UI-57)`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        val vm = newVm()
        vm.onFilamentSelected(sampleFilament)
        assertEquals(7, vm.state.value.form.selectedFilamentId)

        vm.onFilamentSelected(null)
        val form = vm.state.value.form
        assertNull("link is dropped", form.selectedFilamentId)
        // Everything the sister filament prefilled survives — this is the
        // whole point of the flow.
        assertEquals("PLA", form.material?.name)
        assertEquals("Polymaker", form.brand?.name)
        assertEquals("FF0000", form.colorHex)
        assertEquals(1.30f, form.densityGPerCm3)
        assertEquals(1000f, form.fullSpoolWeightG)
        assertEquals(200f, form.emptySpoolWeightG)
    }

    /**
     * A selected spool implies its filament (FormMapping.fromSpoolman carries
     * spool.filament.id), so leaving the spool linked while unlinking the
     * filament would let reDeriveSelectedSpoolForm silently re-link it on the
     * next cache refresh. Both links must go.
     */
    @Test fun `onFilamentSelected null also drops the spool link (UI-57)`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        val vm = newVm()
        vm.onSpoolSelected(SpoolmanSpool(id = 42, filament = sampleFilament))
        assertEquals(42, vm.state.value.form.selectedSpoolId)
        assertEquals(7, vm.state.value.form.selectedFilamentId)

        vm.onFilamentSelected(null)
        assertNull(vm.state.value.form.selectedFilamentId)
        assertNull(vm.state.value.form.selectedSpoolId)
        assertNull(vm.state.value.spoolman.selectedSpoolId)
    }

    /**
     * UI-57 — onClearAll inherits the reset the filament X used to perform,
     * including preserving the two view-only toggles.
     */
    @Test fun `onClearAll resets every field but keeps view-only toggles (UI-57)`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        val vm = newVm()
        vm.onFilamentSelected(sampleFilament)
        vm.onMoreDetailsToggled()
        val expandedBefore = vm.state.value.form.moreDetailsExpanded
        assertTrue("expander was toggled open", expandedBefore)

        vm.onClearAll()
        val form = vm.state.value.form
        assertNull(form.selectedFilamentId)
        assertNull(form.selectedSpoolId)
        assertEquals("PLA", form.material?.name)
        assertNull(form.brand)
        assertEquals("FFFFFF", form.colorHex)
        // A fresh form is not empty: material defaults to PLA, which carries
        // PLA's preset density. Compare against a default FormState rather than
        // hardcoding the preset value.
        assertEquals(FormState().densityGPerCm3, form.densityGPerCm3)
        assertEquals(FormState().fullSpoolWeightG, form.fullSpoolWeightG)
        // View-only state survives so the user stays on the section they had open.
        assertEquals(expandedBefore, form.moreDetailsExpanded)
        assertTrue(vm.state.value.scanSuggestedFilamentIds.isEmpty())
        assertTrue(vm.state.value.scanSuggestedSpoolIds.isEmpty())
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

    @Test fun `onWriteTapped routing — filament selected only is gated (no spool yet)`() = runTest {
        // U13: Write is disabled until a spool exists. With filament-only
        // selection the user must Save first to mint a spool, then Write.
        spoolman.setFilaments(listOf(sampleFilament))
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        val vm = newVm()
        vm.onFilamentSelected(sampleFilament)
        createAndPair.nextResult = CreateAndPairResult.Cancelled("test")

        assertEquals(false, vm.canWrite.value)
        vm.onWriteTapped()
        assertEquals(0, createAndPair.invokeCalls)
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

}
