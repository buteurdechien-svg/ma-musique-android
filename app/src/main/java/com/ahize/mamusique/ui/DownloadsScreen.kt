@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahize.mamusique.ui

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ahize.mamusique.MusicViewModel
import com.ahize.mamusique.data.DownloadCenter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(vm: MusicViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var link by rememberSaveable { mutableStateOf("") }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<DownloadCenter.Analysis.Found?>(null) }
    val selected = remember { mutableStateListOf<Int>() }
    var playlistName by remember { mutableStateOf("") }
    var createPlaylist by remember { mutableStateOf(true) }
    var recent by remember { mutableStateOf(DownloadCenter.recent(context)) }

    // Rafraîchit la progression des téléchargements
    LaunchedEffect(Unit) {
        while (true) {
            recent = DownloadCenter.recent(context)
            delay(1000)
        }
    }

    fun analyze() {
        focus.clearFocus()
        analyzing = true
        error = null
        found = null
        scope.launch {
            when (val result = DownloadCenter.analyze(link)) {
                is DownloadCenter.Analysis.Found -> {
                    found = result
                    selected.clear()
                    selected.addAll(result.files.indices)
                    playlistName = result.title
                }
                is DownloadCenter.Analysis.Error -> error = result.message
            }
            analyzing = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { LargeTitle("Télécharger") }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("Lien Drive, page de ton site ou .m3u") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        if (link.isEmpty()) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.let { link = it.toString().trim() }
                            }) { Icon(Icons.Rounded.ContentPaste, "Coller") }
                        } else {
                            IconButton(onClick = { link = ""; found = null; error = null }) {
                                Icon(Icons.Rounded.Close, "Effacer")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { analyze() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { analyze() },
                    enabled = link.isNotBlank() && !analyzing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (analyzing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Recherche des fichiers…")
                    } else {
                        Icon(Icons.Rounded.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Trouver les morceaux", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Fonctionne avec un dossier Google Drive partagé, une page de ton site qui contient des liens MP3, " +
                        "ou un fichier de playlist .m3u.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        error?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(10.dp))
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        found?.let { result ->
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                    Text(
                        "${result.files.size} morceau${if (result.files.size > 1) "x" else ""} trouvé${if (result.files.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = createPlaylist, onCheckedChange = { createPlaylist = it })
                        Text("Créer une playlist", Modifier.clickable { createPlaylist = !createPlaylist })
                    }
                    if (createPlaylist) {
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text("Nom de la playlist") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (selected.size == result.files.size) selected.clear()
                                else { selected.clear(); selected.addAll(result.files.indices) }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (selected.size == result.files.size) "Tout décocher" else "Tout cocher")
                        }
                        Button(
                            onClick = {
                                val playlistId = if (createPlaylist && playlistName.isNotBlank()) {
                                    vm.createPlaylist(playlistName.trim())
                                } else null
                                var started = 0
                                selected.sorted().forEach { index ->
                                    val file = result.files[index]
                                    if (DownloadCenter.download(context, file.url, file.name, playlistId) != null) started++
                                }
                                Toast.makeText(context, "$started téléchargement(s) lancé(s)", Toast.LENGTH_SHORT).show()
                                found = null
                                link = ""
                                recent = DownloadCenter.recent(context)
                            },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Télécharger (${selected.size})")
                        }
                    }
                }
            }
            itemsIndexed(result.files) { index, file ->
                val checked = index in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (checked) selected.remove(index) else selected.add(index) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { if (it) selected.add(index) else selected.remove(index) })
                    Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Besoin de te connecter ou de choisir un fichier à la main ?",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { nav.navigate("browser") }) { Text("Navigateur") }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Téléchargements récents", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        DownloadCenter.clearFinished(context)
                        recent = DownloadCenter.recent(context)
                    }) { Text("Effacer") }
                }
            }
            items(recent, key = { it.id }) { item ->
                DownloadRow(item)
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadCenter.DownloadStatus) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint) = when (item.status) {
            DownloadManager.STATUS_SUCCESSFUL -> Icons.Rounded.CheckCircle to Color(0xFF34C759)
            DownloadManager.STATUS_FAILED -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
            DownloadManager.STATUS_PAUSED -> Icons.Rounded.PauseCircle to MaterialTheme.colorScheme.onSurfaceVariant
            else -> Icons.Rounded.Downloading to MaterialTheme.colorScheme.primary
        }
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> Text("Terminé", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                DownloadManager.STATUS_FAILED -> Text("Échec : vérifie que le fichier est partagé publiquement",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                DownloadManager.STATUS_PAUSED -> Text("En pause (réseau)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    Spacer(Modifier.height(6.dp))
                    if (item.progress > 0f) {
                        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
