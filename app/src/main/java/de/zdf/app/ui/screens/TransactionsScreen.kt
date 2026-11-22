package de.zdf.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.zdf.app.data.AppData
import de.zdf.app.data.Categories
import de.zdf.app.data.Repository
import de.zdf.app.data.Source
import de.zdf.app.data.Txn
import de.zdf.app.ui.components.DayHeader
import de.zdf.app.ui.components.EmptyHint
import de.zdf.app.ui.components.TxnRow
import de.zdf.app.ui.components.TxnSheet
import de.zdf.app.ui.money
import de.zdf.app.ui.monthName
import java.time.YearMonth

@Composable
fun TransactionsScreen(data: AppData, repo: Repository, padding: PaddingValues, initialCategory: String?, initialMonth: String?) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(0) } // 0 alle, 1 Einnahmen, 2 Ausgaben
    var accountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var month by rememberSaveable { mutableStateOf(initialMonth) }
    var selected by remember { mutableStateOf<Txn?>(null) }

    val names = data.accounts.associate { it.id to it.name }
    val hiddenIds = data.accounts.filter { it.hidden }.map { it.id }.toSet()
    val q = query.trim().lowercase()
    val list = data.txns.asSequence()
        .filter { it.accountId !in hiddenIds }
        .filter { accountFilter == null || it.accountId == accountFilter }
        .filter { category == null || it.category == category }
        .filter { month == null || it.date.startsWith(month!!) }
        .filter { kind == 0 || (kind == 1 && it.amountCents > 0) || (kind == 2 && it.amountCents < 0) }
        .filter { q.isEmpty() || it.counterparty.lowercase().contains(q) || it.purpose.lowercase().contains(q) || Categories.get(it.category).label.lowercase().contains(q) || money(it.amountCents).contains(q) }
        .sortedByDescending { it.date }
        .toList()
    val grouped = list.groupBy { it.date }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Leeren") } },
            placeholder = { Text("Suchen …") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(kind == 0, { kind = 0 }, { Text("Alle") })
            FilterChip(kind == 1, { kind = 1 }, { Text("Einnahmen") })
            FilterChip(kind == 2, { kind = 2 }, { Text("Ausgaben") })
            category?.let { c -> InputChip(true, { category = null }, { Text("${Categories.get(c).icon} ${Categories.get(c).label}") }, trailingIcon = { Icon(Icons.Default.Close, null) }) }
            month?.let { m -> InputChip(true, { month = null }, { Text(monthName(YearMonth.parse(m))) }, trailingIcon = { Icon(Icons.Default.Close, null) }) }
            data.accounts.filter { !it.hidden }.takeIf { it.size > 1 }?.forEach { a ->
                FilterChip(accountFilter == a.id, { accountFilter = if (accountFilter == a.id) null else a.id }, { Text(a.name) })
            }
        }
        val sum = list.sumOf { it.amountCents }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${list.size} Umsätze", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("Summe ${money(sum, signed = true)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        if (list.isEmpty()) {
            EmptyHint(if (data.txns.isEmpty()) "Noch keine Umsätze. Verbinde unter „Konten“ ein Konto oder importiere eine CSV." else "Keine Treffer für diese Filter.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)) {
            grouped.forEach { (day, items) ->
                item(key = "h$day") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayHeader(day)
                        Spacer(Modifier.weight(1f))
                        Text(money(items.sumOf { it.amountCents }, signed = true), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp, top = 10.dp))
                    }
                }
                items(items, key = { it.id }) { t -> TxnRow(t, names[t.accountId]) { selected = t } }
            }
        }
    }

    selected?.let { t ->
        val acc = data.accounts.firstOrNull { it.id == t.accountId }
        TxnSheet(
            t = data.txns.firstOrNull { it.id == t.id } ?: t,
            account = acc,
            onDismiss = { selected = null },
            onCategory = { c, always -> repo.setCategory(t.id, c, always); selected = null },
            onDelete = if (acc?.source == Source.MANUAL || acc?.source == Source.CSV || acc?.source == Source.DEMO) ({ repo.deleteTxn(t.id); selected = null }) else null,
        )
    }
}
