package com.atlasfpt.ui.feature.assets.realestate.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.atlasfpt.domain.model.EuriborRate
import com.atlasfpt.domain.model.RealEstateAsset
import com.atlasfpt.domain.model.ReferenceRate
import com.atlasfpt.ui.component.LinkedTransactionsSection
import com.atlasfpt.ui.navigation.Screen
import com.atlasfpt.util.CurrencyFormatter
import com.atlasfpt.util.describeInterest
import com.atlasfpt.util.formatPercent
import com.atlasfpt.util.monthsRemaining
import com.atlasfpt.util.relativeTimeString
import java.time.Instant

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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Couldn't load property")
                    Spacer(
                        Modifier.height(8.dp)
                    )
                    TextButton(
                        onClick = { navController.popBackStack() }
                    ) { Text("Go back") }
                }
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
                    asset.photoUri?.let { photo ->
                        item {
                            AsyncImage(
                                model = photo,
                                contentDescription = "Property photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    item { ValuationCard(asset, equity) }
                    item { Spacer(Modifier.height(16.dp)); PurchaseSummary(asset) }
                    item {
                        Spacer(Modifier.height(16.dp))
                        DebtSection(
                            asset = asset,
                            euribor = state.euribor,
                            effectiveRate = state.effectiveRate,
                            onSetManual = { value, asOf ->
                                asset.referenceRate?.let { tenor ->
                                    viewModel.onManualEuriborSet(tenor, value, asOf)
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)); PropertyDetails(asset) }
                    asset.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        item { Spacer(Modifier.height(16.dp)); NotesSection(notes) }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
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
private fun DebtSection(
    asset: RealEstateAsset,
    euribor: EuriborRate?,
    effectiveRate: Double?,
    onSetManual: (Double, Instant) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

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
        if (asset.interestType == com.atlasfpt.domain.model.InterestType.VARIABLE && asset.referenceRate != null) {
            EuriborRow(
                tenor = asset.referenceRate,
                euribor = euribor,
                effectiveRate = effectiveRate,
                onClick = { dialogOpen = true },
            )
        }
        asset.creditEndDate?.let {
            Text("End date: $it")
            Text(monthsRemaining(it))
        }
    }

    if (dialogOpen && asset.referenceRate != null) {
        ManualEuriborDialog(
            tenor = asset.referenceRate,
            current = euribor,
            onDismiss = { dialogOpen = false },
            onSave = { value, asOf ->
                onSetManual(value, asOf)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun EuriborRow(
    tenor: ReferenceRate,
    euribor: EuriborRate?,
    effectiveRate: Double?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            if (euribor != null) {
                Text(
                    text = "${tenor.label} ${formatPercent(euribor.value)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val srcLabel = when (euribor.source) {
                    EuriborRate.Source.ECB -> "ECB"
                    EuriborRate.Source.MANUAL -> "manual"
                }
                Text(
                    text = "$srcLabel · ${relativeTimeString(euribor.asOf.toEpochMilli())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${tenor.label} — no value yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Tap to enter manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (effectiveRate != null) {
            Text(
                text = "Effective ${formatPercent(effectiveRate)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ManualEuriborDialog(
    tenor: ReferenceRate,
    current: EuriborRate?,
    onDismiss: () -> Unit,
    onSave: (Double, Instant) -> Unit,
) {
    var valueText by remember { mutableStateOf(current?.value?.toString().orEmpty()) }
    var dateText by remember {
        mutableStateOf(
            current?.asOf?.let { java.time.LocalDate.ofInstant(it, java.time.ZoneOffset.UTC).toString() }
                ?: java.time.LocalDate.now().toString(),
        )
    }
    val parsedValue = valueText.replace(',', '.').toDoubleOrNull()
    val parsedDate = runCatching { java.time.LocalDate.parse(dateText) }.getOrNull()
    val canSave = parsedValue != null && parsedDate != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set ${tenor.label} manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Rate (%)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("As-of date (YYYY-MM-DD)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        parsedValue!!,
                        parsedDate!!.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
