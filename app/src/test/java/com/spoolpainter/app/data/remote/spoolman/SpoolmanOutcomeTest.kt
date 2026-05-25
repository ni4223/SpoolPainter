package com.spoolpainter.app.data.remote.spoolman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SpoolmanOutcomeTest {

    @Test
    fun `flatMap success then success returns inner success`() {
        val out: SpoolmanOutcome<Int> = SpoolmanOutcome.Success(2)
        val result = out.flatMap { v -> SpoolmanOutcome.Success(v * 3) }
        assertEquals(SpoolmanOutcome.Success(6), result)
    }

    @Test
    fun `flatMap success then error short-circuits with inner error`() {
        val out: SpoolmanOutcome<Int> = SpoolmanOutcome.Success(2)
        val result = out.flatMap { SpoolmanOutcome.HttpError(500, "boom") }
        assertEquals(SpoolmanOutcome.HttpError(500, "boom"), result)
    }

    @Test
    fun `flatMap on HttpError propagates without invoking block`() {
        val original: SpoolmanOutcome<Int> = SpoolmanOutcome.HttpError(404, "missing")
        var blockInvoked = false
        val result = original.flatMap {
            blockInvoked = true
            SpoolmanOutcome.Success("never")
        }
        assertEquals(SpoolmanOutcome.HttpError(404, "missing"), result)
        assertTrue(!blockInvoked)
    }

    @Test
    fun `flatMap on NetworkError preserves cause`() {
        val cause = IOException("dns")
        val original: SpoolmanOutcome<Int> = SpoolmanOutcome.NetworkError(cause)
        val result = original.flatMap { SpoolmanOutcome.Success(it.toString()) }
        assertTrue(result is SpoolmanOutcome.NetworkError)
        assertEquals(cause, (result as SpoolmanOutcome.NetworkError).cause)
    }

    @Test
    fun `map equals flatMap of Success`() {
        val out: SpoolmanOutcome<Int> = SpoolmanOutcome.Success(7)
        val mapped = out.map { it + 1 }
        val flatMapped = out.flatMap { SpoolmanOutcome.Success(it + 1) }
        assertEquals(flatMapped, mapped)
    }

    @Test
    fun `map preserves error variant identity`() {
        val original: SpoolmanOutcome<Int> = SpoolmanOutcome.ParseError(IllegalStateException("x"))
        val result = original.map { it.toString() }
        assertTrue(result is SpoolmanOutcome.ParseError)
    }
}
