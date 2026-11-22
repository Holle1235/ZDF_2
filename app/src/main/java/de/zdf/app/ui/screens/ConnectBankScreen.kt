package de.zdf.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.zdf.app.data.AppData
import de.zdf.app.data.Repository
import de.zdf.app.net.Aspsp
import de.zdf.app.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun ConnectBankScreen(data: AppData, repo: Repository, padding: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var appId by remember { mutableStateOf(s.ebAppId) }
    var pem by remember { mutableStateOf(s.ebPrivateKeyPem) }
    var redirect by remember { mutableStateOf(s.ebRedirectUrl) }
    var editCreds by remember { mutableStateOf(s.ebAppId.isBlank() || s.ebPrivateKeyPem.isBlank()) }
    var country by remember { mutableStateOf("DE") }
    var banks by remember { mutableStateOf<List<Aspsp>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var callback by remember { mutableStateOf("") }

    val pickPem = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pem = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
    }

    fun loadBanks() {
        loading = true; error = null
        scope.launch {
            try { banks = repo.listBanks(country.trim().uppercase()) } catch (e: Exception) { error = e.message }
            loading = false
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "1 · Enable-Banking-Zugang") {
                if (!editCreds) {
                    Text("App-ID: ${s.ebAppId.take(8)}…  ✓ Schlüssel hinterlegt", style = MaterialTheme.typography.bodyMedium)
                    TextButton({ editCreds = true }) { Text("Ändern") }
                } else {
                    Text(
                        "Einmalig: Kostenlosen Account auf enablebanking.com anlegen → Control Panel → „Applications“ → neue App (Environment: Production) erstellen. " +
                            "Als Redirect-URL die unten stehende URL eintragen. Den heruntergeladenen Private Key (.pem) hier auswählen und danach in der App „Link accounts“ deine eigenen Konten aktivieren.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(appId, { appId = it }, label = { Text("Application ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row {
                        OutlinedButton({ pickPem.launch(arrayOf("*/*")) }) { Text(if (pem.contains("PRIVATE KEY")) "✓ Private Key geladen" else "Private Key (.pem) wählen") }
                    }
                    OutlinedTextField(redirect, { redirect = it }, label = { Text("Redirect-URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = appId.isNotBlank() && pem.contains("PRIVATE KEY") && redirect.startsWith("https://"),
                        onClick = { repo.saveEbCredentials(appId, pem, redirect); editCreds = false; loadBanks() },
                    ) { Text("Speichern") }
                }
            }
        }
        if (!editCreds) {
            item {
                SectionCard(title = "2 · Bank auswählen") {
                    Row {
                        OutlinedTextField(country, { country = it.take(2) }, label = { Text("Land") }, singleLine = true, modifier = Modifier.weight(0.3f))
                        Spacer(Modifier.padding(4.dp))
                        OutlinedTextField(filter, { filter = it }, label = { Text("Bank suchen") }, singleLine = true, modifier = Modifier.weight(0.7f))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (banks.isEmpty() && !loading) Button(::loadBanks) { Text("Banken laden") }
                    if (loading) CircularProgressIndicator()
                    error?.let { Text("Fehler: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val shown = banks.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }.take(80)
            items(shown, key = { it.name + it.country }) { b ->
                Column(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch {
                            try {
                                val url = repo.startBankAuth(b)
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }.padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(b.name, fontWeight = FontWeight.Medium)
                    HorizontalDivider(Modifier.padding(top = 10.dp))
                }
            }
            item {
                SectionCard(title = "3 · Zurück aus dem Bank-Login") {
                    Text(
                        "Nach der Freigabe bei deiner Bank springst du automatisch zurück in ZDF. Falls nicht (z. B. weil die Redirect-Seite nicht erreichbar ist), kopiere die komplette URL aus der Adresszeile des Browsers hier hinein:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(callback, { callback = it }, label = { Text("…?code=…&state=…") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Button(enabled = callback.contains("code="), onClick = { repo.completeBankAuth(callback); callback = "" }) { Text("Verbindung abschließen") }
                }
            }
        }
    }
}
