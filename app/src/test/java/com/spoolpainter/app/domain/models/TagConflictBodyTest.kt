package com.spoolpainter.app.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagConflictBodyTest {

    @Test
    fun `parses spool conflict with holder id`() {
        val body = parseTagConflict(
            """{"message": "Tag is already used", "spool_id": 3, "filament_id": null}""",
        )
        assertEquals("Tag is already used", body?.message)
        assertEquals(3, body?.spool_id)
        assertNull(body?.filament_id)
    }

    @Test
    fun `parses filament conflict with holder id`() {
        val body = parseTagConflict(
            """{"message": "Tag is already used", "spool_id": null, "filament_id": 7}""",
        )
        assertEquals(7, body?.filament_id)
        assertNull(body?.spool_id)
    }

    @Test
    fun `null holder ids when server omits them`() {
        val body = parseTagConflict("""{"message": "conflict"}""")
        assertEquals("conflict", body?.message)
        assertNull(body?.spool_id)
        assertNull(body?.filament_id)
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(parseTagConflict("not json"))
        assertNull(parseTagConflict(""))
        assertNull(parseTagConflict("[1, 2, 3]"))
    }

    @Test
    fun `body without required message returns null`() {
        assertNull(parseTagConflict("""{"spool_id": 3}"""))
        assertNull(parseTagConflict("{}"))
    }
}
