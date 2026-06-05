package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.SyncProject
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncProjectDao {
    @Query("SELECT * FROM sync_projects ORDER BY createdAt DESC")
    fun getAllProjectsFlow(): Flow<List<SyncProject>>

    @Query("SELECT * FROM sync_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): SyncProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: SyncProject): Long

    @Update
    suspend fun updateProject(project: SyncProject)

    @Delete
    suspend fun deleteProject(project: SyncProject)

    @Query("DELETE FROM sync_projects")
    suspend fun clearAll()
}

@Database(entities = [SyncProject::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncProjectDao(): SyncProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "docusync_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
