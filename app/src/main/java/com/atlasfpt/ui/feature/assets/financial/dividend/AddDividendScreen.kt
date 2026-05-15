package com.atlasfpt.ui.feature.assets.financial.dividend

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
import com.atlasfpt.ui.component.DatePickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDividendScreen(
    navController: NavController,
    viewModel: AddDividendViewModel = hiltViewModel(),
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
                    Text(if (state.isEditMode) "Edit dividend · ${state.ticker}" else "Add dividend · ${state.ticker}")
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
            DatePickerField(
                label = "Pay date",
                value = state.payDate,
                onChange = viewModel::onPayDate,
                error = state.formErrors.payDate,
            )
            Column {
                OutlinedTextField(
                    value = state.grossAmount,
                    onValueChange = viewModel::onGrossAmount,
                    label = { Text("Gross amount (${state.currencyCode})") },
                    isError = state.formErrors.grossAmount != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.formErrors.grossAmount?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
