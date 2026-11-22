package de.zdf.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.zdf.app.data.AppData
import de.zdf.app.data.Categories
import de.zdf.app.data.Contract
import de.zdf.app.data.ContractDetector
import de.zdf.app.data.Repository
import de.zdf.app.ui.components.CategoryDot
import de.zdf.app.ui.components.EmptyHint
import de.zdf.app.ui.components.SectionCard
import de.zdf.app.ui.money
import de.zdf.app.ui.shortDate
import de.zdf.app.ui.theme.amountColor

@Composable
fun ContractsScreen(data: AppData, repo: Repository, padding: PaddingValues) {
    val hidden = data.accounts.filter { it.hidden }.map { it.id }.toSet()
    val all = remember(data.txns) { ContractDetector.detect(data.txns) }.filter { it.accountId !in hidden }
    val contracts = all.filter { it.key !in data.dismissedContracts }
    val expenses = contracts.filter { !it.isIncome }.sortedBy { it.monthlyCents }
    val income = contracts.filter { it.isIncome }.sortedByDescending { it.monthlyCents }
    val monthlyOut = -expenses.sumOf { it.monthlyCents }
    val monthlyIn = income.sumOf { it.monthlyCents }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Fixkosten / Monat", style = MaterialTheme.typography.labelMedium)
                        Text(money(monthlyOut), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("≈ ${money(monthlyOut * 12)} pro Jahr", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Regelm. Einnahmen", style = MaterialTheme.typography.labelMedium)
                        Text(money(monthlyIn), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("frei verfügbar ≈ ${money(monthlyIn - monthlyOut)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (contracts.isEmpty()) {
            item { EmptyHint("Noch keine Verträge erkannt. ZDF erkennt wiederkehrende Zahlungen automatisch, sobald mindestens 2–3 Zahlungen im gleichen Rhythmus vorliegen.") }
        }
        if (expenses.isNotEmpty()) item { ContractList("Verträge & Abos (${expenses.size})", expenses, repo) }
        if (income.isNotEmpty()) item { ContractList("Regelmäßige Einnahmen", income, repo) }
        if (data.dismissedContracts.isNotEmpty()) {
            item { TextButton({ repo.restoreContracts() }) { Text("${data.dismissedContracts.size} ausgeblendete Verträge wieder anzeigen") } }
        }
    }
}

@Composable
private fun ContractList(title: String, list: List<Contract>, repo: Repository) {
    SectionCard(title = title) {
        list.forEach { c ->
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(c.category, 36)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${c.interval.label} · ${Categories.get(c.category).label}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("nächste ${shortDate(c.nextDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(c.amountCents, signed = true), color = amountColor(c.amountCents), fontWeight = FontWeight.SemiBold)
                    if (c.interval != de.zdf.app.data.Interval.MONTHLY) Text("${money(c.monthlyCents)}/Monat", style = MaterialTheme.typography.labelSmall)
                }
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Optionen") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Kein Vertrag – ausblenden") }, { repo.dismissContract(c.key); menu = false })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
