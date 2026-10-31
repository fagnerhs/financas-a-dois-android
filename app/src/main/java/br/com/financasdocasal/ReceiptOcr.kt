package br.com.financasdocasal

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Suggestions extracted from OCR. These values are untrusted and must be reviewed by the user. */
data class ReceiptFieldSuggestions(
    val amount: Double? = null,
    /** ISO date (yyyy-MM-dd), if a complete, valid date was recognized. */
    val date: String? = null,
    val merchant: String? = null
)

/** Pure Kotlin, best-effort receipt field suggestions. This never creates or saves a transaction. */
object ReceiptFields {
    private val totalLabel = Regex("""(?i)\b(?:total(?:\s+(?:a\s+pagar|geral|da\s+nota))?|valor\s+(?:total|a\s+pagar)|vlr\.?\s*total)\b""")
    private val moneyToken = Regex("(?i)(?:R\\$\\s*)?((?:\\d{1,3}(?:\\.\\d{3})+|\\d+)(?:,\\d{1,2})?)(?![\\d,.])")
    private val dateDmy = Regex("(?<!\\d)(\\d{2})[/.-](\\d{2})[/.-](\\d{4})(?!\\d)")
    private val dateYmd = Regex("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)")

    fun parse(text: String): ReceiptFieldSuggestions {
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        val amount = findAmount(lines)
        val date = findDate(lines)
        val merchant = findMerchant(lines)
        return ReceiptFieldSuggestions(amount = amount, date = date, merchant = merchant)
    }

    private fun findAmount(lines: List<String>): Double? {
        // Require an explicit total label or BRL currency marker; avoid guessing from arbitrary numbers.
        val prioritized = lines.filter { totalLabel.containsMatchIn(it) }
        for (line in prioritized) {
            val afterLabel = line.substring(totalLabel.find(line)!!.range.last + 1)
            val match = moneyToken.find(afterLabel) ?: moneyToken.find(line) ?: continue
            val amount = parsePtBrNumber(match.groupValues[1]) ?: continue
            if (amount in 0.01..1_000_000.0) return amount
        }
        for (line in lines) {
            if (!line.contains("R$", ignoreCase = true)) continue
            val match = moneyToken.find(line) ?: continue
            val amount = parsePtBrNumber(match.groupValues[1]) ?: continue
            if (amount in 0.01..1_000_000.0) return amount
        }
        return null
    }

    private fun findDate(lines: List<String>): String? {
        for (line in lines) {
            val dmy = dateDmy.find(line)
            val ymd = dateYmd.find(line)
            val candidate = try {
                when {
                    dmy != null -> LocalDate.of(
                        dmy.groupValues[3].toInt(), dmy.groupValues[2].toInt(), dmy.groupValues[1].toInt()
                    )
                    ymd != null -> LocalDate.of(
                        ymd.groupValues[1].toInt(), ymd.groupValues[2].toInt(), ymd.groupValues[3].toInt()
                    )
                    else -> null
                }
            } catch (_: DateTimeException) {
                null
            }
            if (candidate != null) return candidate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
        return null
    }

    private fun findMerchant(lines: List<String>): String? {
        val ignored = listOf(
            "nota fiscal", "cupom fiscal", "documento auxiliar", "consumidor", "cnpj", "cpf",
            "inscricao estadual", "inscrição estadual", "data", "hora", "total", "subtotal",
            "pagamento", "forma de pagamento", "operador", "caixa", "endereco", "endereço"
        )
        return lines.firstOrNull { line ->
            val normalized = line.lowercase(Locale("pt", "BR"))
            val letters = line.count(Char::isLetter)
            letters >= 3 && line.length <= 70 &&
                ignored.none(normalized::contains) &&
                !Regex("\\d{5,}").containsMatchIn(line) &&
                !moneyToken.matches(line)
        }?.trim()?.takeIf(String::isNotBlank)
    }
}

/** Reads receipt text on-device with the bundled Latin ML Kit model. No upload or file writing occurs. */
suspend fun readReceiptText(context: Context, uri: Uri): String {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val image = InputImage.fromFilePath(context, uri)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result.text)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    } finally {
        recognizer.close()
    }
}
