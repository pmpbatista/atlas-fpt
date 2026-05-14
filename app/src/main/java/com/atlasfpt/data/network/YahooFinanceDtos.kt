package com.atlasfpt.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Top-level Yahoo /v8/finance/chart response. We only deserialize the fields we use. */
@Serializable
data class ChartResponse(
    val chart: Chart,
)

@Serializable
data class Chart(
    val result: List<ChartResult>? = null,
    val error: ChartError? = null,
)

@Serializable
data class ChartResult(
    val meta: ChartMeta,
)

@Serializable
data class ChartMeta(
    val symbol: String,
    val currency: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val regularMarketPrice: Double? = null,
    val regularMarketTime: Long? = null,
)

@Serializable
data class ChartError(
    val code: String,
    val description: String? = null,
)
