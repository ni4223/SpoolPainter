package com.spoolpainter.app.ui.components.sheets

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// FR-4.9 — vendor-tag UID-only pair opt-in sheet. Behaviour added in U7.
data class VendorOptInUiState(val placeholder: Boolean = true)

@HiltViewModel
class VendorOptInViewModel @Inject constructor() : ViewModel()
