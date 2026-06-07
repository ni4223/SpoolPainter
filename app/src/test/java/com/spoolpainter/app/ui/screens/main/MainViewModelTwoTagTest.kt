package com.spoolpainter.app.ui.screens.main

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.domain.usecases.CreateAndPairResult
import com.spoolpainter.app.domain.usecases.ReadAndPairUseCase
import com.spoolpainter.app.domain.usecases.RepairConfirmRequest
import com.spoolpainter.app.domain.usecases.TwoTagResult
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
class MainViewModelTwoTagTest {

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

    private val sampleUid = CardUid("AABBCCDD")
    private val sampleVendor = SpoolmanVendor(id = 1, name = "Bambu")
    private val sampleSpool = SpoolmanSpool(
        id = 42,
        filament = SpoolmanFilament(
            id = 7, name = "PLA Red", material = "PLA",
            vendor = sampleVendor, color_hex = "FF0000",
        ),
    )

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
        settings.pushSettings(com.spoolpainter.app.data.local.Settings(url = "http://10.0.0.5:8000"))
        vm.onMaterialPicked(Material("PLA", 190, 220, 55, 65))
        vm.onBrandPicked(Brand("Bambu"))
        vm.onColorHexChanged("FF0000")
        vm.onTempRangesChanged(
            TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
        )
        nfc.pushLastSeenTag(TagBuffer(sampleUid, TagClassification.Blank, capturedAtEpochMs = 0L))
    }

    /** See [MainViewModelTest.awaitNonAmbientSnackbar] for rationale. */
    private suspend fun ReceiveTurbine<UiEffect>.awaitNonAmbientSnackbar(): UiEffect.ShowSnackbar {
        val ambient = setOf(
            "Tag detected. Press Read tag to load.",
            "Tag detected. Press Read to load.",
            "Blank tag detected.",
            "Vendor tag. Press Read to load.",
        )
        while (true) {
            val effect = awaitItem() as UiEffect.ShowSnackbar
            if (effect.message in ambient) continue
            return effect
        }
    }

    private fun stagePromptingPairAnother(vm: MainViewModel) {
        primeFormForWrite(vm)
        // U13 — Write requires a saved spool. Pre-select one so onWriteTapped
        // can fire (canWrite gates on spoolman.selectedSpoolId != null).
        spoolman.setSpools(listOf(sampleSpool))
        vm.onSpoolSelected(sampleSpool)
        createAndPair.nextResult = CreateAndPairResult.Success.WrittenAndPaired(
            spoolId = 42, uid = sampleUid, isNewSpool = false,
        )
        vm.onWriteTapped()
    }

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `successful first pair transitions to PromptingPairAnother`() = runTest {
        val vm = newVm()
        stagePromptingPairAnother(vm)
        assertTrue(
            "got ${vm.state.value.activeFlow}",
            vm.state.value.activeFlow is ActiveFlow.PromptingPairAnother,
        )
        assertEquals(42, (vm.state.value.activeFlow as ActiveFlow.PromptingPairAnother).spoolId)
    }

    @Test
    fun `onPairAnotherTagAccepted transitions to WritingSecondTag and invokes useCase`() = runTest {
        val vm = newVm()
        stagePromptingPairAnother(vm)

        // Stage a slow result so the WritingSecondTag state is observable.
        twoTag.nextResult = TwoTagResult.Cancelled("delay-stub")
        twoTag.nextDelayMs = 50L
        vm.onPairAnotherTagAccepted()
        // The job is launched, but because nextDelayMs > 0 the use case
        // hasn't returned yet (UnconfinedTestDispatcher will run the prefix
        // up to the suspend point). Assert WritingSecondTag is set.
        assertTrue(
            "got ${vm.state.value.activeFlow}",
            vm.state.value.activeFlow is ActiveFlow.WritingSecondTag,
        )
    }

    @Test
    fun `onPairAnotherTagDismissed preserves form and selection and returns to Idle`() = runTest {
        val vm = newVm()
        stagePromptingPairAnother(vm)

        vm.effects.test {
            vm.onPairAnotherTagDismissed()
            val emission = awaitNonAmbientSnackbar()
            assertEquals("Saved with one tag.", emission.message)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
        // UI-06 + UI-10: form and spool selection are both preserved.
        assertNotNull(vm.state.value.form.material)
        assertNotNull(vm.state.value.form.selectedSpoolId)
        assertNotNull(vm.state.value.spoolman.selectedSpoolId)
    }

    @Test
    fun `applyTwoTagResult Success preserves form and selection and emits Both tags paired snackbar`() = runTest {
        val vm = newVm()
        stagePromptingPairAnother(vm)
        twoTag.nextResult = TwoTagResult.Success.SecondTagPaired(spoolId = 42, uid = CardUid("11223344"))

        vm.effects.test {
            vm.onPairAnotherTagAccepted()
            val emission = awaitNonAmbientSnackbar()
            assertEquals("Both tags paired", emission.message)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
        // UI-06 + UI-10: form and selection both preserved.
        assertNotNull(vm.state.value.form.material)
        assertNotNull(vm.state.value.form.selectedSpoolId)
        assertNotNull(vm.state.value.spoolman.selectedSpoolId)
    }

    @Test
    fun `applyTwoTagResult VendorTagRejected reroutes to vendor pair flow`() = runTest {
        // Per U7: when the second tag is vendor-classified, instead of
        // surfacing a snackbar we re-route through the vendor UID-only pair
        // flow against the same spool. Form preserved; transitions through
        // PairingVendorUidOnly back to Idle.
        val vm = newVm()
        stagePromptingPairAnother(vm)
        twoTag.nextResult = TwoTagResult.VendorTagRejected(CardUid("11223344"))
        vendorUidOnlyPair.nextResult = com.spoolpainter.app.domain.usecases.VendorUidOnlyPairResult.Success.UidPaired(
            spoolId = 42, uid = CardUid("11223344"), isNewSpool = false,
        )

        vm.onPairAnotherTagAccepted()

        assertEquals(ActiveFlow.Idle, vm.state.value.activeFlow)
        assertNotNull(vm.state.value.form.material)
        // Vendor use-case was invoked once.
        assertEquals(1, vendorUidOnlyPair.invokeCalls)
    }

    @Test
    fun `applyTwoTagResult MoveOnBindPartial emits partial snackbar with spool id`() = runTest {
        val vm = newVm()
        stagePromptingPairAnother(vm)
        twoTag.nextResult = TwoTagResult.MoveOnBindPartial(
            uid = CardUid("11223344"),
            partiallyModifiedSpoolId = 7,  // unchanged in TwoTagResult.MoveOnBindPartial
            reason = "boom",
        )

        vm.effects.test {
            vm.onPairAnotherTagAccepted()
            val emission = awaitNonAmbientSnackbar()
            assertTrue("got: ${emission.message}", emission.message.contains("#7"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmer pendingRequest emission transitions to AwaitingRepairConfirmation`() = runTest {
        val vm = newVm()
        // Bare VM (no flow active). When confirmer publishes a request, VM
        // transitions to AwaitingRepairConfirmation.
        confirmer.emitPending(
            RepairConfirmRequest(others = listOf(sampleSpool), targetSpoolId = 99, uid = sampleUid),
        )
        assertTrue(
            "got ${vm.state.value.activeFlow}",
            vm.state.value.activeFlow is ActiveFlow.AwaitingRepairConfirmation,
        )
        val flow = vm.state.value.activeFlow as ActiveFlow.AwaitingRepairConfirmation
        assertEquals(sampleUid, flow.uid)
        assertEquals(99, flow.targetSpoolId)
    }

    @Test
    fun `onRepairResult forwards to confirmer submitResult`() = runTest {
        val vm = newVm()
        vm.onRepairResult(true)
        assertEquals(1, confirmer.submitCalls)
        assertEquals(true, confirmer.lastSubmitValue)

        vm.onRepairResult(false)
        assertEquals(2, confirmer.submitCalls)
        assertEquals(false, confirmer.lastSubmitValue)
    }

    @Test
    fun `UI-08 AmbiguousOwnership ParseError surfaces friendly copy with spool ids`() = runTest {
        val vm = newVm()
        stagePromptingPairAnother(vm)
        twoTag.nextResult = TwoTagResult.SpoolmanFailed(
            uid = CardUid("11223344"),
            outcome = SpoolmanOutcome.ParseError(
                IllegalStateException("ambiguous ownership: spool ids 7, 8"),
            ),
        )

        vm.effects.test {
            vm.onPairAnotherTagAccepted()
            val emission = awaitNonAmbientSnackbar()
            assertTrue("got: ${emission.message}", emission.message.contains("#7"))
            assertTrue("got: ${emission.message}", emission.message.contains("#8"))
            assertTrue(
                "got: ${emission.message}",
                emission.message.contains("Fix in Spoolman", ignoreCase = true),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applyTwoTagResult SpoolmanFailed emits human readable snackbar`() = runTest {
        val vm = newVm()
        stagePromptingPairAnother(vm)
        twoTag.nextResult = TwoTagResult.SpoolmanFailed(
            uid = CardUid("11223344"),
            outcome = SpoolmanOutcome.HttpError(500, "boom"),
        )

        vm.effects.test {
            vm.onPairAnotherTagAccepted()
            val emission = awaitNonAmbientSnackbar()
            assertTrue(emission.message.contains("500"))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
