package com.tempmonitor.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tempmonitor.app.ui.dashboard.DashboardScreen
import com.tempmonitor.app.ui.history.HistoryScreen
import com.tempmonitor.app.ui.info.InfoScreen
import com.tempmonitor.app.ui.session.SessionScreen
import com.tempmonitor.app.ui.settings.SettingsScreen

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : Destination("dashboard", "Dashboard", Icons.Filled.Thermostat)
    data object Sessions : Destination("sessions", "Sesi", Icons.Filled.SportsEsports)
    data object History : Destination("history", "History", Icons.Filled.History)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)
    data object Info : Destination("info", "Bantuan", Icons.AutoMirrored.Filled.HelpOutline)
}

private val items = listOf(
    Destination.Dashboard,
    Destination.Sessions,
    Destination.History,
    Destination.Settings,
    Destination.Info
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { dest ->
                    val selected = currentRoute == dest.route ||
                        backStackEntry?.destination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Dashboard.route) { DashboardScreen() }
            composable(Destination.Sessions.route) { SessionScreen() }
            composable(Destination.History.route) { HistoryScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
            composable(Destination.Info.route) { InfoScreen() }
        }
    }
}
