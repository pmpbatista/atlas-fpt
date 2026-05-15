package com.atlasfpt.ui.feature.assets.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atlasfpt.domain.model.AssetType
import com.atlasfpt.ui.component.ChartRangeChips
import com.atlasfpt.ui.component.LineChart
import com.atlasfpt.ui.feature.assets.component.AssetListRow
import com.atlasfpt.ui.feature.assets.component.TotalWealthHeader
import com.atlasfpt.ui.feature.assets.typepicker.AssetTypePickerSheet
import com.atlasfpt.ui.navigation.Screen
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsListScreen(
    navController: NavController,
    viewModel: AssetsListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTypePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.refreshMessage) {
        state.refreshMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearRefreshMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Assets") },
                actions = {
                    if (state.hasFinancial) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh prices")
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTypePicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add asset")
            }
        }
    ) { padding ->
        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No assets yet\nTap + to add one",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item { TotalWealthHeader(total = state.total, lastRefreshAt = state.settings.lastPriceRefreshAt) }
                if (state.hasFinancial) {
                    item {
                        Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Portfolio value",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ChartRangeChips(
                                        selected = state.chartRange,
                                        onSelected = viewModel::onChartRange,
                                    )
                                }
                                if (state.isChartLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(160.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { CircularProgressIndicator() }
                                } else if (state.portfolioHistory.isEmpty()) {
                                    Text(
                                        "No history yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 16.dp),
                                    )
                                } else {
                                    LineChart(points = state.portfolioHistory)
                                }
                            }
                        }
                    }
                }
                items(state.items, key = { it.id }) { asset ->
                    AssetListRow(
                        item = asset,
                        onClick = {
                            when (asset.type) {
                                AssetType.REAL_ESTATE ->
                                    navController.navigate(Screen.RealEstateDetail.createRoute(asset.id))
                                AssetType.FINANCIAL ->
                                    navController.navigate(Screen.FinancialDetail.createRoute(asset.id))
                            }
                        }
                    )
                }
            }
        }
    }

    if (showTypePicker) {
        AssetTypePickerSheet(
            onDismiss = { showTypePicker = false },
            onRealEstate = {
                showTypePicker = false
                navController.navigate(Screen.AddRealEstate.route)
            },
            onFinancial = {
                showTypePicker = false
                navController.navigate(Screen.AddFinancialAsset.route)
            },
        )
    }
}
