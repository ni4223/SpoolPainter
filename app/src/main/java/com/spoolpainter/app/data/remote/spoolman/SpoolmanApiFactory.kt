package com.spoolpainter.app.data.remote.spoolman

import com.google.gson.Gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SpoolmanApiFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    open fun create(baseUrl: String): SpoolmanApi {
        val normalised = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalised)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SpoolmanApi::class.java)
    }
}
