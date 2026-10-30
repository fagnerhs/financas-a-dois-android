package br.com.financasdocasal

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleParserTest {
    @Test
    fun parsesBrazilianCurrencyWithThousandsAndCents() {
        val expense = Parser.parse("gastei R$ 1.234,56 no mercado", emptyList())!!
        assertEquals(1234.56, expense.amount, 0.001)
        assertEquals("Mercado", expense.category)
    }
}
