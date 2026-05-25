package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.SpoolmanRepository
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ReadAndPairUseCase @Inject constructor(
    private val nfc: NfcRepository,
    private val spoolman: SpoolmanRepository,
) {

    suspend operator fun invoke(): ReadAndPairResult {
        val nfcOutcome = readTag()
        return when (nfcOutcome) {
            is NfcResult.Error -> ReadAndPairResult.NfcFailed(nfcOutcome.reason)
            is NfcResult.Success -> handleSuccess(nfcOutcome)
            else -> ReadAndPairResult.NfcFailed("unexpected NFC state: $nfcOutcome")
        }
    }

    private suspend fun readTag(): NfcResult {
        val buffered = nfc.consumeLastSeen(NfcIntent.Read)
        if (buffered != null) return buffered
        nfc.arm(NfcIntent.Read)
        return nfc.state.first { it is NfcResult.Success || it is NfcResult.Error }
    }

    private suspend fun handleSuccess(success: NfcResult.Success): ReadAndPairResult {
        if (success.uid.hex.isEmpty()) {
            return ReadAndPairResult.NfcFailed("zero-length UID — non-NFC-A tag?")
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
            // BR-U5-RP-7 — URL-not-configured falls through to the 0-match branch.
            if (outcome.cause is UrlNotConfiguredException) {
                branchOnMatches(uid, classification, emptyList())
            } else {
                ReadAndPairResult.SpoolmanFailed(uid, classification, outcome)
            }
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
            is SpoolmanOutcome.NetworkError,
            is SpoolmanOutcome.ParseError -> ReadAndPairResult.SpoolmanFailed(uid, classification, outcome)
        }
    }
}
