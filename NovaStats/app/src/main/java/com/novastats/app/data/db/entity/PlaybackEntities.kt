package com.novastats.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * MODULE 2 — Écoutes (tables 6 à 10)
 * MODULE 3 — Snapshots (tables 11 à 14)
 */

/** Table 6 — scrobbles : une ligne par écoute validée (ou en cours) */
@Entity(
    tableName = "scrobbles",
    indices = [
        Index("track_id"), Index("artist_id"), Index("album_id"),
        Index("started_at"), Index(value = ["track_id", "started_at"], unique = true)
    ]
)
data class ScrobbleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "scrobble_id") val scrobbleId: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "validated_at") val validatedAt: Long? = null,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "duration_listened_ms") val durationListenedMs: Long = 0,
    @ColumnInfo(name = "source_app") val sourceApp: String? = null,
    /** MEDIA_SESSION / NOTIFICATION / IMPORT */
    @ColumnInfo(name = "detection_source") val detectionSource: String = "MEDIA_SESSION",
    @ColumnInfo(name = "confidence_score") val confidenceScore: Int = 100,
    /** PENDING / CONFIRMED / CANCELLED */
    val status: String = ScrobbleStatus.PENDING,
    @ColumnInfo(name = "volume_level") val volumeLevel: Int? = null,
    @ColumnInfo(name = "is_skip") val isSkip: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

object ScrobbleStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val CANCELLED = "CANCELLED"
}

/** Table 7 — sessions (session = suite d'écoutes sans gap ≥ 15 min) */
@Entity(tableName = "sessions", indices = [Index("started_at")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "session_id") val sessionId: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long = 0,
    @ColumnInfo(name = "track_count") val trackCount: Int = 0,
    @ColumnInfo(name = "source_app") val sourceApp: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 8 — pending_queue : écoutes en attente d'analyse (retry au redémarrage) */
@Entity(tableName = "pending_queue", indices = [Index("status"), Index("expires_at")])
data class PendingQueueEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "queue_id") val queueId: Long = 0,
    @ColumnInfo(name = "track_title") val trackTitle: String,
    @ColumnInfo(name = "artist_name") val artistName: String? = null,
    @ColumnInfo(name = "album_name") val albumName: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "source_app") val sourceApp: String? = null,
    /** PENDING / PROCESSING */
    val status: String = "PENDING",
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** Expiration 7 jours */
    @ColumnInfo(name = "expires_at") val expiresAt: Long = System.currentTimeMillis() + 7L * 24 * 3600 * 1000
)

/** Table 9 — daily_plays : agrégat par (titre, jour) — base des snapshots et des périodes */
@Entity(
    tableName = "daily_plays",
    indices = [Index(value = ["track_id", "date"], unique = true), Index("date"), Index("artist_id"), Index("album_id")]
)
data class DailyPlayEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "daily_id") val dailyId: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long? = null,
    val date: String,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long = 0
)

/** Table 10 — daily_streaks : une ligne par jour */
@Entity(tableName = "daily_streaks", indices = [Index(value = ["date"], unique = true)])
data class DailyStreakEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "streak_id") val streakId: Long = 0,
    val date: String,
    @ColumnInfo(name = "has_play") val hasPlay: Boolean,
    @ColumnInfo(name = "current_streak") val currentStreak: Int,
    @ColumnInfo(name = "best_streak") val bestStreak: Int,
    @ColumnInfo(name = "best_streak_date") val bestStreakDate: String? = null
)

/** Table 11 — snapshots : instantané quotidien / hebdo / mensuel / annuel des classements */
@Entity(tableName = "snapshots", indices = [Index(value = ["type", "date"], unique = true)])
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "snapshot_id") val snapshotId: Long = 0,
    /** DAILY / WEEKLY / MONTHLY / YEARLY */
    val type: String,
    val date: String,
    @ColumnInfo(name = "week_number") val weekNumber: Int? = null,
    val month: Int? = null,
    val year: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 12 — snapshot_tracks */
@Entity(
    tableName = "snapshot_tracks",
    indices = [Index(value = ["snapshot_id", "track_id"], unique = true), Index("track_id")]
)
data class SnapshotTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "snapshot_id") val snapshotId: Long,
    @ColumnInfo(name = "track_id") val trackId: Long,
    val position: Int,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long,
    @ColumnInfo(name = "previous_position") val previousPosition: Int? = null,
    val movement: Int? = null,
    @ColumnInfo(name = "is_new") val isNew: Boolean = false,
    @ColumnInfo(name = "is_reentry") val isReentry: Boolean = false,
    @ColumnInfo(name = "days_in_chart") val daysInChart: Int = 0,
    @ColumnInfo(name = "weeks_in_chart") val weeksInChart: Int = 0,
    @ColumnInfo(name = "months_in_chart") val monthsInChart: Int = 0,
    @ColumnInfo(name = "peak_position") val peakPosition: Int,
    @ColumnInfo(name = "peak_date") val peakDate: String? = null,
    @ColumnInfo(name = "times_at_peak") val timesAtPeak: Int = 1,
    @ColumnInfo(name = "debut_position") val debutPosition: Int,
    @ColumnInfo(name = "debut_date") val debutDate: String,
    @ColumnInfo(name = "variation_plays") val variationPlays: Int = 0
)

/** Table 13 — snapshot_artists */
@Entity(
    tableName = "snapshot_artists",
    indices = [Index(value = ["snapshot_id", "artist_id"], unique = true), Index("artist_id")]
)
data class SnapshotArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "snapshot_id") val snapshotId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val position: Int,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long,
    @ColumnInfo(name = "previous_position") val previousPosition: Int? = null,
    val movement: Int? = null,
    @ColumnInfo(name = "is_new") val isNew: Boolean = false,
    @ColumnInfo(name = "is_reentry") val isReentry: Boolean = false,
    @ColumnInfo(name = "days_in_chart") val daysInChart: Int = 0,
    @ColumnInfo(name = "weeks_in_chart") val weeksInChart: Int = 0,
    @ColumnInfo(name = "months_in_chart") val monthsInChart: Int = 0,
    @ColumnInfo(name = "peak_position") val peakPosition: Int,
    @ColumnInfo(name = "peak_date") val peakDate: String? = null,
    @ColumnInfo(name = "times_at_peak") val timesAtPeak: Int = 1,
    @ColumnInfo(name = "debut_position") val debutPosition: Int,
    @ColumnInfo(name = "debut_date") val debutDate: String,
    @ColumnInfo(name = "variation_plays") val variationPlays: Int = 0,
    @ColumnInfo(name = "distinct_tracks") val distinctTracks: Int = 0,
    @ColumnInfo(name = "distinct_albums") val distinctAlbums: Int = 0
)

/** Table 14 — snapshot_albums */
@Entity(
    tableName = "snapshot_albums",
    indices = [Index(value = ["snapshot_id", "album_id"], unique = true), Index("album_id")]
)
data class SnapshotAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "snapshot_id") val snapshotId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long,
    val position: Int,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long,
    @ColumnInfo(name = "previous_position") val previousPosition: Int? = null,
    val movement: Int? = null,
    @ColumnInfo(name = "is_new") val isNew: Boolean = false,
    @ColumnInfo(name = "is_reentry") val isReentry: Boolean = false,
    @ColumnInfo(name = "days_in_chart") val daysInChart: Int = 0,
    @ColumnInfo(name = "weeks_in_chart") val weeksInChart: Int = 0,
    @ColumnInfo(name = "months_in_chart") val monthsInChart: Int = 0,
    @ColumnInfo(name = "peak_position") val peakPosition: Int,
    @ColumnInfo(name = "peak_date") val peakDate: String? = null,
    @ColumnInfo(name = "times_at_peak") val timesAtPeak: Int = 1,
    @ColumnInfo(name = "debut_position") val debutPosition: Int,
    @ColumnInfo(name = "debut_date") val debutDate: String,
    @ColumnInfo(name = "variation_plays") val variationPlays: Int = 0,
    @ColumnInfo(name = "distinct_tracks") val distinctTracks: Int = 0
)
