package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.ExternalFilament
import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanTag
import com.spoolpainter.app.domain.models.SpoolmanVendor
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SpoolmanApi {

    @GET("api/v1/info")
    suspend fun getInfo(): Response<SpoolmanInfo>

    /** SpoolmanDB search (server ≥ 0.26.0; offset paging ≥ 0.27.0). Raw
     *  [Response] so callers can read the `x-total-count` header. */
    @GET("api/v1/external/filament/search")
    suspend fun searchExternalFilaments(
        @Query("query") query: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Response<List<ExternalFilament>>

    @GET("api/v1/spool")
    suspend fun listSpools(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("allow_archived") allowArchived: Boolean? = null,
        @Query("tag") tag: String? = null,
    ): Response<List<SpoolmanSpool>>

    @GET("api/v1/spool/{id}")
    suspend fun getSpool(@Path("id") spoolId: Int): Response<SpoolmanSpool>

    @GET("api/v1/filament")
    suspend fun listFilaments(): Response<List<SpoolmanFilament>>

    @GET("api/v1/filament/{id}")
    suspend fun getFilament(@Path("id") filamentId: Int): Response<SpoolmanFilament>

    @GET("api/v1/vendor")
    suspend fun listVendors(): Response<List<SpoolmanVendor>>

    @POST("api/v1/vendor")
    suspend fun createVendor(@Body body: CreateVendorRequest): Response<SpoolmanVendor>

    @DELETE("api/v1/vendor/{id}")
    suspend fun deleteVendor(@Path("id") vendorId: Int): Response<Unit>

    @POST("api/v1/filament")
    suspend fun createFilament(@Body body: CreateFilamentRequest): Response<SpoolmanFilament>

    @PATCH("api/v1/filament/{id}")
    suspend fun patchFilament(
        @Path("id") filamentId: Int,
        @Body body: PatchFilamentBody,
    ): Response<SpoolmanFilament>

    @DELETE("api/v1/filament/{id}")
    suspend fun deleteFilament(@Path("id") filamentId: Int): Response<Unit>

    @POST("api/v1/spool")
    suspend fun createSpool(@Body body: CreateSpoolRequest): Response<SpoolmanSpool>

    @PATCH("api/v1/spool/{id}")
    suspend fun patchSpool(
        @Path("id") spoolId: Int,
        @Body body: SpoolPatchBody,
    ): Response<SpoolmanSpool>

    @DELETE("api/v1/spool/{id}")
    suspend fun deleteSpool(@Path("id") spoolId: Int): Response<Unit>

    /** Native tags (server ≥ 0.27.0). Linking a UID the same spool already
     *  holds is idempotent (201 again); a UID linked to ANOTHER spool or a
     *  filament yields 409 with a JSON conflict body (message + spool_id/
     *  filament_id of the holder). UIDs are globally unique across spools. */
    @POST("api/v1/spool/{id}/tag")
    suspend fun linkTag(
        @Path("id") spoolId: Int,
        @Body body: TagLinkBody,
    ): Response<SpoolmanTag>

    /** Native tags (server ≥ 0.27.0). 404 when the spool is unknown or does
     *  not hold this UID. The server normalizes the UID path segment (case,
     *  separators, 0x prefix), so callers may pass any spelling. */
    @DELETE("api/v1/spool/{id}/tag/{uid}")
    suspend fun unlinkTag(
        @Path("id") spoolId: Int,
        @Path("uid") uid: String,
    ): Response<Unit>

    @GET("api/v1/field/{entityType}")
    suspend fun listFields(@Path("entityType") entityType: String): Response<List<ExtraFieldDef>>

    @POST("api/v1/field/{entityType}/{key}")
    suspend fun postField(
        @Path("entityType") entityType: String,
        @Path("key") key: String,
        @Body body: ExtraFieldDef,
    ): Response<ExtraFieldDef>
}
