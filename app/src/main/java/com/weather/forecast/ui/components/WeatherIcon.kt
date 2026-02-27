package com.weather.forecast.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.weather.forecast.data.model.WeatherCondition
import com.weather.forecast.data.locale.LocalStrings
import com.weather.forecast.ui.theme.*

/**
 * Weather Icon Component
 * 
 * Menampilkan icon cuaca berdasarkan weather code dan waktu (siang/malam)
 */
@Composable
fun WeatherIcon(
    weatherCode: Int,
    isDay: Boolean,
    modifier: Modifier = Modifier.size(48.dp)
) {
    val condition = WeatherCondition.fromCode(weatherCode)
    
    val (icon, tint) = when (condition) {
        WeatherCondition.CLEAR -> {
            if (isDay) Pair(Icons.Filled.WbSunny, Sunny)
            else Pair(Icons.Filled.NightsStay, Color(0xFF5C6BC0))
        }
        WeatherCondition.MAINLY_CLEAR -> {
            if (isDay) Pair(Icons.Outlined.WbSunny, Sunny)
            else Pair(Icons.Filled.NightsStay, Color(0xFF5C6BC0))
        }
        WeatherCondition.PARTLY_CLOUDY -> {
            if (isDay) Pair(Icons.Filled.WbCloudy, Color(0xFF90A4AE))
            else Pair(Icons.Filled.Cloud, Color(0xFF78909C))
        }
        WeatherCondition.OVERCAST -> Pair(Icons.Filled.Cloud, Cloudy)
        WeatherCondition.FOG -> Pair(Icons.Filled.Cloud, Color(0xFF90A4AE))
        WeatherCondition.DRIZZLE_LIGHT,
        WeatherCondition.DRIZZLE_MODERATE,
        WeatherCondition.DRIZZLE_DENSE,
        WeatherCondition.FREEZING_DRIZZLE -> Pair(Icons.Filled.Grain, Rainy)
        WeatherCondition.RAIN_SLIGHT,
        WeatherCondition.RAIN_MODERATE -> Pair(Icons.Filled.WaterDrop, Rainy)
        WeatherCondition.RAIN_HEAVY,
        WeatherCondition.FREEZING_RAIN,
        WeatherCondition.RAIN_SHOWERS_SLIGHT,
        WeatherCondition.RAIN_SHOWERS_MODERATE,
        WeatherCondition.RAIN_SHOWERS_VIOLENT -> Pair(Icons.Filled.Thunderstorm, Color(0xFF1976D2))
        WeatherCondition.SNOW_SLIGHT,
        WeatherCondition.SNOW_MODERATE,
        WeatherCondition.SNOW_HEAVY,
        WeatherCondition.SNOW_SHOWERS -> Pair(Icons.Filled.AcUnit, Snowy)
        WeatherCondition.THUNDERSTORM,
        WeatherCondition.THUNDERSTORM_HAIL -> Pair(Icons.Filled.Thunderstorm, Stormy)
    }

    Icon(
        imageVector = icon,
        contentDescription = LocalStrings.current.localized(condition.description, condition.descriptionId),
        modifier = modifier,
        tint = tint
    )
}
