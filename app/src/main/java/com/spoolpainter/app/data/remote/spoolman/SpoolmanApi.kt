package com.spoolpainter.app.data.remote.spoolman

import com.spoolpainter.app.domain.models.SpoolmanFilament
import com.spoolpainter.app.domain.models.SpoolmanInfo
import com.spoolpainter.app.domain.models.SpoolmanSpool
import com.spoolpainter.app.domain.models.SpoolmanVendor
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SpoolmanApi {

    @GET("api/v1/info")
    suspend fun getInfo(): Response<SpoolmanInfo>

    @GET("api/v1/spool")
    suspend fun listSpools(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("allow_archived") allowArchived: Boolean? = null,
    ): Response<List<SpoolmanSpool>>

    @GET("api/v1/spool/{id}")
    suspend fun getSpool(@Path("id") spoolId: Int): Response<SpoolmanSpool>

    @GET("api/v1/filament")
    suspend fun listFilaments(): Response<List<SpoolmanFilament>>

    @GET("api/v1/vendor")
    suspend fun listVendors(): Response<List<SpoolmanVendor>>

    @POST("api/v1/vendor")
    suspend fun createVendor(@Body body: CreateVendorRequest): Response<SpoolmanVendor>

    @POST("api/v1/filament")
    suspend fun createFilament(@Body body: CreateFilamentRequest): Response<SpoolmanFilament>

    @POST("api/v1/spool")
    suspend fun createSpool(@Body body: CreateSpoolRequest): Response<SpoolmanSpool>

    @PATCH("api/v1/spool/{id}")
    suspend fun patchSpool(
        @Path("id") spoolId: Int,
        @Body body: SpoolPatchBody,
    ): Response<SpoolmanSpool>

    @GET("api/v1/field/{entityType}")
    suspend fun listFields(@Path("entityType") entityType: String): Response<List<ExtraFieldDef>>

    @POST("api/v1/field/{entityType}/{key}")
    suspend fun postField(
        @Path("entityType") entityType: String,
        @Path("key") key: String,
        @Body body: ExtraFieldDef,
    ): Response<ExtraFieldDef>
}
