package com.spendtrack.di

import com.spendtrack.data.network.PriceSource
import com.spendtrack.data.network.YahooFinanceApi
import com.spendtrack.data.network.impl.YahooPriceSource
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT = "Mozilla/5.0 (compatible; SpendTrack/1.0)"
    private const val YAHOO_BASE_URL = "https://query1.finance.yahoo.com/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
            )
        }
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(YAHOO_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideYahooFinanceApi(retrofit: Retrofit): YahooFinanceApi =
        retrofit.create(YahooFinanceApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindings {
    @Binds
    @Singleton
    abstract fun bindPriceSource(impl: YahooPriceSource): PriceSource
}
