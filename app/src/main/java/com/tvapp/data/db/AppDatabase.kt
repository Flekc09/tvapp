package com.tvapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CountryEntity::class, CategoryEntity::class, ChannelEntity::class, StreamEntity::class,
        FavoriteEntity::class, RecentEntity::class, StreamStatEntity::class,
        StreamFailureEntity::class, ChannelStatusEntity::class, SettingEntity::class],
    version = 1, exportSchema = true, // schema JSON in app/schemas, committed (Opus adversarial review 2026-09-23, major 17)
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun local(): LocalDao

    companion object {
        // Never fallbackToDestructiveMigration: favorites, recents and the PIN live here. A schema change bumps `version`,
        // adds its Migration here and a MigrationTestHelper test against app/schemas (RELEASE.md, "Updating a TV").
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tvapp.db").build()
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
