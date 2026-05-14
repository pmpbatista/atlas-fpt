package com.atlasfpt.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.AssetListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetPickerBottomSheet(
    allAssets: List<AssetListItem>,
    selectedAssetId: Long?,
    onSelect: (AssetListItem?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "Link asset",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            if (allAssets.isEmpty()) {
                Text(
                    text = "No assets yet. Add them under Assets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn {
                    item {
                        AssetRow(
                            label = "None",
                            sublabel = null,
                            selected = selectedAssetId == null,
                            onClick = { onSelect(null) },
                        )
                        HorizontalDivider()
                    }
                    items(allAssets, key = { it.id }) { asset ->
                        AssetRow(
                            label = asset.name,
                            sublabel = "${asset.type.name.lowercase().replaceFirstChar { it.uppercase() }} · ${asset.currencyCode}",
                            selected = selectedAssetId == asset.id,
                            onClick = { onSelect(asset) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetRow(
    label: String,
    sublabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
