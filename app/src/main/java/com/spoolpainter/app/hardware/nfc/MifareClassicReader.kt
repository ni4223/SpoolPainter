package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.util.Log

private const val TAG = "MifareClassicReader"

object MifareClassicReader {
    private val defaultKeys = listOf(
        byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
        byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
    )

    fun tryReadRawCounted(
        tag: Tag,
        bambuKeysA: List<ByteArray>?,
        smKeysA: List<ByteArray>?,
        smKeysB: List<ByteArray>?,
    ): Triple<ByteArray?, Int, Int> {
        val mc = MifareClassic.get(tag) ?: run { Log.w(TAG, "tryReadRawCounted: not a MifareClassic tag"); return Triple(null, 0, 0) }
        Log.d(TAG, "tryReadRawCounted: sectors=${mc.sectorCount} bambuKeys=${bambuKeysA?.size} smKeysA=${smKeysA?.size} smKeysB=${smKeysB?.size}")
        return try {
            mc.connect()
            val sectorCount = mc.sectorCount
            val result = ByteArray(sectorCount * 64)
            var anyAuthed = false
            var bambuCount = 0
            var smCount = 0

            for (sector in 0 until sectorCount) {
                var authed = false
                var authSource = "none"

                if (bambuKeysA != null) {
                    val key = bambuKeysA.getOrNull(sector)
                    if (key != null && runCatching { mc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)) {
                        authed = true; bambuCount++; authSource = "bambu-A"
                    }
                }
                if (!authed && smKeysA != null) {
                    val key = smKeysA.getOrNull(sector)
                    if (key != null && runCatching { mc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)) {
                        authed = true; smCount++; authSource = "sm-A"
                    }
                }
                if (!authed && smKeysB != null) {
                    val key = smKeysB.getOrNull(sector)
                    if (key != null && runCatching { mc.authenticateSectorWithKeyB(sector, key) }.getOrDefault(false)) {
                        authed = true; smCount++; authSource = "sm-B"
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
            Log.d(TAG, "tryReadRawCounted done: bambu=$bambuCount sm=$smCount anyAuthed=$anyAuthed")
            Triple(if (anyAuthed) result else null, bambuCount, smCount)
        } catch (e: Exception) {
            Log.e(TAG, "tryReadRawCounted exception: $e")
            runCatching { mc.close() }
            Triple(null, 0, 0)
        }
    }
}
