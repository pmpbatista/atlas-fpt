package com.spendtrack.util

import com.spendtrack.domain.model.TransactionType

object CurrencyFormatter {

    fun format(amount: Double, symbol: String, type: TransactionType): String {
        val formatted = formatAmount(amount)
        return if (type == TransactionType.EXPENSE) "-$symbol$formatted" else "$symbol$formatted"
    }

    fun formatAbsolute(amount: Double, symbol: String): String = "$symbol${formatAmount(amount)}"

    private fun formatAmount(amount: Double): String {
        return if (amount >= 1000) {
            // Portuguese thousands: 1.471,00
            val s = String.format("%,.2f", amount)  // "1,471.00" (US locale)
            s.replace(",", "X").replace(".", ",").replace("X", ".")
        } else {
            String.format("%.2f", amount).replace(".", ",")
        }
    }
}
