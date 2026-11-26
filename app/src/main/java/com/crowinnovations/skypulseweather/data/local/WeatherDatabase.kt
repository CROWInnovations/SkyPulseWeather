package com.crowinnovations.skypulseweather.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SavedLocationEntity::class, CachedWeatherEntity::class],
    version = 3,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cached_weather ADD COLUMN cloudCoverPercent INTEGER NOT NULL DEFAULT -1"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE cached_weather ADD COLUMN uvIndex REAL NOT NULL DEFAULT -1.0"
                )
            }
        }
    }
}
