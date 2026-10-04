package com.novastats.app.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/* ----------------------------- POJOs Rewind ----------------------------- */

data class RewindTotals(
    val plays: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val days: Int,
    val tracks: Int,
    val artists: Int,
    val albums: Int
)

data class HourCount(val hour: Int, val plays: Int)
data class WeekdayCount(val weekday: Int, val plays: Int) // 0 = dimanche … 6 = samedi (strftime %w)

data class CertEvent(
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "entity_type") val entityType: String,
    val level: String,
    val multiplier: Int,
    @ColumnInfo(name = "certified_at") val certifiedAt: Long,
    val name: String?,
    @ColumnInfo(name = "artist_name") val artistName: String?,
    val cover: String?
)

data class PantheonEvent(
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val status: String,
    @ColumnInfo(name = "date_reached") val dateReached: Long,
    val name: String?,
    val photo: String?
)

data class NewArtist(
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val name: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String?,
    val plays: Int
)

/**
 * ✨ Nova Rewind — requêtes d'agrégation sur une période (mois / année), toutes dérivées de `daily_plays`
 * (par racine de titre, cf. règle 10) et de `scrobbles` pour les heures d'écoute.
 */
@Dao
interface RewindDao {

    @Query(
        """
        SELECT IFNULL(SUM(play_count), 0) AS plays, IFNULL(SUM(total_duration_ms), 0) AS duration_ms,
               COUNT(DISTINCT date) AS days, COUNT(DISTINCT root_id) AS tracks,
               COUNT(DISTINCT artist_id) AS artists, COUNT(DISTINCT album_id) AS albums
        FROM daily_plays WHERE date BETWEEN :from AND :to
        """
    )
    suspend fun totals(from: String, to: String): RewindTotals

    /** Mois (AAAA-MM) ayant au moins une écoute, du plus récent au plus ancien. */
    @Query("SELECT DISTINCT substr(date, 1, 7) FROM daily_plays WHERE play_count > 0 ORDER BY 1 DESC")
    suspend fun monthsWithPlays(): List<String>

    @Query("SELECT DISTINCT substr(date, 1, 4) FROM daily_plays WHERE play_count > 0 ORDER BY 1 DESC")
    suspend fun yearsWithPlays(): List<String>

    @Query("SELECT date, SUM(play_count) AS play_count FROM daily_plays WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY play_count DESC, date LIMIT 1")
    suspend fun bestDay(from: String, to: String): DayCount?

    @Query("SELECT date, SUM(play_count) AS play_count FROM daily_plays WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date")
    suspend fun dailySeries(from: String, to: String): List<DayCount>

    @Query("SELECT COUNT(DISTINCT date) FROM daily_plays WHERE root_id = :trackId AND date BETWEEN :from AND :to AND play_count > 0")
    suspend fun daysWithTrack(trackId: Long, from: String, to: String): Int

    @Query("SELECT COUNT(DISTINCT date) FROM daily_plays WHERE artist_id = :artistId AND date BETWEEN :from AND :to AND play_count > 0")
    suspend fun daysWithArtist(artistId: Long, from: String, to: String): Int

    /** Heures d'écoute (heure locale de l'appareil) sur la période. */
    @Query(
        """
        SELECT CAST(strftime('%H', started_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, COUNT(*) AS plays
        FROM scrobbles WHERE status = 'CONFIRMED' AND started_at BETWEEN :fromMs AND :toMs GROUP BY hour ORDER BY hour
        """
    )
    suspend fun hours(fromMs: Long, toMs: Long): List<HourCount>

    @Query(
        """
        SELECT CAST(strftime('%w', started_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday, COUNT(*) AS plays
        FROM scrobbles WHERE status = 'CONFIRMED' AND started_at BETWEEN :fromMs AND :toMs GROUP BY weekday ORDER BY weekday
        """
    )
    suspend fun weekdays(fromMs: Long, toMs: Long): List<WeekdayCount>

    /** Artistes découverts sur la période (première écoute dans l'intervalle), les plus écoutés d'abord. */
    @Query(
        """
        SELECT a.artist_id, a.name, a.photo_url, IFNULL(SUM(d.play_count), 0) AS plays
        FROM artists a LEFT JOIN daily_plays d ON d.artist_id = a.artist_id AND d.date BETWEEN :from AND :to
        WHERE a.first_played_at BETWEEN :fromMs AND :toMs AND a.is_merged = 0 AND a.play_count > 0
        GROUP BY a.artist_id ORDER BY plays DESC, a.name LIMIT :limit
        """
    )
    suspend fun newArtists(from: String, to: String, fromMs: Long, toMs: Long, limit: Int = 12): List<NewArtist>

    @Query("SELECT COUNT(*) FROM artists WHERE first_played_at BETWEEN :fromMs AND :toMs AND is_merged = 0 AND play_count > 0")
    suspend fun newArtistCount(fromMs: Long, toMs: Long): Int

    @Query("SELECT COUNT(*) FROM tracks WHERE first_played_at BETWEEN :fromMs AND :toMs AND original_track_id IS NULL AND play_count > 0")
    suspend fun newTrackCount(fromMs: Long, toMs: Long): Int

    /** Certifications décrochées sur la période (titres et albums), avec nom, artiste et pochette. */
    @Query(
        """
        SELECT h.entity_id, h.entity_type, h.level, h.multiplier, h.certified_at,
               CASE WHEN h.entity_type = 'TRACK' THEN (SELECT t.title FROM tracks t WHERE t.track_id = h.entity_id)
                    ELSE (SELECT al.title FROM albums al WHERE al.album_id = h.entity_id) END AS name,
               CASE WHEN h.entity_type = 'TRACK' THEN (SELECT a.name FROM tracks t JOIN artists a ON a.artist_id = t.artist_id WHERE t.track_id = h.entity_id)
                    ELSE (SELECT IFNULL(a.name, 'Artistes variés') FROM albums al LEFT JOIN artists a ON a.artist_id = al.artist_id WHERE al.album_id = h.entity_id) END AS artist_name,
               CASE WHEN h.entity_type = 'TRACK' THEN (SELECT t.cover_url FROM tracks t WHERE t.track_id = h.entity_id)
                    ELSE (SELECT al.cover_url FROM albums al WHERE al.album_id = h.entity_id) END AS cover
        FROM certification_history h
        WHERE h.certified_at BETWEEN :fromMs AND :toMs
        ORDER BY CASE h.level WHEN 'DIAMOND' THEN 4 WHEN 'PLATINUM' THEN 3 WHEN 'GOLD' THEN 2 ELSE 1 END DESC, h.multiplier DESC, h.certified_at
        """
    )
    suspend fun certifications(fromMs: Long, toMs: Long): List<CertEvent>

    @Query(
        """
        SELECT h.artist_id, h.status, h.date_reached, a.name, a.photo_url AS photo
        FROM pantheon_history h LEFT JOIN artists a ON a.artist_id = h.artist_id
        WHERE h.date_reached BETWEEN :fromMs AND :toMs ORDER BY h.date_reached
        """
    )
    suspend fun pantheon(fromMs: Long, toMs: Long): List<PantheonEvent>

    /** Plus longue séance (session) de la période, en ms. */
    @Query("SELECT IFNULL(MAX(ended_at - started_at), 0) FROM sessions WHERE started_at BETWEEN :fromMs AND :toMs AND ended_at IS NOT NULL")
    suspend fun longestSessionMs(fromMs: Long, toMs: Long): Long
}
