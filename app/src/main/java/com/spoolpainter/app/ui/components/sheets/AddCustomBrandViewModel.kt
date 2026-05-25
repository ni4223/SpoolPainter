package com.spoolpainter.app.ui.components.sheets

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// FR-8.5 — add-custom-brand sheet. Behaviour added in U8.
data class AddCustomBrandUiState(val placeholder: Boolean = true)

@HiltViewModel
class AddCustomBrandViewModel @Inject constructor() : ViewModel()
