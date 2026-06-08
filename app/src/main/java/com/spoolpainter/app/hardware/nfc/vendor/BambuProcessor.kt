package com.spoolpainter.app.hardware.nfc.vendor

import android.nfc.tech.MifareClassic
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.hardware.nfc.bambuDeriveKeys
import com.spoolpainter.app.hardware.nfc.parseBambuTag

/**
 * Adapter wrapping the existing top-level Bambu functions in
 * `hardware/nfc/BambuFormat.kt`. No algorithm change — U14b's purpose is
 * the registry seam, not behaviour. Heavy testing stays in
 * [com.spoolpainter.app.hardware.nfc.BambuFormatTest]; the adapter is
 * smoke-checked by `BambuProcessorAdapterTest`.
 */
object BambuProcessor : VendorTagProcessor {
    override val id = VendorId.Bambu
    override val displayName = "Bambu Lab"

    override fun isEnabled(settings: VendorSettings) = settings.bambuSalt.isNotBlank()

    override fun matchesChipType(techList: List<String>): Boolean =
        techList.contains(MifareClassic::class.java.name)

    override fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth? {
        if (settings.bambuSalt.isBlank()) return null
        return runCatching {
            VendorAuth(keysA = bambuDeriveKeys(uid, settings.bambuSalt), keysB = null)
        }.getOrNull()
    }

    override fun parse(
        uid: ByteArray,
        raw: ByteArray,
        auth: VendorAuth?,
        settings: VendorSettings,
    ): OpenSpoolPayload? = parseBambuTag(raw)
}
