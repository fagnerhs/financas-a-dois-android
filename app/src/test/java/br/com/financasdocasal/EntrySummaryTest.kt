package br.com.financasdocasal

import org.junit.Assert.assertEquals
import org.junit.Test

class EntrySummaryTest {
    @Test
    fun separatesSharedIncomeExpensesAndBalanceOnlyRows() {
        val entries = listOf(
            Expense(1000.0, "Salário", "Fagner", "", "2026-10-05", visibility = "shared", kind = EntryKind.INCOME),
            Expense(250.0, "Mercado", "Thay", "Compras", "2026-10-08", visibility = "shared"),
            Expense(30.0, "", "", "", "2026-10-10", visibility = "balances_only"),
            Expense(500.0, "Renda extra", "Fagner", "", "2026-10-12", visibility = "private", kind = EntryKind.INCOME),
            Expense(80.0, "Delivery", "Thay", "Jantar", "2026-09-29", visibility = "shared")
        )

        val summary = summarizeMonthlyEntries(entries, "2026-10")

        assertEquals(1000.0, summary.income, 0.0)
        assertEquals(280.0, summary.expenses, 0.0)
        assertEquals(-720.0, summary.net, 0.0)
        assertEquals(3, summary.movementCount)
    }
}
