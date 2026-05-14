package com.atlasfpt.util

import com.atlasfpt.domain.model.InterestType
import com.atlasfpt.domain.model.RealEstateAsset
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Parses a decimal text input. Accepts both "." and "," as decimal separator
 * (Portuguese locale uses ","). Trims whitespace. Returns null on any other failure
 * — never throws into UI.
 */
fun parseDecimal(input: String): Double? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.replace(',', '.').toDoubleOrNull()
}

/**
 * Bucketed relative time: today / yesterday / N days / N months / over a year.
 */
fun relativeTimeString(epochMillis: Long, now: Instant = Instant.now()): String {
    val then = Instant.ofEpochMilli(epochMillis)
    val days = ChronoUnit.DAYS.between(then.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
        now.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 30L -> "$days days ago"
        days < 365L -> {
            val months = (days / 30L).toInt()
            if (months == 1) "1 month ago" else "$months months ago"
        }
        else -> "over a year ago"
    }
}

/**
 * Human-readable remaining time on a credit. "Credit ended" when [endDate] is in the past
 * or today.
 */
fun monthsRemaining(endDate: LocalDate, today: LocalDate = LocalDate.now()): String {
    val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), endDate.withDayOfMonth(1))
    return when {
        months <= 0L -> "Credit ended"
        months <= 12L -> if (months == 1L) "1 month remaining" else "$months months remaining"
        else -> {
            val years = months / 12L
            val rem = months % 12L
            if (rem == 0L) "$years years remaining"
            else "$years years $rem months remaining"
        }
    }
}

/**
 * Formats a percent with two decimals: 0.5 -> "0,50%", -0.2 -> "-0,20%".
 */
fun formatPercent(value: Double): String {
    val s = String.format(java.util.Locale.US, "%.2f", value).replace(".", ",")
    return "$s%"
}

private fun signedPercent(value: Double): String {
    return when {
        value > 0 -> "+ ${formatPercent(value)}"
        value < 0 -> "− ${formatPercent(-value)}"
        else -> formatPercent(0.0)
    }
}

fun describeInterest(asset: RealEstateAsset): String = when (asset.interestType) {
    null -> "Bought outright"
    InterestType.FIXED -> "Fixed ${formatPercent(asset.fixedRate ?: 0.0)}"
    InterestType.VARIABLE -> {
        val ref = asset.referenceRate?.label ?: "Variable"
        val spread = asset.spread ?: 0.0
        "$ref ${signedPercent(spread)}"
    }
}

/**
 * Formats a quantity with decimal precision adapted to magnitude.
 * Examples: 100.0 -> "100", 10.5 -> "10.5", 0.001234 -> "0.001234", 1.23e-7 -> "0.00000012"
 */
fun formatQuantity(qty: Double): String {
    val abs = kotlin.math.abs(qty)
    val raw = when {
        abs >= 100 -> String.format(java.util.Locale.US, "%.0f", qty)
        abs >= 1   -> String.format(java.util.Locale.US, "%.4f", qty).trimEnd('0').trimEnd('.')
        abs >= 0.001 -> String.format(java.util.Locale.US, "%.6f", qty).trimEnd('0').trimEnd('.')
        else -> String.format(java.util.Locale.US, "%.8f", qty).trimEnd('0').trimEnd('.')
    }
    return raw
}

/**
 * Formats a signed currency amount: "+ €123,45" or "− €50,00". The sign uses a Unicode
 * minus (U+2212) for symmetry with [signedPercent].
 */
fun formatSignedCurrency(amount: Double, currencyCode: String): String {
    val abs = kotlin.math.abs(amount)
    val formatted = com.atlasfpt.util.CurrencyFormatter.formatAbsoluteForCurrency(abs, currencyCode)
    val sign = if (amount < 0) "−" else "+"
    return "$sign $formatted"
}
