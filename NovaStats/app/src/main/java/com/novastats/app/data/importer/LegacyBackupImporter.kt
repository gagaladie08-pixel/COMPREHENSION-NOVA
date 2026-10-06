package com.novastats.app.data.importer

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.MigrationLogEntity
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.data.db.entity.ScrobbleStatus
import com.novastats.app.data.repository.AlbumSharing
import com.novastats.app.data.repository.LibraryRepository
import com.novastats.app.domain.TitleNormalizer
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
    val plays: List<LegacyPlay> = emptyList(),
    /** 🔒 Noms d'artistes protégés (format 2.1+) — restaurés AVANT la résolution des titres. */
    val artist_exceptions: List<String> = emptyList(),
    /** 💿 Marquages manuels « album multi-artistes » (format 2.2+). */
    val album_overrides: List<com.novastats.app.data.importer.ExportAlbumOverride> = emptyList()
)

@Serializable
data class LegacySong(
    val id: Long,
    val title: String,
    val artistNames: String = "",
    val albumName: String? = null,
    val duration: Long? = null,
    val genre: String? = null,
    /** ID source du titre racine ; absent des anciens backups, utilisé pour restaurer les liens de versions. */
    val originalSongId: Long? = null
)

@Serializable
data class LegacyPlay(
    val songId: Long,
    val playedAt: Long,
    val listenedDuration: Long = 0,
    /** Statut exporté par NovaStats : null dans les anciens backups, donc déterminé avec le seuil courant. */
    val isConfirmed: Boolean? = null,
    /** Horodatage exact de validation dans les exports NovaStats ; absent des anciens backups. */
    val validatedAt: Long? = null
) {
    val listenedDurationForImport: Long get() = listenedDuration.coerceAtLeast(0L)

    fun isConfirmedAtImport(thresholdSec: Int): Boolean =
        isConfirmed ?: ScrobbleRules.isValidated(listenedDurationForImport, thresholdSec)

    /** Garde un horodatage cohérent même si l'ancien format ne le fournissait pas. */
    fun validatedAtForImport(thresholdSec: Int): Long? {
        if (!isConfirmedAtImport(thresholdSec)) return null
        val endAt = safeAdd(playedAt, listenedDurationForImport)
        val fallback = safeAdd(playedAt, minOf(listenedDurationForImport, thresholdSec.coerceAtLeast(0) * 1000L))
        return (validatedAt ?: fallback).coerceIn(playedAt, endAt)
    }

    fun endedAtForImport(): Long = safeAdd(playedAt, listenedDurationForImport)

    private fun safeAdd(start: Long, duration: Long): Long =
        try { Math.addExact(start, duration) } catch (_: ArithmeticException) { Long.MAX_VALUE }
}

data class ImportReport(
    val songsInFile: Int,
    val playsInFile: Int,
    val playsImported: Int,
    val playsIgnoredDuplicates: Int,
    val playsRepaired: Int = 0,
    val playsBelowThreshold: Int,
    val unknownSongRefs: Int,
    val durationMs: Long
) {
    fun summary(): String = buildString {
        appendLine("✅ Import terminé en ${durationMs / 1000}s")
        appendLine("• $songsInFile titres dans le fichier")
        appendLine("• $playsImported écoutes importées sur $playsInFile")
        if (playsIgnoredDuplicates > 0) appendLine("• $playsIgnoredDuplicates doublons ignorés")
        if (playsRepaired > 0) appendLine("• $playsRepaired écoutes existantes réattribuées à la bonne version (invités / remix)")
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
        // Noms protégés du backup (+ défauts) → chargés avant de découper « HUNTR/X & … »
        backup.artist_exceptions.filter { it.isNotBlank() }.forEach { n ->
            db.artistExceptionDao().insert(com.novastats.app.data.db.entity.ArtistExceptionEntity(name = n.trim(), nameKey = TitleNormalizer.normalizeKey(n)))
        }
        library.loadArtistExceptions(seedDefaults = true)
        backup.album_overrides.forEach { o ->
            val key = TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(o.title))
            val existing = db.editorDao().correction(key, LibraryRepository.CORRECTION_ALBUM_SHARED)
            db.editorDao().upsertCorrection(com.novastats.app.data.db.entity.UserCorrectionEntity(id = existing?.id ?: 0, originalValue = key, correctedValue = if (o.shared) "1" else "0", correctionType = LibraryRepository.CORRECTION_ALBUM_SHARED, timesApplied = existing?.timesApplied ?: 0))
        }
        library.invalidateCorrections()

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
            // Restaure après résolution : les identifiants du backup ne sont pas ceux de cette base.
            backup.songs.forEach { song ->
                val originalSongId = song.originalSongId ?: return@forEach
                val version = trackBySongId[song.id] ?: return@forEach
                val original = trackBySongId[originalSongId] ?: return@forEach
                if (version.trackId == original.trackId) return@forEach
                val originalEntity = db.trackDao().getById(original.trackId) ?: return@forEach
                val rootId = originalEntity.originalTrackId ?: originalEntity.trackId
                if (rootId != version.trackId) db.trackDao().linkToRoot(version.trackId, rootId)
            }
            db.trackDao().flattenRoots()
        }

        onProgress("Import de ${backup.plays.size} écoutes…")
        var imported = 0; var duplicates = 0; var belowThreshold = 0; var unknown = 0; var repaired = 0
        val batch = ArrayList<ScrobbleEntity>(500)
        val songById = backup.songs.associateBy { it.id }
        val rootOf = HashMap<Long, Long>()
        suspend fun root(trackId: Long): Long = rootOf.getOrPut(trackId) { db.trackDao().getById(trackId)?.originalTrackId ?: trackId }

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
                val song = songById[play.songId]
                // Même écoute déjà en base (même instant, même titre ou une version de son groupe) : jamais de doublon.
                // Si elle était sur la mauvaise version (import ancien sans valeurs brutes) → déplacée + valeurs brutes mémorisées.
                val existing = db.scrobbleDao().findInGroupAt(r.trackId, root(r.trackId), play.playedAt)
                if (existing != null) {
                    if (existing.trackId != r.trackId || existing.rawArtist == null) {
                        db.scrobbleDao().repair(existing.scrobbleId, r.trackId, r.primaryArtistId, r.albumId ?: existing.albumId, song?.title ?: existing.rawTitle, song?.artistNames ?: existing.rawArtist, song?.albumName ?: existing.rawAlbum)
                        if (existing.trackId != r.trackId) repaired++ else duplicates++
                    } else duplicates++
                    return@forEachIndexed
                }
                val validated = play.isConfirmedAtImport(thresholdSec)
                if (!validated) belowThreshold++
                batch += ScrobbleEntity(
                    trackId = r.trackId,
                    artistId = r.primaryArtistId,
                    albumId = r.albumId,
                    rawTitle = song?.title,
                    rawArtist = song?.artistNames,
                    rawAlbum = song?.albumName,
                    startedAt = play.playedAt,
                    validatedAt = play.validatedAtForImport(thresholdSec),
                    endedAt = play.endedAtForImport(),
                    durationListenedMs = play.listenedDurationForImport,
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

        onProgress("Albums multi-artistes…")
        AlbumSharing.consolidate(db, library)
        onProgress("Recalcul des statistiques…")
        StatsRebuilder(db, library).rebuildAll(onProgress = onProgress)

        val report = ImportReport(
            songsInFile = backup.songs.size,
            playsInFile = backup.plays.size,
            playsImported = imported,
            playsIgnoredDuplicates = duplicates,
            playsRepaired = repaired,
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
