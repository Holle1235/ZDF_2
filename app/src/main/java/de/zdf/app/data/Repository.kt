package de.zdf.app.data

import android.content.Context
import de.zdf.app.importer.CsvImporter
import de.zdf.app.net.Aspsp
import de.zdf.app.net.EnableBanking
import de.zdf.app.net.PayPal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

data class SyncState(val running: Boolean = false, val message: String? = null, val error: Boolean = false)

class Repository(context: Context, private val store: Store = Store(context)) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _data = MutableStateFlow(store.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        _data.update(transform)
        store.save(_data.value)
    }

    fun clearMessage() = _sync.update { it.copy(message = null, error = false) }

    // ---------------------------------------------------------------- Kategorien

    private fun categorize(d: AppData, accountId: String, cp: String, purpose: String, amount: Long, code: String?): String {
        val acc = d.accounts.firstOrNull { it.id == accountId }
        return Categorizer.categorize(
            cp, purpose, amount, d.rules,
            isPaypalAccount = acc?.source == Source.PAYPAL || (acc?.name?.contains("paypal", true) == true),
            hasPaypal = d.accounts.any { it.source == Source.PAYPAL || it.name.contains("paypal", true) },
            code = code,
        )
    }

    private fun recategorized(d: AppData): AppData = d.copy(
        txns = d.txns.map { t ->
            if (t.categoryManual) t else t.copy(category = categorize(d, t.accountId, t.counterparty, t.purpose, t.amountCents, t.code))
        }
    )

    fun setCategory(txnId: String, category: String, createRule: Boolean) = update { d ->
        val t = d.txns.firstOrNull { it.id == txnId } ?: return@update d
        var nd = d.copy(txns = d.txns.map { if (it.id == txnId) it.copy(category = category, categoryManual = true) else it })
        val pattern = t.counterparty.ifBlank { t.purpose }.trim()
        if (createRule && pattern.isNotBlank()) {
            nd = nd.copy(rules = listOf(Rule(pattern, category)) + nd.rules.filterNot { it.pattern.equals(pattern, true) })
            nd = recategorized(nd)
        }
        nd
    }

    fun deleteRule(rule: Rule) = update { recategorized(it.copy(rules = it.rules - rule)) }

    // ---------------------------------------------------------------- Transaktionen zusammenführen

    private fun hash(vararg parts: Any?): String {
        val md = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").encodeToByteArray())
        return md.take(10).joinToString("") { "%02x".format(it) }
    }

    private data class Incoming(val ref: String?, val date: String, val amount: Long, val currency: String, val cp: String, val purpose: String, val pending: Boolean, val code: String? = null)

    private fun merge(d: AppData, accountId: String, incoming: List<Incoming>, dropPending: Boolean): Pair<AppData, Int> {
        val seen = HashMap<String, Int>()
        val existing = d.txns.filter { it.accountId == accountId && !(dropPending && it.pending) }.associateBy { it.id }.toMutableMap()
        var added = 0
        for (i in incoming) {
            val baseId = if (i.ref != null) "$accountId:${i.ref}" else hash(accountId, i.date, i.amount, i.cp, i.purpose, i.pending)
            val n = seen.merge(baseId, 1, Int::plus)!!
            val id = if (n > 1) "$baseId#$n" else baseId
            val old = existing[id]
            val cat = if (old?.categoryManual == true) old.category else categorize(d, accountId, i.cp, i.purpose, i.amount, i.code)
            if (old == null) added++
            existing[id] = Txn(
                id = id, accountId = accountId, date = i.date, amountCents = i.amount, currency = i.currency,
                counterparty = i.cp, purpose = i.purpose, category = cat, categoryManual = old?.categoryManual == true,
                pending = i.pending, code = i.code,
            )
        }
        val others = d.txns.filter { it.accountId != accountId }
        return d.copy(txns = (others + existing.values).sortedByDescending { it.date }) to added
    }

    // ---------------------------------------------------------------- Enable Banking (Bankkonten)

    fun saveEbCredentials(appId: String, pem: String, redirect: String) = update {
        it.copy(settings = it.settings.copy(ebAppId = appId.trim(), ebPrivateKeyPem = pem.trim(), ebRedirectUrl = redirect.trim()))
    }

    private fun eb(): EnableBanking {
        val s = _data.value.settings
        require(s.ebAppId.isNotBlank() && s.ebPrivateKeyPem.isNotBlank()) { "Enable-Banking-Zugangsdaten fehlen" }
        return EnableBanking(s.ebAppId, s.ebPrivateKeyPem)
    }

    suspend fun listBanks(country: String): List<Aspsp> = eb().aspsps(country)

    suspend fun startBankAuth(aspsp: Aspsp): String {
        val state = UUID.randomUUID().toString()
        val url = eb().startAuth(aspsp, _data.value.settings.ebRedirectUrl, state)
        update { it.copy(settings = it.settings.copy(pendingState = state, pendingAspsp = aspsp.name, pendingCountry = aspsp.country)) }
        return url
    }

    /** Verarbeitet die Rücksprung-URL (Deep Link oder manuell eingefügt). */
    private val usedCodes = java.util.Collections.synchronizedSet(HashSet<String>())

    fun completeBankAuth(callbackUrl: String) {
        scope.launch {
            try {
                val uri = java.net.URI(callbackUrl.trim().replace(" ", "%20"))
                val params = (uri.rawQuery ?: "").split('&').filter { '=' in it }.associate {
                    val (k, v) = it.split('=', limit = 2)
                    k to java.net.URLDecoder.decode(v, "UTF-8")
                }
                params["error"]?.let { error("Bank meldet: $it ${params["error_description"].orEmpty()}") }
                val code = params["code"] ?: error("Kein 'code' in der URL gefunden")
                val s = _data.value.settings
                // Derselbe Code kann doppelt ankommen (App Link + manuell eingefügt) – nur einmal einlösen.
                if (!usedCodes.add(code) || (params["state"] != null && s.pendingState == null)) {
                    _sync.value = SyncState(false, "Diese Bankfreigabe wurde bereits übernommen ✓")
                    return@launch
                }
                if (params["state"] != null && params["state"] != s.pendingState) error("Ungültiger state – bitte Verbindung neu starten")
                _sync.value = SyncState(true, "Bankverbindung wird eingerichtet …")
                val session = eb().createSession(code)
                val connId = UUID.randomUUID().toString()
                update { d ->
                    val conn = BankConnection(connId, session.sessionId, s.pendingAspsp ?: "Bank", s.pendingCountry ?: "DE", session.validUntil)
                    val newAccounts = session.accounts.map { a ->
                        val existing = d.accounts.firstOrNull { it.source == Source.ENABLE_BANKING && it.iban != null && it.iban == a.iban }
                        (existing ?: Account(id = "eb-" + UUID.randomUUID().toString().take(8), name = a.name ?: (s.pendingAspsp ?: "Konto"), source = Source.ENABLE_BANKING))
                            .copy(providerRef = a.uid, connectionId = connId, iban = a.iban, currency = a.currency ?: "EUR", institution = s.pendingAspsp, lastError = null)
                    }
                    val keep = d.accounts.filterNot { o -> newAccounts.any { it.id == o.id } }
                    d.copy(
                        accounts = keep + newAccounts,
                        connections = d.connections + conn,
                        settings = d.settings.copy(pendingState = null, pendingAspsp = null, pendingCountry = null),
                    )
                }
                _sync.value = SyncState(false, "${session.accounts.size} Konto/Konten verbunden")
                syncAll()
            } catch (e: Exception) {
                val already = e.message?.contains("ALREADY_AUTHORIZED") == true
                _sync.value = if (already) SyncState(false, "Diese Bankfreigabe wurde bereits übernommen ✓")
                else SyncState(false, "Verbindung fehlgeschlagen: ${e.message}", true)
            }
        }
    }

    private suspend fun syncEbAccount(acc: Account) {
        val client = eb()
        val uid = acc.providerRef ?: return
        val hasData = _data.value.txns.any { it.accountId == acc.id }
        val txns = try {
            client.transactions(uid, LocalDate.now().minusDays(if (hasData) 60 else 730))
        } catch (e: Exception) {
            if (hasData) throw e
            client.transactions(uid, LocalDate.now().minusDays(89))
        }
        val bal = runCatching { client.balance(uid) }.getOrNull()
        update { d ->
            val (nd, _) = merge(d, acc.id, txns.map { Incoming(it.ref, it.date, it.amountCents, it.currency, it.counterparty, it.purpose, it.pending) }, dropPending = true)
            nd.copy(accounts = nd.accounts.map {
                if (it.id != acc.id) it else it.copy(
                    balanceCents = bal?.first ?: it.balanceCents, currency = bal?.second ?: it.currency,
                    balanceDate = LocalDate.now().toString(), lastSync = System.currentTimeMillis(), lastError = null,
                )
            })
        }
    }

    // ---------------------------------------------------------------- PayPal

    fun connectPaypal(clientId: String, secret: String, sandbox: Boolean) {
        update { d ->
            val s = d.settings.copy(paypalClientId = clientId.trim(), paypalSecret = secret.trim(), paypalSandbox = sandbox)
            val accounts = if (d.accounts.any { it.source == Source.PAYPAL }) d.accounts
            else d.accounts + Account(id = "paypal", name = "PayPal", source = Source.PAYPAL, institution = "PayPal")
            recategorized(d.copy(settings = s, accounts = accounts))
        }
        syncAll()
    }

    private suspend fun syncPaypal(acc: Account) {
        val s = _data.value.settings
        require(s.paypalClientId.isNotBlank()) { "PayPal-Zugangsdaten fehlen" }
        val pp = PayPal(s.paypalClientId, s.paypalSecret, s.paypalSandbox)
        val hasData = _data.value.txns.any { it.accountId == acc.id }
        val txns = pp.transactions(if (hasData) 45 else 365).filter { it.currency == acc.currency }
        val bal = runCatching { pp.balance(acc.currency) }.getOrNull()
        update { d ->
            val (nd, _) = merge(d, acc.id, txns.map { Incoming(it.id, it.date, it.amountCents, it.currency, it.counterparty, it.purpose, it.pending, it.code) }, dropPending = false)
            nd.copy(accounts = nd.accounts.map {
                if (it.id != acc.id) it else it.copy(
                    balanceCents = bal ?: it.balanceCents, balanceDate = LocalDate.now().toString(),
                    lastSync = System.currentTimeMillis(), lastError = null,
                )
            })
        }
    }

    // ---------------------------------------------------------------- Sync

    fun syncAll() {
        scope.launch {
            if (!syncMutex.tryLock()) return@launch
            try {
                val accounts = _data.value.accounts.filter { it.source == Source.ENABLE_BANKING || it.source == Source.PAYPAL }
                if (accounts.isEmpty()) {
                    _sync.value = SyncState(false, "Keine verbundenen Konten – füge eins unter Konten hinzu")
                    return@launch
                }
                var errors = 0
                for (acc in accounts) {
                    _sync.value = SyncState(true, "Aktualisiere ${acc.name} …")
                    try {
                        if (acc.source == Source.PAYPAL) syncPaypal(acc) else syncEbAccount(acc)
                    } catch (e: Exception) {
                        errors++
                        update { d -> d.copy(accounts = d.accounts.map { if (it.id == acc.id) it.copy(lastError = e.message?.take(300)) else it }) }
                    }
                }
                _sync.value = if (errors == 0) SyncState(false, "Alles aktuell ✓")
                else SyncState(false, "$errors Konto/Konten mit Fehler – Details unter Konten", true)
            } finally {
                syncMutex.unlock()
            }
        }
    }

    // ---------------------------------------------------------------- CSV & manuelle Konten

    fun createManualAccount(name: String, balanceCents: Long, source: Source = Source.MANUAL): String {
        val id = "m-" + UUID.randomUUID().toString().take(8)
        update { it.copy(accounts = it.accounts + Account(id = id, name = name.ifBlank { "Konto" }, source = source, balanceCents = balanceCents, balanceDate = LocalDate.now().toString())) }
        return id
    }

    /** @return Anzahl neuer Buchungen und Hinweistext */
    fun importCsv(accountId: String, bytes: ByteArray): Pair<Int, String> {
        val res = CsvImporter.parse(bytes)
        var added = 0
        update { d ->
            val (nd, a) = merge(d, accountId, res.rows.map { Incoming(null, it.date, it.amountCents, it.currency, it.counterparty, it.purpose, false) }, dropPending = false)
            added = a
            nd.copy(accounts = nd.accounts.map { if (it.id == accountId) it.copy(lastSync = System.currentTimeMillis()) else it })
        }
        return added to "${res.rows.size} Zeilen gelesen, $added neu, ${res.skipped} übersprungen.\n${res.detected}"
    }

    fun addManualTxn(accountId: String, date: LocalDate, amountCents: Long, counterparty: String, purpose: String) = update { d ->
        merge(d, accountId, listOf(Incoming("man-" + UUID.randomUUID(), date.toString(), amountCents, "EUR", counterparty, purpose, false)), false).first
    }

    fun deleteTxn(id: String) = update { it.copy(txns = it.txns.filterNot { t -> t.id == id }) }

    fun editAccount(id: String, name: String, balanceCents: Long?, hidden: Boolean) = update { d ->
        d.copy(accounts = d.accounts.map {
            if (it.id != id) it else it.copy(name = name.ifBlank { it.name }, balanceCents = balanceCents ?: it.balanceCents, hidden = hidden)
        })
    }

    fun deleteAccount(id: String) {
        val d = _data.value
        val acc = d.accounts.firstOrNull { it.id == id } ?: return
        update { cur ->
            var nd = cur.copy(accounts = cur.accounts.filterNot { it.id == id }, txns = cur.txns.filterNot { it.accountId == id })
            if (acc.source == Source.PAYPAL) nd = nd.copy(settings = nd.settings.copy(paypalClientId = "", paypalSecret = ""))
            val connId = acc.connectionId
            if (connId != null && nd.accounts.none { it.connectionId == connId }) nd = nd.copy(connections = nd.connections.filterNot { it.id == connId })
            recategorized(nd)
        }
        val connId = acc.connectionId
        if (connId != null && _data.value.accounts.none { it.connectionId == connId }) {
            d.connections.firstOrNull { it.id == connId }?.let { c -> scope.launch { runCatching { eb().deleteSession(c.sessionId) } } }
        }
    }

    // ---------------------------------------------------------------- Budgets, Verträge

    fun setBudget(category: String, limitCents: Long?) = update { d ->
        val rest = d.budgets.filterNot { it.category == category }
        d.copy(budgets = if (limitCents == null || limitCents <= 0) rest else rest + Budget(category, limitCents))
    }

    fun dismissContract(key: String) = update { it.copy(dismissedContracts = it.dismissedContracts + key) }
    fun restoreContracts() = update { it.copy(dismissedContracts = emptySet()) }

    fun setAppLock(on: Boolean) = update { it.copy(settings = it.settings.copy(appLock = on)) }

    // ---------------------------------------------------------------- Backup

    fun exportJson(includeSecrets: Boolean) = store.exportJson(_data.value, includeSecrets)

    fun importJson(text: String) {
        val imported = store.importJson(text)
        update { imported }
    }

    fun wipe() {
        store.wipe()
        _data.value = AppData()
        store.save(_data.value)
    }

    fun loadDemo() = update { d -> recategorized(DemoData.generate(d)) }
}
