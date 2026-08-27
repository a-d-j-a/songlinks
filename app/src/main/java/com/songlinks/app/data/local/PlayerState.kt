package com.songlinks.app.data.local

import com.songlinks.app.api.SongResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PlayerState {

    private val _currentSong = MutableStateFlow<SongResult?>(null)
    val currentSong: StateFlow<SongResult?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<SongResult>>(emptyList())
    val queue: StateFlow<List<SongResult>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    fun playSong(song: SongResult) {
        _currentSong.value = song
        _isPlaying.value = true
        _position.value = 0L
        _duration.value = (song.duration ?: 0).toLong()
    }

    fun playQueue(songs: List<SongResult>, startIndex: Int = 0) {
        if (songs.isEmpty()) {
            _queue.value = emptyList()
            _currentIndex.value = -1
            return
        }
        val safeIndex = startIndex.coerceIn(songs.indices)
        _queue.value = songs
        _currentIndex.value = safeIndex
        playSong(songs[safeIndex])
    }

    fun togglePlay() {
        _isPlaying.value = !_isPlaying.value
    }

    fun next() {
        val currentQueue = _queue.value
        val index = _currentIndex.value
        if (currentQueue.isEmpty()) return

        val repeat = _repeatMode.value

        if (repeat == 2) {
            _position.value = 0L
            _isPlaying.value = true
            return
        }

        val nextIndex = if (_shuffle.value) {
            if (currentQueue.size == 1) 0
            else generateSequence {
                (currentQueue.indices).random()
            }.first { it != index }
        } else {
            index + 1
        }

        if (nextIndex in currentQueue.indices) {
            _currentIndex.value = nextIndex
            playSong(currentQueue[nextIndex])
        } else if (repeat == 1) {
            _currentIndex.value = 0
            playSong(currentQueue[0])
        } else {
            _isPlaying.value = false
            _position.value = 0L
        }
    }

    fun previous() {
        val currentQueue = _queue.value
        val index = _currentIndex.value
        if (currentQueue.isEmpty()) return

        if (_position.value > 3000L) {
            _position.value = 0L
            return
        }

        val prevIndex = if (_shuffle.value) {
            if (currentQueue.size == 1) 0
            else generateSequence {
                (currentQueue.indices).random()
            }.first { it != index }
        } else {
            index - 1
        }

        if (prevIndex in currentQueue.indices) {
            _currentIndex.value = prevIndex
            playSong(currentQueue[prevIndex])
        } else if (_repeatMode.value == 1) {
            _currentIndex.value = currentQueue.lastIndex
            playSong(currentQueue.last())
        } else {
            _position.value = 0L
            _isPlaying.value = false
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = _duration.value
        _position.value = if (dur > 0) positionMs.coerceIn(0L, dur) else positionMs.coerceAtLeast(0L)
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
    }

    fun cycleRepeat() {
        _repeatMode.value = (_repeatMode.value + 1) % 3
    }

    fun setQueue(songs: List<SongResult>, startIndex: Int = 0) {
        _queue.value = songs
        _currentIndex.value = if (songs.isEmpty()) -1 else startIndex.coerceIn(songs.indices)
        if (songs.isNotEmpty() && startIndex in songs.indices) {
            _currentSong.value = songs[startIndex]
        }
    }

    fun updatePosition(positionMs: Long) {
        _position.value = positionMs
    }

    fun updateDuration(durationMs: Long) {
        _duration.value = durationMs
    }

    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()

    fun updateSleepTimerRemaining(ms: Long) {
        _sleepTimerRemaining.value = ms
    }

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
    }

    private val _equalizerBands = MutableStateFlow<List<Pair<Int, Short>>>(emptyList())
    val equalizerBands: StateFlow<List<Pair<Int, Short>>> = _equalizerBands.asStateFlow()

    fun updateEqualizerBands(bands: List<Pair<Int, Short>>) {
        _equalizerBands.value = bands
    }

    fun moveQueueItem(from: Int, to: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (from !in currentQueue.indices || to !in currentQueue.indices) return
        val item = currentQueue.removeAt(from)
        currentQueue.add(to, item)
        _queue.value = currentQueue
        val curIdx = _currentIndex.value
        _currentIndex.value = when (curIdx) {
            from -> to
            in (from + 1)..to -> curIdx - 1
            in to until from -> curIdx + 1
            else -> curIdx
        }
    }
}
