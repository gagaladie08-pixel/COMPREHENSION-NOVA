package com.novastats.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.novastats.app.ui.screens.BillboardScreen
import com.novastats.app.ui.screens.HomeScreen
import com.novastats.app.ui.screens.PlaceholderScreen
import com.novastats.app.ui.screens.RecordsScreen
import com.novastats.app.ui.screens.HallOfFameScreen
import com.novastats.app.ui.screens.CertificationsScreen
import com.novastats.app.ui.screens.AwardsScreen
import com.novastats.app.ui.screens.PantheonScreen
import com.novastats.app.ui.screens.SettingsScreen
import com.novastats.app.ui.screens.StatsScreen
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaMotion
import com.novastats.app.ui.theme.ThemeEvents
import com.novastats.app.ui.theme.ThemeFxHost
import com.novastats.app.ui.theme.ThemedTabIcon

/** Les onglets de la barre de navigation inférieure (ordre du cahier des charges). */
enum class NovaTab(val route: String, val label: String, val emoji: String, val icon: ImageVector) {
    HOME("home", "Accueil", "🏠", Icons.Filled.Home),
    STATS("stats", "Stats", "📊", Icons.Filled.BarChart),
    BILLBOARD("billboard", "Billboard", "🏆", Icons.Filled.EmojiEvents),
    RECORDS("records", "Records", "🏅", Icons.Filled.MilitaryTech),
    CERTIFICATIONS("certifications", "Certifs", "💎", Icons.Filled.Diamond),
    HALL_OF_FAME("hall_of_fame", "Hall of Fame", "🏛️", Icons.Filled.AccountBalance),
    PANTHEON("pantheon", "Panthéon", "👑", Icons.Filled.Star),
    AWARDS("awards", "Awards", "🏆", Icons.Filled.WorkspacePremium),
    SETTINGS("settings", "Réglages", "⚙️", Icons.Filled.Settings);
}

/** Tous les onglets du cahier des charges — la barre défile horizontalement. */
private val bottomTabs = NovaTab.entries

@Composable
fun NovaApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val theme = Nova.theme

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val scroll = rememberScrollState()
            val selectedIndex = bottomTabs.indexOfFirst { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
            // Garde l'onglet actif visible dans la barre défilante
            val itemPx = with(LocalDensity.current) { 76.dp.toPx() }
            LaunchedEffect(selectedIndex) {
                if (selectedIndex >= 0) scroll.animateScrollTo(((selectedIndex - 2) * itemPx).toInt().coerceAtLeast(0))
            }
            Row(
                Modifier.fillMaxWidth().background(theme.surface).navigationBarsPadding().horizontalScroll(scroll).padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                bottomTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(76.dp).clip(Nova.chipShape)
                            .background(if (selected) theme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                if (!selected) ThemeEvents.tabChanged()
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        ThemedTabIcon(tab.icon, contentDescription = tab.label, selected = selected)
                        Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = if (selected) theme.primary else theme.textSecondary)
                    }
                }
            }
        }
    ) { padding ->
      Column(Modifier.padding(padding)) {
        // Bannière 🔴 permanente tant que l'accès aux notifications n'est pas accordé (détection impossible)
        val ctx = LocalContext.current
        var listenerOk by remember { mutableStateOf(com.novastats.app.service.NovaListenerService.isEnabled(ctx)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { listenerOk = com.novastats.app.service.NovaListenerService.isEnabled(ctx); onPauseOrDispose { } }
        if (!listenerOk) Row(
            Modifier.fillMaxWidth().background(Color(0xFFE74C3C)).clickable { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔴 Détection inactive — NovaStats n'écoute pas. Touche pour autoriser l'accès aux notifications.", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        }
        // 🟠 Permission OK mais service endormi (pas de battement de cœur) → relance au toucher
        val detection by com.novastats.app.service.DetectionState.state.collectAsStateWithLifecycle()
        val health by com.novastats.app.service.ServiceHealth.state.collectAsStateWithLifecycle()
        var batteryExempt by remember { mutableStateOf(com.novastats.app.service.Watchdog.isBatteryExempt(ctx)) }
        var batteryDismissed by rememberSaveable { mutableStateOf(false) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { batteryExempt = com.novastats.app.service.Watchdog.isBatteryExempt(ctx); onPauseOrDispose { } }
        if (listenerOk && (!detection.listenerConnected || health.isStale())) Row(
            Modifier.fillMaxWidth().background(Color(0xFFE67E22)).clickable { com.novastats.app.service.Watchdog.revive(ctx, "bannière Accueil") }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🟠 Service de détection endormi (dernier signe de vie : ${com.novastats.app.service.ServiceHealth.ago(health.lastHeartbeat)}). Touche pour le relancer.",
                color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)
            )
        }
        // 🔋 Optimisation batterie active → l'OS peut geler la détection hors de l'app
        if (listenerOk && !batteryExempt && !batteryDismissed) Row(
            Modifier.fillMaxWidth().background(Color(0xFFB7950B)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🔋 Optimisation batterie active : Android peut couper la détection quand l'app est fermée. Touche pour l'exclure.",
                color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f).clickable {
                    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${ctx.packageName}"))) }
                        .onFailure { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
            )
            Text("  ✕", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { batteryDismissed = true })
        }
        ThemeFxHost(Modifier.weight(1f)) {
        NavHost(
            navController = navController,
            startDestination = NovaTab.HOME.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { NovaMotion.enter(theme) },
            exitTransition = { NovaMotion.exit(theme) },
            popEnterTransition = { NovaMotion.enter(theme) },
            popExitTransition = { NovaMotion.exit(theme) }
        ) {
            composable(NovaTab.HOME.route) { HomeScreen(onOpenTab = { navController.navigate(it.route) }) }
            composable(NovaTab.STATS.route) { StatsScreen() }
            composable(NovaTab.BILLBOARD.route) { BillboardScreen() }
            composable(NovaTab.RECORDS.route) { RecordsScreen() }
            composable(NovaTab.CERTIFICATIONS.route) { CertificationsScreen() }
            composable(NovaTab.HALL_OF_FAME.route) { HallOfFameScreen() }
            composable(NovaTab.PANTHEON.route) { PantheonScreen() }
            composable(NovaTab.AWARDS.route) { AwardsScreen() }
            composable(NovaTab.SETTINGS.route) { SettingsScreen() }
        }
        }
      }
    }
}
