package com.spendtrack.ui.feature.assets.realestate.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.spendtrack.domain.model.RealEstateAsset
import com.spendtrack.ui.navigation.Screen
import com.spendtrack.util.CurrencyFormatter
import com.spendtrack.util.describeInterest
import com.spendtrack.util.monthsRemaining
import com.spendtrack.util.relativeTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealEstateDetailScreen(
    navController: NavController,
    viewModel: RealEstateDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.asset?.name ?: "Property") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.asset?.let { a ->
                        IconButton(onClick = {
                            navController.navigate(Screen.EditRealEstate.createRoute(a.id))
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loadError -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Couldn't load property")
            }
            state.asset == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading…")
            }
            else -> {
                val asset = state.asset!!
                val equity = state.equity!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                ) {
                    item { ValuationCard(asset, equity) }
                    item { Spacer(Modifier.height(16.dp)); PurchaseSummary(asset) }
                    item { Spacer(Modifier.height(16.dp)); DebtSection(asset) }
                    item { Spacer(Modifier.height(16.dp)); PropertyDetails(asset) }
                    asset.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        item { Spacer(Modifier.height(16.dp)); NotesSection(notes) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuationCard(asset: RealEstateAsset, equity: Double) {
    Card(modifier = Modifier.padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current value", style = MaterialTheme.typography.labelMedium)
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(asset.currentValue, asset.currencyCode),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(4.dp))
            Text("Equity", style = MaterialTheme.typography.labelSmall)
            Text(
                CurrencyFormatter.formatAbsoluteForCurrency(equity, asset.currencyCode),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Updated ${relativeTimeString(asset.currentValueUpdatedAt.toEpochMilli())}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PurchaseSummary(asset: RealEstateAsset) {
    Column {
        Text("Purchase", style = MaterialTheme.typography.titleSmall)
        Text("Date: ${asset.purchaseDate}")
        Text("Cost: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.cost, asset.currencyCode)}")
        Text("Invested capital: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.investedCapital, asset.currencyCode)}")
    }
}

@Composable
private fun DebtSection(asset: RealEstateAsset) {
    Column {
        Text("Debt", style = MaterialTheme.typography.titleSmall)
        if (asset.debtAmount == null) {
            Text("Bought outright")
            return@Column
        }
        Text("Initial: ${CurrencyFormatter.formatAbsoluteForCurrency(asset.debtAmount, asset.currencyCode)}")
        asset.outstandingDebt?.let {
            Text("Outstanding: ${CurrencyFormatter.formatAbsoluteForCurrency(it, asset.currencyCode)}")
        }
        Text(describeInterest(asset))
        asset.creditEndDate?.let {
            Text("End date: $it")
            Text(monthsRemaining(it))
        }
    }
}

@Composable
private fun PropertyDetails(asset: RealEstateAsset) {
    Column {
        Text("Details", style = MaterialTheme.typography.titleSmall)
        Text("${asset.district} · ${asset.council} · ${asset.parish}")
        Text("Size: ${asset.sizeM2} m²")
        Text("Energy rating: ${asset.energyRating.label}")
    }
}

@Composable
private fun NotesSection(notes: String) {
    Column {
        Text("Notes", style = MaterialTheme.typography.titleSmall)
        Text(notes)
    }
}
