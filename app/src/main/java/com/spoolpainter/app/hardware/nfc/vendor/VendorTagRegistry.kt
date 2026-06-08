package com.spoolpainter.app.hardware.nfc.vendor

/**
 * Static list of vendor tag processors. Insertion order is the dispatch
 * priority: when two processors match the same chip family, the first one
 * to return a non-null parse wins.
 *
 * Order rationale:
 * - Bambu / Snapmaker first — pre-existing behaviour preserved.
 * - QIDI / Anycubic / Elegoo / Creality next — added in U14b.
 *
 * Ultralight processors (Anycubic, Elegoo) and Classic processors (Bambu,
 * Snapmaker, QIDI, Creality) are filtered into separate dispatch branches
 * by [TagFormatParser.parseVendor], so cross-family ordering doesn't matter.
 *
 * The list grows milestone by milestone in U14b so each new vendor lands
 * with its tests + dispatch wired up in one diff.
 */
object VendorTagRegistry {
    val processors: List<VendorTagProcessor> = listOf(
        BambuProcessor,
        SnapmakerProcessor,
        QidiProcessor,
        AnycubicProcessor,
        ElegooProcessor,
        CrealityProcessor,
    )
}
