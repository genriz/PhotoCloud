package com.app.photocloud.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.app.photocloud.data.model.ItemPhoto

@Database(
    entities = [ItemPhoto::class],
    version = 1,
    exportSchema = true,
    autoMigrations = [
        // Example: AutoMigration (from = 1, to = 2)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inspectionDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "photocloud_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
