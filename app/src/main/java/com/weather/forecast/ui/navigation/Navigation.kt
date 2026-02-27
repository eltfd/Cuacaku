package com.weather.forecast.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.locale.AppStrings
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.ui.screens.AirQualityScreen
import com.weather.forecast.ui.screens.DisasterForecastScreen
import com.weather.forecast.ui.screens.DisasterMonitorScreen
import com.weather.forecast.ui.screens.HomeScreen
import com.weather.forecast.ui.screens.SettingsScreen
import com.weather.forecast.ui.screens.WaterQualityScreen
import com.weather.forecast.ui.viewmodel.EnvironmentViewModel
import com.weather.forecast.ui.viewmodel.WeatherViewModel

/**
 * Navigation Routes
 */
object Routes {
    const val HOME = "home"
    const val AIR_QUALITY = "air_quality"
    const val WATER_QUALITY = "water_quality"
    const val DISASTER = "disaster"
    const val DISASTER_MONITOR = "disaster_monitor"
    const val SETTINGS = "settings"
}

/**
 * Bottom Navigation Items
 */
enum class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    WEATHER(
        route = Routes.HOME,
        title = "Cuaca",
        selectedIcon = Icons.Filled.Cloud,
        unselectedIcon = Icons.Outlined.Cloud
    ),
    AIR_QUALITY(
        route = Routes.AIR_QUALITY,
        title = "Udara",
        selectedIcon = Icons.Filled.Air,
        unselectedIcon = Icons.Outlined.Air
    ),
    WATER_QUALITY(
        route = Routes.WATER_QUALITY,
        title = "Air",
        selectedIcon = Icons.Filled.Water,
        unselectedIcon = Icons.Outlined.Water
    ),
    DISASTER(
        route = Routes.DISASTER,
        title = "Bencana",
        selectedIcon = Icons.Filled.Warning,
        unselectedIcon = Icons.Outlined.Warning
    ),
    MONITOR(
        route = Routes.DISASTER_MONITOR,
        title = "Pantau",
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar
    )
}

/**
 * Main Navigation Composable with Bottom Navigation Bar
 */
@Composable
fun WeatherNavigation() {
    val navController = rememberNavController()
    val weatherViewModel: WeatherViewModel = viewModel()
    val environmentViewModel: EnvironmentViewModel = viewModel()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Collect locale for reactive UI updates
    val locale by AppLocaleManager.localeFlow.collectAsState()
    val strings = AppStrings.get(locale)

    // Only show bottom bar on main screens (not settings)
    val showBottomBar = currentDestination?.route in listOf(
        Routes.HOME, Routes.AIR_QUALITY, Routes.WATER_QUALITY,
        Routes.DISASTER, Routes.DISASTER_MONITOR
    )

    CompositionLocalProvider(LocalStrings provides strings) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        BottomNavItem.entries.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true

                            val localizedTitle = when (item) {
                                BottomNavItem.WEATHER -> strings.navWeather
                                BottomNavItem.AIR_QUALITY -> strings.navAir
                                BottomNavItem.WATER_QUALITY -> strings.navWater
                                BottomNavItem.DISASTER -> strings.navDisaster
                                BottomNavItem.MONITOR -> strings.navMonitor
                            }

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = localizedTitle
                                    )
                                },
                                label = { Text(localizedTitle) },
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
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
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = weatherViewModel,
                        onNavigateToSettings = {
                            navController.navigate(Routes.SETTINGS)
                        }
                    )
                }

                composable(Routes.AIR_QUALITY) {
                    AirQualityScreen(
                        viewModel = environmentViewModel
                    )
                }

                composable(Routes.WATER_QUALITY) {
                    WaterQualityScreen(
                        viewModel = environmentViewModel
                    )
                }

                composable(Routes.DISASTER) {
                    DisasterForecastScreen(
                        viewModel = environmentViewModel
                    )
                }

                composable(Routes.DISASTER_MONITOR) {
                    DisasterMonitorScreen(
                        viewModel = environmentViewModel
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = weatherViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
