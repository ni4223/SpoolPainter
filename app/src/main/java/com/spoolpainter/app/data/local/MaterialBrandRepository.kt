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
 * - Brands  = presets ∪ spoolman.vendors (dedup case-insensitive, presets first)
 * - Materials = presets ∪ distinct material strings on spoolman.filaments
 *
 * Invariants:
 *   materials.distinctBy { it.name.uppercase() }.size == materials.size
 *   brands.distinctBy   { it.lowercase()       }.size == brands.size
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
        internal fun mergeMaterials(
            presets: List<Material>,
            spoolmanMaterialNames: List<String>,
        ): List<Material> {
            val seenUpper = presets.mapTo(mutableSetOf()) { it.name.uppercase() }
            val derived = spoolmanMaterialNames
                .filter { it.isNotBlank() }
                .filter { seenUpper.add(it.uppercase()) }
                .map { name -> Material(name, 200, 220, 50, 70, density = null) }
            return presets + derived
        }

        internal fun mergeBrands(
            presets: List<String>,
            vendors: List<String>,
        ): List<String> = (presets + vendors).distinctBy { it.lowercase() }
    }
}
