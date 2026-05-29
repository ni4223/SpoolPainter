package com.spoolpainter.app.data.local.presets

import com.spoolpainter.app.domain.models.Material
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialPresetSource @Inject constructor() {
    val materials: List<Material> = PRESETS

    fun getMaterial(name: String): Material? =
        materials.find { it.name.equals(name, ignoreCase = true) }

    companion object {
        const val DEFAULT_DIAMETER_MM = 1.75f
        const val DEFAULT_FULL_SPOOL_WEIGHT_G = 1000f
        const val PLA_DENSITY_FALLBACK = 1.24f

        // "Other" is kept as the last entry — selecting it reveals an inline
        // typed-name field on the form (v1 behaviour). On Save & Write, the
        // typed name is auto-persisted to the user store so it shows up
        // directly in the dropdown on the next session.
        val PRESETS: List<Material> = listOf(
            Material("PLA",   190, 220,  40,  65, density = 1.24f),
            Material("ABS",   220, 260,  80, 110, density = 1.04f),
            Material("PETG",  220, 250,  60,  80, density = 1.27f),
            Material("TPU",   210, 230,  40,  60, density = 1.20f),
            Material("ASA",   240, 270, 100, 110, density = 1.07f),
            Material("PC",    270, 310,  80, 110, density = 1.20f),
            Material("Nylon", 240, 280,  60, 100, density = 1.14f),
            Material("PVA",   180, 210,  40,  60, density = 1.19f),
            Material("HIPS",  220, 260, 100, 110, density = 1.04f),
            Material("Other", 200, 220,  50,  70, density = null),
        )

        fun lookup(name: String): Material? =
            PRESETS.find { it.name.equals(name, ignoreCase = true) }

        fun densityFor(materialName: String): Float =
            lookup(materialName)?.density ?: PLA_DENSITY_FALLBACK
    }
}
