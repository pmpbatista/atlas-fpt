package com.atlasfpt.domain.usecase

import com.atlasfpt.data.repository.RecurringRuleRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Transaction
import javax.inject.Inject

class DeleteTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val recurringRuleRepository: RecurringRuleRepository
) {
    suspend operator fun invoke(transaction: Transaction) {
        transactionRepository.delete(transaction)

        val ruleId = transaction.recurringRuleId ?: return
        val remaining = transactionRepository.countRealTransactionsForRule(ruleId)
        if (remaining == 0) {
            transactionRepository.deleteScheduledForRule(ruleId)
            recurringRuleRepository.deleteById(ruleId)
        }
    }
}
