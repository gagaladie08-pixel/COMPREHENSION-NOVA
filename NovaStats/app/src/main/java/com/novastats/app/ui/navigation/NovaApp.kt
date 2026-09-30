package com.novastats.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.novastats.app.ui.screens.PantheonScreen
import com.novastats.app.ui.screens.SettingsScreen
import com.novastats.app.ui.screens.StatsScreen
import com.novastats.app.ui.theme.Nova

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
                        modifier = Modifier.width(76.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) theme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, tint = if (selected) theme.primary else theme.textSecondary)
                        Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = if (selected) theme.primary else theme.textSecondary)
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NovaTab.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(NovaTab.HOME.route) { HomeScreen(onOpenTab = { navController.navigate(it.route) }) }
            composable(NovaTab.STATS.route) { StatsScreen() }
            composable(NovaTab.BILLBOARD.route) { BillboardScreen() }
            composable(NovaTab.RECORDS.route) { RecordsScreen() }
            composable(NovaTab.CERTIFICATIONS.route) { PlaceholderScreen(NovaTab.CERTIFICATIONS, "Argent · Or · Platine · Diamant") }
            composable(NovaTab.HALL_OF_FAME.route) { HallOfFameScreen() }
            composable(NovaTab.PANTHEON.route) { PantheonScreen() }
            composable(NovaTab.AWARDS.route) { PlaceholderScreen(NovaTab.AWARDS, "9 récompenses annuelles") }
            composable(NovaTab.SETTINGS.route) { SettingsScreen() }
        }
    }
}
