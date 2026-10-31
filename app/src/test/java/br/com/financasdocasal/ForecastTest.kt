package br.com.financasdocasal

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ForecastTest {
    private val snapshot = LocalDate.parse("2026-10-01")
    private val today = LocalDate.parse("2026-10-10")
    private val payday = LocalDate.parse("2026-11-10")
    private fun expense(amount: Double, date: String, visibility: String = "shared") =
        Expense(amount, "x", "", "", date, visibility = visibility)

    @Test fun incomeIsNotSubtractedFromManualBalance() {
        val income = Expense(800.0, "Salário", "", "", "2026-10-15", kind = EntryKind.INCOME)
        val expense = expense(125.0, "2026-10-16")
        val result = calculateCashForecast(1000.0, snapshot, today, payday, listOf(income, expense), emptyList())
        assertEquals(125.0, result.includedExpenses, 0.001)
        assertEquals(875.0, result.remaining, 0.001)
    }

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

    @Test fun closingCardStatementDoesNotReduceCashUntilPaid() {
        val closed = CreditCardCycle(id="cycle-1", spaceId="space", openedAt="2026-10-01T00:00:00Z", closedAt="2026-10-20T00:00:00Z", statementAmount=240.0)
        val result = calculateCashForecast(1000.0, snapshot, today, payday, emptyList(), emptyList(), listOf(closed))
        assertEquals(1000.0, result.remaining, 0.001)
        assertEquals(0.0, result.includedCardPayments, 0.001)
    }

    @Test fun paidStatementReducesCashOnceOnlyAfterSnapshotAndWithinPayday() {
        val cycles = listOf(
            CreditCardCycle(id="after", spaceId="space", openedAt="2026-10-01T00:00:00Z", closedAt="2026-10-20T00:00:00Z", statementAmount=240.0, paidAt="2026-10-21T12:00:00Z"),
            CreditCardCycle(id="before", spaceId="space", openedAt="2026-09-01T00:00:00Z", closedAt="2026-10-01T00:00:00Z", statementAmount=80.0, paidAt="2026-10-01T18:00:00Z"),
            CreditCardCycle(id="after-payday", spaceId="space", openedAt="2026-10-01T00:00:00Z", closedAt="2026-11-01T00:00:00Z", statementAmount=90.0, paidAt="2026-11-11T12:00:00Z")
        )
        val result = calculateCashForecast(1000.0, snapshot, today, payday, emptyList(), emptyList(), cycles)
        assertEquals(240.0, result.includedCardPayments, 0.001)
        assertEquals(760.0, result.remaining, 0.001)
    }

    @Test fun nextCreditCardCycleIdIsStableForRetryAndScopedByCycle() {
        val first = nextCreditCardCycleId("space-a", "cycle-1")
        assertEquals(first, nextCreditCardCycleId("space-a", "cycle-1"))
        assertNotEquals(first, nextCreditCardCycleId("space-b", "cycle-1"))
        assertNotEquals(first, nextCreditCardCycleId("space-a", "cycle-2"))
    }
}
