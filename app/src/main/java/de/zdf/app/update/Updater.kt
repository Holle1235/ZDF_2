package de.zdf.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import de.zdf.app.BuildConfig
import de.zdf.app.net.ApiException
import de.zdf.app.net.Http
import de.zdf.app.net.arr
import de.zdf.app.net.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val versionCode: Int, val versionName: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Selbst-Update über GitHub-Releases des eigenen Repositorys.
 * Versionsschema: Tag "v1.1.<N>" ⇒ versionCode 100 + N (siehe .github/workflows/build.yml).
 */
object Updater {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionCode get() = BuildConfig.VERSION_CODE
    val currentVersionName get() = BuildConfig.VERSION_NAME

    private const val PREFS = "zdf_update"

    fun versionCodeFromTag(tag: String): Int? =
        tag.trim().removePrefix("v").split('.').lastOrNull()?.toIntOrNull()?.let { 100 + it }

    suspend fun fetchLatest(): Release? {
        val r = try {
            Http.request(
                "GET", "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest",
                mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "ZDF-App"),
            )
        } catch (e: ApiException) {
            if (e.status == 404) return null else throw e
        }
        val tag = r.str("tag_name") ?: return null
        val code = versionCodeFromTag(tag) ?: return null
        val asset = r.arr("assets").firstOrNull { it.str("name")?.endsWith(".apk") == true } ?: return null
        return Release(
            versionCode = code,
            versionName = tag.removePrefix("v"),
            notes = r.str("body").orEmpty().trim(),
            apkUrl = asset.str("browser_download_url") ?: return null,
            size = asset.str("size")?.toLongOrNull() ?: 0,
        )
    }

    /** Prüft auf ein Update. @return verfügbares Release oder null */
    suspend fun check(ctx: Context): Release? {
        _state.value = UpdateState.Checking
        return try {
            val rel = fetchLatest()
            ctx.getSharedPreferences(PREFS, 0).edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            if (rel != null && rel.versionCode > currentVersionCode) {
                _state.value = UpdateState.Available(rel); rel
            } else {
                _state.value = UpdateState.UpToDate; null
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Update-Prüfung fehlgeschlagen: ${e.message}")
            null
        }
    }

    fun lastCheck(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getLong("lastCheck", 0)

    fun dismiss() { _state.value = UpdateState.Idle }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** true, wenn ZDF sich selbst installiert hat – dann gehen Updates ab Android 12 ohne Rückfrage. */
    fun canUpdateSilently(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !canInstall(ctx)) return false
        return runCatching { ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == ctx.packageName }
            .getOrDefault(false)
    }

    suspend fun downloadAndInstall(ctx: Context, rel: Release) {
        try {
            val file = download(ctx, rel)
            _state.value = UpdateState.Installing
            install(ctx, file)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Update fehlgeschlagen: ${e.message}")
        }
    }

    private suspend fun download(ctx: Context, rel: Release): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "zdf-${rel.versionCode}.apk")
        var url = URL(rel.apkUrl)
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "ZDF-App")
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) {
                url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue
            }
            if (code != 200) throw ApiException(code, "Download HTTP $code")
            break
        }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.size
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) _state.value = UpdateState.Downloading(rel, done.toFloat() / total)
                }
            }
        }
        conn.disconnect()
        out
    }

    private suspend fun install(ctx: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("zdf.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }

    internal fun onInstallResult(ok: Boolean, message: String?) {
        _state.value = if (ok) UpdateState.Idle else UpdateState.Failed("Installation abgebrochen${message?.let { ": $it" } ?: ""}")
    }
}
