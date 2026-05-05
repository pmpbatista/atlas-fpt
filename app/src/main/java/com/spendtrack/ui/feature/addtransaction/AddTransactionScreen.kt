package com.spendtrack.ui.feature.addtransaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.spendtrack.domain.model.TransactionType
import com.spendtrack.ui.component.AmountDisplay
import com.spendtrack.ui.component.CategoryPickerBottomSheet
import com.spendtrack.ui.component.LabelChip
import com.spendtrack.ui.component.PersonChip
import com.spendtrack.ui.component.PersonPickerBottomSheet
import com.spendtrack.ui.theme.ExpenseColor
import com.spendtrack.ui.theme.IncomeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    transactionId: Long? = null,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val isEditMode = transactionId != null && transactionId != 0L

    LaunchedEffect(transactionId) {
        if (isEditMode) viewModel.loadTransaction(transactionId!!)
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) navController.popBackStack()
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) navController.popBackStack()
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditMode) "Edit Transaction" else "Add Transaction")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = viewModel::onDeleteRequested) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete transaction")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Type toggle
            TabRow(
                selectedTabIndex = if (uiState.transactionType == TransactionType.EXPENSE) 0 else 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Tab(
                    selected = uiState.transactionType == TransactionType.EXPENSE,
                    onClick = { viewModel.onTypeToggle(TransactionType.EXPENSE) },
                    text = { Text("Expense", color = if (uiState.transactionType == TransactionType.EXPENSE) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
                Tab(
                    selected = uiState.transactionType == TransactionType.INCOME,
                    onClick = { viewModel.onTypeToggle(TransactionType.INCOME) },
                    text = { Text("Income", color = if (uiState.transactionType == TransactionType.INCOME) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Amount display
            AmountDisplay(
                cents = uiState.amountCents,
                currencySymbol = uiState.settings.currencySymbol,
                isExpense = uiState.transactionType == TransactionType.EXPENSE
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Numpad
            Numpad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { viewModel.onShowCategoryPicker() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.selectedCategory?.name ?: "Select category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.selectedCategory != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showDatePicker = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("pt", "PT"))),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Note
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChanged,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Labels
            if (uiState.labels.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.labels.forEach { label ->
                        LabelChip(
                            label = label.name,
                            onRemove = { viewModel.onLabelRemoved(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Person chips
            if (uiState.persons.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    uiState.persons.forEach { person ->
                        PersonChip(
                            name = person.name,
                            onRemove = { viewModel.onPersonRemoved(person) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Link persons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { viewModel.onShowPersonPicker() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.persons.isEmpty()) "Link persons (optional)" else "Edit linked persons",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

        } // end scrollable content column

            // Save button
            Button(
                onClick = viewModel::save,
                enabled = uiState.selectedCategory != null && uiState.amountCents > 0 && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save")
            }
        }
    }

    if (uiState.showPersonPicker) {
        PersonPickerBottomSheet(
            allPersons = uiState.availablePersons,
            selectedPersons = uiState.persons,
            onToggle = { person ->
                if (uiState.persons.any { it.id == person.id }) viewModel.onPersonRemoved(person)
                else viewModel.onPersonAdded(person)
            },
            onDismiss = viewModel::onDismissPersonPicker
        )
    }

    if (uiState.showCategoryPicker) {
        val filtered = uiState.availableCategories.filter { cat ->
            cat.type == com.spendtrack.domain.model.CategoryType.BOTH ||
                cat.type.name == uiState.transactionType.name
        }
        CategoryPickerBottomSheet(
            categories = filtered,
            onSelect = viewModel::onCategorySelected,
            onDismiss = viewModel::onDismissCategoryPicker
        )
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = uiState.date,
            onDateSelected = { viewModel.onDateChanged(it) },
            onDismiss = { showDatePicker = false }
        )
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text("Delete transaction?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::delete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismissed) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onDateSelected(date)
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun Numpad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
        listOf(-1, 0, -2)  // -1 = empty, -2 = backspace
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (key == -1) Color.Transparent
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .then(
                                if (key != -1) Modifier.clickable {
                                    if (key == -2) onBackspace() else onDigit(key)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            -2 -> Text("⌫", style = MaterialTheme.typography.titleMedium)
                            -1 -> {}
                            else -> Text(
                                text = key.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
