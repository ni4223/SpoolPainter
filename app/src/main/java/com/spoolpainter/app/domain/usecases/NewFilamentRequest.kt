package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.ui.screens.main.FormState

data class NewFilamentRequest(
    val name: String,
    val vendorName: String,
    val materialName: String,
    val colorHex: String,
    val variant: String?,
    val tempRanges: TempRanges,
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
        )
    }
}
