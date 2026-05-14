package com.atlasfpt.ui.feature.assets.financial.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFinancialAssetScreen(
    navController: NavController,
    viewModel: AddFinancialAssetViewModel = hiltViewModel(),
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
                title = { Text("Add financial asset") },
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
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Ticker", style = MaterialTheme.typography.titleSmall) }
            item {
                Column {
                    OutlinedTextField(
                        value = state.ticker,
                        onValueChange = viewModel::onTicker,
                        label = { Text("Symbol (e.g. AAPL, VWCE.DE, BTC-USD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val ts = state.tickerState
                    when (ts) {
                        TickerState.Idle -> Unit
                        TickerState.Validating -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Checking…", style = MaterialTheme.typography.bodySmall)
                        }
                        is TickerState.Valid -> Text(
                            "✓ ${ts.quote.displayName} · ${ts.quote.currencyCode} · ${"%.2f".format(java.util.Locale.US, ts.quote.price)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        is TickerState.Invalid -> Text(ts.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is TickerState.Error -> Text(ts.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { TextRow("Name", state.name, state.formErrors.name, viewModel::onName) }
            item { Spacer(Modifier.height(8.dp)); Text("Purchase", style = MaterialTheme.typography.titleSmall) }
            item {
                DateRow(
                    label = "Date",
                    value = state.purchaseDate,
                    error = state.formErrors.purchaseDate,
                    onChange = viewModel::onPurchaseDate,
                )
            }
            item { NumberRow("Quantity", state.quantity, state.formErrors.quantity, viewModel::onQuantity) }
            item { NumberRow("Price/share (${state.currencyCode.ifBlank { "—" }})", state.pricePerUnit, state.formErrors.pricePerUnit, viewModel::onPricePerUnit) }
            item { Spacer(Modifier.height(8.dp)); Text("Notes", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(
                    value = state.notes, onValueChange = viewModel::onNotes,
                    label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun TextRow(label: String, value: String, error: String?, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) },
            isError = error != null, modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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

@Composable
private fun DateRow(label: String, value: LocalDate?, error: String?, onChange: (LocalDate?) -> Unit) {
    var raw by remember(value) { mutableStateOf(value?.toString() ?: "") }
    Column {
        OutlinedTextField(
            value = raw,
            onValueChange = {
                raw = it
                onChange(runCatching { LocalDate.parse(it) }.getOrNull())
            },
            label = { Text("$label (YYYY-MM-DD)") },
            isError = error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
