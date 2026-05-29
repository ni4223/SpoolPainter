package com.spoolpainter.app.data.local

import com.spoolpainter.app.data.local.presets.BrandPresetSource
import com.spoolpainter.app.data.local.presets.MaterialPresetSource
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.Material
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Test stand-in for [MaterialBrandRepository]. Bypasses Spoolman + Hilt
 * wiring so each test can drive the merged materials/brands flow directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeMaterialBrandRepository(
    initialMaterials: List<Material> = MaterialPresetSource.PRESETS,
    initialBrands: List<String> = BrandPresetSource.PRESETS,
) : MaterialBrandRepository(
    materialPresets = MaterialPresetSource(),
    brandPresets = BrandPresetSource(),
    spoolman = mockk<SpoolmanRepository>(relaxed = true) {
        every { vendors } returns MutableStateFlow(emptyList())
        every { filaments } returns MutableStateFlow(emptyList())
    },
    scope = TestScope(UnconfinedTestDispatcher()),
) {
    private val _materials = MutableStateFlow(initialMaterials)
    override val materials: StateFlow<List<Material>> = _materials

    private val _brands = MutableStateFlow(initialBrands)
    override val brands: StateFlow<List<String>> = _brands

    fun setMaterials(list: List<Material>) {
        _materials.value = list
    }

    fun setBrands(list: List<String>) {
        _brands.value = list
    }
}
