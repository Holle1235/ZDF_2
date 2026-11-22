package de.zdf.app.data

import android.content.Context
import de.zdf.app.security.Crypto
import de.zdf.app.security.DataCipher
import kotlinx.serialization.json.Json
import java.io.File

/** Speichert alle Daten verschlüsselt im privaten App-Speicher. */
class Store(context: Context, private val cipher: DataCipher = Crypto) {
    private val file = File(context.filesDir, "zdf.bin")
    private val tmp = File(context.filesDir, "zdf.bin.tmp")

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            json.decodeFromString(AppData.serializer(), cipher.decrypt(file.readBytes()).decodeToString())
        } catch (e: Exception) {
            // Beschädigte Datei sichern statt still zu überschreiben
            file.copyTo(File(file.parentFile, "zdf.bin.broken-${System.currentTimeMillis()}"), overwrite = true)
            AppData()
        }
    }

    @Synchronized
    fun save(data: AppData) {
        val bytes = cipher.encrypt(json.encodeToString(AppData.serializer(), data).encodeToByteArray())
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
    }

    fun exportJson(data: AppData, includeSecrets: Boolean): String {
        val d = if (includeSecrets) data else data.copy(
            settings = data.settings.copy(ebPrivateKeyPem = "", paypalSecret = "")
        )
        return Json { prettyPrint = true; encodeDefaults = true }.encodeToString(AppData.serializer(), d)
    }

    fun importJson(text: String): AppData = json.decodeFromString(AppData.serializer(), text)

    fun wipe() {
        file.delete(); tmp.delete()
    }
}
