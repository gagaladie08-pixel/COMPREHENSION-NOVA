package com.novastats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.novastats.app.data.db.entity.DailyPlayEntity
import com.novastats.app.data.db.entity.DailyStatsEntity
import com.novastats.app.data.db.entity.DailyStreakEntity
import com.novastats.app.data.db.entity.NowPlayingEntity
import com.novastats.app.data.db.entity.PendingQueueEntity
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.data.db.entity.SessionEntity
import com.novastats.app.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

data class RecentScrobble(
    @Embedded val scrobble: ScrobbleEntity,
    @androidx.room.ColumnInfo(name = "title") val title: String,
    @androidx.room.ColumnInfo(name = "artist_name") val artistName: String,
    @androidx.room.ColumnInfo(name = "cover_url") val coverUrl: String?
)

data class DayCount(val date: String, @androidx.room.ColumnInfo(name = "play_count") val playCount: Int)
data class DailyEntityRow(@androidx.room.ColumnInfo(name = "track_id") val trackId: Long, @androidx.room.ColumnInfo(name = "artist_id") val artistId: Long, @androidx.room.ColumnInfo(name = "album_id") val albumId: Long?, val date: String)
data class IdCount(val id: Long, val plays: Int, @androidx.room.ColumnInfo(name = "duration_ms") val durationMs: Long)
data class ArtistMonths(@androidx.room.ColumnInfo(name = "artist_id") val artistId: Long, val months: Int, val plays: Int)

data class BestTrackDay(
    @Embedded val daily: DailyPlayEntity,
    @androidx.room.ColumnInfo(name = "title") val title: String
)

data class PeriodSummary(
    @androidx.room.ColumnInfo(name = "play_count") val playCount: Int,
    @androidx.room.ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long,
    @androidx.room.ColumnInfo(name = "distinct_tracks") val distinctTracks: Int,
    @androidx.room.ColumnInfo(name = "distinct_artists") val distinctArtists: Int,
    @androidx.room.ColumnInfo(name = "distinct_albums") val distinctAlbums: Int,
    @androidx.room.ColumnInfo(name = "active_days") val activeDays: Int
)

/** ⚠️ À corriger — un groupe = un titre dont au moins une écoute est à réviser. */
data class ReviewGroup(
    @androidx.room.ColumnInfo(name = "track_id") val trackId: Long,
    val title: String,
    @androidx.room.ColumnInfo(name = "artist_name") val artistName: String,
    @androidx.room.ColumnInfo(name = "album_title") val albumTitle: String?,
    @androidx.room.ColumnInfo(name = "cover_url") val coverUrl: String?,
    val count: Int,
    @androidx.room.ColumnInfo(name = "last_at") val lastAt: Long,
    @androidx.room.ColumnInfo(name = "min_score") val minScore: Int,
    val reason: String?,
    @androidx.room.ColumnInfo(name = "detection_source") val detectionSource: String?,
    @androidx.room.ColumnInfo(name = "source_app") val sourceApp: String?,
    @androidx.room.ColumnInfo(name = "raw_title") val rawTitle: String?,
    @androidx.room.ColumnInfo(name = "raw_artist") val rawArtist: String?,
    @androidx.room.ColumnInfo(name = "raw_album") val rawAlbum: String?
)

@Dao
interface ScrobbleDao {
    /* ---------- ⚠️ À corriger (révision par écoute) ---------- */

    @Query(
        """
        SELECT s.track_id, t.title, a.name AS artist_name, al.title AS album_title, t.cover_url,
               COUNT(*) AS count, MAX(s.started_at) AS last_at, MIN(s.confidence_score) AS min_score,
               MAX(s.review_reason) AS reason, MAX(s.detection_source) AS detection_source, MAX(s.source_app) AS source_app,
               MAX(s.raw_title) AS raw_title, MAX(s.raw_artist) AS raw_artist, MAX(s.raw_album) AS raw_album
        FROM scrobbles s
        JOIN tracks t ON t.track_id = s.track_id
        JOIN artists a ON a.artist_id = t.artist_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE s.needs_review = 1 AND s.status = 'CONFIRMED'
        GROUP BY s.track_id
        ORDER BY last_at DESC
        """
    )
    fun reviewGroups(): Flow<List<ReviewGroup>>

    @Query("SELECT COUNT(DISTINCT track_id) FROM scrobbles WHERE needs_review = 1 AND status = 'CONFIRMED'")
    fun reviewCountFlow(): Flow<Int>

    @Query("SELECT * FROM scrobbles WHERE track_id = :trackId AND needs_review = 1 AND status = 'CONFIRMED' ORDER BY started_at DESC")
    suspend fun reviewOfTrack(trackId: Long): List<ScrobbleEntity>

    @Query("SELECT * FROM scrobbles WHERE track_id = :trackId AND status = 'CONFIRMED' ORDER BY started_at DESC")
    suspend fun allOfTrack(trackId: Long): List<ScrobbleEntity>

    @Query("SELECT COUNT(*) FROM scrobbles WHERE track_id = :trackId AND status = 'CONFIRMED'")
    suspend fun countOfTrack(trackId: Long): Int

    @Query("UPDATE scrobbles SET track_id = :trackId, artist_id = :artistId, album_id = :albumId WHERE scrobble_id IN (:ids)")
    suspend fun moveScrobbles(ids: List<Long>, trackId: Long, artistId: Long, albumId: Long?)

    @Query("UPDATE scrobbles SET needs_review = 0, confidence_score = 100, review_reason = NULL WHERE scrobble_id IN (:ids)")
    suspend fun clearReview(ids: List<Long>)

    @Query("UPDATE scrobbles SET needs_review = 0, confidence_score = 100, review_reason = NULL WHERE track_id = :trackId")
    suspend fun clearReviewOfTrack(trackId: Long)

    @Query("UPDATE scrobbles SET needs_review = 1, review_reason = :reason WHERE scrobble_id IN (:ids)")
    suspend fun reflag(ids: List<Long>, reason: String)

    @Query("DELETE FROM scrobbles WHERE scrobble_id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)

    @Query("SELECT * FROM scrobbles WHERE scrobble_id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<ScrobbleEntity>

    /** IGNORE : les doublons (même titre + même timestamp) sont ignorés à l'import. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scrobble: ScrobbleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(scrobbles: List<ScrobbleEntity>): List<Long>

    @Update
    suspend fun update(scrobble: ScrobbleEntity)

    @Query("SELECT * FROM scrobbles WHERE scrobble_id = :id")
    suspend fun getById(id: Long): ScrobbleEntity?

    @Query("SELECT COUNT(*) FROM scrobbles WHERE status = 'CONFIRMED'")
    fun countConfirmedFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scrobbles WHERE status = 'CONFIRMED'")
    suspend fun countConfirmed(): Int

    @Query(
        """
        SELECT s.*, t.title AS title, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name, t.cover_url AS cover_url
        FROM scrobbles s
        JOIN tracks t ON t.track_id = s.track_id
        JOIN artists a ON a.artist_id = s.artist_id
        WHERE s.status = 'CONFIRMED'
        ORDER BY s.started_at DESC LIMIT :limit
        """
    )
    fun recent(limit: Int = 20): Flow<List<RecentScrobble>>

    @Query("SELECT * FROM scrobbles WHERE status = 'PENDING' ORDER BY started_at")
    suspend fun pending(): List<ScrobbleEntity>

    @Query("SELECT * FROM scrobbles WHERE track_id = :trackId AND started_at = :startedAt LIMIT 1")
    suspend fun findByTrackAndStart(trackId: Long, startedAt: Long): ScrobbleEntity?

    @Query("DELETE FROM scrobbles WHERE scrobble_id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE scrobbles SET artist_id = :into WHERE artist_id = :from") suspend fun moveArtist(from: Long, into: Long)
    @Query("UPDATE scrobbles SET album_id = :into WHERE album_id = :from") suspend fun moveAlbum(from: Long, into: Long)
    @Query("UPDATE scrobbles SET track_id = :into WHERE track_id = :from") suspend fun moveTrack(from: Long, into: Long)
    @Query("UPDATE scrobbles SET artist_id = :artistId WHERE track_id = :trackId") suspend fun setArtistForTrack(trackId: Long, artistId: Long)
    @Query("UPDATE scrobbles SET album_id = :albumId WHERE track_id = :trackId") suspend fun setAlbumForTrack(trackId: Long, albumId: Long?)
    @Query("SELECT * FROM scrobbles WHERE status = 'CONFIRMED' ORDER BY started_at DESC LIMIT :limit OFFSET :offset") suspend fun page(limit: Int, offset: Int): List<ScrobbleEntity>
    @Query("SELECT DISTINCT source_app FROM scrobbles WHERE source_app IS NOT NULL") fun distinctSources(): Flow<List<String>>
    @Query("SELECT MIN(started_at) FROM scrobbles WHERE status = 'CONFIRMED'") fun firstScrobbleAt(): Flow<Long?>

    @Query(
        """
        SELECT COUNT(*) AS play_count,
               IFNULL(SUM(duration_listened_ms), 0) AS total_duration_ms,
               COUNT(DISTINCT track_id) AS distinct_tracks,
               (SELECT COUNT(DISTINCT ta.artist_id) FROM scrobbles s2 JOIN track_artists ta ON ta.track_id = s2.track_id
                WHERE s2.status = 'CONFIRMED' AND s2.started_at BETWEEN :fromMs AND :toMs) AS distinct_artists,
               COUNT(DISTINCT album_id) AS distinct_albums,
               COUNT(DISTINCT date(started_at / 1000, 'unixepoch', 'localtime')) AS active_days
        FROM scrobbles
        WHERE status = 'CONFIRMED' AND started_at BETWEEN :fromMs AND :toMs
        """
    )
    fun summary(fromMs: Long, toMs: Long): Flow<PeriodSummary>

    /** Date (epoch ms) de la N-ième écoute confirmée d'un titre — dates rétroactives des certifications. */
    @Query("SELECT started_at FROM scrobbles WHERE track_id = :trackId AND status = 'CONFIRMED' ORDER BY started_at LIMIT 1 OFFSET :n - 1")
    suspend fun nthPlayOfTrack(trackId: Long, n: Int): Long?

    @Query("SELECT started_at FROM scrobbles WHERE album_id = :albumId AND status = 'CONFIRMED' ORDER BY started_at LIMIT 1 OFFSET :n - 1")
    suspend fun nthPlayOfAlbum(albumId: Long, n: Int): Long?

    @Query(
        """
        SELECT s.started_at FROM scrobbles s JOIN track_artists ta ON ta.track_id = s.track_id
        WHERE ta.artist_id = :artistId AND s.status = 'CONFIRMED' ORDER BY s.started_at LIMIT 1 OFFSET :n - 1
        """
    )
    suspend fun nthPlayOfArtist(artistId: Long, n: Int): Long?

    @Query("SELECT MIN(started_at) FROM scrobbles WHERE status = 'CONFIRMED'")
    suspend fun firstPlayTime(): Long?

    @Query("SELECT DISTINCT date(started_at / 1000, 'unixepoch', 'localtime') AS d FROM scrobbles WHERE status = 'CONFIRMED' ORDER BY d")
    suspend fun activeDates(): List<String>

    @Query("SELECT * FROM scrobbles WHERE status = 'CONFIRMED' ORDER BY started_at")
    suspend fun allConfirmedOrdered(): List<ScrobbleEntity>
}

@Dao
interface DailyPlayDao {
    @Upsert
    suspend fun upsert(dailyPlay: DailyPlayEntity)

    @Query("SELECT * FROM daily_plays WHERE track_id = :trackId AND date = :date")
    suspend fun get(trackId: Long, date: String): DailyPlayEntity?

    @Query("DELETE FROM daily_plays")
    suspend fun clear()

    /** Reconstruction complète depuis les scrobbles confirmés (jour local). */
    @Query(
        """
        INSERT INTO daily_plays (track_id, artist_id, album_id, date, play_count, total_duration_ms)
        SELECT track_id, artist_id, album_id,
               date(started_at / 1000, 'unixepoch', 'localtime') AS d,
               COUNT(*), SUM(duration_listened_ms)
        FROM scrobbles WHERE status = 'CONFIRMED'
        GROUP BY track_id, d
        """
    )
    suspend fun rebuildFromScrobbles()

    @Query("SELECT DISTINCT date FROM daily_plays ORDER BY date")
    suspend fun allDates(): List<String>

    @Query("SELECT IFNULL(SUM(play_count), 0) FROM daily_plays WHERE date BETWEEN :from AND :to")
    suspend fun playsBetween(from: String, to: String): Int

    @Query("SELECT IFNULL(SUM(total_duration_ms), 0) FROM daily_plays WHERE date BETWEEN :from AND :to")
    suspend fun durationBetween(from: String, to: String): Long

    /** Record : le titre le plus écouté en une seule journée. */
    @Query("SELECT d.*, t.title AS title FROM daily_plays d JOIN tracks t ON t.track_id = d.track_id ORDER BY d.play_count DESC, d.total_duration_ms DESC LIMIT 1")
    fun bestTrackDay(): Flow<BestTrackDay?>

    @Query("SELECT MIN(date) FROM daily_plays")
    suspend fun firstDate(): String?
    /** Toutes les lignes (titre, artiste principal, album, jour) avec au moins une écoute — base des séries d'écoute (record 28). */
    @Query("SELECT track_id, artist_id, album_id, date FROM daily_plays WHERE play_count > 0 ORDER BY date") suspend fun allEntityDays(): List<DailyEntityRow>
    @Query("SELECT date, SUM(play_count) AS play_count FROM daily_plays WHERE track_id = :id GROUP BY date ORDER BY date") suspend fun seriesForTrack(id: Long): List<DayCount>
    @Query("SELECT date, SUM(play_count) AS play_count FROM daily_plays WHERE album_id = :id GROUP BY date ORDER BY date") suspend fun seriesForAlbum(id: Long): List<DayCount>
    @Query("SELECT date, SUM(play_count) AS play_count FROM daily_plays WHERE artist_id = :id GROUP BY date ORDER BY date") suspend fun seriesForArtist(id: Long): List<DayCount>
    @Query("SELECT DISTINCT date FROM daily_plays WHERE date BETWEEN :from AND :to ORDER BY date") suspend fun activeDatesBetween(from: String, to: String): List<String>
    @Query("SELECT artist_id AS id, SUM(play_count) AS plays, SUM(total_duration_ms) AS duration_ms FROM daily_plays WHERE date BETWEEN :from AND :to GROUP BY artist_id") suspend fun artistPlaysBetween(from: String, to: String): List<IdCount>
    @Query("SELECT track_id AS id, SUM(play_count) AS plays, SUM(total_duration_ms) AS duration_ms FROM daily_plays WHERE date BETWEEN :from AND :to GROUP BY track_id") suspend fun trackPlaysBetween(from: String, to: String): List<IdCount>
    @Query("SELECT artist_id, COUNT(DISTINCT substr(date, 1, 7)) AS months, SUM(play_count) AS plays FROM daily_plays WHERE date BETWEEN :from AND :to GROUP BY artist_id ORDER BY months DESC, plays DESC, SUM(total_duration_ms) DESC LIMIT 1") suspend fun mostLoyalArtist(from: String, to: String): ArtistMonths?
}

@Dao
interface DailyStatsDao {
    @Upsert
    suspend fun upsert(stats: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun forDate(date: String): Flow<DailyStatsEntity?>

    /** Record : la journée avec le plus d'écoutes. */
    @Query("SELECT * FROM daily_stats ORDER BY play_count DESC, total_duration_ms DESC LIMIT 1")
    fun bestDay(): Flow<DailyStatsEntity?>

    @Query("DELETE FROM daily_stats")
    suspend fun clear()

    @Query(
        """
        INSERT INTO daily_stats (date, play_count, total_duration_ms, distinct_artists, distinct_albums, distinct_tracks)
        SELECT date(s.started_at / 1000, 'unixepoch', 'localtime') AS d,
               COUNT(*), SUM(s.duration_listened_ms),
               (SELECT COUNT(DISTINCT ta.artist_id) FROM scrobbles s2 JOIN track_artists ta ON ta.track_id = s2.track_id
                WHERE s2.status = 'CONFIRMED' AND date(s2.started_at / 1000, 'unixepoch', 'localtime') = date(s.started_at / 1000, 'unixepoch', 'localtime')),
               COUNT(DISTINCT s.album_id), COUNT(DISTINCT s.track_id)
        FROM scrobbles s WHERE s.status = 'CONFIRMED'
        GROUP BY d
        """
    )
    suspend fun rebuildFromScrobbles()
}

@Dao
interface DailyStreakDao {
    @Upsert
    suspend fun upsert(streak: DailyStreakEntity)

    @Upsert
    suspend fun upsertAll(streaks: List<DailyStreakEntity>)

    @Query("SELECT * FROM daily_streaks ORDER BY date DESC LIMIT 1")
    fun latest(): Flow<DailyStreakEntity?>

    @Query("SELECT * FROM daily_streaks ORDER BY date DESC LIMIT 1")
    suspend fun latestOnce(): DailyStreakEntity?

    @Query("DELETE FROM daily_streaks")
    suspend fun clear()
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity): Long
    @Update suspend fun update(session: SessionEntity)
    @Query("SELECT * FROM sessions ORDER BY started_at DESC LIMIT 1") suspend fun latest(): SessionEntity?
    @Query("SELECT * FROM sessions ORDER BY total_duration_ms DESC LIMIT 1") suspend fun longest(): SessionEntity?
    @Query("SELECT * FROM sessions WHERE started_at BETWEEN :fromMs AND :toMs ORDER BY total_duration_ms DESC LIMIT 1") suspend fun longestBetween(fromMs: Long, toMs: Long): SessionEntity?
    @Query("DELETE FROM sessions") suspend fun clear()
}

@Dao
interface PendingQueueDao {
    @Insert suspend fun insert(item: PendingQueueEntity): Long
    @Query("SELECT * FROM pending_queue WHERE status = 'PENDING' AND expires_at > :now ORDER BY started_at") suspend fun pending(now: Long = System.currentTimeMillis()): List<PendingQueueEntity>
    @Query("DELETE FROM pending_queue WHERE queue_id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM pending_queue WHERE expires_at <= :now") suspend fun purgeExpired(now: Long = System.currentTimeMillis())
}

@Dao
interface NowPlayingDao {
    @Upsert suspend fun upsert(nowPlaying: NowPlayingEntity)
    @Query("SELECT * FROM now_playing WHERE id = 1") fun observe(): Flow<NowPlayingEntity?>
    @Query("SELECT * FROM tracks WHERE track_id = :id") suspend fun track(id: Long): TrackEntity?
}
