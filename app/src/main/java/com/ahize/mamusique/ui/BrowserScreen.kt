@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahize.mamusique.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahize.mamusique.data.DownloadCenter
import kotlinx.coroutines.delay

private val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "oga")
private val videoExtensions = setOf("mp4", "m4v", "webm", "mov", "3gp")

/** Un fichier audio ou vidéo repéré sur la page (comme IDM). */
data class DetectedMedia(
    val url: String,
    val isVideo: Boolean,
    val pageTitle: String,
    val extension: String,
) {
    val label: String
        get() = pageTitle.ifBlank {
            Uri.decode(Uri.parse(url).lastPathSegment ?: "Fichier").substringBeforeLast('.')
        }
}

private fun extensionOf(url: String): String =
    MimeTypeMap.getFileExtensionFromUrl(url).lowercase().ifBlank {
        Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
    }

/** Repère les médias dans la page : lecteurs <video>/<audio> et clic sur « lecture ». */
private const val DETECTION_SCRIPT = """
(function(){
  if (window.__volkoffSound) return; window.__volkoffSound = true;
  function report(el, playing){
    try {
      var src = el.currentSrc || el.src;
      if (!src) { var s = el.querySelector('source'); if (s) src = s.src; }
      if (src && /^https?:/i.test(src)) {
        VolkoffBridge.onMedia(src, el.tagName.toLowerCase(), document.title || '', !!playing);
      }
    } catch (e) {}
  }
  document.addEventListener('play', function(e){ report(e.target, true); }, true);
  var timer = null;
  function scan(){ document.querySelectorAll('video,audio').forEach(function(el){ report(el, false); }); }
  scan();
  new MutationObserver(function(){
    if (timer) return;
    timer = setTimeout(function(){ timer = null; scan(); }, 1500);
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
"""

/** Navigateur intégré avec détection automatique des musiques et vidéos libres. */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun BrowserScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences("browser", Context.MODE_PRIVATE) }
    var homePage by remember { mutableStateOf(prefs.getString("home", "") ?: "") }

    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("Navigateur") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val detected = remember { mutableStateListOf<DetectedMedia>() }
    var banner by remember { mutableStateOf<DetectedMedia?>(null) }
    val dismissed = remember { mutableStateListOf<String>() }
    var choosing by remember { mutableStateOf<DetectedMedia?>(null) }
    var showList by remember { mutableStateOf(false) }

    fun onMediaFound(url: String, isVideo: Boolean, pageTitle: String, playing: Boolean) {
        val ext = extensionOf(url)
        if (ext == "m3u8" || ext == "mpd") return // flux en direct, non téléchargeables tels quels
        val existing = detected.firstOrNull { it.url == url }
        val media = existing ?: DetectedMedia(url, isVideo, pageTitle.trim(), ext).also { detected.add(0, it) }
        // On propose automatiquement le téléchargement (une fois par fichier)
        if ((existing == null || playing) && media.url !in dismissed && choosing == null) {
            banner = media
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMedia(url: String, tag: String, pageTitle: String, playing: Boolean) {
                    val ext = extensionOf(url)
                    val isVideo = if (ext in audioExtensions) false else tag == "video" || ext in videoExtensions
                    post { onMediaFound(url, isVideo, pageTitle, playing) }
                }
            }, "VolkoffBridge")

            fun updateNav(view: WebView) {
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val ext = extensionOf(url)
                    if (request.isForMainFrame && (ext in audioExtensions || ext in videoExtensions)) {
                        // Lien direct vers un fichier : on propose le choix du format
                        onMediaFound(url, ext in videoExtensions, view.title ?: "", true)
                        return true
                    }
                    return false
                }

                // Repère les fichiers audio/vidéo chargés en arrière-plan par la page
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    if (url.startsWith("http")) {
                        val ext = extensionOf(url)
                        if (ext in audioExtensions || ext in videoExtensions) {
                            view.post { onMediaFound(url, ext in videoExtensions, view.title ?: "", false) } // title lu sur le thread principal
                        }
                    }
                    return null
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (url != currentUrl) {
                        detected.clear()
                        banner = null
                    }
                    currentUrl = url
                    address = url
                    updateNav(view)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    address = url
                    updateNav(view)
                    view.evaluateJavascript(DETECTION_SCRIPT, null)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    currentUrl = url
                    address = url
                    updateNav(view)
                    view.evaluateJavascript(DETECTION_SCRIPT, null)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, newTitle: String?) {
                    title = newTitle?.takeIf { it.isNotBlank() } ?: "Navigateur"
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                val ext = extensionOf(url)
                val isVideo = mimeType?.startsWith("video/") == true || ext in videoExtensions
                val isAudio = mimeType?.startsWith("audio/") == true || ext in audioExtensions
                if (isVideo || isAudio) {
                    onMediaFound(url, isVideo, this.title ?: "", true)
                } else {
                    startDownload(context, url, userAgent, contentDisposition, mimeType)
                }
            }
            loadUrl(homePage.ifBlank { "https://www.google.com" })
        }
    }

    // Le bandeau disparaît tout seul après 12 secondes
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(12_000)
            banner = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }
    BackHandler(enabled = canGoBack) { webView.goBack() }

    fun go(input: String) {
        val url = toUrl(input) ?: return
        focus.clearFocus()
        webView.loadUrl(url)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fermer") }
            },
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            singleLine = true,
            placeholder = { Text("Adresse ou recherche") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go(address) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

            // Bandeau automatique « Télécharger ? »
            androidx.compose.animation.AnimatedVisibility(
                visible = banner != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                banner?.let { media ->
                    DetectionBanner(
                        media = media,
                        onDownload = { choosing = media; banner = null },
                        onDismiss = { dismissed.add(media.url); banner = null },
                    )
                }
            }

            // Bouton flottant permanent tant que des médias sont détectés
            if (detected.isNotEmpty()) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val scale by pulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "scale",
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        if (detected.size == 1) choosing = detected.first() else showList = true
                    },
                    icon = { Icon(Icons.Rounded.Download, null) },
                    text = { Text(if (detected.size == 1) "Télécharger" else "Télécharger (${detected.size})") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .scale(scale),
                )
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { webView.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Précédent")
                }
                IconButton(onClick = { webView.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Suivant")
                }
                IconButton(onClick = { webView.reload() }) {
                    Icon(Icons.Rounded.Refresh, "Recharger")
                }
                IconButton(onClick = { go(homePage.ifBlank { "https://www.google.com" }) }) {
                    Icon(Icons.Rounded.Home, "Accueil")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.Bookmarks, "Favoris")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Google Drive") },
                            leadingIcon = { Icon(Icons.Rounded.Cloud, null) },
                            onClick = { menuOpen = false; go("https://drive.google.com") },
                        )
                        DropdownMenuItem(
                            text = { Text("Musique libre (Internet Archive)") },
                            leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) },
                            onClick = { menuOpen = false; go("https://archive.org/details/audio") },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Définir cette page comme accueil") },
                            leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                            enabled = currentUrl.isNotBlank(),
                            onClick = {
                                menuOpen = false
                                homePage = currentUrl
                                prefs.edit().putString("home", currentUrl).apply()
                                Toast.makeText(context, "Page d'accueil enregistrée", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            Text(
                "Fichiers trouvés sur cette page",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(detected.toList(), key = { it.url }) { media ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showList = false; choosing = media }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (media.isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(media.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (media.isVideo) "Vidéo" else "Musique") +
                                    (media.extension.takeIf { it.isNotBlank() }?.let { " · ${it.uppercase()}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Download, null)
                    }
                }
            }
        }
    }

    choosing?.let { media ->
        FormatSheet(
            media = media,
            onDismiss = { choosing = null },
            onChoose = { mode ->
                choosing = null
                val ext = when (mode) {
                    DownloadCenter.Mode.AUDIO -> media.extension.ifBlank { "mp3" }
                    else -> media.extension.ifBlank { "mp4" }
                }
                val name = media.label.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().take(80) + ".$ext"
                val id = DownloadCenter.download(
                    context, media.url,
                    fileName = name,
                    userAgent = webView.settings.userAgentString,
                    mode = mode,
                )
                Toast.makeText(
                    context,
                    if (id != null) "Téléchargement lancé : retrouve-le dans l'onglet Télécharger" else "Téléchargement impossible",
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }
}

@Composable
private fun DetectionBanner(media: DetectedMedia, onDownload: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (media.isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote,
                    null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (media.isVideo) "Vidéo détectée" else "Musique détectée",
                    fontWeight = FontWeight.Bold,
                )
                Text(media.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onDownload, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text("Télécharger")
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Ignorer") }
        }
    }
}

/** Choix du format, avec de gros boutons faciles à toucher. */
@Composable
private fun FormatSheet(media: DetectedMedia, onDismiss: () -> Unit, onChoose: (DownloadCenter.Mode) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 36.dp)) {
            Text("Quel format veux-tu ?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(media.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))

            if (media.isVideo) {
                FormatOption(
                    icon = Icons.Rounded.MusicNote,
                    title = "Musique seulement",
                    subtitle = "Format M4A, même qualité que la vidéo · ajoutée à ta bibliothèque",
                    highlighted = true,
                ) { onChoose(DownloadCenter.Mode.EXTRACT_AUDIO) }
                Spacer(Modifier.height(12.dp))
                FormatOption(
                    icon = Icons.Rounded.Movie,
                    title = "Vidéo complète",
                    subtitle = "Format ${media.extension.ifBlank { "mp4" }.uppercase()} · enregistrée dans la Galerie",
                    highlighted = false,
                ) { onChoose(DownloadCenter.Mode.VIDEO) }
            } else {
                val format = media.extension.ifBlank { "mp3" }.uppercase()
                FormatOption(
                    icon = Icons.Rounded.MusicNote,
                    title = "Musique $format",
                    subtitle = "Ajoutée à ta bibliothèque Volkoff Sound",
                    highlighted = true,
                ) { onChoose(DownloadCenter.Mode.AUDIO) }
            }
        }
    }
}

@Composable
private fun FormatOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun toUrl(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    return when {
        text.startsWith("http://") || text.startsWith("https://") -> text
        text.contains('.') && !text.contains(' ') -> "https://$text"
        else -> "https://www.google.com/search?q=" + Uri.encode(text)
    }
}

/** Télécharge un fichier (non audio/vidéo) dans Music/MaMusique. */
fun startDownload(context: Context, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
    if (!url.startsWith("http")) {
        Toast.makeText(context, "Ce lien ne peut pas être téléchargé", Toast.LENGTH_SHORT).show()
        return
    }
    val id = DownloadCenter.download(
        context, url,
        playlistId = null,
        mimeType = mimeType,
        userAgent = userAgent,
        contentDisposition = contentDisposition,
    )
    Toast.makeText(
        context,
        if (id != null) "Téléchargement lancé" else "Téléchargement impossible",
        Toast.LENGTH_SHORT,
    ).show()
}
