package com.spoolpainter.app.hardware.nfc

import java.io.File

/**
 * Loader + minimal YAML reader for the OpenRFID vendor tag fixtures under
 * `src/test/resources/vendor-fixtures/`. Each `.bin` is a raw chip dump paired
 * with a `.yml` of the expected decoded fields. Sourced from
 * github.com/suchmememanyskill/OpenRFID (GPL-3.0) at commit
 * ddd1609e9abe9cd37c4b8fa1a0e4307b976d5fd4 — see NOTICE.
 *
 * The `.yml` files are flat scalar key/value plus a one-element `colors` list,
 * so a 30-line parser covers them without pulling SnakeYAML onto the test
 * classpath.
 */
object VendorFixtureSupport {

    /** Expected decoded output, projected from an OpenRFID `.yml` sidecar. */
    data class Expected(
        val sourceProcessor: String,
        val manufacturer: String,
        val type: String,
        val modifiers: List<String>,
        /** Low 24 bits of the first ARGB color, as an upper-hex RRGGBB string. */
        val colorRgbHex: String,
        val hotendMinC: Int,
        val hotendMaxC: Int,
        val bedTempC: Int,
    )

    data class Fixture(val name: String, val vendorDir: String, val bin: ByteArray, val expected: Expected) {
        override fun toString() = "$vendorDir/$name"
    }

    private fun fixturesRoot(): File {
        val url = VendorFixtureSupport::class.java.classLoader!!
            .getResource("vendor-fixtures")
            ?: error("vendor-fixtures resource dir not found on test classpath")
        return File(url.toURI())
    }

    /** All fixtures under the given vendor subdirectory (e.g. "Elegoo"). */
    fun load(vendorDir: String): List<Fixture> {
        val dir = File(fixturesRoot(), vendorDir)
        require(dir.isDirectory) { "no fixture dir: $dir" }
        return dir.listFiles { f -> f.extension == "bin" }!!
            .sortedBy { it.name }
            .map { binFile ->
                val yml = File(binFile.parentFile, binFile.nameWithoutExtension + ".yml")
                require(yml.exists()) { "missing sidecar yml for ${binFile.name}" }
                Fixture(
                    name = binFile.nameWithoutExtension,
                    vendorDir = vendorDir,
                    bin = binFile.readBytes(),
                    expected = parseExpected(yml.readText()),
                )
            }
    }

    private fun parseExpected(yml: String): Expected {
        val scalars = mutableMapOf<String, String>()
        val colors = mutableListOf<String>()
        val modifiers = mutableListOf<String>()
        var listKey: String? = null
        for (rawLine in yml.lines()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            val listItem = Regex("^\\s+-\\s+(.*)$").find(line)
            if (listItem != null && listKey != null) {
                val v = listItem.groupValues[1].trim().trim('"')
                when (listKey) {
                    "colors" -> colors += v
                    "modifiers" -> modifiers += v
                }
                continue
            }
            val kv = Regex("^([A-Za-z0-9_]+):\\s*(.*)$").find(line) ?: continue
            val key = kv.groupValues[1]
            val value = kv.groupValues[2].trim()
            if (value.isEmpty()) {
                listKey = key // following indented "- x" lines belong to this key
            } else {
                listKey = null
                scalars[key] = value.trim('"')
            }
        }
        val argb = colors.firstOrNull()?.removePrefix("0x")?.removePrefix("0X")
            ?: error("yml has no colors entry")
        val rgb = (argb.toLong(16) and 0xFFFFFF).let { "%06X".format(it) }
        return Expected(
            sourceProcessor = scalars.getValue("source_processor"),
            manufacturer = scalars.getValue("manufacturer"),
            type = scalars.getValue("type"),
            modifiers = modifiers.toList(),
            colorRgbHex = rgb,
            hotendMinC = scalars.getValue("hotend_min_temp_c").toInt(),
            hotendMaxC = scalars.getValue("hotend_max_temp_c").toInt(),
            bedTempC = scalars.getValue("bed_temp_c").toInt(),
        )
    }
}
