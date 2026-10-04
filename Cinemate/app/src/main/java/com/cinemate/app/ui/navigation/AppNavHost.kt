package com.cinemate.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cinemate.app.R
import com.cinemate.app.notifications.InAppBanner
import com.cinemate.app.ui.catalog.CatalogScreen
import com.cinemate.app.ui.details.DetailsScreen
import com.cinemate.app.ui.favorites.FavoritesScreen
import com.cinemate.app.ui.person.PersonScreen
import com.cinemate.app.ui.search.SearchScreen
import com.cinemate.app.ui.settings.SettingsScreen
import com.cinemate.app.ui.upcoming.UpcomingScreen
import com.cinemate.app.ui.watched.WatchedScreen

private data class BottomTab(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int
)

private val bottomTabs = listOf(
    BottomTab("catalog", Icons.Filled.Home, R.string.nav_catalog),
    BottomTab("search", Icons.Filled.Search, R.string.nav_search),
    BottomTab("upcoming", Icons.Filled.Schedule, R.string.nav_upcoming),
    BottomTab("favorites", Icons.Filled.Favorite, R.string.nav_favorites),
    BottomTab("settings", Icons.Filled.Settings, R.string.nav_settings)
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val openDetails: (String, Int) -> Unit = { type, id ->
        navController.navigate("details/$type/$id")
    }

    val openPerson: (Int) -> Unit = { id ->
        navController.navigate("person/$id")
    }

    val openWatched: () -> Unit = {
        navController.navigate("watched")
    }

    Scaffold(
        bottomBar = {
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (bottomTabs.any { it.route == currentRoute }) {
                InAppBanner()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = "catalog",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("catalog") {
                        CatalogScreen(onOpenDetails = openDetails)
                    }
                    composable(
                        route = "details/{type}/{id}",
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType },
                            navArgument("id") { type = NavType.IntType }
                        )
                    ) {
                        DetailsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDetails = openDetails,
                            onOpenPerson = openPerson
                        )
                    }
                    composable(
                        route = "person/{id}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.IntType }
                        )
                    ) {
                        PersonScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDetails = openDetails
                        )
                    }
                    composable("search") {
                        SearchScreen(onOpenDetails = openDetails)
                    }
                    composable("upcoming") {
                        UpcomingScreen(onOpenDetails = openDetails)
                    }
                    composable("favorites") {
                        FavoritesScreen(onOpenDetails = openDetails)
                    }
                    composable("settings") {
                        SettingsScreen(onOpenWatched = openWatched)
                    }
                    composable("watched") {
                        WatchedScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}