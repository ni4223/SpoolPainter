package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import android.util.Log
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.primitives.OpenSpoolDecodeResult
import com.spoolpainter.app.domain.primitives.OpenSpoolPayloadCodec
import kotlin.text.Charsets

object TagFormatParser {
    private const val TAG = "TagFormatParser"

    fun parseDefault(tag: Tag? = null, ndefRecords: List<NdefRecordView>? = null, rawData: ByteArray? = null): OpenSpoolPayload? {
        // 1. OpenSpool NDEF
        if (ndefRecords != null) {
            val mimeRecord = ndefRecords.firstOrNull { record ->
                record.tnf == NdefRecordView.TNF_MIME_MEDIA && run {
                    val mime = String(record.type, Charsets.US_ASCII).lowercase()
                    mime == NfcRepository.MIME_OPENSPOOL || mime == NfcRepository.MIME_JSON
                }
            }
            if (mimeRecord != null) {
                val json = String(mimeRecord.payload, Charsets.UTF_8)
                val decoded = OpenSpoolPayloadCodec.fromJson(json)
                if (decoded is OpenSpoolDecodeResult.Success) {
                    return decoded.payload
                }
            }
        }

        // 2. Bambu binary (if rawData is provided or attempt read with default keys only)
        val data = rawData ?: tag?.let { MifareClassicReader.tryReadRaw(it) }
        if (data != null) {
            parseBambuTag(data)?.let { return it }
        }

        return null
    }

    fun parseWithBambuKeys(tag: Tag, ndefRecords: List<NdefRecordView>?, saltHex: String): OpenSpoolPayload? {
        val uid = tag.id
        val keysA = try { bambuDeriveKeys(uid, saltHex) } catch (e: Exception) { null }
        val raw = if (keysA != null) MifareClassicReader.tryReadRaw(tag, extraKeysA = keysA) else null
        if (raw != null) {
            parseBambuTag(raw)?.let { return it }
        }
        return parseDefault(tag, ndefRecords, raw)
    }

    fun parseWithSnapmakerKeys(tag: Tag, ndefRecords: List<NdefRecordView>?, saltHex: String): OpenSpoolPayload? {
        val uid4 = tag.id.copyOfRange(0, minOf(4, tag.id.size))
        val keys = try { snapmakerDeriveKeys(uid4, saltHex) } catch (e: Exception) { null }
        val raw = if (keys != null) MifareClassicReader.tryReadRaw(tag, keys.first, keys.second) else null
        if (raw != null) {
            parseSnapmakerTag(raw, keysA = keys?.first, keysB = keys?.second)?.let { return it }
        }
        return parseDefault(tag, ndefRecords, raw)
    }

    fun parseWithBothKeys(
        tag: Tag,
        ndefRecords: List<NdefRecordView>?,
        bambuSaltHex: String,
        snapmakerSaltHex: String
    ): OpenSpoolPayload? {
        // Try OpenSpool NDEF first - no keys required
        if (ndefRecords != null) {
            val mimeRecord = ndefRecords.firstOrNull { record ->
                record.tnf == NdefRecordView.TNF_MIME_MEDIA && run {
                    val mime = String(record.type, Charsets.US_ASCII).lowercase()
                    mime == NfcRepository.MIME_OPENSPOOL || mime == NfcRepository.MIME_JSON
                }
            }
            if (mimeRecord != null) {
                val json = String(mimeRecord.payload, Charsets.UTF_8)
                val decoded = OpenSpoolPayloadCodec.fromJson(json)
                if (decoded is OpenSpoolDecodeResult.Success) {
                    return decoded.payload
                }
            }
        }

        val uid = tag.id
        val uid4 = uid.copyOfRange(0, minOf(4, uid.size))

        val bambuKeysA = try { bambuDeriveKeys(uid, bambuSaltHex) } catch (_: Exception) { null }
        val smKeys = try { snapmakerDeriveKeys(uid4, snapmakerSaltHex) } catch (_: Exception) { null }
        val smKeysA = smKeys?.first
        val smKeysB = smKeys?.second

        val (raw, bambuAuthCount, smAuthCount) = MifareClassicReader.tryReadRawCounted(
            tag,
            bambuKeysA = bambuKeysA,
            smKeysA = smKeysA,
            smKeysB = smKeysB
        )

        if (raw != null) {
            val likelySnapmaker = smAuthCount > bambuAuthCount
            if (likelySnapmaker) {
                parseSnapmakerTag(raw, keysA = smKeysA, keysB = smKeysB)?.let { return it }
            }
            parseBambuTag(raw)?.let { return it }
            if (!likelySnapmaker) {
                parseSnapmakerTag(raw, keysA = smKeysA, keysB = smKeysB)?.let { return it }
            }
        }

        return parseDefault(tag, ndefRecords, raw)
    }
}
