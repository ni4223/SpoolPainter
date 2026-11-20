package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.Gson
import com.spoolpainter.app.domain.models.ExternalFilament
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanTag
import com.spoolpainter.app.domain.models.SpoolmanVendor
import com.spoolpainter.app.domain.models.TagConflictBody
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

private val GSON = Gson()

class FakeSpoolmanApi : SpoolmanApi {

    var info: SpoolmanInfo = SpoolmanInfo(version = "0.1.0")
    var vendorList: MutableList<SpoolmanVendor> = mutableListOf()
    var filamentList: MutableList<SpoolmanFilament> = mutableListOf()
    var spoolList: MutableList<SpoolmanSpool> = mutableListOf()
    var nextVendorId: Int = 1
    var nextFilamentId: Int = 1
    var nextSpoolId: Int = 1

    // SpoolmanDB search stub: naive contains-match over manufacturer/name/
    // material. totalCount != null also emits the x-total-count header;
    // null omits it (older-server fallback path).
    var externalFilamentResults: List<ExternalFilament> = emptyList()
    var externalFilamentSearchTotalCount: Int? = null
    var failSearchExternalFilaments: Failure? = null
    var lastExternalFilamentSearch: Triple<String, Int, Int>? = null
        private set

    // Captured POST bodies for create-chain assertions.
    var lastCreateVendorBody: CreateVendorRequest? = null
        private set
    var lastCreateFilamentBody: CreateFilamentRequest? = null
        private set

    val spoolExtraFields: MutableSet<String> = mutableSetOf()
    val filamentExtraFields: MutableSet<String> = mutableSetOf()

    var failGetInfo: Failure? = null
    var failListSpools: Failure? = null
    var failGetSpool: Failure? = null
    var failListFilaments: Failure? = null
    var failGetFilament: Failure? = null
    var failListVendors: Failure? = null
    var failCreateVendor: Failure? = null
    var failCreateFilament: Failure? = null
    var failCreateSpool: Failure? = null
    var failPatchSpool: Failure? = null
    var failListFieldsSpool: Failure? = null
    var failListFieldsFilament: Failure? = null
    var failPostFieldSpool: Failure? = null
    var failPostFieldFilament: Failure? = null

    var nextSpoolPatchHttpError: Failure.Http? = null
    var nextFilamentCreateHttpError: Failure.Http? = null
    var nextSpoolCreateHttpError: Failure.Http? = null
    var failPatchFilament: Failure? = null

    // Native tag store (server ≥ 0.27.0): normalized UID → holding spool id,
    // globally unique across spools. Mirrors the server's UID normalization
    // (uppercase hex, separators [ :_.-] and 0x prefix stripped).
    val spoolTags: MutableMap<String, Int> = mutableMapOf()
    var lastLinkTagBody: TagLinkBody? = null
        private set
    var failLinkTag: Failure? = null
    var failUnlinkTag: Failure? = null

    // One-shot linkTag failure (consumed on first call), mirroring the
    // staged-error pattern of nextSpoolPatchHttpError — used to fail a
    // single UID while later calls succeed.
    var nextLinkTagFailure: Failure? = null

    val callLog: MutableList<String> = mutableListOf()

    override suspend fun getInfo(): Response<SpoolmanInfo> {
        callLog += "getInfo"
        failGetInfo?.let { return it.toResponse() }
        return Response.success(info)
    }

    override suspend fun searchExternalFilaments(
        query: String,
        limit: Int,
        offset: Int,
    ): Response<List<ExternalFilament>> {
        callLog += "searchExternalFilaments(query=$query,limit=$limit,offset=$offset)"
        failSearchExternalFilaments?.let { return it.toResponse() }
        lastExternalFilamentSearch = Triple(query, limit, offset)
        val matched = if (query.isBlank()) {
            externalFilamentResults
        } else {
            externalFilamentResults.filter {
                it.manufacturer.contains(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.material.contains(query, ignoreCase = true)
            }
        }
        return externalFilamentSearchTotalCount
            ?.let { total -> Response.success(matched, Headers.headersOf("x-total-count", total.toString())) }
            ?: Response.success(matched)
    }

    override suspend fun listSpools(
        limit: Int?,
        offset: Int?,
        allowArchived: Boolean?,
        tag: String?,
    ): Response<List<SpoolmanSpool>> {
        // tag kept out of the legacy log spelling so historical callLog
        // assertions on the three-arg form stay stable.
        val tagSuffix = tag?.let { ",tag=$it" } ?: ""
        callLog += "listSpools(limit=$limit,offset=$offset,allowArchived=$allowArchived$tagSuffix)"
        failListSpools?.let { return it.toResponse() }
        val filtered = if (allowArchived == false) {
            spoolList.filterNot { it.archived }
        } else {
            spoolList.toList()
        }.let { unarchived ->
            if (tag == null) {
                unarchived
            } else {
                val holder = spoolTags[normaliseTagUid(tag)]
                unarchived.filter { it.id == holder }
            }
        }
        return Response.success(filtered.map { it.withNativeTags() })
    }

    override suspend fun getSpool(spoolId: Int): Response<SpoolmanSpool> {
        callLog += "getSpool($spoolId)"
        failGetSpool?.let { return it.toResponse() }
        val spool = spoolList.firstOrNull { it.id == spoolId }
            ?: return Response.error(404, "not found".toResponseBody(null))
        return Response.success(spool.withNativeTags())
    }

    override suspend fun listFilaments(): Response<List<SpoolmanFilament>> {
        callLog += "listFilaments"
        failListFilaments?.let { return it.toResponse() }
        return Response.success(filamentList.toList())
    }

    override suspend fun getFilament(filamentId: Int): Response<SpoolmanFilament> {
        callLog += "getFilament($filamentId)"
        failGetFilament?.let { return it.toResponse() }
        val filament = filamentList.firstOrNull { it.id == filamentId }
            ?: return Response.error(404, "not found".toResponseBody(null))
        return Response.success(filament)
    }

    override suspend fun listVendors(): Response<List<SpoolmanVendor>> {
        callLog += "listVendors"
        failListVendors?.let { return it.toResponse() }
        return Response.success(vendorList.toList())
    }

    override suspend fun createVendor(body: CreateVendorRequest): Response<SpoolmanVendor> {
        callLog += "createVendor(${body.name},external_id=${body.external_id})"
        failCreateVendor?.let { return it.toResponse() }
        lastCreateVendorBody = body
        val vendor = SpoolmanVendor(id = nextVendorId++, name = body.name, external_id = body.external_id)
        vendorList += vendor
        return Response.success(vendor)
    }

    override suspend fun createFilament(body: CreateFilamentRequest): Response<SpoolmanFilament> {
        callLog += "createFilament(vendor=${body.vendor_id},mat=${body.material},color=${body.color_hex},name=${body.name},extra=${body.extra})"
        nextFilamentCreateHttpError?.let { staged ->
            nextFilamentCreateHttpError = null
            return staged.toResponse()
        }
        failCreateFilament?.let { return it.toResponse() }
        lastCreateFilamentBody = body
        body.extra?.keys?.forEach { key ->
            if (key !in filamentExtraFields) {
                return Response.error(400, "Unknown extra field: $key".toResponseBody(null))
            }
        }
        val vendor = vendorList.firstOrNull { it.id == body.vendor_id }
        val filament = SpoolmanFilament(
            id = nextFilamentId++,
            name = body.name,
            material = body.material,
            vendor = vendor,
            color_hex = body.color_hex,
            settings_extruder_temp = body.settings_extruder_temp,
            settings_bed_temp = body.settings_bed_temp,
            density = body.density,
            diameter = body.diameter,
            weight = body.weight,
            spool_weight = body.spool_weight,
            price = body.price,
            external_id = body.external_id,
            extra = body.extra,
        )
        filamentList += filament
        return Response.success(filament)
    }

    override suspend fun deleteVendor(vendorId: Int): Response<Unit> {
        callLog += "deleteVendor($vendorId)"
        val removed = vendorList.firstOrNull { it.id == vendorId }
        return if (removed != null) {
            vendorList.remove(removed)
            Response.success(Unit)
        } else {
            Response.error(404, "no such vendor".toResponseBody(null))
        }
    }

    override suspend fun deleteFilament(filamentId: Int): Response<Unit> {
        callLog += "deleteFilament($filamentId)"
        val removed = filamentList.firstOrNull { it.id == filamentId }
        return if (removed != null) {
            filamentList.remove(removed)
            Response.success(Unit)
        } else {
            Response.error(404, "no such filament".toResponseBody(null))
        }
    }

    override suspend fun deleteSpool(spoolId: Int): Response<Unit> {
        callLog += "deleteSpool($spoolId)"
        val removed = spoolList.firstOrNull { it.id == spoolId }
        return if (removed != null) {
            spoolList.remove(removed)
            spoolTags.entries.removeAll { it.value == spoolId }
            Response.success(Unit)
        } else {
            Response.error(404, "no such spool".toResponseBody(null))
        }
    }

    override suspend fun linkTag(spoolId: Int, body: TagLinkBody): Response<SpoolmanTag> {
        callLog += "linkTag($spoolId,uid=${body.uid})"
        nextLinkTagFailure?.let { staged ->
            nextLinkTagFailure = null
            return staged.toResponse()
        }
        failLinkTag?.let { return it.toResponse() }
        lastLinkTagBody = body
        if (spoolList.none { it.id == spoolId }) {
            return Response.error(404, "no such spool".toResponseBody(null))
        }
        val key = normaliseTagUid(body.uid)
        val holder = spoolTags[key]
        val tag = SpoolmanTag(
            uid = body.uid,
            format = body.format,
            added = "2026-01-01T00:00:00Z",
        )
        return when (holder) {
            null -> {
                spoolTags[key] = spoolId
                Response.success(tag)
            }
            spoolId -> Response.success(tag)
            else -> {
                val conflict = TagConflictBody(
                    message = "Tag ${body.uid} is already used by spool $holder",
                    spool_id = holder,
                )
                Response.error(409, GSON.toJson(conflict).toResponseBody(null))
            }
        }
    }

    override suspend fun unlinkTag(spoolId: Int, uid: String): Response<Unit> {
        callLog += "unlinkTag($spoolId,uid=$uid)"
        failUnlinkTag?.let { return it.toResponse() }
        if (spoolList.none { it.id == spoolId }) {
            return Response.error(404, "no such spool".toResponseBody(null))
        }
        val key = normaliseTagUid(uid)
        val holder = spoolTags[key]
        return if (holder == spoolId) {
            spoolTags.remove(key)
            Response.success(Unit)
        } else {
            // Real Spoolman 0.27.0 wording for a not-held tag.
            Response.error(404, "Spool $spoolId has no tag with UID $uid.".toResponseBody(null))
        }
    }

    override suspend fun createSpool(body: CreateSpoolRequest): Response<SpoolmanSpool> {
        callLog += "createSpool(filament=${body.filament_id},extra=${body.extra})"
        nextSpoolCreateHttpError?.let { staged ->
            nextSpoolCreateHttpError = null
            return staged.toResponse()
        }
        failCreateSpool?.let { return it.toResponse() }
        body.extra?.keys?.forEach { key ->
            if (key !in spoolExtraFields) {
                return Response.error(400, "Unknown extra field: $key".toResponseBody(null))
            }
        }
        val filament = filamentList.firstOrNull { it.id == body.filament_id }
            ?: return Response.error(400, "missing filament".toResponseBody(null))
        val spool = SpoolmanSpool(
            id = nextSpoolId++,
            filament = filament,
            extra = body.extra,
        )
        spoolList += spool
        return Response.success(spool)
    }

    override suspend fun patchSpool(
        spoolId: Int,
        body: SpoolPatchBody,
    ): Response<SpoolmanSpool> {
        callLog += "patchSpool($spoolId,extra=${body.extra})"
        nextSpoolPatchHttpError?.let { staged ->
            nextSpoolPatchHttpError = null
            return staged.toResponse()
        }
        failPatchSpool?.let { return it.toResponse() }
        body.extra?.keys?.forEach { key ->
            if (key !in spoolExtraFields) {
                return Response.error(400, "Unknown extra field: $key".toResponseBody(null))
            }
        }
        val idx = spoolList.indexOfFirst { it.id == spoolId }
        if (idx < 0) return Response.error(404, "not found".toResponseBody(null))
        val updated = spoolList[idx].copy(extra = body.extra)
        spoolList[idx] = updated
        return Response.success(updated)
    }

    override suspend fun patchFilament(
        filamentId: Int,
        body: PatchFilamentBody,
    ): Response<SpoolmanFilament> {
        callLog += "patchFilament($filamentId,body=$body)"
        failPatchFilament?.let { return it.toResponse() }
        val idx = filamentList.indexOfFirst { it.id == filamentId }
        if (idx < 0) return Response.error(404, "not found".toResponseBody(null))
        val current = filamentList[idx]
        val updated = current.copy(
            name = body.name ?: current.name,
            color_hex = body.color_hex ?: current.color_hex,
            settings_extruder_temp = body.settings_extruder_temp ?: current.settings_extruder_temp,
            settings_bed_temp = body.settings_bed_temp ?: current.settings_bed_temp,
            density = body.density ?: current.density,
            diameter = body.diameter ?: current.diameter,
            weight = body.weight ?: current.weight,
            spool_weight = body.spool_weight ?: current.spool_weight,
            price = body.price ?: current.price,
            extra = body.extra ?: current.extra,
        )
        filamentList[idx] = updated
        return Response.success(updated)
    }

    override suspend fun listFields(entityType: String): Response<List<ExtraFieldDef>> {
        callLog += "listFields($entityType)"
        when (entityType) {
            "spool" -> failListFieldsSpool?.let { return it.toResponse() }
            "filament" -> failListFieldsFilament?.let { return it.toResponse() }
        }
        val source = when (entityType) {
            "spool" -> spoolExtraFields
            "filament" -> filamentExtraFields
            else -> emptySet()
        }
        val defs = source.map { key ->
            ExtraFieldDef(
                key = key,
                name = key,
                field_type = "text",
                order = 1,
                default_value = "\"\"",
            )
        }
        return Response.success(defs)
    }

    override suspend fun postField(
        entityType: String,
        key: String,
        body: ExtraFieldDef,
    ): Response<ExtraFieldDef> {
        callLog += "postField($entityType,$key)"
        when (entityType) {
            "spool" -> failPostFieldSpool?.let { return it.toResponse() }
            "filament" -> failPostFieldFilament?.let { return it.toResponse() }
        }
        when (entityType) {
            "spool" -> spoolExtraFields += key
            "filament" -> filamentExtraFields += key
        }
        return Response.success(body.copy(key = key))
    }

    sealed class Failure {
        data class Http(val code: Int, val message: String = "error") : Failure()
        data class Throws(val cause: Throwable) : Failure()
    }

    private fun normaliseTagUid(uid: String): String =
        uid.replace(Regex("[:_.\\-]"), "").removePrefix("0x").uppercase()

    /** GET /spool payload shape on ≥ 0.27.0: each spool carries the tags
     *  the native store holds for it. Spools built directly in fixtures
     *  keep their own (default empty) list. */
    private fun SpoolmanSpool.withNativeTags(): SpoolmanSpool {
        val id = id ?: return this
        val tags = spoolTags.entries
            .filter { it.value == id }
            .map { SpoolmanTag(uid = it.key, added = "2026-01-01T00:00:00Z") }
        return if (tags.isEmpty()) this else copy(tags = tags)
    }

    private fun <T> Failure.toResponse(): Response<T> = when (this) {
        is Failure.Http -> Response.error(code, message.toResponseBody(null))
        is Failure.Throws -> throw cause
    }
}
