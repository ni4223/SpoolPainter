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
) {
    companion object {
        fun fromForm(
            form: FormState,
            name: String,
            vendorName: String,
        ): NewFilamentRequest = NewFilamentRequest(
            name = name,
            vendorName = vendorName,
            materialName = form.material?.name ?: "",
            colorHex = form.colorHex ?: "",
            variant = form.variant,
            tempRanges = form.tempRanges,
            expanderOverrides = form.toExpanderOverrides(),
        )
    }
}
