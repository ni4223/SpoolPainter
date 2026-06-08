package com.spoolpainter.app.ui.screens.settings

import com.spoolpainter.app.data.local.Currency
import com.spoolpainter.app.data.local.FilamentSortKey
import com.spoolpainter.app.data.local.SortDirection
import com.spoolpainter.app.data.local.SpoolSortKey
import com.spoolpainter.app.data.local.ThemeOverride
import com.spoolpainter.app.support.FakeSettingsRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
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
}
