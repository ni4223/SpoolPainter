package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
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
import org.junit.Before
import org.junit.Test

/**
 * U24 (UI-59 reading (a)) — the Filament picker floats matches derived from the
 * form's own fields, so the UI-57 sister-filament flow (pick, unlink, re-pick a
 * sibling) doesn't start from the default sort.
 *
 * The load-bearing test here is the first one: an untouched form must float
 * NOTHING. A fresh form is PLA + FFFFFF, both of which score, so the gate is
 * the only thing standing between this feature and a permanently reordered
 * picker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelFormSuggestionTest {

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

    private val bambu = SpoolmanVendor(id = 1, name = "Bambu Lab")
    private val esun = SpoolmanVendor(id = 2, name = "eSUN")

    private val bambuRedPla = SpoolmanFilament(
        id = 10, name = "Bambu PLA Basic", material = "PLA", vendor = bambu, color_hex = "FF0000",
    )
    private val bambuBlackPla = SpoolmanFilament(
        id = 11, name = "Bambu PLA Black", material = "PLA", vendor = bambu, color_hex = "000000",
    )
    private val esunGreenPetg = SpoolmanFilament(
        id = 20, name = "eSUN PETG", material = "PETG", vendor = esun, color_hex = "00FF00",
    )

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

    private fun seedInventory() {
        spoolman.setFilaments(listOf(bambuRedPla, bambuBlackPla, esunGreenPetg))
        spoolman.setSpools(
            listOf(
                SpoolmanSpool(id = 100, filament = bambuRedPla),
                SpoolmanSpool(id = 110, filament = bambuBlackPla),
                SpoolmanSpool(id = 200, filament = esunGreenPetg),
            ),
        )
    }

    @Test fun `untouched form floats nothing (no permanent floated group)`() = runTest {
        seedInventory()
        val vm = newVm()

        // Defaults only: PLA material, no brand, FFFFFF color.
        assertEquals("PLA", vm.state.value.form.material?.name)
        assertEquals(null, vm.state.value.form.brand)
        assertEquals(emptyList<Int>(), vm.suggestedFilamentIds.value)
    }

    @Test fun `picking a brand floats that brand's filaments`() = runTest {
        seedInventory()
        val vm = newVm()

        vm.onBrandPicked(Brand("Bambu Lab"))

        // Both Bambu PLAs match brand + the defaulted PLA material; the eSUN
        // PETG shares neither.
        assertEquals(listOf(10, 11), vm.suggestedFilamentIds.value)
    }

    @Test fun `color drives the order once a brand is set`() = runTest {
        seedInventory()
        val vm = newVm()

        vm.onBrandPicked(Brand("Bambu Lab"))
        vm.onColorHexChanged("000000")

        // Black now scores the exact color, so it outranks red.
        assertEquals(listOf(11, 10), vm.suggestedFilamentIds.value)
    }

    @Test fun `material narrows the float to that material`() = runTest {
        seedInventory()
        val vm = newVm()

        vm.onBrandPicked(Brand("eSUN"))
        vm.onMaterialPicked(Material("PETG", 230, 250, 60, 80, density = 1.27f))

        assertEquals(listOf(20), vm.suggestedFilamentIds.value)
    }

    @Test fun `sister-filament flow floats the siblings after the link is dropped (UI-57 + UI-59)`() = runTest {
        seedInventory()
        val vm = newVm()

        // Pick a filament: the form fills, and with a selection there is no
        // float (the identity fields are locked to the selection's own values).
        vm.onFilamentSelected(bambuRedPla)
        assertEquals(emptyList<Int>(), vm.suggestedFilamentIds.value)

        // Tap the X: U23 keeps every field and drops only the link. The form's
        // brand is still Bambu Lab, so the sister and its siblings float.
        vm.onFilamentSelected(null)

        assertEquals(listOf(10, 11), vm.suggestedFilamentIds.value)
    }

    @Test fun `clear all takes the float away with the form`() = runTest {
        seedInventory()
        val vm = newVm()
        vm.onBrandPicked(Brand("Bambu Lab"))
        assertEquals(listOf(10, 11), vm.suggestedFilamentIds.value)

        vm.onClearAll()

        assertEquals(emptyList<Int>(), vm.suggestedFilamentIds.value)
    }

    @Test fun `a scan set wins over the form (U20 precedence)`() = runTest {
        seedInventory()
        val vm = newVm()
        // Form says Bambu, which on its own would float 10 then 11.
        vm.onBrandPicked(Brand("Bambu Lab"))

        // A read of an unpaired green eSUN PETG tag suggests the eSUN filament.
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        nfc.setBufferedTap(
            NfcResult.Success(
                CardUid("0455AA01"),
                TagClassification.Vendor(
                    reason = "vendor",
                    parsedHint = OpenSpoolPayload(
                        type = "PETG", colorHex = "00FF00", brand = "eSUN",
                        minTemp = "230", maxTemp = "250",
                    ),
                ),
            ),
        )
        vm.onReadTapped()

        assertEquals(listOf(20), vm.state.value.scanSuggestedFilamentIds)
        assertEquals(listOf(20), vm.suggestedFilamentIds.value)
    }

    @Test fun `form float never changes the selection (passive invariant)`() = runTest {
        seedInventory()
        val vm = newVm()

        vm.onBrandPicked(Brand("Bambu Lab"))

        assertEquals(listOf(10, 11), vm.suggestedFilamentIds.value)
        assertEquals(null, vm.state.value.form.selectedFilamentId)
        assertEquals(null, vm.state.value.form.selectedSpoolId)
        assertEquals(null, vm.state.value.spoolman.selectedSpoolId)
    }

    @Test fun `float is capped at three, same as the scan path`() = runTest {
        val many = (1..6).map { i ->
            SpoolmanFilament(id = i, name = "Bambu PLA $i", material = "PLA", vendor = bambu, color_hex = "FF0000")
        }
        spoolman.setFilaments(many)
        val vm = newVm()

        vm.onBrandPicked(Brand("Bambu Lab"))

        assertEquals(3, vm.suggestedFilamentIds.value.size)
    }
}
