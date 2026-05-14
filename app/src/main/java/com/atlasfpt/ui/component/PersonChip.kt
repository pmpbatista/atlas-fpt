package com.atlasfpt.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PersonChip(
    name: String,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LabelChip(
        label = name,
        onRemove = onRemove,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}
