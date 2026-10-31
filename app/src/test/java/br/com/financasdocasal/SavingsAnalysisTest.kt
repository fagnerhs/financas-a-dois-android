package br.com.financasdocasal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsAnalysisTest {
    @Test
    fun reserveContributionAddsToExistingTotalInCents() {
        assertEquals(1001.0, updatedReserveTotal(1000.25, 0.75)!!, 0.0)
        assertTrue(updatedReserveTotal(100.0, 0.0) == null)
        assertTrue(updatedReserveTotal(100.0, -5.0) == null)
        assertTrue(updatedReserveTotal(9_999_999_999.99, 1.0) == null)
    }

    @Test
    fun reportsSharedCategoryAndBudgetWithoutUsingPrivateOrBalanceOnlyRows() {
        val entries = listOf(
            Expense(250.0, "Mercado", "A", "", "2026-10-02", visibility = "shared"),
            Expense(90.0, "Delivery", "B", "", "2026-10-03", visibility = "shared"),
            Expense(600.0, "Lazer", "A", "", "2026-10-04", visibility = "private"),
            Expense(800.0, "", "B", "", "2026-10-05", visibility = "balances_only"),
            Expense(1000.0, "Salário", "A", "", "2026-10-06", visibility = "shared", kind = EntryKind.INCOME),
            Expense(700.0, "Moradia", "A", "", "2026-09-30", visibility = "shared")
        )

        val result = analyzeSavingsOpportunities(entries, mapOf("Mercado" to 200.0, "Delivery" to 120.0), "2026-10")

        assertEquals("Mercado", result.first().category)
        assertEquals(250.0, result.first().amount, 0.0)
        assertEquals(50.0, result.first().amountOverBudget, 0.0)
        assertEquals(1, result.first().transactionCount)
        assertEquals(250.0 / 340.0, result.first().shareOfCategorizedSpending, 0.0001)
        assertTrue(result.none { it.category == "Lazer" || it.category.isBlank() || it.category == "Salário" })
    }

    @Test
    fun returnsNoSuggestionsWhenThereAreNoVisibleCategorizedExpenses() {
        val entries = listOf(
            Expense(50.0, "Outros", "A", "", "2026-10-02", visibility = "private"),
            Expense(500.0, "Salário", "A", "", "2026-10-03", kind = EntryKind.INCOME)
        )

        assertTrue(analyzeSavingsOpportunities(entries, emptyMap(), "2026-10").isEmpty())
    }
}
