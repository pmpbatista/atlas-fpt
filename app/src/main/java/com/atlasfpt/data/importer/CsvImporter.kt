package com.atlasfpt.data.importer

import android.content.Context
import android.net.Uri
import com.atlasfpt.data.repository.CategoryRepository
import com.atlasfpt.data.repository.TransactionRepository
import com.atlasfpt.domain.model.Transaction
import com.atlasfpt.domain.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val imported: Int, val skipped: Int, val errors: List<String>)

@Singleton
class CsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    // Expected CSV format: date,amount,type,category,note
    // date: ISO-8601 (yyyy-MM-dd) or dd/MM/yyyy
    // type: expense/income (case-insensitive)
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val ptFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val categories = categoryRepository.observeAll().first()
        val categoryByName = categories.associateBy { it.name.lowercase() }
        val fallbackExpense = categories.firstOrNull { it.type.name != "INCOME" }
        val fallbackIncome = categories.firstOrNull { it.type.name != "EXPENSE" }

        val errors = mutableListOf<String>()
        var imported = 0
        var skipped = 0

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val reader = BufferedReader(InputStreamReader(stream))
            var lineNum = 0
            reader.lineSequence().forEach { line ->
                lineNum++
                if (lineNum == 1 && line.lowercase().startsWith("date")) return@forEach // skip header

                val parts = line.split(",").map { it.trim() }
                if (parts.size < 3) {
                    errors.add("Line $lineNum: not enough columns")
                    skipped++
                    return@forEach
                }

                val date = parseDate(parts[0]) ?: run {
                    errors.add("Line $lineNum: invalid date '${parts[0]}'")
                    skipped++
                    return@forEach
                }

                val amount = parts[1].replace(",", ".").toDoubleOrNull() ?: run {
                    errors.add("Line $lineNum: invalid amount '${parts[1]}'")
                    skipped++
                    return@forEach
                }

                val type = when (parts[2].lowercase()) {
                    "expense", "despesa", "saída" -> TransactionType.EXPENSE
                    "income", "receita", "entrada" -> TransactionType.INCOME
                    else -> {
                        errors.add("Line $lineNum: unknown type '${parts[2]}'")
                        skipped++
                        return@forEach
                    }
                }

                val categoryName = parts.getOrNull(3)?.lowercase()
                val category = categoryByName[categoryName]
                    ?: if (type == TransactionType.EXPENSE) fallbackExpense else fallbackIncome

                if (category == null) {
                    errors.add("Line $lineNum: no suitable category found")
                    skipped++
                    return@forEach
                }

                val note = parts.getOrNull(4)?.takeIf { it.isNotBlank() }

                val tx = Transaction(
                    id = 0,
                    amount = amount,
                    type = type,
                    category = category,
                    date = date,
                    note = note,
                    photoUri = null,
                    labels = emptyList(),
                    persons = emptyList(),
                    recurringRuleId = null,
                    isScheduled = false
                )
                transactionRepository.save(tx)
                imported++
            }
        }

        ImportResult(imported, skipped, errors)
    }

    private fun parseDate(s: String): LocalDate? {
        return try { LocalDate.parse(s, isoFormatter) } catch (_: DateTimeParseException) {
            try { LocalDate.parse(s, ptFormatter) } catch (_: DateTimeParseException) { null }
        }
    }
}
