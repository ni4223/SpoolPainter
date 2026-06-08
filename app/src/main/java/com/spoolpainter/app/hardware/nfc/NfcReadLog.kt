package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ring buffer of the last N tag reads, kept in memory only. Used by the
 * "Share last NFC scan" Settings affordance so testers can paste a parsed
 * record into the feedback form when a vendor tag fails to prefill.
 *
 * No PII: UID is included (already exposed in the form when paired), but
 * raw bytes are limited to the first 256 to avoid leaking any cryptographic
 * material that lives deeper in the chip. Cleared on process death.
 */
@Singleton
class NfcReadLog @Inject constructor() {

    data class Entry(
        val timestampEpochMs: Long,
        val uidHex: String,
        val techList: List<String>,
        val rawHex: String,
        val rawByteCount: Int,
        val parseOutcome: String,
    )

    private val buffer = ArrayDeque<Entry>(MAX_ENTRIES)
    private val lock = Any()

    fun record(entry: Entry) {
        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry)
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun isEmpty(): Boolean = synchronized(lock) { buffer.isEmpty() }

    /** Render the buffer as a paste-friendly text block. */
    fun renderShareText(formUrl: String? = null): String {
        val entries = snapshot()
        val sb = StringBuilder()
        // Lead with form-pasting instructions so testers know exactly where
        // this text belongs.
        sb.append("Paste this in the SpoolPainter feedback form, in the\n")
        sb.append("\"NFC scan diagnostic\" field.")
        if (formUrl != null) {
            sb.append("\nForm: $formUrl")
        }
        sb.append("\n\n")
        sb.append("SpoolPainter ${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE}\n")
        if (entries.isEmpty()) {
            sb.append("(no NFC scans yet)\n")
            return sb.toString()
        }
        sb.append("Last ${entries.size} NFC scan(s)\n\n")
        entries.reversed().forEachIndexed { idx, e ->
            sb.append("--- scan ${idx + 1} (most recent first) ---\n")
            sb.append("ts: ${e.timestampEpochMs}\n")
            sb.append("uid: ${e.uidHex}\n")
            sb.append("techList: ${e.techList.joinToString()}\n")
            sb.append("rawBytes: ${e.rawByteCount}\n")
            sb.append("rawHex (first 256): ${e.rawHex}\n")
            sb.append("outcome: ${e.parseOutcome}\n\n")
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_ENTRIES = 5
        const val MAX_RAW_BYTES = 256
    }
}
