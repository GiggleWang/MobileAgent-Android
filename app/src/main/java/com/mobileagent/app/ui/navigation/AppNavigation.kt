package com.mobileagent.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobileagent.app.MainActivity
import com.mobileagent.app.R
import com.mobileagent.app.data.PreferencesManager
import com.mobileagent.app.ui.screens.MainScreen
import com.mobileagent.app.ui.screens.PermissionGuideScreen
import com.mobileagent.app.ui.screens.SettingsScreen

sealed class Screen(val route: String, val labelRes: Int) {
    data object Main : Screen("main", R.string.nav_main)
    data object Settings : Screen("settings", R.string.nav_settings)
    data object Permissions : Screen("permissions", R.string.nav_permissions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(preferencesManager: PreferencesManager, initialRoute: String? = null) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Main, Screen.Settings, Screen.Permissions)

    LaunchedEffect(initialRoute) {
        if (initialRoute == "permissions") {
            navController.navigate(Screen.Permissions.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    val pendingRoute = MainActivity.pendingNavRoute
    LaunchedEffect(pendingRoute) {
        if (pendingRoute == "permissions") {
            navController.navigate(Screen.Permissions.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
            MainActivity.pendingNavRoute = null
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
                    val icon = when (screen) {
                        Screen.Main -> Icons.Default.Home
                        Screen.Settings -> Icons.Default.Settings
                        Screen.Permissions -> Icons.Default.Security
                    }
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(screen.labelRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Main.route) { MainScreen() }
            composable(Screen.Settings.route) { SettingsScreen(preferencesManager) }
            composable(Screen.Permissions.route) { PermissionGuideScreen() }
        }
    }
}
