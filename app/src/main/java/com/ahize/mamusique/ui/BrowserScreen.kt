@file:OptIn(ExperimentalMaterial3Api::class)

package com.ahize.mamusique.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val downloadableExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

/** Navigateur intégré : les fichiers audio sont envoyés dans Music/MaMusique. */
@SuppressLint("SetJavaScriptEnabled")
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

            fun updateNav(view: WebView) {
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val ext = MimeTypeMap.getFileExtensionFromUrl(url).lowercase()
                    if (ext in downloadableExtensions) {
                        startDownload(context, url, settings.userAgentString, null, null)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    currentUrl = url
                    address = url
                    updateNav(view)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    address = url
                    updateNav(view)
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    currentUrl = url
                    address = url
                    updateNav(view)
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
                startDownload(context, url, userAgent, contentDisposition, mimeType)
            }
            loadUrl(homePage.ifBlank { "https://drive.google.com" })
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

        AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())

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
                IconButton(onClick = {
                    if (currentUrl.isNotBlank()) {
                        startDownload(context, currentUrl, webView.settings.userAgentString, null, null)
                    }
                }) {
                    Icon(Icons.Rounded.Download, "Télécharger cette page")
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
                        if (homePage.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Ma page d'accueil") },
                                leadingIcon = { Icon(Icons.Rounded.Home, null) },
                                onClick = { menuOpen = false; go(homePage) },
                            )
                        }
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

/** Télécharge un fichier dans Music/MaMusique (visible ensuite dans la bibliothèque). */
fun startDownload(context: Context, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
    if (!url.startsWith("http")) {
        Toast.makeText(context, "Ce lien ne peut pas être téléchargé", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            mimeType?.let { setMimeType(it) }
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            userAgent?.let { addRequestHeader("User-Agent", it) }
            setTitle(fileName)
            setDescription("Ma Musique")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "MaMusique/$fileName")
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Téléchargement : $fileName", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Téléchargement impossible : ${e.message}", Toast.LENGTH_LONG).show()
    }
}
