package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.domain.usecases.RawWriteResult
import com.spoolpainter.app.hardware.nfc.TagBuffer
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

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelRawWriteTest {

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

    private val sampleUid = CardUid("AABBCCDD")

    private val materialBrandRepo = com.spoolpainter.app.data.local.FakeMaterialBrandRepository()

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

    private fun primeFormForWrite(vm: MainViewModel) {
        vm.onMaterialPicked(Material("PLA", 190, 220, 55, 65))
        vm.onBrandPicked(Brand("Bambu"))
        vm.onColorHexChanged("FF0000")
        vm.onTempRangesChanged(
            TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
        )
        nfc.pushLastSeenTag(TagBuffer(sampleUid, TagClassification.Blank, capturedAtEpochMs = 0L))
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `writeMode is RawNoUrl when settings url is blank`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        // Allow init flows to settle.
        assertEquals(WriteMode.RawNoUrl, vm.state.value.writeMode)
    }

    @Test
    fun `writeMode is Spoolman when url configured and connectivity unknown`() = runTest {
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        val vm = newVm()
        // ConnectivityState.Unknown by default → not Unreachable → Spoolman mode.
        assertEquals(WriteMode.Spoolman, vm.state.value.writeMode)
    }

    @Test
    fun `onWriteTapped with RawNoUrl routes to rawWrite`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        rawWrite.nextResult = RawWriteResult.Success.Written(sampleUid)

        vm.onWriteTapped()

        assertEquals(1, rawWrite.invokeCalls)
        assertEquals(0, createAndPair.invokeCalls)
    }

    @Test
    fun `applyRawWriteResult Success transitions to Idle and emits snackbar`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        rawWrite.nextResult = RawWriteResult.Success.Written(sampleUid)

        vm.onWriteTapped()

        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
        assertEquals(sampleUid, vm.state.value.form.cardUid)
    }

    @Test
    fun `applyRawWriteResult VendorTagRejected emits unreadable snackbar`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        rawWrite.nextResult = RawWriteResult.VendorTagRejected(sampleUid)

        vm.onWriteTapped()

        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
    }

    // --- UI-63: a typed brand must reach the tag exactly as typed ---

    /**
     * GitHub #8, reproduced end to end. The reporter had no Spoolman URL (their
     * screenshot showed the "Write to NFC" label, which only RawNoUrl renders),
     * picked Brand = Other and typed "Tecbears". The preset list holds
     * "TECBEARS", and the old `resolveBrandName` let that preset overwrite what
     * they typed — so the tag received a spelling that was never on screen.
     */
    @Test
    fun `typed brand reaches the tag verbatim even when a preset differs only by case`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        vm.onBrandPicked(Brand("Other"))
        vm.onCustomBrandChanged("Tecbears")
        rawWrite.nextResult = RawWriteResult.Success.Written(sampleUid)

        vm.onWriteTapped()

        assertEquals("Tecbears", rawWrite.lastInput?.newFilamentVendor)
    }

    /** The reporter's other example: "Jayo" was becoming "JAYO". */
    @Test
    fun `typed brand is not folded to the preset casing`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        vm.onBrandPicked(Brand("Other"))
        vm.onCustomBrandChanged("Jayo")
        rawWrite.nextResult = RawWriteResult.Success.Written(sampleUid)

        vm.onWriteTapped()

        assertEquals("Jayo", rawWrite.lastInput?.newFilamentVendor)
    }

    /**
     * "NextShapes stays NextShapes" in the report, because no preset matches it.
     * Pinned so the no-collision case cannot regress while the collision case is
     * being changed.
     */
    @Test
    fun `a typed brand with no preset collision is still passed through`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        vm.onBrandPicked(Brand("Other"))
        vm.onCustomBrandChanged("NextShapes")
        rawWrite.nextResult = RawWriteResult.Success.Written(sampleUid)

        vm.onWriteTapped()

        assertEquals("NextShapes", rawWrite.lastInput?.newFilamentVendor)
    }

    /**
     * Whitespace is the one thing still normalised: an untrimmed brand would
     * render as a double space inside the derived filament name.
     */
    @Test
    fun `typed brand is trimmed but otherwise untouched`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        vm.onBrandPicked(Brand("Other"))
        vm.onCustomBrandChanged("  Tecbears  ")
        rawWrite.nextResult = RawWriteResult.Success.Written(sampleUid)

        vm.onWriteTapped()

        assertEquals("Tecbears", rawWrite.lastInput?.newFilamentVendor)
    }

    /** Picking a preset from the dropdown still yields that preset's spelling. */
    @Test
    fun `a brand picked from the dropdown is written as listed`() = runTest {
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        vm.onBrandPicked(Brand("TECBEARS"))
        rawWrite.nextResult = RawWriteResult.Success.Written(sampleUid)

        vm.onWriteTapped()

        assertEquals("TECBEARS", rawWrite.lastInput?.newFilamentVendor)
    }

    @Test
    fun `vendor tag plus no Spoolman url - Write disabled (no pair affordance)`() = runTest {
        // U13: vendor tag without Spoolman has no Spoolman target to pair to,
        // so Write is disabled. RawWrite isn't a valid fallback for vendor
        // tags either — they're factory-encoded and can't be written.
        // Vendor state is reached via explicit Read (nfc.state.Success);
        // passive ambient taps no longer flip observedTagKind (2026-06-06).
        settings.pushSettings(Settings(url = ""))
        val vm = newVm()
        primeFormForWrite(vm)
        nfc.pushState(
            com.spoolpainter.app.domain.primitives.NfcResult.Success(
                sampleUid,
                TagClassification.Vendor("non-NDEF"),
            ),
        )

        // canWrite is false; tap is a no-op.
        vm.onWriteTapped()
        assertEquals(0, rawWrite.invokeCalls)
        assertEquals(0, vendorUidOnlyPair.invokeCalls)
        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
    }

    @Test
    fun `vendor tag plus Spoolman plus selected spool - Write routes to vendorUidOnlyPair (2026-06-06 reframe)`() = runTest {
        // 2026-06-06: vendor UID mapping moved off Save and onto Write.
        // Save = pure HTTP form edits across all states. Write = NFC + UID
        // for writable tags, HTTP-only UID append for vendor tags. Vendor
        // mapping requires a spool target so Write is gated on
        // selectedSpoolId; the vendor case is reachable only after a Save
        // (or pre-existing pick) populates the spool dropdown.
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        val vm = newVm()
        primeFormForWrite(vm)
        // Pre-existing spool to satisfy canWrite gate.
        val existingSpool = SpoolmanSpool(
            id = 99,
            filament = SpoolmanFilament(
                id = 200,
                name = "PLA",
                vendor = SpoolmanVendor(id = 300, name = "Bambu"),
                material = "PLA",
                color_hex = "FF0000",
                settings_extruder_temp = 210,
                settings_bed_temp = 60,
            ),
        )
        spoolman.setSpools(listOf(existingSpool))
        vm.onSpoolSelected(existingSpool)
        // Explicit Read flips observedTagKind to Vendor (2026-06-06).
        nfc.pushState(
            com.spoolpainter.app.domain.primitives.NfcResult.Success(
                sampleUid,
                TagClassification.Vendor("non-NDEF"),
            ),
        )
        vendorUidOnlyPair.nextResult =
            com.spoolpainter.app.domain.usecases.VendorUidOnlyPairResult.Cancelled("noop")

        vm.onWriteTapped()

        assertEquals(1, vendorUidOnlyPair.invokeCalls)
        assertEquals(0, rawWrite.invokeCalls)
        assertEquals(0, createAndPair.invokeCalls)
    }
}
