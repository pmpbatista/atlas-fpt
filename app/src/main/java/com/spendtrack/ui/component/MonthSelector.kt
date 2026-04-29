package com.spendtrack.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthSelector(
    yearMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Previous month",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable(onClick = onPrevious)
                .padding(8.dp)
        )
        Text(
            text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "PT"))} ${yearMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Next month",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable(onClick = onNext)
                .padding(8.dp)
        )
    }
}
