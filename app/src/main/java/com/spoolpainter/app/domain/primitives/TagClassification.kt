package com.spoolpainter.app.domain.primitives

import com.spoolpainter.app.domain.models.OpenSpoolPayload

sealed interface TagClassification {
    data object Blank : TagClassification
    data class OpenSpool(val payload: OpenSpoolPayload) : TagClassification
    data class Vendor(
        val reason: String,
        val parsedHint: OpenSpoolPayload? = null,
    ) : TagClassification
}
