package com.spendtrack.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendtrack.ui.feature.addtransaction.AddTransactionScreen
import com.spendtrack.ui.feature.assets.list.AssetsListScreen
import com.spendtrack.ui.feature.assets.realestate.detail.RealEstateDetailScreen
import com.spendtrack.ui.feature.assets.realestate.edit.AddEditRealEstateScreen
import com.spendtrack.ui.feature.csvimport.ImportScreen
import com.spendtrack.ui.feature.overview.OverviewScreen
import com.spendtrack.ui.feature.persons.PersonsScreen
import com.spendtrack.ui.feature.settings.SettingsScreen
import com.spendtrack.ui.feature.timeline.TimelineScreen

sealed class Screen(val route: String) {
    object Timeline : Screen("timeline")
    object Overview : Screen("overview")
    object Assets : Screen("assets")
    object Settings : Screen("settings")
    object AddTransaction : Screen("add_transaction")
    object EditTransaction : Screen("edit_transaction/{transactionId}") {
        fun createRoute(id: Long) = "edit_transaction/$id"
    }
    object Import : Screen("import")
    object Persons : Screen("persons")
    object RealEstateDetail : Screen("real_estate_detail/{assetId}") {
        fun createRoute(id: Long) = "real_estate_detail/$id"
    }
    object AddRealEstate : Screen("add_real_estate")
    object EditRealEstate : Screen("edit_real_estate/{assetId}") {
        fun createRoute(id: Long) = "edit_real_estate/$id"
    }
}

private val bottomNavItems = listOf(
    Triple(Screen.Timeline, Icons.Default.Home, "Timeline"),
    Triple(Screen.Overview, Icons.Default.BarChart, "Overview"),
    Triple(Screen.Assets, Icons.Default.AccountBalance, "Assets"),
    Triple(Screen.Settings, Icons.Default.MoreHoriz, "More"),
)

private val bottomNavRoutes = bottomNavItems.map { it.first.route }.toSet()

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                AppBottomBar(navController)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Timeline.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Timeline.route) {
                TimelineScreen(navController = navController)
            }
            composable(Screen.Overview.route) {
                OverviewScreen()
            }
            composable(Screen.Assets.route) {
                AssetsListScreen(navController = navController)
            }
            composable(Screen.RealEstateDetail.route) { backStack ->
                RealEstateDetailScreen(navController = navController)
            }
            composable(Screen.AddRealEstate.route) {
                AddEditRealEstateScreen(navController = navController)
            }
            composable(Screen.EditRealEstate.route) {
                AddEditRealEstateScreen(navController = navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(Screen.AddTransaction.route) {
                AddTransactionScreen(navController = navController)
            }
            composable(Screen.EditTransaction.route) { backStack ->
                val id = backStack.arguments?.getString("transactionId")?.toLongOrNull()
                AddTransactionScreen(navController = navController, transactionId = id)
            }
            composable(Screen.Import.route) {
                ImportScreen(navController = navController)
            }
            composable(Screen.Persons.route) {
                PersonsScreen(navController = navController)
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    NavigationBar {
        bottomNavItems.forEach { (screen, icon, label) ->
            val selected = backStackEntry?.destination?.hierarchy
                ?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

