package com.atlasfpt.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import com.atlasfpt.ui.theme.ExpenseColor
import com.atlasfpt.ui.theme.IncomeColor
import com.atlasfpt.util.CurrencyFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("pt", "PT"))

@Composable
fun LinkedTransactionsSection(
    transactions: List<Transaction>,
    currencySymbol: String,
    onRowClick: (Long) -> Unit,
) {
    Column {
        Text("Linked transactions", style = MaterialTheme.typography.titleSmall)
        if (transactions.isEmpty()) {
            Text(
                text = "No transactions linked yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            transactions.forEach { tx ->
                LinkedTransactionRow(tx, currencySymbol, onClick = { onRowClick(tx.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LinkedTransactionRow(
    tx: Transaction,
    currencySymbol: String,
    onClick: () -> Unit,
) {
    val isExpense = tx.type == TransactionType.EXPENSE
    val color = if (isExpense) ExpenseColor else IncomeColor
    val signed = CurrencyFormatter.format(tx.amount, currencySymbol, tx.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(tx.category.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = tx.date.format(dateFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = signed, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
