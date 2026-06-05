package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.FavoriteSurah
import com.example.data.model.LastPlayed
import com.example.data.model.UserSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface QuranDao {
    @Query("SELECT * FROM favorite_surahs ORDER BY timestamp DESC")
    fun getFavoriteSurahs(): Flow<List<FavoriteSurah>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favoriteSurah: FavoriteSurah)

    @Query("DELETE FROM favorite_surahs WHERE surahNumber = :surahNumber")
    suspend fun deleteFavorite(surahNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_surahs WHERE surahNumber = :surahNumber)")
    fun isFavorite(surahNumber: Int): Flow<Boolean>

    @Query("SELECT * FROM last_played WHERE id = 1")
    fun getLastPlayed(): Flow<LastPlayed?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLastPlayed(lastPlayed: LastPlayed)

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getUserSettings(): Flow<UserSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserSettings(settings: UserSettings)
}

@Database(
    entities = [FavoriteSurah::class, LastPlayed::class, UserSettings::class],
    version = 2,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun quranDao(): QuranDao

    companion object {
        @Volatile
        private var INSTANCE: QuranDatabase? = null

        fun getDatabase(context: Context): QuranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuranDatabase::class.java,
                    "quran_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
