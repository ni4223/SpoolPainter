package com.spoolpainter.app.data.local

import com.spoolpainter.app.data.local.presets.BrandPresetSource
import com.spoolpainter.app.data.local.presets.MaterialPresetSource
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanVendor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MaterialBrandRepositoryTest {

    private fun build(
        spoolmanVendors: List<String> = emptyList(),
        spoolmanFilamentMaterials: List<String> = emptyList(),
    ): MaterialBrandRepository {
        val spoolman = mockk<SpoolmanRepository>(relaxed = true) {
            every { vendors } returns MutableStateFlow(
                spoolmanVendors.mapIndexed { i, name -> SpoolmanVendor(id = i + 1, name = name) },
            )
            every { filaments } returns MutableStateFlow(
                spoolmanFilamentMaterials.mapIndexed { i, mat ->
                    SpoolmanFilament(id = i + 1, material = mat)
                },
            )
        }
        return MaterialBrandRepository(
            materialPresets = MaterialPresetSource(),
            brandPresets = BrandPresetSource(),
            spoolman = spoolman,
            scope = TestScope(UnconfinedTestDispatcher()),
        )
    }

    @Test fun `materials presets only — 10 entries (incl Other)`() = runTest {
        val list = build().materials.first()
        assertEquals(10, list.size)
        assertEquals("PLA", list.first().name)
        assertTrue(list.any { it.name.equals("Other", ignoreCase = true) })
    }

    @Test fun `materials union spoolman filaments — case-insensitive dedup`() = runTest {
        val list = build(spoolmanFilamentMaterials = listOf("pla", "PA-CF", "PLA")).materials.first()
        // 10 presets + PA-CF (PLA dup eliminated)
        assertEquals(11, list.size)
        // Preset PLA wins (presets enumerate first).
        val pla = list.first { it.name.equals("PLA", ignoreCase = true) }
        assertEquals("PLA", pla.name)
        assertEquals(190, pla.defaultMinTemp)
    }

    @Test fun `brands union spoolman vendors — preset spelling wins on collision`() = runTest {
        val list = build(spoolmanVendors = listOf("bambu lab", "3DJake")).brands.first()
        assertTrue(list.contains("3DJake"))
        // "Bambu Lab" preset wins over "bambu lab" Spoolman vendor.
        val bambu = list.filter { it.equals("Bambu Lab", ignoreCase = true) }
        assertEquals(1, bambu.size)
        assertEquals("Bambu Lab", bambu.single())
    }

    @Test fun `materials never duplicate (distinctBy invariant)`() = runTest {
        val list = build(
            spoolmanFilamentMaterials = listOf("pla", "PLA", "Pla"),
        ).materials.first()
        assertEquals(list.size, list.distinctBy { it.name.uppercase() }.size)
    }

    @Test fun `brands never duplicate (distinctBy invariant)`() = runTest {
        val list = build(
            spoolmanVendors = listOf("Bambu Lab", "bambu lab", "BAMBU LAB"),
        ).brands.first()
        assertEquals(list.size, list.distinctBy { it.lowercase() }.size)
    }

    @Test fun `blank or null spoolman material strings are filtered out`() = runTest {
        val list = build(spoolmanFilamentMaterials = listOf("", "  ")).materials.first()
        assertEquals(MaterialPresetSource.PRESETS.size, list.size)
        assertFalse(list.any { it.name.isBlank() })
    }
}
