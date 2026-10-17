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
                    .build()
                    .also { instance = it }
            }
    }
}
