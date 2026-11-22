package de.zdf.app.net

import android.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Aspsp(val name: String, val country: String, val maxConsentSeconds: Long?, val psuTypes: List<String>)

data class EbAccount(
    val uid: String,
    val iban: String?,
    val name: String?,
    val currency: String?,
)

data class EbSession(val sessionId: String, val accounts: List<EbAccount>, val validUntil: String?)

data class EbTxn(
    val ref: String?,
    val amountCents: Long,
    val currency: String,
    val date: String,
    val counterparty: String,
    val purpose: String,
    val pending: Boolean,
)

/**
 * Client für die Enable Banking API (PSD2/Open Banking, lizenzierter Kontoinformationsdienst).
 * Mit einer eigenen Anwendung im "restricted mode" lassen sich die eigenen Konten kostenlos verknüpfen.
 * Doku: https://enablebanking.com/docs/api/reference/
 */
class EnableBanking(private val appId: String, privateKeyPem: String) {

    private val base = "https://api.enablebanking.com"
    private val key: PrivateKey = parsePrivateKey(privateKeyPem)

    private fun jwt(): String {
        val now = Instant.now().epochSecond
        val header = """{"typ":"JWT","alg":"RS256","kid":"$appId"}"""
        val payload = """{"iss":"enablebanking.com","aud":"api.enablebanking.com","iat":$now,"exp":${now + 3600}}"""
        val input = b64(header.encodeToByteArray()) + "." + b64(payload.encodeToByteArray())
        val sig = Signature.getInstance("SHA256withRSA").run {
            initSign(key); update(input.encodeToByteArray()); sign()
        }
        return input + "." + b64(sig)
    }

    private fun auth() = mapOf("Authorization" to "Bearer ${jwt()}")

    suspend fun aspsps(country: String): List<Aspsp> {
        val r = Http.request("GET", "$base/aspsps?country=$country&psu_type=personal", auth())
        return r.arr("aspsps").map { a ->
            Aspsp(
                name = a.str("name") ?: "?",
                country = a.str("country") ?: country,
                maxConsentSeconds = a.str("maximum_consent_validity")?.toLongOrNull(),
                psuTypes = a.arr("psu_types").map { (it as JsonPrimitive).content },
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Startet den Bank-Login und liefert die URL, die im Browser geöffnet werden muss. */
    suspend fun startAuth(aspsp: Aspsp, redirectUrl: String, state: String): String {
        val maxDays = ((aspsp.maxConsentSeconds ?: (90L * 86400)) / 86400).coerceIn(1, 180)
        val validUntil = Instant.now().plus(maxDays, ChronoUnit.DAYS).minusSeconds(60)
        val body = buildJsonObject {
            putJsonObject("access") { put("valid_until", validUntil.toString()) }
            putJsonObject("aspsp") { put("name", aspsp.name); put("country", aspsp.country) }
            put("state", state)
            put("redirect_url", redirectUrl)
            put("psu_type", "personal")
        }
        val r = Http.request("POST", "$base/auth", auth(), body.toString())
        return r.str("url") ?: throw ApiException(0, "Keine Login-URL von Enable Banking erhalten")
    }

    suspend fun createSession(code: String): EbSession {
        val body = buildJsonObject { put("code", code) }
        val r = Http.request("POST", "$base/sessions", auth(), body.toString())
        val accounts = r.arr("accounts").map { a ->
            EbAccount(
                uid = a.str("uid") ?: error("Konto ohne uid"),
                iban = a.path("account_id", "iban"),
                name = a.str("name") ?: a.str("product") ?: a.str("details"),
                currency = a.str("currency"),
            )
        }
        return EbSession(r.str("session_id") ?: error("Keine session_id"), accounts, r.path("access", "valid_until"))
    }

    suspend fun deleteSession(sessionId: String) {
        runCatching { Http.request("DELETE", "$base/sessions/$sessionId", auth()) }
    }

    /** Bevorzugt gebuchten Saldo (CLBD), dann verfügbaren/erwarteten Saldo. */
    suspend fun balance(uid: String): Pair<Long, String>? {
        val r = Http.request("GET", "$base/accounts/$uid/balances", auth())
        val list = r.arr("balances")
        val order = listOf("CLBD", "ITBD", "XPCD", "ITAV", "CLAV", "OPBD", "PRCD", "INFO")
        val best = list.minByOrNull { b -> order.indexOf(b.str("balance_type")).let { if (it < 0) 99 else it } }
            ?: return null
        val cents = parseCents(best.path("balance_amount", "amount")) ?: return null
        return cents to (best.path("balance_amount", "currency") ?: "EUR")
    }

    suspend fun transactions(uid: String, from: LocalDate): List<EbTxn> {
        val out = mutableListOf<EbTxn>()
        var cont: String? = null
        var guard = 0
        do {
            val url = buildString {
                append("$base/accounts/$uid/transactions?date_from=$from")
                if (cont != null) append("&continuation_key=").append(java.net.URLEncoder.encode(cont, "UTF-8"))
            }
            val r = Http.request("GET", url, auth())
            for (t in r.arr("transactions")) {
                val raw = parseCents(t.path("transaction_amount", "amount")) ?: continue
                val debit = t.str("credit_debit_indicator") == "DBIT"
                val amount = if (debit) -kotlin.math.abs(raw) else kotlin.math.abs(raw)
                val cpName = if (debit) t.path("creditor", "name") else t.path("debtor", "name")
                val remit = (t as? kotlinx.serialization.json.JsonObject)?.get("remittance_information")
                val purpose = when (remit) {
                    is JsonArray -> remit.joinToString(" ") { (it as? JsonPrimitive)?.content.orEmpty() }
                    is JsonPrimitive -> remit.content
                    else -> ""
                }.trim()
                val date = t.str("booking_date") ?: t.str("value_date") ?: t.str("transaction_date") ?: continue
                out += EbTxn(
                    ref = t.str("entry_reference") ?: t.str("transaction_id"),
                    amountCents = amount,
                    currency = t.path("transaction_amount", "currency") ?: "EUR",
                    date = date.take(10),
                    counterparty = cpName ?: t.path("bank_transaction_code", "description") ?: "",
                    purpose = purpose,
                    pending = t.str("status") == "PDNG",
                )
            }
            cont = r.str("continuation_key")?.takeIf { it.isNotBlank() }
        } while (cont != null && ++guard < 50)
        return out
    }

    companion object {
        private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        fun parsePrivateKey(pem: String): PrivateKey {
            val isPkcs1 = pem.contains("BEGIN RSA PRIVATE KEY")
            val body = pem.lines().filter { !it.startsWith("-----") }.joinToString("").trim()
            require(body.isNotEmpty()) { "Kein Private Key gefunden" }
            var der = Base64.decode(body, Base64.DEFAULT)
            if (isPkcs1) der = pkcs1ToPkcs8(der)
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        }

        /** Verpackt einen PKCS#1-RSA-Key in eine PKCS#8-Struktur. */
        fun pkcs1ToPkcs8(pkcs1: ByteArray): ByteArray {
            val algId = byteArrayOf(
                0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(),
                0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
            )
            val version = byteArrayOf(0x02, 0x01, 0x00)
            val octet = der(0x04, pkcs1)
            return der(0x30, version + algId + octet)
        }

        private fun der(tag: Int, content: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(tag)
            val len = content.size
            when {
                len < 0x80 -> out.write(len)
                len < 0x100 -> { out.write(0x81); out.write(len) }
                len < 0x10000 -> { out.write(0x82); out.write(len shr 8); out.write(len and 0xff) }
                else -> { out.write(0x83); out.write(len shr 16); out.write((len shr 8) and 0xff); out.write(len and 0xff) }
            }
            out.write(content)
            return out.toByteArray()
        }
    }
}
