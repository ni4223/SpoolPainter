package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import android.util.Log
import androidx.activity.ComponentActivity
import com.spoolpainter.app.BuildConfig
import com.spoolpainter.app.di.AppScope
import com.spoolpainter.app.di.IoDispatcher
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.OpenSpoolDecodeResult
import com.spoolpainter.app.domain.primitives.OpenSpoolPayloadCodec
import com.spoolpainter.app.domain.primitives.TagClassification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton
import com.spoolpainter.app.data.local.SettingsRepository

@Singleton
open class NfcRepository internal constructor(
    private val wrapper: NfcAdapterWrapper,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    private val settingsRepository: SettingsRepository,
    private val ttlMs: Long,
) {

    @Inject
    constructor(
        wrapper: NfcAdapterWrapper,
        @AppScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        clock: Clock,
        settingsRepository: SettingsRepository,
    ) : this(wrapper, scope, ioDispatcher, clock, settingsRepository, TTL_MS_DEFAULT)

    private val _state = MutableStateFlow<NfcResult>(NfcResult.Idle)
    open val state: StateFlow<NfcResult> = _state.asStateFlow()

    private val _lastSeenTag = MutableStateFlow<TagBuffer?>(null)
    open val lastSeenTag: StateFlow<TagBuffer?> = _lastSeenTag.asStateFlow()

    private val mutex = Mutex()
    private var armedIntent: NfcIntent? = null
    private var attached: ComponentActivity? = null

    fun attach(activity: ComponentActivity) {
        if (attached === activity) return
        attached?.let { wrapper.disableForegroundDispatch(it) }
        attached = activity
        wrapper.enableForegroundDispatch(activity)
    }

    fun detach() {
        val active = attached ?: return
        wrapper.disableForegroundDispatch(active)
        attached = null
        // Don't error-out an in-flight Write/Verify here. Android may cycle
        // through onPause → onResume when an NFC intent is dispatched (esp.
        // Android 14+ singleTop), and we'd surface a spurious "paused
        // mid-write" error every successful write. The viewmodel layer guards
        // long-running flows with withTimeoutOrNull, which catches a real
        // user-driven pause.
    }

    open suspend fun arm(intent: NfcIntent) {
        if (!wrapper.isAvailable()) {
            mutex.withLock {
                armedIntent = null
                _state.value = NfcResult.Error("NFC not available")
            }
            return
        }
        mutex.withLock {
            armedIntent = intent
            _state.value = when (intent) {
                is NfcIntent.Read -> NfcResult.Reading
                is NfcIntent.Write -> NfcResult.Writing
                is NfcIntent.Verify -> NfcResult.Verifying
            }
        }
    }

    open suspend fun consumeLastSeen(intent: NfcIntent): NfcResult? {
        if (intent !is NfcIntent.Read) return null
        val buffer = _lastSeenTag.value ?: return null
        if (clock.now().toEpochMilliseconds() - buffer.capturedAtEpochMs > ttlMs) return null
        return mutex.withLock {
            // Accept Idle and terminal states (Success / Error). Reject only in-flight intents
            // (Reading / Writing / Verifying) where consuming the buffer would race with the
            // armed handler. Terminal states exist precisely to mean "nothing in flight".
            when (_state.value) {
                is NfcResult.Reading,
                is NfcResult.Writing,
                is NfcResult.Verifying -> return@withLock null
                else -> Unit
            }
            val result = NfcResult.Success(buffer.uid, buffer.classification)
            _lastSeenTag.value = null
            _state.value = result
            result
        }
    }

    open suspend fun disarm() {
        mutex.withLock {
            armedIntent = null
            _state.value = NfcResult.Idle
        }
    }

    fun onTagDiscovered(tag: Tag) {
        scope.launch { handleTag(tag) }
    }

    internal suspend fun handleTag(tag: Tag) {
        // Peek state first: on a Writing-state tap we skip the NDEF pre-read
        // entirely and synthesize a RawTagRead from the in-memory Tag object
        // (uid + techList, no I/O). Cuts one Ndef.connect cycle off the write
        // path so the user has a smaller "keep phone steady" window. Vendor
        // taps were already gated upstream in MainViewModel.onWriteTapped, so
        // arriving here in Writing state means the tag is NDEF-capable.
        val isWriting = _state.value is NfcResult.Writing
        val raw = if (isWriting) {
            RawTagRead(
                uid = CardUid.fromBytes(tag.id),
                records = null,
                techList = tag.techList?.toList().orEmpty(),
            )
        } else {
            try {
                wrapper.read(tag)
            } catch (t: Throwable) {
                transition { NfcResult.Error("zero-length UID, non-NFC-A tag?", t) }
                logCause("zero-length UID, non-NFC-A tag?", t)
                return
            }
        }
        val classification = if (isWriting) {
            classify(raw)
        } else {
            val bSalt = settingsRepository.settings.value.bambuSalt
            val smSalt = SNAPMAKER_KEY_SALT
            val parsedPayload = tryReadAndParseWithKeys(tag, raw.records, bSalt, smSalt)
            if (parsedPayload != null) {
                TagClassification.OpenSpool(parsedPayload)
            } else {
                classify(raw)
            }
        }
        val now = clock.now().toEpochMilliseconds()
        _lastSeenTag.value = TagBuffer(raw.uid, classification, now)

        val (intent, currentState) = mutex.withLock {
            val snapshot = armedIntent to _state.value
            if (snapshot.first != null) armedIntent = null
            snapshot
        }
        when (currentState) {
            is NfcResult.Reading -> if (intent is NfcIntent.Read) {
                // Tap was spent on this armed Read — clear the buffer so a
                // second button press requires a fresh tap rather than
                // re-consuming the same UID.
                _lastSeenTag.value = null
                transition { NfcResult.Success(raw.uid, classification) }
            }
            is NfcResult.Writing -> if (intent is NfcIntent.Write) {
                runWriteThenVerify(tag, raw, classification, intent)
            }
            is NfcResult.Verifying -> if (intent is NfcIntent.Verify) {
                runStandaloneVerify(tag, raw, classification, intent.expectedPayload)
            }
            else -> {
                // Idle / Success / Error — buffer-only update already done above.
            }
        }
    }

    private suspend fun runWriteThenVerify(
        tag: Tag,
        raw: RawTagRead,
        classification: TagClassification,
        intent: NfcIntent.Write,
    ) {
        // Pre-block vendor-classified tags from any NDEF write attempt. Two
        // cases land here as Vendor:
        //   1. MifareClassic-only chip (no Ndef in techList) — Ndef.get
        //      returns null and writeRecords would throw NonNdefTagException.
        //   2. Bambu/Creality chips Android promotes to NDEF in techList.
        //      writeRecords would return success bytes-wise but the chip
        //      doesn't actually persist them; we'd then PATCH Spoolman
        //      with the UID (correct outcome) but surface a "tag write
        //      failed" snackbar (misleading).
        // Surface as the standard vendor-tag rejection so the dispatch
        // layer's vendor-tag snackbar fires.
        if (classification is TagClassification.Vendor) {
            transition {
                NfcResult.Error("vendor-tag protected (FR-4.7): ${classification.reason}", null)
            }
            return
        }
        val records = encodePayloadRecords(intent.payload)
        try {
            wrapper.writeRecords(tag, records)
        } catch (t: NonNdefTagException) {
            transition {
                NfcResult.Error("vendor-tag protected (FR-4.7): non-NDEF tag", t)
            }
            return
        } catch (t: Throwable) {
            transition { NfcResult.Error("write failed: ${t.message ?: t::class.simpleName}", t) }
            logCause("write failed", t)
            return
        }
        // transition { NfcResult.Verifying }
        // val readback = try {
        //     wrapper.readRecords(tag)
        // } catch (t: Throwable) {
        //     transition { NfcResult.Error("verify mismatch", t) }
        //     logCause("verify mismatch", t)
        //     return
        // }
        // if (readback != null && readback != records) {
        //     android.util.Log.w(
        //         TAG,
        //         "verify mismatch: readback=${readback.size} records=${records.size} bytesEqual=${readback.flatMap { it.payload.toList() } == records.flatMap { it.payload.toList() }}",
        //     )
        //     transition { NfcResult.Error("verify mismatch (readback != written)") }
        //     return
        // }
        // if (readback == null) {
        //     android.util.Log.w(TAG, "readback null after write — treating as success (tag promoted to NDEF, handle stale)")
        // }
        transition {
            NfcResult.Success(raw.uid, TagClassification.OpenSpool(intent.payload))
        }
    }

    private suspend fun runStandaloneVerify(
        tag: Tag,
        raw: RawTagRead,
        classification: TagClassification,
        expectedPayload: OpenSpoolPayload,
    ) {
        // (Vendor pre-block removed; see runWriteThenVerify. The chip's
        // own write-protection / NDEF availability is the only gate.)
        val expectedRecords = encodePayloadRecords(expectedPayload)
        val readback = try {
            wrapper.readRecords(tag)
        } catch (t: Throwable) {
            transition { NfcResult.Error("verify mismatch", t) }
            logCause("verify mismatch", t)
            return
        }
        if (readback == null || readback != expectedRecords) {
            transition { NfcResult.Error("verify mismatch") }
            return
        }
        transition {
            NfcResult.Success(raw.uid, TagClassification.OpenSpool(expectedPayload))
        }
    }

    private fun classify(raw: RawTagRead): TagClassification {
        val records = raw.records
        if (records == null) {
            // No NDEF readable. Distinguish a truly blank-but-formattable tag
            // (which the user can still write to) from a non-NDEF vendor tag.
            //
            // MifareClassic chips are factory-encrypted by vendors (Bambu,
            // Creality, etc.); Android still reports NdefFormatable in their
            // techList but the sectors are locked. Treat MifareClassic-only
            // tags (no Ndef in techList) as vendor tags.
            //
            // A tag with `Ndef` in techList that returns null records is NOT
            // a vendor tag — it's typically a tag we just wrote where the OS
            // tag handle is briefly stale (the U6a OPEN-1 race). Treat it as
            // Blank so the user's next Save & Write doesn't get misrouted
            // into the vendor-pair-only flow.
            val isMifareClassic = raw.techList.contains("android.nfc.tech.MifareClassic")
            val isFormattable = raw.techList.contains("android.nfc.tech.NdefFormatable")
            val isNdef = raw.techList.contains("android.nfc.tech.Ndef")
            return when {
                // MifareClassic in techList means a vendor-encrypted chip
                // (Bambu, Creality, etc.). Android sometimes ALSO exposes
                // Ndef on these (it auto-promotes), but our writes won't
                // persist — the chip's crypto rejects them silently. Treat
                // as Vendor regardless of Ndef presence so the write path
                // pre-block + Spoolman UID-only pair fires.
                isMifareClassic ->
                    TagClassification.Vendor("MifareClassic (vendor-encrypted)")
                isNdef -> TagClassification.Blank
                isFormattable -> TagClassification.Blank
                else ->
                    TagClassification.Vendor("non-NDEF tag (${raw.techList.joinToString().ifEmpty { "unknown tech" }})")
            }
        }
        // Records readable but no OpenSpool MIME match (or empty list).
        // Treat as Blank — this is a writable NDEF tag the user can overwrite.
        // The earlier behaviour (return Vendor) misclassified our own
        // partially-written tags as "vendor" and blocked rewrites; the only
        // tags that should actually be rejected as vendor are MifareClassic-
        // locked ones, which we already caught above.
        if (records.isEmpty()) return TagClassification.Blank
        val mimeRecord = records.firstOrNull { record ->
            record.tnf == NdefRecordView.TNF_MIME_MEDIA && run {
                val mime = String(record.type, Charsets.US_ASCII).lowercase()
                mime == MIME_OPENSPOOL || mime == MIME_JSON
            }
        } ?: return TagClassification.Blank
        val payloadBytes = mimeRecord.payload
        if (payloadBytes.isEmpty()) return TagClassification.Blank
        val text = try {
            String(payloadBytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            return TagClassification.Blank
        }
        // OpenSpool MIME is present but the JSON didn't parse → most likely a
        // truncated / partial write of our own format. Still Blank-like so
        // the user can rewrite it.
        return when (val decoded = OpenSpoolPayloadCodec.fromJson(text)) {
            is OpenSpoolDecodeResult.Success -> TagClassification.OpenSpool(decoded.payload)
            is OpenSpoolDecodeResult.Malformed,
            is OpenSpoolDecodeResult.NotOpenSpool -> TagClassification.Blank
        }
    }

    private fun encodePayloadRecords(payload: OpenSpoolPayload): List<NdefRecordView> {
        val json = OpenSpoolPayloadCodec.toJson(payload)
        // MIME is application/json (FR-U6b-Δ-3) — Snapmaker U1 firmware filters
        // by MIME and only accepts application/json. The read-side classifier
        // dual-accepts both this and the legacy application/vnd.openspool+json
        // so tags written by intermediate v2 builds still round-trip.
        return listOf(
            NdefRecordView(
                tnf = NdefRecordView.TNF_MIME_MEDIA,
                type = MIME_JSON.toByteArray(Charsets.US_ASCII),
                payload = json.toByteArray(Charsets.UTF_8),
            ),
        )
    }

    private fun tryReadAndParseWithKeys(
        tag: Tag,
        ndefRecords: List<NdefRecordView>?,
        bambuSalt: String,
        snapmakerSalt: String
    ): OpenSpoolPayload? {
        return when {
            bambuSalt.isNotBlank() && snapmakerSalt.isNotBlank() -> {
                TagFormatParser.parseWithBothKeys(tag, ndefRecords, bambuSalt, snapmakerSalt)
            }
            bambuSalt.isNotBlank() -> {
                TagFormatParser.parseWithBambuKeys(tag, ndefRecords, bambuSalt)
            }
            snapmakerSalt.isNotBlank() -> {
                TagFormatParser.parseWithSnapmakerKeys(tag, ndefRecords, snapmakerSalt)
            }
            else -> {
                TagFormatParser.parseDefault(tag, ndefRecords)
            }
        }
    }

    private suspend fun transition(block: () -> NfcResult) {
        withContext(ioDispatcher) {
            mutex.withLock {
                _state.value = block()
            }
        }
    }

    private fun logCause(reason: String, cause: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, reason, cause)
        }
    }

    companion object {
        const val TTL_MS_DEFAULT: Long = 5_000L
        internal const val MIME_OPENSPOOL = "application/vnd.openspool+json"
        internal const val MIME_JSON = "application/json"
        private const val TAG = "NfcRepository"
    }
}
