package br.com.financasdocasal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lista de conferência e histórico de movimentações. O chamador atualiza as despesas apenas
 * depois de sucesso remoto. Deve ser colocada em um contêiner de altura delimitada.
 * Snapshots nunca são lidos nem apresentados; erros ficam a cargo do chamador.
 */
@Composable
fun ReviewScreen(
    expenses: List<Expense>,
    events: List<ExpenseEvent>,
    memberNames: Map<String, String>,
    currentUserId: String,
    onReviewed: (String) -> Unit
) {
    val pending = expenses.filter { it.visibility == "shared" && it.reviewStatus == "pending" && it.kind in setOf(EntryKind.EXPENSE, EntryKind.INCOME) }
    val visibleEvents = events
        .filter { it.visibility == "shared" || it.actorId == currentUserId }
        .sortedByDescending { it.occurredAt }
        .take(20)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Conferir movimentações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Movimentações compartilhadas aguardando sua conferência", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (pending.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Tudo em dia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Não há movimentações compartilhadas pendentes de conferência.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            items(pending, key = { "review-${it.remoteId}" }) { expense ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(expense.description.ifBlank { if (expense.kind == EntryKind.INCOME) "Renda" else "Despesa" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("${if (expense.kind == EntryKind.INCOME) "Entrada · Renda" else "Despesa"} · ${expense.category} · ${expense.date}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { onReviewed(expense.remoteId) }) { Text("Conferi") }
                    }
                }
            }
        }
        item { Text("Histórico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (visibleEvents.isEmpty()) {
            item { Text("Ainda não há atividades para mostrar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(visibleEvents, key = { "event-${it.id}-${it.occurredAt}" }) { event ->
                val actor = event.actorId?.let { memberNames[it] }?.takeIf { it.isNotBlank() } ?: "Pessoa do casal"
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(actor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(actionLabel(event.eventType), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(formatEventTimestamp(event.occurredAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun actionLabel(eventType: String): String = when (eventType.lowercase(Locale.ROOT)) {
    "created", "expense_created", "inserted", "insert" -> "Adicionou uma movimentação"
    "updated", "expense_updated", "update" -> "Atualizou uma movimentação"
    "deleted", "expense_deleted", "delete" -> "Removeu uma movimentação"
    "reviewed", "expense_reviewed" -> "Conferiu uma movimentação"
    else -> "Realizou uma atividade"
}

private fun formatEventTimestamp(value: String): String {
    val instant = runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    return instant?.let {
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
            .withZone(java.time.ZoneId.systemDefault()).format(it)
    } ?: value.ifBlank { "Horário não informado" }
}
