package com.crowinnovations.skypulseweather.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM saved_locations ORDER BY isGps DESC, sortOrder ASC, id ASC")
    fun observeLocations(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations ORDER BY isGps DESC, sortOrder ASC, id ASC")
    suspend fun getLocationsOnce(): List<SavedLocationEntity>

    @Query("SELECT * FROM saved_locations WHERE id = :id LIMIT 1")
    suspend fun getLocationById(id: Long): SavedLocationEntity?

    @Query("SELECT * FROM saved_locations WHERE isGps = 1 LIMIT 1")
    suspend fun getGpsLocation(): SavedLocationEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM saved_locations")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(entity: SavedLocationEntity): Long

    @Update
    suspend fun updateLocation(entity: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteLocationById(id: Long)

    @Query("SELECT * FROM cached_weather")
    fun observeAllWeather(): Flow<List<CachedWeatherEntity>>

    @Query("SELECT * FROM cached_weather WHERE locationId = :locationId LIMIT 1")
    suspend fun getWeatherForLocation(locationId: Long): CachedWeatherEntity?

    @Query("SELECT * FROM cached_weather WHERE cacheCellKey = :cellKey ORDER BY fetchedAtMillis DESC LIMIT 1")
    suspend fun getFreshestWeatherForCell(cellKey: String): CachedWeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeather(entity: CachedWeatherEntity)
}
