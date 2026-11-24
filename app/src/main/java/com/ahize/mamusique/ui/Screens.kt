@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahize.mamusique.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahize.mamusique.MusicViewModel
import com.ahize.mamusique.data.LibraryState
import com.ahize.mamusique.data.Track
import com.ahize.mamusique.data.TrackFilters
import java.util.Calendar

// region Accueil

@Composable
fun HomeScreen(vm: MusicViewModel, nav: NavHostController) {
    val tracks by vm.tracks.collectAsState()
    val library by vm.library.collectAsState()
    val loaded by vm.loaded.collectAsState()

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }

    Column(Modifier.fillMaxSize()) {
        LargeTitle("Accueil")
        if (loaded && tracks.isEmpty()) {
            EmptyLibrary(onBrowser = { nav.navigate("browser") })
            return@Column
        }
        val recentlyPlayed = remember(tracks, library) {
            tracks.filter { library.lastPlayed.containsKey(it.id) }
                .sortedByDescending { library.lastPlayed[it.id] }
                .take(15)
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(greeting, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    PlayShuffleButtons(TrackFilters.apply(TrackFilters.ALL, tracks, library), vm)
                }
            }
            item { Carousel("Écoutés récemment", recentlyPlayed, vm) }
            item { Carousel("Ajoutés récemment", TrackFilters.apply(TrackFilters.RECENT, tracks, library).take(15), vm) }
            item { Carousel("Tes favoris", TrackFilters.apply(TrackFilters.FAVORITES, tracks, library), vm) }
            item { Carousel("Les plus écoutés", TrackFilters.apply(TrackFilters.MOST, tracks, library).take(15), vm) }
            item { Carousel("À redécouvrir", TrackFilters.apply(TrackFilters.NEVER, tracks, library).take(15), vm) }
        }
    }
}

@Composable
private fun Carousel(title: String, tracks: List<Track>, vm: MusicViewModel) {
    if (tracks.isEmpty()) return
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                CoverCard(track, track.title, track.artist, Modifier.width(150.dp)) {
                    vm.play(tracks, index)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onBrowser: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Ta bibliothèque est vide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Copie tes MP3 dans le dossier Musique de ton téléphone : ils apparaîtront ici automatiquement. " +
                "Tu peux aussi les télécharger depuis ton Drive ou ton site.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBrowser) {
            Icon(Icons.Rounded.Language, null)
            Spacer(Modifier.width(8.dp))
            Text("Ouvrir le navigateur")
        }
    }
}

@Composable
fun LargeTitle(text: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SubPageBar(title: String, nav: NavHostController, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = MaterialTheme.colorScheme.primary)
            }
        },
        actions = actions,
    )
}

// endregion

// region Bibliothèque

@Composable
fun LibraryScreen(vm: MusicViewModel, nav: NavHostController) {
    val tracks by vm.tracks.collectAsState()
    val recent = remember(tracks) { tracks.sortedByDescending { it.dateAdded }.take(12) }

    Column(Modifier.fillMaxSize()) {
        LargeTitle("Bibliothèque") {
            IconButton(onClick = { nav.navigate("browser") }) {
                Icon(Icons.Rounded.Language, "Navigateur", tint = MaterialTheme.colorScheme.primary)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val rows = listOf(
                Triple("Playlists", Icons.AutoMirrored.Rounded.QueueMusic, "playlists"),
                Triple("Artistes", Icons.Rounded.Mic, "artists"),
                Triple("Albums", Icons.Rounded.Album, "albums"),
                Triple("Morceaux", Icons.Rounded.MusicNote, "tracks/${TrackFilters.ALL}"),
                Triple("Favoris", Icons.Rounded.Star, "tracks/${TrackFilters.FAVORITES}"),
            )
            rows.forEach { (label, icon, route) ->
                item(span = { GridItemSpan(2) }) {
                    LibraryRow(label, icon) { nav.navigate(route) }
                }
            }
            if (recent.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text("Ajoutés récemment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
                }
                items(recent, key = { it.id }) { track ->
                    val index = recent.indexOf(track)
                    CoverCard(
                        track, track.title, track.artist,
                        Modifier.padding(
                            start = if (index % 2 == 0) 16.dp else 0.dp,
                            end = if (index % 2 == 1) 16.dp else 0.dp,
                            bottom = 12.dp,
                        ),
                    ) { vm.play(recent, index) }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(18.dp))
            Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(start = 60.dp))
    }
}

@Composable
fun TracksScreen(vm: MusicViewModel, nav: NavHostController, key: String) {
    val tracks by vm.tracks.collectAsState()
    val library by vm.library.collectAsState()
    val player by vm.player.collectAsState()
    val list = remember(tracks, library, key) { TrackFilters.apply(key, tracks, library) }
    val isAlbum = TrackFilters.isAlbum(key)

    Column(Modifier.fillMaxSize()) {
        SubPageBar(if (isAlbum) "" else TrackFilters.title(key), nav)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (isAlbum && list.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(list.first(), Modifier.size(240.dp), cornerRadius = 12.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(list.first().album, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(list.first().artist, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            item {
                PlayShuffleButtons(list, vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (list.isEmpty()) {
                item {
                    Text("Aucun morceau", Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(list, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    track = track,
                    vm = vm,
                    isCurrent = player.currentId == track.id,
                    isPlaying = player.isPlaying,
                    isFavorite = track.id in library.favorites,
                    showArtwork = !isAlbum,
                    onClick = { vm.play(list, index) },
                )
            }
        }
    }
}

@Composable
fun ArtistsScreen(vm: MusicViewModel, nav: NavHostController) {
    val tracks by vm.tracks.collectAsState()
    val artists = remember(tracks) {
        tracks.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Artistes", nav)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(artists, key = { it.first }) { (name, list) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.openTracks(TrackFilters.artist(name)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(list.first(), Modifier.size(56.dp), cornerRadius = 28.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${list.size} morceau${if (list.size > 1) "x" else ""}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsScreen(vm: MusicViewModel, nav: NavHostController) {
    val tracks by vm.tracks.collectAsState()
    val albums = remember(tracks) {
        tracks.groupBy { it.album to it.artist }.toList().sortedBy { it.first.first.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Albums", nav)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(albums, key = { "${it.first.first}|${it.first.second}" }) { (key, list) ->
                CoverCard(list.first(), key.first, key.second) {
                    nav.openTracks(TrackFilters.album(key.first, key.second))
                }
            }
        }
    }
}

// endregion

// region Playlists

@Composable
fun PlaylistsScreen(vm: MusicViewModel, nav: NavHostController) {
    val library by vm.library.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    var showCreate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        SubPageBar("Playlists", nav)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showCreate = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        Modifier.size(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(14.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("Nouvelle playlist…", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            items(library.playlists.sortedByDescending { it.created }, key = { it.id }) { playlist ->
                val cover = playlist.trackIds.firstNotNullOfOrNull { byId[it] }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.navigate("playlist/${playlist.id}") }
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(cover, Modifier.size(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${playlist.trackIds.size} morceau${if (playlist.trackIds.size > 1) "x" else ""}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { toDelete = playlist.id }) {
                        Icon(Icons.Rounded.DeleteOutline, "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Nouvelle playlist",
            confirm = "Créer",
            onDismiss = { showCreate = false },
            onConfirm = { vm.createPlaylist(it); showCreate = false },
        )
    }
    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer la playlist ?") },
            text = { Text("Les morceaux restent dans ta bibliothèque.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaylist(id); toDelete = null }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    confirm: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Nom de la playlist") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun PlaylistDetailScreen(vm: MusicViewModel, nav: NavHostController, playlistId: String) {
    val library by vm.library.collectAsState()
    val tracks by vm.tracks.collectAsState()
    val player by vm.player.collectAsState()
    val playlist = library.playlists.firstOrNull { it.id == playlistId }
    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    if (playlist == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    // Garde l'index réel dans la playlist pour retirer / déplacer
    val entries = playlist.trackIds.mapIndexedNotNull { i, id -> byId[id]?.let { i to it } }
    val list = entries.map { it.second }

    Column(Modifier.fillMaxSize()) {
        SubPageBar(playlist.name, nav) {
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Rounded.Edit, "Renommer", tint = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { PlayShuffleButtons(list, vm, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            item {
                TextButton(onClick = { showPicker = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Rounded.AddCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter des morceaux")
                }
            }
            itemsIndexed(entries, key = { _, e -> "${e.first}-${e.second.id}" }) { position, (realIndex, track) ->
                TrackRow(
                    track = track,
                    vm = vm,
                    isCurrent = player.currentId == track.id,
                    isPlaying = player.isPlaying,
                    isFavorite = track.id in library.favorites,
                    onClick = { vm.play(list, position) },
                    extraMenu = { close ->
                        HorizontalDivider()
                        if (realIndex > 0) DropdownMenuItem(
                            text = { Text("Monter") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) },
                            onClick = { vm.movePlaylistTrack(playlist.id, realIndex, realIndex - 1); close() },
                        )
                        if (realIndex < playlist.trackIds.lastIndex) DropdownMenuItem(
                            text = { Text("Descendre") },
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) },
                            onClick = { vm.movePlaylistTrack(playlist.id, realIndex, realIndex + 1); close() },
                        )
                        DropdownMenuItem(
                            text = { Text("Retirer de la playlist") },
                            leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                            onClick = { vm.removeFromPlaylist(playlist.id, realIndex); close() },
                        )
                    },
                )
            }
        }
    }

    if (showPicker) {
        TrackPickerDialog(
            tracks = TrackFilters.apply(TrackFilters.ALL, tracks, library),
            alreadyIn = playlist.trackIds.toSet(),
            onDismiss = { showPicker = false },
            onAdd = { vm.addToPlaylist(playlist.id, it); showPicker = false },
        )
    }
    if (showRename) {
        NameDialog(
            title = "Renommer",
            confirm = "OK",
            initial = playlist.name,
            onDismiss = { showRename = false },
            onConfirm = { vm.renamePlaylist(playlist.id, it); showRename = false },
        )
    }
}

@Composable
private fun TrackPickerDialog(
    tracks: List<Track>,
    alreadyIn: Set<Long>,
    onDismiss: () -> Unit,
    onAdd: (List<Long>) -> Unit,
) {
    val selection = remember { mutableStateListOf<Long>() }
    var query by remember { mutableStateOf("") }
    val shown = tracks.filter {
        query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter des morceaux") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Rechercher") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(shown, key = { it.id }) { track ->
                        val added = track.id in alreadyIn
                        val selected = track.id in selection
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !added) {
                                    if (selected) selection.remove(track.id) else selection.add(track.id)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(track, Modifier.size(40.dp), cornerRadius = 4.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                if (added || selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                null,
                                tint = if (added) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selection.isNotEmpty(), onClick = { onAdd(selection.toList()) }) {
                Text("Ajouter (${selection.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

// endregion

// region Recherche

private data class Tile(val key: String, val title: String, val color: androidx.compose.ui.graphics.Color, val cover: Track?)

@Composable
fun SearchScreen(vm: MusicViewModel, nav: NavHostController) {
    val tracks by vm.tracks.collectAsState()
    val library by vm.library.collectAsState()
    val player by vm.player.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        LargeTitle("Recherche")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Artistes, morceaux, albums…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (query.isBlank()) {
            BrowseGrid(tracks, library, nav)
        } else {
            SearchResults(query.trim(), tracks, library, player.currentId, player.isPlaying, vm, nav)
        }
    }
}

@Composable
private fun BrowseGrid(tracks: List<Track>, library: LibraryState, nav: NavHostController) {
    val tiles = remember(tracks, library) {
        fun cover(list: List<Track>) = list.firstOrNull()
        val base = listOf(
            Tile(TrackFilters.FAVORITES, "Favoris", androidx.compose.ui.graphics.Color(0xFFE8334A),
                cover(TrackFilters.apply(TrackFilters.FAVORITES, tracks, library))),
            Tile(TrackFilters.RECENT, "Ajoutés récemment", androidx.compose.ui.graphics.Color(0xFFD9B53D),
                cover(TrackFilters.apply(TrackFilters.RECENT, tracks, library))),
            Tile(TrackFilters.MOST, "Les plus écoutés", androidx.compose.ui.graphics.Color(0xFF6B8CE8),
                cover(TrackFilters.apply(TrackFilters.MOST, tracks, library))),
            Tile(TrackFilters.NEVER, "Jamais écoutés", androidx.compose.ui.graphics.Color(0xFF61BA82),
                cover(TrackFilters.apply(TrackFilters.NEVER, tracks, library))),
        )
        val artists = tracks.groupBy { it.artist }.toList()
            .sortedByDescending { it.second.size }
            .take(16)
            .map { (name, list) -> Tile(TrackFilters.artist(name), name, stableColor(name), list.first()) }
        base + artists
    }

    if (tracks.isEmpty()) {
        Text(
            "Ajoute de la musique pour pouvoir la rechercher.",
            Modifier.fillMaxWidth().padding(40.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(tiles, key = { it.key }) { tile ->
            BrowseTile(tile.title, tile.color, tile.cover) { nav.openTracks(tile.key) }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    tracks: List<Track>,
    library: LibraryState,
    currentId: Long?,
    isPlaying: Boolean,
    vm: MusicViewModel,
    nav: NavHostController,
) {
    val matches = remember(query, tracks) {
        tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }.sortedBy { it.title.lowercase() }
    }
    val artists = remember(query, tracks) {
        tracks.map { it.artist }.distinct().filter { it.contains(query, true) }.sorted()
    }
    val albums = remember(query, tracks) {
        tracks.map { it.album to it.artist }.distinct().filter { it.first.contains(query, true) }
            .sortedBy { it.first.lowercase() }
    }

    if (matches.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
        Text("Aucun résultat pour « $query »", Modifier.fillMaxWidth().padding(40.dp),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (artists.isNotEmpty()) {
            item { SectionTitle("Artistes") }
            items(artists, key = { "a-$it" }) { name ->
                ResultRow(Icons.Rounded.Mic, name, null) { nav.openTracks(TrackFilters.artist(name)) }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            items(albums, key = { "al-${it.first}-${it.second}" }) { (album, artist) ->
                ResultRow(Icons.Rounded.Album, album, artist) { nav.openTracks(TrackFilters.album(album, artist)) }
            }
        }
        if (matches.isNotEmpty()) {
            item { SectionTitle("Morceaux") }
            itemsIndexed(matches, key = { _, t -> "t-${t.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    vm = vm,
                    isCurrent = currentId == track.id,
                    isPlaying = isPlaying,
                    isFavorite = track.id in library.favorites,
                    onClick = { vm.play(matches, index) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// endregion
