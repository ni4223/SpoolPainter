package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.MaterialDatabase
import com.spoolpainter.app.data.remote.spoolman.CardUidEncoding
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid

internal object FormMapping {

    fun fromSpoolman(
        spool: SpoolmanSpool,
        currentUid: CardUid?,
        rawWriteMode: Boolean,
        uidSource: SpoolmanUidSource = SpoolmanUidSource.PreserveCurrent,
    ): FormState {
        val materialName = spool.filament.material ?: "Unknown"
        val materialData = MaterialDatabase.getMaterial(materialName)
        val tempRanges = deriveSpoolmanTemps(materialData, spool)
        val resolvedUid = when (uidSource) {
            SpoolmanUidSource.PreserveCurrent -> currentUid
            SpoolmanUidSource.FromLotNrOrClear ->
                CardUidEncoding.decode(spool.lot_nr ?: "").uids.firstOrNull()
        }
        return FormState(
            cardUid = resolvedUid,
            material = materialData,
            brand = Brand(spool.filament.vendor?.name ?: "Unknown"),
            colorHex = canonicaliseColorHex(spool.filament.color_hex),
            variant = null,
            tempRanges = tempRanges,
            selectedSpoolId = spool.id,
            rawWriteMode = rawWriteMode,
        )
    }

    enum class SpoolmanUidSource {
        /** Read flow + auto-pair after read: keep the UID we just tapped. */
        PreserveCurrent,

        /** Manual dropdown selection: derive the UID from the spool's lot_nr (or null). */
        FromLotNrOrClear,
    }

    fun fromOpenSpool(
        uid: CardUid,
        payload: OpenSpoolPayload,
        rawWriteMode: Boolean,
    ): FormState {
        val baseMaterial = MaterialDatabase.getMaterial(payload.type)
        val parsedExtruderMin = payload.minTemp.toIntOrNull()
        val parsedExtruderMax = payload.maxTemp.toIntOrNull()
        val parsedBedMin = payload.bedMinTemp?.toIntOrNull()
        val parsedBedMax = payload.bedMaxTemp?.toIntOrNull()
        val resolvedMaterial = baseMaterial ?: synthesiseMaterial(
            name = payload.type,
            extMin = parsedExtruderMin,
            extMax = parsedExtruderMax,
            bedMin = parsedBedMin,
            bedMax = parsedBedMax,
        )
        return FormState(
            cardUid = uid,
            material = resolvedMaterial,
            brand = Brand(payload.brand),
            colorHex = canonicaliseColorHex(payload.colorHex),
            variant = payload.subtype.takeUnless { it == "Basic" || it.isBlank() },
            tempRanges = TempRanges(
                extruderMin = parsedExtruderMin ?: resolvedMaterial?.defaultMinTemp,
                extruderMax = parsedExtruderMax ?: resolvedMaterial?.defaultMaxTemp,
                bedMin = parsedBedMin ?: resolvedMaterial?.defaultBedMinTemp,
                bedMax = parsedBedMax ?: resolvedMaterial?.defaultBedMaxTemp,
            ),
            selectedSpoolId = null,
            rawWriteMode = rawWriteMode,
        )
    }

    fun blankForm(uid: CardUid, rawWriteMode: Boolean): FormState =
        FormState(cardUid = uid, rawWriteMode = rawWriteMode)

    fun clearedFromDropdown(currentUid: CardUid?, rawWriteMode: Boolean): FormState =
        FormState(cardUid = currentUid, rawWriteMode = rawWriteMode)

    internal fun canonicaliseColorHex(raw: String?): String? =
        raw?.removePrefix("#")
            ?.let { if (it.length > 6) it.takeLast(6) else it }
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }

    private fun deriveSpoolmanTemps(
        materialData: Material?,
        spool: SpoolmanSpool,
    ): TempRanges {
        val extruderTemp = spool.filament.settings_extruder_temp
        val bedTemp = spool.filament.settings_bed_temp
        val (extMin, extMax) = if (
            materialData != null &&
            extruderTemp != null &&
            extruderTemp >= materialData.defaultMinTemp &&
            extruderTemp <= materialData.defaultMaxTemp
        ) {
            materialData.defaultMinTemp to materialData.defaultMaxTemp
        } else {
            extruderTemp to extruderTemp?.plus(20)
        }
        val (bedMin, bedMax) = if (
            materialData != null &&
            bedTemp != null &&
            bedTemp >= materialData.defaultBedMinTemp &&
            bedTemp <= materialData.defaultBedMaxTemp
        ) {
            materialData.defaultBedMinTemp to materialData.defaultBedMaxTemp
        } else {
            bedTemp to bedTemp?.plus(10)
        }
        return TempRanges(
            extruderMin = extMin,
            extruderMax = extMax,
            bedMin = bedMin,
            bedMax = bedMax,
        )
    }

    private fun synthesiseMaterial(
        name: String,
        extMin: Int?,
        extMax: Int?,
        bedMin: Int?,
        bedMax: Int?,
    ): Material? {
        if (extMin == null && extMax == null && bedMin == null && bedMax == null) return null
        return Material(
            name = name,
            defaultMinTemp = extMin ?: extMax ?: 0,
            defaultMaxTemp = extMax ?: extMin?.plus(20) ?: 0,
            defaultBedMinTemp = bedMin ?: bedMax ?: 0,
            defaultBedMaxTemp = bedMax ?: bedMin?.plus(10) ?: 0,
        )
    }
}
