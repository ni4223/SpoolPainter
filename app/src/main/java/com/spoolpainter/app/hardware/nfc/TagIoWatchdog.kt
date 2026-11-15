package com.spoolpainter.app.hardware.nfc

import android.nfc.tech.TagTechnology
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Holder through which a tag-I/O session registers its live tech object so
 * the watchdog can force-close it when the session overruns its budget.
 *
 * The session must [register] the tech BEFORE calling
 * [TagTechnology.connect] — a connect that never returns is exactly the
 * failure mode this exists for.
 */
class TagTechCloser {
    private val techRef = AtomicReference<TagTechnology?>(null)

    fun register(tech: TagTechnology?) {
        techRef.set(tech)
    }

    /**
     * Best-effort hard close of the registered tech. Returns true when a
     * tech was registered (close() itself is allowed to throw — the goal is
     * to abort any pending connect/transceive, and a throwing close still
     * unwedges the blocked binder call). Double-close by the session's own
     * finally block is fine: callers already tolerate it.
     */
    fun closeRegistered(): Boolean {
        val tech = techRef.getAndSet(null) ?: return false
        return try {
            tech.close()
            true
        } catch (_: Throwable) {
            true
        }
    }
}

/**
 * Runs a blocking tag-I/O session ([block]) under a hard timeout, on the
 * calling code's behalf.
 *
 * Why this exists (device evidence, v2.3.1 field report "tag gets no
 * contact fast enough"): on a marginal / fumbling RF contact,
 * [TagTechnology.connect] — a blocking binder call into the OS NFC
 * service — can hang INDEFINITELY. The connection is then never closed,
 * and a stuck tech connection wedges the OS NFC stack for ALL further
 * dispatch/connect attempts FROM THIS PROCESS until process death; only an
 * app restart recovers NFC. App-side state-machine recovery cannot fix
 * that, so the tag I/O itself must be bounded and the tech hard-aborted
 * via [TagTechnology.close] — the documented Android recovery: close()
 * aborts a pending connect/transceive, which makes the blocked call
 * terminate with an IOException.
 *
 * Semantics: [block] runs inside `runInterruptible(dispatcher)`; it
 * receives a [TagTechCloser] and registers its tech with it before
 * connecting. On timeout the watchdog force-closes the registered tech —
 * unblocking the interrupted session — and throws
 * [TagIoWatchdogTimeoutException]. If the block completes within the
 * budget, no close is performed by the watchdog; the session keeps its own
 * close-in-finally behaviour. Exceptions thrown by the block itself
 * (within the budget) propagate unchanged.
 */
suspend fun <T> withTechWatchdog(
    dispatcher: CoroutineDispatcher,
    timeoutMs: Long,
    block: (TagTechCloser) -> T,
): T {
    val closer = TagTechCloser()
    // Wrap in Result so a block that legitimately returns null (e.g. a
    // short vendor read) is distinguishable from withTimeoutOrNull's null
    // (= timed out).
    val outcome: Result<T>? = withTimeoutOrNull(timeoutMs) {
        runInterruptible(dispatcher) { Result.success(block(closer)) }
    }
    if (outcome == null) {
        // Timeout: hard-close the tech FIRST (this aborts the pending
        // connect/transceive so the interrupted block terminates with an
        // IOException instead of leaking its thread forever), then report.
        closer.closeRegistered()
        throw TagIoWatchdogTimeoutException(timeoutMs)
    }
    return outcome.getOrThrow()
}

/**
 * Watchdog trip. Deliberately a plain [IOException] subclass so
 * NfcRepository's existing catch paths treat it as a normal read/write
 * failure (Error state + snackbar) instead of a crash.
 */
class TagIoWatchdogTimeoutException(
    timeoutMs: Long,
) : IOException("tag I/O watchdog timeout (${timeoutMs}ms), connection force-closed")

/**
 * Vendor full-dump reads (Bambu/Snapmaker MifareClassic full-sector dumps,
 * Ultralight/NfcA page walks) legitimately take seconds on slow chips —
 * longer than the NDEF budget — so vendor reader sessions get this larger
 * cap.
 */
const val VENDOR_TECH_IO_TIMEOUT_MS: Long = 12_000L
