package com.spendtrack.ui.feature.assets.typepicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetTypePickerSheet(
    onDismiss: () -> Unit,
    onRealEstate: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                "Add asset",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
            ListItem(
                headlineContent = { Text("Real Estate") },
                leadingContent = { Icon(Icons.Filled.HomeWork, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onRealEstate)
            )
            ListItem(
                headlineContent = {
                    Text(
                        "Financial",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                supportingContent = { Text("Coming soon") },
                leadingContent = {
                    Icon(
                        Icons.Filled.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // not clickable: disabled in this spec
            )
        }
    }
}
