package com.novastats.app.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.entity.EntityType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.UUID

/**
 * Images choisies par l'utilisateur (photo d'artiste / pochette d'album) :
 *  - 🖼️ galerie → copie dans le stockage privé de l'app ;
 *  - 🌐 web → recherche d'images lancée dans le navigateur, et au retour dans l'app la dernière image téléchargée
 *    depuis le lancement est appliquée automatiquement (nécessite l'accès aux photos) ;
 *  - partage d'une image depuis le navigateur vers NovaStats (« Partager l'image ») → appliquée à la cible en attente ;
 *  - 🔑 mots-clés par entité pour orienter la recherche web et les propositions des APIs.
 */
object UserImages {
    private const val PREFS = "nova_user_images"
    private const val PENDING_TTL_MS = 30 * 60 * 1000L

    /** Incrémenté après chaque changement d'image → les popups ouverts rechargent leur entité. */
    val version = MutableStateFlow(0)
    private val imageMutex = Mutex()

    data class Pending(val type: String, val id: Long, val name: String, val launchedAt: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Mots-clés ----------
    fun keywords(ctx: Context, type: String, id: Long): String = prefs(ctx).getString("kw:$type:$id", "") ?: ""
    fun setKeywords(ctx: Context, type: String, id: Long, value: String) {
        prefs(ctx).edit().apply { if (value.isBlank()) remove("kw:$type:$id") else putString("kw:$type:$id", value.trim()) }.apply()
    }

    // ---------- Cible en attente (recherche web / partage) ----------
    fun pending(ctx: Context): Pending? {
        val p = prefs(ctx)
        val type = p.getString("p_type", null) ?: return null
        val at = p.getLong("p_at", 0L)
        if (System.currentTimeMillis() - at > PENDING_TTL_MS) { clearPending(ctx); return null }
        return Pending(type, p.getLong("p_id", 0L), p.getString("p_name", "") ?: "", at)
    }

    fun clearPending(ctx: Context) { prefs(ctx).edit().remove("p_type").remove("p_id").remove("p_name").remove("p_at").apply() }

    private fun setPending(ctx: Context, type: String, id: Long, name: String) {
        prefs(ctx).edit().putString("p_type", type).putLong("p_id", id).putString("p_name", name).putLong("p_at", System.currentTimeMillis()).apply()
    }

    /** Permission de lecture des images (pour détecter le téléchargement au retour du navigateur). */
    val readImagesPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canReadImages(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, readImagesPermission) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)

    /** Requête de recherche d'images : nom (+ artiste pour un album) + mots-clés de l'utilisateur. */
    fun webQuery(type: String, name: String, artist: String?, keywords: String): String {
        val base = if (type == EntityType.ALBUM) listOfNotNull(artist, name, "album cover") else listOf(name, "photo")
        return (base + keywords.split(' ').filter { it.isNotBlank() }).joinToString(" ")
    }

    /** Ouvre le navigateur sur une recherche Google Images et mémorise la cible pour le retour. */
    fun openWebSearch(ctx: Context, type: String, id: Long, name: String, query: String): Boolean {
        setPending(ctx, type, id, name)
        val url = "https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            clearPending(ctx)
            false
        } catch (_: SecurityException) {
            clearPending(ctx)
            false
        }
    }

    // ---------- Import / application ----------
    /** Copie une image (galerie, partage, téléchargement) dans le stockage privé ; renvoie une URL file:// stable. */
    suspend fun importUri(ctx: Context, uri: Uri, prefix: String): String? = withContext(Dispatchers.IO) {
        var out: File? = null
        try {
            val dir = File(ctx.filesDir, "images").apply { mkdirs() }
            if (!dir.isDirectory) return@withContext null
            val safePrefix = prefix.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(60).ifBlank { "image" }
            val outputFile = File(dir, "${safePrefix}_${UUID.randomUUID()}.img")
            out = outputFile
            val input = ctx.contentResolver.openInputStream(uri) ?: run {
                outputFile.delete()
                return@withContext null
            }
            input.use { source ->
                outputFile.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 25L * 1024 * 1024) throw java.io.IOException("Image trop volumineuse (maximum 25 Mo)")
                        target.write(buffer, 0, count)
                    }
                }
            }
            if (outputFile.length() < 1024) { outputFile.delete(); null } else Uri.fromFile(outputFile).toString()
        } catch (cancelled: CancellationException) {
            out?.delete()
            throw cancelled
        } catch (_: Exception) {
            out?.delete()
            null
        }
    }

    /** Applique une URL (file:// ou http) à l'artiste / l'album, en 👤 USER ; supprime l'ancien fichier privé s'il y en avait un. */
    suspend fun apply(app: NovaStatsApp, type: String, id: Long, url: String): Boolean = imageMutex.withLock {
        if (type != EntityType.ARTIST && type != EntityType.ALBUM) return@withLock false
        val cleanedUrl = url.takeIf(String::isNotBlank) ?: return@withLock false
        val old = when (type) {
            EntityType.ARTIST -> app.database.artistDao().getById(id)?.photoUrl
            else -> app.database.albumDao().getById(id)?.coverUrl
        }
        try {
            if (type == EntityType.ARTIST) app.editor.setArtistPhoto(id, cleanedUrl) else app.editor.setAlbumCover(id, cleanedUrl)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { cleanupIfUnreferenced(app, cleanedUrl) }
            throw cancelled
        } catch (_: Exception) {
            cleanupIfUnreferenced(app, cleanedUrl)
            return@withLock false
        }
        if (old != null && old.startsWith("file://") && old != cleanedUrl) cleanupIfUnreferenced(app, old)
        version.value = version.value + 1
        true
    }

    private suspend fun cleanupIfUnreferenced(app: NovaStatsApp, url: String) {
        if (!url.startsWith("file://")) return
        val db = app.database
        val references = try {
            db.artistDao().photoReferenceCount(url) + db.albumDao().coverReferenceCount(url) + db.trackDao().coverReferenceCount(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return // En cas de doute, garder le fichier plutôt que casser une image encore référencée.
        }
        if (references == 0) deletePrivateImage(app, url)
    }

    /** Ne supprime que les fichiers `.img` créés dans le répertoire privé de l'app, jamais un chemin file:// arbitraire. */
    private fun deletePrivateImage(ctx: Context, url: String) {
        try {
            val uri = Uri.parse(url)
            if (uri.scheme != "file") return
            val path = uri.path ?: return
            val imageDir = File(ctx.filesDir, "images").canonicalFile
            val file = File(path).canonicalFile
            if (file.isFile && file.extension == "img" && file.parentFile == imageDir) file.delete()
        } catch (_: java.io.IOException) {
            // Nettoyage opportuniste : une erreur de fichier ne doit pas invalider le changement déjà sauvegardé.
        } catch (_: SecurityException) {
            // Même principe pour un stockage temporairement inaccessible.
        }
    }

    /**
     * Retour dans l'app après une recherche web : la dernière image ajoutée à la galerie / aux téléchargements depuis le
     * lancement est appliquée à la cible en attente. Renvoie un message à afficher, ou null si rien à faire.
     */
    suspend fun checkDownloaded(app: NovaStatsApp): String? {
        val p = pending(app) ?: return null
        if (!canReadImages(app)) return null
        val uri = withContext(Dispatchers.IO) { newestImageSince(app, p.launchedAt / 1000) } ?: return null
        val url = importUri(app, uri, "${p.type.lowercase()}_${p.id}") ?: return "⚠️ Image téléchargée illisible"
        val applied = apply(app, p.type, p.id, url)
        if (applied) clearPending(app)
        return if (applied) "📷 ${if (p.type == EntityType.ARTIST) "Photo" else "Pochette"} de ${p.name} remplacée par l'image téléchargée"
        else "⚠️ Impossible d'appliquer l'image à ${p.name}"
    }

    private fun newestImageSince(ctx: Context, sinceSeconds: Long): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = arrayOf(sinceSeconds.toString())
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        return runCatching {
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, sort)?.use { c ->
                if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null
            }
        }.getOrNull()
    }

    /** Image partagée vers NovaStats (« Partager l'image » depuis le navigateur / la galerie). */
    suspend fun handleShare(app: NovaStatsApp, intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        intent.action = null // consommé
        val p = pending(app) ?: return "ℹ️ Ouvre d'abord un artiste ou un album, appuie sur 🌐, puis partage l'image vers NovaStats"
        @Suppress("DEPRECATION")
        val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        val url = when {
            stream != null -> importUri(app, stream, "${p.type.lowercase()}_${p.id}")
            text != null && text.startsWith("http") && !text.contains(' ') -> text
            else -> null
        } ?: return "⚠️ Image partagée illisible"
        val applied = apply(app, p.type, p.id, url)
        if (applied) clearPending(app)
        return if (applied) "📷 ${if (p.type == EntityType.ARTIST) "Photo" else "Pochette"} de ${p.name} remplacée" else "⚠️ Impossible d'appliquer l'image à ${p.name}"
    }
}
