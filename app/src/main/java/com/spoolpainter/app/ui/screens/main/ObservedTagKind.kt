package com.spoolpainter.app.ui.screens.main

/**
 * Coarse classification of the most recently observed tag, surfaced to the UI
 * so the chip + helper text + Save-button copy can react. Derived from
 * `nfc.lastSeenTag.classification`.
 */
sealed interface ObservedTagKind {
    data object None : ObservedTagKind
    data object Blank : ObservedTagKind
    data object OpenSpool : ObservedTagKind
    /** [sku]: product code decoded from the tag on an explicit Read (see
     *  OpenSpoolPayload.sku); null when nothing was decoded. */
    data class Vendor(val sku: String? = null) : ObservedTagKind
}
