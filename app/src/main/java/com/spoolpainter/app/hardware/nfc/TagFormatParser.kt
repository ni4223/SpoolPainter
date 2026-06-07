package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import com.spoolpainter.app.domain.models.OpenSpoolPayload

/**
 * Vendor-tag parser dispatcher. Only invoked from `NfcRepository.handleTag`
 * when an explicit Read encounters a tag that the standard NDEF classifier
 * already labelled as Vendor (MifareClassic chip with no readable OpenSpool
 * MIME record). NDEF parsing is therefore not duplicated here — the standard
 * `classify(raw)` path owns it.
 *
 * Either or both salts may be blank; HKDF derivation throws on a malformed
 * salt and is caught locally, so callers can pass empty strings to disable a
 * format.
 */
object TagFormatParser {

    fun parseVendor(
        tag: Tag,
        bambuSaltHex: String,
        snapmakerSaltHex: String,
    ): OpenSpoolPayload? {
        val uid = tag.id
        val uid4 = uid.copyOfRange(0, minOf(4, uid.size))

        val bambuKeysA = if (bambuSaltHex.isNotBlank()) {
            try { bambuDeriveKeys(uid, bambuSaltHex) } catch (_: Exception) { null }
        } else null
        val smKeys = if (snapmakerSaltHex.isNotBlank()) {
            try { snapmakerDeriveKeys(uid4, snapmakerSaltHex) } catch (_: Exception) { null }
        } else null
        val smKeysA = smKeys?.first
        val smKeysB = smKeys?.second

        if (bambuKeysA == null && smKeysA == null) return null

        val (raw, bambuAuthCount, smAuthCount) = MifareClassicReader.tryReadRawCounted(
            tag,
            bambuKeysA = bambuKeysA,
            smKeysA = smKeysA,
            smKeysB = smKeysB,
        )
        if (raw == null) return null

        // Auth-count tiebreak: whichever key set unlocked more sectors goes
        // first. Falls through to the other format if the first parse fails
        // (e.g. correctly-derived keys but corrupted tag bytes).
        val likelySnapmaker = smAuthCount > bambuAuthCount
        return if (likelySnapmaker) {
            parseSnapmakerTag(raw, keysA = smKeysA, keysB = smKeysB)
                ?: parseBambuTag(raw)
        } else {
            parseBambuTag(raw)
                ?: parseSnapmakerTag(raw, keysA = smKeysA, keysB = smKeysB)
        }
    }
}
