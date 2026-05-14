package com.atlasfpt.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsFilterChip(
    persons: List<Person>,
    selectedIds: Set<Long>,
    onSelectionChanged: (Set<Long>) -> Unit
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val label = when {
        selectedIds.isEmpty() -> "All people"
        selectedIds.size == 1 -> persons.firstOrNull { it.id in selectedIds }?.name ?: "1 person"
        else -> "${selectedIds.size} people"
    }

    AssistChip(
        onClick = { sheetOpen = true },
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
    )

    if (sheetOpen) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = state
        ) {
            Text(
                text = "Filter by person",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (persons.isEmpty()) {
                Text(
                    text = "No persons yet — add them in Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                persons.forEach { person ->
                    val isChecked = person.id in selectedIds
                    ListItem(
                        headlineContent = { Text(person.name) },
                        leadingContent = {
                            Icon(
                                imageVector = if (isChecked) Icons.Default.Check else Icons.Default.People,
                                contentDescription = if (isChecked) "Selected" else null,
                                tint = if (isChecked) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            val next = if (isChecked) selectedIds - person.id else selectedIds + person.id
                            onSelectionChanged(next)
                        }) {
                            Text(if (isChecked) "Remove" else "Add")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = {
                    onSelectionChanged(emptySet())
                }) {
                    Text("Clear filter")
                }
                TextButton(onClick = { sheetOpen = false }) {
                    Text("Done")
                }
            }
        }
    }
}
