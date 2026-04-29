package com.spendtrack.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendtrack.ui.theme.ExpenseColor
import com.spendtrack.ui.theme.IncomeColor

@Composable
fun AmountDisplay(
    cents: Long,
    currencySymbol: String,
    isExpense: Boolean,
    modifier: Modifier = Modifier
) {
    val euros = cents / 100L
    val centPart = cents % 100L
    val color = if (isExpense) ExpenseColor else IncomeColor
    val sign = if (isExpense) "-" else "+"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = sign,
            fontSize = 24.sp,
            color = color,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "$currencySymbol${formatEuros(euros)}",
            fontSize = 48.sp,
            color = color
        )
        Column(modifier = Modifier.padding(bottom = 6.dp)) {
            Text(
                text = ",${centPart.toString().padStart(2, '0')}",
                fontSize = 24.sp,
                color = color
            )
        }
    }
}

private fun formatEuros(euros: Long): String {
    if (euros < 1000) return euros.toString()
    // Portuguese thousands separator: 1.471
    val s = euros.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.toString()
}
