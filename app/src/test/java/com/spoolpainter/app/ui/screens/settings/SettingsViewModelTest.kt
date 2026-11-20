package com.spoolpainter.app.ui.screens.settings

import com.spoolpainter.app.data.local.Currency
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.data.local.TagStorageMode
import com.spoolpainter.app.data.local.ThemeOverride
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.TagSyncConflict
import com.spoolpainter.app.domain.models.TagSyncState
import com.spoolpainter.app.support.FakeSettingsRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val spoolman = FakeSpoolmanRepository(settings = settings)

    private fun newVm(): SettingsViewModel = SettingsViewModel(
        settings = settings,
        spoolman = spoolman,
        nfcReadLog = com.spoolpainter.app.hardware.nfc.NfcReadLog(),
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
    fun `onSpoolSortKeyChanged invokes setSpoolSortKey on repository`() = runTest {
        val vm = newVm()
        vm.onSpoolSortKeyChanged(SpoolSortKey.Material)
        advanceUntilIdle()
        assertEquals(SpoolSortKey.Material, settings.settings.value.spoolSortKey)
        assertEquals(FilamentSortKey.Id, settings.settings.value.filamentSortKey)
    }

    @Test
    fun `onSpoolSortDirectionChanged sets the requested direction`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(SortDirection.Desc, settings.settings.value.spoolSortDirection)
        vm.onSpoolSortDirectionChanged(SortDirection.Asc)
        advanceUntilIdle()
        assertEquals(SortDirection.Asc, settings.settings.value.spoolSortDirection)
        vm.onSpoolSortDirectionChanged(SortDirection.Desc)
        advanceUntilIdle()
        assertEquals(SortDirection.Desc, settings.settings.value.spoolSortDirection)
    }

    @Test
    fun `onFilamentSortKeyChanged invokes setFilamentSortKey on repository`() = runTest {
        val vm = newVm()
        vm.onFilamentSortKeyChanged(FilamentSortKey.Brand)
        advanceUntilIdle()
        assertEquals(FilamentSortKey.Brand, settings.settings.value.filamentSortKey)
        assertEquals(SpoolSortKey.Id, settings.settings.value.spoolSortKey)
    }

    @Test
    fun `onFilamentSortDirectionChanged sets the requested direction`() = runTest {
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(SortDirection.Desc, settings.settings.value.filamentSortDirection)
        vm.onFilamentSortDirectionChanged(SortDirection.Asc)
        advanceUntilIdle()
        assertEquals(SortDirection.Asc, settings.settings.value.filamentSortDirection)
    }

    @Test
    fun `onCurrencyChanged invokes setCurrency on repository`() = runTest {
        val vm = newVm()
        vm.onCurrencyChanged(Currency.Euro)
        advanceUntilIdle()
        assertEquals(Currency.Euro, settings.settings.value.currency)
    }

    @Test
    fun `onThemeToggled flips Dark to Light and back (fresh-install default = Dark)`() = runTest {
        // v2.0.2 fresh-install default is Dark (per user direction
        // "device mode or dark by default" — picked Dark for the
        // simpler 2-state toggle).
        val vm = newVm()
        advanceUntilIdle()
        assertEquals(ThemeOverride.Dark, vm.themeOverride.value)
        vm.onThemeToggled()
        advanceUntilIdle()
        assertEquals(ThemeOverride.Light, settings.settings.value.themeOverride)
        vm.onThemeToggled()
        advanceUntilIdle()
        assertEquals(ThemeOverride.Dark, settings.settings.value.themeOverride)
    }

    @Test
    fun `initial state maps tagStorageMode BOTH from default settings`() = runTest {
        val vm = newVm()
        // state uses WhileSubscribed — keep a collector alive so the
        // mapped value (not just the initial placeholder) is asserted.
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(TagStorageMode.BOTH, vm.state.value.tagStorageMode)
        assertEquals(null, vm.state.value.serverVersion)
        assertFalse(vm.state.value.supportsNativeTags)
    }

    @Test
    fun `onTagStorageModeSelected invokes setTagStorageMode and updates state`() = runTest {
        val vm = newVm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        vm.onTagStorageModeSelected(TagStorageMode.LEGACY_ONLY)
        advanceUntilIdle()
        assertEquals(TagStorageMode.LEGACY_ONLY, settings.settings.value.tagStorageMode)
        assertEquals(TagStorageMode.LEGACY_ONLY, vm.state.value.tagStorageMode)
        vm.onTagStorageModeSelected(TagStorageMode.NATIVE_ONLY)
        advanceUntilIdle()
        assertEquals(TagStorageMode.NATIVE_ONLY, settings.settings.value.tagStorageMode)
        assertEquals(TagStorageMode.NATIVE_ONLY, vm.state.value.tagStorageMode)
    }

    @Test
    fun `serverInfo with version 0_27 or newer maps supportsNativeTags true`() = runTest {
        val vm = newVm()
        backgroundScope.launch { vm.state.collect {} }
        spoolman.setServerInfo(SpoolmanInfo(version = "0.27.1"))
        advanceUntilIdle()
        assertEquals("0.27.1", vm.state.value.serverVersion)
        assertTrue(vm.state.value.supportsNativeTags)
    }

    @Test
    fun `serverInfo older than 0_27 maps supportsNativeTags false with version exposed`() = runTest {
        val vm = newVm()
        backgroundScope.launch { vm.state.collect {} }
        spoolman.setServerInfo(SpoolmanInfo(version = "0.26.4"))
        advanceUntilIdle()
        assertEquals("0.26.4", vm.state.value.serverVersion)
        assertFalse(vm.state.value.supportsNativeTags)
    }

    @Test
    fun `mode selection to BOTH or NATIVE_ONLY requests a tag sync`() = runTest {
        val vm = newVm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(0, spoolman.requestTagSyncCalls)

        vm.onTagStorageModeSelected(TagStorageMode.BOTH)
        advanceUntilIdle()
        assertEquals(1, spoolman.requestTagSyncCalls)

        vm.onTagStorageModeSelected(TagStorageMode.NATIVE_ONLY)
        advanceUntilIdle()
        assertEquals(2, spoolman.requestTagSyncCalls)
    }

    @Test
    fun `mode selection to LEGACY_ONLY does not request a tag sync`() = runTest {
        val vm = newVm()
        advanceUntilIdle()

        vm.onTagStorageModeSelected(TagStorageMode.LEGACY_ONLY)
        advanceUntilIdle()

        assertEquals(0, spoolman.requestTagSyncCalls)
    }

    @Test
    fun `tagSyncState surfaces in ui state`() = runTest {
        val vm = newVm()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(TagSyncState.Idle, vm.state.value.tagSyncState)

        spoolman.setTagSyncState(
            TagSyncState.Finished(
                mirrored = 3,
                conflicts = listOf(TagSyncConflict(uid = "AABBCCDD", targetSpoolId = 2, holderSpoolId = 1)),
                failed = 1,
            ),
        )
        advanceUntilIdle()

        val state = vm.state.value.tagSyncState
        assertTrue(state is TagSyncState.Finished)
        state as TagSyncState.Finished
        assertEquals(3, state.mirrored)
        assertEquals(1, state.failed)
        assertEquals(
            listOf(TagSyncConflict(uid = "AABBCCDD", targetSpoolId = 2, holderSpoolId = 1)),
            state.conflicts,
        )
    }
}
