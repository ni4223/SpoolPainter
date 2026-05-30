package com.spoolpainter.app.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    // Legacy `sortOrder` / `spoolSortOrder` / `filamentSortOrder` keys from
    // pre-U9-Δ-1 payloads are silently dropped on read. `coerceInputValues`
    // turns out-of-range enum values (e.g. legacy `themeOverride: "System"`,
    // dropped in U9-Δ-1) into the field's default rather than throwing.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(input: InputStream): Settings {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(Settings.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot read Settings", e)
        }
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(
            json.encodeToString(Settings.serializer(), t).encodeToByteArray()
        )
    }
}
