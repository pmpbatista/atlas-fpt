package com.atlasfpt.ui.feature.assets.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlasfpt.domain.model.AssetListItem
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.util.CurrencyFormatter

@Composable
fun AssetListRow(item: AssetListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.photoUri != null) {
            AsyncImage(
                model = item.photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Icon(
                imageVector = when (item.type) {
                    AssetType.REAL_ESTATE -> Icons.Filled.HomeWork
                    AssetType.FINANCIAL -> Icons.AutoMirrored.Filled.ShowChart
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
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
