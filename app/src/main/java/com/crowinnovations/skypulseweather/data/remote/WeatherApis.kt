package com.crowinnovations.skypulseweather.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface OpenMeteoApi {
    @GET
    suspend fun getForecast(
        @Url url: String,
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,wind_speed_10m,relative_humidity_2m,uv_index",
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,weather_code,is_day",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "mph",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 10
    ): OpenMeteoForecastResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun searchLocations(
        @Query("name") query: String,
        @Query("count") count: Int = 8,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): OpenMeteoGeocodingResponse
}

interface ZippopotamApi {
    @GET("us/{zipCode}")
    suspend fun lookupUsZip(
        @Path("zipCode") zipCode: String
    ): ZippopotamResponse
}

interface NwsApi {
    @GET("alerts/active")
    suspend fun getActiveAlerts(
        @Query("point") latLonPoint: String
    ): NwsAlertsResponse

    @GET("points/{lat},{lon}")
    suspend fun getPointMetadata(
        @Path("lat") lat: String,
        @Path("lon") lon: String
    ): NwsPointsResponse
}
