package com.scamshield.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scamshield.app.share.SharedInputHolder
import com.scamshield.app.ui.apps.AppsScreen
import com.scamshield.app.ui.breach.BreachScreen
import com.scamshield.app.ui.calls.CallsScreen
import com.scamshield.app.ui.family.FamilyScreen
import com.scamshield.app.ui.home.HomeScreen
import com.scamshield.app.ui.paywall.PaywallScreen
import com.scamshield.app.ui.scan.ScanResultScreen
import com.scamshield.app.ui.scan.ScanScreen
import com.scamshield.app.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val CALLS = "calls"
    const val APPS = "apps"
    const val FAMILY = "family"
    const val PAYWALL = "paywall"
    const val SETTINGS = "settings"
    const val BREACH = "breach"
    const val RESULT = "result/{id}"
    fun result(id: Long) = "result/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Default.Home),
    Tab(Routes.SCAN, "Scan", Icons.Default.Search),
    Tab(Routes.CALLS, "Calls", Icons.Default.Call),
    Tab(Routes.APPS, "Apps", Icons.Default.Apps),
    Tab(Routes.FAMILY, "Family", Icons.Default.Group)
)

@HiltViewModel
class RootViewModel @Inject constructor(val sharedInput: SharedInputHolder) : ViewModel()

@Composable
fun ScamShieldRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val pendingInput by rootViewModel.sharedInput.pendingInput.collectAsStateWithLifecycle()
    val pendingRoute by rootViewModel.sharedInput.pendingRoute.collectAsStateWithLifecycle()

    // Ask for notification permission once on Android 13+.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Shared text / opened link -> Scan tab (the ScanViewModel consumes the input itself).
    LaunchedEffect(pendingInput) {
        if (pendingInput != null) navController.navigateTab(Routes.SCAN)
    }
    // Notification tap -> route.
    LaunchedEffect(pendingRoute) {
        val route = rootViewModel.sharedInput.consumeRoute() ?: return@LaunchedEffect
        if (tabs.any { it.route == route }) navController.navigateTab(route) else navController.navigate(route)
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val showBar = tabs.any { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = { navController.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onScan = { navController.navigateTab(Routes.SCAN) },
                    onCalls = { navController.navigateTab(Routes.CALLS) },
                    onApps = { navController.navigateTab(Routes.APPS) },
                    onFamily = { navController.navigateTab(Routes.FAMILY) },
                    onPaywall = { navController.navigate(Routes.PAYWALL) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onBreach = { navController.navigate(Routes.BREACH) }
                )
            }
            composable(Routes.SCAN) {
                ScanScreen(
                    onResult = { id -> navController.navigate(Routes.result(id)) },
                    onPaywall = { navController.navigate(Routes.PAYWALL) }
                )
            }
            composable(Routes.CALLS) { CallsScreen(onPaywall = { navController.navigate(Routes.PAYWALL) }) }
            composable(Routes.APPS) { AppsScreen(onPaywall = { navController.navigate(Routes.PAYWALL) }) }
            composable(Routes.FAMILY) { FamilyScreen(onPaywall = { navController.navigate(Routes.PAYWALL) }) }
            composable(Routes.PAYWALL) { PaywallScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.BREACH) {
                BreachScreen(onBack = { navController.popBackStack() }, onPaywall = { navController.navigate(Routes.PAYWALL) })
            }
            composable(
                Routes.RESULT,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                ScanResultScreen(scanId = id, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
