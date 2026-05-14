package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.CategoryRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Category
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
    operator fun invoke(month: YearMonth): Flow<OverviewUiState> {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
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
}
