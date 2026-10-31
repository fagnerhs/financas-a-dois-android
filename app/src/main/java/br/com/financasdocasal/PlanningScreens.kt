package br.com.financasdocasal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Manual planning UI. All mutations are delegated to callbacks; private bills are omitted from shared totals. */
@Composable
fun PlanningScreen(
    bills: List<RecurringBill>,
    goals: List<SavingsGoal>,
    forecastLabel: String,
    onSaveBill: (RecurringBill) -> Unit,
    onConfirmPaid: (RecurringBill) -> Unit,
    onDeleteBill: (RecurringBill) -> Unit,
    onSaveGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (SavingsGoal) -> Unit
) {
    var billEditor by remember { mutableStateOf<RecurringBill?>(null) }
    var goalEditor by remember { mutableStateOf<SavingsGoal?>(null) }
    val sharedTotal = bills.filter { it.isActive && it.visibility != "private" }.sumOf { it.amount }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Planejamento", style = MaterialTheme.typography.headlineMedium)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("Previsão", style = MaterialTheme.typography.titleMedium)
                Text(forecastLabel)
                Text("Contas compartilhadas ativas: ${currency.format(sharedTotal)}")
                Text("Contas privadas não entram neste total.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { billEditor = RecurringBill() }, modifier = Modifier.weight(1f)) { Text("Adicionar conta") }
            OutlinedButton(onClick = { goalEditor = SavingsGoal() }, modifier = Modifier.weight(1f)) { Text("Adicionar meta") }
        }
        Text("Contas recorrentes", style = MaterialTheme.typography.titleLarge)
        if (bills.isEmpty()) Text("Nenhuma conta cadastrada.")
        bills.forEach { bill ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(bill.description.ifBlank { bill.category }, style = MaterialTheme.typography.titleMedium)
                    Text("${currency.format(bill.amount)} · ${bill.category} · ${bill.frequency}")
                    Text("Próximo vencimento: ${bill.nextDate} · ${if (bill.visibility == "private") "Privada" else "Compartilhada"}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onConfirmPaid(bill) }) { Text("Confirmar que foi paga") }
                        TextButton(onClick = { billEditor = bill }) { Text("Editar") }
                        TextButton(onClick = { onDeleteBill(bill) }) { Text("Excluir") }
                    }
                    Text("Ao confirmar, o app registra uma despesa manual e avança o próximo vencimento. Nenhum pagamento é feito pelo app.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Metas", style = MaterialTheme.typography.titleLarge)
        if (goals.isEmpty()) Text("Nenhuma meta cadastrada.")
        goals.forEach { goal ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(goal.name, style = MaterialTheme.typography.titleMedium)
                    Text("${currency.format(goal.savedAmount)} de ${currency.format(goal.targetAmount)}")
                    LinearProgressIndicator(progress = { if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                    Text("Aporte mensal: ${currency.format(goal.monthlyContribution)}${goal.targetDate?.let { " · prazo $it" } ?: ""}")
                    Row {
                        TextButton(onClick = { goalEditor = goal }) { Text("Editar") }
                        TextButton(onClick = { onDeleteGoal(goal) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
    billEditor?.let { initial -> BillEditor(initial, onDismiss = { billEditor = null }) { onSaveBill(it); billEditor = null } }
    goalEditor?.let { initial -> GoalEditor(initial, onDismiss = { goalEditor = null }) { onSaveGoal(it); goalEditor = null } }
}

@Composable
private fun BillEditor(initial: RecurringBill, onDismiss: () -> Unit, onSave: (RecurringBill) -> Unit) {
    var amount by remember(initial.id) { mutableStateOf(if (initial.amount == 0.0) "" else initial.amount.toString()) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var date by remember(initial.id) { mutableStateOf(initial.nextDate) }
    var frequency by remember(initial.id) { mutableStateOf(initial.frequency) }
    var visibility by remember(initial.id) { mutableStateOf(initial.visibility) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }
    val parsedAmount = parsePtBrNumber(amount)
    val validDate = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.description.isBlank() && initial.amount == 0.0) "Nova conta" else "Editar conta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { categoryExpanded = true }) { Text("Categoria: $category") }
                DropdownMenu(categoryExpanded, { categoryExpanded = false }) { Categories.all.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { category = value; categoryExpanded = false }) } }
                OutlinedTextField(date, { date = it }, label = { Text("Próximo vencimento (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { frequencyExpanded = true }) { Text("Frequência: $frequency") }
                DropdownMenu(frequencyExpanded, { frequencyExpanded = false }) {
                    listOf("weekly", "biweekly", "monthly", "yearly").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { frequency = value; frequencyExpanded = false }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { visibility = "shared" }) { Text(if (visibility == "shared") "✓ Compartilhada" else "Compartilhada") }
                    TextButton(onClick = { visibility = "private" }) { Text(if (visibility == "private") "✓ Privada" else "Privada") }
                }
                if (date.isNotBlank() && !validDate) Text("Use uma data válida no formato AAAA-MM-DD.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(enabled = (parsedAmount ?: 0.0) > 0 && validDate && description.isNotBlank(), onClick = { onSave(initial.copy(amount = parsedAmount!!, category = category, description = description.trim(), nextDate = date, frequency = frequency, visibility = visibility)) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun GoalEditor(initial: SavingsGoal, onDismiss: () -> Unit, onSave: (SavingsGoal) -> Unit) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var target by remember(initial.id) { mutableStateOf(if (initial.targetAmount == 0.0) "" else initial.targetAmount.toString()) }
    var saved by remember(initial.id) { mutableStateOf(initial.savedAmount.toString()) }
    var monthly by remember(initial.id) { mutableStateOf(initial.monthlyContribution.toString()) }
    var date by remember(initial.id) { mutableStateOf(initial.targetDate.orEmpty()) }
    val targetValue = parsePtBrNumber(target) ?: 0.0
    val savedValue = parsePtBrNumber(saved) ?: 0.0
    val monthlyValue = parsePtBrNumber(monthly) ?: 0.0
    val dateValid = date.isBlank() || runCatching { LocalDate.parse(date); true }.getOrDefault(false)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.name.isBlank()) "Nova meta" else "Editar meta") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(target, { target = it }, label = { Text("Valor-alvo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(saved, { saved = it }, label = { Text("Já guardado") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(monthly, { monthly = it }, label = { Text("Aporte mensal") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it }, label = { Text("Prazo opcional (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            if (date.isNotBlank() && !dateValid) Text("Use uma data válida no formato AAAA-MM-DD.", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && targetValue > 0 && savedValue >= 0 && monthlyValue >= 0 && dateValid, onClick = { onSave(initial.copy(name = name.trim(), targetAmount = targetValue, savedAmount = savedValue, monthlyContribution = monthlyValue, targetDate = date.ifBlank { null })) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
