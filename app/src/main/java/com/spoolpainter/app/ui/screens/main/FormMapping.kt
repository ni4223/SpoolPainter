package com.spoolpainter.app.ui.screens.main

import com.spoolpainter.app.data.local.presets.MaterialPresetSource
import com.spoolpainter.app.domain.models.Brand
import com.spoolpainter.app.domain.models.ExternalFilament
import com.spoolpainter.app.domain.models.Material
import com.spoolpainter.app.domain.models.OpenSpoolPayload
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.TempRanges
import com.spoolpainter.app.domain.primitives.CardUid
import com.spoolpainter.app.domain.primitives.ColorHexCodec
import com.spoolpainter.app.domain.primitives.ExtraCardUidsCodec

internal object FormMapping {

    fun fromSpoolman(
        spool: SpoolmanSpool,
        currentUid: CardUid?,
        rawWriteMode: Boolean,
        uidSource: SpoolmanUidSource = SpoolmanUidSource.PreserveCurrent,
    ): FormState {
        val materialName = spool.filament.material ?: "Unknown"
        // Fall back to a synthesised Material for custom names that aren't
        // in the preset list (e.g. "PA-CF" the user typed via Other). Without
        // this the picker shows blank when re-selecting the same spool, even
        // though Spoolman has the material string.
        val materialData = MaterialPresetSource.lookup(materialName)
            ?: synthesiseMaterialFromSpool(materialName, spool.filament)
        val tempRanges = deriveSpoolmanTemps(materialData, spool)
        val resolvedUid = when (uidSource) {
            SpoolmanUidSource.PreserveCurrent -> currentUid
            SpoolmanUidSource.FromCardUidsOrClear ->
                ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "").firstOrNull()
        }
        // Decision M: spool's own price preferred; filament price is
        // fallback for legacy data without per-spool prices, and matches
        // Spoolman's COALESCE(spool.price, filament.price) sort behaviour.
        val effectivePrice = spool.price ?: spool.filament.price
        // Empty-spool weight: prefer the filament default so the form matches
        // Spoolman's own edit UI, which shows filament.spool_weight in its
        // "Empty Weight" field and computes Measured (remaining + empty) from
        // it. A per-spool spool.spool_weight is used only when the filament has
        // no default, so spools whose filament defines an empty weight stay in
        // lockstep with what Spoolman displays.
        // U32 B2 (D2): the vendor's empty-spool weight is the last fallback,
        // so Measured works for spools whose filament and spool both lack one.
        val effectiveSpoolWeight = spool.filament.spool_weight ?: spool.spool_weight
            ?: spool.filament.vendor?.empty_spool_weight
        return FormState(
            cardUid = resolvedUid,
            material = materialData,
            brand = Brand(spool.filament.vendor?.name ?: "Unknown"),
            colorHex = canonicaliseColorHex(spool.filament.color_hex),
            // Variant lives on extra.variant. NOT on filament.name — that
            // field is the full filament display name (e.g. "Polymaker PLA
            // Matte") and pulling it through as variant would clobber the
            // form. If extra.variant is missing, leave variant null and let
            // the tag's OpenSpool subtype fill it in via the merge in
            // MainViewModel.applyResult.
            variant = decodeExtraVariant(spool.filament.extra?.get("variant")),
            tempRanges = tempRanges,
            selectedSpoolId = spool.id,
            // Selecting a spool implies its parent filament is also "selected"
            // for write routing — show the filament chip + carry spool metadata
            // through to the expander.
            selectedFilamentId = spool.filament.id,
            rawWriteMode = rawWriteMode,
            // Spool metadata pulled from the parent filament record (Spoolman
            // stores these on the filament, not the spool). Null = field is
            // unset on Spoolman; expander shows blank. Diameter no longer
            // round-trips through the form (decision N).
            densityGPerCm3 = spool.filament.density,
            fullSpoolWeightG = spool.filament.weight,
            emptySpoolWeightG = effectiveSpoolWeight,
            priceMajor = effectivePrice,
            // v2.0.2 — spool-scope prefill snapshots. The use case
            // compares form values against these to decide whether
            // patchSpoolFields fires. Snapshot must match what the form
            // shows on initial prefill so an untouched save is a no-op.
            remainingWeightG = spool.remaining_weight,
            prefilledRemainingWeightG = spool.remaining_weight,
            initialWeightG = spool.initial_weight,
            usedWeightG = spool.used_weight,
            prefilledTempRanges = tempRanges,
            prefilledPriceMajor = effectivePrice,
            prefilledEmptySpoolWeightG = effectiveSpoolWeight,
        )
    }

    /**
     * Spoolman stores extra `text` fields as JSON-encoded strings — `"matte"` not
     * `matte` — per its `extra_fields.py:60-66` validator. Strip the wrapping
     * quotes if present so the raw value reaches the form.
     */
    internal fun decodeExtraVariant(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        val unwrapped = if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
        return unwrapped.takeIf { it.isNotBlank() }
    }

    enum class SpoolmanUidSource {
        /** Read flow + auto-pair after read: keep the UID we just tapped. */
        PreserveCurrent,

        /** Manual dropdown selection: derive the UID from the spool's extra.card_uids (or null). */
        FromCardUidsOrClear,
    }

    fun fromOpenSpool(
        uid: CardUid,
        payload: OpenSpoolPayload,
        rawWriteMode: Boolean,
    ): FormState {
        // U28 D2: a blank type (unknown vendor material code) leaves Material
        // empty for the user to pick; the tag's temps are still used.
        val unknownMaterial = payload.type.isBlank()
        val baseMaterial = MaterialPresetSource.lookup(payload.type)
        val parsedExtruderMin = payload.minTemp.toIntOrNull()
        val parsedExtruderMax = payload.maxTemp.toIntOrNull()
        val parsedBedMin = payload.bedMinTemp?.toIntOrNull()
        val parsedBedMax = payload.bedMaxTemp?.toIntOrNull()
        val resolvedMaterial = if (unknownMaterial) null else baseMaterial ?: synthesiseMaterial(
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

    /**
     * U8-Δ-1 — prefill the form from a Spoolman filament selected via the
     * hidden "Filament ▾" expander. Material/vendor/color/temps + the five
     * "More details" override fields are seeded from the filament; variant
     * comes from `extra.variant`. selectedFilamentId is set; selectedSpoolId
     * is cleared (mutex per Q-U8-7=A).
     */
    fun fromFilament(filament: SpoolmanFilament, rawWriteMode: Boolean): FormState {
        val materialName = filament.material ?: "Unknown"
        val materialData = MaterialPresetSource.lookup(materialName)
            ?: filament.settings_extruder_temp?.let { extMin ->
                Material(
                    name = materialName,
                    defaultMinTemp = extMin,
                    defaultMaxTemp = extMin + 20,
                    defaultBedMinTemp = filament.settings_bed_temp ?: 0,
                    defaultBedMaxTemp = (filament.settings_bed_temp ?: 0) + 10,
                    density = filament.density,
                )
            }
        // U32 B1 (D1): min falls back to the preset too, not only max.
        val extMin = filament.settings_extruder_temp ?: materialData?.defaultMinTemp
        val bedMin = filament.settings_bed_temp ?: materialData?.defaultBedMinTemp
        return FormState(
            material = materialData,
            brand = Brand(filament.vendor?.name ?: "Unknown"),
            colorHex = canonicaliseColorHex(filament.color_hex),
            variant = decodeExtraVariant(filament.extra?.get("variant")),
            // U32 FN1: a filament temp above the preset max would give
            // min > max and block Save; use deriveSpoolmanTemps' +20/+10 rule.
            tempRanges = TempRanges(
                extruderMin = extMin,
                extruderMax = materialData?.defaultMaxTemp?.let { if (extMin != null && extMin > it) extMin + 20 else it },
                bedMin = bedMin,
                bedMax = materialData?.defaultBedMaxTemp?.let { if (bedMin != null && bedMin > it) bedMin + 10 else it },
            ),
            selectedSpoolId = null,
            selectedFilamentId = filament.id,
            rawWriteMode = rawWriteMode,
            densityGPerCm3 = filament.density,
            fullSpoolWeightG = filament.weight,
            emptySpoolWeightG = filament.spool_weight,
            priceMajor = filament.price,
        )
    }

    fun blankForm(uid: CardUid, rawWriteMode: Boolean): FormState =
        FormState(cardUid = uid, rawWriteMode = rawWriteMode)

    /**
     * SpoolmanDB prefill — seed the form from an external filament pick.
     * Mirrors [fromFilament]: material via preset lookup (synthesised from
     * the DB temps/density when unknown), temps min=max when the DB carries
     * them (null keeps the material preset seeds), expander override fields
     * from the DB values. Variant strips ONE case-insensitive
     * word-boundary occurrence of the material from the DB name. Both
     * selections clear — Save creates a new filament stamped with the
     * external_id.
     */
    fun applyExternalFilament(form: FormState, external: ExternalFilament): FormState {
        val materialName = external.material.trim().ifBlank { "Unknown" }
        val materialData = MaterialPresetSource.lookup(materialName)
            ?: external.extruderTemp?.let { extTemp ->
                Material(
                    name = materialName,
                    defaultMinTemp = extTemp,
                    defaultMaxTemp = extTemp + 20,
                    defaultBedMinTemp = external.bedTemp ?: 0,
                    defaultBedMaxTemp = (external.bedTemp ?: 0) + 10,
                    density = external.density?.toFloat(),
                )
            }
        return FormState(
            cardUid = form.cardUid,
            material = materialData,
            brand = Brand(external.manufacturer.trim().ifBlank { "Unknown" }),
            colorHex = canonicaliseColorHex(external.colorHex ?: external.colorHexes?.firstOrNull()),
            variant = deriveExternalVariant(external.name, external.material),
            tempRanges = TempRanges(
                extruderMin = external.extruderTemp ?: materialData?.defaultMinTemp,
                extruderMax = external.extruderTemp ?: materialData?.defaultMaxTemp,
                bedMin = external.bedTemp ?: materialData?.defaultBedMinTemp,
                bedMax = external.bedTemp ?: materialData?.defaultBedMaxTemp,
            ),
            selectedSpoolId = null,
            selectedFilamentId = null,
            rawWriteMode = form.rawWriteMode,
            moreDetailsExpanded = form.moreDetailsExpanded,
            densityGPerCm3 = external.density?.toFloat(),
            fullSpoolWeightG = external.weight?.toFloat(),
            emptySpoolWeightG = external.spoolWeight?.toFloat(),
            externalFilamentId = external.id,
            externalDiameterMm = external.diameter,
        )
    }

    /** variant = DB name minus one material-word occurrence ("PolyLite
     *  PLA"/PLA → "PolyLite", "PLA"/PLA → null), whitespace collapsed. */
    internal fun deriveExternalVariant(name: String, material: String): String? {
        val mat = material.trim()
        val cleaned = if (mat.isNotEmpty()) {
            Regex("\\b${Regex.escape(mat)}\\b", RegexOption.IGNORE_CASE)
                .replaceFirst(name, "")
        } else {
            name
        }
        return cleaned.replace(Regex("\\s+"), " ").trim().ifEmpty { null }
    }

    fun clearedFromDropdown(currentUid: CardUid?, rawWriteMode: Boolean): FormState =
        FormState(cardUid = currentUid, rawWriteMode = rawWriteMode)

    internal fun canonicaliseColorHex(raw: String?): String? = ColorHexCodec.canonicalise(raw)

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
        } else if (extruderTemp == null && materialData != null) {
            // U32 B1 (D1): filaments made in Spoolman's web UI often have no
            // Extruder Temp; without a fallback Save stays disabled (#12).
            // Accepted: Save then writes these preset temps into the filament.
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
        } else if (bedTemp == null && materialData != null) {
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

    /**
     * Synthesise a Material for a name that isn't in the preset list — e.g. a
     * custom "PA-CF" the user typed via Other on a previous Save. Pulls temps
     * + density off the Spoolman filament record so the form shows the user's
     * actual values when they re-pick the spool.
     */
    private fun synthesiseMaterialFromSpool(
        name: String,
        filament: com.spoolpainter.app.domain.models.SpoolmanFilament,
    ): Material = Material(
        name = name,
        defaultMinTemp = filament.settings_extruder_temp ?: 200,
        defaultMaxTemp = filament.settings_extruder_temp?.plus(20) ?: 220,
        defaultBedMinTemp = filament.settings_bed_temp ?: 50,
        defaultBedMaxTemp = filament.settings_bed_temp?.plus(10) ?: 70,
        density = filament.density,
    )
}
