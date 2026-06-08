package com.spoolpainter.app.hardware.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.hardware.nfc.vendor.MifareUltralightReader
import com.spoolpainter.app.hardware.nfc.vendor.VendorAuth
import com.spoolpainter.app.hardware.nfc.vendor.VendorId
import com.spoolpainter.app.hardware.nfc.vendor.VendorTagRegistry
import com.spoolpainter.app.hardware.nfc.vendor.VendorSettings

/**
 * Vendor-tag parser dispatcher. Only invoked from `NfcRepository.handleTag`
 * when an explicit Read encounters a tag that the standard NDEF classifier
 * already labelled as Vendor (MifareClassic / MifareUltralight chip with no
 * readable OpenSpool MIME record). NDEF parsing is therefore not duplicated
 * here — the standard `classify(raw)` path owns it.
 *
 * Dispatch shape:
 *   1. Filter processors by `isEnabled(settings)` + `matchesChipType(techList)`.
 *   2. Ultralight branch: read pages once via [MifareUltralightReader], try
 *      candidates in registry order; first non-null parse wins.
 *   3. MifareClassic branch: derive auth keys per candidate, read once via
 *      [MifareClassicReader.tryReadRawCountedMulti], pick the vendor with
 *      the highest auth count, then fall through to the next-highest if its
 *      parse fails (matches U14 behaviour).
 */
object TagFormatParser {

    fun parseVendor(tag: Tag, settings: VendorSettings): OpenSpoolPayload? {
        val techList = tag.techList?.toList().orEmpty()
        val candidates = VendorTagRegistry.processors.filter {
            it.isEnabled(settings) && it.matchesChipType(techList)
        }
        if (candidates.isEmpty()) return null

        val uid = tag.id

        // Ultralight branch — fires when the techList includes
        // MifareUltralight OR when it only exposes NfcA (some Android stacks
        // don't promote a genuine Ultralight chip past the NfcA layer; the
        // reader falls back to raw NfcA READ commands in that case).
        // MifareClassic chips also expose NfcA, so we exclude them here so
        // the Classic branch below picks them up instead.
        val isUltralightCandidate = techList.contains(MifareUltralight::class.java.name) ||
            (techList.contains(NfcA::class.java.name) && !techList.contains(MifareClassic::class.java.name))
        val ultralightCandidates = if (isUltralightCandidate) {
            candidates.filter {
                // Anycubic / Elegoo accept NfcA in their matchesChipType fallback.
                it.matchesChipType(listOf(MifareUltralight::class.java.name, NfcA::class.java.name))
            }
        } else emptyList()
        if (ultralightCandidates.isNotEmpty()) {
            val raw = MifareUltralightReader.tryReadPages(tag) ?: return null
            for (proc in ultralightCandidates) {
                val parsed = proc.parse(uid, raw, auth = null, settings = settings)
                if (parsed != null) return parsed
            }
        }

        // MifareClassic branch — derive auth per candidate, multi-vendor read,
        // sort by auth count.
        val classicCandidates = candidates.filter {
            it.matchesChipType(listOf(MifareClassic::class.java.name)) &&
                techList.contains(MifareClassic::class.java.name)
        }
        if (classicCandidates.isEmpty()) return null

        val authMap: Map<VendorId, VendorAuth> = buildMap {
            for (proc in classicCandidates) {
                val auth = proc.deriveAuthKeys(uid, settings) ?: continue
                put(proc.id, auth)
            }
        }
        if (authMap.isEmpty()) return null

        val (raw, counts) = MifareClassicReader.tryReadRawCountedMulti(tag, authMap)
        if (raw == null) return null

        // Sort vendors by auth count desc; ties keep registry order. Try parse
        // in that order; first non-null wins. Falls through on parse failure
        // so a correctly-derived-but-wrong-format chip doesn't lose to its
        // runner-up. Matches U14 behaviour.
        val sortedByAuth = classicCandidates
            .filter { it.id in authMap }
            .sortedByDescending { counts[it.id] ?: 0 }
        for (proc in sortedByAuth) {
            val parsed = proc.parse(uid, raw, authMap[proc.id], settings)
            if (parsed != null) return parsed
        }
        return null
    }
}
