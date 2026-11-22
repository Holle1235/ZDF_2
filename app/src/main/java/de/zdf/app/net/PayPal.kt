package de.zdf.app.net

import android.util.Base64
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit

data class PpTxn(
    val id: String,
    val amountCents: Long,
    val currency: String,
    val date: String,
    val counterparty: String,
    val purpose: String,
    val code: String,
    val pending: Boolean,
)

/**
 * PayPal REST API (Transaction Search + Balances).
 * Benötigt eine eigene "Live"-App unter developer.paypal.com mit aktivierter
 * Berechtigung "Transaction Search".
 */
class PayPal(private val clientId: String, private val secret: String, sandbox: Boolean) {

    private val base = if (sandbox) "https://api-m.sandbox.paypal.com" else "https://api-m.paypal.com"
    private var token: String? = null

    private suspend fun auth(): Map<String, String> {
        val t = token ?: run {
            val basic = Base64.encodeToString("$clientId:$secret".encodeToByteArray(), Base64.NO_WRAP)
            val r = Http.request(
                "POST", "$base/v1/oauth2/token",
                mapOf("Authorization" to "Basic $basic"),
                "grant_type=client_credentials", "application/x-www-form-urlencoded",
            )
            r.str("access_token") ?: throw ApiException(0, "PayPal: kein Access-Token")
        }
        token = t
        return mapOf("Authorization" to "Bearer $t")
    }

    suspend fun balance(currency: String = "EUR"): Long? {
        val r = Http.request("GET", "$base/v1/reporting/balances?currency_code=$currency", auth())
        val b = r.arr("balances").firstOrNull { it.str("currency") == currency } ?: r.arr("balances").firstOrNull()
        return parseCents(b.path("total_balance", "value") ?: b.path("available_balance", "value"))
    }

    /** Lädt Transaktionen in 31-Tage-Fenstern (API-Limit). */
    suspend fun transactions(days: Long): List<PpTxn> {
        val out = mutableListOf<PpTxn>()
        val end = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        var from = end.minus(days, ChronoUnit.DAYS)
        while (from.isBefore(end)) {
            val to = minOf(from.plus(31, ChronoUnit.DAYS).minusSeconds(1), end)
            var page = 1
            var pages: Int
            do {
                val url = "$base/v1/reporting/transactions?start_date=${enc(from.toString())}" +
                    "&end_date=${enc(to.toString())}&fields=all&page_size=500&page=$page"
                val r = Http.request("GET", url, auth())
                pages = r.str("total_pages")?.toIntOrNull() ?: 1
                for (d in r.arr("transaction_details")) {
                    val info = d.obj("transaction_info") ?: continue
                    val status = info.str("transaction_status") ?: "S"
                    if (status == "D" || status == "V") continue // abgelehnt / storniert
                    val amount = parseCents(info.path("transaction_amount", "value")) ?: continue
                    if (amount == 0L) continue
                    val payer = d.obj("payer_info")
                    val name = payer.path("payer_name", "alternate_full_name")
                        ?: listOfNotNull(payer.path("payer_name", "given_name"), payer.path("payer_name", "surname"))
                            .joinToString(" ").ifBlank { null }
                        ?: d.path("shipping_info", "name")
                        ?: payer.str("email_address")
                    val item = d.obj("cart_info").arr("item_details").firstOrNull().str("item_name")
                    val code = info.str("transaction_event_code") ?: ""
                    out += PpTxn(
                        id = info.str("transaction_id") ?: continue,
                        amountCents = amount,
                        currency = info.path("transaction_amount", "currency_code") ?: "EUR",
                        date = (info.str("transaction_initiation_date") ?: continue).take(10),
                        counterparty = name ?: eventLabel(code),
                        purpose = listOfNotNull(info.str("transaction_subject"), item, info.str("transaction_note"))
                            .distinct().joinToString(" · "),
                        code = code,
                        pending = status == "P",
                    )
                }
                page++
            } while (page <= pages && page < 100)
            from = to.plusSeconds(1)
        }
        return out
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        fun eventLabel(code: String): String = when {
            code.startsWith("T03") -> "Einzahlung vom Bankkonto"
            code.startsWith("T04") -> "Auszahlung auf Bankkonto"
            code.startsWith("T02") -> "Währungsumrechnung"
            code.startsWith("T11") -> "Rückzahlung / Erstattung"
            code.startsWith("T12") -> "Rückbuchung"
            code.startsWith("T00") -> "PayPal-Zahlung"
            else -> "PayPal"
        }
    }
}
