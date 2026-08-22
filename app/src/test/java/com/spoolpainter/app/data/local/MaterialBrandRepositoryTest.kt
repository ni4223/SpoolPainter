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

    @Test fun `materials presets only — 10 entries with Other pinned top`() = runTest {
        val list = build().materials.first()
        assertEquals(10, list.size)
        // F-2 (v2.0.3): "Other" is pinned at the top as actionable
        // affordance; the rest sorts case-insensitive alphabetical.
        assertEquals("Other", list.first().name)
        val rest = list.drop(1)
        assertEquals(rest.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }), rest)
    }

    @Test fun `materials union spoolman filaments — case-insensitive dedup`() = runTest {
        val list = build(spoolmanFilamentMaterials = listOf("pla", "PA-CF", "PLA")).materials.first()
        // 10 presets + PA-CF (PLA dup eliminated)
        assertEquals(11, list.size)
        // Preset PLA wins (presets enumerate first; merge dedups before sort).
        val pla = list.first { it.name.equals("PLA", ignoreCase = true) }
        assertEquals("PLA", pla.name)
        assertEquals(190, pla.defaultMinTemp)
    }

    @Test fun `materials sorted alphabetically including spoolman-derived`() = runTest {
        // F-2 (v2.0.3): the merge alphabetises across presets + Spoolman-
        // derived names, so a custom material from Spoolman lands in its
        // case-insensitive slot.
        val list = build(spoolmanFilamentMaterials = listOf("PA-CF", "carbon-fiber"))
            .materials.first()
        val names = list.map { it.name }
        // "Other" pinned top, then alphabetised.
        assertEquals("Other", names.first())
        // ABS < PA-CF < PETG < PLA etc. — case-insensitive.
        val rest = names.drop(1)
        assertEquals(rest.sortedWith(String.CASE_INSENSITIVE_ORDER), rest)
        assertTrue(rest.contains("PA-CF"))
        assertTrue(rest.contains("carbon-fiber"))
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

    // F-2 (v2.0.3) — alphabetise + Other-pin-top for brands.

    @Test fun `brands presets only — Other pinned top, rest alphabetical`() = runTest {
        val list = build().brands.first()
        assertEquals("Other", list.first())
        val rest = list.drop(1)
        assertEquals(rest.sortedWith(String.CASE_INSENSITIVE_ORDER), rest)
    }

    @Test fun `brands union vendors — alphabetised case-insensitive`() = runTest {
        val list = build(spoolmanVendors = listOf("3DJake", "Anycubic", "yousu"))
            .brands.first()
        // "Other" pinned top.
        assertEquals("Other", list.first())
        val rest = list.drop(1)
        // Case-insensitive ordering — "yousu" lands at the end (Y), not in
        // a separate lowercase block.
        assertEquals(rest.sortedWith(String.CASE_INSENSITIVE_ORDER), rest)
        assertTrue(rest.contains("3DJake"))
        assertTrue(rest.contains("Anycubic"))
        assertTrue(rest.contains("yousu"))
    }

    // --- UI-62: two visually identical brand rows in the dropdown ---

    /**
     * The dedupe key was `lowercase()` with no trim, so a Spoolman vendor
     * carrying stray whitespace ("TECBEARS ") did not collide with the preset
     * ("TECBEARS") and both rows rendered — indistinguishable on screen.
     */
    @Test fun `brands dedupe vendors that differ from a preset only by whitespace`() = runTest {
        val list = build(spoolmanVendors = listOf("TECBEARS ", "  TECBEARS")).brands.first()
        assertEquals(1, list.count { it.equals("TECBEARS", ignoreCase = true) })
        assertTrue(list.contains("TECBEARS"))
    }

    @Test fun `brands never render a value with surrounding whitespace`() = runTest {
        val list = build(spoolmanVendors = listOf("  Fancy Filament  ")).brands.first()
        assertTrue(list.contains("Fancy Filament"))
        assertFalse(list.any { it != it.trim() })
    }

    @Test fun `brands drop blank and whitespace-only vendor names`() = runTest {
        val list = build(spoolmanVendors = listOf("", "   ")).brands.first()
        assertEquals(BrandPresetSource.PRESETS.size, list.size)
    }

    /**
     * Not every lookalike pair is a bug: "Techbear" and "TECBEARS" are different
     * words and must both survive. Only whitespace/case variants of the *same*
     * string collapse.
     */
    @Test fun `brands keep a genuinely different spelling as its own entry`() = runTest {
        val list = build(spoolmanVendors = listOf("Techbear")).brands.first()
        assertTrue(list.contains("TECBEARS"))
        assertTrue(list.contains("Techbear"))
    }
}
