package com.spoolpainter.app.ui.screens.main

/**
 * Derived runtime mode that controls Save & Write dispatch.
 *
 * Auto-derived from `settings.url` — there is no user-facing toggle
 * (per [[Q-U7-5]] reframe). Connectivity state does NOT influence the mode:
 * a transient unreachable Spoolman should not silently flip the app into
 * raw-write — the user already configured a URL, that's the intent.
 */
sealed interface WriteMode {
    /** Spoolman URL configured. Standard create-and-pair / vendor-uid-only flows. */
    data object Spoolman : WriteMode

    /** Spoolman URL not configured. Raw-write only (writes payload to tag,
     *  no Spoolman calls). UI is otherwise identical to Spoolman mode. */
    data object RawNoUrl : WriteMode
}
