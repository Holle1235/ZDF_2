package de.zdf.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.zdf.app.data.Analytics
import de.zdf.app.data.AppData
import de.zdf.app.data.Categories
import de.zdf.app.data.ContractDetector
import de.zdf.app.data.Repository
import de.zdf.app.data.Txn
import de.zdf.app.ui.components.CashflowBars
import de.zdf.app.ui.components.CategoryDot
import de.zdf.app.ui.components.Donut
import de.zdf.app.ui.components.ProgressBar
import de.zdf.app.ui.components.SectionCard
import de.zdf.app.ui.components.Slice
import de.zdf.app.ui.components.TxnRow
import de.zdf.app.ui.components.TxnSheet
import de.zdf.app.ui.components.sourceLabel
import de.zdf.app.ui.money
import de.zdf.app.ui.monthName
import de.zdf.app.ui.theme.Negative
import de.zdf.app.ui.theme.Positive
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(
    data: AppData,
    repo: Repository,
    padding: PaddingValues,
    onOpenAccounts: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenContracts: () -> Unit,
    onOpenAnalysis: () -> Unit,
) {
    var selected by remember { mutableStateOf<Txn?>(null) }
    val month = YearMonth.now()
    val visible = data.accounts.filter { !it.hidden }
    val visibleIds = visible.map { it.id }.toSet()
    val txns = data.txns.filter { it.accountId in visibleIds }
    val total = visible.sumOf { it.balanceCents }
    val summary = Analytics.summary(txns, month)
    val contracts = remember(data.txns, data.dismissedContracts) {
        ContractDetector.detect(data.txns).filter { it.key !in data.dismissedContracts && it.accountId in visibleIds }
    }
    val fixedCosts = -contracts.filter { !it.isIncome }.sumOf { it.monthlyCents }
    val today = LocalDate.now()
    val endOfMonth = month.atEndOfMonth()
    val stillExpected = contracts.filter { !it.nextDate.isAfter(endOfMonth) && it.nextDate.isAfter(today.minusDays(1)) }.sumOf { it.amountCents }
    val byCat = Analytics.spendingByCategory(txns, month)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Gesamtvermögen", style = MaterialTheme.typography.labelLarge)
                    Text(money(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Einnahmen ${monthName(month).substringBefore(' ')}", style = MaterialTheme.typography.labelMedium)
                            Text(money(summary.incomeCents), fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ausgaben", style = MaterialTheme.typography.labelMedium)
                            Text(money(summary.expenseCents), fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Bilanz", style = MaterialTheme.typography.labelMedium)
                            Text(money(summary.netCents, signed = true), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (stillExpected != 0L) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Bis Monatsende noch erwartet: ${money(stillExpected, signed = true)} · Prognose Kontostand: ${money(total + stillExpected)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (data.accounts.isEmpty()) {
            item {
                SectionCard(title = "Willkommen bei ZDF") {
                    Text("Zahlen, Daten, Fakten – deine Finanzen, nur auf deinem Gerät. Verbinde dein Bankkonto (PSD2), PayPal oder importiere eine CSV-Datei.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenAccounts) { Text("Konto hinzufügen") }
                    OutlinedButton(onClick = { repo.loadDemo() }) { Text("Erst mal mit Demo-Daten ansehen") }
                }
            }
        }

        if (visible.isNotEmpty()) {
            item {
                SectionCard(title = "Konten", action = { TextButton(onOpenAccounts) { Text("Verwalten") } }) {
                    visible.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (a.lastError != null) "⚠ Fehler beim Abruf" else sourceLabel(a),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (a.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(money(a.balanceCents, a.currency), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (txns.isNotEmpty()) {
            item {
                SectionCard(title = "Ausgaben ${monthName(month)}", action = { TextButton(onOpenAnalysis) { Text("Analyse") } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(
                            slices = byCat.take(6).map { Slice(it.second, Color(Categories.get(it.first).color)) } +
                                listOfNotNull(byCat.drop(6).sumOf { it.second }.takeIf { it > 0 }?.let { Slice(it, Color(0xFF94A3B8)) }),
                            size = 130.dp, centerTop = "Ausgaben", centerBottom = money(byCat.sumOf { it.second }),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            byCat.take(5).forEach { (cat, v) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(Categories.get(cat).icon)
                                    Spacer(Modifier.width(6.dp))
                                    Text(Categories.get(cat).label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(money(v), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (byCat.isEmpty()) Text("Noch keine Ausgaben in diesem Monat", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Cashflow – letzte 6 Monate") {
                    CashflowBars(Analytics.lastMonths(txns, 6))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Text("■ Einnahmen", color = Positive, style = MaterialTheme.typography.labelSmall)
                        Text("■ Ausgaben", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (contracts.isNotEmpty()) {
            item {
                SectionCard(title = "Verträge & Abos", action = { TextButton(onOpenContracts) { Text("Alle") } }) {
                    Text("${contracts.count { !it.isIncome }} erkannt · ${money(fixedCosts)} pro Monat Fixkosten", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    contracts.filter { !it.isIncome && !it.nextDate.isBefore(today) }.sortedBy { it.nextDate }.take(3).forEach { c ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryDot(c.category, 32)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text("nächste Zahlung ${de.zdf.app.ui.shortDate(c.nextDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(money(c.amountCents), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        val budgets = data.budgets
        if (budgets.isNotEmpty()) {
            item {
                SectionCard(title = "Budgets", action = { TextButton(onOpenAnalysis) { Text("Bearbeiten") } }) {
                    budgets.forEach { b ->
                        val spent = Analytics.spentInCategory(txns, b.category, month)
                        val frac = spent.toFloat() / b.monthlyLimitCents
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row {
                                Text("${Categories.get(b.category).icon} ${Categories.get(b.category).label}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${money(spent)} / ${money(b.monthlyLimitCents)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(frac, if (frac > 1f) Negative else Color(Categories.get(b.category).color))
                        }
                    }
                }
            }
        }

        if (txns.isNotEmpty()) {
            item {
                SectionCard(title = "Letzte Umsätze", action = { TextButton(onOpenTransactions) { Text("Alle") } }, flush = true) {
                    val names = data.accounts.associate { it.id to it.name }
                    txns.sortedByDescending { it.date }.take(6).forEach { t -> TxnRow(t, names[t.accountId]) { selected = t } }
                }
            }
        }
    }

    selected?.let { t ->
        TxnSheet(
            t = data.txns.firstOrNull { it.id == t.id } ?: t,
            account = data.accounts.firstOrNull { it.id == t.accountId },
            onDismiss = { selected = null },
            onCategory = { c, always -> repo.setCategory(t.id, c, always); selected = null },
            onDelete = null,
        )
    }
}
