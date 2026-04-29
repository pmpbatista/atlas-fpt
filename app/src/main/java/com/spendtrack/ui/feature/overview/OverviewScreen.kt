package com.spendtrack.ui.feature.overview

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendtrack.domain.usecase.CategoryBreakdown
import com.spendtrack.ui.component.MonthSelector
import com.spendtrack.ui.theme.ExpenseColor
import com.spendtrack.ui.theme.IncomeColor
import com.spendtrack.util.CurrencyFormatter

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = hiltViewModel()) {
    val screenState by viewModel.uiState.collectAsState()
    val state = screenState.overviewUiState
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSelector(
            yearMonth = screenState.selectedMonth,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text(
                    "Expenses",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (selectedTab == 0) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text(
                    "Income",
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = if (selectedTab == 1) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val breakdown = if (selectedTab == 0) state.expenseBreakdown else state.incomeBreakdown
        val total = if (selectedTab == 0) state.totalExpense else state.totalIncome
        val accentColor = if (selectedTab == 0) ExpenseColor else IncomeColor

        if (breakdown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No transactions this month",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                DonutChart(
                    breakdown = breakdown,
                    accentColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(16.dp)
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total", style = MaterialTheme.typography.titleMedium)
                    Text(
                        CurrencyFormatter.formatAbsolute(total, screenState.currencySymbol),
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor
                    )
                }
                HorizontalDivider()
            }
            items(breakdown) { item ->
                CategoryBreakdownRow(item, screenState.currencySymbol)
                HorizontalDivider()
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun DonutChart(
    breakdown: List<CategoryBreakdown>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = breakdown.mapIndexed { i, _ ->
        val hue = (i * 137.5f) % 360f
        Color.hsl(hue, 0.6f, 0.5f)
    }

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        val radius = (size.minDimension - strokeWidth) / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        var startAngle = -90f
        breakdown.forEachIndexed { i, item ->
            val sweep = item.percentage / 100f * 360f
            drawArc(
                color = colors[i],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun CategoryBreakdownRow(item: CategoryBreakdown, currencySymbol: String) {
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
                    .size(12.dp)
                    .then(Modifier)
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(color = Color(item.category.color))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(item.category.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${item.percentage.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            CurrencyFormatter.formatAbsolute(item.amount, currencySymbol),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
