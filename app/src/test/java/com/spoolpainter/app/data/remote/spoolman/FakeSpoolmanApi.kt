package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class FakeSpoolmanApi : SpoolmanApi {

    var info: SpoolmanInfo = SpoolmanInfo(version = "0.1.0")
    var vendorList: MutableList<SpoolmanVendor> = mutableListOf()
    var filamentList: MutableList<SpoolmanFilament> = mutableListOf()
    var spoolList: MutableList<SpoolmanSpool> = mutableListOf()
    var nextVendorId: Int = 1
    var nextFilamentId: Int = 1
    var nextSpoolId: Int = 1

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

    val callLog: MutableList<String> = mutableListOf()

    override suspend fun getInfo(): Response<SpoolmanInfo> {
        callLog += "getInfo"
        failGetInfo?.let { return it.toResponse() }
        return Response.success(info)
    }

    override suspend fun listSpools(
        limit: Int?,
        offset: Int?,
        allowArchived: Boolean?,
    ): Response<List<SpoolmanSpool>> {
        callLog += "listSpools(limit=$limit,offset=$offset,allowArchived=$allowArchived)"
        failListSpools?.let { return it.toResponse() }
        val filtered = if (allowArchived == false) {
            spoolList.filterNot { it.archived }
        } else {
            spoolList.toList()
        }
        return Response.success(filtered)
    }

    override suspend fun getSpool(spoolId: Int): Response<SpoolmanSpool> {
        callLog += "getSpool($spoolId)"
        failGetSpool?.let { return it.toResponse() }
        val spool = spoolList.firstOrNull { it.id == spoolId }
            ?: return Response.error(404, "not found".toResponseBody(null))
        return Response.success(spool)
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
        callLog += "createVendor(${body.name})"
        failCreateVendor?.let { return it.toResponse() }
        val vendor = SpoolmanVendor(id = nextVendorId++, name = body.name)
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
            extra = body.extra,
        )
        filamentList += filament
        return Response.success(filament)
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

    private fun <T> Failure.toResponse(): Response<T> = when (this) {
        is Failure.Http -> Response.error(code, message.toResponseBody(null))
        is Failure.Throws -> throw cause
    }
}
