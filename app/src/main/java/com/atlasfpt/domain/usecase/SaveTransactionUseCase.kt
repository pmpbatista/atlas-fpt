package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.RecurringRuleRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.RecurringFrequency
import com.atlasfpt.domain.model.Transaction
import java.time.LocalDate
import javax.inject.Inject

class SaveTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val recurringRuleRepository: RecurringRuleRepository
) {
    suspend operator fun invoke(transaction: Transaction): Long {
        val id = transactionRepository.save(transaction)

        val ruleId = transaction.recurringRuleId ?: return id
        val rule = recurringRuleRepository.getById(ruleId) ?: return id

        transactionRepository.deleteScheduledForRule(ruleId)
        var nextDate = rule.nextTriggerDate
        repeat(3) {
            if (rule.endDate == null || nextDate <= rule.endDate) {
                transactionRepository.save(
                    transaction.copy(id = 0, date = nextDate, isScheduled = true)
                )
                nextDate = advance(nextDate, rule.frequency, rule.interval)
            }
        }

        return id
    }

    private fun advance(date: LocalDate, freq: RecurringFrequency, interval: Int) = when (freq) {
        RecurringFrequency.DAILY -> date.plusDays(interval.toLong())
        RecurringFrequency.WEEKLY -> date.plusWeeks(interval.toLong())
        RecurringFrequency.MONTHLY -> date.plusMonths(interval.toLong())
        RecurringFrequency.YEARLY -> date.plusYears(interval.toLong())
    }
}
