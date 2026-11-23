package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.ui.screens.main.FormMapping
import com.spoolpainter.app.ui.screens.main.saveBlocker
import com.spoolpainter.app.domain.primitives.CardUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U28 / UI-70 (#10). The type tables are pinned to Snapmaker's firmware, and a
 * signature-verified tag with a code the app doesn't know still reads (D1, D2).
 *
 * Decoding is tested past the RSA check through [decodeSnapmakerPayload], on a
 * real Snapmaker dump with its type bytes rewritten (which would break the
 * signature, hence the split).
 */
class SnapmakerTypeCodesTest {

    // Snapmaker/u1-klipper klippy/extras/filament_protocol.py, read 2026-11-22
    // at 7ac453b. Main type 0 ("Reserved") is deliberately not mapped.
    private val firmwareMainTypes = mapOf(
        1 to "PLA", 2 to "PETG", 3 to "ABS", 4 to "TPU", 5 to "PVA", 6 to "ASA",
        9 to "PA", 10 to "PA-CF", 11 to "PA-GF", 12 to "PC", 17 to "PETG-CF",
        20 to "PLA-CF", 22 to "PEBA", 23 to "TPE", 24 to "PAHT-CF", 25 to "PA6-CF",
        26 to "PA6-GF", 27 to "ASA-CF",
    )
    private val firmwareSubTypes = mapOf(
        1 to "Basic", 2 to "Matte", 3 to "SnapSpeed", 4 to "Silk", 5 to "Support",
        6 to "HF", 7 to "95A", 8 to "95A HF", 9 to "90A", 10 to "85A", 11 to "Wood",
        12 to "Translucent", 13 to "Full Spectrum", 14 to "Glow", 15 to "Rainbow",
        16 to "Marble", 17 to "Starlight", 18 to "Aero",
    )

    private val realDump: ByteArray =
        javaClass.getResourceAsStream("/vendor-fixtures/Snapmaker/Snapmaker SnapSpeed PLA Black 500g.bin")!!
            .readBytes()

    /** The real dump with its main/sub type codes rewritten (u16 LE at 66 / 68). */
    private fun dumpWith(main: Int? = null, sub: Int? = null): ByteArray = realDump.copyOf().also { b ->
        main?.let { b[66] = (it and 0xFF).toByte(); b[67] = (it shr 8).toByte() }
        sub?.let { b[68] = (it and 0xFF).toByte(); b[69] = (it shr 8).toByte() }
    }

    @Test
    fun `type tables match the firmware exactly`() {
        assertEquals(firmwareMainTypes, MAIN_TYPES)
        assertEquals(firmwareSubTypes, SUB_TYPES)
    }

    @Test
    fun `decode of the untouched dump matches the signed parse`() {
        val signed = parseSnapmakerTag(realDump, keysA = null, keysB = null)
        assertNotNull(signed)
        assertEquals(signed, decodeSnapmakerPayload(realDump))
        assertTrue(signed!!.decodeNotes.isEmpty())
        assertNull(signed.readNotice)
    }

    @Test
    fun `new sub types decode by name`() {
        assertEquals("Translucent", decodeSnapmakerPayload(dumpWith(main = 2, sub = 12)).subtype)
        assertEquals("Starlight", decodeSnapmakerPayload(dumpWith(sub = 17)).subtype)
        assertEquals("Aero", decodeSnapmakerPayload(dumpWith(sub = 18)).subtype)
    }

    @Test
    fun `new main types decode by name`() {
        assertEquals("PA-CF", decodeSnapmakerPayload(dumpWith(main = 10)).type)
        assertEquals("ASA-CF", decodeSnapmakerPayload(dumpWith(main = 27)).type)
    }

    @Test
    fun `unknown sub type reads as no variant and is noted (D1)`() {
        val p = decodeSnapmakerPayload(dumpWith(sub = 19))
        assertEquals("Basic", p.subtype)
        assertEquals("PLA", p.type)
        assertEquals(listOf("Snapmaker sub type 19 unknown"), p.decodeNotes)
        assertNull(p.readNotice)
        assertNull(FormMapping.fromOpenSpool(CardUid("0a1b"), p, rawWriteMode = false).variant)
    }

    @Test
    fun `unknown main type leaves material empty, keeps the rest, and says so (D2)`() {
        val known = decodeSnapmakerPayload(realDump)
        val p = decodeSnapmakerPayload(dumpWith(main = 30))
        assertEquals("", p.type)
        assertEquals("Unknown Snapmaker material (code 30). Pick the material.", p.readNotice)
        assertEquals(listOf("Snapmaker main type 30 unknown"), p.decodeNotes)
        // Everything else from the tag is kept.
        assertEquals(known.colorHex, p.colorHex)
        assertEquals(known.brand, p.brand)
        assertEquals(known.minTemp, p.minTemp)
        assertEquals(known.maxTemp, p.maxTemp)
        assertEquals(known.subtype, p.subtype)

        val form = FormMapping.fromOpenSpool(CardUid("0a1b"), p, rawWriteMode = false)
        assertNull(form.material)
        assertEquals(known.minTemp.toInt(), form.tempRanges.extruderMin)
        assertEquals(known.colorHex, form.colorHex)
        assertEquals("Pick a material.", form.saveBlocker)
    }

    @Test
    fun `main type 0 (Reserved) reads as unknown`() {
        assertEquals("", decodeSnapmakerPayload(dumpWith(main = 0)).type)
    }

    @Test
    fun `a rewritten dump still fails the signed parse`() {
        // Guards the premise of this file: the split is what makes these
        // cases testable, the signature check itself is unchanged.
        assertNull(parseSnapmakerTag(dumpWith(sub = 17), keysA = null, keysB = null))
    }
}
