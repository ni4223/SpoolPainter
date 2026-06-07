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

    // U13 (Cluster A) — radio-style weight handlers + prefill snapshots.

    @Test fun `onActiveWeightChanged in Remaining mode updates remainingWeightG only`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Remaining)
        vm.onActiveWeightChanged("730")
        val form = vm.state.value.form
        assertEquals(730f, form.remainingWeightG)
        // The prefill snapshot is untouched — only onSpoolSelected populates it.
        assertNull(form.prefilledRemainingWeightG)
    }

    @Test fun `onActiveWeightChanged in Measured mode with empty=220 input=950 - remaining becomes 730`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onEmptySpoolWeightChanged("220")
        vm.onActiveWeightChanged("950")
        val form = vm.state.value.form
        assertEquals(730f, form.remainingWeightG)
        assertEquals(950f, form.measuredEntry)
    }

    @Test fun `onActiveWeightChanged in Measured mode without empty stashes measuredEntry`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onActiveWeightChanged("950")
        // No remaining commit yet — empty-spool reference is unknown.
        val form = vm.state.value.form
        assertNull(form.remainingWeightG)
        assertEquals(950f, form.measuredEntry)
    }

    @Test fun `setting empty-spool after stashed measuredEntry commits derived remaining`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onActiveWeightChanged("950")
        vm.onEmptySpoolWeightChanged("220") // becomes known after entry
        val form = vm.state.value.form
        assertEquals(730f, form.remainingWeightG)
        assertEquals(220f, form.emptySpoolWeightG)
    }

    @Test fun `onActiveWeightChanged Measured with negative back-solve skips commit (mid-typing protection)`() = runTest {
        // Lets the user type "9" → "95" → "950" without each intermediate
        // state recomputing remaining=0/-125/-150 and resetting the
        // DecimalField's local text via remember(value).
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onEmptySpoolWeightChanged("220")
        vm.onActiveWeightChanged("950") // commits remaining=730
        vm.onActiveWeightChanged("100") // would back-solve to negative — skip
        // Remaining is unchanged from the prior commit, not coerced to 0.
        assertEquals(730f, vm.state.value.form.remainingWeightG)
    }

    @Test fun `switching method drops measuredEntry`() = runTest {
        val vm = newVm()
        vm.onWeightMethodPicked(WeightMethod.Measured)
        vm.onActiveWeightChanged("950") // stashed in measuredEntry
        assertEquals(950f, vm.state.value.form.measuredEntry)
        vm.onWeightMethodPicked(WeightMethod.Remaining)
        assertNull(vm.state.value.form.measuredEntry)
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

    @Test fun `toExpanderOverrides with selectedSpoolId flows full filament-record overrides — v2_1 unlock`() = runTest {
        // v2.1: Color + Density + Filament weight + Temps + Variant all flow
        // on existing-spool Save. sparseDiff in the repo collapses unchanged
        // values to a no-op. Material + Brand are NOT in the override bag —
        // those stay locked at the UI layer (changing them = wrong filament
        // picked). Spool-scope fields (price, spool_weight) ride
        // patchSpoolFields separately.
        val form = FormState(
            selectedSpoolId = 42,
            variant = "Matte",
            colorHex = "FF8800",
            densityGPerCm3 = 1.27f,
            fullSpoolWeightG = 750f,
            priceMajor = 19.99f,
            tempRanges = com.spoolpainter.app.domain.models.TempRanges(
                extruderMin = 215, extruderMax = 235, bedMin = 70, bedMax = 70,
            ),
        )
        val overrides = form.toExpanderOverrides()
        assertEquals("Matte", overrides.variant)
        assertEquals("FF8800", overrides.colorHex)
        assertEquals(1.27f, overrides.density)
        assertEquals(750f, overrides.weight)
        assertEquals(215, overrides.extruderTemp)
        assertEquals(70, overrides.bedTemp)
        // Spool-scope per-spool overrides DO NOT ride filament PATCH.
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
