package br.com.financasdocasal

/** Sugestão explicável, calculada no aparelho a partir de lançamentos categorizados compartilhados. */
data class SavingOpportunity(
    val category: String,
    val amount: Double,
    val transactionCount: Int,
    val shareOfCategorizedSpending: Double,
    val budgetLimit: Double?
) {
    val amountOverBudget: Double
        get() = budgetLimit?.takeIf { it > 0.0 }?.let { (amount - it).coerceAtLeast(0.0) } ?: 0.0
}

/** Soma um aporte válido em centavos; o limite corresponde ao numeric(12,2) do Supabase. */
internal fun updatedReserveTotal(currentTotal: Double, contribution: Double): Double? {
    if (!currentTotal.isFinite() || !contribution.isFinite() || currentTotal < 0.0 || contribution < 0.01) return null
    val currentCents = kotlin.math.round(currentTotal * 100.0).toLong()
    val contributionCents = kotlin.math.round(contribution * 100.0).toLong()
    val maxCents = 999_999_999_999L
    if (contributionCents < 1L || currentCents < 0L || currentCents > maxCents - contributionCents) return null
    return (currentCents + contributionCents) / 100.0
}

/**
 * Retorna até três pontos para revisão. Considera somente despesas compartilhadas com categoria
 * visível no mês; ignora rendas, itens privados e itens compartilhados apenas pelo saldo.
 * É uma análise determinística local, não uma resposta gerada por IA.
 */
internal fun analyzeSavingsOpportunities(
    entries: List<Expense>,
    budgets: Map<String, Double>,
    month: String
): List<SavingOpportunity> {
    val categorized = entries.filter {
        it.date.startsWith(month) && it.kind == EntryKind.EXPENSE && it.visibility == "shared" && it.category.isNotBlank()
    }
    if (categorized.isEmpty()) return emptyList()

    val byCategory = categorized.groupBy { it.category.trim() }
    val totals = byCategory.mapValues { (_, rows) -> rows.sumOf { it.amount } }
    val categorizedTotal = totals.values.sum()
    if (categorizedTotal <= 0.0) return emptyList()

    val overBudget = totals.keys
        .mapNotNull { category ->
            val limit = budgets[category]?.takeIf { it > 0.0 } ?: return@mapNotNull null
            val amount = totals.getValue(category)
            if (amount > limit) category to (amount - limit) else null
        }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(2)

    val highestCategory = totals.maxByOrNull { it.value }?.key ?: return emptyList()
    val selected = (overBudget + highestCategory).distinct().take(3)
    return selected.map { category ->
        val amount = totals.getValue(category)
        SavingOpportunity(
            category = category,
            amount = amount,
            transactionCount = byCategory.getValue(category).size,
            shareOfCategorizedSpending = amount / categorizedTotal,
            budgetLimit = budgets[category]?.takeIf { it > 0.0 }
        )
    }
}
