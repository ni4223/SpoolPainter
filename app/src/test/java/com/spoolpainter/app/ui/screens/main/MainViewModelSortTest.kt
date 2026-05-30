package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSortTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val nfc = FakeNfcRepository()
    private val settings = FakeSettingsRepository()
    private val spoolman = FakeSpoolmanRepository(settings = settings)
    private val createAndPair = FakeCreateAndPairUseCase(nfc = nfc, spoolman = spoolman)
    private val twoTag = FakeTwoTagUseCase(nfc = nfc, spoolman = spoolman)
    private val confirmer = FakeMoveOnBindConfirmer()
    private val moveOnBind = FakeMoveOnBindUseCase()
    private val rawWrite = FakeRawWriteUseCase(nfc = nfc)
    private val vendorUidOnlyPair = FakeVendorUidOnlyPairUseCase(spoolman = spoolman, moveOnBind = moveOnBind)
    private val materialBrandRepo = FakeMaterialBrandRepository()

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `flipping spool sort key does not change filament sort and vice versa`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(SpoolSortKey.Id, vm.state.value.spoolSortKey)
        assertEquals(FilamentSortKey.Id, vm.state.value.filamentSortKey)

        settings.setSpoolSortKey(SpoolSortKey.Material)
        advanceUntilIdle()
        assertEquals(SpoolSortKey.Material, vm.state.value.spoolSortKey)
        assertEquals(FilamentSortKey.Id, vm.state.value.filamentSortKey)

        settings.setFilamentSortKey(FilamentSortKey.Brand)
        advanceUntilIdle()
        assertEquals(SpoolSortKey.Material, vm.state.value.spoolSortKey)
        assertEquals(FilamentSortKey.Brand, vm.state.value.filamentSortKey)
    }

    @Test
    fun `sort directions are independent across the two dropdowns`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(SortDirection.Desc, vm.state.value.spoolSortDirection)
        assertEquals(SortDirection.Desc, vm.state.value.filamentSortDirection)

        settings.setSpoolSortDirection(SortDirection.Asc)
        advanceUntilIdle()
        assertEquals(SortDirection.Asc, vm.state.value.spoolSortDirection)
        assertEquals(SortDirection.Desc, vm.state.value.filamentSortDirection)
    }
}
