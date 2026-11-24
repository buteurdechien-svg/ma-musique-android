package com.ahize.mamusique.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PlaylistData(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackIds: List<Long> = emptyList(),
    val created: Long = System.currentTimeMillis(),
)

data class LibraryState(
    val favorites: Set<Long> = emptySet(),
    val playCounts: Map<Long, Int> = emptyMap(),
    val lastPlayed: Map<Long, Long> = emptyMap(),
    val playlists: List<PlaylistData> = emptyList(),
)

/** Favoris, statistiques d'écoute et playlists, sauvegardés sur le téléphone. */
class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun update(transform: (LibraryState) -> LibraryState) {
        _state.update(transform)
        save(_state.value)
    }

    private fun load(): LibraryState {
        val raw = prefs.getString("state", null) ?: return LibraryState()
        return try {
            val json = JSONObject(raw)
            val favorites = json.optJSONArray("favorites").toLongList().toSet()
            val counts = json.optJSONObject("playCounts").toMap { it.toString().toIntOrNull() ?: 0 }
            val last = json.optJSONObject("lastPlayed").toMap { it.toString().toLongOrNull() ?: 0L }
            val playlists = mutableListOf<PlaylistData>()
            val arr = json.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                playlists += PlaylistData(
                    id = p.getString("id"),
                    name = p.getString("name"),
                    trackIds = p.optJSONArray("trackIds").toLongList(),
                    created = p.optLong("created"),
                )
            }
            LibraryState(favorites, counts, last, playlists)
        } catch (e: Exception) {
            LibraryState()
        }
    }

    private fun save(state: LibraryState) {
        val json = JSONObject()
        json.put("favorites", JSONArray(state.favorites.toList()))
        json.put("playCounts", JSONObject().apply { state.playCounts.forEach { (k, v) -> put(k.toString(), v) } })
        json.put("lastPlayed", JSONObject().apply { state.lastPlayed.forEach { (k, v) -> put(k.toString(), v) } })
        json.put("playlists", JSONArray().apply {
            state.playlists.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("trackIds", JSONArray(p.trackIds))
                    put("created", p.created)
                })
            }
        })
        prefs.edit().putString("state", json.toString()).apply()
    }

    private fun JSONArray?.toLongList(): List<Long> {
        if (this == null) return emptyList()
        return (0 until length()).map { getLong(it) }
    }

    private fun <V> JSONObject?.toMap(convert: (Any) -> V): Map<Long, V> {
        if (this == null) return emptyMap()
        val map = mutableMapOf<Long, V>()
        keys().forEach { key -> key.toLongOrNull()?.let { map[it] = convert(get(key)) } }
        return map
    }
}
