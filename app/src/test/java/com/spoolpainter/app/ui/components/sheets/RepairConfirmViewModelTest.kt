package com.spoolpainter.app.ui.components.sheets

import app.cash.turbine.test
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.usecases.RepairConfirmRequest
import com.spoolpainter.app.support.FakeMoveOnBindConfirmer
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

@OptIn(ExperimentalCoroutinesApi::class)
class RepairConfirmViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val uid = CardUid("AABBCCDD")
    private val spool = SpoolmanSpool(
        id = 7,
        filament = SpoolmanFilament(
            id = 11, name = "PLA Red", material = "PLA",
            vendor = SpoolmanVendor(id = 1, name = "Bambu"),
            color_hex = "FF0000",
        ),
    )

    @Test
    fun `uiState hidden when no pending request`() = runTest {
        val confirmer = FakeMoveOnBindConfirmer()
        val vm = RepairConfirmViewModel(confirmer)

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.visible)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uiState renders display name and ids when request pending`() = runTest {
        val confirmer = FakeMoveOnBindConfirmer()
        val vm = RepairConfirmViewModel(confirmer)

        vm.uiState.test {
            // initial hidden
            assertFalse(awaitItem().visible)

            confirmer.emitPending(RepairConfirmRequest(listOf(spool), targetSpoolId = 42, uid = uid))

            val s = awaitItem()
            assertTrue(s.visible)
            assertEquals(1, s.otherSpoolDisplays.size)
            assertEquals(42, s.targetSpoolId)
            assertEquals(uid, s.uid)
            assertTrue(
                "display: ${s.otherSpoolDisplays.first()}",
                s.otherSpoolDisplays.first().contains("Bambu"),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onConfirm submits true to confirmer`() = runTest {
        val confirmer = FakeMoveOnBindConfirmer()
        val vm = RepairConfirmViewModel(confirmer)
        vm.onConfirm()
        assertEquals(1, confirmer.submitCalls)
        assertEquals(true, confirmer.lastSubmitValue)
    }

    @Test
    fun `onDismiss submits false to confirmer`() = runTest {
        val confirmer = FakeMoveOnBindConfirmer()
        val vm = RepairConfirmViewModel(confirmer)
        vm.onDismiss()
        assertEquals(1, confirmer.submitCalls)
        assertEquals(false, confirmer.lastSubmitValue)
    }
}
