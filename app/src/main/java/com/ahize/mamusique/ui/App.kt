@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahize.mamusique.ui

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ahize.mamusique.MusicViewModel
import com.ahize.mamusique.data.Track

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("home", "Accueil", Icons.Rounded.Home),
    TabItem("library", "Bibliothèque", Icons.Rounded.LibraryMusic),
    TabItem("search", "Recherche", Icons.Rounded.Search),
)

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun MaMusiqueApp(vm: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[audioPermission()] == true
        if (hasPermission) vm.refresh()
    }

    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(audioPermission())
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    // Rescan à chaque retour dans l'app (nouveaux fichiers ajoutés ailleurs)
    LifecycleResumeEffect(hasPermission) {
        if (hasPermission) vm.refresh()
        onPauseOrDispose { }
    }

    // Fin d'un téléchargement lancé depuis le navigateur intégré
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                onDownloadComplete(ctx, id) { vm.refresh() }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Suppression d'un fichier : Android demande une confirmation
    var pendingDelete by remember { mutableStateOf<Track?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val track = pendingDelete
        if (result.resultCode == android.app.Activity.RESULT_OK && track != null) {
            vm.onTrackDeleted(track.id)
        }
        pendingDelete = null
    }
    val deleteTrack: (Track) -> Unit = { track ->
        pendingDelete = track
        try {
            val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(track.uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (e: Exception) {
            Toast.makeText(context, "Suppression impossible", Toast.LENGTH_SHORT).show()
        }
    }

    if (!hasPermission) {
        PermissionScreen { permissionLauncher.launch(arrayOf(audioPermission())) }
        return
    }

    CompositionLocalProvider(LocalDeleteTrack provides deleteTrack) {
        MainScaffold(vm)
    }
}

private fun onDownloadComplete(context: Context, downloadId: Long, onScanned: () -> Unit) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
        if (!c.moveToFirst()) return
        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        if (status != DownloadManager.STATUS_SUCCESSFUL || localUri == null) {
            Toast.makeText(context, "Échec du téléchargement", Toast.LENGTH_SHORT).show()
            return
        }
        val path = Uri.parse(localUri).path ?: return
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ ->
            android.os.Handler(context.mainLooper).post {
                onScanned()
                Toast.makeText(context, "Ajouté à ta bibliothèque", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("Accès à ta musique", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ma Musique a besoin d'accéder aux fichiers audio de ton téléphone pour les lire.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Autoriser") }
    }
}

@Composable
private fun MainScaffold(vm: MusicViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val playerState by vm.player.collectAsState()
    var showPlayer by remember { mutableStateOf(false) }
    val inBrowser = route == "browser"
    var currentTab by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(route) {
        if (tabs.any { it.route == route }) currentTab = route ?: "home"
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!inBrowser) {
                    Column {
                        if (playerState.currentId != null) {
                            MiniPlayer(vm, onOpen = { showPlayer = true })
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            tabs.forEach { tab ->
                                val selected = currentTab == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        currentTab = tab.route
                                        nav.navigate(tab.route) {
                                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, tab.label) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(padding),
            ) {
                composable("home") { HomeScreen(vm, nav) }
                composable("library") { LibraryScreen(vm, nav) }
                composable("search") { SearchScreen(vm, nav) }
                composable("tracks/{key}") { entry ->
                    TracksScreen(vm, nav, Uri.decode(entry.arguments?.getString("key") ?: "all"))
                }
                composable("artists") { ArtistsScreen(vm, nav) }
                composable("albums") { AlbumsScreen(vm, nav) }
                composable("playlists") { PlaylistsScreen(vm, nav) }
                composable("playlist/{id}") { entry ->
                    PlaylistDetailScreen(vm, nav, entry.arguments?.getString("id") ?: "")
                }
                composable("browser") { BrowserScreen(onClose = { nav.popBackStack() }) }
            }
        }

        AnimatedVisibility(
            visible = showPlayer && playerState.currentId != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(vm, onClose = { showPlayer = false })
        }
    }
    BackHandler(enabled = showPlayer) { showPlayer = false }
}

fun NavHostController.openTracks(key: String) = navigate("tracks/${Uri.encode(key)}")
