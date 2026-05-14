package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

sealed class TimelineItem {
    data class DateHeader(val date: LocalDate, val dailyTotal: Double) : TimelineItem()
    data class TransactionRow(val transaction: Transaction) : TimelineItem()
}

data class TimelineData(
    val totalCashFlow: Double = 0.0,
    val monthlySummaries: List<com.atlasfpt.data.db.dao.MonthlySummary> = emptyList(),
    val scheduledTransactions: List<Transaction> = emptyList(),
    val timelineItems: List<TimelineItem> = emptyList()
)

class GetTimelineUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(): Flow<TimelineData> = combine(
        transactionRepository.observeAll(),
        transactionRepository.observeScheduled(),
        transactionRepository.observeMonthlySummaries()
    ) { all, scheduled, summaries ->
        val totalCashFlow = all.sumOf { tx ->
            if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
        }
        TimelineData(
            totalCashFlow = totalCashFlow,
            monthlySummaries = summaries,
            scheduledTransactions = scheduled,
            timelineItems = buildItems(all)
        )
    }

    private fun buildItems(transactions: List<Transaction>): List<TimelineItem> {
        val grouped = transactions.groupBy { it.date }
        return grouped.entries
            .sortedByDescending { it.key }
            .flatMap { (date, txs) ->
                val dailyTotal = txs.sumOf { tx ->
                    if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
                }
                listOf(TimelineItem.DateHeader(date, dailyTotal)) +
                    txs.map { TimelineItem.TransactionRow(it) }
            }
    }
}
