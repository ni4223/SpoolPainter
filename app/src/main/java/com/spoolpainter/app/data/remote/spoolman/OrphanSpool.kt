package com.spoolpainter.app.data.remote.spoolman

/**
 * Identifies the records freshly created during a single create-and-pair (or
 * vendor UID-only pair) flow. Passed to [SpoolmanRepository.chainDeleteOrphan]
 * so we only delete records *we* created in the same transaction; reused
 * vendors / filaments are left untouched.
 *
 * - [spoolId] is always non-null when the flow created a new spool.
 * - [filamentId] is non-null only when the filament was created fresh in the
 *   same flow (not when an existing filament was matched by case-insensitive
 *   material+colour+variant lookup).
 * - [vendorId] is non-null only when the vendor was created fresh in the
 *   same flow.
 */
data class OrphanSpool(
    val spoolId: Int,
    val filamentId: Int? = null,
    val vendorId: Int? = null,
)

/**
 * Outcome of a resolve-or-create helper. Distinguishes records returned from
 * the existing list vs records freshly POSTed in this call. Callers use the
 * `wasCreatedFresh` flag to decide whether the record is theirs to clean up.
 */
internal data class Resolved<T>(val value: T, val wasCreatedFresh: Boolean)

/**
 * Returned by [SpoolmanRepository.createSpoolForNewFilament]. Carries the
 * created spool plus whether the underlying filament and vendor were freshly
 * POSTed (true) or matched against an existing record (false). Used by the
 * caller to construct an [OrphanSpool] the chain-delete path can act on.
 */
data class NewSpoolBundle(
    val spool: com.spoolpainter.app.domain.models.SpoolmanSpool,
    val filamentWasFresh: Boolean,
    val vendorWasFresh: Boolean,
    val filamentId: Int?,
    val vendorId: Int?,
)
