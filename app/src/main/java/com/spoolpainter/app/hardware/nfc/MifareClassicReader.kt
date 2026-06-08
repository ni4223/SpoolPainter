package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.util.Log
import com.spoolpainter.app.hardware.nfc.vendor.VendorAuth
import com.spoolpainter.app.hardware.nfc.vendor.VendorId

private const val TAG = "MifareClassicReader"

object MifareClassicReader {
    private val defaultKeys = listOf(
        byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
        byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
    )

    /**
     * Read all sectors of a MifareClassic 1k tag, trying each vendor's keys
     * in registry order per sector. Returns the raw 1024-byte read (if any
     * sector authed) plus a per-vendor auth count the dispatcher uses to
     * pick the most-likely vendor in a tiebreak.
     *
     * `vendorAuths` is iterated in insertion order — callers control vendor
     * priority by ordering the map (LinkedHashMap / `mapOf`).
     */
    fun tryReadRawCountedMulti(
        tag: Tag,
        vendorAuths: Map<VendorId, VendorAuth>,
    ): Pair<ByteArray?, Map<VendorId, Int>> {
        val mc = MifareClassic.get(tag) ?: run {
            Log.w(TAG, "tryReadRawCountedMulti: not a MifareClassic tag")
            return null to vendorAuths.keys.associateWith { 0 }
        }
        Log.d(TAG, "tryReadRawCountedMulti: sectors=${mc.sectorCount} vendors=${vendorAuths.keys}")
        return try {
            mc.connect()
            val sectorCount = mc.sectorCount
            val result = ByteArray(sectorCount * 64)
            var anyAuthed = false
            val counts = vendorAuths.keys.associateWith { 0 }.toMutableMap()

            for (sector in 0 until sectorCount) {
                var authed = false
                var authSource = "none"

                for ((vendorId, vendorAuth) in vendorAuths) {
                    if (authed) break
                    val keyA = vendorAuth.keysA.getOrNull(sector)
                    if (keyA != null && runCatching { mc.authenticateSectorWithKeyA(sector, keyA) }.getOrDefault(false)) {
                        authed = true
                        counts[vendorId] = (counts[vendorId] ?: 0) + 1
                        authSource = "$vendorId-A"
                        break
                    }
                    val keyB = vendorAuth.keysB?.getOrNull(sector)
                    if (keyB != null && runCatching { mc.authenticateSectorWithKeyB(sector, keyB) }.getOrDefault(false)) {
                        authed = true
                        counts[vendorId] = (counts[vendorId] ?: 0) + 1
                        authSource = "$vendorId-B"
                        break
                    }
                }
                if (!authed) {
                    val defaultAuthed = defaultKeys.any { key ->
                        runCatching { mc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)
                    }
                    if (defaultAuthed) { authed = true; authSource = "default" }
                }
                Log.d(TAG, "  sector $sector: auth=${if (authed) authSource else "FAILED"}")
                if (!authed) continue
                anyAuthed = true

                val firstBlock = mc.sectorToBlock(sector)
                val blockCount = mc.getBlockCountInSector(sector)
                for (block in firstBlock until firstBlock + blockCount) {
                    val blockData = runCatching { mc.readBlock(block) }.getOrNull()
                    if (blockData != null) {
                        blockData.copyInto(result, (sector * 64) + (block - firstBlock) * 16)
                    } else {
                        Log.w(TAG, "    block $block: read FAILED")
                    }
                }
            }
            mc.close()
            Log.d(TAG, "tryReadRawCountedMulti done: counts=$counts anyAuthed=$anyAuthed")
            (if (anyAuthed) result else null) to counts
        } catch (e: Exception) {
            Log.e(TAG, "tryReadRawCountedMulti exception: $e")
            runCatching { mc.close() }
            null to vendorAuths.keys.associateWith { 0 }
        }
    }
}
