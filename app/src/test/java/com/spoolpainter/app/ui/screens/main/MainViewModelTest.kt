package com.spoolpainter.app.ui.screens.main

import app.cash.turbine.test
import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.domain.usecases.CreateAndPairResult
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.hardware.nfc.TagBuffer
import com.spoolpainter.app.support.FakeCreateAndPairUseCase
import com.spoolpainter.app.support.FakeMoveOnBindConfirmer
import com.spoolpainter.app.support.FakeMoveOnBindUseCase
import com.spoolpainter.app.support.FakeNfcRepository
import com.spoolpainter.app.support.FakeRawWriteUseCase
import com.spoolpainter.app.support.FakeSettingsRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
import com.spoolpainter.app.support.FakeTwoTagUseCase
import com.spoolpainter.app.support.FakeVendorUidOnlyPairUseCase
import com.spoolpainter.app.ui.common.UiEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

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

    private val sampleUid = CardUid("0A1B2C3D")
    private val openSpoolPayload = OpenSpoolPayload(
        type = "PLA",
        colorHex = "FF0000",
        brand = "Bambu",
        minTemp = "190",
        maxTemp = "220",
    )
    private val sampleSpool = SpoolmanSpool(
        id = 42,
        filament = SpoolmanFilament(
            id = 7,
            name = "PLA Red",
            material = "PLA",
            vendor = SpoolmanVendor(id = 1, name = "Bambu"),
            color_hex = "ff0000",
            settings_extruder_temp = 200,
            settings_bed_temp = 50,
        ),
    )
    private val anotherSpool = sampleSpool.copy(id = 43)

    private val materialBrandRepo = com.spoolpainter.app.data.local.FakeMaterialBrandRepository()

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

    private fun primeFormForWrite(vm: MainViewModel) {
        // Settings URL must be non-blank for WriteMode.Spoolman; otherwise
        // onWriteTapped() routes to rawWrite (U7 dispatch).
        settings.pushSettings(Settings(url = "http://10.0.0.5:8000"))
        vm.onMaterialPicked(Material("PLA", 190, 220, 55, 65))
        vm.onBrandPicked(Brand("Bambu"))
        vm.onColorHexChanged("FF0000")
        vm.onTempRangesChanged(
            TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
        )
        // Force UID via lastSeenTag.
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
    fun `initial state is default MainUiState`() = runTest {
        val vm = newVm()
        val state = vm.state.value
        assertEquals(FormState(), state.form)
        assertEquals(BannerState.Hidden, state.banner)
        assertEquals(ActiveFlow.Idle, state.activeFlow)
        assertNull(state.ambiguity)
    }

    @Test
    fun `nfc slice mirrors NfcRepository state`() = runTest {
        val vm = newVm()
        nfc.pushState(NfcResult.Reading)
        assertEquals(NfcResult.Reading, vm.state.value.nfc)
    }

    @Test
    fun `spools slice mirrors SpoolmanRepository spools`() = runTest {
        val vm = newVm()
        spoolman.setSpools(listOf(sampleSpool))
        assertEquals(listOf(sampleSpool), vm.state.value.spoolman.spools)
    }

    @Test
    fun `urlConfigured mirrors settings url blank status`() = runTest {
        val vm = newVm()
        assertEquals(false, vm.state.value.spoolman.urlConfigured)
        settings.pushSettings(Settings(url = "http://nas:7912"))
        assertEquals(true, vm.state.value.spoolman.urlConfigured)
        settings.pushSettings(Settings(url = ""))
        assertEquals(false, vm.state.value.spoolman.urlConfigured)
    }

    @Test
    fun `banner is always Hidden in U5`() = runTest {
        val vm = newVm()
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.HttpError(500, "boom")
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        vm.onReadTapped()
        assertEquals(BannerState.Hidden, vm.state.value.banner)
    }

    @Test
    fun `onReadTapped PrefillFromSpoolman updates form and resets activeFlow`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(openSpoolPayload)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(sampleSpool))

        vm.onReadTapped()

        val s = vm.state.value
        assertEquals(ActiveFlow.Idle, s.activeFlow)
        assertEquals(42, s.form.selectedSpoolId)
        assertEquals(42, s.spoolman.selectedSpoolId)
        assertEquals(sampleUid, s.form.cardUid)
        assertEquals("PLA", s.form.material?.name)
        assertEquals("Bambu", s.form.brand?.name)
        assertEquals("FF0000", s.form.colorHex)
        assertNull(s.ambiguity)
    }

    @Test
    fun `onReadTapped PrefillFromTag maps payload to form`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(openSpoolPayload)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        vm.onReadTapped()

        val s = vm.state.value
        assertEquals(sampleUid, s.form.cardUid)
        assertEquals("Bambu", s.form.brand?.name)
        assertEquals(190, s.form.tempRanges.extruderMin)
        assertEquals(220, s.form.tempRanges.extruderMax)
        assertNull(s.form.selectedSpoolId)
    }

    @Test
    fun `onReadTapped BlankForm resets form to defaults preserves rawWriteMode`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        vm.onReadTapped()

        val s = vm.state.value
        assertEquals(sampleUid, s.form.cardUid)
        // Form resets to defaults (PLA / Generic), not null.
        assertEquals("PLA", s.form.material?.name)
        assertEquals("Generic", s.form.brand?.name)
        assertNull(s.form.selectedSpoolId)
    }

    @Test
    fun `onReadTapped Ambiguous populates AmbiguityState and form stays at defaults`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(openSpoolPayload)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(sampleSpool, anotherSpool))

        vm.onReadTapped()

        val s = vm.state.value
        assertNotNull(s.ambiguity)
        assertEquals(2, s.ambiguity!!.matches.size)
        assertEquals("PLA", s.form.material?.name)
        assertNull(s.form.selectedSpoolId)
    }

    @Test
    fun `onReadTapped SpoolmanFailed emits ShowSnackbar`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.HttpError(500, "boom")

        vm.effects.test {
            vm.onReadTapped()
            val emission = awaitItem()
            assertTrue(emission is UiEffect.ShowSnackbar)
            assertTrue((emission as UiEffect.ShowSnackbar).message.contains("500"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onReadTapped NfcFailed emits ShowSnackbar`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(null)
        nfc.setNextRead(NfcResult.Error("NFC not available"))

        vm.effects.test {
            vm.onReadTapped()
            val emission = awaitItem()
            assertTrue(emission is UiEffect.ShowSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onReadTapped while already armed is a no-op when activeFlow is not Idle`() = runTest {
        val vm = newVm()
        // First call leaves activeFlow in ReadingForPair (no buffered, no scheduled result).
        nfc.setBufferedTap(null)
        vm.onReadTapped()
        assertEquals(ActiveFlow.ReadingForPair, vm.state.value.activeFlow)
        val callsBefore = nfc.armCalls

        vm.onReadTapped()  // VM-9 guard: returns early.

        assertEquals(callsBefore, nfc.armCalls)
    }

    @Test
    fun `onSpoolSelected non-null prefills form from spool`() = runTest {
        val vm = newVm()
        vm.onSpoolSelected(sampleSpool)
        val s = vm.state.value
        assertEquals(42, s.form.selectedSpoolId)
        assertEquals("Bambu", s.form.brand?.name)
        assertEquals("FF0000", s.form.colorHex)
    }

    @Test
    fun `onSpoolSelected null clears form to defaults`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(sampleSpool))
        vm.onReadTapped()
        vm.onSpoolSelected(null)
        val s = vm.state.value
        assertNull(s.form.cardUid)
        assertNull(s.form.selectedSpoolId)
        // Form resets to defaults — not null.
        assertEquals("PLA", s.form.material?.name)
    }

    @Test
    fun `onSpoolSelected non-null with card_uids decodes UID into form`() = runTest {
        val vm = newVm()
        val spoolWithCardUids = sampleSpool.copy(
            extra = mapOf(
                "card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("0A1B2C3D"))),
            ),
        )
        vm.onSpoolSelected(spoolWithCardUids)
        assertEquals(CardUid("0A1B2C3D"), vm.state.value.form.cardUid)
    }

    @Test
    fun `onSpoolSelected non-null without card_uids clears UID`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        vm.onReadTapped()
        assertEquals(sampleUid, vm.state.value.form.cardUid)
        val spoolNoUid = sampleSpool.copy(extra = null)
        vm.onSpoolSelected(spoolNoUid)
        assertNull(vm.state.value.form.cardUid)
    }

    @Test
    fun `onSpoolSelected non-null multiUid in card_uids picks first UID`() = runTest {
        val vm = newVm()
        val spool = sampleSpool.copy(
            extra = mapOf(
                "card_uids" to ExtraCardUidsCodec.encode(
                    listOf(CardUid("AABBCCDD"), CardUid("11223344"), CardUid("DEADBEEF")),
                ),
            ),
        )
        vm.onSpoolSelected(spool)
        assertEquals(CardUid("AABBCCDD"), vm.state.value.form.cardUid)
    }

    @Test
    fun `onSpoolSelected with same id is idempotent`() = runTest {
        val vm = newVm()
        vm.onSpoolSelected(sampleSpool)
        val first = vm.state.value
        vm.onSpoolSelected(sampleSpool)
        assertEquals(first, vm.state.value)
    }

    @Test
    fun `onSpoolSelected clears AmbiguityState`() = runTest {
        val vm = newVm()
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult =
            SpoolmanOutcome.Success(listOf(sampleSpool, anotherSpool))
        vm.onReadTapped()
        assertNotNull(vm.state.value.ambiguity)

        vm.onSpoolSelected(sampleSpool)
        assertNull(vm.state.value.ambiguity)
    }

    @Test
    fun `lastSeenTag uid is mirrored into form cardUid for ambient surfacing`() = runTest {
        val vm = newVm()
        nfc.pushLastSeenTag(TagBuffer(sampleUid, TagClassification.Blank, capturedAtEpochMs = 0L))
        assertEquals(sampleUid, vm.state.value.form.cardUid)
    }

    @Test
    fun `onSettingsTapped emits Navigate settings`() = runTest {
        val vm = newVm()
        vm.effects.test {
            vm.onSettingsTapped()
            val effect = awaitItem()
            assertTrue(effect is UiEffect.Navigate)
            assertEquals("settings", (effect as UiEffect.Navigate).destination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- U6a: onWriteTapped ----

    @Test
    fun `onWriteTapped whenCanWriteFalse isNoOp andDoesNotChangeActiveFlow`() = runTest {
        val vm = newVm()
        // Force canSubmit false by clearing material; defaults pre-fill it,
        // so without this the fresh form would satisfy canWrite.
        vm.onMaterialPicked(null)
        assertEquals(false, vm.canWrite.value)
        vm.onWriteTapped()
        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
        assertEquals(0, createAndPair.invokeCalls)
    }

    @Test
    fun `onWriteTapped existingSpool emitsSnackbarAndKeepsFormOnSuccess`() = runTest {
        val vm = newVm()
        primeFormForWrite(vm)
        val spoolWithUid = sampleSpool.copy(
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(sampleUid))),
        )
        vm.onSpoolSelected(spoolWithUid)
        createAndPair.nextResult = CreateAndPairResult.Success.WrittenAndPaired(
            spoolId = 42, uid = sampleUid, isNewSpool = false,
        )

        vm.onWriteTapped()
        // UI-03 (U6b polish): the "Paired and written" snackbar was removed
        // because it slid up underneath the PairAnotherTagSheet and was
        // immediately covered. Success is now communicated by the sheet's
        // title ("Saved. Pair another tag with this spool?").
        // U6b: first-pair success transitions to PromptingPairAnother so the
        // bottom sheet asks "Pair another tag with this spool?". Form is
        // intentionally NOT cleared here — that happens on dismiss / Done.
        assertTrue(
            "got ${vm.state.value.activeFlow}",
            vm.state.value.activeFlow is ActiveFlow.PromptingPairAnother,
        )
        assertEquals(42, (vm.state.value.activeFlow as ActiveFlow.PromptingPairAnother).spoolId)
        assertNotNull(vm.state.value.form.material)
        assertEquals("FF0000", vm.state.value.form.colorHex)
        assertEquals(sampleUid, vm.state.value.form.cardUid)
        assertEquals(42, vm.state.value.form.selectedSpoolId)
        assertEquals(42, vm.state.value.spoolman.selectedSpoolId)
    }

    @Test
    fun `onWriteTapped newSpool emitsSnackbarAndKeepsFormOnSuccess`() = runTest {
        val vm = newVm()
        primeFormForWrite(vm)
        createAndPair.nextResult = CreateAndPairResult.Success.WrittenAndPaired(
            spoolId = 99, uid = sampleUid, isNewSpool = true,
        )

        vm.onWriteTapped()
        // UI-03: snackbar removed; sheet title carries the success message.
        assertNotNull(vm.state.value.form.material)
        // cardUid reflects the just-written UID (display); enforcement gone.
        assertEquals(sampleUid, vm.state.value.form.cardUid)
        // Newly-minted spool stays selected so dropdown reflects what landed.
        assertEquals(99, vm.state.value.form.selectedSpoolId)
        assertEquals(99, vm.state.value.spoolman.selectedSpoolId)
        // U6b: PromptingPairAnother is now active — see test above.
        assertTrue(vm.state.value.activeFlow is ActiveFlow.PromptingPairAnother)
    }

    @Test
    fun `onWriteTapped verifyFailed keepsFormAndEmitsSnackbar`() = runTest {
        val vm = newVm()
        primeFormForWrite(vm)
        createAndPair.nextResult = CreateAndPairResult.VerifyFailed(
            spoolId = 1, uid = sampleUid, isNewSpool = false, cause = "verify mismatch",
        )

        vm.effects.test {
            vm.onWriteTapped()
            val emission = awaitItem() as UiEffect.ShowSnackbar
            assertTrue(emission.message.contains("Verify failed"))
            cancelAndIgnoreRemainingEvents()
        }
        // Form preserved.
        assertNotNull(vm.state.value.form.material)
        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
    }

    @Test
    fun `onWriteTapped spoolmanFailed keepsFormAndEmitsHumanReadable`() = runTest {
        val vm = newVm()
        primeFormForWrite(vm)
        createAndPair.nextResult = CreateAndPairResult.SpoolmanFailed(
            uid = sampleUid,
            outcome = SpoolmanOutcome.HttpError(500, "boom"),
        )

        vm.effects.test {
            vm.onWriteTapped()
            val emission = awaitItem() as UiEffect.ShowSnackbar
            assertTrue(emission.message.contains("500"))
            cancelAndIgnoreRemainingEvents()
        }
        assertNotNull(vm.state.value.form.material)
    }

    @Test
    fun `onWriteTapped nfcFailed keepsFormAndEmitsSnackbar`() = runTest {
        val vm = newVm()
        primeFormForWrite(vm)
        createAndPair.nextResult = CreateAndPairResult.NfcFailed(
            uid = sampleUid, reason = "tag lost",
        )

        vm.effects.test {
            vm.onWriteTapped()
            val emission = awaitItem() as UiEffect.ShowSnackbar
            assertTrue(emission.message.contains("tag lost"))
            cancelAndIgnoreRemainingEvents()
        }
        assertNotNull(vm.state.value.form.material)
    }

    @Test
    fun `onWriteTapped concurrentReadTapped is dropped`() = runTest {
        val vm = newVm()
        primeFormForWrite(vm)
        // Stage write that returns synchronously.
        createAndPair.nextResult = CreateAndPairResult.Success.WrittenAndPaired(
            spoolId = 1, uid = sampleUid, isNewSpool = true,
        )
        vm.onWriteTapped()
        // U6b: after a successful write, activeFlow is PromptingPairAnother
        // (not Idle). onReadTapped guards against non-Idle activeFlow, so the
        // call is a no-op and the prompt stays up.
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        vm.onReadTapped()
        assertTrue(
            "got ${vm.state.value.activeFlow}",
            vm.state.value.activeFlow is ActiveFlow.PromptingPairAnother,
        )
    }
}
