package com.atlasfpt.domain.model

import java.time.Instant

data class EuriborRate(
    val tenor: ReferenceRate,
    val value: Double,
    val asOf: Instant,
    val source: Source,
) {
    enum class Source { ECB, MANUAL }
}
