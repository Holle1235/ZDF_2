package de.zdf.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.zdf.app.data.Repository
import de.zdf.app.ui.screens.AccountsScreen
import de.zdf.app.ui.screens.AnalysisScreen
import de.zdf.app.ui.screens.ConnectBankScreen
import de.zdf.app.ui.screens.ContractsScreen
import de.zdf.app.ui.screens.DashboardScreen
import de.zdf.app.ui.screens.PayPalScreen
import de.zdf.app.ui.screens.TransactionsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Übersicht", Icons.Default.Dashboard),
    Tab("transactions", "Umsätze", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("contracts", "Verträge", Icons.Default.Autorenew),
    Tab("analysis", "Analyse", Icons.Default.PieChart),
)

private val titles = mapOf(
    "dashboard" to "ZDF", "transactions" to "Umsätze", "contracts" to "Verträge & Abos", "analysis" to "Analyse",
    "accounts" to "Konten & Einstellungen", "bank" to "Bankkonto verbinden", "paypal" to "PayPal verbinden",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(repo: Repository, unlocked: Boolean, onUnlock: () -> Unit) {
    val data by repo.data.collectAsStateWithLifecycle()
    val sync by repo.sync.collectAsStateWithLifecycle()

    if (data.settings.appLock && !unlocked) {
        LockScreen(onUnlock); return
    }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?') ?: "dashboard"
    val isTab = tabs.any { it.route == route }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(sync.message, sync.running) {
        val m = sync.message
        if (m != null && !sync.running) {
            snackbar.showSnackbar(m)
            repo.clearMessage()
        }
    }

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(titles[route] ?: "ZDF", fontWeight = if (route == "dashboard") FontWeight.Black else FontWeight.SemiBold) },
                    navigationIcon = {
                        if (!isTab) IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    },
                    actions = {
                        if (sync.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else IconButton({ repo.syncAll() }) { Icon(Icons.Default.Refresh, "Aktualisieren") }
                        if (isTab) IconButton({ nav.navigate("accounts") }) { Icon(Icons.Default.Settings, "Konten & Einstellungen") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                if (sync.running) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    sync.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally)) }
                }
            }
        },
        bottomBar = {
            if (isTab) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { goTab(t.route) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(nav, startDestination = "dashboard") {
            composable("dashboard") {
                DashboardScreen(
                    data, repo, padding,
                    onOpenAccounts = { nav.navigate("accounts") },
                    onOpenTransactions = { goTab("transactions") },
                    onOpenContracts = { goTab("contracts") },
                    onOpenAnalysis = { goTab("analysis") },
                )
            }
            composable(
                "transactions?cat={cat}&month={month}",
                arguments = listOf(
                    navArgument("cat") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("month") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e ->
                TransactionsScreen(data, repo, padding, e.arguments?.getString("cat"), e.arguments?.getString("month"))
            }
            composable("contracts") { ContractsScreen(data, repo, padding) }
            composable("analysis") {
                AnalysisScreen(data, repo, padding) { cat, month -> nav.navigate("transactions?cat=$cat&month=$month") }
            }
            composable("accounts") {
                AccountsScreen(data, repo, padding, onConnectBank = { nav.navigate("bank") }, onConnectPaypal = { nav.navigate("paypal") })
            }
            composable("bank") { ConnectBankScreen(data, repo, padding) }
            composable("paypal") { PayPalScreen(data, repo, padding) { nav.popBackStack() } }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("ZDF ist gesperrt", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onUnlock) { Text("Entsperren") }
        }
    }
}
