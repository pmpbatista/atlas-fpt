package com.atlasfpt.ui.feature.timeline

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.atlasfpt.domain.usecase.DayGroup
import com.atlasfpt.domain.usecase.ScheduledRollup
import com.atlasfpt.domain.usecase.TimelineMode
import com.atlasfpt.ui.component.CashFlowBarChart
import com.atlasfpt.ui.component.TransactionRow
import com.atlasfpt.ui.navigation.Screen
import com.atlasfpt.ui.theme.DateHeaderBackground
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import com.atlasfpt.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val data = uiState.timelineData
        val symbol = uiState.settings.currencySymbol

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                CashFlowHeader(headerTotal = data.headerTotal, currencySymbol = symbol)
            }
            item {
                FilterChipsRow(
                    mode = uiState.mode,
                    onModeSelected = viewModel::onModeSelected,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (data.bars.isNotEmpty()) {
                item {
                    CashFlowBarChart(
                        bars = data.bars,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            data.scheduled?.let { rollup ->
                item {
                    ScheduledRollupRow(rollup = rollup, currencySymbol = symbol)
                }
            }
            items(data.days, key = { "day_${it.date}" }) { day ->
                DayHeader(day = day, currencySymbol = symbol)
                day.rows.forEach { rowItem ->
                    val isPending = uiState.pendingDelete?.id == rowItem.transaction.id
                    if (!isPending) {
                        TransactionRow(
                            item = rowItem,
                            currencySymbol = symbol,
                            onClick = {
                                navController.navigate(Screen.EditTransaction.createRoute(rowItem.transaction.id))
                            },
                            onDelete = { viewModel.requestDelete(rowItem.transaction) }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CashFlowHeader(headerTotal: Double, currencySymbol: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val color = if (headerTotal >= 0) IncomeColor else ExpenseColor
        Text(
            text = CurrencyFormatter.formatAbsolute(abs(headerTotal), currencySymbol),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "Cash Flow",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FilterChipsRow(
    mode: TimelineMode,
    onModeSelected: (TimelineMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            AssistChip(
                onClick = { menuOpen = true },
                label = { Text(modeLabel(mode)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                TimelineMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(modeLabel(option)) },
                        onClick = { onModeSelected(option); menuOpen = false }
                    )
                }
            }
        }
    }
}

private fun modeLabel(mode: TimelineMode): String = when (mode) {
    TimelineMode.Monthly -> "Monthly"
    TimelineMode.Annual -> "Annual"
    TimelineMode.Total -> "Total"
}

@Composable
private fun ScheduledRollupRow(rollup: ScheduledRollup, currencySymbol: String) {
    val color = if (rollup.net >= 0) IncomeColor else ExpenseColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Schedule, contentDescription = "Scheduled",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Scheduled", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${rollup.count} transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = CurrencyFormatter.formatAbsolute(abs(rollup.net), currencySymbol),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DayHeader(day: DayGroup, currencySymbol: String) {
    val color = if (day.net >= 0) IncomeColor else ExpenseColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DateHeaderBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDayLabel(day.date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = CurrencyFormatter.formatAbsolute(abs(day.net), currencySymbol),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

private val DAY_LABEL_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("pt", "PT"))

private fun formatDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DAY_LABEL_FORMATTER)
    }
}
