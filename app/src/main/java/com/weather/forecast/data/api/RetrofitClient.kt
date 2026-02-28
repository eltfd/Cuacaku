package com.weather.forecast.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit Client Factory
 * 
 * Menyediakan instance Retrofit untuk berbagai API services.
 * Menggunakan singleton pattern untuk efisiensi memory.
 */
object RetrofitClient {

    private const val TIMEOUT_SECONDS = 30L

    /**
     * OkHttp client dengan logging dan timeout configuration
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            // User-Agent required by Nominatim
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Cuacaku/1.9.1")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    /**
     * Weather API Service (Open-Meteo)
     */
    val weatherApi: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WeatherApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    /**
     * Geocoding API Service (Nominatim)
     */
    val geocodingApi: GeocodingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GeocodingApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApiService::class.java)
    }

    /**
     * Air Quality API Service (Open-Meteo Air Quality)
     */
    val airQualityApi: AirQualityApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AirQualityApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityApiService::class.java)
    }

    /**
     * Marine API Service (Open-Meteo Marine)
     */
    val marineApi: MarineApiService by lazy {
        Retrofit.Builder()
            .baseUrl(MarineApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarineApiService::class.java)
    }

    /**
     * Flood API Service (Open-Meteo Flood / GloFAS)
     */
    val floodApi: FloodApiService by lazy {
        Retrofit.Builder()
            .baseUrl(FloodApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FloodApiService::class.java)
    }

    /**
     * ReliefWeb API Service (UN OCHA Disaster Data)
     */
    val reliefWebApi: ReliefWebApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ReliefWebApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ReliefWebApiService::class.java)
    }

    /**
     * Open-Elevation API Service (Terrain Elevation)
     */
    val elevationApi: ElevationApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ElevationApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ElevationApiService::class.java)
    }

    /**
     * NASA EONET API Service (Natural Event Tracker)
     */
    val eonetApi: EonetApiService by lazy {
        Retrofit.Builder()
            .baseUrl(EonetApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EonetApiService::class.java)
    }

    /**
     * USGS Earthquake Hazards API Service
     */
    val earthquakeApi: USGSEarthquakeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(USGSEarthquakeApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(USGSEarthquakeApiService::class.java)
    }

    /**
     * USGS Volcano Alert API Service
     */
    val volcanoApi: USGSVolcanoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(USGSVolcanoApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(USGSVolcanoApiService::class.java)
    }

    /**
     * BMKG API Service (Indonesian Meteorological Agency)
     * Real-time earthquake data specific to Indonesia.
     */
    val bmkgApi: BmkgApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BmkgApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BmkgApiService::class.java)
    }

    /**
     * PetaBencana.id API Service
     * Crowdsourced real-time disaster reports for Indonesia.
     */
    val petaBencanaApi: PetaBencanaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(PetaBencanaApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PetaBencanaApiService::class.java)
    }
}
