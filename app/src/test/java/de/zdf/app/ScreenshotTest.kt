package de.zdf.app

import android.graphics.Bitmap


import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText

import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import de.zdf.app.data.Repository
import de.zdf.app.data.Store
import de.zdf.app.security.DataCipher
import de.zdf.app.ui.AppRoot
import de.zdf.app.ui.theme.ZdfTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private object PlainCipher : DataCipher {
    override fun encrypt(plain: ByteArray) = plain
    override fun decrypt(data: ByteArray) = data
}

/** Rendert die echte Oberfläche mit Demo-Daten (läuft ohne Gerät). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        val dir = File(System.getProperty("zdf.shots") ?: "build/screenshots").apply { mkdirs() }
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun screens() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = Repository(ctx, Store(ctx, PlainCipher))
        repo.wipe()
        rule.setContent { ZdfTheme { AppRoot(repo, unlocked = true, onUnlock = {}) } }
        rule.waitForIdle(); shot("0-empty")
        repo.loadDemo()
        assertTrue(repo.data.value.txns.size > 50)
        rule.waitForIdle(); shot("1-dashboard")
        for ((tab, n) in listOf("Umsätze" to "2-transactions", "Verträge" to "3-contracts", "Analyse" to "4-analysis")) {
            rule.onAllNodesWithText(tab)[0].performClick()
            rule.waitForIdle(); shot(n)
        }
        rule.onNodeWithContentDescriptionSafe("Konten & Einstellungen")
        rule.waitForIdle(); shot("5-accounts")
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithContentDescriptionSafe(d: String) {
        onAllNodes(androidx.compose.ui.test.hasContentDescription(d))[0].performClick()
    }
}
