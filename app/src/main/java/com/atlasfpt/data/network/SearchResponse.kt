package com.atlasfpt.data.network

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(val quotes: List<SearchQuote> = emptyList())

@Serializable
data class SearchQuote(
    val symbol: String? = null,
    val shortname: String? = null,
    val longname: String? = null,
    val exchange: String? = null,
    val quoteType: String? = null,
    val typeDisp: String? = null,
)
