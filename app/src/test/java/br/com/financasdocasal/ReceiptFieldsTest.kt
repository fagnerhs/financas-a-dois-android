package br.com.financasdocasal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptFieldsTest {
    @Test
    fun extractsBrazilianTotalValidDateAndMerchant() {
        val fields = ReceiptFields.parse(
            "SUPERMERCADO BOM PRECO\nCNPJ 12.345.678/0001-90\nData: 05/03/2026\nTOTAL A PAGAR R$ 1.234,56"
        )

        assertEquals(1234.56, fields.amount!!, 0.001)
        assertEquals("2026-03-05", fields.date)
        assertEquals("SUPERMERCADO BOM PRECO", fields.merchant)
    }

    @Test
    fun rejectsInvalidCalendarDatesAndUnlabeledNumbers() {
        val fields = ReceiptFields.parse("LOJA CENTRAL\nData 31/02/2026\nItens 123,45")

        assertNull(fields.amount)
        assertNull(fields.date)
        assertEquals("LOJA CENTRAL", fields.merchant)
    }

    @Test
    fun canSuggestAmountFromExplicitBrazilianCurrencyMarker() {
        val fields = ReceiptFields.parse("PADARIA DO BAIRRO\nPagamento no cartão\nR$ 27,90")

        assertEquals(27.90, fields.amount!!, 0.001)
        assertEquals("PADARIA DO BAIRRO", fields.merchant)
    }

    @Test
    fun doesNotInterpretPaymentStatusAsAnExpenseOrSaveAnything() {
        val fields = ReceiptFields.parse("CONTA DE ENERGIA\nFATURA PAGA\nVencimento 10/04/2026")

        assertNull(fields.amount)
        assertEquals("2026-04-10", fields.date)
        assertEquals("CONTA DE ENERGIA", fields.merchant)
    }
}
