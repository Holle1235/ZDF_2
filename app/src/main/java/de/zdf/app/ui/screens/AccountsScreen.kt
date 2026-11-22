package de.zdf.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.zdf.app.data.Account
import de.zdf.app.data.AppData
import de.zdf.app.data.Categories
import de.zdf.app.data.Repository
import de.zdf.app.data.Source
import de.zdf.app.ui.components.SectionCard
import de.zdf.app.ui.components.sourceLabel
import de.zdf.app.ui.money
import de.zdf.app.ui.parseUserAmount
import java.text.DateFormat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun AccountsScreen(data: AppData, repo: Repository, padding: PaddingValues, onConnectBank: () -> Unit, onConnectPaypal: () -> Unit) {
    val ctx = LocalContext.current
    var csvBytes by remember { mutableStateOf<ByteArray?>(null) }
    var csvName by remember { mutableStateOf("") }
    var editAccount by remember { mutableStateOf<Account?>(null) }
    var newManual by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var exportSecrets by remember { mutableStateOf(false) }

    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            csvBytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            csvName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "CSV-Konto"
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson(exportSecrets).encodeToByteArray()) }
            Toast.makeText(ctx, "Backup gespeichert", Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                repo.importJson(text)
                Toast.makeText(ctx, "Backup wiederhergestellt", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                info = "Backup konnte nicht gelesen werden: ${e.message}"
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Konten") {
                if (data.accounts.isEmpty()) Text("Noch keine Konten.", style = MaterialTheme.typography.bodySmall)
                data.accounts.forEach { a ->
                    Column(Modifier.fillMaxWidth().clickable { editAccount = a }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name + if (a.hidden) " (ausgeblendet)" else "", fontWeight = FontWeight.Medium)
                                Text(sourceLabel(a) + (a.iban?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                a.lastSync?.let { Text("Stand: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(money(a.balanceCents, a.currency), fontWeight = FontWeight.SemiBold)
                        }
                        a.lastError?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                data.connections.forEach { c ->
                    c.validUntil?.let { Text("Zugriff ${c.aspspName} gültig bis ${it.take(10)} – danach neu verbinden (PSD2-Vorgabe).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            SectionCard(title = "Konto hinzufügen") {
                Action(Icons.Default.AccountBalance, "Bankkonto verbinden", "Über PSD2/Open Banking (Enable Banking) – fast alle deutschen Banken", onConnectBank)
                Action(Icons.Default.Payments, "PayPal verbinden", "Über die offizielle PayPal-API mit deiner eigenen App", onConnectPaypal)
                Action(Icons.Default.UploadFile, "CSV-Kontoauszug importieren", "Funktioniert mit jeder Bank und dem PayPal-Aktivitätenbericht") {
                    pickCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                }
                Action(Icons.Default.Add, "Manuelles Konto", "z. B. Bargeld, Sparschwein, Depot-Wert") { newManual = true }
            }
        }
        if (data.rules.isNotEmpty()) {
            item {
                SectionCard(title = "Eigene Kategorie-Regeln") {
                    data.rules.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("„${r.pattern}“ → ${Categories.get(r.category).label}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton({ repo.deleteRule(r) }) { Icon(Icons.Default.Delete, "Regel löschen") }
                        }
                    }
                }
            }
        }
        item { UpdateSection() }
        item {
            SectionCard(title = "Sicherheit & Daten") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("App-Sperre")
                        Text("Fingerabdruck / Gesicht / PIN beim Öffnen", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(data.settings.appLock, { repo.setAppLock(it) })
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Alle Daten liegen AES-verschlüsselt nur auf diesem Gerät. Es gibt keinen ZDF-Server.", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { exportSecrets = !exportSecrets }) {
                    Checkbox(exportSecrets, { exportSecrets = it })
                    Text("Backup inkl. API-Schlüssel (unverschlüsselt!)", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton({ exportLauncher.launch("zdf-backup-${java.time.LocalDate.now()}.json") }) { Text("Backup exportieren") }
                    TextButton({ importLauncher.launch(arrayOf("application/json", "*/*")) }) { Text("Backup laden") }
                }
                Row {
                    TextButton({ repo.loadDemo() }) { Text("Demo-Daten laden") }
                    TextButton({ confirmWipe = true }) { Text("Alles löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            Text(
                "ZDF – Zahlen, Daten, Fakten · Open Source & self-hosted.\nBankzugriff: Enable Banking (lizenzierter Kontoinformationsdienst nach PSD2). Deine Zugangsdaten zur Bank gibst du nur auf der Seite deiner Bank ein.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
            )
        }
    }

    // ---- CSV: Zielkonto wählen
    csvBytes?.let { bytes ->
        var target by remember { mutableStateOf<String?>(null) }
        var newName by remember { mutableStateOf(csvName) }
        AlertDialog(
            onDismissRequest = { csvBytes = null },
            title = { Text("CSV importieren in …") },
            text = {
                Column {
                    data.accounts.filter { it.source != Source.ENABLE_BANKING && it.source != Source.PAYPAL }.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { target = a.id }) {
                            RadioButton(target == a.id, { target = a.id }); Text(a.name)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { target = null }) {
                        RadioButton(target == null, { target = null }); Text("Neues Konto")
                    }
                    if (target == null) OutlinedTextField(newName, { newName = it }, label = { Text("Kontoname") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton({
                    try {
                        val id = target ?: repo.createManualAccount(newName, 0, Source.CSV)
                        val (_, msg) = repo.importCsv(id, bytes)
                        info = msg + if (target == null) "\n\nTipp: Tippe auf das Konto, um den aktuellen Kontostand einzutragen." else ""
                    } catch (e: Exception) {
                        info = "Import fehlgeschlagen: ${e.message}"
                    }
                    csvBytes = null
                }) { Text("Importieren") }
            },
            dismissButton = { TextButton({ csvBytes = null }) { Text("Abbrechen") } },
        )
    }

    if (newManual) {
        var name by remember { mutableStateOf("") }
        var bal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newManual = false },
            title = { Text("Manuelles Konto") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(bal, { bal = it }, label = { Text("Kontostand in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = { TextButton({ repo.createManualAccount(name, parseUserAmount(bal) ?: 0); newManual = false }) { Text("Anlegen") } },
            dismissButton = { TextButton({ newManual = false }) { Text("Abbrechen") } },
        )
    }

    editAccount?.let { a -> EditAccountDialog(a, repo) { editAccount = null } }

    info?.let { msg ->
        AlertDialog({ info = null }, confirmButton = { TextButton({ info = null }) { Text("OK") } }, title = { Text("Hinweis") }, text = { Text(msg) })
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wirklich alles löschen?") },
            text = { Text("Konten, Umsätze, Regeln, Budgets und Zugangsdaten werden von diesem Gerät entfernt.") },
            confirmButton = { TextButton({ repo.wipe(); confirmWipe = false }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmWipe = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun EditAccountDialog(a: Account, repo: Repository, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var bal by remember { mutableStateOf(String.format(java.util.Locale.GERMANY, "%.2f", a.balanceCents / 100.0)) }
    var hidden by remember { mutableStateOf(a.hidden) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editableBalance = a.source == Source.MANUAL || a.source == Source.CSV || a.source == Source.DEMO
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konto bearbeiten") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (editableBalance) OutlinedTextField(bal, { bal = it }, label = { Text("Kontostand in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hidden = !hidden }) {
                    Checkbox(hidden, { hidden = it }); Text("In Übersicht & Analyse ausblenden")
                }
                Spacer(Modifier.height(8.dp))
                TextButton({ confirmDelete = true }) { Text("Konto & Umsätze entfernen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton({ repo.editAccount(a.id, name, if (editableBalance) parseUserAmount(bal) else null, hidden); onDismiss() }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${a.name}“ entfernen?") },
            text = { Text("Alle Umsätze dieses Kontos werden vom Gerät gelöscht." + if (a.source == Source.ENABLE_BANKING) " Der Bankzugriff wird widerrufen." else "") },
            confirmButton = { TextButton({ repo.deleteAccount(a.id); confirmDelete = false; onDismiss() }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state by de.zdf.app.update.Updater.state.collectAsStateWithLifecycle()
    var auto by remember { mutableStateOf(de.zdf.app.update.UpdatePrefs.auto(ctx)) }
    SectionCard(title = "App-Updates") {
        Text("Installierte Version: ${de.zdf.app.update.Updater.currentVersionName}", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatisch aktualisieren")
                Text("Neue Versionen aus dem GitHub-Repository laden und installieren", style = MaterialTheme.typography.bodySmall)
            }
            Switch(auto, { auto = it; de.zdf.app.update.UpdatePrefs.setAuto(ctx, it) })
        }
        Text(
            when (state) {
                de.zdf.app.update.UpdateState.Checking -> "Suche …"
                de.zdf.app.update.UpdateState.UpToDate -> "✓ Du hast die neueste Version."
                is de.zdf.app.update.UpdateState.Available -> "Update verfügbar."
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton({ scope.launch { de.zdf.app.update.Updater.check(ctx) } }) { Text("Jetzt nach Updates suchen") }
    }
}
