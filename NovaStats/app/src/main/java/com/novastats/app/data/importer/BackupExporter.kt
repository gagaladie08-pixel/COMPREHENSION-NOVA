package com.novastats.app.data.importer

import android.os.Build
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.ScrobbleStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 📤 Export JSON NovaStats v2 — rétro-compatible v1 : les clés `songs` / `plays` sont exactement celles du format
 * legacy (donc ré-importables par [LegacyBackupImporter]), enrichies des artistes, albums, certifications,
 * Panthéon, Hall of Fame et de l'historique d'édition.
 */
@Serializable
data class NovaExport(
    val nova_version: String = FORMAT_VERSION,
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val export_date: String,
    val device: String,
    val app_version: String,
    val songs: List<LegacySong>,
    val plays: List<LegacyPlay>,
    val artists: List<ExportArtist> = emptyList(),
    val albums: List<ExportAlbum> = emptyList(),
    val certifications: List<ExportCertification> = emptyList(),
    val pantheon: List<ExportPantheon> = emptyList(),
    val hall_of_fame: List<ExportHof> = emptyList(),
    val edit_history: List<ExportEdit> = emptyList(),
    val artist_exceptions: List<String> = emptyList(),
    val album_overrides: List<ExportAlbumOverride> = emptyList()
) { companion object { const val FORMAT_VERSION = "2.2" } }

@Serializable data class ExportArtist(val id: Long, val name: String, val photoUrl: String? = null, val playCount: Int = 0)
@Serializable data class ExportAlbum(val id: Long, val title: String, val artistId: Long? = null, val coverUrl: String? = null, val playCount: Int = 0)
/** Marquage manuel « album partagé » (shared = true) / « album normal » (false) — correction ALBUM_SHARED. */
@Serializable data class ExportAlbumOverride(val title: String, val shared: Boolean)
@Serializable data class ExportCertification(val entityId: Long, val entityType: String, val level: String, val multiplier: Int, val certifiedAt: Long)
@Serializable data class ExportPantheon(val artistId: Long, val status: String, val statusDate: Long)
@Serializable data class ExportHof(val entityId: Long, val entityType: String, val periodType: String, val entryType: String, val entryDate: String)
@Serializable data class ExportEdit(val type: String, val entityType: String, val entityId: Long, val before: String? = null, val after: String? = null, val createdAt: Long)

data class ExportSummary(val songs: Int, val plays: Int, val artists: Int, val albums: Int, val bytes: Int)

object BackupExporter {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun fileName(): String = "nova_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"

    suspend fun build(db: NovaDatabase, appVersion: String): Pair<String, ExportSummary> {
        val tracks = db.trackDao().allPlayed()
        val artists = db.artistDao().allPlayedList()
        val artistName = artists.associate { it.artistId to it.name }
        val albums = db.albumDao().all()
        val albumTitle = albums.associate { it.albumId to it.title }
        val featured = db.trackLinkDao().allTrackArtists().groupBy { it.trackId }

        val songs = tracks.map { t ->
            val ids = featured[t.trackId].orEmpty().sortedByDescending { it.isPrimary }.map { it.artistId }.ifEmpty { listOf(t.artistId) }
            LegacySong(
                id = t.trackId, title = t.title,
                artistNames = ids.mapNotNull { artistName[it] }.distinct().joinToString(", "),
                albumName = t.albumId?.let { albumTitle[it] }, duration = t.durationMs, genre = t.genre
            )
        }
        val plays = db.scrobbleDao().allConfirmedOrdered().filter { it.status == ScrobbleStatus.CONFIRMED }
            .map { LegacyPlay(songId = it.trackId, playedAt = it.startedAt, listenedDuration = it.durationListenedMs) }

        val export = NovaExport(
            export_date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
            device = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})",
            app_version = appVersion,
            songs = songs, plays = plays,
            artists = artists.map { ExportArtist(it.artistId, it.name, it.photoUrl, it.playCount) },
            albums = albums.map { ExportAlbum(it.albumId, it.title, it.artistId, it.coverUrl, it.playCount) },
            certifications = db.certificationDao().allCurrent().map { ExportCertification(it.entityId, it.entityType, it.level, it.multiplier, it.certifiedAt) },
            pantheon = db.pantheonDao().allCurrent().map { ExportPantheon(it.artistId, it.currentStatus, it.statusDate) },
            hall_of_fame = db.hallOfFameDao().all().map { ExportHof(it.entityId, it.entityType, it.periodType, it.entryType, it.entryDate) },
            edit_history = db.editorDao().historyList().map { ExportEdit(it.type, it.entityType, it.entityId, it.before, it.after, it.createdAt) },
            artist_exceptions = db.artistExceptionDao().allList().map { it.name },
            album_overrides = db.editorDao().correctionsOfType(com.novastats.app.data.repository.LibraryRepository.CORRECTION_ALBUM_SHARED).map { ExportAlbumOverride(it.originalValue, it.correctedValue == "1") }
        )
        val text = json.encodeToString(NovaExport.serializer(), export)
        return text to ExportSummary(songs.size, plays.size, artists.size, albums.size, text.toByteArray().size)
    }

    /** Lit `nova_version` d'un fichier (null = format v1 legacy). */
    fun formatVersion(text: String): String? = runCatching {
        (json.parseToJsonElement(text) as? JsonObject)?.get("nova_version")?.jsonPrimitive?.content
    }.getOrNull()

    /** Vrai si le fichier vient d'une version de NovaStats plus récente que celle-ci. */
    fun isNewerThanSupported(version: String?): Boolean {
        if (version == null) return false
        val a = version.split('.').mapNotNull { it.toIntOrNull() }
        val b = NovaExport.FORMAT_VERSION.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
