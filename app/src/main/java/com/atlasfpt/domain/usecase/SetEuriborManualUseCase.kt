package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.EuriborRepository
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.ReferenceRate
import java.time.Instant
import javax.inject.Inject

class SetEuriborManualUseCase @Inject constructor(
    private val repository: EuriborRepository,
) {
    operator fun invoke(tenor: ReferenceRate, value: Double, asOf: Instant) {
        repository.put(
            EuriborRate(tenor = tenor, value = value, asOf = asOf, source = EuriborRate.Source.MANUAL),
        )
    }
}
