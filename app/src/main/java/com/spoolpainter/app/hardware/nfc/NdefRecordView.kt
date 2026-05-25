package com.spoolpainter.app.hardware.nfc

data class NdefRecordView(
    val tnf: Short,
    val type: ByteArray,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NdefRecordView) return false
        return tnf == other.tnf &&
            type.contentEquals(other.type) &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = tnf.toInt()
        result = 31 * result + type.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        // android.nfc.NdefRecord.TNF_MIME_MEDIA — duplicated as a literal so JVM unit
        // tests can reference NdefRecordView without loading the Android stub jar.
        const val TNF_MIME_MEDIA: Short = 0x02
    }
}
