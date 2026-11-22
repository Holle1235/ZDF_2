package de.zdf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.sp
import de.zdf.app.data.Account
import de.zdf.app.data.Categories
import de.zdf.app.data.Source
import de.zdf.app.data.Txn
import de.zdf.app.ui.dayHeader
import de.zdf.app.ui.money
import de.zdf.app.ui.theme.amountColor

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    flush: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = if (flush) 0.dp else 16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = if (flush) 16.dp else 0.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun CategoryDot(key: String, size: Int = 40) {
    val c = Categories.get(key)
    Box(
        Modifier.size(size.dp).background(Color(c.color).copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(c.icon, fontSize = (size * 0.45).sp) }
}

@Composable
fun TxnRow(t: Txn, accountName: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryDot(t.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.counterparty.ifBlank { t.purpose.ifBlank { "Unbekannt" } }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val sub = buildString {
                append(Categories.get(t.category).label)
                if (accountName != null) append(" · ").append(accountName)
                if (t.pending) append(" · vorgemerkt")
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(money(t.amountCents, t.currency, signed = true), color = amountColor(t.amountCents), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DayHeader(iso: String) {
    Text(
        dayHeader(iso), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

fun sourceLabel(a: Account) = when (a.source) {
    Source.ENABLE_BANKING -> "Bank (PSD2) · ${a.institution ?: ""}"
    Source.PAYPAL -> "PayPal API"
    Source.CSV -> "CSV-Import"
    Source.MANUAL -> "Manuell"
    Source.DEMO -> "Demo"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnSheet(
    t: Txn,
    account: Account?,
    onDismiss: () -> Unit,
    onCategory: (String, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var always by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(t.category, 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.counterparty.ifBlank { "Unbekannt" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(dayHeader(t.date), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(money(t.amountCents, t.currency, true), style = MaterialTheme.typography.headlineMedium, color = amountColor(t.amountCents), fontWeight = FontWeight.Bold)
            if (t.purpose.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(t.purpose, style = MaterialTheme.typography.bodyMedium)
            }
            if (account != null) {
                Spacer(Modifier.height(4.dp))
                Text("Konto: ${account.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Kategorie", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Categories.all.forEach { c ->
                    FilterChip(
                        selected = c.key == t.category,
                        onClick = { onCategory(c.key, always) },
                        label = { Text("${c.icon} ${c.label}") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { always = !always }) {
                Checkbox(always, { always = it })
                Text("Immer so für „${t.counterparty.ifBlank { t.purpose }.take(30)}“ (Regel anlegen)", style = MaterialTheme.typography.bodyMedium)
            }
            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }) { Text("Buchung löschen", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = { TextButton({ onDelete(); confirmDelete = false }) { Text("Löschen") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } },
            title = { Text("Buchung löschen?") },
        )
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}
