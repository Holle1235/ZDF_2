package de.zdf.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.zdf.app.data.AppData
import de.zdf.app.data.Repository
import de.zdf.app.ui.components.SectionCard

@Composable
fun PayPalScreen(data: AppData, repo: Repository, padding: PaddingValues, onDone: () -> Unit) {
    val s = data.settings
    var id by remember { mutableStateOf(s.paypalClientId) }
    var secret by remember { mutableStateOf(s.paypalSecret) }
    var sandbox by remember { mutableStateOf(s.paypalSandbox) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "PayPal über die offizielle API") {
            Text(
                "1. Auf developer.paypal.com mit deinem PayPal-Konto anmelden (die API setzt ein Geschäftskonto voraus – ein privates Konto lässt sich kostenlos umstellen).\n" +
                    "2. „Apps & Credentials“ → Live → „Create App“.\n" +
                    "3. In den App-Einstellungen unter „Features“ den Haken bei „Transaction Search“ setzen.\n" +
                    "4. Client ID und Secret hier eintragen. Neue Umsätze erscheinen bei PayPal mit bis zu 3 h Verzögerung.\n\n" +
                    "Alternative ohne API: In PayPal unter Aktivitäten → Kontoauszüge einen CSV-Bericht herunterladen und unter Konten → CSV importieren.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(id, { id = it }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(secret, { secret = it }, label = { Text("Secret") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sandbox (Testumgebung)", modifier = Modifier.weight(1f))
                Switch(sandbox, { sandbox = it })
            }
            Spacer(Modifier.height(8.dp))
            Button(enabled = id.isNotBlank() && secret.isNotBlank(), onClick = { repo.connectPaypal(id, secret, sandbox); onDone() }) {
                Text("Verbinden & abrufen")
            }
        }
        SectionCard(title = "Keine Doppelzählung") {
            Text(
                "Zahlst du mit PayPal per Lastschrift, taucht die Abbuchung auch auf deinem Girokonto auf. ZDF markiert diese automatisch als Umbuchung – gezählt wird nur der echte Händler aus PayPal.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
