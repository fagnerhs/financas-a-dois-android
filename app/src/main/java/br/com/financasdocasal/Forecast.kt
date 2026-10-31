package br.com.financasdocasal

import java.time.LocalDate

/** Resultado determinístico; não representa saldo bancário nem consulta instituições financeiras. */
data class CashForecast(val remaining: Double, val includedExpenses: Double, val includedRecurring: Double, val includedCardPayments: Double = 0.0)

/**
 * Projeta a partir de um saldo manual informado em referenceDate até payday.
 * Despesas são consideradas apenas quando posteriores ao snapshot; private é excluído e
 * balances_only contribui exclusivamente com amount/date. Recorrências ativas e visíveis
 * são repetidas segundo sua frequência entre nextDate e payday, após referenceDate. Compras
 * de cartão não são fluxo de caixa; uma fatura só reduz o saldo quando marcada como paga.
 */
fun calculateCashForecast(
    manualBalance: Double,
    referenceDate: LocalDate,
    today: LocalDate,
    payday: LocalDate,
    expenses: List<Expense>,
    recurringBills: List<RecurringBill>,
    creditCardCycles: List<CreditCardCycle> = emptyList()
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
    val cardPaymentTotal = creditCardCycles.asSequence()
        .filter { it.closedAt != null && it.paidAt != null && it.statementAmount > 0.0 }
        .filter { cycle ->
            val paidDate = cycle.paidAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@filter false
            paidDate.isAfter(referenceDate) && !paidDate.isAfter(payday)
        }
        .sumOf { it.statementAmount }
    return CashForecast(manualBalance - expenseTotal - recurringTotal - cardPaymentTotal, expenseTotal, recurringTotal, cardPaymentTotal)
}
