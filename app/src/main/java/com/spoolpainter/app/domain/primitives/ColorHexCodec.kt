package com.spoolpainter.app.domain.primitives

object ColorHexCodec {
    fun canonicalise(raw: String?): String? =
        raw?.removePrefix("#")
            ?.let { if (it.length > 6) it.takeLast(6) else it }
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
}
