package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

enum class TimelineMode { Monthly, Annual, Total }

data class CashFlowBar(
    val label: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
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
    val bars: List<CashFlowBar> = emptyList(),
    val scheduled: ScheduledRollup? = null,
    val days: List<DayGroup> = emptyList()
)

private val MONTH_LABEL_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM\nyyyy", Locale("pt", "PT"))

class GetTimelineUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    operator fun invoke(mode: TimelineMode = TimelineMode.Monthly): Flow<TimelineData> = combine(
        transactionRepository.observeAll(),
        transactionRepository.observeScheduled()
    ) { all, scheduled ->
        val headerTotal = all.sumOf { tx ->
            if (tx.type == TransactionType.EXPENSE) -tx.amount else tx.amount
        }
        TimelineData(
            headerTotal = headerTotal,
            bars = buildBars(all, mode),
            scheduled = buildScheduled(scheduled),
            days = buildDays(all)
        )
    }

    private fun buildBars(transactions: List<Transaction>, mode: TimelineMode): List<CashFlowBar> =
        when (mode) {
            TimelineMode.Monthly -> buildMonthlyBars(transactions)
            TimelineMode.Annual -> buildAnnualBars(transactions)
            TimelineMode.Total -> buildTotalBar(transactions)
        }

    private fun buildMonthlyBars(transactions: List<Transaction>): List<CashFlowBar> {
        val today = YearMonth.now()
        val earliest = transactions.minOfOrNull { YearMonth.from(it.date) } ?: today
        val months = mutableListOf<YearMonth>()
        var cursor = if (earliest.isAfter(today)) today else earliest
        while (!cursor.isAfter(today)) {
            months += cursor
            cursor = cursor.plusMonths(1)
        }
        val byMonth = transactions.groupBy { YearMonth.from(it.date) }
        return months.map { month ->
            val rows = byMonth[month].orEmpty()
            CashFlowBar(
                label = month.atDay(1).format(MONTH_LABEL_FORMATTER),
                periodStart = month.atDay(1),
                periodEnd = month.atEndOfMonth(),
                income = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                isCurrent = month == today
            )
        }
    }

    private fun buildAnnualBars(transactions: List<Transaction>): List<CashFlowBar> {
        val today = Year.now()
        val byYear = transactions.groupBy { Year.from(it.date) }
        val years = (byYear.keys + today).toSortedSet()
        return years.map { year ->
            val rows = byYear[year].orEmpty()
            CashFlowBar(
                label = year.value.toString(),
                periodStart = year.atDay(1),
                periodEnd = year.atMonth(12).atEndOfMonth(),
                income = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                isCurrent = year == today
            )
        }
    }

    private fun buildTotalBar(transactions: List<Transaction>): List<CashFlowBar> {
        val today = LocalDate.now()
        if (transactions.isEmpty()) {
            return listOf(
                CashFlowBar(
                    label = "All",
                    periodStart = today,
                    periodEnd = today,
                    income = 0.0,
                    expense = 0.0,
                    isCurrent = true
                )
            )
        }
        val sorted = transactions.map { it.date }.sorted()
        return listOf(
            CashFlowBar(
                label = "All",
                periodStart = sorted.first(),
                periodEnd = maxOf(sorted.last(), today),
                income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                isCurrent = true
            )
        )
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
