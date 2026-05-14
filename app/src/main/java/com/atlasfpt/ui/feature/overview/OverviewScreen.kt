package com.atlasfpt.ui.feature.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.domain.usecase.CategoryBreakdown
import com.atlasfpt.ui.component.CategoryDonut
import com.atlasfpt.ui.component.MonthSelector
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import com.atlasfpt.util.CurrencyFormatter

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = hiltViewModel()) {
    val screenState by viewModel.uiState.collectAsState()
    val state = screenState.overviewUiState
    val isExpense = screenState.selectedSide == TransactionType.EXPENSE
    val slices = if (isExpense) state.expenseBreakdown else state.incomeBreakdown
    val accent = if (isExpense) ExpenseColor else IncomeColor

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        MonthSelector(
            yearMonth = screenState.selectedMonth,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        SegmentedTotalsRow(
            selectedSide = screenState.selectedSide,
            expenseTotal = state.totalExpense,
            incomeTotal = state.totalIncome,
            currencySymbol = screenState.currencySymbol,
            onSideSelected = viewModel::onSideSelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (slices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                CategoryDonut(
                    slices = slices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(24.dp)
                )
            }
            items(slices) { slice ->
                CategorySliceRow(slice = slice, currencySymbol = screenState.currencySymbol, accent = accent)
                HorizontalDivider()
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SegmentedTotalsRow(
    selectedSide: TransactionType,
    expenseTotal: Double,
    incomeTotal: Double,
    currencySymbol: String,
    onSideSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TotalsCard(
            label = "Expenses",
            amount = expenseTotal,
            color = ExpenseColor,
            selected = selectedSide == TransactionType.EXPENSE,
            currencySymbol = currencySymbol,
            onClick = { onSideSelected(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f)
        )
        TotalsCard(
            label = "Income",
            amount = incomeTotal,
            color = IncomeColor,
            selected = selectedSide == TransactionType.INCOME,
            currencySymbol = currencySymbol,
            onClick = { onSideSelected(TransactionType.INCOME) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TotalsCard(
    label: String,
    amount: Double,
    color: Color,
    selected: Boolean,
    currencySymbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                CurrencyFormatter.formatAbsolute(amount, currencySymbol),
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CategorySliceRow(slice: CategoryBreakdown, currencySymbol: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(slice.category.color)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    slice.category.name.firstOrNull()?.uppercase() ?: "·",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(slice.category.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${slice.transactionCount} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            CurrencyFormatter.formatAbsolute(slice.amount, currencySymbol),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}
