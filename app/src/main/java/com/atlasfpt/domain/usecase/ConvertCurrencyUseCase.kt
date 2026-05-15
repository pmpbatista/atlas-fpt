package com.atlasfpt.domain.usecase

import com.atlasfpt.domain.model.FxRate
import javax.inject.Inject

/**
 * Stateless conversion helper. Pass in the current rate map; returns null when a leg
 * is missing so callers can degrade gracefully.
 *
 * EUR is implicit (not stored). Cross-currency conversion (e.g. USD → GBP) routes through EUR.
 */
class ConvertCurrencyUseCase @Inject constructor() {

    operator fun invoke(
        amount: Double,
        from: String,
        to: String,
        rates: Map<String, FxRate>,
    ): Double? {
        if (from == to) return amount
        val inEur = when (from) {
            EUR -> amount
            else -> rates[from]?.unitsPerEur?.let { perEur -> amount / perEur }
        } ?: return null
        return when (to) {
            EUR -> inEur
            else -> rates[to]?.unitsPerEur?.let { perEur -> inEur * perEur }
        }
    }

    private companion object {
        const val EUR = "EUR"
    }
}
