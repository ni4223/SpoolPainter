package com.spoolpainter.app.domain.usecases

import com.spoolpainter.app.data.remote.spoolman.SpoolmanOutcome
import com.spoolpainter.app.data.remote.spoolman.UrlNotConfiguredException
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.support.FakeNfcRepository
import com.spoolpainter.app.support.FakeSpoolmanRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ReadAndPairUseCaseTest {

    private val nfc = FakeNfcRepository()
    private val spoolman = FakeSpoolmanRepository()
    private val useCase = ReadAndPairUseCase(nfc, spoolman)

    private val sampleUid = CardUid("0a1b2c3d")
    private val openSpoolPayload = OpenSpoolPayload(
        type = "PLA",
        colorHex = "FF0000",
        brand = "Bambu",
        minTemp = "190",
        maxTemp = "220",
    )
    private val sampleSpool = SpoolmanSpool(
        id = 42,
        filament = SpoolmanFilament(id = 7, material = "PLA"),
        lot_nr = "card_uid:0a1b2c3d",
    )
    private val anotherSpool = sampleSpool.copy(id = 43)

    @Test
    fun `tag-first buffered OpenSpool with zero spoolman matches returns PrefillFromTag`() = runTest {
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(openSpoolPayload)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        val result = useCase.invoke()

        assertTrue("got $result", result is ReadAndPairResult.Success.PrefillFromTag)
        assertEquals(0, nfc.armCalls)
        assertEquals(sampleUid, spoolman.lastFindUid)
    }

    @Test
    fun `tag-first buffered OpenSpool with one match returns PrefillFromSpoolman (collision rule)`() = runTest {
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(openSpoolPayload)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(sampleSpool))

        val result = useCase.invoke()

        assertTrue(result is ReadAndPairResult.Success.PrefillFromSpoolman)
        val match = result as ReadAndPairResult.Success.PrefillFromSpoolman
        assertEquals(42, match.spool.id)
    }

    @Test
    fun `tag-first miss falls back to arm Read`() = runTest {
        nfc.setBufferedTap(null)
        nfc.setNextRead(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        val result = useCase.invoke()

        assertEquals(1, nfc.armCalls)
        assertTrue(result is ReadAndPairResult.Success.BlankForm)
    }

    @Test
    fun `arm Read then Blank with zero matches returns BlankForm Blank`() = runTest {
        nfc.setBufferedTap(null)
        nfc.setNextRead(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        val result = useCase.invoke() as ReadAndPairResult.Success.BlankForm
        assertTrue(result.classification is TagClassification.Blank)
    }

    @Test
    fun `arm Read then Vendor with zero matches returns BlankForm Vendor (Spoolman is still called)`() = runTest {
        nfc.setBufferedTap(null)
        nfc.setNextRead(NfcResult.Success(sampleUid, TagClassification.Vendor("non-OpenSpool NDEF")))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        val result = useCase.invoke() as ReadAndPairResult.Success.BlankForm
        assertTrue(result.classification is TagClassification.Vendor)
        assertEquals(sampleUid, spoolman.lastFindUid)
    }

    @Test
    fun `arm Read then OpenSpool with two matches returns Ambiguous`() = runTest {
        nfc.setBufferedTap(null)
        nfc.setNextRead(NfcResult.Success(sampleUid, TagClassification.OpenSpool(openSpoolPayload)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(listOf(sampleSpool, anotherSpool))

        val result = useCase.invoke()

        assertTrue(result is ReadAndPairResult.Ambiguous)
        assertEquals(2, (result as ReadAndPairResult.Ambiguous).matches.size)
    }

    @Test
    fun `Spoolman HttpError returns SpoolmanFailed`() = runTest {
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.HttpError(500, "boom")

        val result = useCase.invoke()
        assertTrue(result is ReadAndPairResult.SpoolmanFailed)
    }

    @Test
    fun `Spoolman NetworkError with UrlNotConfigured cause returns BlankForm`() = runTest {
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult =
            SpoolmanOutcome.NetworkError(UrlNotConfiguredException())

        val result = useCase.invoke()
        assertTrue("got $result", result is ReadAndPairResult.Success.BlankForm)
    }

    @Test
    fun `Spoolman NetworkError falls through to BlankForm so user is not blocked`() = runTest {
        // Per U7: any Spoolman NetworkError (URL not configured OR server
        // unreachable) falls through to the 0-match branch so the tag's own
        // OpenSpool payload (or a blank form) prefills. The user gets their
        // data; a separate banner already surfaces the connectivity issue.
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult =
            SpoolmanOutcome.NetworkError(IOException("offline"))

        val result = useCase.invoke()
        assertTrue("got $result", result is ReadAndPairResult.Success.BlankForm)
    }

    @Test
    fun `Spoolman ParseError returns SpoolmanFailed`() = runTest {
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.Blank))
        spoolman.nextFindSpoolsByCardUidResult =
            SpoolmanOutcome.ParseError(IllegalStateException("bad json"))

        val result = useCase.invoke()
        assertTrue(result is ReadAndPairResult.SpoolmanFailed)
    }

    @Test
    fun `Nfc Error short-circuits and does not call Spoolman`() = runTest {
        nfc.setBufferedTap(null)
        nfc.setNextRead(NfcResult.Error("NFC not available"))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        val result = useCase.invoke()
        assertTrue(result is ReadAndPairResult.NfcFailed)
        assertEquals(null, spoolman.lastFindUid)
    }

    @Test
    fun `zero uid matches with payload spool_id resolved returns PrefillFromSpoolman`() = runTest {
        val payloadWithSpoolId = openSpoolPayload.copy(spoolId = "42")
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(payloadWithSpoolId)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        spoolman.nextGetSpoolResult = SpoolmanOutcome.Success(sampleSpool)

        val result = useCase.invoke()

        assertTrue("got $result", result is ReadAndPairResult.Success.PrefillFromSpoolman)
        assertEquals(42, spoolman.lastGetSpoolId)
    }

    @Test
    fun `zero uid matches with payload spool_id 404 falls back to PrefillFromTag`() = runTest {
        val payloadWithSpoolId = openSpoolPayload.copy(spoolId = "99")
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(payloadWithSpoolId)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        spoolman.nextGetSpoolResult = SpoolmanOutcome.HttpError(404, "Not found")

        val result = useCase.invoke()

        assertTrue("got $result", result is ReadAndPairResult.Success.PrefillFromTag)
        assertEquals(99, spoolman.lastGetSpoolId)
    }

    @Test
    fun `zero uid matches with payload spool_id NetworkError falls back to PrefillFromTag`() = runTest {
        // Per U7: when getSpool's NetworkError fires (URL not configured OR
        // server unreachable), the OpenSpool payload on the tag is enough to
        // prefill the form. Falling through avoids erroring the user out
        // when the tag carries everything we need.
        val payloadWithSpoolId = openSpoolPayload.copy(spoolId = "7")
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(payloadWithSpoolId)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())
        spoolman.nextGetSpoolResult = SpoolmanOutcome.NetworkError(IOException("offline"))

        val result = useCase.invoke()

        assertTrue("got $result", result is ReadAndPairResult.Success.PrefillFromTag)
    }

    @Test
    fun `zero uid matches with null payload spool_id does not call getSpool returns PrefillFromTag`() = runTest {
        val payloadNoSpoolId = openSpoolPayload.copy(spoolId = null)
        nfc.setBufferedTap(NfcResult.Success(sampleUid, TagClassification.OpenSpool(payloadNoSpoolId)))
        spoolman.nextFindSpoolsByCardUidResult = SpoolmanOutcome.Success(emptyList())

        val result = useCase.invoke()

        assertTrue("got $result", result is ReadAndPairResult.Success.PrefillFromTag)
        assertEquals(0, spoolman.getSpoolCalls)
    }

    @Test
    fun `zero-length UID returns NfcFailed`() = runTest {
        nfc.setBufferedTap(NfcResult.Success(CardUid(""), TagClassification.Blank))

        val result = useCase.invoke()
        assertTrue(result is ReadAndPairResult.NfcFailed)
        assertEquals("zero-length UID, non-NFC-A tag?", (result as ReadAndPairResult.NfcFailed).reason)
    }
}
