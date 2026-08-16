package com.spoolpainter.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Variant/subtype field sanitisation. `.` was added 2026-08-15 per user
 * direction; the cap and the `+ ( )` allowances came from UI-50 Ask 2.
 */
class SanitiseVariantTest {

    @Test fun `dot survives`() {
        assertEquals("1.75", sanitiseVariant("1.75"))
        assertEquals("PLA 2.0", sanitiseVariant("PLA 2.0"))
    }

    @Test fun `UI-50 Ask 2 cases still survive`() {
        assertEquals("PLA (Matte)", sanitiseVariant("PLA (Matte)"))
        assertEquals("PLA+", sanitiseVariant("PLA+"))
        assertEquals("Silk-Rainbow", sanitiseVariant("Silk-Rainbow"))
    }

    @Test fun `characters outside the allowlist are dropped`() {
        // Deliberately still excluded: / # & % etc. Adding them is a separate
        // decision, not a side effect of allowing `.`.
        assertEquals("PLAPHA", sanitiseVariant("PLA/PHA"))
        // Space is allowed, so only the `#` is removed here.
        assertEquals("Color FF0000", sanitiseVariant("Color #FF0000"))
        assertEquals("AB", sanitiseVariant("A&B"))
    }

    @Test fun `control characters are dropped, not replaced by a space`() {
        assertEquals("PLAMatte", sanitiseVariant("PLA\tMatte\n"))
    }

    @Test fun `cap stays at 50 characters`() {
        val long = "A".repeat(60)
        assertEquals(50, sanitiseVariant(long)?.length)
    }

    @Test fun `dot does not consume the cap differently`() {
        // 50 chars of "A." pairs — the cap counts characters, dots included.
        val dotted = "A.".repeat(40)
        assertEquals(50, sanitiseVariant(dotted)?.length)
    }

    @Test fun `blank and symbol-only input map to null`() {
        assertNull(sanitiseVariant(""))
        assertNull(sanitiseVariant("   "))
        assertNull(sanitiseVariant("###"))
    }

    @Test fun `casing is not forced`() {
        assertEquals("mAtTe", sanitiseVariant("mAtTe"))
    }
}
