@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahize.mamusique.ui

import android.content.Context
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahize.mamusique.MusicViewModel
import com.ahize.mamusique.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

// region Pochettes

object ArtworkLoader {
    private val cache = LruCache<Long, ImageBitmap>(200)
    private val misses = Collections.synchronizedSet(HashSet<Long>())

    fun cached(id: Long): ImageBitmap? = cache.get(id)

    suspend fun load(context: Context, track: Track): ImageBitmap? = withContext(Dispatchers.IO) {
        cache.get(track.id)?.let { return@withContext it }
        if (track.id in misses) return@withContext null
        try {
            val bitmap = context.contentResolver.loadThumbnail(track.uri, Size(512, 512), null).asImageBitmap()
            cache.put(track.id, bitmap)
            bitmap
        } catch (e: Exception) {
            misses.add(track.id)
            null
        }
    }
}

@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    grayscale: Boolean = false,
) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = track?.let { ArtworkLoader.cached(it.id) }, track?.id) {
        value = track?.let { ArtworkLoader.load(context, it) }
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(Color(0xFFD9D9DE), Color(0xFFEDEDF0)))),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                colorFilter = if (grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFF9A9AA0),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}

// endregion

// region Lignes et cartes

@Composable
fun TrackRow(
    track: Track,
    vm: MusicViewModel,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    showArtwork: Boolean = true,
    extraMenu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Artwork(track, Modifier.size(50.dp), cornerRadius = 6.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (isCurrent && isPlaying) {
            Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        } else if (isFavorite) {
            Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TrackMenu(
                track = track,
                vm = vm,
                isFavorite = isFavorite,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                extra = extraMenu,
            )
        }
    }
}

/** Menu d'un morceau : lire ensuite, file d'attente, favori, playlist, supprimer. */
@Composable
fun TrackMenu(
    track: Track,
    vm: MusicViewModel,
    isFavorite: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extra: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val onDelete = LocalDeleteTrack.current

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Lire ensuite") },
            leadingIcon = { Icon(Icons.Rounded.SubdirectoryArrowRight, null) },
            onClick = { vm.playNext(track); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Ajouter à la file d'attente") },
            leadingIcon = { Icon(Icons.Rounded.AddToQueue, null) },
            onClick = { vm.addToQueue(track); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris") },
            leadingIcon = { Icon(if (isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star, null) },
            onClick = { vm.toggleFavorite(track.id); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Ajouter à une playlist") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            onClick = { showPlaylistPicker = true; onDismiss() },
        )
        extra?.invoke(this, onDismiss)
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Supprimer du téléphone", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDelete(track); onDismiss() },
        )
    }

    if (showPlaylistPicker) {
        val library by vm.library.collectAsState()
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Ajouter à une playlist") },
            text = {
                if (library.playlists.isEmpty()) {
                    Text("Aucune playlist. Crée-en une dans Bibliothèque > Playlists.")
                } else {
                    LazyColumn {
                        items(library.playlists, key = { it.id }) { playlist ->
                            Text(
                                playlist.name,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.addToPlaylist(playlist.id, listOf(track.id))
                                        showPlaylistPicker = false
                                    }
                                    .padding(vertical = 14.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Fermer") }
            },
        )
    }
}

@Composable
fun CoverCard(
    track: Track?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Artwork(track, Modifier.fillMaxWidth(), cornerRadius = 10.dp)
        Spacer(Modifier.height(6.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium)
        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PlayShuffleButtons(tracks: List<Track>, vm: MusicViewModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalButton(
            onClick = { vm.play(tracks) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text("Lecture", fontWeight = FontWeight.SemiBold)
        }
        FilledTonalButton(
            onClick = { vm.playShuffled(tracks) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(6.dp))
            Text("Aléatoire", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Tuile colorée façon Apple Music (pochette en bichromie) */
@Composable
fun BrowseTile(title: String, color: Color, cover: Track?, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick)
    ) {
        if (cover != null) {
            Artwork(
                cover,
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .graphicsLayer(alpha = 0.55f, compositingStrategy = CompositingStrategy.Offscreen),
                cornerRadius = 0.dp,
                grayscale = true,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = 0.6f))))
        )
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

private val tilePalette = listOf(
    Color(0xFFE8334A), Color(0xFFD9B53D), Color(0xFF6B8CE8), Color(0xFFDB4A87),
    Color(0xFFDB6649), Color(0xFF61BA82), Color(0xFF8C5CD9), Color(0xFF339EB8), Color(0xFF737833),
)

fun stableColor(text: String): Color {
    var hash = 5381L
    text.forEach { hash = (hash * 33 + it.code) and 0x7FFFFFFF }
    return tilePalette[(hash % tilePalette.size).toInt()]
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Fonction de suppression fournie par l'écran principal (demande de confirmation Android). */
val LocalDeleteTrack = staticCompositionLocalOf<(Track) -> Unit> { {} }

// endregion
