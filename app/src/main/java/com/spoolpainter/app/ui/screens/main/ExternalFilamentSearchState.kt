package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.domain.models.ExternalFilament

/**
 * SpoolmanDB (external filament database, server ≥ 0.26) search section
 * state for the Filament picker. [Unsupported] is forced by the ViewModel
 * gate whenever `serverInfo.supportsExternalDbSearch()` is false — it wins
 * over every other state so stale results can never surface.
 */
sealed interface ExternalSearchUiState {
    /** Query blank/too short — section hidden (blank) or muted hint (1 char). */
    data object Idle : ExternalSearchUiState

    data object Searching : ExternalSearchUiState

    data class Results(val items: List<ExternalFilament>, val totalCount: Int) : ExternalSearchUiState {
        val hasMore: Boolean get() = items.size < totalCount
    }

    data class Error(val message: String) : ExternalSearchUiState

    /** Server < 0.26 (or version unknown) — picker shows a hint row. */
    data object Unsupported : ExternalSearchUiState
}
