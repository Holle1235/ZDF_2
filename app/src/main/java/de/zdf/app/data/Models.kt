package de.zdf.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class Source { ENABLE_BANKING, PAYPAL, CSV, MANUAL, DEMO }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val source: Source,
    val iban: String? = null,
    val currency: String = "EUR",
    /** Kontostand in Cent */
    val balanceCents: Long = 0,
    val balanceDate: String? = null,
    /** Enable-Banking-Account-UID bzw. PayPal-Kennung */
    val providerRef: String? = null,
    val connectionId: String? = null,
    val institution: String? = null,
    val hidden: Boolean = false,
    val lastSync: Long? = null,
    val lastError: String? = null,
)

@Serializable
data class Txn(
    val id: String,
    val accountId: String,
    /** ISO-Datum yyyy-MM-dd */
    val date: String,
    /** Betrag in Cent, negativ = Ausgabe */
    val amountCents: Long,
    val currency: String = "EUR",
    val counterparty: String = "",
    val purpose: String = "",
    val category: String = Categories.OTHER,
    val categoryManual: Boolean = false,
    val pending: Boolean = false,
    /** Anbieterspezifischer Code, z. B. PayPal-Event-Code T0300 */
    val code: String? = null,
)

@Serializable
data class Rule(val pattern: String, val category: String)

@Serializable
data class Budget(val category: String, val monthlyLimitCents: Long)

/** Eine PSD2-Verbindung (Session) bei Enable Banking. */
@Serializable
data class BankConnection(
    val id: String,
    val sessionId: String,
    val aspspName: String,
    val country: String,
    val validUntil: String? = null,
)

@Serializable
data class Settings(
    val ebAppId: String = "",
    val ebPrivateKeyPem: String = "",
    val ebRedirectUrl: String = "https://holle1235.github.io/zdf_2/callback.html",
    val paypalClientId: String = "",
    val paypalSecret: String = "",
    val paypalSandbox: Boolean = false,
    val appLock: Boolean = false,
    /** offener Bank-Login */
    val pendingState: String? = null,
    val pendingAspsp: String? = null,
    val pendingCountry: String? = null,
)

@Serializable
data class AppData(
    val version: Int = 1,
    val accounts: List<Account> = emptyList(),
    val txns: List<Txn> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val connections: List<BankConnection> = emptyList(),
    val settings: Settings = Settings(),
    val dismissedContracts: Set<String> = emptySet(),
)
