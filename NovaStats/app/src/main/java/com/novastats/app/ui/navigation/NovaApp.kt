package com.novastats.app.ui.navigation

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

/** Onglets visibles dans la barre (les autres restent accessibles par navigation). */
private val bottomTabs = listOf(NovaTab.HOME, NovaTab.STATS, NovaTab.BILLBOARD, NovaTab.CERTIFICATIONS, NovaTab.PANTHEON, NovaTab.SETTINGS)

@Composable
fun NovaApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val theme = Nova.theme

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = theme.surface) {
                bottomTabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            indicatorColor = theme.primary.copy(alpha = 0.15f),
                            unselectedIconColor = theme.textSecondary,
                            unselectedTextColor = theme.textSecondary
                        )
                    )
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
            composable(NovaTab.RECORDS.route) { PlaceholderScreen(NovaTab.RECORDS, "Les 24 records") }
            composable(NovaTab.CERTIFICATIONS.route) { PlaceholderScreen(NovaTab.CERTIFICATIONS, "Argent · Or · Platine · Diamant") }
            composable(NovaTab.HALL_OF_FAME.route) { PlaceholderScreen(NovaTab.HALL_OF_FAME, "Direct Debut · Long Run · Triple Debut · Legendary Run") }
            composable(NovaTab.PANTHEON.route) { PlaceholderScreen(NovaTab.PANTHEON, "Star → Superstar → Megastar → Légende → Mythique") }
            composable(NovaTab.AWARDS.route) { PlaceholderScreen(NovaTab.AWARDS, "9 récompenses annuelles") }
            composable(NovaTab.SETTINGS.route) { SettingsScreen() }
        }
    }
}
