package com.novastats.app.data.importer

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.MigrationLogEntity
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.data.db.entity.ScrobbleStatus
import com.novastats.app.data.repository.LibraryRepository
import com.novastats.app.data.repository.StatsRebuilder
import com.novastats.app.domain.ScrobbleRules
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Format "legacy" v1 (ex. nova_backup_20260707_1500.json) :
 * {
 *   "version": 1, "exportedAt": 1783436424515,
 *   "songs": [{ "id", "title", "artistNames", "albumName", "duration", "genre" }],
 *   "plays": [{ "songId", "playedAt", "listenedDuration" }]
 * }
 */
@Serializable
data class LegacyBackup(
    val version: Int = 1,
    val exportedAt: Long? = null,
    val songs: List<LegacySong> = emptyList(),
    val plays: List<LegacyPlay> = emptyList()
)

@Serializable
data class LegacySong(
    val id: Long,
    val title: String,
    val artistNames: String = "",
    val albumName: String? = null,
    val duration: Long? = null,
    val genre: String? = null
)

@Serializable
data class LegacyPlay(
    val songId: Long,
    val playedAt: Long,
    val listenedDuration: Long = 0
)

data class ImportReport(
    val songsInFile: Int,
    val playsInFile: Int,
    val playsImported: Int,
    val playsIgnoredDuplicates: Int,
    val playsBelowThreshold: Int,
    val unknownSongRefs: Int,
    val durationMs: Long
) {
    fun summary(): String = buildString {
        appendLine("✅ Import terminé en ${durationMs / 1000}s")
        appendLine("• $songsInFile titres dans le fichier")
        appendLine("• $playsImported écoutes importées sur $playsInFile")
        if (playsIgnoredDuplicates > 0) appendLine("• $playsIgnoredDuplicates doublons ignorés")
        if (playsBelowThreshold > 0) appendLine("• $playsBelowThreshold écoutes sous le seuil (comptées comme skip)")
        if (unknownSongRefs > 0) appendLine("• $unknownSongRefs écoutes sans titre correspondant")
    }
}

object LegacyBackupImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(input: InputStream): LegacyBackup =
        json.decodeFromString(LegacyBackup.serializer(), input.bufferedReader().use { it.readText() })

    /**
     * Importe le backup : fusion avec l'existant (titres/artistes/albums existants réutilisés,
     * doublons d'écoutes (même titre + même timestamp) ignorés), puis recalcul complet des stats.
     */
    suspend fun import(
        db: NovaDatabase,
        backup: LegacyBackup,
        thresholdSec: Int = ScrobbleRules.DEFAULT_THRESHOLD_SEC,
        onProgress: (String) -> Unit = {}
    ): ImportReport {
        val t0 = System.currentTimeMillis()
        val library = LibraryRepository(db)

        onProgress("Résolution de ${backup.songs.size} titres…")
        val trackBySongId = HashMap<Long, LibraryRepository.Resolved>(backup.songs.size * 2)
        db.withTransaction {
            backup.songs.forEachIndexed { i, song ->
                trackBySongId[song.id] = library.resolve(
                    rawTitleIn = song.title,
                    rawArtistsIn = song.artistNames,
                    rawAlbumIn = song.albumName,
                    durationMs = song.duration,
                    genre = song.genre
                )
                if (i % 200 == 0) onProgress("Titres : $i / ${backup.songs.size}")
            }
        }

        onProgress("Import de ${backup.plays.size} écoutes…")
        var imported = 0; var duplicates = 0; var belowThreshold = 0; var unknown = 0
        val batch = ArrayList<ScrobbleEntity>(500)

        suspend fun flush() {
            if (batch.isEmpty()) return
            val ids = db.scrobbleDao().insertAll(batch)
            val ok = ids.count { it != -1L }
            imported += ok
            duplicates += batch.size - ok
            batch.clear()
        }

        db.withTransaction {
            backup.plays.sortedBy { it.playedAt }.forEachIndexed { i, play ->
                val r = trackBySongId[play.songId]
                if (r == null) { unknown++; return@forEachIndexed }
                val validated = ScrobbleRules.isValidated(play.listenedDuration, thresholdSec)
                if (!validated) belowThreshold++
                batch += ScrobbleEntity(
                    trackId = r.trackId,
                    artistId = r.primaryArtistId,
                    albumId = r.albumId,
                    startedAt = play.playedAt,
                    validatedAt = if (validated) play.playedAt + thresholdSec * 1000L else null,
                    endedAt = play.playedAt + play.listenedDuration,
                    durationListenedMs = play.listenedDuration,
                    sourceApp = "legacy_import",
                    detectionSource = "IMPORT",
                    confidenceScore = 100,
                    status = if (validated) ScrobbleStatus.CONFIRMED else ScrobbleStatus.CANCELLED,
                    isSkip = !validated
                )
                if (batch.size >= 500) flush()
                if (i % 2000 == 0) onProgress("Écoutes : $i / ${backup.plays.size}")
            }
            flush()
        }

        onProgress("Recalcul des statistiques…")
        StatsRebuilder(db).rebuildAll(onProgress = onProgress)

        val report = ImportReport(
            songsInFile = backup.songs.size,
            playsInFile = backup.plays.size,
            playsImported = imported,
            playsIgnoredDuplicates = duplicates,
            playsBelowThreshold = belowThreshold,
            unknownSongRefs = unknown,
            durationMs = System.currentTimeMillis() - t0
        )
        db.migrationLogDao().insert(
            MigrationLogEntity(
                fromVersion = "legacy-v${backup.version}",
                toVersion = "nova-1.0",
                status = if (unknown == 0) "SUCCESS" else "PARTIAL",
                details = report.summary()
            )
        )
        return report
    }
}
