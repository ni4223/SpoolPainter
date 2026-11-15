package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.ExpanderOverrides
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.ui.screens.main.FormState
import com.spoolpainter.app.ui.screens.main.toExpanderOverrides

data class NewFilamentRequest(
    val name: String,
    val vendorName: String,
    val materialName: String,
    val colorHex: String,
    val variant: String?,
    val tempRanges: TempRanges,
    val expanderOverrides: ExpanderOverrides = ExpanderOverrides.EMPTY,
    /** SpoolmanDB import: external filament slug from
     *  /api/v1/external/filament/search. Non-null also derives the vendor
     *  external_id (= vendor name) inside the repository. Null = manual
     *  filament entry, no external linkage. */
    val externalFilamentId: String? = null,
    /** Explicit diameter (mm) — SpoolmanDB imports carry e.g. 2.85. Null
     *  keeps the DEFAULT_DIAMETER_MM (1.75) behavior. Treated like an
     *  explicit override (form overrides still win). */
    val diameterMm: Double? = null,
) {
    companion object {
        fun fromForm(
            form: FormState,
            name: String,
            vendorName: String,
            externalFilamentId: String? = null,
            diameterMm: Double? = null,
        ): NewFilamentRequest = NewFilamentRequest(
            name = name,
            vendorName = vendorName,
            materialName = form.material?.name ?: "",
            colorHex = form.colorHex ?: "",
            variant = form.variant,
            tempRanges = form.tempRanges,
            expanderOverrides = form.toExpanderOverrides(),
            externalFilamentId = externalFilamentId,
            diameterMm = diameterMm,
        )
    }
}
