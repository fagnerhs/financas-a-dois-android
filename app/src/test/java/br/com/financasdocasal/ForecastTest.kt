package br.com.financasdocasal

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastTest {
    private val snapshot = LocalDate.parse("2026-10-01")
    private val today = LocalDate.parse("2026-10-10")
    private val payday = LocalDate.parse("2026-11-10")
    private fun expense(amount: Double, date: String, visibility: String = "shared") =
        Expense(amount, "x", "", "", date, visibility = visibility)

    @Test fun includesOnlyExpensesAfterSnapshotAndNotPrivate() {
        val result = calculateCashForecast(1000.0, snapshot, today, payday, listOf(
            expense(100.0, "2026-10-01"), expense(200.0, "2026-10-02"),
            expense(300.0, "2026-10-03", "private"), expense(50.0, "2026-10-04", "balances_only"),
            expense(90.0, "2026-11-11")
        ), emptyList())
        assertEquals(250.0, result.includedExpenses, 0.001)
        assertEquals(750.0, result.remaining, 0.001)
    }

    @Test fun weeklyAndBiweeklyRecurrencesCountDuesTodayThroughPayday() {
        val bills = listOf(
            RecurringBill(amount = 10.0, nextDate = "2026-10-03", frequency = "weekly"),
            RecurringBill(amount = 20.0, nextDate = "2026-10-10", frequency = "biweekly")
        )
        val result = calculateCashForecast(200.0, snapshot, today, payday, emptyList(), bills)
        assertEquals(110.0, result.includedRecurring, 0.001) // weekly: 10/10 a 07/11; biweekly: 10/10, 24/10 e 07/11
        assertEquals(90.0, result.remaining, 0.001)
    }

    @Test fun monthlyAndYearlyRecurrencesRespectHorizonAndPrivateVisibility() {
        val bills = listOf(
            RecurringBill(amount = 25.0, nextDate = "2026-10-01", frequency = "monthly"),
            RecurringBill(amount = 80.0, nextDate = "2026-10-10", frequency = "yearly"),
            RecurringBill(amount = 500.0, nextDate = "2026-10-12", frequency = "monthly", visibility = "private")
        )
        val result = calculateCashForecast(500.0, snapshot, today, payday, emptyList(), bills)
        assertEquals(105.0, result.includedRecurring, 0.001)
    }
}
