package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.parseTagConflict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** FakeSpoolmanApi's in-memory native-tag store: global UID uniqueness,
 *  idempotent re-link, 409 with holder spool_id, unlink 404s, tag filter. */
class FakeSpoolmanApiTagTest {

    private lateinit var api: FakeSpoolmanApi

    @Before
    fun setUp() {
        api = FakeSpoolmanApi()
        listOf(1, 2).forEach { id ->
            api.spoolList += SpoolmanSpool(
                id = id,
                filament = SpoolmanFilament(id = id, material = "PLA"),
            )
        }
    }

    private fun linkBody(uid: String) = TagLinkBody(uid = uid)

    @Test
    fun `linkTag succeeds and records the holder`() = runTest {
        val response = api.linkTag(1, linkBody("AABBCCDD"))
        assertTrue(response.isSuccessful)
        assertEquals("AABBCCDD", response.body()?.uid)
        assertEquals(1, api.spoolTags["AABBCCDD"])
    }

    @Test
    fun `linkTag is idempotent when the same spool already holds the uid`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))
        val response = api.linkTag(1, linkBody("AABBCCDD"))
        assertTrue(response.isSuccessful)
        assertEquals("AABBCCDD", response.body()?.uid)
        assertEquals(1, api.spoolTags["AABBCCDD"])
    }

    @Test
    fun `linkTag conflicts with holder spool_id when another spool holds the uid`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))
        val response = api.linkTag(2, linkBody("AABBCCDD"))
        assertEquals(409, response.code())
        val conflict = parseTagConflict(response.errorBody()!!.string())
        assertEquals(1, conflict?.spool_id)
        assertNull(conflict?.filament_id)
        assertTrue(!conflict?.message.isNullOrEmpty())
        assertEquals(1, api.spoolTags["AABBCCDD"])
    }

    @Test
    fun `linkTag returns 404 for unknown spool`() = runTest {
        val response = api.linkTag(99, linkBody("AABBCCDD"))
        assertEquals(404, response.code())
    }

    @Test
    fun `unlinkTag removes the tag then 404s on repeat`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))
        assertTrue(api.unlinkTag(1, "AABBCCDD").isSuccessful)
        assertTrue(!api.spoolTags.containsKey("AABBCCDD"))
        assertEquals(404, api.unlinkTag(1, "AABBCCDD").code())
    }

    @Test
    fun `unlinkTag returns 404 for unknown spool`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))
        assertEquals(404, api.unlinkTag(99, "AABBCCDD").code())
    }

    @Test
    fun `unlinkTag returns 404 when another spool holds the uid`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))
        assertEquals(404, api.unlinkTag(2, "AABBCCDD").code())
        assertEquals(1, api.spoolTags["AABBCCDD"])
    }

    @Test
    fun `listSpools tag param returns only the holding spool`() = runTest {
        api.linkTag(2, linkBody("AABBCCDD"))
        val response = api.listSpools(limit = 1000, offset = 0, allowArchived = true, tag = "AABBCCDD")
        assertTrue(response.isSuccessful)
        assertEquals(listOf(2), response.body()?.map { it.id })
    }

    @Test
    fun `listSpools tag param with unknown uid returns empty list`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))
        val response = api.listSpools(limit = 1000, offset = 0, allowArchived = true, tag = "11223344")
        assertTrue(response.isSuccessful)
        assertEquals(emptyList<Int>(), response.body()?.map { it.id })
    }

    @Test
    fun `tag lookups normalise case separators and 0x prefix like the server`() = runTest {
        api.linkTag(1, linkBody("aa:bb-cc"))
        val response = api.listSpools(limit = 1000, offset = 0, allowArchived = true, tag = "0xAABBCC")
        assertTrue(response.isSuccessful)
        assertEquals(listOf(1), response.body()?.map { it.id })
        assertTrue(api.unlinkTag(1, "aa.bb_cc").isSuccessful)
    }

    @Test
    fun `deleteSpool drops tags held by the removed spool`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))
        assertTrue(api.deleteSpool(1).isSuccessful)
        assertTrue(!api.spoolTags.containsKey("AABBCCDD"))
    }

    @Test
    fun `listSpools and getSpool carry tags derived from the native store`() = runTest {
        api.linkTag(1, linkBody("AABBCCDD"))

        val listed = api.listSpools(limit = 1000, offset = 0, allowArchived = true).body()!!
        assertEquals(listOf("AABBCCDD"), listed.first { it.id == 1 }.tags.map { it.uid })
        assertTrue(listed.first { it.id == 2 }.tags.isEmpty())

        val fetched = api.getSpool(1).body()!!
        assertEquals(listOf("AABBCCDD"), fetched.tags.map { it.uid })
        assertEquals("2026-01-01T00:00:00Z", fetched.tags.single().added)
    }
}
