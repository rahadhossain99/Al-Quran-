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

class QuranRepository(private val quranDao: QuranDao, val context: android.content.Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val surahAdapter = moshi.adapter(FormattedSurah::class.java)

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

    // Helper to get local cached surah file
    fun getCachedSurahFile(number: Int, qari: String): java.io.File {
        val dir = java.io.File(context.filesDir, "cached_surahs")
        if (!dir.exists()) dir.mkdirs()
        return java.io.File(dir, "surah_${number}_${qari}.json")
    }

    // Helper to get local cached audio file path based on MD5 of URL
    fun getCachedAudioFile(context: android.content.Context, audioUrl: String): java.io.File? {
        if (audioUrl.isBlank()) return null
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(audioUrl.toByteArray())
            val hex = bytes.joinToString("") { "%02x".format(it) }
            val dir = java.io.File(context.filesDir, "cached_audio")
            if (!dir.exists()) dir.mkdirs()
            java.io.File(dir, "$hex.mp3")
        } catch (e: Exception) {
            null
        }
    }

    // Real audio file downloader using OkHttpClient
    suspend fun downloadAudioFile(url: String): Boolean {
        val destinationFile = getCachedAudioFile(context, url) ?: return false
        if (destinationFile.exists() && destinationFile.length() > 0) {
            return true // Already downloaded completed
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body ?: return@withContext false
                    destinationFile.parentFile?.mkdirs()
                    destinationFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                try { destinationFile.delete() } catch (ex: Exception) {}
                false
            }
        }
    }

    // API calls with built-in automatic JSON fallback cache
    suspend fun getSurahs(): List<SurahModel> {
        return quranApi.getSurahs().data
    }

    suspend fun getSurahEditions(number: Int, qari: String): FormattedSurah {
        val cachedFile = getCachedSurahFile(number, qari)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                val json = cachedFile.readText()
                val cachedSurah = surahAdapter.fromJson(json)
                if (cachedSurah != null) {
                    return cachedSurah
                }
            } catch (e: Exception) {
                // Ignore and call network fallback
            }
        }

        // Fetch from network API
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

        val formattedSurah = FormattedSurah(
            number = arabic.number,
            name = arabic.name,
            englishName = arabic.englishName,
            englishNameTranslation = arabic.englishNameTranslation,
            revelationType = arabic.revelationType,
            numberOfAyahs = arabic.numberOfAyahs,
            ayahs = formattedAyahs
        )

        // Cache the newly fetched structure locally for future instant offline access
        try {
            val json = surahAdapter.toJson(formattedSurah)
            cachedFile.writeText(json)
        } catch (e: Exception) {
            // Ignored
        }

        return formattedSurah
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
