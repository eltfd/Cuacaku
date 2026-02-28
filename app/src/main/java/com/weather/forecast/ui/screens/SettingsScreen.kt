package com.weather.forecast.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weather.forecast.BuildConfig
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.ui.viewmodel.WeatherViewModel

/**
 * Settings Screen - Notification and preferences settings
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    val preferences by viewModel.userPreferences.collectAsState()
    val s = LocalStrings.current

    // Notification permission for Android 13+
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = s.back)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Notification Settings Section
            SettingsSection(title = s.notifications) {
                // Master notification toggle
                SwitchSettingItem(
                    title = s.enableNotifications,
                    subtitle = s.enableNotificationsDesc,
                    checked = preferences.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && notificationPermission != null && !notificationPermission.status.isGranted) {
                            notificationPermission.launchPermissionRequest()
                        }
                        viewModel.updateNotificationSettings(enabled = enabled)
                    }
                )

                if (preferences.notificationsEnabled) {
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Daily forecast notification
                    SwitchSettingItem(
                        title = s.dailyForecast,
                        subtitle = s.dailyForecastDesc,
                        checked = preferences.dailyNotificationEnabled,
                        onCheckedChange = { 
                            viewModel.updateNotificationSettings(dailyEnabled = it)
                        }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Severe weather alert
                    SwitchSettingItem(
                        title = s.severeWeatherAlert,
                        subtitle = s.severeWeatherAlertDesc,
                        checked = preferences.severeWeatherAlert,
                        onCheckedChange = {
                            viewModel.updateNotificationSettings(severeWeatherAlert = it)
                        }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Rain alert
                    SwitchSettingItem(
                        title = s.rainAlert,
                        subtitle = s.rainAlertDesc,
                        checked = preferences.rainAlert,
                        onCheckedChange = {
                            viewModel.updateNotificationSettings(rainAlert = it)
                        }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Temperature alert
                    SwitchSettingItem(
                        title = s.extremeTempAlert,
                        subtitle = s.extremeTempAlertDesc,
                        checked = preferences.temperatureAlert,
                        onCheckedChange = {
                            viewModel.updateNotificationSettings(temperatureAlert = it)
                        }
                    )

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Disaster AI alert
                    SwitchSettingItem(
                        title = s.disasterAlertSetting,
                        subtitle = s.disasterAlertSettingDesc,
                        checked = preferences.disasterAlert,
                        onCheckedChange = {
                            viewModel.updateNotificationSettings(disasterAlert = it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About Section
            SettingsSection(title = s.about) {
                InfoItem(
                    title = s.dataSource,
                    subtitle = s.openMeteoDesc
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                InfoItem(
                    title = s.geocoding,
                    subtitle = s.nominatimDesc
                )
                
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
                
                InfoItem(
                    title = s.appVersion,
                    subtitle = BuildConfig.VERSION_NAME
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun InfoItem(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
