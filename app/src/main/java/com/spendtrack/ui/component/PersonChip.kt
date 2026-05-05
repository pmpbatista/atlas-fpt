package com.spendtrack.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spendtrack.domain.model.Person

@Composable
fun PersonChip(
    person: Person,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LabelChip(
        label = person.name,
        onRemove = onRemove,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}
