package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
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
import org.junit.Before
import org.junit.Test

/**
 * U13 (Cluster A) — radio weight picker handlers.
 *
 *   - Active=Remaining + edit
 *   - Active=Measured + edit-with-empty
 *   - Active=Measured + edit-without-empty + later-empty-set
 *   - Switch active mid-edit
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelWeightMethodTest {

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

    @Test fun `default weightMethod is Measured`() = runTest {
        val vm = newVm()
        assertEquals(WeightMethod.Measured, vm.state.value.form.weightMethod)
    }

    @Test fun `active=Remaining edit commits remainingWeightG`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Remaining)
        vm.onActiveWeightChanged("730")
        assertEquals(730f, vm.state.value.form.remainingWeightG)
        assertNull(vm.state.value.form.measuredEntry)
    }

    @Test fun `active=Measured + empty known commits derived remaining`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onEmptySpoolWeightChanged("220")
        vm.onActiveWeightChanged("950")
        assertEquals(730f, vm.state.value.form.remainingWeightG)
        assertEquals(950f, vm.state.value.form.measuredEntry)
    }

    @Test fun `active=Measured without empty stashes entry then commits when empty arrives`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onActiveWeightChanged("950")
        // Pre-empty: remaining is unknown, entry is held.
        assertNull(vm.state.value.form.remainingWeightG)
        assertEquals(950f, vm.state.value.form.measuredEntry)
        vm.onEmptySpoolWeightChanged("220")
        // Now derived.
        assertEquals(730f, vm.state.value.form.remainingWeightG)
    }

    @Test fun `switching method drops measuredEntry`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onActiveWeightChanged("950")
        assertEquals(950f, vm.state.value.form.measuredEntry)
        vm.onWeightMethodPicked(WeightMethod.Remaining)
        assertNull(vm.state.value.form.measuredEntry)
    }
}
