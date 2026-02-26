package com.weather.forecast.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.weather.forecast.ui.screens.HomeScreen
import com.weather.forecast.ui.screens.SettingsScreen
import com.weather.forecast.ui.viewmodel.WeatherViewModel

/**
 * Navigation Routes
 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

/**
 * Main Navigation Composable
 */
@Composable
fun WeatherNavigation() {
    val navController = rememberNavController()
    val viewModel: WeatherViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
