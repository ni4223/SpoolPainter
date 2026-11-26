package com.spoolpainter.app.data.remote.spoolman

/**
 * Outcome of a resolve-or-create helper. Distinguishes records returned from
 * the existing list vs records freshly POSTed in this call. Callers use the
 * `wasCreatedFresh` flag to decide how to treat the record.
 */
internal data class Resolved<T>(val value: T, val wasCreatedFresh: Boolean)

/**
 * Returned by [SpoolmanRepository.createSpoolForNewFilamentBundle]. Carries the
 * created spool plus whether the underlying filament and vendor were freshly
 * POSTed (true) or matched against an existing record (false).
 */
data class NewSpoolBundle(
    val spool: com.spoolpainter.app.domain.models.SpoolmanSpool,
    val filamentWasFresh: Boolean,
    val vendorWasFresh: Boolean,
    val filamentId: Int?,
    val vendorId: Int?,
    /** The background `article_number` fill started for a matched filament
     *  that had none; null when no fill was needed. The spool is already
     *  created: await this only to report the fill. */
    val articleNumberFill: kotlinx.coroutines.Deferred<
        SpoolmanOutcome<com.spoolpainter.app.domain.models.SpoolmanFilament>,
    >? = null,
)
