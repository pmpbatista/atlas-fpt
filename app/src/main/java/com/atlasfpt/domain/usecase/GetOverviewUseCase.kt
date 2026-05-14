package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.CategoryRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Category
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import javax.inject.Inject

data class CategoryBreakdown(
    val category: Category,
    val amount: Double,
    val percentage: Float,
    val transactionCount: Int
)

data class OverviewUiState(
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val expenseBreakdown: List<CategoryBreakdown> = emptyList(),
    val incomeBreakdown: List<CategoryBreakdown> = emptyList(),
    val isLoading: Boolean = true
)

class GetOverviewUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(month: YearMonth, personFilterIds: Set<Long> = emptySet()): Flow<OverviewUiState> {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        return if (personFilterIds.isEmpty()) {
            daoAggregatePath(from, to)
        } else {
            inMemoryFilteredPath(from, to, personFilterIds)
        }
    }

    private fun daoAggregatePath(from: java.time.LocalDate, to: java.time.LocalDate): Flow<OverviewUiState> {
        return combine(
            transactionRepository.getCategoryTotals(TransactionType.EXPENSE, from, to),
            transactionRepository.getCategoryTotals(TransactionType.INCOME, from, to),
            categoryRepository.observeAll()
        ) { expenseTotals, incomeTotals, categories ->
            val categoryMap = categories.associateBy { it.id }
            val totalExpense = expenseTotals.sumOf { it.total }
            val totalIncome = incomeTotals.sumOf { it.total }

            fun breakdown(totals: List<com.atlasfpt.data.db.dao.CategoryTotal>, grandTotal: Double) =
                totals.mapNotNull { ct ->
                    val cat = categoryMap[ct.categoryId] ?: return@mapNotNull null
                    CategoryBreakdown(
                        category = cat,
                        amount = ct.total,
                        percentage = if (grandTotal > 0) (ct.total / grandTotal * 100).toFloat() else 0f,
                        transactionCount = ct.count
                    )
                }.sortedByDescending { it.amount }

            OverviewUiState(
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                expenseBreakdown = breakdown(expenseTotals, totalExpense),
                incomeBreakdown = breakdown(incomeTotals, totalIncome),
                isLoading = false
            )
        }
    }

    private fun inMemoryFilteredPath(
        from: java.time.LocalDate,
        to: java.time.LocalDate,
        personFilterIds: Set<Long>
    ): Flow<OverviewUiState> {
        return combine(
            transactionRepository.observeByDateRange(from, to),
            categoryRepository.observeAll()
        ) { transactions, categories ->
            val filtered = transactions.filter { tx -> tx.persons.any { it.id in personFilterIds } }
            val expense = filtered.filter { it.type == TransactionType.EXPENSE }
            val income = filtered.filter { it.type == TransactionType.INCOME }
            val totalExpense = expense.sumOf { it.amount }
            val totalIncome = income.sumOf { it.amount }
            val categoryMap = categories.associateBy { it.id }

            fun aggregate(rows: List<Transaction>, grandTotal: Double): List<CategoryBreakdown> {
                return rows.groupBy { it.category.id }
                    .mapNotNull { (catId, txs) ->
                        val cat = categoryMap[catId] ?: return@mapNotNull null
                        val sum = txs.sumOf { it.amount }
                        CategoryBreakdown(
                            category = cat,
                            amount = sum,
                            percentage = if (grandTotal > 0) (sum / grandTotal * 100).toFloat() else 0f,
                            transactionCount = txs.size
                        )
                    }.sortedByDescending { it.amount }
            }

            OverviewUiState(
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                expenseBreakdown = aggregate(expense, totalExpense),
                incomeBreakdown = aggregate(income, totalIncome),
                isLoading = false
            )
        }
    }
}
