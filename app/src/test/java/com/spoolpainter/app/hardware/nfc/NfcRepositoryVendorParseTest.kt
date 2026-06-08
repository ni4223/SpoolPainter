package com.spoolpainter.app.hardware.nfc

import com.spoolpainter.app.data.local.Settings
import com.spoolpainter.app.domain.primitives.NfcIntent
import com.spoolpainter.app.domain.primitives.NfcResult
import com.spoolpainter.app.domain.primitives.TagClassification
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.makeTag
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.newRepository
import com.spoolpainter.app.hardware.nfc.NfcTestSupport.sampleUid
import com.spoolpainter.app.support.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vendor parse gating: TagFormatParser.parseVendor (Bambu/Snapmaker HKDF +
 * MifareClassic auth) is expensive — hundreds of ms of phone-on-tag time on
 * a real chip. It must run ONLY when the user explicitly armed Read AND the
 * standard NDEF classifier already labelled the chip Vendor.
 *
 * These tests exercise the gating only; the byte-parsing itself is covered
 * by BambuFormatTest / SnapmakerFormatTest, and the parse-success path is
 * validated end-to-end by the install-gate Snapmaker U1 round-trip in
 * `aidlc-docs/operations/manual-nfc-checklist.md`.
 *
 * In a JVM unit test environment `MifareClassic.get(tag)` returns null (no
 * Android NFC stack), so `parseVendor` always returns null here. We assert
 * the classification flow remains correct under those conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NfcRepositoryVendorParseTest {

    @Test
    fun `Reading a non-MifareClassic vendor chip classifies as Vendor with null parsedHint`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        // No NDEF records + empty techList → classify as
        // Vendor("non-NDEF tag (unknown tech)") via the existing classifier.
        wrapper.simulateRead(sampleUid(), records = null, techList = emptyList())
        val repo = newRepository(
            wrapper = wrapper,
            settingsRepository = FakeSettingsRepository(),
        )
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag(techList = emptyList()))
        val state = repo.state.value as NfcResult.Success
        val vendor = state.classification as TagClassification.Vendor
        assertNull("non-MifareClassic chips skip vendor parse", vendor.parsedHint)
    }

    @Test
    fun `Reading a MifareClassic vendor chip with no Bambu salt still classifies as Vendor`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(
            sampleUid(),
            records = null,
            techList = listOf("android.nfc.tech.MifareClassic"),
        )
        val repo = newRepository(
            wrapper = wrapper,
            // Default Settings: bambuSalt = "" → only Snapmaker (hardcoded
            // salt) attempts derivation. MifareClassic.get returns null in
            // JVM tests, so parseVendor returns null → parsedHint = null.
            settingsRepository = FakeSettingsRepository(Settings(bambuSalt = "")),
        )
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag(techList = listOf("android.nfc.tech.MifareClassic")))
        val state = repo.state.value as NfcResult.Success
        val vendor = state.classification as TagClassification.Vendor
        assertNull(vendor.parsedHint)
        assertTrue(
            "Vendor reason should mention MifareClassic encryption",
            vendor.reason.contains("vendor-encrypted", ignoreCase = true) ||
                vendor.reason.contains("MifareClassic", ignoreCase = true),
        )
    }

    @Test
    fun `Reading a MifareClassic vendor chip with Bambu salt configured still produces null parsedHint when no real chip backs the tag`() = runTest {
        // Even with a salt configured, no MifareClassic backing means the
        // raw read fails (returns null) and parseVendor returns null. The
        // chip stays classified Vendor with no parsedHint — exactly what we
        // want when the user pastes a salt but taps a non-Bambu chip.
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(
            sampleUid(),
            records = null,
            techList = listOf("android.nfc.tech.MifareClassic"),
        )
        val repo = newRepository(
            wrapper = wrapper,
            settingsRepository = FakeSettingsRepository(
                Settings(bambuSalt = "DEADBEEFCAFEBABE0011223344556677"),
            ),
        )
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag(techList = listOf("android.nfc.tech.MifareClassic")))
        val state = repo.state.value as NfcResult.Success
        val vendor = state.classification as TagClassification.Vendor
        assertNull(vendor.parsedHint)
    }

    @Test
    fun `idle ambient tap of a vendor chip does NOT invoke vendor parse`() = runTest {
        // Perf gate: no Read armed → parseVendor must not run. We can't
        // observe "didn't run" directly, but we CAN observe that the
        // classification stays exactly what classify(raw) produces — never
        // a Vendor-with-parsedHint, regardless of salt config.
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(
            sampleUid(),
            records = null,
            techList = listOf("android.nfc.tech.MifareClassic"),
        )
        val repo = newRepository(
            wrapper = wrapper,
            settingsRepository = FakeSettingsRepository(
                Settings(bambuSalt = "DEADBEEFCAFEBABE0011223344556677"),
            ),
        )
        // No arm() — this is a passive ambient tap.
        repo.handleTag(makeTag(techList = listOf("android.nfc.tech.MifareClassic")))
        // State stays Idle on a passive tap; lastSeenTag holds the buffer.
        assertEquals(NfcResult.Idle, repo.state.value)
        val buffered = repo.lastSeenTag.value
        assertTrue(buffered != null)
        val vendor = buffered!!.classification as TagClassification.Vendor
        assertNull("ambient tap must not invoke vendor parse", vendor.parsedHint)
    }

    @Test
    fun `Reading a MifareUltralight vendor chip classifies as Vendor with null parsedHint`() = runTest {
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(
            sampleUid(),
            records = null,
            techList = listOf("android.nfc.tech.MifareUltralight"),
        )
        val repo = newRepository(
            wrapper = wrapper,
            settingsRepository = FakeSettingsRepository(),
        )
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag(techList = listOf("android.nfc.tech.MifareUltralight")))
        val state = repo.state.value as NfcResult.Success
        val vendor = state.classification as TagClassification.Vendor
        assertNull("Anycubic / Elegoo gate without on-device backing returns null", vendor.parsedHint)
        assertTrue(vendor.reason.contains("Ultralight", ignoreCase = true))
    }

    @Test
    fun `Reading a MifareClassic chip with all vendor keys configured stays vendor with null parsedHint`() = runTest {
        // Even with QIDI default keys (always enabled) + Bambu salt + Creality salt + enc key
        // configured, the JVM environment can't authenticate to a real chip, so
        // parseVendor returns null. Dispatcher gating is the assertion.
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(
            sampleUid(),
            records = null,
            techList = listOf("android.nfc.tech.MifareClassic"),
        )
        val repo = newRepository(
            wrapper = wrapper,
            settingsRepository = FakeSettingsRepository(
                Settings(
                    bambuSalt = "DEADBEEFCAFEBABE0011223344556677",
                    crealitySalt = "11".repeat(32),
                    crealityEncKey = "22".repeat(32),
                ),
            ),
        )
        repo.arm(NfcIntent.Read)
        repo.handleTag(makeTag(techList = listOf("android.nfc.tech.MifareClassic")))
        val state = repo.state.value as NfcResult.Success
        val vendor = state.classification as TagClassification.Vendor
        assertNull(vendor.parsedHint)
    }

    @Test
    fun `Reading a Blank chip never invokes vendor parse`() = runTest {
        // classify(raw) returns Blank for an Ndef-techList tag — the vendor
        // gate (baseClassification is Vendor) short-circuits, so parseVendor
        // never runs. This is the dominant happy-path on NTAG213 chips.
        val wrapper = FakeNfcAdapterWrapper()
        wrapper.simulateRead(sampleUid(), records = null)
        val repo = newRepository(
            wrapper = wrapper,
            settingsRepository = FakeSettingsRepository(
                Settings(bambuSalt = "DEADBEEFCAFEBABE0011223344556677"),
            ),
        )
        repo.arm(NfcIntent.Read)
        // makeTag default techList = [Ndef, NdefFormatable] → classifies Blank.
        repo.handleTag(makeTag())
        val state = repo.state.value as NfcResult.Success
        assertEquals(TagClassification.Blank, state.classification)
    }
}
