package com.crowinnovations.skypulseweather.di

import android.content.Context
import androidx.room.Room
import com.crowinnovations.skypulseweather.data.local.WeatherDao
import com.crowinnovations.skypulseweather.data.local.WeatherDatabase
import com.crowinnovations.skypulseweather.data.remote.GeocodingApi
import com.crowinnovations.skypulseweather.data.remote.NwsApi
import com.crowinnovations.skypulseweather.data.remote.OpenMeteoApi
import com.crowinnovations.skypulseweather.data.remote.ZippopotamApi
import com.crowinnovations.skypulseweather.domain.InputSecurityValidator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val modernTlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .build()

        return OkHttpClient.Builder()
            .connectionSpecs(listOf(modernTlsSpec))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                if (!url.isHttps || !InputSecurityValidator.isSafePublicHost(url.host)) {
                    throw IOException("Blocked insecure or non-public network target.")
                }
                val req = original.newBuilder()
                    .header("User-Agent", "SkyPulseWeather/1.0 (+https://crowinnovations.com)")
                    .header("Accept", "application/json, application/geo+json")
                    .build()
                chain.proceed(req)
            }
            .addNetworkInterceptor { chain ->
                val hopUrl = chain.request().url
                if (!hopUrl.isHttps || !InputSecurityValidator.isSafePublicHost(hopUrl.host)) {
                    throw IOException("Blocked redirect hop to insecure or non-public host.")
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenMeteoApi(okHttpClient: OkHttpClient, json: Json): OpenMeteoApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeocodingApi(okHttpClient: OkHttpClient, json: Json): GeocodingApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GeocodingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideZippopotamApi(okHttpClient: OkHttpClient, json: Json): ZippopotamApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.zippopotam.us/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ZippopotamApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNwsApi(okHttpClient: OkHttpClient, json: Json): NwsApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.weather.gov/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NwsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeatherDatabase {
        return Room.databaseBuilder(
            context,
            WeatherDatabase::class.java,
            "skypulse_weather.db"
        )
            .addMigrations(WeatherDatabase.MIGRATION_1_2, WeatherDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideWeatherDao(db: WeatherDatabase): WeatherDao = db.weatherDao()

    @Provides
    @Singleton
    fun provideFusedLocationClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
}
