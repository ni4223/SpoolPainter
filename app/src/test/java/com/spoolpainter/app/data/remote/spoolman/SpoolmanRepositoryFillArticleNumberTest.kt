package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
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
    fun `background fill fills the article number`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        seed(h, articleNumber = null)

        h.repository.fillArticleNumberInBackground(7, "900001")

        assertEquals("900001", h.fakeApi.filamentList.single().article_number)
    }

    @Test
    fun `background fill swallows a failure`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)

        // Unknown filament → 404; must be logged, not thrown to the caller.
        h.repository.fillArticleNumberInBackground(99, "900001")

        assertTrue(h.fakeApi.callLog.none { it.startsWith("patchFilament(") })
    }

    @Test
    fun `unknown filament surfaces the lookup error`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)

        val outcome = h.repository.fillArticleNumberIfEmpty(99, "HPL18-106")

        assertTrue("got $outcome", outcome !is SpoolmanOutcome.Success)
    }
}
