package de.zdf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.zdf.app.data.Source
import de.zdf.app.ui.AppRoot
import de.zdf.app.ui.theme.ZdfTheme

class MainActivity : FragmentActivity() {

    private val unlocked = mutableStateOf(false)
    private val repo get() = (application as ZdfApp).repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            ZdfTheme {
                AppRoot(repo = repo, unlocked = unlocked.value, onUnlock = ::authenticate)
            }
        }
        val d = repo.data.value
        val stale = d.accounts.filter { it.source == Source.ENABLE_BANKING || it.source == Source.PAYPAL }
            .any { (it.lastSync ?: 0) < System.currentTimeMillis() - 6 * 3600_000 }
        if (stale) repo.syncAll()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) unlocked.value = false
    }

    override fun onResume() {
        super.onResume()
        if (repo.data.value.settings.appLock && !unlocked.value) authenticate()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "zdf" && uri.host == "callback") {
            repo.completeBankAuth(uri.toString())
            intent.data = null
        }
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked.value = true
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ZDF entsperren")
            .setSubtitle("Deine Finanzen sind geschützt")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { unlocked.value = true }
    }
}
