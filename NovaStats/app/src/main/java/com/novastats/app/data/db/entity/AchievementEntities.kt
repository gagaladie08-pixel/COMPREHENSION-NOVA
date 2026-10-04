package com.novastats.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * MODULE 5 — Certifications (tables 18-19)
 * MODULE 6 — Hall of Fame   (tables 20-21)
 * MODULE 7 — Panthéon       (tables 22-23)
 * MODULE 8 — Records        (table 24)
 * MODULE 9 — Nova Awards    (tables 25-26)
 */

object EntityType {
    const val TRACK = "TRACK"
    const val ARTIST = "ARTIST"
    const val ALBUM = "ALBUM"
}

/** Table 18 — certifications : niveau ACTUEL de chaque titre/album certifié */
@Entity(
    tableName = "certifications",
    indices = [Index(value = ["entity_id", "entity_type"], unique = true), Index("level")]
)
data class CertificationEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "certification_id") val certificationId: Long = 0,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    /** TRACK / ALBUM (les artistes n'ont pas de certification) */
    @ColumnInfo(name = "entity_type") val entityType: String,
    /** SILVER / GOLD / PLATINUM / DIAMOND */
    val level: String,
    /** 1x / 2x / 3x… — seul DIAMOND se multiplie */
    val multiplier: Int = 1,
    @ColumnInfo(name = "play_count_at_cert") val playCountAtCert: Int,
    @ColumnInfo(name = "certified_at") val certifiedAt: Long,
    @ColumnInfo(name = "time_to_certify_ms") val timeToCertifyMs: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 19 — certification_history : chaque palier atteint avec sa date (dates rétroactives) */
@Entity(
    tableName = "certification_history",
    indices = [Index(value = ["entity_id", "entity_type", "level", "multiplier"], unique = true)]
)
data class CertificationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "entity_type") val entityType: String,
    val level: String,
    val multiplier: Int = 1,
    @ColumnInfo(name = "certified_at") val certifiedAt: Long,
    @ColumnInfo(name = "play_count_at_cert") val playCountAtCert: Int,
    @ColumnInfo(name = "time_to_certify_ms") val timeToCertifyMs: Long? = null
)

/** Table 20 — hall_of_fame */
@Entity(
    tableName = "hall_of_fame",
    indices = [Index(value = ["entity_id", "entity_type", "period_type", "entry_type"]), Index("entry_date")]
)
data class HallOfFameEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "hof_id") val hofId: Long = 0,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "entity_type") val entityType: String,
    /** WEEKLY / MONTHLY / GLOBAL */
    @ColumnInfo(name = "period_type") val periodType: String,
    /** DIRECT_DEBUT / LONG_RUN / TRIPLE_DEBUT / LEGENDARY_RUN */
    @ColumnInfo(name = "entry_type") val entryType: String,
    @ColumnInfo(name = "entry_date") val entryDate: String,
    @ColumnInfo(name = "reign_start") val reignStart: String? = null,
    @ColumnInfo(name = "reign_end") val reignEnd: String? = null,
    @ColumnInfo(name = "weeks_at_1") val weeksAt1: Int = 0,
    @ColumnInfo(name = "play_count_at_entry") val playCountAtEntry: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 21 — hall_of_fame_badges */
@Entity(tableName = "hall_of_fame_badges", indices = [Index("hof_id")])
data class HallOfFameBadgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "hof_id") val hofId: Long,
    @ColumnInfo(name = "badge_type") val badgeType: String,
    @ColumnInfo(name = "badge_date") val badgeDate: String
)

/** Table 22 — pantheon_status : statut actuel par artiste (permanent) */
@Entity(tableName = "pantheon_status", indices = [Index(value = ["artist_id"], unique = true)])
data class PantheonStatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    /** STAR / SUPERSTAR / MEGASTAR / LEGENDE / MYTHIQUE */
    @ColumnInfo(name = "current_status") val currentStatus: String,
    @ColumnInfo(name = "status_date") val statusDate: Long,
    @ColumnInfo(name = "time_to_status_ms") val timeToStatusMs: Long? = null,
    @ColumnInfo(name = "star_threshold") val starThreshold: Int = 425,
    @ColumnInfo(name = "superstar_threshold") val superstarThreshold: Int = 650,
    @ColumnInfo(name = "megastar_threshold") val megastarThreshold: Int = 1250,
    @ColumnInfo(name = "legende_threshold") val legendeThreshold: Int = 3650,
    @ColumnInfo(name = "mythique_threshold") val mythiqueThreshold: Int = 7000,
    /** true = atteint via Option B (écoutes), false = via Option A (certifications) */
    @ColumnInfo(name = "reached_via_plays") val reachedViaPlays: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/** Table 23 — pantheon_history */
@Entity(tableName = "pantheon_history", indices = [Index(value = ["artist_id", "status"], unique = true)])
data class PantheonHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val status: String,
    @ColumnInfo(name = "date_reached") val dateReached: Long,
    @ColumnInfo(name = "time_to_reach_ms") val timeToReachMs: Long? = null,
    @ColumnInfo(name = "play_count_at_status") val playCountAtStatus: Int
)

/** Table 24 — records_cache : résultats pré-calculés des 30 records */
@Entity(
    tableName = "records_cache",
    indices = [Index(value = ["record_type", "period_type", "category", "subcategory"])]
)
data class RecordCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** MOST_CUMULATIVE / MOST_CUMULATIVE_TOP10 / MOST_TIME_AT_1 / … / MOST_SUCCESSIVE_1 */
    @ColumnInfo(name = "record_type") val recordType: String,
    @ColumnInfo(name = "period_type") val periodType: String? = null,
    /** TRACK / ARTIST / ALBUM */
    val category: String,
    /** SONGS / ALBUMS (sous-sections artiste) */
    val subcategory: String? = null,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    val value: Double,
    @ColumnInfo(name = "value_date") val valueDate: String? = null,
    @ColumnInfo(name = "extra_data") val extraData: String? = null,
    @ColumnInfo(name = "calculated_at") val calculatedAt: Long = System.currentTimeMillis()
)

/** Table 25 — nova_awards (année en cours = LIVE, recalculée en temps réel) */
@Entity(tableName = "nova_awards", indices = [Index(value = ["year", "category"], unique = true)])
data class NovaAwardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    /** SONG_OF_YEAR / ARTIST_OF_YEAR / ALBUM_OF_YEAR / BIGGEST_RISE / REVELATION / LOYALTY / BEST_CERTIFICATION / LONGEST_STREAK / LONGEST_SESSION */
    val category: String,
    @ColumnInfo(name = "winner_id") val winnerId: Long?,
    @ColumnInfo(name = "winner_type") val winnerType: String?,
    val value: Double,
    val message: String? = null,
    @ColumnInfo(name = "is_final") val isFinal: Boolean = false,
    @ColumnInfo(name = "calculated_at") val calculatedAt: Long = System.currentTimeMillis()
)

/** Table 26 — nova_awards_history (années finalisées le 31 décembre) */
@Entity(tableName = "nova_awards_history", indices = [Index(value = ["year", "category"], unique = true)])
data class NovaAwardHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val category: String,
    @ColumnInfo(name = "winner_id") val winnerId: Long?,
    @ColumnInfo(name = "winner_type") val winnerType: String?,
    val value: Double,
    val message: String? = null,
    @ColumnInfo(name = "finalized_at") val finalizedAt: Long
)
