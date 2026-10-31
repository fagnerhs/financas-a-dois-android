package br.com.financasdocasal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import io.github.jan.supabase.auth.handleDeeplinks
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.DateRange
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
    private val recoveryLinkReceived = mutableStateOf(false)

    private fun isRecoveryIntent(data: Uri?): Boolean {
        if (data == null) return false
        val fragmentPairs = data.fragment.orEmpty().split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "")
            val value = part.substringAfter('=', "")
            key.takeIf { it.isNotBlank() }?.let { it to value }
        }.toMap()
        val type = data.getQueryParameter("type") ?: fragmentPairs["type"]
        if (type == "recovery") return true
        val hasCode = data.getQueryParameter("code") != null || fragmentPairs.containsKey("code")
        val requestedAt = getSharedPreferences("prefs", Context.MODE_PRIVATE).getLong("password_reset_requested_at", 0L)
        return hasCode && requestedAt > 0 && System.currentTimeMillis() - requestedAt < 24L * 60 * 60 * 1000
    }

    fun clearRecoveryDeepLink() {
        recoveryLinkReceived.value = false
        val cleared = Intent(intent).apply { data = null }
        setIntent(cleared)
        getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().remove("password_reset_requested_at").apply()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recoveryLinkReceived.value = isRecoveryIntent(intent.data)
        runCatching { SupabaseCloud.client.handleDeeplinks(intent) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recoveryLinkReceived.value = isRecoveryIntent(intent.data)
        runCatching { SupabaseCloud.client.handleDeeplinks(intent) }
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
                FinanceApp(store, recoveryLinkReceived.value)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(store: LocalStore, recoveryLinkActive: Boolean = false) {
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
    var rows by remember { mutableStateOf(emptyList<Expense>()) }
    var bills by remember { mutableStateOf(emptyList<RecurringBill>()) }
    var goals by remember { mutableStateOf(emptyList<SavingsGoal>()) }
    var events by remember { mutableStateOf(emptyList<ExpenseEvent>()) }
    var tab by remember { mutableIntStateOf(0) }
    val paidBillIds = remember { mutableSetOf<String>() }
    var explainNotificationPermission by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) explainNotificationPermission = true }
    if (explainNotificationPermission) androidx.compose.material3.AlertDialog(
        onDismissRequest = { explainNotificationPermission = false },
        title = { Text("Lembretes de vencimento") },
        text = { Text("A permissão permite exibir uma notificação local antes do vencimento das contas recorrentes que você cadastrar. Os lembretes não registram pagamentos nem alteram as datas; você pode continuar usando o app sem concedê-la.") },
        confirmButton = { TextButton(onClick = { explainNotificationPermission = false; if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text("Permitir notificações") } },
        dismissButton = { TextButton(onClick = { explainNotificationPermission = false }) { Text("Agora não") } }
    )

    var budgetText by remember { mutableStateOf(prefs.getSecureString("limits", "")) }
    var emergencyGoal by remember { mutableStateOf(prefs.getSecureString("goal", "10000")) }
    var emergencySaved by remember { mutableStateOf(prefs.getSecureString("saved", "0")) }
    var monthlyContribution by remember { mutableStateOf(prefs.getSecureString("monthly", "500")) }
    val budgets = remember(budgetText) { parseBudgetText(budgetText) }
    var mfaChallenge by remember { mutableStateOf<PendingMfaChallenge?>(null) }
    var mfaCode by remember { mutableStateOf("") }
    var securityMessage by remember { mutableStateOf("") }
    var enrollment by remember { mutableStateOf<TotpEnrollment?>(null) }
    var resetMode by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var recoveryMode by remember { mutableStateOf(recoveryLinkActive) }
    var recoveryPassword by remember { mutableStateOf("") }
    var recoveryConfirm by remember { mutableStateOf("") }
    var forecastBalance by remember { mutableStateOf(prefs.getSecureString("forecast_balance", "0")) }
    var forecastAsOf by remember { mutableStateOf(prefs.getSecureString("forecast_as_of", LocalDate.now().toString())) }
    var forecastPayday by remember { mutableStateOf(prefs.getSecureString("forecast_payday", LocalDate.now().plusDays(14).toString())) }
    var monthlyIncome by remember { mutableStateOf(prefs.getSecureString("monthly_income", "0")) }

    fun visibleExpenses(values: List<Expense>) = values.filter { it.synced || it.createdBy == userId }
    fun visibleBills(values: List<RecurringBill>) = values.filter { it.synced || it.createdBy == userId }
    fun visibleGoals(values: List<SavingsGoal>) = values.filter { it.synced || it.createdBy == userId }

    LaunchedEffect(recoveryLinkActive) {
        if (recoveryLinkActive) recoveryMode = true
    }

    suspend fun refreshWorkspace() {
        val uid = SupabaseCloud.currentUserId ?: return
        val previousOwner = prefs.getString("active_user_id", null)
        val sameAccount = previousOwner == null || previousOwner == uid
        if (previousOwner == null) withContext(Dispatchers.IO) { store.claimLegacyPending(uid) }
        if (!sameAccount) {
            withContext(Dispatchers.IO) { store.clearSyncedCache() }
            rows = emptyList(); bills = emptyList(); goals = emptyList(); events = emptyList()
            budgetText = ""; emergencyGoal = "10000"; emergencySaved = "0"; monthlyContribution = "500"
            forecastBalance = "0"; forecastAsOf = LocalDate.now().toString(); forecastPayday = LocalDate.now().plusDays(14).toString(); monthlyIncome = "0"
            prefs.edit().putBoolean("settings_dirty", false).putSecureString("limits", "").putSecureString("goal", emergencyGoal).putSecureString("saved", emergencySaved).putSecureString("monthly", monthlyContribution).putSecureString("forecast_balance", forecastBalance).putSecureString("forecast_as_of", forecastAsOf).putSecureString("forecast_payday", forecastPayday).putSecureString("monthly_income", monthlyIncome).apply()
        }
        prefs.edit().putString("active_user_id", uid).apply()
        userId = uid
        userEmail = SupabaseCloud.currentEmail
        val loaded = withContext(Dispatchers.IO) { SupabaseCloud.findMySpace(uid) }
        space = loaded
        if (loaded != null) {
            val settingsAreDirty = prefs.getBoolean("settings_dirty", false)
            val remoteBudgets = withContext(Dispatchers.IO) { SupabaseCloud.fetchBudgets(loaded.id) }
            if (!settingsAreDirty) {
                budgetText = serializeBudgets(remoteBudgets)
                prefs.edit().putSecureString("limits", budgetText).apply()
            }
            val settings = withContext(Dispatchers.IO) { SupabaseCloud.fetchSettings(loaded.id) }
            if (settings != null && !settingsAreDirty) {
                emergencyGoal = settings.emergencyGoal.toString()
                emergencySaved = settings.emergencySaved.toString()
                monthlyContribution = settings.monthlyContribution.toString()
                forecastBalance = settings.availableBalance.toString()
                forecastAsOf = settings.balanceAsOf.ifBlank { LocalDate.now().toString() }
                forecastPayday = settings.nextPayday ?: LocalDate.now().plusDays(14).toString()
                monthlyIncome = settings.monthlyIncome.toString()
                prefs.edit()
                    .putSecureString("goal", emergencyGoal)
                    .putSecureString("saved", emergencySaved)
                    .putSecureString("monthly", monthlyContribution)
                    .putSecureString("forecast_balance", forecastBalance)
                    .putSecureString("forecast_as_of", forecastAsOf)
                    .putSecureString("forecast_payday", forecastPayday)
                    .putSecureString("monthly_income", monthlyIncome)
                    .apply()
            }
            withContext(Dispatchers.IO) {
                if (sameAccount) {
                    rows = visibleExpenses(store.all())
                    bills = visibleBills(store.recurringBills())
                    goals = visibleGoals(store.savingsGoals())
                } else { rows = emptyList(); bills = emptyList(); goals = emptyList() }
                bills.forEach { LocalBillReminderWorker.schedule(context, it) }
            }
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
                    monthlyContribution = monthlyContribution.toMoneyOrZero(),
                    monthlyIncome = monthlyIncome.toMoneyOrZero(),
                    availableBalance = forecastBalance.toMoneyOrZero(),
                    balanceAsOf = runCatching { LocalDate.parse(forecastAsOf).toString() }.getOrElse { LocalDate.now().toString() },
                    nextPayday = runCatching { LocalDate.parse(forecastPayday).toString() }.getOrNull()
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
            if (userId != null) {
                mfaChallenge = withContext(Dispatchers.IO) { AuthSecurity.beginStepUpIfRequired() }
                if (mfaChallenge == null) refreshWorkspace()
            }
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
                        store.reconcileVisibleExpenses(cloudRows.map { it.id }.toSet())
                        cloudRows.forEach { store.upsertRemote(it.toLocal()) }
                        val cached = visibleExpenses(store.all())
                        val balances = SupabaseCloud.fetchExpenseBalances(currentSpaceId).map { b -> Expense(b.amount, "Despesa compartilhada", "", "Despesa (somente valor e data)", b.expenseDate, remoteId="balance:${b.expenseId}", createdBy=b.createdBy, synced=true, visibility="balances_only") }
                        rows = cached + balances
                    }
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
                else if (tab != 4) {
                    val remoteBudgets = withContext(Dispatchers.IO) { SupabaseCloud.fetchBudgets(currentSpaceId) }
                    budgetText = serializeBudgets(remoteBudgets)
                    prefs.edit().putSecureString("limits", budgetText).apply()
                    val settings = withContext(Dispatchers.IO) { SupabaseCloud.fetchSettings(currentSpaceId) }
                    if (settings != null) {
                        emergencyGoal = settings.emergencyGoal.toString()
                        emergencySaved = settings.emergencySaved.toString()
                        monthlyContribution = settings.monthlyContribution.toString()
                        forecastBalance = settings.availableBalance.toString()
                        forecastAsOf = settings.balanceAsOf.ifBlank { LocalDate.now().toString() }
                        forecastPayday = settings.nextPayday ?: LocalDate.now().plusDays(14).toString()
                        monthlyIncome = settings.monthlyIncome.toString()
                        prefs.edit()
                            .putSecureString("goal", emergencyGoal)
                            .putSecureString("saved", emergencySaved)
                            .putSecureString("monthly", monthlyContribution)
                            .putSecureString("forecast_balance", forecastBalance)
                            .putSecureString("forecast_as_of", forecastAsOf)
                            .putSecureString("forecast_payday", forecastPayday)
                            .putSecureString("monthly_income", monthlyIncome)
                            .apply()
                    }
                }
                withContext(Dispatchers.IO) {
                    SupabaseCloud.uploadPending(currentSpaceId, currentUserId, store)
                    val visible = SupabaseCloud.fetchExpenses(currentSpaceId)
                    store.reconcileVisibleExpenses(visible.map { it.id }.toSet())
                    visible.forEach { store.upsertRemote(it.toLocal()) }
                    val remoteBills = SupabaseCloud.fetchRecurringBills(currentSpaceId)
                    store.reconcileVisibleRecurringBills(remoteBills.map { it.id }.toSet())
                    remoteBills.forEach { store.upsertRemoteRecurringBill(it.toLocal()) }
                    val remoteGoals = SupabaseCloud.fetchSavingsGoals(currentSpaceId)
                    store.reconcileVisibleSavingsGoals(remoteGoals.map { it.id }.toSet())
                    remoteGoals.forEach { store.upsertRemoteSavingsGoal(it.toLocal()) }
                    SupabaseCloud.uploadPendingRecurringBills(currentSpaceId, currentUserId, store)
                    SupabaseCloud.uploadPendingSavingsGoals(currentSpaceId, currentUserId, store)
                    val remoteEvents = SupabaseCloud.fetchExpenseEvents(currentSpaceId).map { it.toLocal() }
                    events = store.expenseEvents(remoteEvents)
                }
                rows = withContext(Dispatchers.IO) { visibleExpenses(store.all()) + SupabaseCloud.fetchExpenseBalances(currentSpaceId).map { b -> Expense(b.amount, "Despesa compartilhada", "", "Despesa (somente valor e data)", b.expenseDate, remoteId="balance:${b.expenseId}", createdBy=b.createdBy, synced=true, visibility="balances_only") } }
                val refreshedBills = withContext(Dispatchers.IO) { visibleBills(store.recurringBills()) }
                if (refreshedBills != bills) refreshedBills.forEach { LocalBillReminderWorker.schedule(context, it) }
                bills = refreshedBills
                goals = withContext(Dispatchers.IO) { visibleGoals(store.savingsGoals()) }
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

    if (recoveryMode && userId != null) {
        RecoveryPasswordScreen(recoveryPassword, { recoveryPassword = it }, recoveryConfirm, { recoveryConfirm = it }, securityMessage, busy) {
            scope.launch { busy = true; securityMessage = ""; try { require(recoveryPassword.length >= 8 && recoveryPassword == recoveryConfirm); withContext(Dispatchers.IO) { AuthSecurity.updateRecoveredPassword(recoveryPassword) }; (context as? MainActivity)?.clearRecoveryDeepLink(); recoveryPassword = ""; recoveryConfirm = ""; recoveryMode = false; securityMessage = "Senha atualizada com sucesso." } catch (_: Exception) { securityMessage = "As senhas devem coincidir e ter ao menos 8 caracteres; confira sua conexão e tente novamente." } finally { busy = false } }
        }
        return
    }
    if (mfaChallenge != null) {
        SecurityChallengeScreen(mfaCode, { mfaCode = it }, securityMessage, busy) {
            scope.launch { busy = true; securityMessage = ""; try { withContext(Dispatchers.IO) { AuthSecurity.verifyStepUp(mfaChallenge!!, mfaCode) }; mfaChallenge = null; refreshWorkspace(); securityMessage = "Verificação concluída." } catch (_: Exception) { securityMessage = "Código inválido ou expirado. Tente novamente." } finally { busy = false } }
        }; return
    }
    if (userId == null) {
        if (resetMode) { PasswordResetScreen(resetEmail, { resetEmail = it }, securityMessage, busy, { scope.launch { busy=true; try { AuthSecurity.sendPasswordReset(resetEmail); prefs.edit().putLong("password_reset_requested_at", System.currentTimeMillis()).apply(); securityMessage = "Se o endereço estiver cadastrado, enviaremos instruções para redefinir sua senha." } catch (_: Exception) { securityMessage = "Não foi possível solicitar a redefinição agora." } finally { busy=false } } }, { resetMode=false; securityMessage="" }); return }
        AuthScreen(
            busy = busy,
            notice = notice,
            onReset = { email -> resetEmail = email; resetMode = true; securityMessage = "" },
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
                            mfaChallenge = withContext(Dispatchers.IO) { AuthSecurity.beginStepUpIfRequired() }
                            if (mfaChallenge == null) refreshWorkspace()
                            notice = ""
                        }
                    } catch (error: Exception) {
                        notice = authErrorNotice(error, creatingAccount = createAccount)
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
    val monthlyRows = rows.filter { it.date.startsWith(currentMonth) && it.visibility != "private" }
    val monthSpend = monthlyRows.filter { it.visibility == "shared" || it.visibility == "balances_only" }.sumOf { it.amount }
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
                    Triple("Planejar", Icons.Filled.DateRange, 3),
                    Triple("Ajustes", Icons.Filled.Settings, 4)
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
                0 -> { DashboardScreen(
                    expenses = monthlyRows.filter { it.visibility != "private" },
                    allExpenses = rows.filter { it.visibility != "private" || it.createdBy == userId },
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
                                rows = withContext(Dispatchers.IO) { visibleExpenses(store.all()) }
                                notice = "Lançamento removido."
                            } catch (_: Exception) {
                                notice = "Só quem registrou este lançamento pode removê-lo."
                            }
                        }
                    }
                )
                if (userId != null) Box(Modifier.fillMaxWidth().height(500.dp)) { ReviewScreen(rows, events, currentSpace.members.associate { it.userId to it.displayName }, userId!!) { expenseId ->
                    scope.launch {
                        try {
                            SupabaseCloud.markExpenseReviewed(currentSpace.id, expenseId)
                            withContext(Dispatchers.IO) {
                                val visible = SupabaseCloud.fetchExpenses(currentSpace.id)
                                store.reconcileVisibleExpenses(visible.map { it.id }.toSet())
                                visible.forEach { store.upsertRemote(it.toLocal()) }
                                events = store.expenseEvents(SupabaseCloud.fetchExpenseEvents(currentSpace.id).map { it.toLocal() })
                                rows = visibleExpenses(store.all())
                            }
                            notice = "Despesa conferida."
                        } catch (_: Exception) { notice = "Não foi possível conferir agora. Verifique a conexão e tente novamente." }
                    }
                }
                } }
                1 -> RegisterScreen(
                    names = memberNames,
                    currentUserId = userId,
                    notice = notice,
                    onAdd = { expense ->
                        scope.launch {
                            val authored = expense.copy(createdBy = userId)
                            withContext(Dispatchers.IO) { store.add(authored) }
                            rows = withContext(Dispatchers.IO) { visibleExpenses(store.all()) }
                            notice = "Registrado neste aparelho. Sincronizando com seu par…"
                            try {
                                withContext(Dispatchers.IO) {
                                    SupabaseCloud.uploadPending(currentSpace.id, userId ?: return@withContext, store)
                                    val visible = SupabaseCloud.fetchExpenses(currentSpace.id)
                                    store.reconcileVisibleExpenses(visible.map { it.id }.toSet())
                                    visible.forEach { store.upsertRemote(it.toLocal()) }
                                }
                                rows = withContext(Dispatchers.IO) { visibleExpenses(store.all()) }
                                notice = "Despesa salva e compartilhada."
                                syncNotice = "Sincronizado com seu par"
                            } catch (_: Exception) {
                                syncNotice = "Offline · será enviado automaticamente"
                            }
                        }
                    }
                )
                2 -> InsightsScreen(expenses = rows, recurringBills = bills, budgets = budgets, goal = emergencyGoal.toMoneyOrZero(), saved = emergencySaved.toMoneyOrZero(), monthlyContribution = monthlyContribution.toMoneyOrZero(), forecastBalanceText = forecastBalance, onForecastBalance = { forecastBalance = it }, forecastSnapshotText = forecastAsOf, onForecastSnapshot = { forecastAsOf = it }, forecastPaydayText = forecastPayday, onForecastPayday = { forecastPayday = it }, monthlyIncome = monthlyIncome, onMonthlyIncome = { monthlyIncome = it })
                3 -> PlanningScreen(
                    bills = bills, goals = goals,
                    forecastLabel = "Projeção manual: saldo informado em $forecastAsOf; recebimento previsto para $forecastPayday. Renda mensal informada de ${currency(monthlyIncome.toMoneyOrZero())} não é somada antes do recebimento.",
                    onSaveBill = { edited -> scope.launch {
                        val saved = withContext(Dispatchers.IO) { store.addRecurringBill(edited.copy(spaceId=currentSpace.id, createdBy=userId, synced=false)) }
                        bills = withContext(Dispatchers.IO) { visibleBills(store.recurringBills()) }
                        LocalBillReminderWorker.schedule(context, saved)
                        try { withContext(Dispatchers.IO) { SupabaseCloud.upsertRecurringBill(currentSpace.id, userId!!, saved); store.markRecurringBillSynced(saved.id) }; bills=withContext(Dispatchers.IO){visibleBills(store.recurringBills())}; notice="Conta salva." }
                        catch (_: Exception) { notice="Conta salva neste aparelho; será sincronizada quando houver conexão." }
                    } },
                    onConfirmPaid = { bill -> scope.launch {
                        val occurrenceKey = "${bill.id}:${bill.nextDate}"
                        if (!paidBillIds.add(occurrenceKey)) return@launch
                        var expenseRecorded = false
                        try {
                            val occurrenceId = java.util.UUID.nameUUIDFromBytes("financas-a-dois:recurring-payment:${currentSpace.id}:${bill.id}:${bill.nextDate}".toByteArray(Charsets.UTF_8)).toString()
                            val paid = Expense(bill.amount,bill.category,bill.payer,bill.description,bill.nextDate,remoteId=occurrenceId,createdBy=userId,visibility=bill.visibility,creatorShareAmount=bill.amount,reviewStatus="pending")
                            val next = runCatching { val date=LocalDate.parse(bill.nextDate); when(bill.frequency){"weekly"->date.plusWeeks(1);"biweekly"->date.plusWeeks(2);"yearly"->date.plusYears(1);else->date.plusMonths(1)}.toString() }.getOrElse { bill.nextDate }
                            val advanced=withContext(Dispatchers.IO){store.recordRecurringPayment(paid,bill.copy(nextDate=next,createdBy=userId,synced=false)).second}
                            expenseRecorded = true
                            LocalBillReminderWorker.schedule(context,advanced)
                            try { withContext(Dispatchers.IO){SupabaseCloud.uploadPending(currentSpace.id,userId!!,store);SupabaseCloud.upsertRecurringBill(currentSpace.id,userId!!,advanced);store.markRecurringBillSynced(advanced.id)}; notice="Pagamento registrado e próxima data atualizada." }
                            catch (_:Exception){ notice="Pagamento registrado neste aparelho; será sincronizado quando houver conexão." }
                            rows=withContext(Dispatchers.IO){visibleExpenses(store.all())}; bills=withContext(Dispatchers.IO){visibleBills(store.recurringBills())}
                        } catch (_: Exception) {
                            notice = "Não foi possível registrar a confirmação. Tente novamente."
                            if (!expenseRecorded) paidBillIds.remove(occurrenceKey)
                        }
                    } },
                    onDeleteBill = { bill -> scope.launch { try { if(bill.synced) SupabaseCloud.deleteRecurringBill(currentSpace.id,bill.id); withContext(Dispatchers.IO){store.deleteRecurringBill(bill.id)}; LocalBillReminderWorker.cancel(context,bill.id); bills=withContext(Dispatchers.IO){visibleBills(store.recurringBills())}; notice="Conta excluída." } catch (_:Exception){notice="Não foi possível excluir a conta online; ela foi mantida neste aparelho."} } },
                    onSaveGoal = { goal -> scope.launch { val saved=withContext(Dispatchers.IO){store.addSavingsGoal(goal.copy(spaceId=currentSpace.id,createdBy=userId,synced=false))}; goals=withContext(Dispatchers.IO){visibleGoals(store.savingsGoals())}; try {withContext(Dispatchers.IO){SupabaseCloud.upsertSavingsGoal(currentSpace.id,userId!!,saved);store.markSavingsGoalSynced(saved.id)};goals=withContext(Dispatchers.IO){visibleGoals(store.savingsGoals())};notice="Meta salva."}catch(_:Exception){notice="Meta salva neste aparelho; será sincronizada quando houver conexão."} } },
                    onDeleteGoal = { goal -> scope.launch { try {if(goal.synced)SupabaseCloud.deleteSavingsGoal(currentSpace.id,goal.id);withContext(Dispatchers.IO){store.deleteSavingsGoal(goal.id)};goals=withContext(Dispatchers.IO){visibleGoals(store.savingsGoals())};notice="Meta excluída."}catch(_:Exception){notice="Não foi possível excluir a meta online; ela foi mantida neste aparelho."} } }
                )
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
                    onExport = {
                        val owner = userId ?: throw IllegalStateException("Conta não autenticada")
                        withContext(Dispatchers.IO) { store.exportJson(owner) }
                    },
                    onImport = { raw ->
                        try {
                            withContext(Dispatchers.IO) { store.importJson(raw,userId) }
                            rows = withContext(Dispatchers.IO) { visibleExpenses(store.all()) }
                            bills = withContext(Dispatchers.IO) { visibleBills(store.recurringBills()) }
                            goals = withContext(Dispatchers.IO) { visibleGoals(store.savingsGoals()) }
                            bills.forEach { LocalBillReminderWorker.schedule(context, it) }
                            notice = "Importação concluída. Os registros serão sincronizados."
                            val uid = userId
                            if (uid != null) runCatching { withContext(Dispatchers.IO) { SupabaseCloud.uploadPending(currentSpace.id, uid, store); SupabaseCloud.uploadPendingRecurringBills(currentSpace.id,uid,store); SupabaseCloud.uploadPendingSavingsGoals(currentSpace.id,uid,store) } }
                            rows = withContext(Dispatchers.IO) { visibleExpenses(store.all()) }
                            bills = withContext(Dispatchers.IO) { visibleBills(store.recurringBills()) }
                            goals = withContext(Dispatchers.IO) { visibleGoals(store.savingsGoals()) }
                        } catch (_: Exception) {
                            notice = "Não foi possível importar. Verifique se o texto JSON está completo."
                        }
                    },
                    onSave = {
                        scope.launch {
                            forecastAsOf = runCatching { LocalDate.parse(forecastAsOf).toString() }.getOrElse { LocalDate.now().toString() }
                            forecastPayday = runCatching { LocalDate.parse(forecastPayday).toString() }.getOrElse { LocalDate.now().plusDays(14).toString() }
                            prefs.edit().putSecureString("limits", budgetText).putSecureString("goal", emergencyGoal).putSecureString("saved", emergencySaved).putSecureString("monthly", monthlyContribution).putSecureString("forecast_balance", forecastBalance).putSecureString("forecast_as_of", forecastAsOf).putSecureString("forecast_payday", forecastPayday).putSecureString("monthly_income", monthlyIncome).apply()
                            prefs.edit().putBoolean("settings_dirty", true).apply()
                            try {
                                saveLocalSettingsToCloud(currentSpace.id)
                                notice = "Ajustes salvos e compartilhados com seu par."
                            } catch (_: Exception) {
                                notice = "Ajustes salvos neste aparelho; sincronizarão quando houver conexão."
                            }
                        }
                    },
                    onGlobalSignOut = {
                        scope.launch {
                            try {
                                SupabaseCloud.signOutEverywhere()
                                userId = null; userEmail = null; space = null; rows = emptyList(); bills = emptyList(); goals = emptyList(); events = emptyList()
                                notice = "Sessões encerradas em todos os dispositivos."
                            } catch (_: Exception) {
                                notice = "Não foi possível revogar sessões remotamente (possível falta de conexão). Você ainda pode sair apenas deste aparelho."
                            }
                        }
                    },
                    onSignOut = {
                        scope.launch {
                            runCatching { SupabaseCloud.signOut() }
                            userId = null
                            userEmail = null
                            space = null
                            rows = emptyList(); bills = emptyList(); goals = emptyList(); events = emptyList()
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
    onReset: (String) -> Unit,
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
            TextButton(onClick = { onReset(email.trim()) }) { Text("Esqueci minha senha") }
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
private fun PasswordResetScreen(email:String,onEmail:(String)->Unit,notice:String,busy:Boolean,onSend:()->Unit,onBack:()->Unit) {
    Column(Modifier.fillMaxSize().background(Canvas).padding(24.dp), verticalArrangement=Arrangement.Center) { BrandMark(); Spacer(Modifier.height(18.dp)); Text("Redefinir senha", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold); Text("Enviaremos instruções para o e-mail da sua conta.", color=Muted); Spacer(Modifier.height(16.dp)); OutlinedTextField(email,onEmail,Modifier.fillMaxWidth(),label={Text("E-mail")},singleLine=true); Button(onClick=onSend,enabled=!busy && email.contains("@"),modifier=Modifier.fillMaxWidth().padding(top=12.dp)){Text("Enviar instruções")}; if(notice.isNotBlank()) NoticeCard(notice); TextButton(onClick=onBack){Text("Voltar ao login")} }
}
@Composable
private fun SecurityChallengeScreen(code:String,onCode:(String)->Unit,notice:String,busy:Boolean,onVerify:()->Unit) {
    Column(Modifier.fillMaxSize().background(Canvas).padding(24.dp), verticalArrangement=Arrangement.Center) { BrandMark(); Text("Confirme sua identidade",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text("Digite o código de seis dígitos do seu autenticador para carregar os dados do espaço.",color=Muted); OutlinedTextField(code,{onCode(it.filter(Char::isDigit).take(6))},Modifier.fillMaxWidth(),label={Text("Código TOTP")},singleLine=true); Button(onClick=onVerify,enabled=!busy&&code.length==6,modifier=Modifier.fillMaxWidth()){Text("Verificar")}; if(notice.isNotBlank()) NoticeCard(notice) }
}

@Composable
private fun RecoveryPasswordScreen(password: String, onPassword: (String) -> Unit, confirmation: String, onConfirmation: (String) -> Unit, notice: String, busy: Boolean, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        BrandMark(); Spacer(Modifier.height(18.dp)); Text("Escolha uma nova senha", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("O link de recuperação foi validado. Use pelo menos 8 caracteres.", color = Muted); Spacer(Modifier.height(16.dp))
        OutlinedTextField(password, onPassword, Modifier.fillMaxWidth(), label = { Text("Nova senha") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
        OutlinedTextField(confirmation, onConfirmation, Modifier.fillMaxWidth(), label = { Text("Confirme a nova senha") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
        Button(onClick = onSave, enabled = !busy && password.length >= 8 && password == confirmation, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (busy) "Salvando…" else "Atualizar senha") }
        if (notice.isNotBlank()) NoticeCard(notice)
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
            ExpenseCard(expense, canDelete = expense.createdBy == currentUserId && expense.visibility != "balances_only", onDelete = { onDelete(expense) })
        }
        if (notice.isNotBlank()) NoticeCard(notice)
        Text(syncNotice, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterScreen(names: List<String>, currentUserId: String?, notice: String, onAdd: (Expense) -> Unit) {
    var manual by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var expenseDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var description by remember { mutableStateOf("") }
    var payer by remember(names) { mutableStateOf(names.firstOrNull() ?: "") }
    var category by remember { mutableStateOf(Categories.all.first()) }
    var categoryMenu by remember { mutableStateOf(false) }
    var visibility by remember { mutableStateOf("shared") }
    var shareText by remember { mutableStateOf("") }
    var receiptNotice by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            receiptNotice = "Lendo recibo neste aparelho…"
            try {
                val text = withContext(Dispatchers.IO) { readReceiptText(context, uri) }
                val suggestion = ReceiptFields.parse(text)
                suggestion.amount?.let { amountText = String.format(Locale.US, "%.2f", it) }
                suggestion.date?.let { expenseDate = it }
                suggestion.merchant?.let { description = it.take(80) }
                manual = true
                receiptNotice = buildString { append("Sugestões não salvas automaticamente. "); append(suggestion.amount?.let { "Valor ${currency(it)}" } ?: "Valor não identificado"); suggestion.date?.let { append(" · data $it") }; suggestion.merchant?.let { append(" · $it") }; append(". Confira e complete os campos.") }
            } catch (_: Exception) { receiptNotice = "Não foi possível ler a imagem. Nenhuma foto foi salva ou enviada." }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Registrar despesa", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("Rápido no dia a dia, organizado para o planejamento de vocês.", color = Muted)
        OutlinedButton(onClick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Ler recibo (OCR no aparelho)") }
        if (receiptNotice.isNotBlank()) NoticeCard(receiptNotice)
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
            OutlinedTextField(expenseDate, { expenseDate = it }, Modifier.fillMaxWidth(), label = { Text("Data (AAAA-MM-DD)") }, singleLine = true, supportingText = { Text("Confira a data sugerida pelo recibo antes de salvar.") })
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Descrição") }, singleLine = true)
            OutlinedTextField(payer, { payer = it }, Modifier.fillMaxWidth(), label = { Text("Quem pagou?") }, singleLine = true)
            Text("Privacidade do lançamento", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("shared" to "Compartilhado", "balances_only" to "Somente saldo", "private" to "Somente eu").forEach { (v, label) -> FilterChip(selected = visibility == v, onClick = { visibility = v }, label = { Text(label) }) } }
            if (visibility != "private") { OutlinedTextField(shareText, { shareText = it }, Modifier.fillMaxWidth(), label = { Text("Minha parte (R$)") }, supportingText = { Text("O restante será atribuído ao parceiro. Deixe vazio para atribuir tudo a você.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            Box {
                OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Categoria: $category") }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    Categories.all.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { category = item; categoryMenu = false }) }
                }
            }
            Button(
                onClick = {
                    val parsedAmount = parsePtBrNumber(amountText)
                    val parsedDate = runCatching { LocalDate.parse(expenseDate).toString() }.getOrNull()
                    if (parsedAmount != null && parsedAmount > 0 && parsedDate != null) { val share = if (visibility == "private") parsedAmount else (parsePtBrNumber(shareText) ?: parsedAmount); if (share in 0.0..parsedAmount) onAdd(Expense(parsedAmount, category, payer.ifBlank { "Não informado" }, description.ifBlank { category }, parsedDate, createdBy=currentUserId, visibility=visibility, creatorShareAmount=share)) }
                },
                enabled = (parsePtBrNumber(amountText) ?: 0.0) > 0 && runCatching { LocalDate.parse(expenseDate) }.isSuccess && (visibility == "private" || (parsePtBrNumber(shareText) ?: (parsePtBrNumber(amountText) ?: 0.0)) in 0.0..(parsePtBrNumber(amountText) ?: 0.0)),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
            ) { Text("Salvar lançamento") }
        }
        if (notice.isNotBlank()) NoticeCard(notice)
    }
}

@Composable
private fun InsightsScreen(expenses: List<Expense>, recurringBills: List<RecurringBill>, budgets: Map<String, Double>, goal: Double, saved: Double, monthlyContribution: Double, forecastBalanceText: String, onForecastBalance: (String) -> Unit, forecastSnapshotText: String, onForecastSnapshot: (String) -> Unit, forecastPaydayText: String, onForecastPayday: (String) -> Unit, monthlyIncome: String, onMonthlyIncome: (String) -> Unit) {
    val grouped = expenses.filter { it.visibility == "shared" }.groupBy { it.category }.mapValues { entry -> entry.value.sumOf(Expense::amount) }.toList().sortedByDescending { it.second }
    val total = expenses.filter { it.visibility == "shared" || it.visibility == "balances_only" }.sumOf(Expense::amount)
    val monthsToGoal = if (goal <= saved) 0 else if (monthlyContribution > 0) kotlin.math.ceil((goal - saved) / monthlyContribution).toInt() else null
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("Uma leitura simples do mês de vocês.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Total do mês", currency(total), "despesas registradas", Modifier.weight(1f))
            MetricCard("Registros", expenses.count { it.visibility == "shared" || it.visibility == "balances_only" }.toString(), "neste mês", Modifier.weight(1f))
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
        SectionTitle("Previsão até o próximo recebimento", "projeção manual; não é saldo bancário")
        OutlinedTextField(forecastBalanceText, onForecastBalance, Modifier.fillMaxWidth(), label = { Text("Saldo informado no snapshot (R$)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(forecastSnapshotText, onForecastSnapshot, Modifier.fillMaxWidth(), label = { Text("Data de referência (AAAA-MM-DD)") }, singleLine = true)
        OutlinedTextField(forecastPaydayText, onForecastPayday, Modifier.fillMaxWidth(), label = { Text("Próxima data de recebimento") }, singleLine = true)
        OutlinedTextField(monthlyIncome, onMonthlyIncome, Modifier.fillMaxWidth(), label = { Text("Renda mensal informada (R$; não somada antes do recebimento)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        val forecast = runCatching { calculateCashForecast(forecastBalanceText.toMoneyOrZero(), LocalDate.parse(forecastSnapshotText), LocalDate.now(), LocalDate.parse(forecastPaydayText), expenses, recurringBills) }.getOrNull()
        Card(colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (forecast == null) "Informe saldo e datas ISO válidas; recebimento deve ser hoje ou futuro." else "Estimativa para $forecastPaydayText: ${currency(forecast.remaining)}", fontWeight = FontWeight.Bold, color = ForestDark)
                if (forecast != null) Text("Parte do saldo informado na data $forecastSnapshotText; subtrai despesas compartilhadas/somente-saldo após essa referência (${currency(forecast.includedExpenses)}) e recorrências compartilhadas cadastradas entre hoje e o recebimento (${currency(forecast.includedRecurring)}). Renda mensal informada: ${currency(monthlyIncome.toMoneyOrZero())}, não adicionada antes do recebimento. Itens não cadastrados não entram.", color = ForestDark, style = MaterialTheme.typography.bodySmall)
                Text("Usa somente dados informados aqui; não consulta bancos e não é saldo bancário real.", color = Muted, style = MaterialTheme.typography.bodySmall)
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
    onExport: suspend () -> String,
    onImport: suspend (String) -> Unit,
    onSave: () -> Unit,
    onSignOut: () -> Unit,
    onGlobalSignOut: () -> Unit
) {
    var confirmGlobalSignOut by remember { mutableStateOf(false) }
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
        TotpManagementCard()
        EncryptedBackupCard(onExport = onExport, onImport = onImport)
        Divider(color = Color(0xFFE3EAE5))
        Text("A categorização por frase funciona localmente e pode errar. Revise o lançamento antes de confiar nos totais.", style = MaterialTheme.typography.bodySmall, color = Muted)
        NoticeCard("A biometria opcional ainda não está disponível. O bloqueio biométrico não substitui a autenticação da conta. Para excluir dados compartilhados ou a conta, use os controles oficiais do serviço; este app não apaga dados do cônjuge.")
        TextButton(onClick = onSignOut) { Icon(Icons.Filled.Logout, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Sair apenas deste aparelho") }
        OutlinedButton(onClick = { confirmGlobalSignOut = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Encerrar sessões em todos os dispositivos") }
        Text("A revogação global exige internet. Se estiver offline, o logout local continua disponível; dados compartilhados não são apagados.", style = MaterialTheme.typography.bodySmall, color = Muted)
        if (confirmGlobalSignOut) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmGlobalSignOut = false },
            title = { Text("Encerrar todas as sessões?") },
            text = { Text("Isso desconectará esta conta de todos os dispositivos. Não remove os dados do espaço nem da conta. Se a solicitação remota falhar, somente o logout local será possível.") },
            confirmButton = { TextButton(onClick = { confirmGlobalSignOut = false; onGlobalSignOut() }) { Text("Encerrar em todos") } },
            dismissButton = { TextButton(onClick = { confirmGlobalSignOut = false }) { Text("Cancelar") } }
        )
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.brand_mark),
                contentDescription = "Símbolo do Finanças a Dois",
                modifier = Modifier.size(58.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Finanças a Dois", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Forest)
            Text("Planejamento em parceria", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
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
                if (expense.visibility == "balances_only") { Text("Despesa compartilhada", fontWeight = FontWeight.SemiBold, color = Ink); Text(expense.date, color = Muted, style = MaterialTheme.typography.labelSmall) }
                else { Text(expense.description.ifBlank { expense.category }, fontWeight = FontWeight.SemiBold, color = Ink); Text("${expense.category} · ${expense.payer} · ${expense.date}", color = Muted, style = MaterialTheme.typography.labelSmall) }
                if (expense.visibility == "private") Text("Somente você", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (expense.visibility == "shared" && expense.creatorShareAmount < expense.amount) Text("Registrador: ${currency(expense.creatorShareAmount)} · parceiro: ${currency(expense.amount - expense.creatorShareAmount)}", color = Muted, style = MaterialTheme.typography.labelSmall)
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
