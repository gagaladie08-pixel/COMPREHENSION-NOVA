package com.novastats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.TrackAlbumEntity
import com.novastats.app.data.db.entity.TrackArtistEntity
import com.novastats.app.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/* ---------- Résultats de classement (Stats / Billboard) ---------- */

data class TrackArtistPair(val trackId: Long, val artistId: Long)

data class RankedTrack(
    @Embedded val track: TrackEntity,
    @androidx.room.ColumnInfo(name = "artist_name") val artistName: String,
    @androidx.room.ColumnInfo(name = "album_title") val albumTitle: String?,
    @androidx.room.ColumnInfo(name = "period_plays") val periodPlays: Int,
    @androidx.room.ColumnInfo(name = "period_duration_ms") val periodDurationMs: Long
)

data class RankedArtist(
    @Embedded val artist: ArtistEntity,
    @androidx.room.ColumnInfo(name = "period_plays") val periodPlays: Int,
    @androidx.room.ColumnInfo(name = "period_duration_ms") val periodDurationMs: Long
)

data class RankedAlbum(
    @Embedded val album: AlbumEntity,
    @androidx.room.ColumnInfo(name = "artist_name") val artistName: String,
    @androidx.room.ColumnInfo(name = "period_plays") val periodPlays: Int,
    @androidx.room.ColumnInfo(name = "period_duration_ms") val periodDurationMs: Long
)

/** Titre d'un artiste sur une période avec sa POSITION dans le classement titres de la période (même au-delà du Top 300). */
data class RankedTrackPos(
    @Embedded val track: TrackEntity,
    @androidx.room.ColumnInfo(name = "album_title") val albumTitle: String?,
    @androidx.room.ColumnInfo(name = "period_plays") val periodPlays: Int,
    @androidx.room.ColumnInfo(name = "period_duration_ms") val periodDurationMs: Long,
    @androidx.room.ColumnInfo(name = "period_rank") val periodRank: Int
)

/** Agrégats d'une entité (titre / artiste / album) sur une période [from, to] — popups de détail « période choisie ». */
data class PeriodEntityStats(
    val plays: Int,
    @androidx.room.ColumnInfo(name = "duration_ms") val durationMs: Long,
    @androidx.room.ColumnInfo(name = "first_date") val firstDate: String?,
    @androidx.room.ColumnInfo(name = "last_date") val lastDate: String?,
    @androidx.room.ColumnInfo(name = "active_days") val activeDays: Int,
    @androidx.room.ColumnInfo(name = "distinct_tracks") val distinctTracks: Int,
    @androidx.room.ColumnInfo(name = "distinct_albums") val distinctAlbums: Int
)

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(track: TrackEntity): Long

    @Update
    suspend fun update(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE track_id = :id")
    suspend fun getById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE title = :title AND artist_id = :artistId LIMIT 1")
    suspend fun findByTitleAndArtist(title: String, artistId: Long): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    /** Titres sans pochette, hors ceux déjà tentés récemment (cache négatif api_cache). Les plus écoutés d'abord. */
    @Query(
        """
        SELECT * FROM tracks WHERE cover_url IS NULL AND track_id NOT IN
            (SELECT entity_id FROM api_cache WHERE entity_type = 'TRACK' AND data_type = 'COVER' AND expires_at > :now)
        ORDER BY play_count DESC LIMIT :limit
        """
    )
    suspend fun missingCover(now: Long, limit: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE cover_url IS NULL") fun missingCoverCount(): Flow<Int>
    /** Contexte bibliothèque (vérification des pochettes) : titres connus d'un artiste / d'un album. */
    @Query("SELECT title FROM tracks WHERE artist_id = :artistId") suspend fun titlesOfArtist(artistId: Long): List<String>
    /** Titres où l'artiste est crédité (principal ou featuring) + titres des fiches homonymes (« X feat. Rihanna », « Rihanna, Y »). */
    @Query("SELECT DISTINCT t.title FROM tracks t JOIN track_artists ta ON ta.track_id = t.track_id JOIN artists a ON a.artist_id = ta.artist_id WHERE ta.artist_id = :artistId OR LOWER(a.name) LIKE '%' || LOWER(:name) || '%'")
    suspend fun titlesCreditedTo(artistId: Long, name: String): List<String>
    @Query("SELECT title FROM tracks WHERE album_id = :albumId") suspend fun titlesOfAlbum(albumId: Long): List<String>
    /** « Tout ré-enrichir » : tous les titres sauf ceux dont la pochette a été choisie à la main. */
    @Query("SELECT track_id FROM tracks WHERE cover_source IS NULL OR cover_source != 'USER' ORDER BY play_count DESC") suspend fun idsForRefresh(): List<Long>

    /**
     * Classement Global (all-time). Tri : écoutes puis temps d'écoute (règle d'égalité).
     */
    @Query(
        """
        SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name, al.title AS album_title,
               t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms
        FROM tracks t
        JOIN artists a ON a.artist_id = t.artist_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE t.play_count > 0 AND t.original_track_id IS NULL
        ORDER BY t.play_count DESC, t.total_duration_ms DESC
        LIMIT :limit
        """
    )
    fun topAllTime(limit: Int = 300): Flow<List<RankedTrack>>

    /**
     * Classement sur une période calendaire [from, to] (dates ISO incluses), via daily_plays.
     */
    @Query(
        """
        SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name, al.title AS album_title,
               SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN tracks t ON t.track_id = d.root_id
        JOIN artists a ON a.artist_id = t.artist_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE d.date BETWEEN :from AND :to
        GROUP BY d.root_id
        ORDER BY period_plays DESC, period_duration_ms DESC
        LIMIT :limit
        """
    )
    fun topForPeriod(from: String, to: String, limit: Int = 300): Flow<List<RankedTrack>>

    /** Position d'un titre dans le classement d'une période (null = non classé). Égalité départagée par le temps. */
    @Query(
        """
        SELECT (SELECT COUNT(*) + 1 FROM (
                    SELECT root_id, SUM(play_count) AS p, SUM(total_duration_ms) AS d FROM daily_plays
                    WHERE date BETWEEN :from AND :to GROUP BY root_id) x
                WHERE x.p > me.p OR (x.p = me.p AND x.d > me.d))
        FROM (SELECT SUM(play_count) AS p, SUM(total_duration_ms) AS d FROM daily_plays
              WHERE root_id = :trackId AND date BETWEEN :from AND :to) me
        WHERE me.p > 0
        """
    )
    suspend fun rankForPeriod(trackId: Long, from: String, to: String): Int?

    @Query(
        """
        SELECT (SELECT COUNT(*) + 1 FROM tracks t WHERE t.original_track_id IS NULL AND (t.play_count > me.p OR (t.play_count = me.p AND t.total_duration_ms > me.d)))
        FROM (SELECT play_count AS p, total_duration_ms AS d FROM tracks WHERE track_id = :trackId) me
        WHERE me.p > 0
        """
    )
    suspend fun rankAllTime(trackId: Long): Int?

    /** Titres d'un artiste (principal ou featuring), les plus écoutés d'abord. */
    @Query(
        """
        SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name,
               al.title AS album_title, t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms
        FROM tracks t JOIN track_artists ta ON ta.track_id = t.track_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE ta.artist_id = :artistId AND t.play_count > 0
        ORDER BY t.play_count DESC, t.total_duration_ms DESC LIMIT :limit
        """
    )
    suspend fun topOfArtist(artistId: Long, limit: Int = 5): List<RankedTrack>

    /** Titres écoutés d'un album, les plus écoutés d'abord. */
    @Query(
        """
        SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name,
               (SELECT title FROM albums WHERE album_id = t.album_id) AS album_title,
               t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms
        FROM tracks t WHERE t.album_id = :albumId AND t.play_count > 0
        ORDER BY t.play_count DESC, t.total_duration_ms DESC
        """
    )
    suspend fun ofAlbum(albumId: Long): List<RankedTrack>

    /** 🔍 Recherche globale : titres dont le titre ou l'artiste contient [q]. */
    @Query(
        """
        SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name,
               (SELECT title FROM albums WHERE album_id = t.album_id) AS album_title,
               t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms
        FROM tracks t WHERE t.play_count > 0 AND (LOWER(t.title) LIKE '%' || LOWER(:q) || '%' OR t.artist_id IN (SELECT artist_id FROM artists WHERE LOWER(name) LIKE '%' || LOWER(:q) || '%'))
        ORDER BY t.play_count DESC LIMIT :limit
        """
    )
    suspend fun search(q: String, limit: Int = 25): List<RankedTrack>

    /** Agrégats d'un titre sur une période. */
    @Query(
        """
        SELECT IFNULL(SUM(play_count), 0) AS plays, IFNULL(SUM(total_duration_ms), 0) AS duration_ms, MIN(date) AS first_date, MAX(date) AS last_date,
               COUNT(DISTINCT date) AS active_days, COUNT(DISTINCT track_id) AS distinct_tracks, COUNT(DISTINCT album_id) AS distinct_albums
        FROM daily_plays WHERE root_id = :trackId AND date BETWEEN :from AND :to
        """
    )
    suspend fun periodStats(trackId: Long, from: String, to: String): PeriodEntityStats?

    /** Titres d'un artiste (principal ou featuring) sur une période, les plus écoutés d'abord. */
    @Query(
        """
        SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name, al.title AS album_title,
               SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN tracks t ON t.track_id = d.track_id
        JOIN track_artists ta ON ta.track_id = t.track_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE ta.artist_id = :artistId AND d.date BETWEEN :from AND :to
        GROUP BY t.track_id
        ORDER BY period_plays DESC, period_duration_ms DESC LIMIT :limit
        """
    )
    suspend fun topOfArtistForPeriod(artistId: Long, from: String, to: String, limit: Int = 5): List<RankedTrack>

    /**
     * TOUS les titres d'un artiste (main + featuring) écoutés sur la période, avec leur position dans le classement
     * titres de la période (même tri que Stats : écoutes puis temps), sans limite de Top 300.
     */
    @Query(
        """
        SELECT t.*, al.title AS album_title, x.p AS period_plays, x.d AS period_duration_ms,
               (SELECT COUNT(*) + 1 FROM (SELECT root_id, SUM(play_count) AS p, SUM(total_duration_ms) AS d FROM daily_plays
                                          WHERE date BETWEEN :from AND :to GROUP BY root_id) y
                WHERE y.p > g.p OR (y.p = g.p AND y.d > g.d)) AS period_rank
        FROM (SELECT d.root_id AS root_id, SUM(d.play_count) AS p, SUM(d.total_duration_ms) AS d
              FROM daily_plays d JOIN track_artists ta ON ta.track_id = d.track_id
              WHERE ta.artist_id = :artistId AND d.date BETWEEN :from AND :to GROUP BY d.root_id) x
        JOIN (SELECT root_id, SUM(play_count) AS p, SUM(total_duration_ms) AS d FROM daily_plays WHERE date BETWEEN :from AND :to GROUP BY root_id) g ON g.root_id = x.root_id
        JOIN tracks t ON t.track_id = x.root_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        ORDER BY x.p DESC, x.d DESC
        """
    )
    suspend fun allOfArtistForPeriod(artistId: Long, from: String, to: String): List<RankedTrackPos>

    /** Idem all time (cumul `tracks.play_count`, position dans le classement général). */
    @Query(
        """
        SELECT t.*, al.title AS album_title, x.p AS period_plays, x.d AS period_duration_ms,
               (SELECT COUNT(*) + 1 FROM tracks y WHERE y.original_track_id IS NULL AND (y.play_count > t.play_count OR (y.play_count = t.play_count AND y.total_duration_ms > t.total_duration_ms))) AS period_rank
        FROM (SELECT d.root_id AS root_id, SUM(d.play_count) AS p, SUM(d.total_duration_ms) AS d
              FROM daily_plays d JOIN track_artists ta ON ta.track_id = d.track_id
              WHERE ta.artist_id = :artistId GROUP BY d.root_id) x
        JOIN tracks t ON t.track_id = x.root_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE x.p > 0
        ORDER BY x.p DESC, x.d DESC
        """
    )
    suspend fun allOfArtistAllTime(artistId: Long): List<RankedTrackPos>

    /** Titres d'un album écoutés sur une période, les plus écoutés d'abord. */
    @Query(
        """
        SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name, al.title AS album_title,
               SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN tracks t ON t.track_id = d.track_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE t.album_id = :albumId AND d.date BETWEEN :from AND :to
        GROUP BY t.track_id
        ORDER BY period_plays DESC, period_duration_ms DESC
        """
    )
    suspend fun ofAlbumForPeriod(albumId: Long, from: String, to: String): List<RankedTrack>

    /** Radar : titres les plus proches de leur prochain palier de certification (calcul du reste côté Kotlin). */
    @Query("SELECT * FROM tracks WHERE play_count > 0 ORDER BY play_count DESC LIMIT :limit")
    fun mostPlayed(limit: Int = 200): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE play_count > 0")
    suspend fun countPlayed(): Int

    @Query("SELECT * FROM tracks WHERE play_count > 0")
    suspend fun allPlayed(): List<TrackEntity>

    /* ---- Éditeur de données ---- */
    @Query("SELECT t.*, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name, (SELECT title FROM albums WHERE album_id = t.album_id) AS album_title, t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms FROM tracks t ORDER BY t.play_count DESC, t.title LIMIT :limit")
    fun allForEditor(limit: Int = 2000): Flow<List<RankedTrack>>

    @Query("SELECT t.*, (SELECT name FROM artists WHERE artist_id = t.artist_id) AS artist_name, (SELECT title FROM albums WHERE album_id = t.album_id) AS album_title, t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms FROM tracks t WHERE t.needs_review = 1 OR t.confidence_score < 70 ORDER BY t.confidence_score, t.play_count DESC")
    fun needingReview(): Flow<List<RankedTrack>>

    @Query("UPDATE tracks SET title = :title WHERE track_id = :id") suspend fun rename(id: Long, title: String)
    @Query("UPDATE tracks SET album_id = :albumId WHERE track_id = :id") suspend fun setAlbum(id: Long, albumId: Long?)
    @Query("UPDATE tracks SET artist_id = :artistId WHERE track_id = :id") suspend fun setPrimaryArtist(id: Long, artistId: Long)
    @Query("UPDATE tracks SET artist_id = :into WHERE artist_id = :from") suspend fun moveArtist(from: Long, into: Long)
    @Query("UPDATE tracks SET album_id = :into WHERE album_id = :from") suspend fun moveAlbum(from: Long, into: Long)
    @Query("UPDATE tracks SET needs_review = 0, confidence_score = 100 WHERE track_id = :id") suspend fun markReviewed(id: Long)
    /** 🟡 À vérifier : correspondance API acceptée avec flag (score 70-89). */
    @Query("SELECT t.*, (SELECT name FROM artists WHERE artist_id = t.artist_id) AS artist_name, (SELECT title FROM albums WHERE album_id = t.album_id) AS album_title, t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms FROM tracks t WHERE t.needs_review = 1 AND t.confidence_score BETWEEN 70 AND 89 ORDER BY t.play_count DESC")
    fun flaggedForVerification(): Flow<List<RankedTrack>>
    /** 🔴 Titres dont les APIs sont épuisées avec un score < 70 (placeholder). */
    @Query("SELECT t.*, (SELECT name FROM artists WHERE artist_id = t.artist_id) AS artist_name, (SELECT title FROM albums WHERE album_id = t.album_id) AS album_title, t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms FROM tracks t WHERE t.confidence_score < 70 ORDER BY t.play_count DESC")
    fun lowConfidence(): Flow<List<RankedTrack>>
    @Query("UPDATE tracks SET cover_url = :url, cover_source = 'USER' WHERE track_id = :id") suspend fun setCover(id: Long, url: String?)
    @Query("UPDATE tracks SET title = :title, album_id = :albumId WHERE track_id = :id") suspend fun setTitleAndAlbum(id: Long, title: String, albumId: Long?)
    @Query("DELETE FROM tracks WHERE track_id = :id") suspend fun delete(id: Long)

    /** Recalcule les agrégats des titres à partir des scrobbles confirmés (après import / édition). */
    @Query(
        """
        UPDATE tracks SET
            play_count = (SELECT COUNT(*) FROM scrobbles s WHERE s.status = 'CONFIRMED' AND (s.track_id = tracks.track_id OR s.track_id IN (SELECT v.track_id FROM tracks v WHERE v.original_track_id = tracks.track_id))),
            total_duration_ms = (SELECT IFNULL(SUM(s.duration_listened_ms), 0) FROM scrobbles s WHERE s.status = 'CONFIRMED' AND (s.track_id = tracks.track_id OR s.track_id IN (SELECT v.track_id FROM tracks v WHERE v.original_track_id = tracks.track_id))),
            first_played_at = (SELECT MIN(s.started_at) FROM scrobbles s WHERE s.status = 'CONFIRMED' AND (s.track_id = tracks.track_id OR s.track_id IN (SELECT v.track_id FROM tracks v WHERE v.original_track_id = tracks.track_id))),
            last_played_at = (SELECT MAX(s.started_at) FROM scrobbles s WHERE s.status = 'CONFIRMED' AND (s.track_id = tracks.track_id OR s.track_id IN (SELECT v.track_id FROM tracks v WHERE v.original_track_id = tracks.track_id)))
        """
    )
    suspend fun recomputeAggregates()

    /** Écoutes PROPRES d'un titre (sans ses versions liées) — répartition par version dans les popups. */
    @Query("SELECT COUNT(*) FROM scrobbles WHERE track_id = :trackId AND status = 'CONFIRMED'")
    suspend fun ownPlays(trackId: Long): Int

    /** Versions liées à un titre racine (remix featuring, version avec invité). */
    @Query("SELECT * FROM tracks WHERE original_track_id = :rootId ORDER BY play_count DESC")
    suspend fun versionsOf(rootId: Long): List<TrackEntity>

    /** Titre racine portant exactement ce titre, crédité à l'un des artistes donnés (original d'un remix dont l'artiste principal diffère). */
    @Query(
        """
        SELECT * FROM tracks t WHERE t.title = :title AND t.original_track_id IS NULL
          AND (t.artist_id IN (:artistIds) OR t.track_id IN (SELECT track_id FROM track_artists WHERE artist_id IN (:artistIds)))
        ORDER BY t.play_count DESC LIMIT 1
        """
    )
    suspend fun findRootByTitleAnyArtist(title: String, artistIds: List<Long>): TrackEntity?

    /** Versions orphelines (« Titre (feat. X) » / « Titre (with X) » sans original) partageant un artiste → à rattacher au nouveau root. */
    @Query(
        """
        SELECT * FROM tracks t WHERE t.original_track_id IS NULL AND t.track_id != :rootId AND t.title LIKE :titlePrefix || ' (%'
          AND (t.artist_id IN (:artistIds) OR t.track_id IN (SELECT track_id FROM track_artists WHERE artist_id IN (:artistIds)))
        """
    )
    suspend fun orphanVersions(rootId: Long, titlePrefix: String, artistIds: List<Long>): List<TrackEntity>

    @Query("UPDATE tracks SET original_track_id = :rootId, is_remix = 1 WHERE track_id = :trackId")
    suspend fun linkToRoot(trackId: Long, rootId: Long)

    @Query("UPDATE tracks SET title = :title WHERE track_id = :trackId")
    suspend fun setTitle(trackId: Long, title: String)
    @Query("SELECT * FROM tracks WHERE album_id = :albumId") suspend fun inAlbum(albumId: Long): List<TrackEntity>
    /** Les titres dont des écoutes pointent vers cet album y sont rattachés (album partagé : toutes les versions). */
    @Query("UPDATE tracks SET album_id = :albumId WHERE track_id IN (SELECT DISTINCT s.track_id FROM scrobbles s WHERE s.album_id = :albumId AND s.status = 'CONFIRMED')")
    suspend fun adoptTracksOf(albumId: Long)

    /** Roots « solo » sans aucune écoute confirmée propre mais avec des versions liées : la version solo n'existe pas → à replier. */
    @Query(
        """
        SELECT * FROM tracks r WHERE r.original_track_id IS NULL
          AND EXISTS (SELECT 1 FROM tracks v WHERE v.original_track_id = r.track_id)
          AND NOT EXISTS (SELECT 1 FROM scrobbles s WHERE s.track_id = r.track_id AND s.status = 'CONFIRMED')
        """
    )
    suspend fun emptyRootsWithVersions(): List<TrackEntity>

    @Query("UPDATE tracks SET cover_url = :url, cover_source = :source WHERE track_id = :trackId AND cover_url IS NULL")
    suspend fun fillCover(trackId: Long, url: String?, source: String?)

    /** Aplatit les chaînes (version d'une version → rattachée au root final). */
    @Query("UPDATE tracks SET original_track_id = (SELECT o.original_track_id FROM tracks o WHERE o.track_id = tracks.original_track_id) WHERE original_track_id IN (SELECT track_id FROM tracks WHERE original_track_id IS NOT NULL)")
    suspend fun flattenRoots()

    /** Rang de découverte = ordre de première écoute. */
    @Query(
        """
        UPDATE tracks SET discovery_rank = (
            SELECT COUNT(*) FROM tracks t2
            WHERE t2.first_played_at IS NOT NULL AND t2.first_played_at <= tracks.first_played_at
        ) WHERE first_played_at IS NOT NULL
        """
    )
    suspend fun recomputeDiscoveryRanks()
}

@Dao
interface ArtistDao {
    /** 🔍 Recherche globale. */
    @Query("SELECT * FROM artists WHERE is_merged = 0 AND LOWER(name) LIKE '%' || LOWER(:q) || '%' ORDER BY play_count DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 15): List<ArtistEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(artist: ArtistEntity): Long

    @Update
    suspend fun update(artist: ArtistEntity)

    @Query("SELECT * FROM artists WHERE artist_id = :id")
    suspend fun getById(id: Long): ArtistEntity?

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByNameNoCase(name: String): ArtistEntity?

    @Query("SELECT COUNT(*) FROM artists")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM artists WHERE play_count > 0 ORDER BY play_count DESC, total_duration_ms DESC")
    fun allByPlays(): Flow<List<ArtistEntity>>

    @Query(
        """
        SELECT * FROM artists WHERE photo_url IS NULL AND is_merged = 0 AND artist_id NOT IN
            (SELECT entity_id FROM api_cache WHERE entity_type = 'ARTIST' AND data_type = 'PHOTO' AND expires_at > :now)
        ORDER BY play_count DESC LIMIT :limit
        """
    )
    suspend fun missingPhoto(now: Long, limit: Int): List<ArtistEntity>

    @Query("SELECT COUNT(*) FROM artists WHERE photo_url IS NULL AND is_merged = 0") fun missingPhotoCount(): Flow<Int>
    @Query("SELECT artist_id FROM artists WHERE is_merged = 0 AND (photo_source IS NULL OR photo_source != 'USER') ORDER BY play_count DESC") suspend fun idsForRefresh(): List<Long>

    @Query(
        """
        SELECT a.*, a.play_count AS period_plays, a.total_duration_ms AS period_duration_ms
        FROM artists a WHERE a.play_count > 0 AND a.is_merged = 0
        ORDER BY a.play_count DESC, a.total_duration_ms DESC LIMIT :limit
        """
    )
    fun topAllTime(limit: Int = 300): Flow<List<RankedArtist>>

    /** Chaque artiste présent sur un titre (main + featured) reçoit l'écoute → jointure track_artists. */
    @Query(
        """
        SELECT a.*, SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN track_artists ta ON ta.track_id = d.track_id
        JOIN artists a ON a.artist_id = ta.artist_id
        WHERE d.date BETWEEN :from AND :to AND a.is_merged = 0
        GROUP BY a.artist_id
        ORDER BY period_plays DESC, period_duration_ms DESC
        LIMIT :limit
        """
    )
    fun topForPeriod(from: String, to: String, limit: Int = 300): Flow<List<RankedArtist>>

    @Query(
        """
        UPDATE artists SET
            play_count = (SELECT COUNT(*) FROM scrobbles s JOIN track_artists ta ON ta.track_id = s.track_id
                          WHERE ta.artist_id = artists.artist_id AND s.status = 'CONFIRMED'),
            total_duration_ms = (SELECT IFNULL(SUM(s.duration_listened_ms), 0) FROM scrobbles s JOIN track_artists ta ON ta.track_id = s.track_id
                          WHERE ta.artist_id = artists.artist_id AND s.status = 'CONFIRMED'),
            distinct_tracks = (SELECT COUNT(DISTINCT ta.track_id) FROM track_artists ta JOIN tracks t ON t.track_id = ta.track_id
                          WHERE ta.artist_id = artists.artist_id AND t.play_count > 0),
            distinct_albums = (SELECT COUNT(*) FROM albums al WHERE al.artist_id = artists.artist_id AND al.play_count > 0),
            first_played_at = (SELECT MIN(s.started_at) FROM scrobbles s JOIN track_artists ta ON ta.track_id = s.track_id
                          WHERE ta.artist_id = artists.artist_id AND s.status = 'CONFIRMED'),
            last_played_at = (SELECT MAX(s.started_at) FROM scrobbles s JOIN track_artists ta ON ta.track_id = s.track_id
                          WHERE ta.artist_id = artists.artist_id AND s.status = 'CONFIRMED')
        """
    )
    suspend fun recomputeAggregates()

    @Query(
        """
        SELECT (SELECT COUNT(*) + 1 FROM (
                    SELECT ta.artist_id, SUM(d.play_count) AS p, SUM(d.total_duration_ms) AS d
                    FROM daily_plays d JOIN track_artists ta ON ta.track_id = d.track_id
                    WHERE d.date BETWEEN :from AND :to GROUP BY ta.artist_id) x
                WHERE x.p > me.p OR (x.p = me.p AND x.d > me.d))
        FROM (SELECT SUM(d.play_count) AS p, SUM(d.total_duration_ms) AS d
              FROM daily_plays d JOIN track_artists ta ON ta.track_id = d.track_id
              WHERE ta.artist_id = :artistId AND d.date BETWEEN :from AND :to) me
        WHERE me.p > 0
        """
    )
    suspend fun rankForPeriod(artistId: Long, from: String, to: String): Int?

    /** Écoutes d'un artiste sur une période (main + featuring). */
    @Query(
        """
        SELECT IFNULL(SUM(d.play_count), 0) FROM daily_plays d JOIN track_artists ta ON ta.track_id = d.track_id
        WHERE ta.artist_id = :artistId AND d.date BETWEEN :from AND :to
        """
    )
    suspend fun playsForPeriod(artistId: Long, from: String, to: String): Int

    /** Agrégats d'un artiste (main + featuring) sur une période. */
    @Query(
        """
        SELECT IFNULL(SUM(d.play_count), 0) AS plays, IFNULL(SUM(d.total_duration_ms), 0) AS duration_ms, MIN(d.date) AS first_date, MAX(d.date) AS last_date,
               COUNT(DISTINCT d.date) AS active_days, COUNT(DISTINCT d.track_id) AS distinct_tracks, COUNT(DISTINCT d.album_id) AS distinct_albums
        FROM daily_plays d JOIN track_artists ta ON ta.track_id = d.track_id
        WHERE ta.artist_id = :artistId AND d.date BETWEEN :from AND :to
        """
    )
    suspend fun periodStats(artistId: Long, from: String, to: String): PeriodEntityStats?

    @Query("SELECT * FROM artists WHERE is_merged = 0 ORDER BY play_count DESC, name")
    fun allForEditor(): Flow<List<ArtistEntity>>
    @Query("SELECT * FROM artists WHERE is_merged = 0 ORDER BY play_count DESC") suspend fun allPlayedList(): List<ArtistEntity>

    @Query("UPDATE artists SET name = :name WHERE artist_id = :id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE artists SET photo_url = :url, photo_source = 'USER' WHERE artist_id = :id") suspend fun setPhoto(id: Long, url: String?)
    @Query("UPDATE artists SET is_merged = 1, merged_into_id = :into, play_count = 0 WHERE artist_id = :from") suspend fun markMerged(from: Long, into: Long)

    @Query("UPDATE artists SET pantheon_status = :status, pantheon_date = :date WHERE artist_id = :artistId")
    suspend fun setPantheonStatus(artistId: Long, status: String, date: Long)
}

@Dao
interface AlbumDao {
    /** 🔍 Recherche globale. */
    @Query("SELECT * FROM albums WHERE LOWER(title) LIKE '%' || LOWER(:q) || '%' ORDER BY play_count DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 15): List<AlbumEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE album_id = :id")
    suspend fun getById(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE title = :title AND artist_id = :artistId LIMIT 1")
    suspend fun findByTitleAndArtist(title: String, artistId: Long): AlbumEntity?
    /** Règle 13 : albums homonymes appartenant à l'un des artistes du titre. */
    @Query("SELECT * FROM albums WHERE title = :title AND artist_id IN (:artistIds)")
    suspend fun findByTitleAndArtists(title: String, artistIds: List<Long>): List<AlbumEntity>
    /** Nombre de titres distincts de l'album créditant cet artiste (principal ou invité). */
    @Query("SELECT COUNT(DISTINCT t.track_id) FROM tracks t JOIN track_artists ta ON ta.track_id = t.track_id WHERE t.album_id = :albumId AND ta.artist_id = :artistId")
    suspend fun tracksCreditingArtist(albumId: Long, artistId: Long): Int
    /** (titre, artiste crédité) pour tous les titres de l'album. */
    @Query("SELECT ta.track_id AS trackId, ta.artist_id AS artistId FROM track_artists ta JOIN tracks t ON t.track_id = ta.track_id WHERE t.album_id = :albumId")
    suspend fun trackArtistPairs(albumId: Long): List<TrackArtistPair>
    @Query("UPDATE albums SET artist_id = :artistId WHERE album_id = :albumId") suspend fun setOwner(albumId: Long, artistId: Long)
    /** Album partagé (sans propriétaire) portant ce titre. */
    @Query("SELECT * FROM albums WHERE title = :title AND artist_id IS NULL LIMIT 1")
    suspend fun findShared(title: String): AlbumEntity?
    @Query("SELECT * FROM albums WHERE artist_id IS NULL")
    suspend fun allShared(): List<AlbumEntity>
    @Query("SELECT COUNT(DISTINCT t.artist_id) FROM tracks t WHERE t.album_id = :albumId AND t.play_count > 0")
    suspend fun distinctArtistCount(albumId: Long): Int
    @Query("UPDATE albums SET cover_url = :url, cover_source = :source WHERE album_id = :id AND cover_url IS NULL") suspend fun fillCover(id: Long, url: String?, source: String?)

    @Query(
        """
        SELECT * FROM albums WHERE cover_url IS NULL AND album_id NOT IN
            (SELECT entity_id FROM api_cache WHERE entity_type = 'ALBUM' AND data_type = 'COVER' AND expires_at > :now)
        ORDER BY play_count DESC LIMIT :limit
        """
    )
    suspend fun missingCover(now: Long, limit: Int): List<AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums WHERE cover_url IS NULL") fun missingCoverCount(): Flow<Int>
    @Query("SELECT album_id FROM albums WHERE cover_source IS NULL OR cover_source != 'USER' ORDER BY play_count DESC") suspend fun idsForRefresh(): List<Long>
    /** Pochette d'album connue → appliquée aux titres de l'album qui n'en ont pas. */
    @Query("UPDATE tracks SET cover_url = :url, cover_source = :source WHERE album_id = :albumId AND cover_url IS NULL")
    suspend fun propagateCoverToTracks(albumId: Long, url: String, source: String)
    @Query("UPDATE tracks SET cover_url = :url, cover_source = :source WHERE album_id = :albumId AND (cover_source IS NULL OR cover_source != 'USER')")
    suspend fun overwriteCoverOfTracks(albumId: Long, url: String, source: String)

    @Query("SELECT COUNT(*) FROM albums")
    fun countFlow(): Flow<Int>

    @Query(
        """
        SELECT al.*, IFNULL(a.name, 'Artistes variés') AS artist_name, al.play_count AS period_plays, al.total_duration_ms AS period_duration_ms
        FROM albums al LEFT JOIN artists a ON a.artist_id = al.artist_id
        WHERE al.play_count > 0 AND al.is_compilation = 0
        ORDER BY al.play_count DESC, al.total_duration_ms DESC LIMIT :limit
        """
    )
    fun topAllTime(limit: Int = 300): Flow<List<RankedAlbum>>

    @Query(
        """
        SELECT al.*, IFNULL(a.name, 'Artistes variés') AS artist_name,
               SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN albums al ON al.album_id = d.album_id
        LEFT JOIN artists a ON a.artist_id = al.artist_id
        WHERE d.date BETWEEN :from AND :to AND al.is_compilation = 0
        GROUP BY d.album_id
        ORDER BY period_plays DESC, period_duration_ms DESC
        LIMIT :limit
        """
    )
    fun topForPeriod(from: String, to: String, limit: Int = 300): Flow<List<RankedAlbum>>

    @Query(
        """
        SELECT (SELECT COUNT(*) + 1 FROM (
                    SELECT album_id, SUM(play_count) AS p, SUM(total_duration_ms) AS d FROM daily_plays
                    WHERE album_id IS NOT NULL AND date BETWEEN :from AND :to GROUP BY album_id) x
                WHERE x.p > me.p OR (x.p = me.p AND x.d > me.d))
        FROM (SELECT SUM(play_count) AS p, SUM(total_duration_ms) AS d FROM daily_plays
              WHERE album_id = :albumId AND date BETWEEN :from AND :to) me
        WHERE me.p > 0
        """
    )
    suspend fun rankForPeriod(albumId: Long, from: String, to: String): Int?

    /** Albums d'un artiste présents dans la bibliothèque (au moins une écoute). */
    @Query("SELECT * FROM albums WHERE artist_id = :artistId AND play_count > 0 ORDER BY play_count DESC, total_duration_ms DESC")
    suspend fun ofArtist(artistId: Long): List<AlbumEntity>

    /** Albums d'un artiste écoutés sur une période (classés par écoutes sur la période). */
    @Query(
        """
        SELECT al.*, IFNULL(a.name, 'Artistes variés') AS artist_name, SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN albums al ON al.album_id = d.album_id
        LEFT JOIN artists a ON a.artist_id = al.artist_id
        WHERE al.artist_id = :artistId AND d.date BETWEEN :from AND :to
        GROUP BY al.album_id
        ORDER BY period_plays DESC, period_duration_ms DESC
        """
    )
    suspend fun ofArtistForPeriod(artistId: Long, from: String, to: String): List<RankedAlbum>

    /** Agrégats d'un album sur une période. */
    @Query(
        """
        SELECT IFNULL(SUM(play_count), 0) AS plays, IFNULL(SUM(total_duration_ms), 0) AS duration_ms, MIN(date) AS first_date, MAX(date) AS last_date,
               COUNT(DISTINCT date) AS active_days, COUNT(DISTINCT track_id) AS distinct_tracks, COUNT(DISTINCT album_id) AS distinct_albums
        FROM daily_plays WHERE album_id = :albumId AND date BETWEEN :from AND :to
        """
    )
    suspend fun periodStats(albumId: Long, from: String, to: String): PeriodEntityStats?

    @Query("SELECT al.*, IFNULL(a.name, 'Artistes variés') AS artist_name, al.play_count AS period_plays, al.total_duration_ms AS period_duration_ms FROM albums al LEFT JOIN artists a ON a.artist_id = al.artist_id WHERE al.play_count > 0 ORDER BY al.play_count DESC LIMIT :limit")
    fun mostPlayed(limit: Int = 100): Flow<List<RankedAlbum>>

    @Query("SELECT * FROM albums") suspend fun all(): List<AlbumEntity>

    /* ---- Éditeur de données ---- */
    @Query("SELECT al.*, IFNULL(a.name, 'Artistes variés') AS artist_name, al.play_count AS period_plays, al.total_duration_ms AS period_duration_ms FROM albums al LEFT JOIN artists a ON a.artist_id = al.artist_id ORDER BY al.play_count DESC, al.title")
    fun allForEditor(): Flow<List<RankedAlbum>>

    @Query("UPDATE albums SET title = :title WHERE album_id = :id") suspend fun rename(id: Long, title: String)
    @Query("UPDATE albums SET cover_url = :url, cover_source = 'USER' WHERE album_id = :id") suspend fun setCover(id: Long, url: String?)
    @Query("UPDATE albums SET artist_id = :into WHERE artist_id = :from") suspend fun moveArtist(from: Long, into: Long)
    @Query("DELETE FROM albums WHERE album_id = :id") suspend fun delete(id: Long)

    /** Écoutes d'un album = somme des écoutes de tous ses titres. */
    @Query(
        """
        UPDATE albums SET
            play_count = (SELECT COUNT(*) FROM scrobbles s WHERE s.album_id = albums.album_id AND s.status = 'CONFIRMED'),
            total_duration_ms = (SELECT IFNULL(SUM(s.duration_listened_ms), 0) FROM scrobbles s WHERE s.album_id = albums.album_id AND s.status = 'CONFIRMED'),
            distinct_tracks_played = (SELECT COUNT(DISTINCT s.track_id) FROM scrobbles s WHERE s.album_id = albums.album_id AND s.status = 'CONFIRMED'),
            first_played_at = (SELECT MIN(s.started_at) FROM scrobbles s WHERE s.album_id = albums.album_id AND s.status = 'CONFIRMED'),
            last_played_at = (SELECT MAX(s.started_at) FROM scrobbles s WHERE s.album_id = albums.album_id AND s.status = 'CONFIRMED')
        """
    )
    suspend fun recomputeAggregates()
}

@Dao
interface TrackLinkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackArtist(link: TrackArtistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackAlbum(link: TrackAlbumEntity): Long

    @Query("SELECT artist_id FROM track_artists WHERE track_id = :trackId ORDER BY is_primary DESC")
    suspend fun artistIdsForTrack(trackId: Long): List<Long>

    @Query("SELECT * FROM track_artists") suspend fun allTrackArtists(): List<TrackArtistEntity>
    /** Titres crédités à un artiste (principal + featuring) + titres dont il est l'artiste principal. */
    @Query("SELECT track_id FROM track_artists WHERE artist_id = :artistId UNION SELECT track_id FROM tracks WHERE artist_id = :artistId")
    suspend fun trackIdsForArtist(artistId: Long): List<Long>
    @Query("DELETE FROM track_artists WHERE artist_id = :from AND track_id IN (SELECT track_id FROM track_artists WHERE artist_id = :into)") suspend fun dropDuplicateLinks(from: Long, into: Long)
    @Query("UPDATE track_artists SET artist_id = :into WHERE artist_id = :from") suspend fun moveArtist(from: Long, into: Long)
    @Query("DELETE FROM track_artists WHERE track_id = :trackId") suspend fun clearTrackArtists(trackId: Long)
    @Query("INSERT OR IGNORE INTO track_artists (track_id, artist_id, is_primary, role) SELECT :into, artist_id, 0, 'featured' FROM track_artists WHERE track_id = :from AND artist_id NOT IN (SELECT artist_id FROM track_artists WHERE track_id = :into)")
    suspend fun copyArtistLinks(from: Long, into: Long)
    @Query("INSERT OR IGNORE INTO track_albums (track_id, album_id) SELECT :into, album_id FROM track_albums WHERE track_id = :from")
    suspend fun copyAlbumLinks(from: Long, into: Long)
    /** Fusion d'albums : les liens titre↔album de `from` passent sur `into`. */
    @Query("INSERT OR IGNORE INTO track_albums (track_id, album_id) SELECT track_id, :into FROM track_albums WHERE album_id = :from")
    suspend fun retargetAlbumLinks(from: Long, into: Long)
    @Query("DELETE FROM track_albums WHERE track_id = :trackId AND album_id = :albumId") suspend fun unlinkTrackAlbum(trackId: Long, albumId: Long)
    @Query("DELETE FROM track_albums WHERE track_id = :trackId") suspend fun clearTrackAlbums(trackId: Long)
    @Query("DELETE FROM track_albums WHERE album_id = :albumId") suspend fun clearAlbumLinks(albumId: Long)
}


@Dao
interface ArtistExceptionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: com.novastats.app.data.db.entity.ArtistExceptionEntity): Long
    @Query("SELECT * FROM artist_exceptions ORDER BY name COLLATE NOCASE") fun all(): Flow<List<com.novastats.app.data.db.entity.ArtistExceptionEntity>>
    @Query("SELECT * FROM artist_exceptions ORDER BY name COLLATE NOCASE") suspend fun allList(): List<com.novastats.app.data.db.entity.ArtistExceptionEntity>
    @Query("DELETE FROM artist_exceptions WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM artist_exceptions") suspend fun count(): Int
}
