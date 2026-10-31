package br.com.financasdocasal

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object EntryKind { const val EXPENSE = "expense"; const val INCOME = "income" }
object Categories {
    val all = listOf("Alimentação", "Mercado", "Moradia", "Transporte", "Lazer", "Saúde", "Educação", "Delivery", "Outros")
}

enum class EntryVisibility { shared, private }

data class Expense(
    val amount: Double,
    val category: String,
    val payer: String,
    val description: String,
    val date: String,
    val remoteId: String = UUID.randomUUID().toString(),
    val createdBy: String? = null,
    val id: Long = 0,
    val synced: Boolean = false,
    val visibility: String = "shared",
    val creatorShareAmount: Double = amount,
    val reviewStatus: String = "pending",
    val reviewedBy: String? = null,
    val reviewedAt: String? = null,
    val kind: String = EntryKind.EXPENSE
)

data class RecurringBill(
    val id: String = UUID.randomUUID().toString(), val spaceId: String = "", val amount: Double = 0.0,
    val category: String = "Outros", val payer: String = "Não informado", val description: String = "",
    val nextDate: String = "", val frequency: String = "monthly", val isActive: Boolean = true,
    val visibility: String = "shared", val createdBy: String? = null, val synced: Boolean = false
)

data class SavingsGoal(
    val id: String = UUID.randomUUID().toString(), val spaceId: String = "", val name: String = "",
    val targetAmount: Double = 0.0, val savedAmount: Double = 0.0, val monthlyContribution: Double = 0.0,
    val targetDate: String? = null, val createdBy: String? = null, val synced: Boolean = false
)

data class CreditCardCycle(
    val id: String = UUID.randomUUID().toString(), val spaceId: String = "", val openedAt: String = "",
    val closedAt: String? = null, val statementAmount: Double = 0.0, val closedBy: String? = null,
    val paidAt: String? = null, val paidBy: String? = null, val synced: Boolean = false
)

data class CreditCardPurchase(
    val id: String = UUID.randomUUID().toString(), val spaceId: String = "", val cycleId: String = "",
    val amount: Double = 0.0, val category: String = "Outros", val description: String = "",
    val purchaseDate: String = "", val payer: String = "Não informado", val createdBy: String? = null,
    val synced: Boolean = false
)

internal fun nextCreditCardCycleId(spaceId: String, closedCycleId: String): String =
    UUID.nameUUIDFromBytes("financas-a-dois:credit-card-next:$spaceId:$closedCycleId".toByteArray(Charsets.UTF_8)).toString()

data class ExpenseEvent(
    val id: Long = 0, val expenseId: String = "", val spaceId: String = "", val actorId: String? = null,
    val eventType: String = "", val visibility: String = "shared", val snapshot: String = "{}", val occurredAt: String = ""
)

internal fun parsePtBrNumber(value: String): Double? {
    val clean = value.trim().replace("R$", "").replace(" ", "")
    val normalized = when {
        ',' in clean -> clean.replace(".", "").replace(',', '.')
        Regex("""^[+-]?\d{1,3}(?:\.\d{3})+$""").matches(clean) -> clean.replace(".", "")
        else -> clean
    }
    return normalized.toDoubleOrNull()
}

object Parser {
    fun parse(text: String, names: List<String>, now: Date = Date()): Expense? {
        val amountText = Regex("""(?:\d{1,3}(?:\.\d{3})+|\d+)(?:[,.]\d{1,2})?""").find(text)?.value ?: return null
        val amount = parsePtBrNumber(amountText) ?: return null
        if (amount <= 0) return null
        val normalized = text.lowercase(Locale("pt", "BR"))
        val category = when {
            listOf("mercado", "supermercado", "feira").any(normalized::contains) -> "Mercado"
            listOf("delivery", "ifood", "entrega").any(normalized::contains) -> "Delivery"
            listOf("uber", "99", "taxi", "ônibus", "onibus", "combustível", "combustivel").any(normalized::contains) -> "Transporte"
            listOf("aluguel", "condomínio", "condominio", "luz", "água", "agua", "internet").any(normalized::contains) -> "Moradia"
            listOf("farmácia", "farmacia", "médico", "medico", "saúde", "saude").any(normalized::contains) -> "Saúde"
            listOf("escola", "curso", "faculdade").any(normalized::contains) -> "Educação"
            listOf("cinema", "bar", "show", "passeio").any(normalized::contains) -> "Lazer"
            listOf("restaurante", "almoço", "almoco", "jantar", "café", "cafe").any(normalized::contains) -> "Alimentação"
            else -> "Outros"
        }
        val payer = names.firstOrNull { it.isNotBlank() && normalized.contains(it.lowercase(Locale("pt", "BR"))) } ?: "Não informado"
        val calendar = Calendar.getInstance().apply { time = now }
        when { normalized.contains("anteontem") -> calendar.add(Calendar.DAY_OF_YEAR, -2); normalized.contains("ontem") -> calendar.add(Calendar.DAY_OF_YEAR, -1) }
        return Expense(amount, category, payer, text.trim(), SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time))
    }
}
