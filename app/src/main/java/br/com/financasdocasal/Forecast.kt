package br.com.financasdocasal

import java.time.LocalDate

/** Resultado determinístico; não representa saldo bancário nem consulta instituições financeiras. */
data class CashForecast(val remaining: Double, val includedExpenses: Double, val includedRecurring: Double)

/**
 * Projeta a partir de um saldo manual informado em referenceDate até payday.
 * Despesas são consideradas apenas quando posteriores ao snapshot; private é excluído e
 * balances_only contribui exclusivamente com amount/date. Recorrências ativas e visíveis
 * são repetidas segundo sua frequência entre nextDate e payday, após referenceDate.
 */
fun calculateCashForecast(
    manualBalance: Double,
    referenceDate: LocalDate,
    today: LocalDate,
    payday: LocalDate,
    expenses: List<Expense>,
    recurringBills: List<RecurringBill>
): CashForecast {
    require(!payday.isBefore(today)) { "A próxima data de recebimento deve ser igual ou posterior a hoje" }
    val expenseTotal = expenses.asSequence()
        .filter { it.kind != EntryKind.INCOME }
        .filter { it.visibility == "shared" || it.visibility == "balances_only" }
        .filter { runCatching { LocalDate.parse(it.date).isAfter(referenceDate) && !LocalDate.parse(it.date).isAfter(payday) }.getOrDefault(false) }
        .sumOf { it.amount.coerceAtLeast(0.0) }
    val recurringTotal = recurringBills.asSequence()
        .filter { it.isActive && it.visibility != "private" && it.amount > 0 }
        .sumOf { bill ->
            val first = runCatching { LocalDate.parse(bill.nextDate) }.getOrNull() ?: return@sumOf 0.0
            var due = first
            var total = 0.0
            var iterations = 0
            while (due.isBefore(today) && iterations++ < 500) {
                val next = when (bill.frequency) {
                    "weekly" -> due.plusWeeks(1)
                    "biweekly" -> due.plusWeeks(2)
                    "monthly" -> due.plusMonths(1)
                    "yearly" -> due.plusYears(1)
                    else -> null
                } ?: break
                if (!next.isAfter(due)) break
                due = next
            }
            while (due.isAfter(referenceDate) && !due.isBefore(today) && !due.isAfter(payday) && iterations++ < 1000) {
                total += bill.amount
                val next = when (bill.frequency) {
                    "weekly" -> due.plusWeeks(1)
                    "biweekly" -> due.plusWeeks(2)
                    "monthly" -> due.plusMonths(1)
                    "yearly" -> due.plusYears(1)
                    else -> null
                } ?: break
                if (!next.isAfter(due)) break
                due = next
            }
            total
        }
    return CashForecast(manualBalance - expenseTotal - recurringTotal, expenseTotal, recurringTotal)
}
