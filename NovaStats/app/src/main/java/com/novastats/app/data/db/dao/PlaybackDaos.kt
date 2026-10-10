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

data class RelinkRow(
    @androidx.room.ColumnInfo(name = "scrobble_id") val scrobbleId: Long,
    @androidx.room.ColumnInfo(name = "track_id") val trackId: Long,
    @androidx.room.ColumnInfo(name = "artist_id") val artistId: Long,
    @androidx.room.ColumnInfo(name = "album_id") val albumId: Long?,
    @androidx.room.ColumnInfo(name = "started_at") val startedAt: Long,
    @androidx.room.ColumnInfo(name = "raw_title") val rawTitle: String?,
    @androidx.room.ColumnInfo(name = "raw_artist") val rawArtist: String?,
    @androidx.room.ColumnInfo(name = "raw_album") val rawAlbum: String?
)

data class DayCount(val date: String, @androidx.room.ColumnInfo(name = "play_count") val playCount: Int)
data class DailyEntityRow(@androidx.room.ColumnInfo(name = "track_id") val trackId: Long, @androidx.room.ColumnInfo(name = "artist_id") val artistId: Long, @androidx.room.ColumnInfo(name = "album_id") val albumId: Long?, val date: String)
data class IdCount(val id: Long, val plays: Int, @androidx.room.ColumnInfo(name = "duration_ms") val durationMs: Long)
data class ArtistMonths(@androidx.room.ColumnInfo(name = "artist_id") val artistId: Long, val months: Int, val plays: Int)

/** Ligne d'un classement annuel (Year-End Charts). */
data class YearEndRow(
    @androidx.room.ColumnInfo(name = "id") val id: Long,
    @androidx.room.ColumnInfo(name = "name") val name: String,
    @androidx.room.ColumnInfo(name = "subtitle") val subtitle: String?,
    @androidx.room.ColumnInfo(name = "image_url") val imageUrl: String?,
    @androidx.room.ColumnInfo(name = "plays") val plays: Int,
    @androidx.room.ColumnInfo(name = "duration_ms") val durationMs: Long
)

data class SourceCount(
    @androidx.room.ColumnInfo(name = "name") val name: String?,
    @androidx.room.ColumnInfo(name = "plays") val plays: Int
)

data class BestTrackDay(
    @Embedded val daily: DailyPlayEntity,
    @androidx.room.ColumnInfo(name = "title") val title: String
)

data class PeriodSummary(
    @androidx.room.ColumnInfo(name = "play_count") val playCount: Long,
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

    @Query("SELECT * FROM scrobbles WHERE artist_id = :artistId AND status = 'CONFIRMED' ORDER BY started_at")
    suspend fun allOfArtist(artistId: Long): List<ScrobbleEntity>

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

    @Query("UPDATE scrobbles SET track_id = :trackId, artist_id = :artistId, album_id = :albumId, confidence_score = :confidenceScore, needs_review = :needsReview, review_reason = :reviewReason WHERE scrobble_id = :scrobbleId")
    suspend fun restoreReviewFixState(scrobbleId: Long, trackId: Long, artistId: Long, albumId: Long?, confidenceScore: Int, needsReview: Boolean, reviewReason: String?)

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
    /** Fusion : une écoute de [from] au même instant qu'une écoute de [into] est la même écoute journalisée deux fois → supprimée. */
    @Query("DELETE FROM scrobbles WHERE track_id = :from AND started_at IN (SELECT started_at FROM scrobbles WHERE track_id = :into)") suspend fun dropClashing(from: Long, into: Long)
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
    @Query(
        """
        WITH RECURSIVE fam AS (
            SELECT :trackId AS t
            UNION ALL
            SELECT v.track_id FROM tracks v JOIN fam f ON v.original_track_id = f.t
        )
        SELECT started_at FROM scrobbles WHERE status = 'CONFIRMED' AND track_id IN (SELECT t FROM fam) ORDER BY started_at LIMIT 1 OFFSET :n - 1
        """
    )
    suspend fun nthPlayOfTrack(trackId: Long, n: Int): Long?

    /** Écoutes dont le titre doit être re-résolu (liens artistes / versions) : valeurs brutes du lecteur si connues. */
    @Query("SELECT scrobble_id, track_id, artist_id, album_id, started_at, raw_title, raw_artist, raw_album FROM scrobbles WHERE status = 'CONFIRMED' ORDER BY started_at")
    suspend fun allForRelink(): List<RelinkRow>
    @Query("UPDATE scrobbles SET track_id = :trackId, artist_id = :artistId, album_id = :albumId WHERE scrobble_id = :id")
    suspend fun relink(id: Long, trackId: Long, artistId: Long, albumId: Long?)

    /** Écoute au même instant sur le titre cible OU une version de son groupe (original + versions liées) — le titre exact d'abord. */
    @Query(
        """
        WITH RECURSIVE fam AS (
            SELECT :rootId AS t
            UNION ALL
            SELECT v.track_id FROM tracks v JOIN fam f ON v.original_track_id = f.t
        )
        SELECT * FROM scrobbles WHERE started_at = :startedAt
          AND (track_id = :trackId OR track_id IN (SELECT t FROM fam))
        ORDER BY (track_id = :trackId) DESC LIMIT 1
        """
    )
    suspend fun findInGroupAt(trackId: Long, rootId: Long, startedAt: Long): ScrobbleEntity?

    /** Fusion de deux titres : les écoutes de `from` au même instant qu'une écoute de `into` sont des doublons. */
    @Query("DELETE FROM scrobbles WHERE track_id = :from AND started_at IN (SELECT started_at FROM scrobbles WHERE track_id = :into)")
    suspend fun dropDuplicatesAgainst(from: Long, into: Long)
    @Query("UPDATE scrobbles SET track_id = :into WHERE track_id = :from")
    suspend fun moveAll(from: Long, into: Long)

    /** Réparation à l'import : déplace l'écoute vers la bonne version et mémorise les valeurs brutes du fichier. */
    @Query("UPDATE scrobbles SET track_id = :trackId, artist_id = :artistId, album_id = :albumId, raw_title = :rawTitle, raw_artist = :rawArtist, raw_album = :rawAlbum WHERE scrobble_id = :id")
    suspend fun repair(id: Long, trackId: Long, artistId: Long, albumId: Long?, rawTitle: String?, rawArtist: String?, rawAlbum: String?)

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

    /* ---------- Santé de la détection ---------- */

    @Query("SELECT COUNT(*) FROM scrobbles WHERE status = 'CONFIRMED' AND started_at >= :from")
    fun countSince(from: Long): Flow<Int>

    @Query("SELECT MAX(started_at) FROM scrobbles WHERE status = 'CONFIRMED'")
    fun lastPlayAt(): Flow<Long?>

    @Query("""SELECT source_app AS name, COUNT(*) AS plays FROM scrobbles
             WHERE source_app IS NOT NULL AND source_app <> ''
             GROUP BY source_app ORDER BY plays DESC LIMIT 10""")
    fun sourceCounts(): Flow<List<SourceCount>>

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

    /**
     * 🧟 Albums fantômes : les écoutes pointant vers un album qui n'a PLUS aucun titre rattaché
     * sont réattribuées à l'album ACTUEL de leur titre (jamais forcées si le titre n'a pas d'album).
     */
    @Query(
        """
        UPDATE scrobbles SET album_id = (SELECT t.album_id FROM tracks t WHERE t.track_id = scrobbles.track_id)
        WHERE album_id IN (SELECT a.album_id FROM albums a WHERE NOT EXISTS (SELECT 1 FROM tracks t WHERE t.album_id = a.album_id))
          AND (SELECT t2.album_id FROM tracks t2 WHERE t2.track_id = scrobbles.track_id) IS NOT NULL
        """
    )
    suspend fun reattachOrphanAlbumPlays(): Int

    /** Reconstruction complète depuis les scrobbles confirmés (jour local). */
    @Query(
        """
        WITH RECURSIVE true_root AS (
            SELECT track_id, track_id AS root_id FROM tracks WHERE original_track_id IS NULL
            UNION ALL
            SELECT t.track_id, r.root_id FROM tracks t JOIN true_root r ON t.original_track_id = r.track_id
        )
        INSERT INTO daily_plays (track_id, root_id, artist_id, album_id, date, play_count, total_duration_ms)
        SELECT s.track_id, IFNULL(r.root_id, s.track_id), s.artist_id, s.album_id,
               date(s.started_at / 1000, 'unixepoch', 'localtime') AS d,
               COUNT(*), SUM(s.duration_listened_ms)
        FROM scrobbles s LEFT JOIN true_root r ON r.track_id = s.track_id WHERE s.status = 'CONFIRMED'
        GROUP BY s.track_id, d
        """
    )
    suspend fun rebuildFromScrobbles()

    @Query("SELECT DISTINCT date FROM daily_plays ORDER BY date")
    suspend fun allDates(): List<String>

    @Query("SELECT IFNULL(SUM(play_count), 0) FROM daily_plays WHERE date BETWEEN :from AND :to")
    suspend fun playsBetween(from: String, to: String): Int

    @Query("SELECT IFNULL(SUM(total_duration_ms), 0) FROM daily_plays WHERE date BETWEEN :from AND :to")
    suspend fun durationBetween(from: String, to: String): Long

    /* ---------- 🏆 Year-End Charts (mêmes règles que le Billboard) ---------- */

    @Query(
        """SELECT IFNULL(SUM(play_count), 0) AS play_count, IFNULL(SUM(total_duration_ms), 0) AS total_duration_ms,
                  (SELECT COUNT(DISTINCT y.root_id) FROM daily_plays y WHERE y.date BETWEEN :from AND :to) AS distinct_tracks,
                  (SELECT COUNT(DISTINCT ta.artist_id) FROM daily_plays y2
                     JOIN track_artists ta ON ta.track_id = y2.track_id
                     JOIN artists a ON a.artist_id = ta.artist_id AND a.is_merged = 0
                   WHERE y2.date BETWEEN :from AND :to) AS distinct_artists,
                  (SELECT COUNT(DISTINCT y3.album_id) FROM daily_plays y3
                     JOIN albums al ON al.album_id = y3.album_id AND al.is_compilation = 0
                   WHERE y3.date BETWEEN :from AND :to AND y3.album_id IS NOT NULL) AS distinct_albums,
                  COUNT(DISTINCT date) AS active_days
           FROM daily_plays WHERE date BETWEEN :from AND :to"""
    )
    suspend fun yearSummary(from: String, to: String): PeriodSummary

    @Query("SELECT date, SUM(play_count) AS play_count FROM daily_plays WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY play_count DESC LIMIT 1")
    suspend fun bestDayBetween(from: String, to: String): DayCount?

    /** Record : le titre le plus écouté en une seule journée. */
    @Query("SELECT d.*, t.title AS title FROM daily_plays d JOIN tracks t ON t.track_id = d.track_id ORDER BY d.play_count DESC, d.total_duration_ms DESC LIMIT 1")
    fun bestTrackDay(): Flow<BestTrackDay?>

    @Query("SELECT MIN(date) FROM daily_plays")
    suspend fun firstDate(): String?
    /** Toutes les lignes (titre, artiste principal, album, jour) avec au moins une écoute — base des séries d'écoute (record 28). */
    @Query("SELECT root_id AS track_id, artist_id, album_id, date FROM daily_plays WHERE play_count > 0 ORDER BY date") suspend fun allEntityDays(): List<DailyEntityRow>
    /** 🎯 Jours actifs (au moins une écoute) depuis [from] — pour la streak d'écoute en cours. */
    @Query("SELECT date FROM daily_plays WHERE date >= :from GROUP BY date HAVING SUM(play_count) > 0 ORDER BY date")
    suspend fun activeDaysSince(from: String): List<String>

    @Query("SELECT date, SUM(play_count) AS play_count FROM daily_plays WHERE root_id = :id GROUP BY date ORDER BY date") suspend fun seriesForTrack(id: Long): List<DayCount>
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

    /** Les journées depuis :fromIso inclus, en ordre chronologique (sparkline d'activité de l'Accueil). */
    @Query("SELECT * FROM daily_stats WHERE date >= :fromIso ORDER BY date ASC")
    fun since(fromIso: String): Flow<List<DailyStatsEntity>>

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
