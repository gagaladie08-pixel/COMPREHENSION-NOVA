package com.novastats.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * MODULE 10 — Accueil            (tables 27-29)
 * MODULE 11 — Cache API & Éditeur (tables 30-33)
 * Import/Export                   (table 34)
 */

/** Table 27 — now_playing : une seule ligne (id = 1), mise à jour par le service */
@Entity(tableName = "now_playing")
data class NowPlayingEntity(
    @PrimaryKey val id: Long = 1,
    @ColumnInfo(name = "track_id") val trackId: Long? = null,
    /** Titre/artiste bruts pour l'affichage immédiat avant résolution en base */
    @ColumnInfo(name = "raw_title") val rawTitle: String? = null,
    @ColumnInfo(name = "raw_artist") val rawArtist: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    /** Temps réellement écouté (validation du seuil) */
    @ColumnInfo(name = "progress_ms") val progressMs: Long = 0,
    /** Position dans le morceau + durée (barre de progression de l'Accueil) */
    @ColumnInfo(name = "position_ms", defaultValue = "0") val positionMs: Long = 0,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    @ColumnInfo(name = "is_playing", defaultValue = "0") val isPlaying: Boolean = false,
    @ColumnInfo(name = "raw_album") val rawAlbum: String? = null,
    @ColumnInfo(name = "source_app") val sourceApp: String? = null,
    /** PENDING / VALIDATED / IDLE */
    @ColumnInfo(name = "scrobble_status") val scrobbleStatus: String = "IDLE",
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/** Table 28 — daily_stats : résumé "Aujourd'hui" (section 2 de l'Accueil) */
@Entity(tableName = "daily_stats", indices = [Index(value = ["date"], unique = true)])
data class DailyStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long = 0,
    @ColumnInfo(name = "distinct_artists") val distinctArtists: Int = 0,
    @ColumnInfo(name = "distinct_albums") val distinctAlbums: Int = 0,
    @ColumnInfo(name = "distinct_tracks") val distinctTracks: Int = 0
)

/** Table 29 — notifications_feed : "Dernières actualités" (section 4 de l'Accueil) */
@Entity(tableName = "notifications_feed", indices = [Index("created_at"), Index("is_read")])
data class NotificationFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** CERTIFICATION / PANTHEON / HOF / RECORD */
    val type: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "entity_type") val entityType: String,
    val message: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 30 — api_cache : score ≥ 90 → 6 mois, 70-89 → 1 mois, < 70 → pas mis en cache */
@Entity(
    tableName = "api_cache",
    indices = [Index(value = ["entity_type", "entity_id", "data_type"]), Index("expires_at")]
)
data class ApiCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    /** iTunes / Spotify / Last.fm / Fanart / TheAudioDB / MusicBrainz / Deezer / Discogs / Google */
    val source: String,
    /** COVER / PHOTO / METADATA / BIO */
    @ColumnInfo(name = "data_type") val dataType: String,
    @ColumnInfo(name = "cached_url") val cachedUrl: String? = null,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Int,
    @ColumnInfo(name = "cached_at") val cachedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "is_rejected") val isRejected: Boolean = false,
    @ColumnInfo(name = "is_blacklisted") val isBlacklisted: Boolean = false
)

/** Table 31 — api_reliability : fiabilité dynamique → priorité de cascade ajustée */
@Entity(tableName = "api_reliability", indices = [Index(value = ["api_name"], unique = true)])
data class ApiReliabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "api_name") val apiName: String,
    @ColumnInfo(name = "success_count") val successCount: Int = 0,
    @ColumnInfo(name = "fail_count") val failCount: Int = 0,
    @ColumnInfo(name = "success_rate") val successRate: Double = 100.0,
    @ColumnInfo(name = "current_priority") val currentPriority: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

/** Table 32 — edit_history (max 50 entrées côté éditeur, undo = dernière action) */
@Entity(tableName = "edit_history", indices = [Index("created_at")])
data class EditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** RENAME / MERGE / COVER_CHANGE / ALBUM_CHANGE / ARTIST_CHANGE */
    val type: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    val before: String? = null,
    val after: String? = null,
    @ColumnInfo(name = "extra_data") val extraData: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 33 — user_corrections : apprentissage éditeur (corrections mémorisées et réutilisées) */
@Entity(
    tableName = "user_corrections",
    indices = [Index(value = ["original_value", "correction_type"], unique = true)]
)
data class UserCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "original_value") val originalValue: String,
    @ColumnInfo(name = "corrected_value") val correctedValue: String,
    /** TITLE / ARTIST / ALBUM */
    @ColumnInfo(name = "correction_type") val correctionType: String,
    @ColumnInfo(name = "times_applied") val timesApplied: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 34 — migration_log */
@Entity(tableName = "migration_log")
data class MigrationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "from_version") val fromVersion: String,
    @ColumnInfo(name = "to_version") val toVersion: String,
    @ColumnInfo(name = "migrated_at") val migratedAt: Long = System.currentTimeMillis(),
    /** SUCCESS / PARTIAL / FAILED */
    val status: String,
    val details: String? = null
)
