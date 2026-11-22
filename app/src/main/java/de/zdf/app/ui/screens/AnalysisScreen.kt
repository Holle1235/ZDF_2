package de.zdf.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.zdf.app.data.Analytics
import de.zdf.app.data.AppData
import de.zdf.app.data.Categories
import de.zdf.app.data.Repository
import de.zdf.app.ui.components.CashflowBars
import de.zdf.app.ui.components.CategoryDot
import de.zdf.app.ui.components.Donut
import de.zdf.app.ui.components.EmptyHint
import de.zdf.app.ui.components.ProgressBar
import de.zdf.app.ui.components.SectionCard
import de.zdf.app.ui.components.Slice
import de.zdf.app.ui.money
import de.zdf.app.ui.monthName
import de.zdf.app.ui.parseUserAmount
import de.zdf.app.ui.theme.Negative
import java.time.YearMonth

@Composable
fun AnalysisScreen(data: AppData, repo: Repository, padding: PaddingValues, onOpenCategory: (String, YearMonth) -> Unit) {
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthStr)
    var budgetFor by remember { mutableStateOf<String?>(null) }
    var pickBudget by remember { mutableStateOf(false) }

    val hidden = data.accounts.filter { it.hidden }.map { it.id }.toSet()
    val txns = data.txns.filter { it.accountId !in hidden }
    val summary = Analytics.summary(txns, month)
    val byCat = Analytics.spendingByCategory(txns, month)
    val totalSpent = byCat.sumOf { it.second }
    val avg = Analytics.lastMonths(txns, 3, month.minusMonths(1)).map { it.expenseCents }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton({ monthStr = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vormonat") }
                Text(monthName(month), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ monthStr = month.plusMonths(1).toString() }, enabled = month < YearMonth.now()) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächster Monat") }
            }
        }
        item {
            SectionCard {
                Row {
                    Stat("Einnahmen", money(summary.incomeCents), Modifier.weight(1f))
                    Stat("Ausgaben", money(summary.expenseCents), Modifier.weight(1f))
                    Stat("Bilanz", money(summary.netCents, signed = true), Modifier.weight(1f))
                }
                if (avg > 0) {
                    Spacer(Modifier.height(8.dp))
                    val diff = summary.expenseCents - avg
                    Text(
                        "Ø Ausgaben der 3 Vormonate: ${money(avg)} (${if (diff > 0) "+" else ""}${money(diff)} diesen Monat)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionCard(title = "Ausgaben nach Kategorie") {
                if (byCat.isEmpty()) EmptyHint("Keine Ausgaben in diesem Monat.")
                else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Donut(byCat.map { Slice(it.second, Color(Categories.get(it.first).color)) }, 170.dp, "Gesamt", money(totalSpent))
                    }
                    Spacer(Modifier.height(12.dp))
                    byCat.forEach { (cat, v) ->
                        val c = Categories.get(cat)
                        Column(Modifier.fillMaxWidth().clickable { onOpenCategory(cat, month) }.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryDot(cat, 30)
                                Spacer(Modifier.width(8.dp))
                                Text(c.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${(v * 100 / totalSpent.coerceAtLeast(1))} %  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(money(v), fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            ProgressBar(v.toFloat() / byCat.first().second, Color(c.color), Modifier.padding(start = 38.dp))
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "Budgets", action = { TextButton({ pickBudget = true }) { Text("+ Budget") } }) {
                if (data.budgets.isEmpty()) Text("Lege monatliche Limits für Kategorien fest, z. B. 400 € für Lebensmittel.", style = MaterialTheme.typography.bodySmall)
                data.budgets.forEach { b ->
                    val spent = Analytics.spentInCategory(txns, b.category, month)
                    val frac = spent.toFloat() / b.monthlyLimitCents
                    Column(Modifier.fillMaxWidth().clickable { budgetFor = b.category }.padding(vertical = 6.dp)) {
                        Row {
                            Text("${Categories.get(b.category).icon} ${Categories.get(b.category).label}", modifier = Modifier.weight(1f))
                            Text(if (spent <= b.monthlyLimitCents) "noch ${money(b.monthlyLimitCents - spent)}" else "${money(spent - b.monthlyLimitCents)} drüber",
                                color = if (frac > 1) Negative else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(4.dp))
                        ProgressBar(frac, if (frac > 1f) Negative else Color(Categories.get(b.category).color))
                        Text("${money(spent)} von ${money(b.monthlyLimitCents)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SectionCard(title = "Cashflow – 12 Monate") {
                CashflowBars(Analytics.lastMonths(txns, 12, month))
            }
        }
    }

    if (pickBudget) CategoryPicker(onPick = { budgetFor = it; pickBudget = false }, onDismiss = { pickBudget = false })
    budgetFor?.let { cat ->
        val existing = data.budgets.firstOrNull { it.category == cat }
        var text by remember(cat) { mutableStateOf(existing?.let { (it.monthlyLimitCents / 100).toString() } ?: "") }
        AlertDialog(
            onDismissRequest = { budgetFor = null },
            title = { Text("Budget: ${Categories.get(cat).label}") },
            text = {
                OutlinedTextField(text, { text = it }, label = { Text("Monatliches Limit in €") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = { TextButton({ repo.setBudget(cat, parseUserAmount(text)); budgetFor = null }) { Text("Speichern") } },
            dismissButton = {
                Row {
                    if (existing != null) TextButton({ repo.setBudget(cat, null); budgetFor = null }) { Text("Entfernen") }
                    TextButton({ budgetFor = null }) { Text("Abbrechen") }
                }
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorie wählen") },
        text = {
            FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Categories.all.filter { it.key != Categories.INCOME && it.key != Categories.TRANSFER }.forEach { c ->
                    FilterChip(false, { onPick(c.key) }, { Text("${c.icon} ${c.label}") })
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}
