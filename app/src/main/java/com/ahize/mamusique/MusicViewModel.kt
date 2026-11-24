package com.ahize.mamusique

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ahize.mamusique.data.LibraryState
import com.ahize.mamusique.data.LibraryStore
import com.ahize.mamusique.data.MusicRepository
import com.ahize.mamusique.data.PlaylistData
import com.ahize.mamusique.data.Track
import com.ahize.mamusique.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QueueEntry(val index: Int, val trackId: Long)

data class PlayerUiState(
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val upNext: List<QueueEntry> = emptyList(),
)

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LibraryStore(app)
    val library: StateFlow<LibraryState> = store.state

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _player = MutableStateFlow(PlayerUiState())
    val player: StateFlow<PlayerUiState> = _player.asStateFlow()

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState()

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.toLongOrNull()?.let { recordPlay(it) }
        }
    }

    init {
        connectToService()
        viewModelScope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
                if (c.isPlaying) {
                    _player.update {
                        it.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0))
                    }
                }
            }
        }
    }

    // region Bibliothèque

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MusicRepository.scan(getApplication()) }
            _tracks.value = list
            _loaded.value = true
        }
    }

    fun track(id: Long?): Track? = id?.let { wanted -> _tracks.value.firstOrNull { it.id == wanted } }

    fun toggleFavorite(id: Long) = store.update {
        it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id)
    }

    private fun recordPlay(id: Long) = store.update {
        it.copy(
            playCounts = it.playCounts + (id to ((it.playCounts[id] ?: 0) + 1)),
            lastPlayed = it.lastPlayed + (id to System.currentTimeMillis()),
        )
    }

    fun createPlaylist(name: String): String {
        val playlist = PlaylistData(name = name)
        store.update { it.copy(playlists = it.playlists + playlist) }
        return playlist.id
    }

    fun deletePlaylist(id: String) = store.update {
        it.copy(playlists = it.playlists.filterNot { p -> p.id == id })
    }

    fun renamePlaylist(id: String, name: String) = updatePlaylist(id) { it.copy(name = name) }

    fun addToPlaylist(playlistId: String, trackIds: List<Long>) = updatePlaylist(playlistId) { p ->
        p.copy(trackIds = p.trackIds + trackIds.filterNot { it in p.trackIds })
    }

    fun removeFromPlaylist(playlistId: String, index: Int) = updatePlaylist(playlistId) { p ->
        p.copy(trackIds = p.trackIds.toMutableList().also { if (index in it.indices) it.removeAt(index) })
    }

    fun movePlaylistTrack(playlistId: String, from: Int, to: Int) = updatePlaylist(playlistId) { p ->
        val list = p.trackIds.toMutableList()
        if (from in list.indices && to in list.indices) list.add(to, list.removeAt(from))
        p.copy(trackIds = list)
    }

    private fun updatePlaylist(id: String, transform: (PlaylistData) -> PlaylistData) = store.update { state ->
        state.copy(playlists = state.playlists.map { if (it.id == id) transform(it) else it })
    }

    /** Après suppression d'un fichier : on le retire de la file, des playlists et des favoris. */
    fun onTrackDeleted(id: Long) {
        controller?.let { c ->
            for (i in c.mediaItemCount - 1 downTo 0) {
                if (c.getMediaItemAt(i).mediaId == id.toString()) c.removeMediaItem(i)
            }
        }
        store.update { s ->
            s.copy(
                favorites = s.favorites - id,
                playlists = s.playlists.map { p -> p.copy(trackIds = p.trackIds.filterNot { it == id }) },
            )
        }
        refresh()
    }

    // endregion

    // region Lecture

    fun play(list: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(list.map { it.toMediaItem() }, startIndex.coerceIn(list.indices), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Track>) {
        if (list.isNotEmpty()) play(list, list.indices.random(), shuffle = true)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }

    fun previous() { controller?.seekToPrevious() }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _player.update { it.copy(positionMs = ms) }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playNext(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(track))
        c.addMediaItem(track.toMediaItem())
    }

    fun jumpTo(index: Int) {
        controller?.let {
            it.seekToDefaultPosition(index)
            it.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index != c.currentMediaItemIndex && index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    private fun connectToService() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        viewModelScope.launch {
            try {
                val c = MediaController.Builder(app, token).buildAsync().await()
                controller = c
                c.addListener(listener)
                syncState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun syncState() {
        val c = controller ?: return
        val upNext = mutableListOf<QueueEntry>()
        val timeline = c.currentTimeline
        if (!timeline.isEmpty && c.currentMediaItemIndex != C.INDEX_UNSET) {
            var index = c.currentMediaItemIndex
            while (upNext.size < 300) {
                index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
                if (index == C.INDEX_UNSET) break
                val id = c.getMediaItemAt(index).mediaId.toLongOrNull() ?: continue
                upNext += QueueEntry(index, id)
            }
        }
        _player.value = PlayerUiState(
            currentId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            upNext = upNext,
        )
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }

    // endregion
}

fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
