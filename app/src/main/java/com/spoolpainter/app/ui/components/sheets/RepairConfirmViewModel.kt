package com.spoolpainter.app.ui.components.sheets

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// FR-5.2 — move-on-bind confirmation sheet. Behaviour added in U6b.
data class RepairConfirmUiState(val placeholder: Boolean = true)

@HiltViewModel
class RepairConfirmViewModel @Inject constructor() : ViewModel()
