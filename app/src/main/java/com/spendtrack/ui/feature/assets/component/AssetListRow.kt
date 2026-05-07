package com.spendtrack.ui.feature.assets.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendtrack.domain.model.AssetListItem
import com.spendtrack.domain.model.AssetType
import com.spendtrack.util.CurrencyFormatter

@Composable
fun AssetListRow(item: AssetListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (item.type) {
                AssetType.REAL_ESTATE -> Icons.Filled.HomeWork
                AssetType.FINANCIAL -> Icons.AutoMirrored.Filled.ShowChart
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(item.equity, item.currencyCode),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "Value " + CurrencyFormatter.formatAbsoluteForCurrency(item.currentValue, item.currencyCode),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
