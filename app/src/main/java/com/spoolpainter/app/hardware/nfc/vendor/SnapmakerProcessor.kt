package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.tech.MifareClassic
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.hardware.nfc.SNAPMAKER_KEY_SALT
import com.spoolpainter.app.hardware.nfc.parseSnapmakerTag
import com.spoolpainter.app.hardware.nfc.snapmakerDeriveKeys

/**
 * Adapter wrapping the existing top-level Snapmaker functions in
 * `hardware/nfc/SnapmakerFormat.kt`. The salt is hardcoded (community
 * constant), so [isEnabled] is always true. No algorithm change.
 */
object SnapmakerProcessor : VendorTagProcessor {
    override val id = VendorId.Snapmaker

    // Q-U14b-8: drop "U1" from display name to match OpenRFID upstream
    // naming. The chip row + future hint copy share this string.
    override val displayName = "Snapmaker"

    // Salt is a baked-in community constant, so always available.
    override fun isEnabled(settings: VendorSettings) = true

    override fun matchesChipType(techList: List<String>): Boolean =
        techList.contains(MifareClassic::class.java.name)

    override fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth? {
        val uid4 = uid.copyOfRange(0, minOf(4, uid.size))
        return runCatching {
            val (a, b) = snapmakerDeriveKeys(uid4, SNAPMAKER_KEY_SALT)
            VendorAuth(keysA = a, keysB = b)
        }.getOrNull()
    }

    override fun parse(
        uid: ByteArray,
        raw: ByteArray,
        auth: VendorAuth?,
        settings: VendorSettings,
    ): OpenSpoolPayload? = parseSnapmakerTag(raw, keysA = auth?.keysA, keysB = auth?.keysB)
}
