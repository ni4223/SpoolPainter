package com.spoolpainter.app.hardware.nfc

import android.nfc.NdefMessage
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

    fun tagDescription(tag: Tag): String? {
        val mc = MifareClassic.get(tag) ?: return null
        return when (mc.type) {
            MifareClassic.TYPE_PLUS -> "Mifare Plus"
            MifareClassic.TYPE_PRO -> "Mifare Pro"
            else -> when (mc.size) {
                MifareClassic.SIZE_1K -> "Mifare Classic 1K"
                MifareClassic.SIZE_2K -> "Mifare Classic 2K"
                MifareClassic.SIZE_4K -> "Mifare Classic 4K"
                else -> "Mifare Classic"
            }
        }
    }

    fun tryReadNdef(tag: Tag): NdefMessage? {
        val mc = MifareClassic.get(tag) ?: return null
        return try {
            mc.connect()
            val bytes = readNdefBytes(mc)
            mc.close()
            bytes?.let(::parseNdefTlv)
        } catch (_: Exception) {
            runCatching { mc.close() }
            null
        }
    }

    fun tryReadRaw(tag: Tag, extraKeysA: List<ByteArray> = emptyList(), extraKeysB: List<ByteArray> = emptyList()): ByteArray? {
        val mc = MifareClassic.get(tag) ?: run { Log.w(TAG, "tryReadRaw: not a MifareClassic tag"); return null }
        Log.d(TAG, "tryReadRaw: sectors=${mc.sectorCount} size=${mc.size} extraKeysA=${extraKeysA.size} extraKeysB=${extraKeysB.size}")
        return try {
            mc.connect()
            val bytes = readRawBytes(mc, extraKeysA, extraKeysB)
            mc.close()
            if (bytes != null) Log.d(TAG, "tryReadRaw: got ${bytes.size} bytes")
            else Log.w(TAG, "tryReadRaw: no sectors authenticated, returning null")
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "tryReadRaw: exception: $e")
            runCatching { mc.close() }
            null
        }
    }

    fun tryReadRawCounted(
        tag: Tag,
        bambuKeysA: List<ByteArray>?,
        smKeysA: List<ByteArray>?,
        smKeysB: List<ByteArray>?
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

                if (!authed && bambuKeysA != null) {
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
                        Log.d(TAG, "    block $block: ${blockData.take(8).joinToString(" ") { "%02X".format(it) }}...")
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

    private fun readRawBytes(mc: MifareClassic, extraKeysA: List<ByteArray>, extraKeysB: List<ByteArray>): ByteArray? {
        val sectorCount = mc.sectorCount
        val result = ByteArray(sectorCount * 64)
        var anyAuthed = false
        for (sector in 0 until sectorCount) {
            val derivedA = extraKeysA.getOrNull(sector)
            val derivedB = extraKeysB.getOrNull(sector)
            val candidateKeysA = listOfNotNull(derivedA) + defaultKeys
            val candidateKeysB = listOfNotNull(derivedB) + defaultKeys

            var authSource = "none"
            val authed = candidateKeysA.indexOfFirst { key ->
                runCatching { mc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)
            }.also { idx ->
                if (idx >= 0) authSource = if (derivedA != null && idx == 0) "derivedA" else "defaultA[$idx]"
            } >= 0 || candidateKeysB.indexOfFirst { key ->
                runCatching { mc.authenticateSectorWithKeyB(sector, key) }.getOrDefault(false)
            }.also { idx ->
                if (idx >= 0) authSource = if (derivedB != null && idx == 0) "derivedB" else "defaultB[$idx]"
            } >= 0

            Log.d(TAG, "  readRawBytes sector $sector: auth=${if (authed) authSource else "FAILED"}")
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
        return if (anyAuthed) result else null
    }

    private fun readNdefBytes(mc: MifareClassic): ByteArray? {
        val result = mutableListOf<Byte>()
        val startSector = if (mc.sectorCount > 1) 1 else 0
        for (sector in startSector until mc.sectorCount) {
            val authed = defaultKeys.any { key ->
                runCatching { mc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)
            }
            if (!authed) continue
            val first = mc.sectorToBlock(sector)
            val count = mc.getBlockCountInSector(sector)
            for (block in first until first + count - 1) {
                runCatching { result.addAll(mc.readBlock(block).toList()) }
            }
        }
        return if (result.isEmpty()) null else result.toByteArray()
    }

    private fun parseNdefTlv(data: ByteArray): NdefMessage? {
        var i = 0
        while (i < data.size) {
            when (val type = data[i++].toInt() and 0xFF) {
                0x00 -> continue
                0xFE -> return null
                0x03 -> {
                    if (i >= data.size) return null
                    val len = if ((data[i].toInt() and 0xFF) == 0xFF) {
                        if (i + 2 >= data.size) return null
                        val v = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i + 2].toInt() and 0xFF)
                        i += 3; v
                    } else {
                        data[i++].toInt() and 0xFF
                    }
                    if (i + len > data.size) return null
                    return runCatching { NdefMessage(data.sliceArray(i until i + len)) }.getOrNull()
                }
                else -> {
                    if (i >= data.size) return null
                    i += 1 + (data[i].toInt() and 0xFF)
                }
            }
        }
        return null
    }
}
