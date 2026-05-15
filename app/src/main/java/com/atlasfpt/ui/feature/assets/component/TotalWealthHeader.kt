package com.atlasfpt.ui.feature.assets.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.domain.model.TotalWealth
import com.atlasfpt.util.CurrencyFormatter

@Composable
fun TotalWealthHeader(
    total: TotalWealth,
    lastRefreshAt: Long? = null,
    modifier: Modifier = Modifier,
) {
    if (total.isEmpty) return
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Net wealth", style = MaterialTheme.typography.labelMedium)

        val converted = total.totalInDisplayCurrency
        if (converted != null && total.isMixedCurrency) {
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(converted, total.displayCurrencyCode),
                style = MaterialTheme.typography.headlineMedium
            )
            total.byCurrency.forEach { (code, value) ->
                Text(
                    CurrencyFormatter.formatAbsoluteForCurrency(value, code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            total.byCurrency.forEach { (code, value) ->
                Text(
                    CurrencyFormatter.formatAbsoluteForCurrency(value, code),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }

        val subtitle = if (total.isMixedCurrency)
            "Across ${total.assetCount} assets · mixed currencies"
        else
            "Across ${total.assetCount} assets"
        Text(subtitle, style = MaterialTheme.typography.bodySmall)

        if (total.isMixedCurrency) {
            val fxAt = total.fxFetchedAt
            val fxLine = when {
                converted != null && fxAt != null ->
                    "FX as of ${formatRelativeAge(fxAt)}"
                converted == null ->
                    "FX rates unavailable — refresh prices to update"
                else -> null
            }
            if (fxLine != null) {
                Text(
                    fxLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (lastRefreshAt != null) {
            Text(
                "Prices updated ${formatRelativeAge(lastRefreshAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val typesPresent = AssetType.values().filter { total.hasType(it) }
        if (typesPresent.size >= 2) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                typesPresent.forEach { type ->
                    SubtotalCard(
                        label = typeLabel(type),
                        byCurrency = total.byCurrencyForType(type),
                        count = total.countByType[type] ?: 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtotalCard(
    label: String,
    byCurrency: Map<String, Double>,
    count: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            byCurrency.forEach { (code, value) ->
                Text(
                    CurrencyFormatter.formatAbsoluteForCurrency(value, code),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            val subtitle = if (byCurrency.size > 1) "$count assets · mixed" else "$count assets"
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun typeLabel(type: AssetType): String = when (type) {
    AssetType.FINANCIAL -> "Financial"
    AssetType.REAL_ESTATE -> "Real Estate"
}

private fun formatRelativeAge(thenMillis: Long): String {
    val deltaSeconds = ((System.currentTimeMillis() - thenMillis) / 1000L).coerceAtLeast(0L)
    return when {
        deltaSeconds < 60 -> "just now"
        deltaSeconds < 3600 -> "${deltaSeconds / 60}m ago"
        deltaSeconds < 86_400 -> "${deltaSeconds / 3600}h ago"
        else -> "${deltaSeconds / 86_400}d ago"
    }
}
