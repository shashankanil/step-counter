package dev.stepcounter.data.db

import android.content.Context
import androidx.room.*

@Entity(tableName = "daily_steps")
data class DailySteps(@PrimaryKey val date: String, val steps: Long, val syncedAt: Long)

@Dao
interface StepDao {
    @Query("SELECT * FROM daily_steps ORDER BY date") suspend fun all(): List<DailySteps>
    @Upsert suspend fun upsert(rows: List<DailySteps>)
    @Query("DELETE FROM daily_steps") suspend fun clear()
    @Query("DELETE FROM daily_steps WHERE date < :date") suspend fun prune(date: String)
}

@Database(entities = [DailySteps::class], version = 1, exportSchema = true)
abstract class StepDatabase : RoomDatabase() {
    abstract fun steps(): StepDao
    companion object {
        @Volatile private var instance: StepDatabase? = null
        fun get(context: Context): StepDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StepDatabase::class.java, "steps.db")
                .build().also { instance = it }
        }
    }
}
