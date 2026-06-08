package com.spoolpainter.app.hardware.nfc.vendor

import com.spoolpainter.app.domain.models.OpenSpoolPayload

/**
 * Stable identity of a vendor tag format. The chip-row composable uses this
 * for ordering + lit/dimmed status; the dispatcher uses it as the routing key
 * for [MifareClassicReader.tryReadRawCountedMulti] auth-count tiebreak.
 *
 * `OpenSpool` is included so the chip row can show "OpenSpool" even though
 * the OpenSpool NDEF format isn't a [VendorTagProcessor] — it's the format
 * SpoolPainter writes itself, handled by the standard NDEF classifier.
 */
enum class VendorId {
    OpenSpool,
    Bambu,
    Snapmaker,
    Qidi,
    Anycubic,
    Elegoo,
    Creality,
}

/**
 * Bag of user-supplied vendor settings sourced from [com.spoolpainter.app.data.local.SettingsRepository].
 * Empty strings mean "not configured".
 */
data class VendorSettings(
    val bambuSalt: String = "",
    val crealitySalt: String = "",
    val crealityEncKey: String = "",
)

/**
 * Auth material derived for one vendor's tag-read attempt. `keysB` is null
 * when the vendor only uses key A (Bambu) or no auth at all (Ultralight).
 */
data class VendorAuth(
    val keysA: List<ByteArray>,
    val keysB: List<ByteArray>? = null,
)

/**
 * Per-vendor tag processor. Six total: Bambu, Snapmaker, Qidi, Anycubic,
 * Elegoo, Creality (OpenSpool is handled by the NDEF classifier). The
 * dispatcher in `TagFormatParser.parseVendor(tag, settings)` filters by
 * [matchesChipType] + [isEnabled], reads raw bytes once per chip family,
 * then calls [parse] on each candidate in registry order until one returns
 * non-null.
 */
interface VendorTagProcessor {
    val id: VendorId

    /** Display name for the chip row + telemetry. */
    val displayName: String

    /** True when this processor's prerequisites (keys, settings) are met. */
    fun isEnabled(settings: VendorSettings): Boolean

    /** Cheap pre-check: does the tag's chip type even match? */
    fun matchesChipType(techList: List<String>): Boolean

    /**
     * Derive auth material for this UID, if applicable. Null means "no auth
     * needed" (Ultralight) OR "keys not configured / derivation failed".
     * Distinguishing those two is the dispatcher's job: Ultralight processors
     * still get [parse] called even when this returns null, MifareClassic
     * processors get skipped.
     */
    fun deriveAuthKeys(uid: ByteArray, settings: VendorSettings): VendorAuth?

    /**
     * Parse the raw bytes after the chip-family read. `auth` is what the
     * dispatcher passed in (used by Snapmaker for its RSA signature check
     * which references the keys; ignored by other processors).
     */
    fun parse(uid: ByteArray, raw: ByteArray, auth: VendorAuth?, settings: VendorSettings): OpenSpoolPayload?
}
