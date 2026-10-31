package br.com.financasdocasal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

@Composable
fun TotpManagementCard() {
    val scope = rememberCoroutineScope()
    var factors by remember { mutableStateOf<List<String>>(emptyList()) }
    var enrollment by remember { mutableStateOf<TotpEnrollment?>(null) }
    var code by remember { mutableStateOf("") }
    var stepUp by remember { mutableStateOf<PendingMfaChallenge?>(null) }
    var activeDisableId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            busy = true
            try { factors = AuthSecurity.verifiedTotpFactorIds(); error = null }
            catch (_: Exception) { error = "Não foi possível concluir a operação. Tente novamente." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Autenticação em duas etapas", style = MaterialTheme.typography.titleMedium)
            Text("Opcional. Ao ativar, o login exigirá um código do seu autenticador. Guarde o segredo com segurança; ele aparece apenas temporariamente nesta tela.", style = MaterialTheme.typography.bodyMedium)
            Text("Antes de ativar, configure um método seguro para recuperar seu autenticador. Este app não emite códigos de recuperação; perder o acesso ao autenticador pode impedir o login.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (factors.isEmpty()) Text("Nenhum autenticador verificado.", style = MaterialTheme.typography.bodySmall)
            factors.forEachIndexed { index, id ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Autenticador ${index + 1} ativo", modifier = Modifier.weight(1f))
                    TextButton(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true; error = null; message = null
                            try {
                                val challenge = AuthSecurity.beginStepUpIfRequired()
                                if (challenge == null) {
                                    if (AuthSecurity.mfaIsSatisfiedForSession()) {
                                        AuthSecurity.disableTotp(id)
                                        factors = AuthSecurity.verifiedTotpFactorIds()
                                        message = "Autenticador desativado após verificação reforçada da sessão."
                                    } else {
                                        error = "Não foi possível iniciar a verificação adicional. Por segurança, o autenticador não foi desativado."
                                    }
                                } else {
                                    stepUp = challenge; activeDisableId = id; code = ""
                                }
                            } catch (_: Exception) { error = "Não foi possível concluir a operação. Tente novamente." }
                            finally { busy = false }
                        }
                    }) { Text("Desativar") }
                }
            }
            if (enrollment == null && factors.isEmpty()) {
                OutlinedButton(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true; error = null
                        try { enrollment = AuthSecurity.enrollTotp(); code = "" }
                        catch (_: Exception) { error = "Não foi possível concluir a operação. Tente novamente." }
                        finally { busy = false }
                    }
                }) { Text("Configurar autenticador (opcional)") }
            }
            enrollment?.let { pending ->
                Text("Escaneie a URI com seu aplicativo autenticador. Se necessário, insira a chave manualmente. Ainda não está ativo até confirmar o código.", style = MaterialTheme.typography.bodySmall)
                Text("URI: ${pending.uri}", style = MaterialTheme.typography.bodySmall)
                Text("Chave: ${pending.secret}", style = MaterialTheme.typography.bodyMedium)
                CodeField(code) { code = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && code.length == 6, onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                AuthSecurity.verifyTotpEnrollment(pending.factorId, code)
                                factors = AuthSecurity.verifiedTotpFactorIds()
                                message = "Autenticador ativado. O login exigirá um código."
                                enrollment = null; code = ""
                            } catch (_: Exception) { error = "Não foi possível concluir a operação. Verifique o código e tente novamente." }
                            finally { busy = false }
                        }
                    }) { Text("Confirmar e ativar") }
                    TextButton(enabled = !busy, onClick = { enrollment = null; code = "" }) { Text("Cancelar") }
                }
            }
            if (stepUp != null) {
                Text("Para desativar, confirme primeiro um código de autenticação.")
                CodeField(code) { code = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && code.length == 6, onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                AuthSecurity.verifyStepUp(stepUp!!, code)
                                AuthSecurity.disableTotp(activeDisableId!!)
                                factors = AuthSecurity.verifiedTotpFactorIds()
                                message = "Autenticador desativado."
                                stepUp = null; activeDisableId = null; code = ""
                            } catch (_: Exception) { error = "Não foi possível concluir a operação. Verifique o código e tente novamente." }
                            finally { busy = false }
                        }
                    }) { Text("Verificar e desativar") }
                    TextButton(enabled = !busy, onClick = { stepUp = null; activeDisableId = null; code = "" }) { Text("Cancelar") }
                }
            }
            TextButton(enabled = !busy, onClick = { refresh() }) { Text("Atualizar fatores") }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun CodeField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { entered -> onValueChange(entered.filter(Char::isDigit).take(6)) },
        label = { Text("Código de 6 dígitos") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun EncryptedBackupCard(onExport: suspend () -> String, onImport: suspend (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEncrypted by remember { mutableStateOf(false) }
    var pendingLegacy by remember { mutableStateOf(false) }
    var legacyWarning by remember { mutableStateOf(false) }
    val clearImportState = { pendingUri = null; pendingEncrypted = false; pendingLegacy = false; legacyWarning = false; password = ""; confirmation = "" }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val secret = password
        if (uri != null && secret.length >= EncryptedBackup.MIN_PASSWORD_LENGTH && secret == confirmation) {
            scope.launch {
                busy = true; error = null
                try {
                    val json = onExport()
                    val encrypted = withContext(Dispatchers.IO) { EncryptedBackup.encrypt(json, secret) }
                    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted.toByteArray(Charsets.UTF_8)) } ?: throw IOException("No output stream") }
                    status = "Backup cifrado salvo localmente."
                } catch (_: Exception) { error = "Não foi possível concluir a operação. Verifique o arquivo e tente novamente." }
                finally { busy = false; password = ""; confirmation = "" }
            }
        } else { password = ""; confirmation = ""; if (uri != null) error = "A senha deve ter ao menos 12 caracteres e coincidir com a confirmação." }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true; error = null
                try {
                    val input = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: throw IOException("No input stream")
                    }
                    val encrypted = runCatching { JSONObject(input).optString("format") == EncryptedBackup.FORMAT }.getOrDefault(false)
                    pendingUri = uri; pendingEncrypted = encrypted; pendingLegacy = !encrypted
                    password = ""; confirmation = ""
                    if (!encrypted) legacyWarning = true
                } catch (_: Exception) { error = "Não foi possível concluir a operação. Verifique o arquivo e tente novamente."; clearImportState() }
                finally { busy = false }
            }
        }
    }

    fun restore(allowLegacy: Boolean) {
        val uri = pendingUri ?: return
        val secret = password
        if (pendingEncrypted && secret.length < EncryptedBackup.MIN_PASSWORD_LENGTH) {
            error = "A senha deve ter ao menos 12 caracteres."; return
        }
        scope.launch {
            busy = true; error = null
            try {
                val json = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: throw IOException("No input stream")
                    EncryptedBackup.decodeMaybeEncrypted(input, secret)
                }
                if (pendingLegacy && !allowLegacy) { legacyWarning = true; return@launch }
                onImport(json)
                status = "Importação concluída. Os registros importados entram em revisão."
                clearImportState()
            } catch (_: Exception) { error = "Não foi possível concluir a operação. Verifique senha e arquivo e tente novamente." }
            finally { busy = false; password = ""; confirmation = "" }
        }
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Backup local cifrado", style = MaterialTheme.typography.titleMedium)
            Text("Exportar cria um arquivo cifrado protegido por senha. Senha mínima de 12 caracteres; ela não é armazenada.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(password, { password = it }, label = { Text("Senha do backup") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirmar senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && password.length >= EncryptedBackup.MIN_PASSWORD_LENGTH && password == confirmation, onClick = { createDocument.launch("financas-casal-backup.json") }) { Text("Exportar backup cifrado") }
            OutlinedButton(enabled = !busy, onClick = { openDocument.launch(arrayOf("application/json", "text/plain")) }) { Text("Selecionar backup para importar") }
            if (pendingUri != null) {
                Text(if (pendingEncrypted) "Arquivo cifrado selecionado; informe a senha usada na exportação." else "Arquivo JSON legado selecionado, sem proteção por senha.", style = MaterialTheme.typography.bodySmall)
                if (pendingEncrypted) OutlinedTextField(password, { password = it }, label = { Text("Senha do backup (mínimo 12 caracteres)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy && (!pendingEncrypted || password.length >= EncryptedBackup.MIN_PASSWORD_LENGTH), onClick = { if (pendingLegacy) legacyWarning = true else restore(false) }) { Text("Restaurar backup") }
                TextButton(onClick = { clearImportState(); password = "" }) { Text("Cancelar importação") }
            }
            Text("A restauração cria novos IDs e os registros entram em revisão. Importações repetidas podem duplicar dados; faça cada importação apenas uma vez.", style = MaterialTheme.typography.bodySmall)
            if (busy) CircularProgressIndicator()
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (legacyWarning) AlertDialog(
        onDismissRequest = { legacyWarning = false; if (pendingLegacy) { clearImportState(); password = "" } },
        title = { Text("Backup legado sem cifragem") },
        text = { Text("Este JSON legado pode conter dados sem proteção por senha. Só continue se confiar no arquivo. A restauração cria novos IDs, coloca registros em revisão e uma importação repetida pode duplicar dados.") },
        confirmButton = { TextButton(onClick = { legacyWarning = false; restore(true) }) { Text("Importar mesmo assim") } },
        dismissButton = { TextButton(onClick = { legacyWarning = false; clearImportState(); password = "" }) { Text("Cancelar") } }
    )

}
