package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec
import com.spoolpainter.app.domain.usecases.NewFilamentRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolmanRepositoryCacheInvalidationTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `successful PATCH replaces spool by id in spools cache`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        val original = SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(id = 1, material = "PLA"),
        )
        h.fakeApi.spoolList += original
        h.repository.refresh()
        assertEquals(1, h.repository.spools.value.size)

        h.repository.appendCardUidToSpool(1, CardUid("AABBCCDD"))
        assertEquals(1, h.repository.spools.value.size)
        assertEquals(
            "\"AABBCCDD\"",
            h.repository.spools.value.first().extra?.get("card_uids"),
        )
    }

    @Test
    fun `successful POST prepends new vendor in vendors cache`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolExtraFields += "card_uids"
        h.fakeApi.filamentExtraFields += "variant"
        val outcome = h.repository.createSpoolForNewFilament(
            NewFilamentRequest(
                name = "Polymaker PLA",
                vendorName = "Polymaker",
                materialName = "PLA",
                colorHex = "FF0000",
                variant = null,
                tempRanges = TempRanges(extruderMin = 200, extruderMax = 220, bedMin = 60, bedMax = 60),
            ),
        )
        assertTrue(outcome is SpoolmanOutcome.Success)
        assertEquals(1, h.repository.vendors.value.size)
        assertEquals("Polymaker", h.repository.vendors.value.first().name)
        assertEquals(1, h.repository.filaments.value.size)
        assertEquals(1, h.repository.spools.value.size)
    }

    @Test
    fun `findSpoolsByCardUid does not touch spools cache`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.spoolList += SpoolmanSpool(
            id = 1,
            filament = SpoolmanFilament(id = 1, material = "PLA"),
            extra = mapOf("card_uids" to ExtraCardUidsCodec.encode(listOf(CardUid("AABBCCDD")))),
        )
        // Pre-condition: cache is empty (no refresh)
        assertTrue(h.repository.spools.value.isEmpty())
        h.repository.findSpoolsByCardUid(CardUid("AABBCCDD"))
        assertTrue(h.repository.spools.value.isEmpty())
    }

    @Test
    fun `refresh repopulates all caches`() = runTest {
        val h = SpoolmanRepositoryHarness(tempFolder)
        h.fakeApi.vendorList += SpoolmanVendor(id = 1, name = "V1")
        h.fakeApi.filamentList += SpoolmanFilament(id = 11, material = "PLA")
        h.fakeApi.spoolList += SpoolmanSpool(
            id = 21,
            filament = SpoolmanFilament(id = 11, material = "PLA"),
        )
        h.repository.refresh()
        assertEquals(1, h.repository.vendors.value.size)
        assertEquals(1, h.repository.filaments.value.size)
        assertEquals(1, h.repository.spools.value.size)
    }
}
