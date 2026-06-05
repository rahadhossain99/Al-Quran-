package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.model.FormattedSurah
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QuranPlayerService : Service() {

    inner class PlayerBinder : Binder() {
        fun getService(): QuranPlayerService = this@QuranPlayerService
    }

    private val binder = PlayerBinder()

    // MediaPlayer and locks
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Reactive states
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentSurah = MutableStateFlow<FormattedSurah?>(null)
    val currentSurah: StateFlow<FormattedSurah?> = _currentSurah

    private val _currentAyahIndex = MutableStateFlow(-1)
    val currentAyahIndex: StateFlow<Int> = _currentAyahIndex

    private val _progress = MutableStateFlow(0) // position in ms
    val progress: StateFlow<Int> = _progress

    private val _duration = MutableStateFlow(0) // duration in ms
    val duration: StateFlow<Int> = _duration

    private val _loopMode = MutableStateFlow(0) // 0 = off, 1 = loop surah, 2 = loop ayah
    val loopMode: StateFlow<Int> = _loopMode

    private val _isError = MutableStateFlow<String?>(null)
    val isError: StateFlow<String?> = _isError

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    // Callbacks for save tracking
    var onAyahChangedListener: ((surahNumber: Int, index: Int, englishName: String, nameArabic: String, numberInSurah: Int) -> Unit)? = null

    companion object {
        const val NOTIFICATION_ID = 2026
        const val CHANNEL_ID = "quran_playback_channel"

        const val ACTION_PLAY_PAUSE = "com.example.alquran.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.alquran.NEXT"
        const val ACTION_PREV = "com.example.alquran.PREV"
        const val ACTION_CLOSE = "com.example.alquran.CLOSE"
    }

    override fun onCreate() {
        super.onCreate()
        initLocks()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrev()
            ACTION_CLOSE -> stopPlaybackAndService()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
        releaseLocks()
        progressJob?.cancel()
    }

    private fun initLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlQuranApp::PlaybackWakeLock")

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AlQuranApp::WifiLock")
        } else {
            @Suppress("DEPRECATION")
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "AlQuranApp::WifiLock")
        }
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld == false) wakeLock?.acquire(10 * 60 * 1000L /*10 mins max*/)
        if (wifiLock?.isHeld == false) wifiLock?.acquire()
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
    }

    fun setLoopMode(mode: Int) {
        _loopMode.value = mode
    }

    fun setSurahAndPlay(surah: FormattedSurah, startIndex: Int = 0) {
        _currentSurah.value = surah
        playAyah(startIndex)
    }

    fun playAyah(index: Int) {
        val surah = _currentSurah.value ?: return
        if (index < 0 || index >= surah.ayahs.size) return

        _currentAyahIndex.value = index
        _isError.value = null

        val ayah = surah.ayahs[index]
        val audioUrl = ayah.audioUrl.trim()

        if (audioUrl.isEmpty()) {
            _isError.value = "অডিও লিঙ্কটি খালি"
            playNext()
            return
        }

        releaseMediaPlayer()
        startForegroundServiceNotification()

        try {
            // Secure connection mapping
            val secureUrl = if (audioUrl.startsWith("http://")) {
                audioUrl.replace("http://", "https://")
            } else {
                audioUrl
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(secureUrl)
                setOnPreparedListener { mp ->
                    acquireLocks()
                    mp.start()
                    _isPlaying.value = true
                    _duration.value = mp.duration
                    startProgressTracker()
                    startForegroundServiceNotification()

                    // Callback trigger
                    onAyahChangedListener?.invoke(
                        surah.number,
                        index,
                        surah.englishName,
                        surah.name,
                        ayah.numberInSurah
                    )
                }
                setOnCompletionListener {
                    handleTrackCompletion()
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    _isError.value = "প্লেব্যাক ত্রুটি"
                    releaseLocks()
                    false
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _isPlaying.value = false
            _isError.value = "মিডিয়া প্লেয়ার তৈরি করতে ব্যর্থ: ${e.localizedMessage}"
            releaseLocks()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        if (player != null) {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                releaseLocks()
                progressJob?.cancel()
                startForegroundServiceNotification()
            } else {
                acquireLocks()
                player.start()
                _isPlaying.value = true
                startProgressTracker()
                startForegroundServiceNotification()
            }
        } else {
            // If we have a surah loaded, play the current index (or 0)
            val index = if (_currentAyahIndex.value >= 0) _currentAyahIndex.value else 0
            if (_currentSurah.value != null) {
                playAyah(index)
            }
        }
    }

    fun playNext() {
        val surah = _currentSurah.value ?: return
        val nextIndex = _currentAyahIndex.value + 1
        if (nextIndex < surah.ayahs.size) {
            playAyah(nextIndex)
        } else {
            if (_loopMode.value == 1) {
                playAyah(0)
            } else {
                stopPlayback()
            }
        }
    }

    fun playPrev() {
        val nextIndex = _currentAyahIndex.value - 1
        if (nextIndex >= 0) {
            playAyah(nextIndex)
        }
    }

    fun seekTo(progressMs: Int) {
        mediaPlayer?.let { player ->
            if (player.isPlaying || _isPlaying.value) {
                player.seekTo(progressMs)
                _progress.value = progressMs
            }
        }
    }

    private fun handleTrackCompletion() {
        val currentMode = _loopMode.value
        val surah = _currentSurah.value ?: return
        val currentIndex = _currentAyahIndex.value

        when (currentMode) {
            2 -> { // Ayah loop
                playAyah(currentIndex)
            }
            else -> {
                val nextIndex = currentIndex + 1
                if (nextIndex < surah.ayahs.size) {
                    playAyah(nextIndex)
                } else {
                    if (currentMode == 1) { // Surah loop
                        playAyah(0)
                    } else {
                        stopPlayback()
                    }
                }
            }
        }
    }

    private fun stopPlayback() {
        releaseMediaPlayer()
        releaseLocks()
        _isPlaying.value = false
        _currentAyahIndex.value = -1
        _progress.value = 0
        _duration.value = 0
        progressJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopPlaybackAndService() {
        stopPlayback()
        stopSelf()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (e: Exception) {
                // Ignored
            }
            player.release()
        }
        mediaPlayer = null
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isPlaying.value) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _progress.value = player.currentPosition
                        }
                    } catch (e: Exception) {
                        // Ignored
                    }
                }
                delay(250)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Quran Recitation"
            val descriptionText = "Al-Quran background audio synchronization controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getPremiumLargeIcon(): Bitmap? {
        try {
            val drawable = ContextCompat.getDrawable(this, com.example.R.drawable.ic_launcher_foreground) ?: return null
            val size = 192
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Optional: Draw a nice background circle first
            val paint = android.graphics.Paint().apply {
                color = 0xFF0B562F.toInt() // Deep emerald green background circle
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            // Now draw the launcher foreground centered on top
            drawable.setBounds(size / 8, size / 8, size - size / 8, size - size / 8)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    private fun startForegroundServiceNotification() {
        val surah = _currentSurah.value ?: return
        val currentAyahNum = if (_currentAyahIndex.value >= 0) {
            surah.ayahs[_currentAyahIndex.value].numberInSurah
        } else {
            1
        }

        // Custom PendingIntent triggers main activity
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Action intents - Explicitly invoke play/pause/etc on this service class via service launch!
        val playPauseIntent = Intent(this, QuranPlayerService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePI = PendingIntent.getService(this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, QuranPlayerService::class.java).apply { action = ACTION_NEXT }
        val nextPI = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val prevIntent = Intent(this, QuranPlayerService::class.java).apply { action = ACTION_PREV }
        val prevPI = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val closeIntent = Intent(this, QuranPlayerService::class.java).apply { action = ACTION_CLOSE }
        val closePI = PendingIntent.getService(this, 4, closeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playIcon = if (isPlaying.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playActionTitle = if (isPlaying.value) "Pause" else "Play"

        val premiumLargeIcon = getPremiumLargeIcon()

        // Build notification
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_quran_notification) // Beautiful, custom Al-Quran monochromatic icon
            .setContentTitle(surah.englishName)
            .setContentText("আয়াহ $currentAyahNum • ${surah.name}")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying.value)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPI)
            .addAction(playIcon, playActionTitle, playPausePI)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", closePI)
            .setColorized(true)
            .setColor(0xFF0B562F.toInt()) // Vibrant deep green matching Islam style

        if (premiumLargeIcon != null) {
            builder.setLargeIcon(premiumLargeIcon)
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
