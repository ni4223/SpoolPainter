package com.spoolpainter.app.domain.primitives

@JvmInline
value class CardUid(val hex: String) {
    override fun toString(): String = hex

    companion object {
        fun fromBytes(bytes: ByteArray): CardUid =
            CardUid(bytes.joinToString("") { "%02X".format(it) })

        private val HEX_PATTERN = Regex("^[0-9A-F]+$")

        fun normaliseHex(raw: String): String {
            val upper = raw.uppercase()
            require(HEX_PATTERN.matches(upper)) { "Not valid hex: $raw" }
            return upper
        }
    }
}
