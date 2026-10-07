package com.novastats.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * MODULE 1 — Entités principales (tables 1 à 5)
 *
 * Conventions de types (valables pour toutes les tables) :
 *   DATETIME  → Long  (epoch millis UTC)
 *   DATE      → String au format ISO "yyyy-MM-dd" (jour local)
 *   BOOLEAN   → Boolean (stocké en INTEGER 0/1 par Room)
 */

/** Table 1 — tracks */
@Entity(
    tableName = "tracks",
    indices = [
        Index("artist_id"), Index("album_id"),
        Index(value = ["title", "artist_id"]),
        Index("play_count")
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "track_id") val trackId: Long = 0,
    val title: String,
    @ColumnInfo(name = "title_raw") val titleRaw: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    val genre: String? = null,
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "cover_source") val coverSource: String? = null,
    @ColumnInfo(name = "is_remix") val isRemix: Boolean = false,
    @ColumnInfo(name = "original_track_id") val originalTrackId: Long? = null,
    @ColumnInfo(name = "discovery_rank") val discoveryRank: Int? = null,
    @ColumnInfo(name = "first_played_at") val firstPlayedAt: Long? = null,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long = 0,
    @ColumnInfo(name = "current_streak") val currentStreak: Int = 0,
    @ColumnInfo(name = "best_streak") val bestStreak: Int = 0,
    @ColumnInfo(name = "best_streak_date") val bestStreakDate: String? = null,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Int = 100,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean = false,
    @ColumnInfo(name = "acoustid_fingerprint") val acoustidFingerprint: String? = null,
    @ColumnInfo(name = "acoustid_resolved") val acoustidResolved: Boolean = false,
    val mbid: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 2 — artists */
@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"], unique = true), Index("play_count"), Index("pantheon_status")]
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "artist_id") val artistId: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_raw") val nameRaw: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String? = null,
    @ColumnInfo(name = "photo_source") val photoSource: String? = null,
    val bio: String? = null,
    val mbid: String? = null,
    @ColumnInfo(name = "spotify_id") val spotifyId: String? = null,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long = 0,
    @ColumnInfo(name = "distinct_tracks") val distinctTracks: Int = 0,
    @ColumnInfo(name = "distinct_albums") val distinctAlbums: Int = 0,
    @ColumnInfo(name = "first_played_at") val firstPlayedAt: Long? = null,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    /** STAR / SUPERSTAR / MEGASTAR / LEGENDE / MYTHIQUE — null si pas encore dans le Panthéon */
    @ColumnInfo(name = "pantheon_status") val pantheonStatus: String? = null,
    @ColumnInfo(name = "pantheon_date") val pantheonDate: Long? = null,
    @ColumnInfo(name = "is_merged") val isMerged: Boolean = false,
    @ColumnInfo(name = "merged_into_id") val mergedIntoId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 3 — albums */
@Entity(
    tableName = "albums",
    indices = [Index("artist_id"), Index(value = ["title", "artist_id"], unique = true), Index("play_count")]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "album_id") val albumId: Long = 0,
    val title: String,
    @ColumnInfo(name = "title_raw") val titleRaw: String,
    /** NULL = album partagé (BO, album d'événement, « Various Artists ») : aucun artiste propriétaire, étiquette « Artistes variés ». */
    @ColumnInfo(name = "artist_id") val artistId: Long?,
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "cover_source") val coverSource: String? = null,
    @ColumnInfo(name = "release_date") val releaseDate: String? = null,
    @ColumnInfo(name = "is_studio") val isStudio: Boolean = true,
    @ColumnInfo(name = "is_compilation") val isCompilation: Boolean = false,
    val mbid: String? = null,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long = 0,
    @ColumnInfo(name = "distinct_tracks_played") val distinctTracksPlayed: Int = 0,
    @ColumnInfo(name = "first_played_at") val firstPlayedAt: Long? = null,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) { val isShared: Boolean get() = artistId == null }

/** Table 4 — track_artists (artistes multiples : chacun reçoit une écoute, même poids) */
@Entity(
    tableName = "track_artists",
    indices = [Index(value = ["track_id", "artist_id"], unique = true), Index("artist_id")],
    foreignKeys = [
        ForeignKey(TrackEntity::class, ["track_id"], ["track_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ArtistEntity::class, ["artist_id"], ["artist_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class TrackArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean,
    /** "main" / "featured" / "remix" */
    val role: String = if (isPrimary) "main" else "featured"
)

/** Table 5 — track_albums */
@Entity(
    tableName = "track_albums",
    indices = [Index(value = ["track_id", "album_id"], unique = true), Index("album_id")],
    foreignKeys = [
        ForeignKey(TrackEntity::class, ["track_id"], ["track_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AlbumEntity::class, ["album_id"], ["album_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class TrackAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int? = null,
    @ColumnInfo(name = "is_primary") val isPrimary: Boolean = true
)


/**
 * 🔒 Noms d'artistes à ne jamais découper (« HUNTR/X », « AC/DC », « Tyler, The Creator »…).
 * Comparés par clé normalisée ; éditables dans l'éditeur de données ; inclus dans les backups.
 */
@Entity(tableName = "artist_exceptions", indices = [Index(value = ["name_key"], unique = true)])
data class ArtistExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
