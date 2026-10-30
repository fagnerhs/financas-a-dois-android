package br.com.financasdocasal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private val Forest = Color(0xFF176B55)
private val ForestDark = Color(0xFF124D40)
private val Mint = Color(0xFFDDF3E9)
private val Canvas = Color(0xFFF5F7F5)
private val Ink = Color(0xFF1B2B27)
private val Muted = Color(0xFF62736C)
private val Amber = Color(0xFFFFF0C2)
private val Rose = Color(0xFFFFE3DF)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { LocalStore(applicationContext) }
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest,
                    onPrimary = Color.White,
                    secondary = Color(0xFF5B8C79),
                    background = Canvas,
                    surface = Color.White,
                    onSurface = Ink
                )
            ) {
                FinanceApp(store)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(store: LocalStore) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var userId by remember { mutableStateOf<String?>(null) }
    var userEmail by remember { mutableStateOf<String?>(null) }
    var space by remember { mutableStateOf<CoupleSpaceDto?>(null) }
    var checkingSession by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var syncNotice by remember { mutableStateOf("Conectando…") }
    var rows by remember { mutableStateOf(store.all()) }
    var tab by remember { mutableIntStateOf(0) }

    var budgetText by remember { mutableStateOf(prefs.getString("limits", "") ?: "") }
    var emergencyGoal by remember { mutableStateOf(prefs.getString("goal", "10000") ?: "10000") }
    var emergencySaved by remember { mutableStateOf(prefs.getString("saved", "0") ?: "0") }
    var monthlyContribution by remember { mutableStateOf(prefs.getString("monthly", "500") ?: "500") }
    val budgets = remember(budgetText) { parseBudgetText(budgetText) }

    suspend fun refreshWorkspace() {
        val uid = SupabaseCloud.currentUserId ?: return
        userId = uid
        userEmail = SupabaseCloud.currentEmail
        val loaded = withContext(Dispatchers.IO) { SupabaseCloud.findMySpace(uid) }
        space = loaded
        if (loaded != null) {
            val settingsAreDirty = prefs.getBoolean("settings_dirty", false)
            val remoteBudgets = withContext(Dispatchers.IO) { SupabaseCloud.fetchBudgets(loaded.id) }
            if (!settingsAreDirty) {
                budgetText = serializeBudgets(remoteBudgets)
                prefs.edit().putString("limits", budgetText).apply()
            }
            val settings = withContext(Dispatchers.IO) { SupabaseCloud.fetchSettings(loaded.id) }
            if (settings != null && !settingsAreDirty) {
                emergencyGoal = settings.emergencyGoal.toString()
                emergencySaved = settings.emergencySaved.toString()
                monthlyContribution = settings.monthlyContribution.toString()
                prefs.edit()
                    .putString("goal", emergencyGoal)
                    .putString("saved", emergencySaved)
                    .putString("monthly", monthlyContribution)
                    .apply()
            }
            rows = withContext(Dispatchers.IO) { store.all() }
        }
    }

    suspend fun saveLocalSettingsToCloud(spaceId: String) {
        withContext(Dispatchers.IO) {
            SupabaseCloud.saveBudgets(spaceId, budgets)
            SupabaseCloud.saveSettings(
                CoupleSettingsDto(
                    spaceId = spaceId,
                    emergencyGoal = emergencyGoal.toMoneyOrZero(),
                    emergencySaved = emergencySaved.toMoneyOrZero(),
                    monthlyContribution = monthlyContribution.toMoneyOrZero()
                )
            )
        }
        prefs.edit().putBoolean("settings_dirty", false).apply()
        syncNotice = "Configurações compartilhadas"
    }

    LaunchedEffect(Unit) {
        try {
            SupabaseCloud.awaitSessionRestore()
            userId = SupabaseCloud.currentUserId
            userEmail = SupabaseCloud.currentEmail
            if (userId != null) refreshWorkspace()
        } catch (_: Exception) {
            notice = "Sem conexão no momento. Tente novamente para carregar seu espaço."
        } finally {
            checkingSession = false
        }
    }

    LaunchedEffect(space?.id, userId) {
        val currentSpaceId = space?.id ?: return@LaunchedEffect
        val currentUserId = userId ?: return@LaunchedEffect
        syncNotice = "Sincronizando…"
        val realtime = launch {
            try {
                SupabaseCloud.observeExpenses(currentSpaceId).collect { cloudRows ->
                    withContext(Dispatchers.IO) {
                        cloudRows.forEach { store.upsertRemote(it.toLocal()) }
                    }
                    rows = withContext(Dispatchers.IO) { store.all() }
                    syncNotice = "Atualizado agora"
                }
            } catch (_: Exception) {
                syncNotice = "Conexão instável · dados salvos neste aparelho"
            }
        }
        while (isActive) {
            try {
                val updatedSpace = withContext(Dispatchers.IO) { SupabaseCloud.findMySpace(currentUserId) }
                if (updatedSpace != null) space = updatedSpace
                if (prefs.getBoolean("settings_dirty", false)) saveLocalSettingsToCloud(currentSpaceId)
                else if (tab != 3) {
                    val remoteBudgets = withContext(Dispatchers.IO) { SupabaseCloud.fetchBudgets(currentSpaceId) }
                    budgetText = serializeBudgets(remoteBudgets)
                    prefs.edit().putString("limits", budgetText).apply()
                    val settings = withContext(Dispatchers.IO) { SupabaseCloud.fetchSettings(currentSpaceId) }
                    if (settings != null) {
                        emergencyGoal = settings.emergencyGoal.toString()
                        emergencySaved = settings.emergencySaved.toString()
                        monthlyContribution = settings.monthlyContribution.toString()
                        prefs.edit()
                            .putString("goal", emergencyGoal)
                            .putString("saved", emergencySaved)
                            .putString("monthly", monthlyContribution)
                            .apply()
                    }
                }
                withContext(Dispatchers.IO) {
                    SupabaseCloud.uploadPending(currentSpaceId, currentUserId, store)
                    SupabaseCloud.fetchExpenses(currentSpaceId).forEach { store.upsertRemote(it.toLocal()) }
                }
                rows = withContext(Dispatchers.IO) { store.all() }
                syncNotice = "Sincronizado com seu par"
            } catch (_: Exception) {
                syncNotice = "Offline · alterações ficam salvas e serão enviadas depois"
            }
            delay(60_000)
        }
        realtime.cancel()
    }

    if (checkingSession) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Forest)
                Spacer(Modifier.height(12.dp))
                Text("Preparando seu espaço financeiro", color = Muted)
            }
        }
        return
    }

    if (userId == null) {
        AuthScreen(
            busy = busy,
            notice = notice,
            onAuthenticate = { email, password, createAccount ->
                scope.launch {
                    busy = true
                    notice = ""
                    try {
                        if (createAccount) SupabaseCloud.signUp(email, password)
                        else SupabaseCloud.signIn(email, password)
                        userId = SupabaseCloud.currentUserId
                        userEmail = SupabaseCloud.currentEmail
                        if (userId == null) {
                            notice = "Conta criada. Confirme o link enviado por e-mail e volte para entrar."
                        } else {
                            refreshWorkspace()
                            notice = ""
                        }
                    } catch (_: Exception) {
                        notice = if (createAccount) "Não foi possível criar a conta. Confira o e-mail, a senha e a conexão." else "Não foi possível entrar. Confira o e-mail e a senha."
                    } finally {
                        busy = false
                    }
                }
            }
        )
        return
    }

    if (space == null) {
        SetupScreen(
            busy = busy,
            notice = notice,
            onCreate = { spaceName, displayName ->
                scope.launch {
                    busy = true
                    notice = ""
                    try {
                        val created = SupabaseCloud.createSpace(spaceName, displayName)
                        prefs.edit().putBoolean("settings_dirty", true).apply()
                        refreshWorkspace()
                        if (space != null) saveLocalSettingsToCloud(created.spaceId)
                        notice = "Espaço criado. Compartilhe o convite com sua esposa."
                    } catch (_: Exception) {
                        notice = "Não foi possível criar o espaço. Confira sua conexão e tente novamente."
                    } finally {
                        busy = false
                    }
                }
            },
            onJoin = { code, displayName ->
                scope.launch {
                    busy = true
                    notice = ""
                    try {
                        SupabaseCloud.joinSpace(code, displayName)
                        refreshWorkspace()
                        notice = "Vocês já estão conectados."
                    } catch (_: Exception) {
                        notice = "Convite inválido, expirado ou já utilizado. Peça um novo código ao titular."
                    } finally {
                        busy = false
                    }
                }
            },
            onSignOut = {
                scope.launch {
                    runCatching { SupabaseCloud.signOut() }
                    userId = null
                    userEmail = null
                    space = null
                    notice = ""
                }
            }
        )
        return
    }

    val currentSpace = space!!
    val currentMonth = YearMonth.now().toString()
    val monthlyRows = rows.filter { it.date.startsWith(currentMonth) }
    val monthSpend = monthlyRows.sumOf { it.amount }
    val totalBudget = budgets.values.sum()
    val budgetRatio = if (totalBudget > 0) (monthSpend / totalBudget).toFloat().coerceIn(0f, 1f) else 0f
    val memberNames = currentSpace.members.map { it.displayName }
    val inviteExpired = runCatching { Instant.parse(currentSpace.inviteExpiresAt).isBefore(Instant.now()) }.getOrDefault(false)

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Finanças a Dois", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
                        Text(currentSpace.name, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                },
                actions = {
                    Icon(
                        imageVector = if (syncNotice.contains("Offline") || syncNotice.contains("instável")) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                        contentDescription = syncNotice,
                        tint = if (syncNotice.contains("Offline") || syncNotice.contains("instável")) Color(0xFFB36B00) else Forest,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val items = listOf(
                    Triple("Painel", Icons.Filled.Home, 0),
                    Triple("Registrar", Icons.Filled.Add, 1),
                    Triple("Insights", Icons.Filled.Assessment, 2),
                    Triple("Ajustes", Icons.Filled.Settings, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index; notice = "" },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (currentSpace.members.size < 2) {
                InviteCard(
                    code = currentSpace.inviteCode,
                    expired = inviteExpired,
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Vamos organizar nossas finanças juntas no app Finanças a Dois.\n\nSeu código de convite: ${currentSpace.inviteCode}\nEle é válido por 48 horas e usado uma única vez. Instale o app, crie sua conta e escolha ‘Tenho um código de convite’.")
                        }
                        context.startActivity(Intent.createChooser(send, "Compartilhar convite"))
                    },
                    onRefresh = {
                        scope.launch {
                            busy = true
                            try {
                                SupabaseCloud.refreshInvite(currentSpace.id)
                                refreshWorkspace()
                                notice = "Novo código gerado."
                            } catch (_: Exception) {
                                notice = "Não foi possível gerar outro convite agora."
                            } finally { busy = false }
                        }
                    }
                )
            }

            when (tab) {
                0 -> DashboardScreen(
                    expenses = monthlyRows,
                    allExpenses = rows,
                    total = monthSpend,
                    budget = totalBudget,
                    progress = budgetRatio,
                    budgets = budgets,
                    syncNotice = syncNotice,
                    notice = notice,
                    currentUserId = userId,
                    onDelete = { expense ->
                        scope.launch {
                            try {
                                if (expense.synced) SupabaseCloud.deleteExpense(currentSpace.id, expense.remoteId)
                                withContext(Dispatchers.IO) { store.delete(expense.remoteId) }
                                rows = withContext(Dispatchers.IO) { store.all() }
                                notice = "Lançamento removido."
                            } catch (_: Exception) {
                                notice = "Só quem registrou este lançamento pode removê-lo."
                            }
                        }
                    }
                )
                1 -> RegisterScreen(
                    names = memberNames,
                    notice = notice,
                    onAdd = { expense ->
                        scope.launch {
                            val authored = expense.copy(createdBy = userId)
                            withContext(Dispatchers.IO) { store.add(authored) }
                            rows = withContext(Dispatchers.IO) { store.all() }
                            notice = "Registrado neste aparelho. Sincronizando com seu par…"
                            try {
                                withContext(Dispatchers.IO) {
                                    SupabaseCloud.uploadPending(currentSpace.id, userId ?: return@withContext, store)
                                    SupabaseCloud.fetchExpenses(currentSpace.id).forEach { store.upsertRemote(it.toLocal()) }
                                }
                                rows = withContext(Dispatchers.IO) { store.all() }
                                notice = "Despesa salva e compartilhada."
                                syncNotice = "Sincronizado com seu par"
                            } catch (_: Exception) {
                                syncNotice = "Offline · será enviado automaticamente"
                            }
                        }
                    }
                )
                2 -> InsightsScreen(expenses = monthlyRows, budgets = budgets, goal = emergencyGoal.toMoneyOrZero(), saved = emergencySaved.toMoneyOrZero(), monthlyContribution = monthlyContribution.toMoneyOrZero())
                else -> SettingsScreen(
                    email = userEmail ?: "",
                    memberNames = memberNames,
                    syncNotice = syncNotice,
                    budgetText = budgetText,
                    onBudgetText = { budgetText = it },
                    goal = emergencyGoal,
                    onGoal = { emergencyGoal = it },
                    saved = emergencySaved,
                    onSaved = { emergencySaved = it },
                    monthly = monthlyContribution,
                    onMonthly = { monthlyContribution = it },
                    importText = "",
                    onImport = { raw ->
                        try {
                            withContext(Dispatchers.IO) { store.importJson(raw) }
                            rows = withContext(Dispatchers.IO) { store.all() }
                            notice = "Importação concluída. Os lançamentos serão sincronizados."
                            val uid = userId
                            if (uid != null) runCatching { SupabaseCloud.uploadPending(currentSpace.id, uid, store) }
                        } catch (_: Exception) {
                            notice = "Não foi possível importar. Verifique se o texto JSON está completo."
                        }
                    },
                    onExport = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, store.exportJson())
                        }
                        context.startActivity(Intent.createChooser(send, "Exportar backup de despesas"))
                    },
                    onSave = {
                        scope.launch {
                            prefs.edit().putString("limits", budgetText).putString("goal", emergencyGoal).putString("saved", emergencySaved).putString("monthly", monthlyContribution).apply()
                            prefs.edit().putBoolean("settings_dirty", true).apply()
                            try {
                                saveLocalSettingsToCloud(currentSpace.id)
                                notice = "Ajustes salvos e compartilhados com seu par."
                            } catch (_: Exception) {
                                notice = "Ajustes salvos neste aparelho; sincronizarão quando houver conexão."
                            }
                        }
                    },
                    onSignOut = {
                        scope.launch {
                            runCatching { SupabaseCloud.signOut() }
                            userId = null
                            userEmail = null
                            space = null
                            notice = ""
                        }
                    }
                )
            }
            if (notice.isNotBlank() && tab != 1) NoticeCard(notice)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AuthScreen(
    busy: Boolean,
    notice: String,
    onAuthenticate: (String, String, Boolean) -> Unit
) {
    var createAccount by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            BrandMark()
            Spacer(Modifier.height(22.dp))
            Text(if (createAccount) "Criem um espaço para os dois" else "Bem-vindos de volta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text("Acompanhem despesas, limites e planos em conjunto, cada um com sua própria conta.", color = Muted)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-mail") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { if (email.contains("@") && password.length >= 6) onAuthenticate(email.trim(), password, createAccount) },
                enabled = !busy && email.contains("@") && password.length >= 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (createAccount) "Criar conta" else "Entrar", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { createAccount = !createAccount; }) {
                Text(if (createAccount) "Já tenho uma conta · entrar" else "Primeira vez? Criar conta")
            }
            if (notice.isNotBlank()) NoticeCard(notice)
            Spacer(Modifier.height(16.dp))
            Text("Se a confirmação de e-mail estiver ativada, confira sua caixa de entrada antes do primeiro login.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun SetupScreen(
    busy: Boolean,
    notice: String,
    onCreate: (String, String) -> Unit,
    onJoin: (String, String) -> Unit,
    onSignOut: () -> Unit
) {
    var mode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var spaceName by remember { mutableStateOf("Finanças da nossa família") }
    var inviteCode by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.Center) {
            BrandMark()
            Spacer(Modifier.height(20.dp))
            Text(if (mode) "Entre no espaço do casal" else "Vamos conectar vocês", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(if (mode) "Digite o código que sua parceira compartilhou." else "Cada pessoa usa sua própria conta. Uma cria o espaço e envia um convite para a outra.", color = Muted)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(displayName, { displayName = it }, Modifier.fillMaxWidth(), label = { Text("Como devemos chamar você?") }, singleLine = true)
            if (!mode) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(spaceName, { spaceName = it }, Modifier.fillMaxWidth(), label = { Text("Nome do espaço compartilhado") }, singleLine = true)
            } else {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(inviteCode, { inviteCode = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Código do convite") }, singleLine = true)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { if (displayName.isNotBlank()) { if (mode) onJoin(inviteCode.trim(), displayName.trim()) else onCreate(spaceName.trim(), displayName.trim()) } },
                enabled = !busy && displayName.isNotBlank() && (!mode || inviteCode.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (mode) "Conectar ao espaço" else "Criar espaço e convite", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { mode = !mode; }) { Text(if (mode) "Prefiro criar um espaço" else "Tenho um código de convite") }
            if (notice.isNotBlank()) NoticeCard(notice)
            TextButton(onClick = onSignOut) { Text("Sair da conta") }
        }
    }
}

@Composable
private fun DashboardScreen(
    expenses: List<Expense>,
    allExpenses: List<Expense>,
    total: Double,
    budget: Double,
    progress: Float,
    budgets: Map<String, Double>,
    syncNotice: String,
    notice: String,
    currentUserId: String?,
    onDelete: (Expense) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = ForestDark), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("VISÃO DO MÊS", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(currency(total), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Orçamento definido", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall); Text(if (budget > 0) currency(budget) else "Ainda não definido", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    Column(horizontalAlignment = Alignment.End) { Text("Lançamentos", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall); Text(expenses.size.toString(), color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
                if (budget > 0) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = if (progress >= 1f) Color(0xFFFFA49B) else Color(0xFF9CE2C4), trackColor = Color.White.copy(alpha = .24f))
                    Spacer(Modifier.height(6.dp))
                    Text("Restam ${currency((budget - total).coerceAtLeast(0.0))} do orçamento geral", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Categorias", budgets.size.toString(), "com limite", Modifier.weight(1f))
            val largest = expenses.groupBy { it.category }.maxByOrNull { it.value.sumOf(Expense::amount) }?.key ?: "—"
            MetricCard("Maior gasto", largest, "neste mês", Modifier.weight(1f))
        }
        if (budgets.isNotEmpty()) {
            SectionTitle("Limites por categoria", "alerta preventivo a partir de 80%")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    budgets.toSortedMap().forEach { (category, limit) ->
                        val spent = expenses.filter { it.category == category }.sumOf { it.amount }
                        val ratio = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category, fontWeight = FontWeight.Medium, color = Ink)
                            Text("${currency(spent)} / ${currency(limit)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = when { ratio >= 1f -> Color(0xFFD45A4A); ratio >= .8f -> Color(0xFFE5A126); else -> Forest }, trackColor = Color(0xFFE8EEEA))
                        if (ratio >= .8f) Text(if (ratio >= 1f) "Limite atingido ou ultrapassado" else "Atenção: você já usou ${((spent / limit) * 100).toInt()}% deste limite", color = if (ratio >= 1f) Color(0xFFB64235) else Color(0xFF9A6500), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        SectionTitle("Movimentações recentes", "${allExpenses.size} no histórico")
        if (allExpenses.isEmpty()) EmptyCard("Comecem pelo primeiro lançamento", "Registre uma despesa pela aba Registrar; ela aparecerá automaticamente no outro celular.")
        allExpenses.take(12).forEach { expense ->
            ExpenseCard(expense, canDelete = expense.createdBy == currentUserId, onDelete = { onDelete(expense) })
        }
        if (notice.isNotBlank()) NoticeCard(notice)
        Text(syncNotice, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterScreen(names: List<String>, notice: String, onAdd: (Expense) -> Unit) {
    var manual by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var payer by remember(names) { mutableStateOf(names.firstOrNull() ?: "") }
    var category by remember { mutableStateOf(Categories.all.first()) }
    var categoryMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Registrar despesa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("Rápido no dia a dia, organizado para o planejamento de vocês.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !manual, onClick = { manual = false }, label = { Text("Por frase") })
            FilterChip(selected = manual, onClick = { manual = true }, label = { Text("Preencher manualmente") })
        }
        if (!manual) {
            Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Exemplos", color = ForestDark, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("gastei 50 no mercado", "Ana pagou 80 no delivery", "João gastou 30 de Uber ontem").forEach { example ->
                            AssistChip(onClick = { phrase = example }, label = { Text(example, fontSize = 11.sp) })
                        }
                    }
                }
            }
            OutlinedTextField(phrase, { phrase = it }, Modifier.fillMaxWidth(), label = { Text("O que vocês gastaram?") }, minLines = 2)
            Text("A categorização é feita no aparelho por regras simples. Confira antes de salvar.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    val parsed = Parser.parse(phrase, names)
                    if (parsed != null) onAdd(parsed)
                },
                enabled = phrase.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
            ) { Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Registrar despesa") }
        } else {
            OutlinedTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = { Text("Valor (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição") }, singleLine = true)
            OutlinedTextField(payer, { payer = it }, Modifier.fillMaxWidth(), label = { Text("Quem pagou?") }, singleLine = true)
            Box {
                OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Categoria: $category") }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    Categories.all.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { category = item; categoryMenu = false }) }
                }
            }
            Button(
                onClick = {
                    val parsedAmount = parsePtBrNumber(amountText)
                    if (parsedAmount != null && parsedAmount > 0) onAdd(Expense(parsedAmount, category, payer.ifBlank { "Não informado" }, description.ifBlank { category }, LocalDate.now().toString()))
                },
                enabled = (parsePtBrNumber(amountText) ?: 0.0) > 0,
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("Salvar lançamento") }
        }
        if (notice.isNotBlank()) NoticeCard(notice)
    }
}

@Composable
private fun InsightsScreen(expenses: List<Expense>, budgets: Map<String, Double>, goal: Double, saved: Double, monthlyContribution: Double) {
    val grouped = expenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf(Expense::amount) }.toList().sortedByDescending { it.second }
    val total = expenses.sumOf(Expense::amount)
    val monthsToGoal = if (goal <= saved) 0 else if (monthlyContribution > 0) kotlin.math.ceil((goal - saved) / monthlyContribution).toInt() else null
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("Uma leitura simples do mês de vocês.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Total do mês", currency(total), "despesas registradas", Modifier.weight(1f))
            MetricCard("Registros", expenses.size.toString(), "neste mês", Modifier.weight(1f))
        }
        SectionTitle("Onde o dinheiro está indo", "categorias ordenadas por valor")
        if (grouped.isEmpty()) EmptyCard("Ainda sem dados do mês", "Os insights aparecem assim que vocês começarem a registrar despesas.")
        grouped.forEachIndexed { index, (category, amount) ->
            val share = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. $category", fontWeight = FontWeight.SemiBold)
                        Text(currency(amount), color = Forest, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Forest, trackColor = Color(0xFFE8EEEA))
                    Text("${(share * 100).toInt()}% do total do mês", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
        SectionTitle("Plano do fundo de emergência", "estimativa simples, sem aconselhamento financeiro")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("${currency(saved)} guardados de ${currency(goal)}", fontWeight = FontWeight.SemiBold, color = Ink)
                val progress = if (goal > 0) (saved / goal).toFloat().coerceIn(0f, 1f) else 0f
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Forest, trackColor = Color(0xFFE8EEEA))
                Spacer(Modifier.height(10.dp))
                Text(if (monthsToGoal == null) "Definam um aporte mensal nos Ajustes para estimar o prazo." else if (monthsToGoal == 0) "Meta alcançada. Parabéns por esse marco!" else "Com ${currency(monthlyContribution)} por mês, a meta fica a aproximadamente $monthsToGoal meses." , color = Muted)
            }
        }
        if (budgets.isEmpty()) NoticeCard("Defina limites por categoria nos Ajustes para receber alertas preventivos no Painel.")
    }
}

@Composable
private fun SettingsScreen(
    email: String,
    memberNames: List<String>,
    syncNotice: String,
    budgetText: String,
    onBudgetText: (String) -> Unit,
    goal: String,
    onGoal: (String) -> Unit,
    saved: String,
    onSaved: (String) -> Unit,
    monthly: String,
    onMonthly: (String) -> Unit,
    importText: String,
    onImport: suspend (String) -> Unit,
    onExport: () -> Unit,
    onSave: () -> Unit,
    onSignOut: () -> Unit
) {
    var importValue by remember(importText) { mutableStateOf(importText) }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("Preferências compartilhadas e informações da conta.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.People, contentDescription = null, tint = Forest); Spacer(Modifier.width(8.dp)); Text("Seu espaço", fontWeight = FontWeight.SemiBold) }
                Text("Pessoas: ${memberNames.ifEmpty { listOf("Você") }.joinToString(" e ")}", color = Ink)
                Text("Conta: $email", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(syncNotice, color = Forest, style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionTitle("Orçamento mensal", "uma categoria por linha")
        OutlinedTextField(budgetText, onBudgetText, Modifier.fillMaxWidth(), label = { Text("Ex.: Mercado=1200") }, minLines = 4, supportingText = { Text("Use uma categoria do app, sinal de igual e o valor. Ex.: Moradia=2500") })
        SectionTitle("Fundo de emergência", "os valores são compartilhados")
        OutlinedTextField(goal, onGoal, Modifier.fillMaxWidth(), label = { Text("Meta total (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(saved, onSaved, Modifier.fillMaxWidth(), label = { Text("Valor já guardado (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(monthly, onMonthly, Modifier.fillMaxWidth(), label = { Text("Aporte mensal planejado (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar e compartilhar ajustes") }
        SectionTitle("Backup e transferência", "útil para importar dados da versão anterior")
        OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Share, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Exportar backup JSON") }
        OutlinedTextField(importValue, { importValue = it }, Modifier.fillMaxWidth(), label = { Text("Cole aqui um backup JSON") }, minLines = 3)
        OutlinedButton(onClick = { scope.launch { importing = true; onImport(importValue); importing = false } }, enabled = importValue.isNotBlank() && !importing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(if (importing) "Importando…" else "Importar e sincronizar") }
        Divider(color = Color(0xFFE3EAE5))
        Text("A categorização por frase funciona localmente e pode errar. Revise o lançamento antes de confiar nos totais.", style = MaterialTheme.typography.bodySmall, color = Muted)
        TextButton(onClick = onSignOut) { Icon(Icons.Filled.Logout, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Sair desta conta") }
    }
}

@Composable
private fun InviteCard(code: String, expired: Boolean, onShare: () -> Unit, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = Forest)
                Spacer(Modifier.width(8.dp))
                Text("Convide sua esposa", fontWeight = FontWeight.Bold, color = ForestDark)
            }
            Text("Quando ela entrar com o código, as despesas e os ajustes passam a aparecer nos dois aparelhos.", color = ForestDark, style = MaterialTheme.typography.bodySmall)
            Text(if (expired) "Este convite expirou." else "Código válido por 48 horas e usado uma única vez:", color = ForestDark, style = MaterialTheme.typography.labelMedium)
            if (!expired) Text(code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (expired) onRefresh else onShare, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                    Icon(if (expired) Icons.Filled.Refresh else Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (expired) "Gerar novo código" else "Compartilhar convite")
                }
                if (expired) Text("Peça para sua esposa inserir o novo código.", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Mint), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.People, contentDescription = null, tint = Forest, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun MetricCard(title: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(caption, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun ExpenseCard(expense: Expense, canDelete: Boolean, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                Text(expense.category.firstOrNull()?.toString() ?: "•", color = Forest, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.description.ifBlank { expense.category }, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("${expense.category} · ${expense.payer} · ${expense.date}", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (!expense.synced) Text("Aguardando sincronização", color = Color(0xFF9A6500), style = MaterialTheme.typography.labelSmall)
            }
            Text(currency(expense.amount), fontWeight = FontWeight.Bold, color = Ink)
            if (canDelete) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Excluir despesa", tint = Muted) }
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NoticeCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = if (message.contains("ultrapass")) Rose else Amber), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(12.dp), color = Ink, style = MaterialTheme.typography.bodySmall)
    }
}

private fun currency(value: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

internal fun String.toMoneyOrZero(): Double = parsePtBrNumber(this)?.coerceAtLeast(0.0) ?: 0.0

internal fun parseBudgetText(text: String): Map<String, Double> = text.lines().mapNotNull { line ->
    val parts = line.split("=", limit = 2)
    if (parts.size != 2) return@mapNotNull null
    val category = parts[0].trim()
    val amount = parsePtBrNumber(parts[1])
    if (category in Categories.all && amount != null && amount > 0) category to amount else null
}.toMap()

private fun serializeBudgets(budgets: Map<String, Double>): String =
    budgets.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
