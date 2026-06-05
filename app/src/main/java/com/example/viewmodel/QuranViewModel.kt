package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.FavoriteSurah
import com.example.data.model.FormattedSurah
import com.example.data.model.LastPlayed
import com.example.data.model.SurahModel
import com.example.data.model.UserSettings
import com.example.data.repository.QuranRepository
import com.example.data.service.QuranPlayerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class QuranViewModel(private val repository: QuranRepository) : ViewModel() {

    // Service bridge
    private val _playerService = MutableStateFlow<QuranPlayerService?>(null)
    val playerService = _playerService.asStateFlow()

    // Surah list loader
    private val _surahListState = MutableStateFlow<UiState<List<SurahModel>>>(UiState.Idle)
    val surahListState: StateFlow<UiState<List<SurahModel>>> = _surahListState.asStateFlow()

    // Current reading surah loader
    private val _readingSurahState = MutableStateFlow<UiState<FormattedSurah>>(UiState.Idle)
    val readingSurahState: StateFlow<UiState<FormattedSurah>> = _readingSurahState.asStateFlow()

    // Search query StateFlow
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Combined filtered Surah list
    val filteredSurahs = combine(
        _surahListState,
        _searchQuery
    ) { state, query ->
        if (state is UiState.Success) {
            if (query.isBlank()) {
                state.data
            } else {
                state.data.filter {
                    it.englishName.contains(query, ignoreCase = true) ||
                    it.name.contains(query) ||
                    it.englishNameTranslation.contains(query, ignoreCase = true)
                }
            }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookmarks direct Flow
    val favoriteSurahs: StateFlow<List<FavoriteSurah>> = repository.getFavoriteSurahs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History (Last played) direct Flow
    val lastPlayed: StateFlow<LastPlayed?> = repository.getLastPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // User settings Flow
    val userSettings: StateFlow<UserSettings> = repository.getUserSettings()
        .combine(MutableStateFlow(UserSettings())) { saved, default ->
            saved ?: default
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    init {
        loadSurahList()
        viewModelScope.launch {
            // Inserts default settings if they don't exist
            repository.getUserSettings().collect { saved ->
                if (saved == null) {
                    repository.saveUserSettings(UserSettings())
                }
            }
        }
    }

    fun setPlayerService(service: QuranPlayerService?) {
        _playerService.value = service
        service?.onAyahChangedListener = { surahNum, index, enName, arName, itemInSurah ->
            saveLastPlayedTrack(surahNum, index, enName, arName, itemInSurah)
        }
    }

    fun loadSurahList() {
        viewModelScope.launch {
            _surahListState.value = UiState.Loading
            try {
                val list = repository.getSurahs()
                _surahListState.value = UiState.Success(list)
            } catch (e: Exception) {
                _surahListState.value = UiState.Error(e.localizedMessage ?: "নেটওয়ার্ক কানেকশন চেক করুন।")
            }
        }
    }

    fun loadSurahReadingView(number: Int) {
        viewModelScope.launch {
            _readingSurahState.value = UiState.Loading
            try {
                val qari = userSettings.value.selectedQari
                val formattedSurah = repository.getSurahEditions(number, qari)
                _readingSurahState.value = UiState.Success(formattedSurah)
            } catch (e: Exception) {
                _readingSurahState.value = UiState.Error(e.localizedMessage ?: "সূরাটি লোড করতে ব্যর্থ হয়েছে")
            }
        }
    }

    fun closeSurahReadingView() {
        _readingSurahState.value = UiState.Idle
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Toggle Bookmarks database triggers
    fun toggleFavoriteSurah(surah: SurahModel) {
        viewModelScope.launch {
            val isFav = favoriteSurahs.value.any { it.surahNumber == surah.number }
            if (isFav) {
                repository.deleteFavorite(surah.number)
            } else {
                repository.insertFavorite(
                    FavoriteSurah(
                        surahNumber = surah.number,
                        englishName = surah.englishName,
                        nameArabic = surah.name,
                        numberOfAyahs = surah.numberOfAyahs,
                        revelationType = surah.revelationType
                    )
                )
            }
        }
    }

    fun toggleFavoriteSurahFromFavorite(favorite: FavoriteSurah) {
        viewModelScope.launch {
            repository.deleteFavorite(favorite.surahNumber)
        }
    }

    // User settings database modifications
    fun updateArabicFontSize(size: Int) {
        viewModelScope.launch {
            val current = userSettings.value
            repository.saveUserSettings(current.copy(arabicFontSize = size))
        }
    }

    fun updateTranslationFontSize(size: Int) {
        viewModelScope.launch {
            val current = userSettings.value
            repository.saveUserSettings(current.copy(translationFontSize = size))
        }
    }

    fun updateTheme(themeName: String) {
        viewModelScope.launch {
            val current = userSettings.value
            repository.saveUserSettings(current.copy(theme = themeName))
        }
    }

    fun updateDailyNotificationEnabled(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            val current = userSettings.value
            val updated = current.copy(dailyNotificationEnabled = enabled)
            repository.saveUserSettings(updated)
            
            val appCtx = context.applicationContext
            if (enabled) {
                com.example.data.receiver.DailyReminderScheduler.scheduleNextDailyReminder(
                    appCtx, updated.notificationHour, updated.notificationMinute
                )
            } else {
                com.example.data.receiver.DailyReminderScheduler.cancelReminder(appCtx)
            }
        }
    }

    fun updateNotificationTime(context: android.content.Context, hour: Int, minute: Int) {
        viewModelScope.launch {
            val current = userSettings.value
            val updated = current.copy(notificationHour = hour, notificationMinute = minute)
            repository.saveUserSettings(updated)
            
            val appCtx = context.applicationContext
            if (updated.dailyNotificationEnabled) {
                com.example.data.receiver.DailyReminderScheduler.scheduleNextDailyReminder(
                    appCtx, hour, minute
                )
            }
        }
    }

    fun toggleOfflineSurah(surahNumber: Int) {
        viewModelScope.launch {
            val currentSetting = userSettings.value
            val list = currentSetting.downloadedSurahsJson
            val newList = if (list.contains(",$surahNumber,")) {
                list.replace(",$surahNumber,", "")
            } else {
                if (list.isEmpty()) ",$surahNumber," else "$list$surahNumber,"
            }
            repository.saveUserSettings(currentSetting.copy(downloadedSurahsJson = newList))
        }
    }

    fun updateQari(qariId: String) {
        viewModelScope.launch {
            val current = userSettings.value
            repository.saveUserSettings(current.copy(selectedQari = qariId))
            // Re-loads active audio sources in real-time if a track is playing
            val service = _playerService.value
            val playingSurah = service?.currentSurah?.value
            if (service != null && playingSurah != null) {
                val wasPlaying = service.isPlaying.value
                val previousIndex = service.currentAyahIndex.value
                try {
                    // Fetch stream configurations under newly selected Qari
                    val updatedSurah = repository.getSurahEditions(playingSurah.number, qariId)
                    if (wasPlaying && previousIndex >= 0) {
                        service.setSurahAndPlay(updatedSurah, previousIndex)
                    } else {
                        service.setLoopMode(service.loopMode.value)
                    }
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }

    private fun saveLastPlayedTrack(surahNumber: Int, ayahIndex: Int, englishName: String, nameArabic: String, numberInSurah: Int) {
        viewModelScope.launch {
            repository.saveLastPlayed(
                LastPlayed(
                    surahNumber = surahNumber,
                    ayahIndex = ayahIndex,
                    surahEnglishName = englishName,
                    surahNameArabic = nameArabic,
                    ayahNumberInSurah = numberInSurah
                )
            )
        }
    }

    // Dynamic Resume
    fun resumeLastPlayed(lastPlayed: LastPlayed) {
        viewModelScope.launch {
            loadSurahReadingView(lastPlayed.surahNumber)
            try {
                // Fetch the Surah editions first
                val qari = userSettings.value.selectedQari
                val data = repository.getSurahEditions(lastPlayed.surahNumber, qari)
                _playerService.value?.setSurahAndPlay(data, lastPlayed.ayahIndex)
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    // Perform actual real background download of Surah text structure and its ayah audios
    fun downloadSurahOffline(
        surahNumber: Int,
        qari: String,
        onProgress: (Float) -> Unit,
        onCompleted: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                onProgress(0.05f)
                val surah = repository.getSurahEditions(surahNumber, qari)
                val ayahs = surah.ayahs
                if (ayahs.isEmpty()) {
                    toggleOfflineSurah(surahNumber)
                    onProgress(1.0f)
                    onCompleted(true)
                    return@launch
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val total = ayahs.size
                    for (i in ayahs.indices) {
                        val url = ayahs[i].audioUrl
                        if (url.isNotBlank()) {
                            repository.downloadAudioFile(url)
                        }
                        val currentProgress = 0.05f + (0.95f * (i + 1).toFloat() / total)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onProgress(currentProgress)
                        }
                    }
                }

                // Register it as downloaded in local settings if not already marked
                val currentSetting = userSettings.value
                if (!currentSetting.downloadedSurahsJson.contains(",$surahNumber,")) {
                    toggleOfflineSurah(surahNumber)
                }
                onCompleted(true)
            } catch (e: Exception) {
                onCompleted(false)
            }
        }
    }

    // Delete cached local files to save disk storage space
    fun deleteDownloadedSurah(surahNumber: Int, qari: String) {
        viewModelScope.launch {
            try {
                val surah = repository.getSurahEditions(surahNumber, qari)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    for (ayah in surah.ayahs) {
                        if (ayah.audioUrl.isNotBlank()) {
                            val file = repository.getCachedAudioFile(repository.context, ayah.audioUrl)
                            if (file != null && file.exists()) {
                                file.delete()
                            }
                        }
                    }
                    val cachedSurahFile = repository.getCachedSurahFile(surahNumber, qari)
                    if (cachedSurahFile.exists()) {
                        cachedSurahFile.delete()
                    }
                }
                // Unregister from settings if currently marked
                val currentSetting = userSettings.value
                if (currentSetting.downloadedSurahsJson.contains(",$surahNumber,")) {
                    toggleOfflineSurah(surahNumber)
                }
            } catch (e: Exception) {
                // Ignored
            }
        }
    }
}

class QuranViewModelFactory(private val repository: QuranRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QuranViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return QuranViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
