package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.FakeMaterialBrandRepository
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
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
 * U20 (UI-49 + UI-52) — scan-time picker surfacing. Verifies the passive
 * suggested-id sets are populated on an unpaired read and cleared on the paths
 * that must not surface, WITHOUT changing any selection (§0 invariant).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSuggestionTest {

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

    // Inventory: a red PLA (Bambu) that should match a red-PLA-Bambu tag, plus
    // a green PETG (eSun) that should not.
    private val redPlaBambu = SpoolmanFilament(
        id = 10,
        name = "Bambu PLA Basic",
        material = "PLA",
        vendor = SpoolmanVendor(id = 1, name = "Bambu Lab"),
        color_hex = "FF0000",
    )
    private val greenPetgEsun = SpoolmanFilament(
        id = 20,
        name = "eSun PETG",
        material = "PETG",
        vendor = SpoolmanVendor(id = 2, name = "eSun"),
        color_hex = "00FF00",
    )
    private val spoolOfRedPla = SpoolmanSpool(id = 100, filament = redPlaBambu)
    private val spoolOfGreenPetg = SpoolmanSpool(id = 200, filament = greenPetgEsun)

    private val redPlaTag = OpenSpoolPayload(
        type = "PLA",
        colorHex = "FF0000",
        brand = "Bambu Lab",
        minTemp = "190",
        maxTemp = "220",
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
        spoolman.setFilaments(listOf(redPlaBambu, greenPetgEsun))
        spoolman.setSpools(listOf(spoolOfRedPla, spoolOfGreenPetg))
    }

    /** Drive a read that resolves to [classification] with 0 card_uid matches. */
    private fun scanUnpaired(classification: TagClassification, uid: String = "0455AA01") {
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        nfc.setBufferedTap(NfcResult.Success(CardUid(uid), classification))
    }

    @Test fun `unpaired vendor read with parsed hint suggests matching filament and its spool`() = runTest {
        seedInventory()
        val vm = newVm()
        scanUnpaired(TagClassification.Vendor(reason = "vendor", parsedHint = redPlaTag))

        vm.onReadTapped()

        val state = vm.state.value
        assertEquals(listOf(10), state.scanSuggestedFilamentIds)
        assertEquals(listOf(100), state.scanSuggestedSpoolIds)
    }

    @Test fun `unpaired OpenSpool read suggests matching filament and spool`() = runTest {
        seedInventory()
        val vm = newVm()
        scanUnpaired(TagClassification.OpenSpool(redPlaTag))

        vm.onReadTapped()

        val state = vm.state.value
        assertEquals(listOf(10), state.scanSuggestedFilamentIds)
        assertEquals(listOf(100), state.scanSuggestedSpoolIds)
    }

    @Test fun `suggestions are ordered best-match first (regression - was picker sort order)`() = runTest {
        // Repro of the on-device bug: writing a red-Elegoo-TPU tag surfaced
        // Bambu-white / Elegoo-black / Elegoo-red — i.e. the picker's default
        // sort, not match quality. The exact color+brand+material match must
        // rank first.
        val elegooRedTpu = SpoolmanFilament(
            id = 1, name = "Elegoo TPU", material = "TPU",
            vendor = SpoolmanVendor(id = 3, name = "Elegoo"), color_hex = "FF0000",
        )
        val elegooBlackTpu = SpoolmanFilament(
            id = 2, name = "Elegoo TPU Black", material = "TPU",
            vendor = SpoolmanVendor(id = 3, name = "Elegoo"), color_hex = "000000",
        )
        val bambuWhiteTpu = SpoolmanFilament(
            id = 3, name = "Bambu TPU", material = "TPU",
            vendor = SpoolmanVendor(id = 1, name = "Bambu Lab"), color_hex = "FFFFFF",
        )
        spoolman.setFilaments(listOf(elegooRedTpu, elegooBlackTpu, bambuWhiteTpu))
        spoolman.setSpools(
            listOf(
                SpoolmanSpool(id = 11, filament = elegooRedTpu),
                SpoolmanSpool(id = 12, filament = elegooBlackTpu),
                SpoolmanSpool(id = 13, filament = bambuWhiteTpu),
            ),
        )
        val vm = newVm()
        val redElegooTag = OpenSpoolPayload(
            type = "TPU", colorHex = "FF0000", brand = "Elegoo",
            minTemp = "220", maxTemp = "240",
        )
        scanUnpaired(TagClassification.Vendor(reason = "vendor", parsedHint = redElegooTag))

        vm.onReadTapped()

        val state = vm.state.value
        // Elegoo-red (material+brand+exact color) first, then Elegoo-black
        // (material+brand), then Bambu-white (material only).
        assertEquals(listOf(1, 2, 3), state.scanSuggestedFilamentIds)
        assertEquals(listOf(11, 12, 13), state.scanSuggestedSpoolIds)
    }

    @Test fun `scan surfacing never changes selection (passive invariant)`() = runTest {
        seedInventory()
        val vm = newVm()
        scanUnpaired(TagClassification.Vendor(reason = "vendor", parsedHint = redPlaTag))

        vm.onReadTapped()

        // Suggestions were computed but nothing auto-selected.
        assertTrue(vm.state.value.scanSuggestedFilamentIds.isNotEmpty())
        assertEquals(null, vm.state.value.form.selectedSpoolId)
        assertEquals(null, vm.state.value.form.selectedFilamentId)
        assertEquals(null, vm.state.value.spoolman.selectedSpoolId)
    }

    @Test fun `blank tag with no metadata suggests nothing`() = runTest {
        seedInventory()
        val vm = newVm()
        scanUnpaired(TagClassification.Blank)

        vm.onReadTapped()

        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedFilamentIds)
        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedSpoolIds)
    }

    @Test fun `vendor read with a hint that matches nothing suggests nothing`() = runTest {
        // Inventory has only red PLA + green PETG; a blue TPU tag matches neither.
        seedInventory()
        val vm = newVm()
        val blueTpu = OpenSpoolPayload(
            type = "TPU", colorHex = "0000FF", brand = "Overture",
            minTemp = "220", maxTemp = "240",
        )
        scanUnpaired(TagClassification.Vendor(reason = "vendor", parsedHint = blueTpu))

        vm.onReadTapped()

        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedFilamentIds)
        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedSpoolIds)
    }

    @Test fun `paired read clears any prior scan suggestions`() = runTest {
        seedInventory()
        val vm = newVm()
        // First: an unpaired scan populates suggestions.
        scanUnpaired(TagClassification.Vendor(reason = "vendor", parsedHint = redPlaTag))
        vm.onReadTapped()
        assertTrue(vm.state.value.scanSuggestedFilamentIds.isNotEmpty())

        // Then: a paired read (1 card_uid match) resolves a concrete spool.
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(spoolOfRedPla))
        nfc.setBufferedTap(NfcResult.Success(CardUid("0455AA02"), TagClassification.Blank))
        vm.onReadTapped()

        assertEquals(100, vm.state.value.spoolman.selectedSpoolId)
        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedFilamentIds)
        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedSpoolIds)
    }

    @Test fun `manual filament select clears scan suggestions`() = runTest {
        seedInventory()
        val vm = newVm()
        scanUnpaired(TagClassification.Vendor(reason = "vendor", parsedHint = redPlaTag))
        vm.onReadTapped()
        assertTrue(vm.state.value.scanSuggestedFilamentIds.isNotEmpty())

        vm.onFilamentSelected(greenPetgEsun)

        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedFilamentIds)
        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedSpoolIds)
        assertEquals(20, vm.state.value.form.selectedFilamentId)
    }

    @Test fun `manual spool select clears scan suggestions`() = runTest {
        seedInventory()
        val vm = newVm()
        scanUnpaired(TagClassification.Vendor(reason = "vendor", parsedHint = redPlaTag))
        vm.onReadTapped()
        assertTrue(vm.state.value.scanSuggestedSpoolIds.isNotEmpty())

        vm.onSpoolSelected(spoolOfGreenPetg)

        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedFilamentIds)
        assertEquals(emptyList<Int>(), vm.state.value.scanSuggestedSpoolIds)
        assertEquals(200, vm.state.value.form.selectedSpoolId)
    }
}
