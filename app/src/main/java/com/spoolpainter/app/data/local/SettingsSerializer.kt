package com.spoolpainter.app.data.local

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings()

    override suspend fun readFrom(input: InputStream): Settings {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            Json.decodeFromString(Settings.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Cannot read Settings", e)
        }
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) {
        output.write(
            Json.encodeToString(Settings.serializer(), t).encodeToByteArray()
        )
    }
}
