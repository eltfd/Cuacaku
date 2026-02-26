package com.weather.forecast.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weather.forecast.worker.WeatherWorkerScheduler

/**
 * Boot Receiver
 * 
 * Menerima broadcast saat device selesai boot.
 * Digunakan untuk menjadwalkan ulang weather update worker.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // Reschedule weather updates after boot
            WeatherWorkerScheduler.schedulePeriodicWeatherUpdate(context)
        }
    }
}
