package com.weather.forecast.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.weather.forecast.data.locale.AppLocaleManager
import com.weather.forecast.data.locale.AppStrings
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.ui.screens.AirQualityScreen
import com.weather.forecast.ui.screens.DisasterForecastScreen
import com.weather.forecast.ui.screens.DisasterMonitorScreen
import com.weather.forecast.ui.screens.HomeScreen
import com.weather.forecast.ui.screens.SeismicMonitorScreen
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
    const val SEISMIC_MONITOR = "seismic_monitor"
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
        route = Routes.SEISMIC_MONITOR,
        title = "Seismik",
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar
    )
}

/**
 * Main Navigation Composable with Bottom Navigation Bar
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherNavigation() {
    val navController = rememberNavController()
    val weatherViewModel: WeatherViewModel = viewModel()
    val environmentViewModel: EnvironmentViewModel = viewModel()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Collect locale for reactive UI updates
    val locale by AppLocaleManager.localeFlow.collectAsState()
    val strings = AppStrings.get(locale)

    // Location permission check for accuracy warning banner
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    val hasAnyLocationPermission = locationPermissions.permissions.any { it.status.isGranted }
    var dismissedLocationWarning by rememberSaveable { mutableStateOf(false) }

    // Only show bottom bar on main screens (not settings)
    val showBottomBar = currentDestination?.route in listOf(
        Routes.HOME, Routes.AIR_QUALITY, Routes.WATER_QUALITY,
        Routes.DISASTER, Routes.DISASTER_MONITOR, Routes.SEISMIC_MONITOR
    )

    // Show warning banner on main screens when location permission is not granted
    val showLocationWarning = !hasAnyLocationPermission && !dismissedLocationWarning && showBottomBar

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
                                BottomNavItem.MONITOR -> strings.navSeismic
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
            Column(modifier = Modifier.padding(innerPadding)) {
                // Location accuracy warning banner
                AnimatedVisibility(
                    visible = showLocationWarning,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    LocationWarningBanner(
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        onDismiss = { dismissedLocationWarning = true }
                    )
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.weight(1f)
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

                composable(Routes.SEISMIC_MONITOR) {
                    SeismicMonitorScreen(
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
}

/**
 * Location accuracy warning banner.
 * Shown when user hasn't granted location permission.
 */
@Composable
private fun LocationWarningBanner(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.locationAccuracyWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Row {
                    TextButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.openSettings,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
