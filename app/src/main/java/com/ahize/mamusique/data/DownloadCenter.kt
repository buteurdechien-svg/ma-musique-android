package com.ahize.mamusique.data

import android.app.DownloadManager
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Téléchargeur intégré :
 * - analyse un dossier Google Drive, une page web ou un fichier .m3u
 * - télécharge les fichiers audio dans Music/MaMusique
 * - les ajoute à une playlist une fois le téléchargement terminé
 */
object DownloadCenter {

    val audioExtensions = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
    private val playlistExtensions = setOf("m3u", "m3u8", "pls")
    private val streamingHosts = listOf(
        "youtube.com", "youtu.be", "spotify.com", "deezer.com",
        "music.apple.com", "soundcloud.com", "tidal.com", "boomplay.com",
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    const val STREAMING_MESSAGE =
        "Les plateformes de streaming (YouTube, Spotify, Deezer…) ne permettent pas de télécharger leurs morceaux. " +
            "Mets tes fichiers dans un dossier Google Drive ou sur ton site, puis colle ce lien ici."

    data class RemoteFile(val url: String, val name: String)

    sealed class Analysis {
        data class Found(val title: String, val files: List<RemoteFile>) : Analysis()
        data class Error(val message: String) : Analysis()
    }

    data class DownloadStatus(
        val id: Long,
        val title: String,
        val progress: Float,
        val status: Int,
    )

    // region Analyse d'un lien

    suspend fun analyze(input: String): Analysis = withContext(Dispatchers.IO) {
        try {
            analyzeBlocking(input)
        } catch (e: Exception) {
            Analysis.Error("Impossible de lire ce lien (${e.message ?: e.javaClass.simpleName}).")
        }
    }

    private fun analyzeBlocking(input: String): Analysis {
        val text = input.trim()
        if (text.isEmpty()) return Analysis.Error("Colle d'abord un lien.")
        val url = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        val host = Uri.parse(url).host?.lowercase().orEmpty()

        if (streamingHosts.any { host == it || host.endsWith(".$it") }) {
            return Analysis.Error(STREAMING_MESSAGE)
        }
        return if (host.endsWith("drive.google.com")) analyzeDrive(url) else analyzeWeb(url)
    }

    private fun analyzeDrive(url: String): Analysis {
        // Fichier unique : https://drive.google.com/file/d/ID/view
        Regex("/file/d/([\\w-]+)").find(url)?.let { match ->
            val id = match.groupValues[1]
            val page = runCatching { fetch("https://drive.google.com/file/d/$id/view") }.getOrDefault("")
            val name = pageTitle(page)?.removeSuffix(" - Google Drive")?.takeIf { it.isNotBlank() } ?: "$id.mp3"
            return Analysis.Found("Google Drive", listOf(RemoteFile(driveDownloadUrl(id), name)))
        }

        val folderId = Regex("/folders/([\\w-]+)").find(url)?.groupValues?.get(1)
            ?: Uri.parse(url).getQueryParameter("id")
            ?: return Analysis.Error("Lien Drive non reconnu. Utilise le lien de partage d'un dossier.")

        val html = fetch("https://drive.google.com/embeddedfolderview?id=$folderId")
        val title = pageTitle(html)?.removeSuffix(" - Google Drive")?.takeIf { it.isNotBlank() } ?: "Dossier Drive"
        val entries = Regex("""id="entry-([\w-]+)"[\s\S]*?class="flip-entry-title">([^<]*)<""")
            .findAll(html)
            .map { it.groupValues[1] to unescape(it.groupValues[2]).trim() }
            .toList()

        val audio = entries
            .filter { (_, name) -> name.substringAfterLast('.', "").lowercase() in audioExtensions }
            .map { (id, name) -> RemoteFile(driveDownloadUrl(id), name) }

        if (audio.isEmpty()) {
            return Analysis.Error(
                if (entries.isEmpty())
                    "Dossier vide ou privé. Partage-le avec « Tous les utilisateurs disposant du lien », " +
                        "ou connecte-toi à Google une fois dans le navigateur de l'app."
                else "Aucun fichier audio (MP3, M4A…) dans ce dossier. Les sous-dossiers ne sont pas parcourus."
            )
        }
        return Analysis.Found(title, audio)
    }

    private fun driveDownloadUrl(id: String) =
        "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"

    private fun analyzeWeb(url: String): Analysis {
        val path = Uri.parse(url).lastPathSegment.orEmpty()
        val ext = path.substringAfterLast('.', "").lowercase()

        // Lien direct vers un fichier audio
        if (ext in audioExtensions) {
            return Analysis.Found("Téléchargements", listOf(RemoteFile(url, fileNameFrom(url))))
        }

        val body = fetch(url)
        val trimmed = body.trimStart()

        // Fichier de playlist .m3u / .pls
        if (ext in playlistExtensions || trimmed.startsWith("#EXTM3U") || trimmed.startsWith("[playlist]", true)) {
            val links = body.lines().map { it.trim() }.mapNotNull { line ->
                when {
                    line.isEmpty() || line.startsWith("#") || line.startsWith("[") -> null
                    line.startsWith("File", ignoreCase = true) && line.contains('=') -> line.substringAfter('=')
                    line.contains('=') -> null
                    else -> line
                }
            }
            val files = links.mapNotNull { resolve(url, it) }.distinct().map { RemoteFile(it, fileNameFrom(it)) }
            if (files.isEmpty()) return Analysis.Error("Cette playlist ne contient aucun lien.")
            return Analysis.Found(path.substringBeforeLast('.').ifBlank { "Playlist" }, files)
        }

        // Page web : on récupère tous les liens vers des fichiers audio
        val files = Regex("""(?:href|src)\s*=\s*["']([^"'#]+)["']""", RegexOption.IGNORE_CASE)
            .findAll(body)
            .mapNotNull { resolve(url, unescape(it.groupValues[1])) }
            .filter { link ->
                Uri.parse(link).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase() in audioExtensions
            }
            .distinct()
            .map { RemoteFile(it, fileNameFrom(it)) }
            .toList()

        if (files.isEmpty()) {
            return Analysis.Error("Aucun lien vers un fichier audio trouvé sur cette page.")
        }
        return Analysis.Found(pageTitle(body)?.takeIf { it.isNotBlank() } ?: "Téléchargements", files)
    }

    private fun fetch(url: String): String {
        var current = url
        repeat(5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", USER_AGENT)
                CookieManager.getInstance().getCookie(current)?.let { setRequestProperty("Cookie", it) }
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: error("redirection invalide")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code >= 400) error("erreur $code")
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        error("trop de redirections")
    }

    private fun pageTitle(html: String): String? =
        Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?.let { unescape(it).trim() }

    private fun resolve(base: String, link: String): String? =
        runCatching { URL(URL(base), link.trim()).toString() }.getOrNull()
            ?.takeIf { it.startsWith("http") }

    private fun fileNameFrom(url: String): String =
        Uri.decode(Uri.parse(url).lastPathSegment ?: "piste.mp3").substringAfterLast('/')

    @Suppress("DEPRECATION")
    private fun unescape(text: String): String = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()

    // endregion

    // region Téléchargement

    /** Ce qu'on garde du fichier téléchargé. */
    enum class Mode {
        /** Fichier audio tel quel (MP3, M4A…) → bibliothèque */
        AUDIO,
        /** Vidéo complète → Films/Volkoff Sound (Galerie) */
        VIDEO,
        /** Vidéo téléchargée puis son extrait (M4A) → bibliothèque */
        EXTRACT_AUDIO,
    }

    /** Lance un téléchargement. Retourne l'identifiant DownloadManager, ou null en cas d'échec. */
    fun download(
        context: Context,
        url: String,
        fileName: String? = null,
        playlistId: String? = null,
        mimeType: String? = null,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mode: Mode = Mode.AUDIO,
    ): Long? {
        return try {
            val name = (fileName ?: URLUtil.guessFileName(url, contentDisposition, mimeType))
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(120)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                mimeType?.let { setMimeType(it) }
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                addRequestHeader("User-Agent", userAgent ?: USER_AGENT)
                setTitle(name)
                setDescription("Volkoff Sound")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                when (mode) {
                    Mode.AUDIO -> setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "MaMusique/$name")
                    Mode.VIDEO -> setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Volkoff Sound/$name")
                    Mode.EXTRACT_AUDIO -> setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, name)
                }
            }
            val id = manager(context).enqueue(request)
            val prefs = prefs(context)
            val pending = JSONObject(prefs.getString("pending", "{}") ?: "{}")
            pending.put(id.toString(), JSONObject().put("p", playlistId ?: "").put("m", mode.name))
            val history = JSONArray(prefs.getString("history", "[]") ?: "[]")
            history.put(id)
            val trimmed = JSONArray()
            val start = (history.length() - 150).coerceAtLeast(0)
            for (i in start until history.length()) trimmed.put(history.getLong(i))
            prefs.edit()
                .putString("pending", pending.toString())
                .putString("history", trimmed.toString())
                .apply()
            id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Traite les téléchargements terminés : indexation par Android,
     * extraction du son des vidéos, ajout à la playlist.
     */
    fun processPending(
        context: Context,
        onTrackReady: (playlistId: String?, trackId: Long) -> Unit,
        onBatchDone: (done: Int, failed: Int) -> Unit,
    ) {
        val prefs = prefs(context)
        val pending = JSONObject(prefs.getString("pending", "{}") ?: "{}")
        if (pending.length() == 0) return
        val main = Handler(Looper.getMainLooper())
        var done = 0
        var failed = 0

        pending.keys().asSequence().toList().forEach { key ->
            val downloadId = key.toLongOrNull()
            val entry = pending.optJSONObject(key)
            val playlistId = (entry?.optString("p") ?: pending.optString(key)).ifBlank { null }
            val mode = runCatching { Mode.valueOf(entry?.optString("m") ?: "AUDIO") }.getOrDefault(Mode.AUDIO)
            if (downloadId == null) {
                pending.remove(key)
                return@forEach
            }
            val cursor = manager(context).query(DownloadManager.Query().setFilterById(downloadId))
            cursor.use { c ->
                if (c == null || !c.moveToFirst()) {
                    pending.remove(key)
                    return@forEach
                }
                when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        pending.remove(key)
                        val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        val path = localUri?.let { Uri.parse(it).path } ?: return@forEach
                        done++
                        when (mode) {
                            Mode.AUDIO -> MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri ->
                                val trackId = uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
                                if (trackId != null) main.post { onTrackReady(playlistId, trackId) }
                            }
                            Mode.VIDEO -> MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                            Mode.EXTRACT_AUDIO -> Thread {
                                val uri = runCatching { AudioExtractor.extract(context, File(path)) }.getOrNull()
                                File(path).delete()
                                main.post {
                                    if (uri != null) {
                                        onTrackReady(playlistId, ContentUris.parseId(uri))
                                    } else {
                                        Toast.makeText(context, "Impossible d'extraire le son de cette vidéo", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.start()
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        pending.remove(key)
                        failed++
                    }
                    else -> Unit // encore en cours
                }
            }
        }
        prefs.edit().putString("pending", pending.toString()).apply()
        if (done > 0 || failed > 0) onBatchDone(done, failed)
    }

    /** État des téléchargements récents, pour l'écran Télécharger. */
    fun recent(context: Context): List<DownloadStatus> {
        val history = JSONArray(prefs(context).getString("history", "[]") ?: "[]")
        if (history.length() == 0) return emptyList()
        val ids = LongArray(history.length()) { history.getLong(it) }
        val result = mutableListOf<DownloadStatus>()
        manager(context).query(DownloadManager.Query().setFilterById(*ids))?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            while (c.moveToNext()) {
                val total = c.getLong(totalCol)
                result += DownloadStatus(
                    id = c.getLong(idCol),
                    title = c.getString(titleCol) ?: "",
                    progress = if (total > 0) c.getLong(doneCol).toFloat() / total else 0f,
                    status = c.getInt(statusCol),
                )
            }
        }
        return result.sortedByDescending { it.id }
    }

    fun clearFinished(context: Context) {
        val active = recent(context).filter {
            it.status == DownloadManager.STATUS_RUNNING ||
                it.status == DownloadManager.STATUS_PENDING ||
                it.status == DownloadManager.STATUS_PAUSED
        }
        prefs(context).edit().putString("history", JSONArray(active.map { it.id }).toString()).apply()
    }

    private fun manager(context: Context) =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun prefs(context: Context) =
        context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    // endregion
}
