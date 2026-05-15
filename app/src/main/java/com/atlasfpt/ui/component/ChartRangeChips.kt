package com.atlasfpt.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.ChartRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartRangeChips(
    selected: ChartRange,
    onSelected: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChartRange.values().forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelected(range) },
                label = { Text(range.label) },
            )
        }
    }
}
