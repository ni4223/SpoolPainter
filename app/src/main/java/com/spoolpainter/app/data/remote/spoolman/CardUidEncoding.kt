package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.primitives.CardUid

object CardUidEncoding {

    internal const val PREFIX = "card_uid:"

    data class Decoded(
        val uids: List<CardUid>,
        val opaque: String,
    )

    fun decode(input: String): Decoded {
        if (input.isBlank()) return Decoded(emptyList(), "")

        val uids = mutableListOf<CardUid>()
        val opaqueEntries = mutableListOf<String>()

        for (raw in input.split(",")) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.length >= PREFIX.length &&
                trimmed.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)
            ) {
                val value = trimmed.substring(PREFIX.length)
                if (value.isNotEmpty() && value.length % 2 == 0 && value.all(::isHexChar)) {
                    uids.add(CardUid(value.lowercase()))
                } else {
                    // Malformed card_uid: entry — preserve original (untrimmed) verbatim.
                    opaqueEntries.add(raw)
                }
            } else {
                opaqueEntries.add(raw)
            }
        }

        return Decoded(uids, opaqueEntries.joinToString(","))
    }

    fun encode(uids: List<CardUid>, opaque: String = ""): String {
        val deduped = uids.distinct()
        val uidEntries = deduped.map { PREFIX + it.hex }
        val all = if (opaque.isNotEmpty()) uidEntries + opaque else uidEntries
        return all.joinToString(",")
    }

    private fun isHexChar(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
