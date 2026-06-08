package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.util.Log

private const val TAG = "MifareUltralightReader"

/**
 * MifareUltralight (incl. NTAG21x/Ultralight C) page reader. Used by the
 * Anycubic / Elegoo Ultralight processors. Reads from page 0 onward and
 * concatenates into one byte array.
 *
 * Per Q-U14b-6=A: try 36 pages first; on partial-read fall back to whatever
 * the chip reports (some Ultralight chips only have 16 pages — short reads
 * still let us parse what came back).
 *
 * Some Android stacks (observed on moto g stylus 2025) don't promote a
 * genuine MIFARE Ultralight chip to `MifareUltralight` in `Tag.techList` —
 * the chip only exposes `NfcA`. We fall back to `NfcA.transceive` with a
 * raw `READ` command (`0x30 <page>`) which the chip answers with 16 bytes
 * (4 pages). ATQA `0x0044` + SAK `0x00` is the canonical Ultralight
 * signature; if the chip responds to `0x30 0x00`, it's Ultralight regardless
 * of what the techList said. Mirrors NFC Tools / TagInfo behaviour.
 */
object MifareUltralightReader {

    fun tryReadPages(tag: Tag, pageCount: Int = 36): ByteArray? {
        // Preferred path: native MifareUltralight tech.
        MifareUltralight.get(tag)?.let { mu ->
            return readViaUltralight(mu, pageCount)
        }
        // Fallback: NfcA + raw READ commands.
        Log.d(TAG, "tryReadPages: no MifareUltralight tech, falling back to NfcA")
        NfcA.get(tag)?.let { na ->
            return readViaNfcA(na, pageCount)
        }
        Log.w(TAG, "tryReadPages: neither MifareUltralight nor NfcA available")
        return null
    }

    private fun readViaUltralight(mu: MifareUltralight, pageCount: Int): ByteArray? {
        return try {
            mu.connect()
            // readPages returns 16 bytes (4 pages of 4 bytes) per call.
            // Calling readPages(N) reads pages N..N+3 (with chip-specific
            // wrap-around behaviour we don't rely on).
            val out = ByteArray(pageCount * 4)
            var bytesWritten = 0
            var page = 0
            while (page < pageCount) {
                val chunk = runCatching { mu.readPages(page) }.getOrNull()
                if (chunk == null) {
                    Log.d(TAG, "readViaUltralight: short read at page $page, got $bytesWritten bytes")
                    break
                }
                val toCopy = minOf(chunk.size, out.size - bytesWritten)
                System.arraycopy(chunk, 0, out, bytesWritten, toCopy)
                bytesWritten += toCopy
                page += 4
            }
            mu.close()
            if (bytesWritten == 0) null else out.copyOfRange(0, bytesWritten)
        } catch (e: Exception) {
            Log.e(TAG, "readViaUltralight exception: $e")
            runCatching { mu.close() }
            null
        }
    }

    private fun readViaNfcA(na: NfcA, pageCount: Int): ByteArray? {
        return try {
            na.connect()
            val out = ByteArray(pageCount * 4)
            var bytesWritten = 0
            var page = 0
            while (page < pageCount) {
                // MIFARE Ultralight READ command: 0x30 <page> → 16 bytes (4 pages).
                val cmd = byteArrayOf(0x30, page.toByte())
                val chunk = runCatching { na.transceive(cmd) }.getOrNull()
                if (chunk == null || chunk.size < 4) {
                    Log.d(TAG, "readViaNfcA: short/null response at page $page, got $bytesWritten bytes")
                    break
                }
                val toCopy = minOf(chunk.size, out.size - bytesWritten)
                System.arraycopy(chunk, 0, out, bytesWritten, toCopy)
                bytesWritten += toCopy
                page += 4
            }
            na.close()
            if (bytesWritten == 0) null else out.copyOfRange(0, bytesWritten)
        } catch (e: Exception) {
            Log.e(TAG, "readViaNfcA exception: $e")
            runCatching { na.close() }
            null
        }
    }
}
