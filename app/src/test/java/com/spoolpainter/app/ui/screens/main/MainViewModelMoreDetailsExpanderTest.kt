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

    @Test fun `default state — collapsed, weight density prefilled, spool weight & price null`() = runTest {
        val form = newVm().state.value.form
        assertFalse(form.moreDetailsExpanded)
        // Spoolman requires density/diameter/weight gt 0; weight + density
        // are pre-filled with the same defaults the call site would send,
        // so the user sees exactly what gets persisted. Diameter no longer
        // surfaces in the form (v2.0.2 decision N) — defaults to 1.75mm at
        // CreateFilamentRequest time. Empty-spool weight + price stay null
        // because they vary too much to default.
        assertNull(form.emptySpoolWeightG)
        assertNull(form.priceMajor)
        assertEquals(1000f, form.fullSpoolWeightG)
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
        vm.onDensityChanged("1.27")
        vm.onDensityChanged("abc")
        assertEquals(1.27f, vm.state.value.form.densityGPerCm3)
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

    // v2.0.2 — Remaining + Measured handlers and prefill snapshots.

    @Test fun `onRemainingWeightChanged updates remainingWeightG only`() = runTest {
        val vm = newVm()
        vm.onRemainingWeightChanged("730")
        val form = vm.state.value.form
        assertEquals(730f, form.remainingWeightG)
        // The prefill snapshot is untouched — only onSpoolSelected populates it.
        assertNull(form.prefilledRemainingWeightG)
    }

    @Test fun `onMeasuredWeightChanged with spool weight 220, input 950 - remainingWeightG becomes 730`() = runTest {
        val vm = newVm()
        vm.onEmptySpoolWeightChanged("220")
        vm.onMeasuredWeightChanged("950")
        assertEquals(730f, vm.state.value.form.remainingWeightG)
    }

    @Test fun `onMeasuredWeightChanged with no spool weight is no-op`() = runTest {
        val vm = newVm()
        // Default form has no empty-spool weight.
        vm.onMeasuredWeightChanged("950")
        assertNull(vm.state.value.form.remainingWeightG)
    }

    @Test fun `onMeasuredWeightChanged with negative back-solve skips commit (mid-typing protection)`() = runTest {
        // Skipping is what lets the user type "9" → "95" → "950" without
        // each intermediate state recomputing remaining=0/-125/-150 and
        // resetting the DecimalField's local text via remember(value).
        val vm = newVm()
        vm.onEmptySpoolWeightChanged("220")
        vm.onRemainingWeightChanged("730") // seed a known remaining
        vm.onMeasuredWeightChanged("100") // would back-solve to negative
        // Remaining is unchanged from the seeded value, not coerced to 0.
        assertEquals(730f, vm.state.value.form.remainingWeightG)
    }

    @Test fun `onSpoolSelected populates remaining + price prefill snapshots from spool fields`() = runTest {
        val filament = SpoolmanFilament(
            id = 1,
            material = "PLA",
            vendor = SpoolmanVendor(id = 1, name = "Bambu"),
            color_hex = "FFFFFF",
            spool_weight = 220f,
            price = 19.99f, // filament-level fallback
        )
        val spool = com.spoolpainter.app.domain.models.SpoolmanSpool(
            id = 5,
            filament = filament,
            remaining_weight = 850f,
            price = 25.50f, // spool-level wins
        )
        spoolman.setSpools(listOf(spool))
        val vm = newVm()
        vm.onSpoolSelected(spool)
        val form = vm.state.value.form
        assertEquals(850f, form.remainingWeightG)
        assertEquals(850f, form.prefilledRemainingWeightG)
        assertEquals(25.50f, form.priceMajor)
        assertEquals(25.50f, form.prefilledPriceMajor)
    }

    @Test fun `onSpoolSelected with null spool price falls back to filament price`() = runTest {
        val filament = SpoolmanFilament(
            id = 1,
            material = "PLA",
            vendor = SpoolmanVendor(id = 1, name = "Bambu"),
            color_hex = "FFFFFF",
            price = 19.99f,
        )
        val spool = com.spoolpainter.app.domain.models.SpoolmanSpool(
            id = 5,
            filament = filament,
            price = null, // spool has no price set
        )
        spoolman.setSpools(listOf(spool))
        val vm = newVm()
        vm.onSpoolSelected(spool)
        val form = vm.state.value.form
        assertEquals(19.99f, form.priceMajor)
        assertEquals(19.99f, form.prefilledPriceMajor)
    }

    @Test fun `toExpanderOverrides with selectedSpoolId returns variant only - filament-spec locked`() = runTest {
        val form = FormState(
            selectedSpoolId = 42,
            variant = "Matte",
            densityGPerCm3 = 1.27f,
            fullSpoolWeightG = 750f,
            priceMajor = 19.99f,
        )
        val overrides = form.toExpanderOverrides()
        assertEquals("Matte", overrides.variant)
        assertNull(overrides.density)
        assertNull(overrides.weight)
        assertNull(overrides.spoolWeight)
        assertNull(overrides.price)
        assertNull(overrides.spoolPrice)
    }

    @Test fun `toExpanderOverrides with selectedFilamentId — filament-spec locked, per-spool fields flow`() = runTest {
        // Filament-picker path: identity + filament-spec locked, but
        // per-spool overrides (spoolPrice + spoolWeightForSpool) flow to
        // the new spool's create body so each new spool can have its own
        // empty-spool weight + price (Spoolman supports this on the spool
        // record per database/models.py:73).
        val form = FormState(
            selectedFilamentId = 7,
            variant = "Glossy",
            densityGPerCm3 = 1.27f,
            priceMajor = 19.99f,
            emptySpoolWeightG = 220f,
        )
        val overrides = form.toExpanderOverrides()
        assertEquals("Glossy", overrides.variant)
        // Filament-spec locked — these fields don't flow.
        assertNull(overrides.density)
        assertNull(overrides.weight)
        assertNull(overrides.price)
        assertNull(overrides.spoolWeight)
        // Per-spool overrides flow.
        assertEquals(19.99f, overrides.spoolPrice)
        assertEquals(220f, overrides.spoolWeightForSpool)
    }

    @Test fun `toExpanderOverrides on new-filament path returns full overrides incl spool price`() = runTest {
        val form = FormState(
            // No spool/filament selected — fresh-create path.
            densityGPerCm3 = 1.27f,
            fullSpoolWeightG = 1000f,
            priceMajor = 19.99f,
        )
        val overrides = form.toExpanderOverrides()
        assertEquals(1.27f, overrides.density)
        assertEquals(1000f, overrides.weight)
        assertEquals(19.99f, overrides.price)
        // Decision M: spool-level price set defensively to the same value.
        assertEquals(19.99f, overrides.spoolPrice)
    }
}
