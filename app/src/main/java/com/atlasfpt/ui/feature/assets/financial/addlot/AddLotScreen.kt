package com.atlasfpt.ui.feature.assets.financial.addlot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atlasfpt.domain.model.LotType
import com.atlasfpt.ui.component.DatePickerField

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddLotScreen(
    navController: NavController,
    viewModel: AddLotViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) { if (state.isSaved) navController.popBackStack() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it); viewModel.clearErrorMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    val prefix = if (state.isEditMode) "Edit" else when (state.lotType) {
                        LotType.BUY -> "Add buy"
                        LotType.SELL -> "Record sale"
                    }
                    Text("$prefix · ${state.ticker}")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.formErrors.hasAny && !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.lotType == LotType.BUY,
                    onClick = { viewModel.onLotType(LotType.BUY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Buy") }
                SegmentedButton(
                    selected = state.lotType == LotType.SELL,
                    onClick = { viewModel.onLotType(LotType.SELL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Sell") }
            }
            DatePickerField(
                label = "Date",
                value = state.purchaseDate,
                onChange = viewModel::onPurchaseDate,
                error = state.formErrors.purchaseDate,
            )
            NumberRow("Quantity", state.quantity, state.formErrors.quantity, viewModel::onQuantity)
            val priceLabel = when (state.lotType) {
                LotType.BUY -> "Price/share (${state.currencyCode})"
                LotType.SELL -> "Sale price/share (${state.currencyCode})"
            }
            NumberRow(priceLabel, state.pricePerUnit, state.formErrors.pricePerUnit, viewModel::onPricePerUnit)
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) },
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

