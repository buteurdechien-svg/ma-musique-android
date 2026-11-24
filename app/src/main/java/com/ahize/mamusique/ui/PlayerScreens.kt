@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahize.mamusique.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ahize.mamusique.MusicViewModel

@Composable
fun MiniPlayer(vm: MusicViewModel, onOpen: () -> Unit) {
    val state by vm.player.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val track = remember(state.currentId, tracks) { tracks.firstOrNull { it.id == state.currentId } } ?: return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(10.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column {
            Row(
                Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.togglePlayPause() }) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Lecture", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Rounded.SkipNext, "Suivant", modifier = Modifier.size(28.dp))
                }
            }
            val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun NowPlayingScreen(vm: MusicViewModel, onClose: () -> Unit) {
    val state by vm.player.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val library by vm.library.collectAsState()
    val track = remember(state.currentId, tracks) { tracks.firstOrNull { it.id == state.currentId } }
    var showQueue by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "artScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Bloque les clics vers l'écran du dessous
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        // Fond : pochette floutée
        Artwork(track, Modifier.fillMaxSize().scale(1.6f).blur(80.dp), cornerRadius = 0.dp)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.7f))))
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Fermer", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.weight(1f))
            Artwork(
                track,
                Modifier
                    .fillMaxWidth()
                    .scale(artScale)
                    .shadow(24.dp, RoundedCornerShape(14.dp)),
                cornerRadius = 14.dp,
            )
            Spacer(Modifier.weight(1f))

            // Titre + favori
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track?.title ?: "", color = Color.White, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.artist ?: "", color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (track != null) {
                    val fav = track.id in library.favorites
                    IconButton(
                        onClick = { vm.toggleFavorite(track.id) },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape),
                    ) {
                        Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favori", tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Barre de progression
            val duration = state.durationMs.coerceAtLeast(1L).toFloat()
            val shown = scrubbing ?: state.positionMs.toFloat()
            Slider(
                value = shown.coerceIn(0f, duration),
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { vm.seekTo(it.toLong()) }
                    scrubbing = null
                },
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(shown.toLong()), color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("-" + formatDuration((state.durationMs - shown.toLong()).coerceAtLeast(0)),
                    color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))

            // Commandes principales
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.previous() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Color.White, modifier = Modifier.size(44.dp))
                }
                IconButton(onClick = { vm.togglePlayPause() }, modifier = Modifier.size(84.dp)) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Lecture",
                        tint = Color.White, modifier = Modifier.size(68.dp))
                }
                IconButton(onClick = { vm.next() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Suivant", tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Aléatoire / répétition / file d'attente
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Aléatoire",
                        tint = Color.White.copy(alpha = if (state.shuffle) 1f else 0.45f))
                }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Répéter",
                        tint = Color.White.copy(alpha = if (state.repeatMode == Player.REPEAT_MODE_OFF) 0.45f else 1f),
                    )
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, "File d'attente", tint = Color.White)
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(vm)
        }
    }
}

@Composable
private fun QueueSheet(vm: MusicViewModel) {
    val state by vm.player.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val library by vm.library.collectAsState()
    val byId = remember(tracks) { tracks.associateBy { it.id } }

    LazyColumn(Modifier.fillMaxHeight(0.85f), contentPadding = PaddingValues(bottom = 24.dp)) {
        byId[state.currentId]?.let { current ->
            item {
                Text("En cours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                TrackRow(current, vm, true, state.isPlaying, current.id in library.favorites, onClick = {})
            }
        }
        item {
            Text("À suivre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
        }
        if (state.upNext.isEmpty()) {
            item {
                Text("Rien à suivre", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        items(state.upNext, key = { it.index }) { entry ->
            val track = byId[entry.trackId] ?: return@items
            TrackRow(
                track = track,
                vm = vm,
                isCurrent = false,
                isPlaying = false,
                isFavorite = track.id in library.favorites,
                onClick = { vm.jumpTo(entry.index) },
                extraMenu = { close ->
                    DropdownMenuItem(
                        text = { Text("Retirer de la file") },
                        leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                        onClick = { vm.removeFromQueue(entry.index); close() },
                    )
                },
            )
        }
    }
}
