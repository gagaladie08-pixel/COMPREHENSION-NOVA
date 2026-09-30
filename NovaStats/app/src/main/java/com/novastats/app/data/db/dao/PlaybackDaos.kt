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

data class PeriodSummary(
    @androidx.room.ColumnInfo(name = "play_count") val playCount: Int,
    @androidx.room.ColumnInfo(name = "total_duration_ms") val totalDurationMs: Long,
    @androidx.room.ColumnInfo(name = "distinct_tracks") val distinctTracks: Int,
    @androidx.room.ColumnInfo(name = "distinct_artists") val distinctArtists: Int,
    @androidx.room.ColumnInfo(name = "distinct_albums") val distinctAlbums: Int,
    @androidx.room.ColumnInfo(name = "active_days") val activeDays: Int
)

@Dao
interface ScrobbleDao {
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
        SELECT s.*, t.title AS title, a.name AS artist_name, t.cover_url AS cover_url
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

    @Query(
        """
        SELECT COUNT(*) AS play_count,
               IFNULL(SUM(duration_listened_ms), 0) AS total_duration_ms,
               COUNT(DISTINCT track_id) AS distinct_tracks,
               COUNT(DISTINCT artist_id) AS distinct_artists,
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

    @Query("SELECT MIN(date) FROM daily_plays")
    suspend fun firstDate(): String?
}

@Dao
interface DailyStatsDao {
    @Upsert
    suspend fun upsert(stats: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun forDate(date: String): Flow<DailyStatsEntity?>

    @Query("DELETE FROM daily_stats")
    suspend fun clear()

    @Query(
        """
        INSERT INTO daily_stats (date, play_count, total_duration_ms, distinct_artists, distinct_albums, distinct_tracks)
        SELECT date(started_at / 1000, 'unixepoch', 'localtime') AS d,
               COUNT(*), SUM(duration_listened_ms),
               COUNT(DISTINCT artist_id), COUNT(DISTINCT album_id), COUNT(DISTINCT track_id)
        FROM scrobbles WHERE status = 'CONFIRMED'
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
