package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.primitives.CardUid
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * UI-67 — guards the header "Clear" button's enabled state.
 *
 * The point of these tests is the **definition**, not the plumbing.
 * `canClear` is "would [MainUiState.cleared] change anything", and `onClearAll`
 * applies that same function, so the two cannot disagree about the in-state
 * fields. What genuinely can rot is the seam these tests aim at:
 *
 *  - the two custom-name buffers live *outside* [MainUiState], so
 *    [MainUiState.cleared] cannot see them and `canClear` folds them in by hand
 *  - a new [MainUiState] field that ought to be cleared but is not added to
 *    [MainUiState.cleared] leaves the button greyed while state lingers
 *
 * The per-field cases below pin the second: each is a field the clear resets, and
 * each must un-grey the button on its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelClearAllTest {

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
    private val vendorUidOnlyPair = FakeVendorUidOnlyPairUseCase(spoolman, moveOnBind)
    private val materialBrandRepo = FakeMaterialBrandRepository()

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- the pure function ------------------------------------------------

    @Test
    fun `clearing a pristine state changes nothing`() {
        // Which is what makes the greyed button truthful rather than a guess.
        val pristine = MainUiState()
        assertEquals(pristine, pristine.cleared())
    }

    @Test
    fun `cleared is idempotent`() {
        val dirty = MainUiState(
            form = FormState(variant = "Matte", brand = Brand("Jayo")),
            ambiguity = null,
            observedTagKind = ObservedTagKind.Vendor,
            scanSuggestedSpoolIds = listOf(1, 2),
        )
        val once = dirty.cleared()
        assertEquals(once, once.cleared())
    }

    @Test
    fun `cleared keeps the two view-only toggles`() {
        // Deliberate: the user should stay on the section they were looking at.
        val dirty = MainUiState(
            form = FormState(
                variant = "Matte",
                rawWriteMode = true,
                moreDetailsExpanded = true,
            ),
        )
        val cleared = dirty.cleared()
        assertTrue("rawWriteMode must survive", cleared.form.rawWriteMode)
        assertTrue("moreDetailsExpanded must survive", cleared.form.moreDetailsExpanded)
        assertEquals("variant must go", null, cleared.form.variant)
    }

    // ---- canClear: the button's enabled state -----------------------------

    @Test
    fun `a fresh form cannot be cleared`() = runTest {
        val vm = newVm()
        assertFalse(vm.canClear.value)
    }

    @Test
    fun `typing a variant enables clear`() = runTest {
        val vm = newVm()
        vm.onVariantChanged("Matte")
        assertTrue(vm.canClear.value)
    }

    @Test
    fun `picking a brand enables clear`() = runTest {
        // Brand is the one identity field defaulting to null, so it is the
        // cheapest single-field proof that a pick un-greys the button.
        val vm = newVm()
        vm.onBrandPicked(Brand("Jayo"))
        assertTrue(vm.canClear.value)
    }

    @Test
    fun `a custom brand typed under Other enables clear on its own`() = runTest {
        // The seam worth testing: _customBrand lives outside MainUiState, so
        // MainUiState.cleared() is blind to it and canClear folds it in by hand.
        // Without that fold the user could type a brand and find Clear greyed.
        val vm = newVm()
        vm.onCustomBrandChanged("tecbears")
        assertTrue(vm.canClear.value)
    }

    @Test
    fun `a custom material typed under Other enables clear on its own`() = runTest {
        val vm = newVm()
        vm.onCustomMaterialChanged("PLA Silk")
        assertTrue(vm.canClear.value)
    }

    @Test
    fun `an observed vendor tag enables clear`() {
        // observedTagKind / observedTagUid are sticky by design and survive a
        // dropdown reset, so Clear is the only way to drop them. If cleared()
        // ignored them the vendor chip would be unclearable.
        val state = MainUiState(
            observedTagKind = ObservedTagKind.Vendor,
            observedTagUid = CardUid("AABBCCDD"),
        )
        assertTrue("cleared() must change a state holding an observed tag", state.cleared() != state)
    }

    @Test
    fun `scan suggestions alone enable clear`() {
        val state = MainUiState(scanSuggestedFilamentIds = listOf(7))
        assertTrue(state.cleared() != state)
    }

    @Test
    fun `clearing disables the button again`() = runTest {
        val vm = newVm()
        vm.onVariantChanged("Matte")
        vm.onCustomBrandChanged("tecbears")
        vm.onMaterialPicked(Material("PETG", 230, 250, 70, 80))
        assertTrue("precondition: dirty", vm.canClear.value)

        vm.onClearAll()

        assertFalse("Clear must grey out once there is nothing left to clear", vm.canClear.value)
    }

    @Test
    fun `clearing leaves nothing a second clear would change`() = runTest {
        // Ties the button's state to the action's own result: after a clear, the
        // clear is a no-op, which is precisely what the greyed button claims.
        val vm = newVm()
        vm.onVariantChanged("Matte")
        vm.onBrandPicked(Brand("Jayo"))
        vm.onClearAll()

        val after = vm.state.value
        assertEquals(after, after.cleared())
    }
}
