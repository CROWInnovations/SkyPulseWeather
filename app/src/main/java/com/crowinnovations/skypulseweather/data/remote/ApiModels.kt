package com.crowinnovations.skypulseweather.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "UTC",
    val current: OpenMeteoCurrentDto? = null,
    val hourly: OpenMeteoHourlyDto? = null,
    val daily: OpenMeteoDailyDto? = null
)

@Serializable
data class OpenMeteoCurrentDto(
    val time: String = "",
    @SerialName("temperature_2m") val temperature2m: Double = 0.0,
    @SerialName("apparent_temperature") val apparentTemperature: Double = 0.0,
    @SerialName("is_day") val isDay: Int = 1,
    val precipitation: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int = 0,
    @SerialName("cloud_cover") val cloudCover: Int = -1,
    @SerialName("wind_speed_10m") val windSpeed10m: Double = -1.0,
    @SerialName("relative_humidity_2m") val relativeHumidity2m: Int = 0,
    @SerialName("uv_index") val uvIndex: Double = -1.0
)

@Serializable
data class OpenMeteoHourlyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature2m: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList()
)

@Serializable
data class OpenMeteoDailyDto(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList()
)

@Serializable
data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoGeoResultDto>? = null
)

@Serializable
data class OpenMeteoGeoResultDto(
    val id: Long = 0L,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val postcodes: List<String>? = null
)

@Serializable
data class ZippopotamResponse(
    @SerialName("post code") val postCode: String = "",
    val country: String = "",
    @SerialName("country abbreviation") val countryAbbreviation: String = "US",
    val places: List<ZippopotamPlaceDto> = emptyList()
)

@Serializable
data class ZippopotamPlaceDto(
    @SerialName("place name") val placeName: String = "",
    val longitude: String = "0.0",
    val state: String = "",
    @SerialName("state abbreviation") val stateAbbreviation: String = "",
    val latitude: String = "0.0"
)

@Serializable
data class NwsAlertsResponse(
    val features: List<NwsAlertFeatureDto> = emptyList()
)

@Serializable
data class NwsAlertFeatureDto(
    val id: String = "",
    val properties: NwsAlertPropertiesDto? = null
)

@Serializable
data class NwsAlertPropertiesDto(
    val id: String? = null,
    val event: String? = null,
    val severity: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val expires: String? = null
)

@Serializable
data class NwsPointsResponse(
    val properties: NwsPointsPropertiesDto? = null
)

@Serializable
data class NwsPointsPropertiesDto(
    val relativeLocation: NwsRelativeLocationDto? = null
)

@Serializable
data class NwsRelativeLocationDto(
    val properties: NwsRelativeLocationPropsDto? = null
)

@Serializable
data class NwsRelativeLocationPropsDto(
    val city: String? = null,
    val state: String? = null
)
