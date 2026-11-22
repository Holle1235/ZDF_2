package de.zdf.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.zdf.app.update.Release
import de.zdf.app.update.UpdateState
import de.zdf.app.update.Updater
import kotlinx.coroutines.launch

/** Startet Download + Installation; fragt vorher einmalig die Berechtigung „Unbekannte Apps installieren“ an. */
@Composable
fun rememberInstallAction(): (Release) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return { rel ->
        if (!Updater.canInstall(ctx)) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
        } else {
            scope.launch { Updater.downloadAndInstall(ctx, rel) }
        }
    }
}

@Composable
fun UpdateDialogs() {
    val state by Updater.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val install = rememberInstallAction()
    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Update auf ${s.release.versionName}") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Installiert: ${Updater.currentVersionName}", style = MaterialTheme.typography.bodySmall)
                    if (s.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.release.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!Updater.canInstall(ctx)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Einmalig nötig: Erlaube ZDF im nächsten Bildschirm „Apps aus dieser Quelle zulassen“, komm zurück und tippe erneut auf Installieren. Danach laufen Updates automatisch.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = { TextButton({ install(s.release) }) { Text(if (Updater.canInstall(ctx)) "Installieren" else "Erlauben") } },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text("Später") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Lade ZDF ${s.release.versionName} …") },
            text = { LinearProgressIndicator(progress = { s.progress }) },
            confirmButton = {},
        )
        UpdateState.Installing -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Update wird installiert …") },
            text = { Text("ZDF startet danach neu. Falls Android nachfragt, bestätige die Installation.") },
            confirmButton = { TextButton({ Updater.dismiss() }) { Text("OK") } },
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("Update") },
            text = { Text(s.message) },
            confirmButton = { TextButton({ Updater.dismiss() }) { Text("OK") } },
        )
        else -> {}
    }
}
