package com.example.data.repository

import com.example.data.api.QuranApi
import com.example.data.db.QuranDao
import com.example.data.model.FavoriteSurah
import com.example.data.model.FormattedAyah
import com.example.data.model.FormattedSurah
import com.example.data.model.LastPlayed
import com.example.data.model.SurahModel
import com.example.data.model.UserSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class QuranRepository(private val quranDao: QuranDao) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val quranApi: QuranApi = Retrofit.Builder()
        .baseUrl("https://api.alquran.cloud/v1/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(okHttpClient)
        .build()
        .create(QuranApi::class.java)

    // API calls
    suspend fun getSurahs(): List<SurahModel> {
        return quranApi.getSurahs().data
    }

    suspend fun getSurahEditions(number: Int, qari: String): FormattedSurah {
        val response = quranApi.getSurahEditions(number, qari)
        val data = response.data
        if (data.size < 4) {
            throw IllegalStateException("API returned incomplete editions for Surah $number")
        }
        val arabic = data[0]
        val bengali = data[1]
        val translit = data[2]
        val audio = data[3]

        val formattedAyahs = arabic.ayahs.mapIndexed { index, arabicAyah ->
            FormattedAyah(
                numberInSurah = arabicAyah.numberInSurah,
                arabicText = arabicAyah.text,
                bengaliText = if (index < bengali.ayahs.size) bengali.ayahs[index].text else "",
                transliterationText = if (index < translit.ayahs.size) translit.ayahs[index].text else "",
                audioUrl = if (index < audio.ayahs.size) audio.ayahs[index].audio ?: "" else ""
            )
        }

        return FormattedSurah(
            number = arabic.number,
            name = arabic.name,
            englishName = arabic.englishName,
            englishNameTranslation = arabic.englishNameTranslation,
            revelationType = arabic.revelationType,
            numberOfAyahs = arabic.numberOfAyahs,
            ayahs = formattedAyahs
        )
    }

    // Room operations
    fun getFavoriteSurahs(): Flow<List<FavoriteSurah>> = quranDao.getFavoriteSurahs()

    suspend fun insertFavorite(favorite: FavoriteSurah) = quranDao.insertFavorite(favorite)

    suspend fun deleteFavorite(surahNumber: Int) = quranDao.deleteFavorite(surahNumber)

    fun isFavorite(surahNumber: Int): Flow<Boolean> = quranDao.isFavorite(surahNumber)

    fun getLastPlayed(): Flow<LastPlayed?> = quranDao.getLastPlayed()

    suspend fun saveLastPlayed(lastPlayed: LastPlayed) = quranDao.saveLastPlayed(lastPlayed)

    fun getUserSettings(): Flow<UserSettings?> = quranDao.getUserSettings()

    suspend fun saveUserSettings(settings: UserSettings) = quranDao.saveUserSettings(settings)
}
