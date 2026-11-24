package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.primitives.CardUid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoveOnBindConfirmerImplTest {

    private val uid = CardUid("AABBCCDD")
    private val otherSpool = SpoolmanSpool(
        id = 7,
        filament = SpoolmanFilament(
            id = 3,
            name = "PLA Red",
            material = "PLA",
            vendor = SpoolmanVendor(id = 1, name = "Bambu"),
            color_hex = "FF0000",
        ),
    )

    @Test
    fun `second confirm while first is pending supersedes instead of throwing`() = runTest(UnconfinedTestDispatcher()) {
        val confirmer = MoveOnBindConfirmerImpl()
        // With Unconfined dispatch the async body runs eagerly up to the
        // deferred.await() suspension point, so a request is pending now.
        val first = async { confirmer.confirm(listOf(otherSpool), targetSpoolId = 42, uid = uid) }
        assertNotNull(confirmer.pendingRequest.value)

        // The exact wedge scenario: a second request arrives while the first
        // is still pending. Old behaviour: check() threw
        // IllegalStateException into the calling NFC flow and stranded the
        // viewmodel's activeFlow. New behaviour: supersede, no throw.
        val second = async { confirmer.confirm(listOf(otherSpool), targetSpoolId = 43, uid = uid) }

        // The stale awaiter resolves Declined (false) so its flow exits
        // cleanly through the Cancelled("repair declined") path.
        assertFalse(first.await())
        // The new request is live and resolves normally.
        confirmer.submitResult(true)
        assertEquals(true, second.await())
        assertNull(confirmer.pendingRequest.value)
    }

    @Test
    fun `superseded first request does not clobber the second pendingRequest`() = runTest(UnconfinedTestDispatcher()) {
        val confirmer = MoveOnBindConfirmerImpl()
        val first = async { confirmer.confirm(listOf(otherSpool), targetSpoolId = 42, uid = uid) }
        val second = async { confirmer.confirm(listOf(otherSpool), targetSpoolId = 43, uid = uid) }

        // Give the superseded awaiter a chance to run its finally block; the
        // second request must still be the one surfaced to the UI.
        first.await()
        assertEquals(43, confirmer.pendingRequest.value?.targetSpoolId)

        confirmer.submitResult(true)
        assertEquals(true, second.await())
        assertNull(confirmer.pendingRequest.value)
    }

    @Test
    fun `submitResult after resolution is a no-op`() = runTest(UnconfinedTestDispatcher()) {
        val confirmer = MoveOnBindConfirmerImpl()
        val first = async { confirmer.confirm(listOf(otherSpool), targetSpoolId = 42, uid = uid) }
        confirmer.submitResult(true)
        assertEquals(true, first.await())

        // A late submit (e.g. UI double-tap on the sheet) resolves nothing
        // and throws nothing — submitResult stays null-safe.
        confirmer.submitResult(false)
        assertNull(confirmer.pendingRequest.value)
    }
}
