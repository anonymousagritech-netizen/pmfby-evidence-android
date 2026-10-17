package com.pmfby.evidence.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FarmerEntity::class, SurveyCaseEntity::class, OfflineEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun farmerDao(): FarmerDao
    abstract fun surveyCaseDao(): SurveyCaseDao
    abstract fun offlineEventDao(): OfflineEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pmfby-evidence.db")
                    // This app has never shipped a release build (nothing has been
                    // compiled or installed anywhere), so there is no real device data to
                    // preserve across a schema change yet — destructive fallback instead
                    // of a real Migration for now. Replace this with a proper Migration
                    // before any actual release; see ROADMAP.md.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
