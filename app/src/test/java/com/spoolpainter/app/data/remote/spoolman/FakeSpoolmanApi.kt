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

    var failGetInfo: Failure? = null
    var failFindSpoolsByLotNr: Failure? = null
    var failListSpools: Failure? = null
    var failGetSpool: Failure? = null
    var failListFilaments: Failure? = null
    var failListVendors: Failure? = null
    var failCreateVendor: Failure? = null
    var failCreateFilament: Failure? = null
    var failCreateSpool: Failure? = null
    var failPatchSpoolLotNr: Failure? = null

    val callLog: MutableList<String> = mutableListOf()

    override suspend fun getInfo(): Response<SpoolmanInfo> {
        callLog += "getInfo"
        failGetInfo?.let { return it.toResponse() }
        return Response.success(info)
    }

    override suspend fun findSpoolsByLotNr(lotNrSubstring: String): Response<List<SpoolmanSpool>> {
        callLog += "findSpoolsByLotNr($lotNrSubstring)"
        failFindSpoolsByLotNr?.let { return it.toResponse() }
        val matches = spoolList.filter { (it.lot_nr ?: "").contains(lotNrSubstring) }
        return Response.success(matches)
    }

    override suspend fun listSpools(limit: Int?, offset: Int?): Response<List<SpoolmanSpool>> {
        callLog += "listSpools(limit=$limit,offset=$offset)"
        failListSpools?.let { return it.toResponse() }
        return Response.success(spoolList.toList())
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
        callLog += "createFilament(vendor=${body.vendor_id},mat=${body.material},color=${body.color_hex},name=${body.name})"
        failCreateFilament?.let { return it.toResponse() }
        val vendor = vendorList.firstOrNull { it.id == body.vendor_id }
        val filament = SpoolmanFilament(
            id = nextFilamentId++,
            name = body.name,
            material = body.material,
            vendor = vendor,
            color_hex = body.color_hex,
            settings_extruder_temp = body.settings_extruder_temp,
            settings_bed_temp = body.settings_bed_temp,
        )
        filamentList += filament
        return Response.success(filament)
    }

    override suspend fun createSpool(body: CreateSpoolRequest): Response<SpoolmanSpool> {
        callLog += "createSpool(filament=${body.filament_id},lot_nr=${body.lot_nr})"
        failCreateSpool?.let { return it.toResponse() }
        val filament = filamentList.firstOrNull { it.id == body.filament_id }
            ?: return Response.error(400, "missing filament".toResponseBody(null))
        val spool = SpoolmanSpool(
            id = nextSpoolId++,
            filament = filament,
            lot_nr = body.lot_nr,
        )
        spoolList += spool
        return Response.success(spool)
    }

    override suspend fun patchSpoolLotNr(
        spoolId: Int,
        body: UpdateSpoolLotNrRequest,
    ): Response<SpoolmanSpool> {
        callLog += "patchSpoolLotNr($spoolId,${body.lot_nr})"
        failPatchSpoolLotNr?.let { return it.toResponse() }
        val idx = spoolList.indexOfFirst { it.id == spoolId }
        if (idx < 0) return Response.error(404, "not found".toResponseBody(null))
        val updated = spoolList[idx].copy(lot_nr = body.lot_nr)
        spoolList[idx] = updated
        return Response.success(updated)
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
