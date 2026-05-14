package com.atlasfpt.util

import com.atlasfpt.domain.model.TransactionType
import java.util.Currency
import java.util.Locale

object CurrencyFormatter {

    fun format(amount: Double, symbol: String, type: TransactionType): String {
        val formatted = formatAmount(amount)
        return if (type == TransactionType.EXPENSE) "-$symbol$formatted" else "$symbol$formatted"
    }

    fun formatAbsolute(amount: Double, symbol: String): String = "$symbol${formatAmount(amount)}"

    /**
     * Formats [amount] with the symbol resolved from a 3-letter ISO 4217 [currencyCode]
     * (e.g. "USD" -> "$", "EUR" -> "€"). Falls back to the code itself when JVM/Android
     * can't resolve the symbol on the device locale.
     */
    fun formatAbsoluteForCurrency(amount: Double, currencyCode: String): String {
        val symbol = runCatching {
            Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
        }.getOrDefault(currencyCode)
        return "$symbol${formatAmount(amount)}"
    }

    private fun formatAmount(amount: Double): String {
        return if (kotlin.math.abs(amount) >= 1000) {
            // Portuguese thousands: 1.471,00
            val s = String.format(Locale.US, "%,.2f", amount)
            s.replace(",", "X").replace(".", ",").replace("X", ".")
        } else {
            String.format(Locale.US, "%.2f", amount).replace(".", ",")
        }
    }
}
