package com.spoolpainter.app.data.local

import com.spoolpainter.app.data.local.presets.BrandPresetSource
import com.spoolpainter.app.data.local.presets.MaterialPresetSource
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.di.AppScope
import com.spoolpainter.app.domain.models.Material
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges hardcoded presets with whatever Spoolman knows about. Spoolman is
 * the authoritative store for user-added entries: a typed material/brand
 * lands in Spoolman on the next Save & Write (vendor row + filament row),
 * and surfaces here on the next refresh — no separate local persistence
 * needed.
 *
 * - Brands  = spoolman.vendors (verbatim) ∪ presets the user has no vendor for
 * - Materials = presets ∪ distinct material strings on spoolman.filaments
 *
 * Invariants:
 *   materials.distinctBy { it.name.uppercase() }.size == materials.size
 *   brands.distinct().size == brands.size
 *   brands.none { it != it.trim() }
 *
 * Note the brand invariant is `distinct()`, not `distinctBy { lowercase() }`:
 * two Spoolman vendors differing only by case are two records and both belong
 * in the list. See [mergeBrands].
 */
@Singleton
open class MaterialBrandRepository @Inject constructor(
    private val materialPresets: MaterialPresetSource,
    private val brandPresets: BrandPresetSource,
    private val spoolman: SpoolmanRepository,
    @AppScope private val scope: CoroutineScope,
) {
    open val materials: StateFlow<List<Material>> = combine(
        spoolman.filaments,
        spoolman.vendors, // co-trigger re-emit on URL change / refresh
    ) { filaments, _ ->
        mergeMaterials(materialPresets.materials, filaments.mapNotNull { it.material })
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = mergeMaterials(materialPresets.materials, emptyList()),
    )

    open val brands: StateFlow<List<String>> = combine(
        spoolman.vendors,
        spoolman.filaments, // co-trigger
    ) { vendors, _ ->
        mergeBrands(brandPresets.brands, vendors.map { it.name })
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = mergeBrands(brandPresets.brands, emptyList()),
    )

    companion object {
        // F-2 (v2.0.3): alphabetise the merged list case-insensitively, with
        // "Other" pinned at the top as an actionable affordance (more
        // discoverable than buried at the bottom of an A-Z scroll).
        internal fun mergeMaterials(
            presets: List<Material>,
            spoolmanMaterialNames: List<String>,
        ): List<Material> {
            val seenUpper = presets.mapTo(mutableSetOf()) { it.name.uppercase() }
            val derived = spoolmanMaterialNames
                .filter { it.isNotBlank() }
                .filter { seenUpper.add(it.uppercase()) }
                .map { name -> Material(name, 200, 220, 50, 70, density = null) }
            val (other, rest) = (presets + derived).partition { it.name == "Other" }
            return other + rest.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        /**
         * UI-63: **the user's own Spoolman vendors win over preset spellings.**
         * Previously presets were listed first and the whole list deduped by
         * `lowercase()`, so a vendor the user had created as "Tecbears" was
         * dropped in favour of the preset "TECBEARS" — their own spelling
         * vanished from the dropdown and could not be picked at all.
         *
         * Two different dedupe keys, deliberately:
         * - **Server vendors: exact (trimmed) string.** Case variants are kept
         *   as separate rows, because they are separate vendor *records* with
         *   separate ids, and `resolveOrCreateVendor` picks among them with an
         *   arbitrary `firstOrNull` — collapsing them would silently decide
         *   which record a filament attaches to. Verified 2026-08-27 that
         *   Spoolman's `vendor.name` is a bare `String(64)` with no unique
         *   constraint, index or case-insensitive collation, so a user really
         *   can hold both "Tecbears" and "TECBEARS". Rows that are identical
         *   once trimmed still collapse (UI-62) — those render the same and
         *   picking between them would be a coin flip with no visible cue.
         * - **Presets: case-insensitive against the server set.** A preset is
         *   only offered when the user has no vendor for that brand, so it
         *   never competes with a spelling they chose.
         */
        internal fun mergeBrands(
            presets: List<String>,
            vendors: List<String>,
        ): List<String> {
            // UI-62: trim before both the dedupe key AND the kept value, so a
            // vendor stored as "TECBEARS " cannot render as a second row that
            // looks identical to "TECBEARS".
            val serverNames = vendors
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val serverFolded = serverNames.mapTo(mutableSetOf()) { it.lowercase() }
            val keptPresets = presets
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .filter { it.lowercase() !in serverFolded }
            val (other, rest) = (serverNames + keptPresets).partition { it == "Other" }
            // CASE_INSENSITIVE_ORDER compares case variants equal, and sortedWith
            // is stable, so "Tecbears" / "TECBEARS" stay adjacent in server order
            // rather than being split across the list.
            return other + rest.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }
}
