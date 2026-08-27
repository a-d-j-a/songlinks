package com.songlinks.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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
import androidx.annotation.OptIn
import com.songlinks.app.MainActivity
import com.songlinks.app.api.SongResult
import com.songlinks.app.data.local.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                exoPlayer?.volume = 0.3f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                exoPlayer?.volume = 1.0f
                resume()
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
            PlayerState.togglePlay()
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
            setAudioAttributes(audioAttributes, true)
        }
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

    @OptIn(UnstableApi::class)
    fun play(url: String, title: String, artist: String) {
        requestAudioFocus()
        val player = exoPlayer ?: return
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        startForeground(NOTIFICATION_ID, buildNotification(title, artist))
    }

    fun playSong(song: SongResult) {
        val streamUrl = song.streams.firstOrNull()?.url
        if (streamUrl.isNullOrBlank()) return
        val durationMs = (song.duration ?: 0).toLong() * 1000L
        PlayerState.playSong(song)
        PlayerState.updateDuration(durationMs)
        play(streamUrl, song.title, song.artist)
    }

    fun pause() {
        exoPlayer?.pause()
        if (PlayerState.isPlaying.value) {
            PlayerState.togglePlay()
        }
        abandonAudioFocus()
    }

    fun resume() {
        requestAudioFocus()
        exoPlayer?.play()
        if (!PlayerState.isPlaying.value) {
            PlayerState.togglePlay()
        }
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
        val currentQueue = PlayerState.queue.value
        if (currentQueue.isEmpty()) {
            pause()
            return
        }
        PlayerState.next()
        val newSong = PlayerState.currentSong.value
        if (newSong != null) {
            val streamUrl = newSong.streams.firstOrNull()?.url
            if (!streamUrl.isNullOrBlank()) {
                play(streamUrl, newSong.title, newSong.artist)
            } else {
                pause()
            }
        } else {
            pause()
        }
    }

    fun skipToPrevious() {
        val currentQueue = PlayerState.queue.value
        if (currentQueue.isEmpty()) return
        PlayerState.previous()
        val newSong = PlayerState.currentSong.value
        if (newSong != null) {
            val streamUrl = newSong.streams.firstOrNull()?.url
            if (!streamUrl.isNullOrBlank()) {
                play(streamUrl, newSong.title, newSong.artist)
            }
        }
    }

    fun setQueueAndPlay(songs: List<SongResult>, startIndex: Int = 0) {
        PlayerState.playQueue(songs, startIndex)
        val song = songs.getOrNull(startIndex) ?: return
        playSong(song)
    }

    private fun onTrackComplete() {
        val currentQueue = PlayerState.queue.value
        val index = PlayerState.currentIndex.value
        val repeat = PlayerState.repeatMode.value

        if (repeat == 2) {
            exoPlayer?.seekTo(0)
            exoPlayer?.play()
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
                val streamUrl = newSong.streams.firstOrNull()?.url
                if (!streamUrl.isNullOrBlank()) {
                    play(streamUrl, newSong.title, newSong.artist)
                    return
                }
            }
        }

        if (repeat == 1 && currentQueue.isNotEmpty()) {
            val firstSong = currentQueue[0]
            PlayerState.playQueue(currentQueue, 0)
            val streamUrl = firstSong.streams.firstOrNull()?.url
            if (!streamUrl.isNullOrBlank()) {
                play(streamUrl, firstSong.title, firstSong.artist)
                return
            }
        }

        PlayerState.togglePlay()
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
        abandonAudioFocus()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
