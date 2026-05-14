package com.atlasfpt.ui.feature.assets.realestate.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.atlasfpt.domain.model.EnergyRating
import com.atlasfpt.domain.model.InterestType
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.ui.component.DatePickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRealEstateScreen(
    navController: NavController,
    viewModel: AddEditRealEstateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved, state.isDeleted) {
        if (state.isSaved || state.isDeleted) navController.popBackStack()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            title = { Text("Delete property?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmation() }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditMode) "Edit property" else "Add property") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEditMode) {
                        IconButton(onClick = { viewModel.showDeleteConfirmation() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.formErrors.hasAny && !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                PhotoSection(
                    photoUri = state.photoUri,
                    onPick = viewModel::onPickPhoto,
                    onRemove = viewModel::onRemovePhoto,
                )
            }
            item { Text("Basics", style = MaterialTheme.typography.titleSmall) }
            item { TextRow("Name", state.name, state.formErrors.name, viewModel::onName) }
            item {
                CurrencyDropdown(
                    value = state.currencyCode,
                    enabled = !state.isEditMode,
                    onChange = viewModel::onCurrencyCode
                )
            }
            item {
                DatePickerField(
                    label = "Purchase date",
                    value = state.purchaseDate,
                    onChange = viewModel::onPurchaseDate,
                    error = state.formErrors.purchaseDate,
                )
            }
            item { NumberRow("Current value", state.currentValue, state.formErrors.currentValue, viewModel::onCurrentValue) }

            item { Spacer(Modifier.height(8.dp)); Text("Money", style = MaterialTheme.typography.titleSmall) }
            item { NumberRow("Cost", state.cost, state.formErrors.cost, viewModel::onCost) }
            item { NumberRow("Invested capital", state.investedCapital, state.formErrors.investedCapital, viewModel::onInvestedCapital) }
            item { NumberRow("Debt amount (leave blank if cash)", state.debtAmount, state.formErrors.debtAmount, viewModel::onDebtAmount) }

            if (state.debtAmount.isNotBlank()) {
                item { Spacer(Modifier.height(8.dp)); Text("Debt details", style = MaterialTheme.typography.titleSmall) }
                item { NumberRow("Outstanding debt", state.outstandingDebt, state.formErrors.outstandingDebt, viewModel::onOutstandingDebt) }
                item {
                    InterestTypeRow(
                        selected = state.interestType,
                        error = state.formErrors.interestType,
                        onChange = viewModel::onInterestType
                    )
                }
                if (state.interestType == InterestType.FIXED) {
                    item { NumberRow("Fixed rate (%)", state.fixedRate, state.formErrors.fixedRate, viewModel::onFixedRate) }
                }
                if (state.interestType == InterestType.VARIABLE) {
                    item {
                        ReferenceRateDropdown(
                            value = state.referenceRate,
                            error = state.formErrors.referenceRate,
                            onChange = viewModel::onReferenceRate
                        )
                    }
                    item { NumberRow("Spread (%)", state.spread, state.formErrors.spread, viewModel::onSpread) }
                }
                item {
                    DatePickerField(
                        label = "Credit end date",
                        value = state.creditEndDate,
                        onChange = viewModel::onCreditEndDate,
                        error = state.formErrors.creditEndDate,
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)); Text("Property", style = MaterialTheme.typography.titleSmall) }
            item { TextRow("District", state.district, state.formErrors.district, viewModel::onDistrict) }
            item { TextRow("Council", state.council, state.formErrors.council, viewModel::onCouncil) }
            item { TextRow("Parish", state.parish, state.formErrors.parish, viewModel::onParish) }
            item { NumberRow("Size (m²)", state.sizeM2, state.formErrors.sizeM2, viewModel::onSizeM2) }
            item {
                EnergyRatingDropdown(
                    value = state.energyRating,
                    onChange = viewModel::onEnergyRating
                )
            }

            item { Spacer(Modifier.height(8.dp)); Text("Notes", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotes,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { Spacer(Modifier.height(80.dp)) } // breathing room above bottom bar
        }
    }
}

@Composable
private fun TextRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun NumberRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    val options = listOf("EUR", "USD", "GBP", "BRL", "CHF", "CAD", "AUD", "JPY")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = { onChange(code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun InterestTypeRow(
    selected: InterestType?,
    error: String?,
    onChange: (InterestType) -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == InterestType.FIXED,
                onClick = { onChange(InterestType.FIXED) },
                label = { Text("Fixed") }
            )
            FilterChip(
                selected = selected == InterestType.VARIABLE,
                onClick = { onChange(InterestType.VARIABLE) },
                label = { Text("Variable") }
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferenceRateDropdown(
    value: ReferenceRate?,
    error: String?,
    onChange: (ReferenceRate?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = value?.label ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Reference rate") },
                isError = error != null,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReferenceRate.values().forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.label) },
                        onClick = { onChange(rate); expanded = false }
                    )
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnergyRatingDropdown(
    value: EnergyRating,
    onChange: (EnergyRating) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Energy rating") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EnergyRating.values().forEach { rating ->
                DropdownMenuItem(
                    text = { Text(rating.label) },
                    onClick = { onChange(rating); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun PhotoSection(
    photoUri: String?,
    onPick: (Uri) -> Unit,
    onRemove: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onPick) },
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Photo", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (photoUri != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Property photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove photo",
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    launcher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Replace photo") }
        } else {
            OutlinedButton(
                onClick = {
                    launcher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add photo")
            }
        }
    }
}
