package com.atlasfpt.data.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET

interface EcbFxRatesApi {
    /** ECB euro reference rates — XML, ~33 currencies, daily refresh ~16:00 CET. No auth. */
    @GET("stats/eurofxref/eurofxref-daily.xml")
    suspend fun fetchDaily(): Response<ResponseBody>
}
