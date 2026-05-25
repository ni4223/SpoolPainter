package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.primitives.CardUid

data class NewSpoolRequest(
    val vendorName: String,
    val materialName: String,
    val colorHex: String,
    val variant: String?,
    val tempRanges: TempRanges,
    val cardUid: CardUid,
)

data class TempRanges(
    val extruderMin: Int?,
    val extruderMax: Int?,
    val bedMin: Int?,
    val bedMax: Int?,
)
