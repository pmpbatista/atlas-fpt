package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.RecurringRuleRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.RecurringFrequency
import java.time.LocalDate
import javax.inject.Inject

class MaterialiseRecurringTransactionsUseCase @Inject constructor(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke() {
        val today = LocalDate.now()
        val dueRules = recurringRuleRepository.getDueRules(today)

        dueRules.forEach { rule ->
            val template = transactionRepository.getLatestForRule(rule.id) ?: return@forEach

            // Materialise the due transaction as real
            transactionRepository.save(
                template.copy(id = 0, date = rule.nextTriggerDate, isScheduled = false)
            )

            // Advance the rule's next trigger date
            val newNext = advance(rule.nextTriggerDate, rule.frequency, rule.interval)
            recurringRuleRepository.update(rule.copy(nextTriggerDate = newNext))

            // Regenerate 3 scheduled lookahead instances
            transactionRepository.deleteScheduledForRule(rule.id)
            var futureDate = newNext
            repeat(3) {
                if (rule.endDate == null || futureDate <= rule.endDate) {
                    transactionRepository.save(
                        template.copy(id = 0, date = futureDate, isScheduled = true, recurringRuleId = rule.id)
                    )
                    futureDate = advance(futureDate, rule.frequency, rule.interval)
                }
            }
        }
    }

    private fun advance(date: LocalDate, freq: RecurringFrequency, interval: Int) = when (freq) {
        RecurringFrequency.DAILY -> date.plusDays(interval.toLong())
        RecurringFrequency.WEEKLY -> date.plusWeeks(interval.toLong())
        RecurringFrequency.MONTHLY -> date.plusMonths(interval.toLong())
        RecurringFrequency.YEARLY -> date.plusYears(interval.toLong())
    }
}
