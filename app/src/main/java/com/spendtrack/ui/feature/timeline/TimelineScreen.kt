package com.spendtrack.ui.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.spendtrack.domain.model.Transaction
import com.spendtrack.domain.usecase.TimelineItem
import com.spendtrack.ui.component.TransactionRow
import com.spendtrack.ui.navigation.Screen
import com.spendtrack.ui.theme.DateHeaderBackground
import com.spendtrack.ui.theme.ExpenseColor
import com.spendtrack.ui.theme.IncomeColor
import com.spendtrack.util.CurrencyFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimelineScreen(
    navController: NavController,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.pendingDelete) {
        val tx = uiState.pendingDelete ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = "Transaction deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddTransaction.route) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val data = uiState.timelineData
        val symbol = uiState.settings.currencySymbol

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                CashFlowHeader(
                    totalCashFlow = data?.totalCashFlow ?: 0.0,
                    currencySymbol = symbol
                )
            }

            val items = data?.timelineItems ?: emptyList()
            items(items, key = { item ->
                when (item) {
                    is TimelineItem.DateHeader -> "header_${item.date}"
                    is TimelineItem.TransactionRow -> "tx_${item.transaction.id}"
                }
            }) { item ->
                when (item) {
                    is TimelineItem.DateHeader -> DateHeader(item, symbol)
                    is TimelineItem.TransactionRow -> {
                        val tx = item.transaction
                        val isPending = uiState.pendingDelete?.id == tx.id
                        if (!isPending) {
                            TransactionRow(
                                transaction = tx,
                                currencySymbol = symbol,
                                onClick = {
                                    navController.navigate(Screen.EditTransaction.createRoute(tx.id))
                                },
                                onDelete = { viewModel.requestDelete(tx) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CashFlowHeader(totalCashFlow: Double, currencySymbol: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Cash Flow",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val color = if (totalCashFlow >= 0) IncomeColor else ExpenseColor
        Text(
            text = CurrencyFormatter.formatAbsolute(kotlin.math.abs(totalCashFlow), currencySymbol),
            style = MaterialTheme.typography.headlineMedium,
            color = color
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DateHeader(item: TimelineItem.DateHeader, currencySymbol: String) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale("pt", "PT"))
    val color = if (item.dailyTotal >= 0) IncomeColor else ExpenseColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DateHeaderBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.date.format(formatter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = CurrencyFormatter.formatAbsolute(kotlin.math.abs(item.dailyTotal), currencySymbol),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
