package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcRepository
import javax.inject.Inject

class ReadAndPairUseCase @Inject constructor(
    private val nfc: NfcRepository,
    private val spoolman: SpoolmanRepository,
) {

    suspend operator fun invoke(): ReadAndPairResult {
        val nfcOutcome = readTag()
            // No tap landed within the wait window. NfcFailed (not Cancelled)
            // because applyResult surfaces NfcFailed.reason verbatim as the
            // snackbar — using the old ViewModel timeout-branch copy here
            // keeps the user-visible "No tag tapped. Try again." UX intact.
            ?: return ReadAndPairResult.NfcFailed("No tag tapped. Try again.")
        return when (nfcOutcome) {
            is NfcResult.Error -> ReadAndPairResult.NfcFailed(nfcOutcome.reason)
            is NfcResult.Success -> handleSuccess(nfcOutcome)
            else -> ReadAndPairResult.NfcFailed("unexpected NFC state: $nfcOutcome")
        }
    }

    private suspend fun readTag(): NfcResult? {
        // A buffered passive tap already carries its vendor decode (handleTag
        // runs the parse on every non-write tap), so consuming it gives the
        // prefill immediately — no re-tap. Only when there's no fresh buffer do
        // we arm and wait for a physical tap.
        val buffered = nfc.consumeLastSeen(NfcIntent.Read)
        if (buffered != null) return buffered
        nfc.arm(NfcIntent.Read)
        val outcome = nfc.awaitTerminal(READ_TAP_WAIT_MS)
        if (outcome == null) {
            // Nobody tapped — disarm so the stale arm can't consume a late
            // tap into the void (this flow has already given up).
            nfc.disarm()
            return null
        }
        return outcome
    }

    private suspend fun handleSuccess(success: NfcResult.Success): ReadAndPairResult {
        if (success.uid.hex.isEmpty()) {
            return ReadAndPairResult.NfcFailed("zero-length UID, non-NFC-A tag?")
        }
        val outcome = spoolman.findSpoolsByCardUid(success.uid)
        return branchOnSpoolman(success.uid, success.classification, outcome)
    }

    private suspend fun branchOnSpoolman(
        uid: CardUid,
        classification: TagClassification,
        outcome: SpoolmanOutcome<List<SpoolmanSpool>>,
    ): ReadAndPairResult = when (outcome) {
        is SpoolmanOutcome.Success -> branchOnMatches(uid, classification, outcome.data)
        is SpoolmanOutcome.NetworkError -> {
            // No Spoolman lookup (URL not configured OR server unreachable):
            // fall through to the 0-match branch so the tag's own OpenSpool
            // payload (or a blank form) prefills. The user gets their data;
            // a separate banner already surfaces the connectivity issue.
            branchOnMatches(uid, classification, emptyList())
        }
        is SpoolmanOutcome.HttpError,
        is SpoolmanOutcome.ParseError -> ReadAndPairResult.SpoolmanFailed(uid, classification, outcome)
    }

    private suspend fun branchOnMatches(
        uid: CardUid,
        classification: TagClassification,
        matches: List<SpoolmanSpool>,
    ): ReadAndPairResult = when {
        matches.size == 1 -> ReadAndPairResult.Success.PrefillFromSpoolman(uid, matches.single(), classification)
        matches.size >= 2 -> ReadAndPairResult.Ambiguous(uid, matches, classification)
        classification is TagClassification.OpenSpool -> resolveBySpoolIdOrPrefillFromTag(uid, classification)
        else -> ReadAndPairResult.Success.BlankForm(uid, classification)
    }

    // BR-U5-RP-13: when UID lookup returns 0 matches and the tag carries an OpenSpool payload
    // with a parseable spool_id, try GET /api/v1/spool/{id} as a fallback before giving up to
    // PrefillFromTag. Recovers v1-era tags whose Spoolman record's lot_nr was never updated to
    // include card_uid:<uid>.
    private suspend fun resolveBySpoolIdOrPrefillFromTag(
        uid: CardUid,
        classification: TagClassification.OpenSpool,
    ): ReadAndPairResult {
        val spoolId = classification.payload.spoolId?.toIntOrNull()
            ?: return ReadAndPairResult.Success.PrefillFromTag(uid, classification.payload)
        return when (val outcome = spoolman.getSpool(spoolId)) {
            is SpoolmanOutcome.Success -> ReadAndPairResult.Success.PrefillFromSpoolman(uid, outcome.data, classification)
            is SpoolmanOutcome.HttpError -> if (outcome.code == 404) {
                ReadAndPairResult.Success.PrefillFromTag(uid, classification.payload)
            } else {
                ReadAndPairResult.SpoolmanFailed(uid, classification, outcome)
            }
            is SpoolmanOutcome.NetworkError -> {
                // No Spoolman (URL not configured OR server unreachable): the
                // tag carries everything we need to populate the form. Prefer
                // showing the user their data over an error snackbar.
                ReadAndPairResult.Success.PrefillFromTag(uid, classification.payload)
            }
            is SpoolmanOutcome.ParseError -> ReadAndPairResult.SpoolmanFailed(uid, classification, outcome)
        }
    }

    private companion object {
        /**
         * How long we wait for the user to physically tap a tag after Read
         * is armed. Preserves the old MainViewModel READ_TIMEOUT_MS_DEFAULT
         * (10s) UX budget for "press Read, then tap".
         */
        const val READ_TAP_WAIT_MS: Long = 10_000L
    }
}
