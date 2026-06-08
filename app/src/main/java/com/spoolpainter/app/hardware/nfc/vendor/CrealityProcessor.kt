package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.tech.MifareClassic
import android.util.Log
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private const val TAG = "CrealityProcessor"

/**
 * Creality MifareClassic 1k tag processor. Two user-supplied 32-byte hex
 * keys: a "salt" used to derive the sector-1 key A, and an AES-256-ECB key
 * used to decrypt the payload when the chip is encrypted. Plaintext tags
 * (firmware ≥ some version) skip the AES step and parse ASCII directly.
 *
 * Per Q-U14b-4=A: encrypted tag with no enc key → silent log warn, null
 * payload (Vendor classification but no prefill). Same posture as Bambu's
 * "no salt configured".
 *
 * Ported from `OpenRFID/src/tag/creality/processor.py` (GPL-3.0, NOTICE).
 */
object CrealityProcessor : VendorTagProcessor {
    override val id = VendorId.Creality
    override val displayName = "Creality"

    // Enabled when at least the salt is set — plaintext tags work with no
    // enc key. The plan §1.3 + Q-U14b-4=A bind this.
    override fun isEnabled(settings: VendorSettings) = settings.crealitySalt.isNotBlank()

    override fun matchesChipType(techList: List<String>): Boolean =
        techList.contains(MifareClassic::class.java.name)

    override fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth? {
        if (settings.crealitySalt.isBlank()) return null
        val saltKey = parseHexKey(settings.crealitySalt) ?: return null
        if (saltKey.size != 32) {
            Log.w(TAG, "deriveAuthKeys: salt key wrong length (${saltKey.size}, expected 32)")
            return null
        }
        // OpenRFID names this `__hkdf_create_key` but it isn't HKDF (RFC
        // 5869). It's AES-ECB-encrypt(saltKey, uid * 4) with the first 6
        // bytes used as the sector-1 A key. Match upstream naming via the
        // helper but document the non-RFC nature.
        val uid4 = uid.copyOfRange(0, minOf(4, uid.size))
        if (uid4.size != 4) {
            Log.w(TAG, "deriveAuthKeys: UID < 4 bytes")
            return null
        }
        val plaintext = ByteArray(16)
        for (i in 0 until 4) uid4.copyInto(plaintext, destinationOffset = i * 4)

        val derived6 = try {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(saltKey, "AES"))
            cipher.doFinal(plaintext).copyOfRange(0, 6)
        } catch (e: Exception) {
            Log.w(TAG, "deriveAuthKeys: AES failed: $e")
            return null
        }

        val defaultKey = ByteArray(6) { 0xFF.toByte() }
        val keysA = MutableList(16) { defaultKey }
        keysA[1] = derived6
        val keysB = List(16) { defaultKey }
        return VendorAuth(keysA = keysA, keysB = keysB)
    }

    override fun parse(
        uid: ByteArray,
        raw: ByteArray,
        auth: VendorAuth?,
        settings: VendorSettings,
    ): OpenSpoolPayload? {
        if (raw.size < 64 + 48) {
            Log.d(TAG, "parse: raw too short (${raw.size})")
            return null
        }
        var dataSubset = raw.copyOfRange(64, 64 + 48)

        // Encrypted vs plaintext detection from upstream: byte 3 == 0x32 AND
        // byte 17 ∈ {0x30, 0x23} → plaintext.
        val test1 = dataSubset[3].toInt() and 0xFF
        val test2 = dataSubset[17].toInt() and 0xFF
        val isEncrypted = !(test1 == 0x32 && (test2 == 0x30 || test2 == 0x23))

        if (isEncrypted) {
            if (settings.crealityEncKey.isBlank()) {
                Log.w(TAG, "parse: tag is encrypted but no encryption key configured (Q-U14b-4=A)")
                return null
            }
            val encKey = parseHexKey(settings.crealityEncKey) ?: run {
                Log.w(TAG, "parse: encryption key not valid hex")
                return null
            }
            if (encKey.size != 32) {
                Log.w(TAG, "parse: encryption key wrong length (${encKey.size})")
                return null
            }
            dataSubset = try {
                val cipher = Cipher.getInstance("AES/ECB/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"))
                cipher.doFinal(dataSubset)
            } catch (e: Exception) {
                Log.w(TAG, "parse: AES decrypt failed: $e")
                return null
            }
        }

        val text = String(dataSubset, Charsets.US_ASCII)
        // 0..3 batch, 3..8 date (yy mm dd), 8..12 supplier, 12..17 material,
        // 17..24 color, 24..28 length, 28..34 serial.
        if (text.length < 34) return null
        val material = text.substring(12, 17)
        val data = CrealityTables.FILAMENT_CODE_TO_DATA[material] ?: run {
            Log.w(TAG, "parse: unknown material code '$material'")
            return null
        }

        val colorHex = runCatching {
            // Skip the leading prefix char ('0' or '#') at offset 17 and read 6 hex chars.
            text.substring(18, 24).uppercase()
        }.getOrElse { return null }

        val subtype = data.modifiers.firstOrNull() ?: "Basic"

        return OpenSpoolPayload(
            type = data.type,
            colorHex = colorHex,
            brand = "Creality",
            minTemp = data.hotendMin.toString(),
            maxTemp = data.hotendMax.toString(),
            bedMinTemp = data.bed.toString(),
            bedMaxTemp = data.bed.toString(),
            subtype = subtype,
            spoolId = null,
        )
    }

    private fun parseHexKey(hex: String): ByteArray? {
        val s = hex.trim()
        if (s.length % 2 != 0) return null
        return try {
            ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
