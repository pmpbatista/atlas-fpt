package com.spendtrack.ui.feature.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.spendtrack.ui.theme.ExpenseColor
import com.spendtrack.ui.theme.IncomeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    navController: NavController,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import CSV") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.result != null -> {
                    val result = uiState.result!!
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Import Complete", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Imported: ${result.imported}",
                            color = IncomeColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (result.skipped > 0) {
                            Text(
                                "Skipped: ${result.skipped}",
                                color = ExpenseColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (result.errors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Errors:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            result.errors.take(5).forEach { error ->
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ExpenseColor
                                )
                            }
                            if (result.errors.size > 5) {
                                Text(
                                    "...and ${result.errors.size - 5} more",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::reset) { Text("Import Another") }
                        Button(onClick = { navController.popBackStack() }) { Text("Done") }
                    }
                }
                uiState.error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Import Failed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            uiState.error!!,
                            color = ExpenseColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = viewModel::reset) { Text("Try Again") }
                    }
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Import transactions from a CSV file.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Expected format:\ndate, amount, type, category, note\n\nSupported date formats:\nyyyy-MM-dd or dd/MM/yyyy\n\nTypes: expense/income",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { filePicker.launch("*/*") }) {
                            Text("Select CSV File")
                        }
                    }
                }
            }
        }
    }
}
