package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.domain.models.SpoolmanFilament
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * UI-54 — a Spoolman-side edit must reach the already-selected spool.
 *
 * Before this, the form was a one-time snapshot taken at selection time, so
 * editing a spool in Spoolman's web UI left the app showing the stale value no
 * matter how many times the user pulled to refresh; only clear-then-reselect
 * worked. These tests lock both halves of the fix: the refresh re-derives an
 * untouched form, and it never clobbers an edit in progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelRefreshRederiveTest {

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

    private fun filament(
        material: String = "PLA",
        colorHex: String = "FF0000",
    ) = SpoolmanFilament(
        id = 7,
        name = "Polymaker $material",
        material = material,
        vendor = SpoolmanVendor(id = 1, name = "Polymaker"),
        color_hex = colorHex,
        settings_extruder_temp = 200,
        settings_bed_temp = 50,
        density = 1.30f,
        diameter = 1.75f,
        weight = 1000f,
        spool_weight = 200f,
    )

    private fun spool(f: SpoolmanFilament) = SpoolmanSpool(id = 42, filament = f)

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

    @Test fun `refresh re-derives the selected spool form when the form is untouched`() = runTest {
        val original = filament(material = "PLA")
        spoolman.setSpools(listOf(spool(original)))
        val vm = newVm()
        vm.onSpoolSelected(spool(original))
        assertEquals("PLA", vm.state.value.form.material?.name)

        // The user edits the spool in Spoolman's web UI; a refresh lands the
        // fresh record in the cache.
        val edited = filament(material = "PETG")
        spoolman.setSpools(listOf(spool(edited)))

        assertEquals("PETG", vm.state.value.form.material?.name)
        assertEquals(42, vm.state.value.form.selectedSpoolId)
    }

    @Test fun `refresh does not clobber a form the user has edited`() = runTest {
        val original = filament(colorHex = "FF0000")
        spoolman.setSpools(listOf(spool(original)))
        val vm = newVm()
        vm.onSpoolSelected(spool(original))

        // User is mid-edit.
        vm.onColorHexChanged("00FF00")
        assertEquals("00FF00", vm.state.value.form.colorHex)

        // A background refresh brings a different colour from the server. The
        // user's in-flight edit must win — same invariant the prefilled*
        // stale-prefill snapshots protect on the save path.
        spoolman.setSpools(listOf(spool(filament(colorHex = "0000FF"))))

        assertEquals("00FF00", vm.state.value.form.colorHex)
    }

    @Test fun `refresh is a no-op when no spool is selected`() = runTest {
        val vm = newVm()
        val before = vm.state.value.form

        spoolman.setSpools(listOf(spool(filament(material = "PETG"))))

        assertEquals(before, vm.state.value.form)
    }

    @Test fun `toggling the More details expander does not suppress a re-derive`() = runTest {
        val original = filament(material = "PLA")
        spoolman.setSpools(listOf(spool(original)))
        val vm = newVm()
        vm.onSpoolSelected(spool(original))

        // Expander state is view state, not an edit — it must not count as
        // "user touched the form" and block a legitimate refresh.
        vm.onMoreDetailsToggled()
        assertTrue(vm.state.value.form.moreDetailsExpanded)

        spoolman.setSpools(listOf(spool(filament(material = "PETG"))))

        assertEquals("PETG", vm.state.value.form.material?.name)
        // ...and the expander stays open under the user.
        assertTrue(vm.state.value.form.moreDetailsExpanded)
    }

    @Test fun `re-selecting the same spool id re-derives (same-id early return removed)`() = runTest {
        val original = filament(material = "PLA")
        spoolman.setSpools(listOf(spool(original)))
        val vm = newVm()
        vm.onSpoolSelected(spool(original))
        assertEquals("PLA", vm.state.value.form.material?.name)

        // Same spool id, fresher data. Previously `if (spool.id ==
        // selectedSpoolId) return` made this a no-op, so the only way to pull
        // an edit in was to clear the selection first.
        vm.onSpoolSelected(spool(filament(material = "PETG")))

        assertEquals("PETG", vm.state.value.form.material?.name)
    }

    @Test fun `deselecting clears the snapshot so a later refresh cannot resurrect a form`() = runTest {
        val original = filament(material = "PLA")
        spoolman.setSpools(listOf(spool(original)))
        val vm = newVm()
        vm.onSpoolSelected(spool(original))
        vm.onSpoolSelected(null)
        val afterDeselect = vm.state.value.form

        spoolman.setSpools(listOf(spool(filament(material = "PETG"))))

        assertEquals(afterDeselect, vm.state.value.form)
    }
}
