package com.atlasfpt.ui.feature.assets.financial.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atlasfpt.domain.model.FinancialAsset
import com.atlasfpt.domain.model.FinancialLot
import com.atlasfpt.domain.usecase.calculateAvgYearlyYield
import com.atlasfpt.ui.component.LinkedTransactionsSection
import com.atlasfpt.ui.navigation.Screen
import com.atlasfpt.util.CurrencyFormatter
import com.atlasfpt.util.formatPercent
import com.atlasfpt.util.formatQuantity
import com.atlasfpt.util.formatSignedCurrency
import com.atlasfpt.util.relativeTimeString
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialDetailScreen(
    navController: NavController,
    viewModel: FinancialDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteAssetDialog by remember { mutableStateOf(false) }
    var lotPendingDelete by remember { mutableStateOf<FinancialLot?>(null) }

    LaunchedEffect(state.isDeleted) { if (state.isDeleted) navController.popBackStack() }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.asset?.name ?: "Asset")
                        state.asset?.let {
                            Text("${it.ticker} · ${it.currencyCode}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.asset?.let { a ->
                        IconButton(onClick = {
                            val uri = Uri.parse("https://finance.yahoo.com/quote/${a.ticker}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Yahoo") }
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh price")
                            }
                        }
                        IconButton(onClick = { showDeleteAssetDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete asset")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loadError -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load asset")
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { navController.popBackStack() }) { Text("Go back") }
                }
            }
            state.asset == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> {
                val asset = state.asset!!
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { AggregatedStatsCard(asset) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lots (${asset.lots.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                navController.navigate(Screen.AddLot.createRoute(asset.id))
                            }) { Text("+ Add") }
                        }
                    }
                    items(asset.lots, key = { it.id }) { lot ->
                        LotRow(
                            lot = lot,
                            currentPrice = asset.latestPrice,
                            currencyCode = asset.currencyCode,
                            onEdit = { navController.navigate(Screen.AddLot.createRouteEdit(asset.id, lot.id)) },
                            onDelete = { lotPendingDelete = lot },
                        )
                    }
                    asset.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        item { Spacer(Modifier.height(8.dp)); Text("Notes", style = MaterialTheme.typography.titleSmall); Text(notes) }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        LinkedTransactionsSection(
                            transactions = state.linkedTransactions,
                            currencySymbol = state.currencySymbol,
                            onRowClick = { id ->
                                navController.navigate("edit_transaction/$id")
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteAssetDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAssetDialog = false },
            title = { Text("Delete asset?") },
            text = { Text("This will delete the asset and all its lots. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAssetDialog = false
                    viewModel.deleteAsset()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAssetDialog = false }) { Text("Cancel") } },
        )
    }

    lotPendingDelete?.let { lot ->
        val isLast = state.asset?.lots?.size == 1
        AlertDialog(
            onDismissRequest = { lotPendingDelete = null },
            title = { Text(if (isLast) "Delete last lot?" else "Delete lot?") },
            text = { if (isLast) Text("This will delete the asset entirely.") else Text("This lot will be removed from the asset.") },
            confirmButton = {
                TextButton(onClick = {
                    lotPendingDelete = null
                    viewModel.deleteLot(lot.id)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { lotPendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AggregatedStatsCard(asset: FinancialAsset) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current value", style = MaterialTheme.typography.labelMedium)
            Text(
                asset.currentValue?.let { CurrencyFormatter.formatAbsoluteForCurrency(it, asset.currencyCode) } ?: "—",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            asset.unrealizedPnl?.let { pnl ->
                val pct = asset.unrealizedPnlPct ?: 0.0
                val color = if (pnl >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text(
                    "${formatSignedCurrency(pnl, asset.currencyCode)} (${formatPercent(pct * 100)})",
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            calculateAvgYearlyYield(asset.lots, asset.latestPrice)?.let { yld ->
                val color = if (yld >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Text("${formatPercent(yld * 100)} per year (CAGR)", color = color, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatQuantity(asset.totalQuantity)} units @ avg ${CurrencyFormatter.formatAbsoluteForCurrency(asset.avgCostPerUnit, asset.currencyCode)}",
                style = MaterialTheme.typography.bodySmall,
            )
            asset.latestPriceAt?.let { Text("Updated ${relativeTimeString(it.toEpochMilli())}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LotRow(
    lot: FinancialLot,
    currentPrice: Double?,
    currencyCode: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { sheetOpen = true }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${lot.purchaseDate} · ${formatQuantity(lot.quantity)} @ ${CurrencyFormatter.formatAbsoluteForCurrency(lot.pricePerUnit, currencyCode)}",
                style = MaterialTheme.typography.bodyMedium)
            val cost = lot.quantity * lot.pricePerUnit
            val cur = currentPrice?.let { lot.quantity * it }
            val text = buildString {
                append("Cost ${CurrencyFormatter.formatAbsoluteForCurrency(cost, currencyCode)}")
                cur?.let {
                    append(" · Now ${CurrencyFormatter.formatAbsoluteForCurrency(it, currencyCode)}")
                    val pct = (it - cost) / cost
                    append(" · ${formatPercent(pct * 100)}")
                }
            }
            Text(text, style = MaterialTheme.typography.bodySmall)
            currentPrice?.let { cp ->
                val years = ChronoUnit.DAYS.between(lot.purchaseDate, LocalDate.now()) / 365.25
                if (years > 0 && lot.pricePerUnit > 0) {
                    val ratio = cp / lot.pricePerUnit
                    val annualized = if (ratio > 0) ratio.pow(1.0 / years) - 1 else -1.0
                    Text("${formatPercent(annualized * 100)} per year", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (sheetOpen) {
        AlertDialog(
            onDismissRequest = { sheetOpen = false },
            title = { Text("Lot actions") },
            text = { Text("Edit or delete this lot?") },
            confirmButton = { TextButton(onClick = { sheetOpen = false; onEdit() }) { Text("Edit") } },
            dismissButton = { TextButton(onClick = { sheetOpen = false; onDelete() }) { Text("Delete") } },
        )
    }
}
