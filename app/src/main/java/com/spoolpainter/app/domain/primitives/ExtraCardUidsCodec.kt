package com.spoolpainter.app.domain.primitives

import android.util.Log
import com.google.gson.Gson

object ExtraCardUidsCodec {

    private val gson = Gson()

    fun encode(uids: List<CardUid>): String =
        gson.toJson(uids.joinToString(",") { it.hex })

    fun decode(value: String): List<CardUid> {
        if (value.isEmpty()) return emptyList()
        val unwrapped = if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            value.substring(1, value.length - 1)
        } else {
            value
        }
        if (unwrapped.isEmpty()) return emptyList()
        return unwrapped.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { entry ->
                runCatching { CardUid(CardUid.normaliseHex(entry)) }.getOrElse {
                    Log.w("ExtraCardUidsCodec", "skipped invalid hex: $entry")
                    null
                }
            }
    }
}
