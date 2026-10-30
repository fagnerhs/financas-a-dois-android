package br.com.financasdocasal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParsingTest {
    @Test
    fun preservesCloudDecimalDot() {
        assertEquals(10000.0, parsePtBrNumber("10000.0")!!, 0.001)
        assertEquals(1234.56, parsePtBrNumber("R$ 1.234,56")!!, 0.001)
    }

    @Test
    fun recognizesBrazilianThousandsWithoutCents() {
        assertEquals(10000.0, parsePtBrNumber("10.000")!!, 0.001)
    }

    @Test
    fun parsesBudgetsLoadedFromCloud() {
        val budgets = parseBudgetText("Mercado=1000.0\nMoradia=2.500,75")
        assertEquals(1000.0, budgets["Mercado"]!!, 0.001)
        assertEquals(2500.75, budgets["Moradia"]!!, 0.001)
    }

    @Test
    fun invalidValueIsIgnored() {
        assertNull(parsePtBrNumber("não é valor"))
        assertEquals(emptyMap<String, Double>(), parseBudgetText("Moradia=texto"))
    }
}
