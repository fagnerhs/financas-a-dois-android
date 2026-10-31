package br.com.financasdocasal

/** Totais de movimentações compartilhadas no mês; não representa saldo bancário. */
data class MonthlyEntrySummary(
    val income: Double,
    val expenses: Double,
    val movementCount: Int
) {
    val net: Double get() = income - expenses
}

/**
 * Soma somente registros do mês e visíveis ao casal. Entradas privadas são omitidas;
 * despesas "somente saldo" contribuem com valor e quantidade, sem expor detalhes.
 */
internal fun summarizeMonthlyEntries(entries: List<Expense>, month: String): MonthlyEntrySummary {
    val visible = entries.filter { it.date.startsWith(month) && it.visibility != "private" }
    val income = visible.filter { it.kind == EntryKind.INCOME && it.visibility == "shared" }.sumOf { it.amount }
    val expenses = visible.filter {
        it.kind == EntryKind.EXPENSE && (it.visibility == "shared" || it.visibility == "balances_only")
    }.sumOf { it.amount }
    return MonthlyEntrySummary(income = income, expenses = expenses, movementCount = visible.size)
}
