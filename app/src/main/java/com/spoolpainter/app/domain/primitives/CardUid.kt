package com.spoolpainter.app.domain.primitives

@JvmInline
value class CardUid(val hex: String) {
    override fun toString(): String = hex

    companion object {
        fun fromBytes(bytes: ByteArray): CardUid =
            CardUid(bytes.joinToString("") { "%02x".format(it) })
    }
}
