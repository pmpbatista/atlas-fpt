package com.atlasfpt.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface YahooFinanceApi {
    @GET("v8/finance/chart/{ticker}")
    suspend fun getChart(
        @Path("ticker") ticker: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "1d",
    ): Response<ChartResponse>

    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 10,
        @Query("newsCount") newsCount: Int = 0,
    ): Response<SearchResponse>
}
