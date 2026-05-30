package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.remote.spoolman.ConnectivityState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelBannerTest {

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
    fun `url blank and connectivity Unknown yields Hidden banner`() = runTest {
        settings.pushSettings(Settings(url = ""))
        spoolman.setConnectivity(ConnectivityState.Unknown)
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(BannerState.Hidden, vm.state.value.banner)
    }

    @Test
    fun `url set and connectivity Reachable yields Hidden banner`() = runTest {
        settings.pushSettings(Settings(url = "http://nas:7912"))
        spoolman.setConnectivity(ConnectivityState.Reachable)
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(BannerState.Hidden, vm.state.value.banner)
    }

    @Test
    fun `url set and connectivity Unreachable yields Offline banner with reason`() = runTest {
        settings.pushSettings(Settings(url = "http://nas:7912"))
        spoolman.setConnectivity(ConnectivityState.Unreachable("dns"))
        val vm = newVm()
        advanceUntilIdle()
        val banner = vm.state.value.banner
        assertTrue(banner is BannerState.Offline)
        assertEquals("dns", (banner as BannerState.Offline).lastError)
    }

    @Test
    fun `URL configured while unreachable surfaces banner mid-flow`() = runTest {
        settings.pushSettings(Settings(url = ""))
        spoolman.setConnectivity(ConnectivityState.Unreachable("timeout"))
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(BannerState.Hidden, vm.state.value.banner)
        settings.pushSettings(Settings(url = "http://nas:7912"))
        advanceUntilIdle()
        assertTrue(vm.state.value.banner is BannerState.Offline)
    }

    @Test
    fun `URL cleared while unreachable hides banner mid-flow`() = runTest {
        settings.pushSettings(Settings(url = "http://nas:7912"))
        spoolman.setConnectivity(ConnectivityState.Unreachable("timeout"))
        val vm = newVm()
        advanceUntilIdle()
        assertTrue(vm.state.value.banner is BannerState.Offline)
        settings.pushSettings(Settings(url = ""))
        advanceUntilIdle()
        assertEquals(BannerState.Hidden, vm.state.value.banner)
    }
}
