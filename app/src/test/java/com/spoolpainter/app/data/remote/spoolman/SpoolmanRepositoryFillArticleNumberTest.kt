package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** fillArticleNumberIfEmpty — records a vendor tag's product code on a
 *  filament without ever overwriting an existing value. */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryFillArticleNumberTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun seed(h: SpoolmanRepositoryHarness, articleNumber: String?) {
        h.fakeApi.filamentList += SpoolmanFilament(id = 7, material = "PLA", article_number = articleNumber)
    }

    @Test
    fun `empty article number is filled`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = null)

        val outcome = h.repository.fillArticleNumberIfEmpty(7, "HPL18-106")

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals("HPL18-106", h.fakeApi.filamentList.single().article_number)
        assertTrue(h.fakeApi.callLog.any { it.startsWith("patchFilament(7,") })
    }

    @Test
    fun `blank article number is treated as empty`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = "  ")

        h.repository.fillArticleNumberIfEmpty(7, "G00-R0")

        assertEquals("G00-R0", h.fakeApi.filamentList.single().article_number)
    }

    @Test
    fun `existing article number is never overwritten`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = "USER-TYPED")

        val outcome = h.repository.fillArticleNumberIfEmpty(7, "HPL18-106")

        assertTrue("got $outcome", outcome is SpoolmanOutcome.Success)
        assertEquals("USER-TYPED", h.fakeApi.filamentList.single().article_number)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchFilament(") })
    }

    @Test
    fun `stale cached code does not skip the write`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = "HPL18-106")
        h.repository.refresh()
        // Cleared in Spoolman after the app cached it.
        h.fakeApi.filamentList[0] = h.fakeApi.filamentList[0].copy(article_number = null)

        h.repository.fillArticleNumberIfEmpty(7, "HPL18-106")

        assertEquals("HPL18-106", h.fakeApi.filamentList.single().article_number)
        assertTrue(h.fakeApi.callLog.any { it.startsWith("patchFilament(7,") })
    }

    @Test
    fun `background fill fills the article number and reports the stored filament`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = null)

        val outcome = h.repository.fillArticleNumberInBackground(7, "900001").await()

        assertEquals("900001", h.fakeApi.filamentList.single().article_number)
        assertEquals("900001", (outcome as SpoolmanOutcome.Success).data.article_number)
    }

    @Test
    fun `background fill swallows a failure`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)

        // Unknown filament → 404; must be logged, not thrown to the caller.
        val outcome = h.repository.fillArticleNumberInBackground(99, "900001").await()

        assertTrue("got $outcome", outcome !is SpoolmanOutcome.Success)
        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchFilament(") })
    }

    @Test
    fun `two concurrent fills on one empty filament PATCH once and the first code wins`() = runTest(
        timeout = 5.seconds,
    ) {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = null)
        val gate = CompletableDeferred<Unit>()
        h.fakeApi.getFilamentLatency = gate

        // A reads "empty" and is held; B starts while A's answer is in flight.
        val a = async(start = CoroutineStart.UNDISPATCHED) { h.repository.fillArticleNumberIfEmpty(7, "HPL18-106") }
        val b = async(start = CoroutineStart.UNDISPATCHED) { h.repository.fillArticleNumberIfEmpty(7, "HPL18-999") }
        gate.complete(Unit)
        a.await()
        b.await()

        assertEquals(1, h.fakeApi.callLog.count { it.startsWith("patchFilament(7,") })
        assertEquals("HPL18-106", h.fakeApi.filamentList.single().article_number)
    }

    @Test
    fun `fills on different filaments don't wait for each other`() = runTest(timeout = 5.seconds) {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = null)
        h.fakeApi.filamentList += SpoolmanFilament(id = 8, material = "PETG")
        val gate = CompletableDeferred<Unit>()
        h.fakeApi.getFilamentLatency = gate

        val a = async(start = CoroutineStart.UNDISPATCHED) { h.repository.fillArticleNumberIfEmpty(7, "HPL18-106") }
        // 7 is still held at its GET; 8 must finish regardless.
        val other = h.repository.fillArticleNumberIfEmpty(8, "A00-G1")
        assertTrue("got $other", other is SpoolmanOutcome.Success)
        assertEquals("A00-G1", h.fakeApi.filamentList.first { it.id == 8 }.article_number)

        gate.complete(Unit)
        a.await()
    }

    @Test
    fun `unknown filament surfaces the lookup error`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)

        val outcome = h.repository.fillArticleNumberIfEmpty(99, "HPL18-106")

        assertTrue("got $outcome", outcome !is SpoolmanOutcome.Success)
    }
}
