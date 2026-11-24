package com.ahize.mamusique.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAdded: Long,
)

/** Lit toute la musique du téléphone (MediaStore) + les fichiers du dossier Music/MaMusique. */
object MusicRepository {

    fun scan(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("%MaMusique%")

        val result = mutableListOf<Track>()
        try {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val fileName = c.getString(nameCol) ?: "Sans titre"
                    result += Track(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        title = c.getString(titleCol).clean() ?: fileName.substringBeforeLast('.'),
                        artist = c.getString(artistCol).clean() ?: "Artiste inconnu",
                        album = c.getString(albumCol).clean() ?: "Album inconnu",
                        durationMs = c.getLong(durationCol),
                        dateAdded = c.getLong(dateCol),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        result += scanVideos(context)
        return result
    }

    /** Vidéos téléchargées dans Music/MaMusique (lues comme des morceaux). Identifiant négatif. */
    private fun scanVideos(context: Context): List<Track> {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DISPLAY_NAME,
        )
        val result = mutableListOf<Track>()
        try {
            context.contentResolver.query(
                collection, projection,
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?", arrayOf("%MaMusique%"), null,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val fileName = c.getString(nameCol) ?: "Vidéo"
                    result += Track(
                        id = -id,
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        title = c.getString(titleCol).clean() ?: fileName.substringBeforeLast('.'),
                        artist = "Vidéos",
                        album = "Vidéos téléchargées",
                        durationMs = c.getLong(durationCol),
                        dateAdded = c.getLong(dateCol),
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return result
    }

    private fun String?.clean(): String? {
        val value = this?.trim()
        if (value.isNullOrEmpty() || value == "<unknown>") return null
        return value
    }
}

/** Les différentes listes de morceaux, encodées dans une clé texte pour la navigation. */
object TrackFilters {
    const val ALL = "all"
    const val FAVORITES = "favorites"
    const val RECENT = "recent"
    const val MOST = "most"
    const val NEVER = "never"
    fun artist(name: String) = "artist:$name"
    fun album(name: String, artist: String) = "album:$name\u0001$artist"

    fun isAlbum(key: String) = key.startsWith("album:")

    fun title(key: String): String = when {
        key == ALL -> "Morceaux"
        key == FAVORITES -> "Favoris"
        key == RECENT -> "Ajoutés récemment"
        key == MOST -> "Les plus écoutés"
        key == NEVER -> "Jamais écoutés"
        key.startsWith("artist:") -> key.removePrefix("artist:")
        key.startsWith("album:") -> key.removePrefix("album:").substringBefore('\u0001')
        else -> ""
    }

    fun apply(key: String, tracks: List<Track>, library: LibraryState): List<Track> {
        val byTitle = compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        return when {
            key == ALL -> tracks.sortedWith(byTitle)
            key == FAVORITES -> tracks.filter { it.id in library.favorites }.sortedWith(byTitle)
            key == RECENT -> tracks.sortedByDescending { it.dateAdded }.take(50)
            key == MOST -> tracks.filter { (library.playCounts[it.id] ?: 0) > 0 }
                .sortedByDescending { library.playCounts[it.id] ?: 0 }.take(50)
            key == NEVER -> tracks.filter { (library.playCounts[it.id] ?: 0) == 0 }.sortedWith(byTitle)
            key.startsWith("artist:") -> {
                val name = key.removePrefix("artist:")
                tracks.filter { it.artist == name }
                    .sortedWith(compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.then(byTitle))
            }
            key.startsWith("album:") -> {
                val parts = key.removePrefix("album:").split('\u0001')
                tracks.filter { it.album == parts[0] && it.artist == parts.getOrNull(1) }.sortedWith(byTitle)
            }
            else -> emptyList()
        }
    }
}
