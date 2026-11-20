package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.ExternalFilament
import com.spoolpainter.app.domain.models.ExternalFilamentPage
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SpoolmanDB external filament search — query debounce, paging, pick
 * prefill (dedup against already-imported inventory), linkage clearing,
 * and Save threading into SaveToSpoolmanInput.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelExternalSearchTest {

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
    )

    private fun externalFilament(id: String = "polymaker-polylite-pla") = ExternalFilament(
        id = id,
        manufacturer = "Polymaker",
        name = "PolyLite PLA",
        material = "PLA",
        density = 1.25,
        weight = 1000.0,
        spoolWeight = 210.0,
        diameter = 1.75,
        colorHex = null,
        colorHexes = listOf("FF0000", "00FF00"),
        extruderTemp = null,
        bedTemp = null,
    )

    private fun pageOf(vararg items: ExternalFilament, totalCount: Int = items.size) =
        SpoolmanOutcome.Success(ExternalFilamentPage(items = items.toList(), totalCount = totalCount))

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

    @Test fun `query is debounced - repository called once after 300ms with trimmed query`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        spoolman.nextSearchExternalFilamentsResult = pageOf(externalFilament())
        val vm = newVm()

        vm.onExternalFilamentQueryChanged("  Poly  ")
        // Before the debounce elapses: state flips to Searching, no call yet.
        assertEquals(ExternalSearchUiState.Searching, vm.externalSearchState.value)
        assertEquals(0, spoolman.searchExternalFilamentsCalls)

        advanceUntilIdle()
        assertEquals(1, spoolman.searchExternalFilamentsCalls)
        assertEquals("Poly", spoolman.lastSearchExternalFilaments?.first)
        assertEquals(20, spoolman.lastSearchExternalFilaments?.second)
        assertEquals(0, spoolman.lastSearchExternalFilaments?.third)
        val state = vm.externalSearchState.value
        assertTrue("got $state", state is ExternalSearchUiState.Results)
    }

    @Test fun `rapid retypes within the debounce collapse to a single call with the latest query`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        spoolman.nextSearchExternalFilamentsResult = pageOf()
        val vm = newVm()

        vm.onExternalFilamentQueryChanged("Po")
        vm.onExternalFilamentQueryChanged("Pol")
        vm.onExternalFilamentQueryChanged("Poly")
        advanceUntilIdle()

        assertEquals(1, spoolman.searchExternalFilamentsCalls)
        assertEquals("Poly", spoolman.lastSearchExternalFilaments?.first)
    }

    @Test fun `query shorter than 2 chars - no repository call, state Idle`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val vm = newVm()

        vm.onExternalFilamentQueryChanged("p")
        advanceUntilIdle()

        assertEquals(0, spoolman.searchExternalFilamentsCalls)
        assertEquals(ExternalSearchUiState.Idle, vm.externalSearchState.value)
    }

    @Test fun `search failure surfaces Error state`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        spoolman.nextSearchExternalFilamentsResult = SpoolmanOutcome.HttpError(500, "boom")
        val vm = newVm()

        vm.onExternalFilamentQueryChanged("Poly")
        advanceUntilIdle()

        val state = vm.externalSearchState.value
        assertTrue("got $state", state is ExternalSearchUiState.Error)
        assertTrue((state as ExternalSearchUiState.Error).message.contains("500"))
    }

    @Test fun `loadMore appends the next page and dedupes by id`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val a = externalFilament("a")
        val b = externalFilament("b")
        val c = externalFilament("c")
        val d = externalFilament("d")
        spoolman.nextSearchExternalFilamentsResult = pageOf(a, b, totalCount = 5)
        val vm = newVm()
        vm.onExternalFilamentQueryChanged("Poly")
        advanceUntilIdle()

        // Page 2 re-serves "b" (server overlap) — merged list must dedupe.
        spoolman.nextSearchExternalFilamentsResult = pageOf(b, c, d, totalCount = 5)
        vm.onExternalFilamentLoadMore()
        advanceUntilIdle()

        assertEquals(2, spoolman.searchExternalFilamentsCalls)
        assertEquals(2, spoolman.lastSearchExternalFilaments?.third) // offset = items.size before append
        val state = vm.externalSearchState.value as ExternalSearchUiState.Results
        assertEquals(listOf(a, b, c, d).map { it.id }, state.items.map { it.id })
        assertEquals(5, state.totalCount)
    }

    @Test fun `loadMore is a no-op when everything is loaded`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val a = externalFilament("a")
        spoolman.nextSearchExternalFilamentsResult = pageOf(a, totalCount = 1)
        val vm = newVm()
        vm.onExternalFilamentQueryChanged("Poly")
        advanceUntilIdle()

        vm.onExternalFilamentLoadMore()
        advanceUntilIdle()

        assertEquals(1, spoolman.searchExternalFilamentsCalls)
    }

    @Test fun `pick without inventory match prefills form, sets external ids, clears both selections`() = runTest {
        spoolman.setFilaments(listOf(sampleFilament))
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val vm = newVm()
        // Pre-select a spool to confirm the pick clears it (create-new path).
        vm.onSpoolSelected(SpoolmanSpool(id = 99, filament = sampleFilament))

        vm.onExternalFilamentPicked(externalFilament())

        val form = vm.state.value.form
        assertEquals("PLA", form.material?.name) // preset lookup
        assertEquals("Polymaker", form.brand?.name)
        assertEquals("FF0000", form.colorHex) // colorHexes.first() canonicalised
        assertEquals("PolyLite", form.variant) // "PolyLite PLA" minus material word
        // extruderTemp/bedTemp null → material preset seeds kept.
        assertEquals(190, form.tempRanges.extruderMin)
        assertEquals(220, form.tempRanges.extruderMax)
        assertEquals(40, form.tempRanges.bedMin)
        assertEquals(65, form.tempRanges.bedMax)
        assertEquals(1.25f, form.densityGPerCm3)
        assertEquals(1000f, form.fullSpoolWeightG)
        assertEquals(210f, form.emptySpoolWeightG)
        assertEquals("polymaker-polylite-pla", form.externalFilamentId)
        assertEquals(1.75, form.externalDiameterMm)
        assertNull(form.selectedFilamentId)
        assertNull(form.selectedSpoolId)
    }

    @Test fun `pick with inventory external_id match delegates to existing-filament selection`() = runTest {
        val imported = sampleFilament.copy(external_id = "polymaker-polylite-pla")
        spoolman.setFilaments(listOf(imported))
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val vm = newVm()

        vm.onExternalFilamentPicked(externalFilament("polymaker-polylite-pla"))

        val form = vm.state.value.form
        assertEquals(7, form.selectedFilamentId)
        assertNull(form.selectedSpoolId)
        // Delegated to onFilamentSelected → FormMapping.fromFilament ran
        // (vendor prefilled) and the external linkage did NOT ride along.
        assertEquals("Polymaker", form.brand?.name)
        assertNull(form.externalFilamentId)
        assertNull(form.externalDiameterMm)
    }

    @Test fun `FilamentSelected clears externalFilamentId and externalDiameterMm`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val vm = newVm()
        vm.onExternalFilamentPicked(externalFilament())
        assertEquals("polymaker-polylite-pla", vm.state.value.form.externalFilamentId)

        vm.onFilamentSelected(sampleFilament)

        assertNull(vm.state.value.form.externalFilamentId)
        assertNull(vm.state.value.form.externalDiameterMm)
    }

    @Test fun `SpoolSelected null clears externalFilamentId and externalDiameterMm`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val vm = newVm()
        vm.onExternalFilamentPicked(externalFilament())

        vm.onSpoolSelected(null)

        assertNull(vm.state.value.form.externalFilamentId)
        assertNull(vm.state.value.form.externalDiameterMm)
        // Only the linkage dropped — the prefilled values stay.
        assertEquals("Polymaker", vm.state.value.form.brand?.name)
    }

    @Test fun `SpoolSelected non-null replaces the external linkage via fromSpoolman prefill`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val spool = SpoolmanSpool(id = 42, filament = sampleFilament)
        spoolman.setSpools(listOf(spool))
        val vm = newVm()
        vm.onExternalFilamentPicked(externalFilament())

        vm.onSpoolSelected(spool)

        assertNull(vm.state.value.form.externalFilamentId)
        assertNull(vm.state.value.form.externalDiameterMm)
        assertEquals(42, vm.state.value.form.selectedSpoolId)
    }

    @Test fun `onSaveTapped threads externalFilamentId and diameterMm into SaveToSpoolmanInput`() = runTest {
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0"))
        val vm = newVm()
        vm.onExternalFilamentPicked(externalFilament())
        assertTrue(vm.canSave.value)

        vm.onSaveTapped()

        assertEquals(1, saveToSpoolman.invokeCalls)
        assertEquals("polymaker-polylite-pla", saveToSpoolman.lastInput?.externalFilamentId)
        assertEquals(1.75, saveToSpoolman.lastInput?.diameterMm)
    }

    @Test fun `unsupported server version - state Unsupported and search never called`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.25.0"))
        val vm = newVm()

        assertEquals(ExternalSearchUiState.Unsupported, vm.externalSearchState.value)
        vm.onExternalFilamentQueryChanged("Poly")
        advanceUntilIdle()

        assertEquals(0, spoolman.searchExternalFilamentsCalls)
        assertEquals(ExternalSearchUiState.Unsupported, vm.externalSearchState.value)
    }

    @Test fun `unknown server version also fails closed to Unsupported`() = runTest {
        val vm = newVm() // serverInfo null
        assertEquals(ExternalSearchUiState.Unsupported, vm.externalSearchState.value)
    }

    @Test fun `external_db_name null falls back to SpoolmanDB label`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.0", external_db_name = null))
        val vm = newVm()
        assertEquals("SpoolmanDB", vm.externalDbLabel.value)
    }

    @Test fun `external_db_name configured surfaces in the label`() = runTest {
        spoolman.setServerInfo(SpoolmanInfo(version = "0.27.0", external_db_name = "FilamentDB"))
        val vm = newVm()
        assertEquals("FilamentDB", vm.externalDbLabel.value)
    }
}
