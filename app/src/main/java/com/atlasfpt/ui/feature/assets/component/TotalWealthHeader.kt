package com.atlasfpt.ui.feature.assets.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.TotalWealth
import com.atlasfpt.util.CurrencyFormatter

@Composable
fun TotalWealthHeader(total: TotalWealth, modifier: Modifier = Modifier) {
    if (total.isEmpty) return
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Net wealth", style = MaterialTheme.typography.labelMedium)
        total.byCurrency.forEach { (code, value) ->
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(value, code),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        val subtitle = if (total.isMixedCurrency)
            "Across ${total.assetCount} assets · mixed currencies"
        else
            "Across ${total.assetCount} assets"
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}
