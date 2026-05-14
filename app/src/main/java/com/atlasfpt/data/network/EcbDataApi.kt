package com.atlasfpt.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface EcbDataApi {
    @GET("service/data/FM/{key}")
    suspend fun fetchSeries(
        @Path("key") key: String,
        @Query("format") format: String = "csvdata",
        @Query("lastNObservations") lastN: Int = 1,
    ): Response<ResponseBody>
}
