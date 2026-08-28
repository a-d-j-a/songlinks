package com.songlinks.app.player

import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.core.app.NotificationCompat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.Builder
import androidx.annotation.OptIn
import com.songlinks.app.MainActivity
import com.songlinks.app.api.SongResult
import com.songlinks.app.data.local.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "PlayerService"

class PlayerService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.songlinks.app.action.PLAY"
        const val ACTION_PAUSE = "com.songlinks.app.action.PAUSE"
        const val ACTION_NEXT = "com.songlinks.app.action.NEXT"
        const val ACTION_PREVIOUS = "com.songlinks.app.action.PREVIOUS"
    }

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    private val binder = PlayerBinder()
    private var exoPlayer: ExoPlayer? = null
    private var positionPollingJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndTime: Long = 0L
    private var equalizer: Equalizer? = null
    private var mediaSession: MediaSession? = null

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    private var wasPlayingBeforeTransientLoss = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeTransientLoss = PlayerState.isPlaying.value
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                exoPlayer?.volume = 0.3f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                exoPlayer?.volume = 1.0f
                if (wasPlayingBeforeTransientLoss) {
                    resume()
                    wasPlayingBeforeTransientLoss = false
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val pos = exoPlayer?.currentPosition ?: 0L
            val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
            PlayerState.updatePosition(pos)
            PlayerState.updateDuration(dur)
            if (isPlaying) {
                startPositionPolling()
            } else {
                stopPositionPolling()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
                    PlayerState.updateDuration(dur)
                }
                Player.STATE_ENDED -> {
                    onTrackComplete()
                }
                Player.STATE_IDLE -> {}
                Player.STATE_BUFFERING -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.message}", error)
            PlayerState.setPlaying(false)
            stopPositionPolling()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initExoPlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra("url") ?: return START_STICKY
                val title = intent.getStringExtra("title") ?: "Unknown"
                val artist = intent.getStringExtra("artist") ?: "Unknown"
                play(url, title, artist)
            }
            ACTION_PAUSE -> pause()
            ACTION_NEXT -> skipToNext()
            ACTION_PREVIOUS -> skipToPrevious()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun initExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(audioAttributes, false)
        }
        exoPlayer?.let { mediaSession = Builder(this, it).build() }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Music playback controls"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun resolveStreamUrl(song: SongResult): String {
        return song.streams.firstOrNull()?.url?.takeIf { it.isNotBlank() }
            ?: song.streamUrl.takeIf { it.isNotBlank() } ?: ""
    }

    private fun playSongWithResolve(song: SongResult) {
        val streamUrl = resolveStreamUrl(song)
        if (streamUrl.isNotBlank()) {
            Log.d(TAG, "playSongWithResolve() playing: ${song.title} - ${song.artist}")
            PlayerState.updateDuration((song.duration ?: 0).toLong())
            play(streamUrl, song.title, song.artist)
        } else {
            Log.d(TAG, "playSongWithResolve() no stream URL, resolving via DirectApi for: ${song.title}")
            PlayerState.updateDuration((song.duration ?: 0).toLong())
            PlayerState.setPlaying(false)
            lifecycleScope.launch {
                try {
                    val api = com.songlinks.app.api.SongApi(applicationContext)
                    val resolvedUrl = api.resolveStreamUrl(song.id, song.title, song.artist)
                    if (resolvedUrl.isNotBlank()) {
                        val updatedSong = song.copy(
                            streams = listOf(com.songlinks.app.api.Stream(quality = "stream", url = resolvedUrl)),
                            streamUrl = resolvedUrl
                        )
                        withContext(Dispatchers.Main) {
                            PlayerState.playSong(updatedSong)
                            play(resolvedUrl, song.title, song.artist)
                        }
                    } else {
                        Log.w(TAG, "playSongWithResolve() failed to resolve for: ${song.title}")
                        withContext(Dispatchers.Main) { PlayerState.setPlaying(false) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "playSongWithResolve() resolve error", e)
                    withContext(Dispatchers.Main) { PlayerState.setPlaying(false) }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun play(url: String, title: String, artist: String) {
        if (url.isBlank()) {
            Log.e(TAG, "play() blank url, aborting")
            return
        }
        Log.d(TAG, "play() url=${url.take(80)}, title=$title, artist=$artist")
        val player = exoPlayer ?: return
        requestAudioFocus()
        try {
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            Log.d(TAG, "play() starting foreground notification")
            startForeground(NOTIFICATION_ID, buildNotification(title, artist))
        } catch (e: Exception) {
            Log.e(TAG, "play() failed", e)
            PlayerState.setPlaying(false)
        }
    }

    fun playSong(song: SongResult) {
        val streamUrl = resolveStreamUrl(song)
        if (streamUrl.isNotBlank()) {
            Log.d(TAG, "playSong() ${song.title} - ${song.artist}, streamUrl=${streamUrl.take(80)}")
            PlayerState.playSong(song)
            PlayerState.updateDuration((song.duration ?: 0).toLong())
            play(streamUrl, song.title, song.artist)
        } else {
            // No stream URL — resolve asynchronously via DirectApi (YouTube Music fallback)
            Log.d(TAG, "playSong() no stream URL, resolving via DirectApi for: ${song.title}")
            PlayerState.playSong(song)
            PlayerState.updateDuration((song.duration ?: 0).toLong())
            PlayerState.setPlaying(false)
            lifecycleScope.launch {
                try {
                    val api = com.songlinks.app.api.SongApi(applicationContext)
                    val resolvedUrl = api.resolveStreamUrl(song.id, song.title, song.artist)
                    if (resolvedUrl.isNotBlank()) {
                        val updatedSong = song.copy(
                            streams = listOf(com.songlinks.app.api.Stream(quality = "stream", url = resolvedUrl)),
                            streamUrl = resolvedUrl
                        )
                        withContext(Dispatchers.Main) {
                            PlayerState.playSong(updatedSong)
                            play(resolvedUrl, song.title, song.artist)
                        }
                    } else {
                        Log.w(TAG, "playSong() failed to resolve stream for: ${song.title}")
                        withContext(Dispatchers.Main) { PlayerState.setPlaying(false) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "playSong() resolve error", e)
                    withContext(Dispatchers.Main) { PlayerState.setPlaying(false) }
                }
            }
        }
    }

    fun pause() {
        Log.d(TAG, "pause()")
        exoPlayer?.pause()
        PlayerState.setPlaying(false)
        abandonAudioFocus()
    }

    fun resume() {
        Log.d(TAG, "resume()")
        requestAudioFocus()
        exoPlayer?.play()
        PlayerState.setPlaying(true)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        PlayerState.seekTo(positionMs)
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying == true

    fun currentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    fun duration(): Long {
        val d = exoPlayer?.duration ?: 0L
        return if (d > 0) d else 0L
    }

    val currentTitle: String
        get() = PlayerState.currentSong.value?.title ?: ""

    val currentArtist: String
        get() = PlayerState.currentSong.value?.artist ?: ""

    fun skipToNext() {
        Log.d(TAG, "skipToNext()")
        val currentQueue = PlayerState.queue.value
        if (currentQueue.isEmpty()) {
            Log.d(TAG, "skipToNext() queue empty, pausing")
            pause()
            return
        }
        PlayerState.next()
        val newSong = PlayerState.currentSong.value
        if (newSong != null) {
            playSongWithResolve(newSong)
        } else {
            Log.d(TAG, "skipToNext() no next song, pausing")
            pause()
        }
    }

    fun skipToPrevious() {
        Log.d(TAG, "skipToPrevious()")
        val currentQueue = PlayerState.queue.value
        if (currentQueue.isEmpty()) return
        if (PlayerState.position.value > 3000L) {
            seekTo(0L)
            exoPlayer?.seekTo(0L)
            return
        }
        PlayerState.previous()
        val newSong = PlayerState.currentSong.value
        if (newSong != null) {
            playSongWithResolve(newSong)
        }
    }

    fun setQueueAndPlay(songs: List<SongResult>, startIndex: Int = 0) {
        Log.d(TAG, "setQueueAndPlay() songs=${songs.size}, startIndex=$startIndex")
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(songs.indices)
        PlayerState.playQueue(songs, safeIndex)
        val song = songs[safeIndex]
        playSongWithResolve(song)
    }

    private fun onTrackComplete() {
        Log.d(TAG, "onTrackComplete()")
        val currentQueue = PlayerState.queue.value.toList()
        val index = PlayerState.currentIndex.value
        val repeat = PlayerState.repeatMode.value

        if (repeat == 2) {
            Log.d(TAG, "onTrackComplete() repeat one, seeking to start")
            exoPlayer?.seekTo(0)
            exoPlayer?.play()
            PlayerState.updatePosition(0L)
            PlayerState.setPlaying(true)
            return
        }

        val nextExists = if (PlayerState.shuffle.value) {
            currentQueue.size > 1
        } else {
            index + 1 < currentQueue.size
        }

        if (nextExists) {
            PlayerState.next()
            val newSong = PlayerState.currentSong.value
            if (newSong != null) {
                Log.d(TAG, "onTrackComplete() playing next: ${newSong.title} - ${newSong.artist}")
                playSongWithResolve(newSong)
                return
            }
        }

        if (repeat == 1 && currentQueue.isNotEmpty()) {
            Log.d(TAG, "onTrackComplete() repeat all, replaying first song")
            val firstSong = currentQueue[0]
            PlayerState.playQueue(currentQueue, 0)
            playSongWithResolve(firstSong)
            return
        }

        Log.d(TAG, "onTrackComplete() no more tracks, stopping")
        PlayerState.setPlaying(false)
        stopPositionPolling()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun startPositionPolling() {
        stopPositionPolling()
        positionPollingJob = lifecycleScope.launch {
            while (isActive) {
                val pos = exoPlayer?.currentPosition ?: 0L
                val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
                PlayerState.updatePosition(pos)
                PlayerState.updateDuration(dur)
                delay(500L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        hasAudioFocus = false
    }

    private fun buildNotification(title: String, artist: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, PlayerReceiver::class.java).setAction(ACTION_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pausePendingIntent = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, PlayerReceiver::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextPendingIntent = PendingIntent.getBroadcast(
            this, 3,
            Intent(this, PlayerReceiver::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            .addAction(
                if (isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying()) "Pause" else "Play",
                pausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    override fun onDestroy() {
        stopPositionPolling()
        cancelSleepTimer()
        disableEqualizer()
        abandonAudioFocus()
        mediaSession?.run {
            release()
        }
        mediaSession = null
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            Log.w(TAG, "startSleepTimer() invalid minutes=$minutes")
            return
        }
        Log.d(TAG, "startSleepTimer() minutes=$minutes")
        cancelSleepTimer()
        val durationMs = minutes.toLong() * 60_000L
        sleepTimerEndTime = System.currentTimeMillis() + durationMs
        PlayerState.updateSleepTimerRemaining(durationMs)
        sleepTimerJob = lifecycleScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                PlayerState.updateSleepTimerRemaining(remaining)
            }
            Log.d(TAG, "startSleepTimer() timer expired, pausing")
            pause()
            PlayerState.updateSleepTimerRemaining(0L)
            sleepTimerEndTime = 0L
        }
    }

    fun cancelSleepTimer() {
        Log.d(TAG, "cancelSleepTimer()")
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndTime = 0L
        PlayerState.updateSleepTimerRemaining(0L)
    }

    fun getSleepTimerEndTimestamp(): Long = sleepTimerEndTime

    fun enableEqualizer(bandLevels: ShortArray) {
        Log.d(TAG, "enableEqualizer() bandLevels=${bandLevels.toList()}")
        disableEqualizer()
        val player = exoPlayer ?: return
        val audioSessionId = player.audioSessionId
        if (audioSessionId == 0 || audioSessionId == android.media.audiofx.AudioEffect.ERROR_BAD_VALUE) {
            Log.w(TAG, "enableEqualizer() invalid audioSessionId=$audioSessionId")
            return
        }
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                val numBands = numberOfBands.toInt()
                val minLevel = bandLevelRange[0]
                val maxLevel = bandLevelRange[1]
                for (i in 0 until numBands) {
                    val level = if (i < bandLevels.size) {
                        bandLevels[i].coerceIn(minLevel, maxLevel)
                    } else {
                        0
                    }
                    setBandLevel(i.toShort(), level)
                }
            }
            PlayerState.setEqualizerEnabled(true)
            updateEqualizerBandsState()
        } catch (e: Exception) {
            Log.e(TAG, "enableEqualizer() failed", e)
            equalizer = null
            PlayerState.setEqualizerEnabled(false)
        }
    }

    fun disableEqualizer() {
        Log.d(TAG, "disableEqualizer()")
        try { equalizer?.release() } catch (_: Exception) {}
        equalizer = null
        PlayerState.setEqualizerEnabled(false)
        PlayerState.updateEqualizerBands(emptyList())
    }

    private fun updateEqualizerBandsState() {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        val bands = mutableListOf<Pair<Int, Short>>()
        for (i in 0 until numBands) {
            val freq = eq.getCenterFreq(i.toShort())
            val level = eq.getBandLevel(i.toShort())
            bands.add(freq to level)
        }
        PlayerState.updateEqualizerBands(bands)
    }

    fun getEqualizerPresets(): List<String> {
        val eq = equalizer ?: return emptyList()
        val count = eq.numberOfPresets.toInt()
        return (0 until count).map { eq.getPresetName(it.toShort()) }
    }

    fun shareSong(context: Context, song: SongResult) {
        val shareText = buildString {
            append("${song.title} - ${song.artist}")
            val url = song.pageUrl
            if (url.isNotBlank()) {
                append("\n$url")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share song").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun fetchLyrics(title: String, artist: String, baseUrl: String): Flow<String> = flow {
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
        val urlStr = "$baseUrl/lyrics?title=$encodedTitle&artist=$encodedArtist"
        var connection: HttpURLConnection? = null
        try {
            connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                emit(body)
            } else {
                emit("")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLyrics error", e)
            emit("")
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}
