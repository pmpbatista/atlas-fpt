package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthBar(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
    val isCurrent: Boolean
)

data class ScheduledRollup(val count: Int, val net: Double)

data class TransactionRowItem(
    val transaction: Transaction,
    val walletLabel: String,
    val noteLine: String?
)

data class DayGroup(
    val date: LocalDate,
    val net: Double,
    val rows: List<TransactionRowItem>
)

data class TimelineData(
    val headerTotal: Double = 0.0,
    val bars: List<MonthBar> = emptyList(),
    val scheduled: ScheduledRollup? = null,
    val days: List<DayGroup> = emptyList()
)

class GetTimelineUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(personFilterIds: Set<Long> = emptySet()): Flow<TimelineData> = combine(
        transactionRepository.observeAll(),
        transactionRepository.observeScheduled()
    ) { all, scheduled ->
        val filtered = applyPersonFilter(all, personFilterIds)
        val filteredScheduled = applyPersonFilter(scheduled, personFilterIds)
        val headerTotal = filtered.sumOf { tx ->
            if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
        }
        TimelineData(
            headerTotal = headerTotal,
            bars = buildBars(filtered),
            scheduled = buildScheduled(filteredScheduled),
            days = buildDays(filtered)
        )
    }

    private fun applyPersonFilter(transactions: List<Transaction>, ids: Set<Long>): List<Transaction> {
        if (ids.isEmpty()) return transactions
        return transactions.filter { tx -> tx.persons.any { it.id in ids } }
    }

    private fun buildBars(transactions: List<Transaction>): List<MonthBar> {
        val today = YearMonth.now()
        val window = (5 downTo 0).map { today.minusMonths(it.toLong()) }
        val byMonth = transactions.groupBy { YearMonth.from(it.date) }
        return window.map { month ->
            val rows = byMonth[month].orEmpty()
            val income = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expense = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            MonthBar(month, income, expense, isCurrent = month == today)
        }
    }

    private fun buildScheduled(scheduled: List<Transaction>): ScheduledRollup? {
        if (scheduled.isEmpty()) return null
        val net = scheduled.sumOf { tx ->
            if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
        }
        return ScheduledRollup(count = scheduled.size, net = net)
    }

    private fun buildDays(transactions: List<Transaction>): List<DayGroup> {
        return transactions.groupBy { it.date }
            .entries
            .sortedByDescending { it.key }
            .map { (date, txs) ->
                val net = txs.sumOf { tx ->
                    if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
                }
                DayGroup(
                    date = date,
                    net = net,
                    rows = txs.map { tx ->
                        TransactionRowItem(
                            transaction = tx,
                            walletLabel = "Wallet",
                            noteLine = tx.note?.takeIf { it.isNotBlank() }
                        )
                    }
                )
            }
    }
}
