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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPickerBottomSheet(
    allPersons: List<Person>,
    selectedPersons: List<Person>,
    onToggle: (Person) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "Link persons",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            if (allPersons.isEmpty()) {
                Text(
                    text = "No persons yet. Add them in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(allPersons, key = { it.id }) { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(person) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedPersons.any { it.id == person.id },
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = person.name)
                        }
                    }
                }
            }
            Text(
                text = "Persons managed in Settings",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
