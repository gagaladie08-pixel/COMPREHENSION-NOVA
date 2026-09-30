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

    /**
     * Classement Global (all-time). Tri : écoutes puis temps d'écoute (règle d'égalité).
     */
    @Query(
        """
        SELECT t.*, a.name AS artist_name, al.title AS album_title,
               t.play_count AS period_plays, t.total_duration_ms AS period_duration_ms
        FROM tracks t
        JOIN artists a ON a.artist_id = t.artist_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE t.play_count > 0
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
        SELECT t.*, a.name AS artist_name, al.title AS album_title,
               SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN tracks t ON t.track_id = d.track_id
        JOIN artists a ON a.artist_id = t.artist_id
        LEFT JOIN albums al ON al.album_id = t.album_id
        WHERE d.date BETWEEN :from AND :to
        GROUP BY d.track_id
        ORDER BY period_plays DESC, period_duration_ms DESC
        LIMIT :limit
        """
    )
    fun topForPeriod(from: String, to: String, limit: Int = 300): Flow<List<RankedTrack>>

    /** Recalcule les agrégats des titres à partir des scrobbles confirmés (après import / édition). */
    @Query(
        """
        UPDATE tracks SET
            play_count = (SELECT COUNT(*) FROM scrobbles s WHERE s.track_id = tracks.track_id AND s.status = 'CONFIRMED'),
            total_duration_ms = (SELECT IFNULL(SUM(s.duration_listened_ms), 0) FROM scrobbles s WHERE s.track_id = tracks.track_id AND s.status = 'CONFIRMED'),
            first_played_at = (SELECT MIN(s.started_at) FROM scrobbles s WHERE s.track_id = tracks.track_id AND s.status = 'CONFIRMED'),
            last_played_at = (SELECT MAX(s.started_at) FROM scrobbles s WHERE s.track_id = tracks.track_id AND s.status = 'CONFIRMED')
        """
    )
    suspend fun recomputeAggregates()

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
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(artist: ArtistEntity): Long

    @Update
    suspend fun update(artist: ArtistEntity)

    @Query("SELECT * FROM artists WHERE artist_id = :id")
    suspend fun getById(id: Long): ArtistEntity?

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ArtistEntity?

    @Query("SELECT COUNT(*) FROM artists")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM artists WHERE play_count > 0 ORDER BY play_count DESC, total_duration_ms DESC")
    fun allByPlays(): Flow<List<ArtistEntity>>

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
            distinct_albums = (SELECT COUNT(DISTINCT t.album_id) FROM track_artists ta JOIN tracks t ON t.track_id = ta.track_id
                          WHERE ta.artist_id = artists.artist_id AND t.play_count > 0 AND t.album_id IS NOT NULL),
            first_played_at = (SELECT MIN(s.started_at) FROM scrobbles s JOIN track_artists ta ON ta.track_id = s.track_id
                          WHERE ta.artist_id = artists.artist_id AND s.status = 'CONFIRMED'),
            last_played_at = (SELECT MAX(s.started_at) FROM scrobbles s JOIN track_artists ta ON ta.track_id = s.track_id
                          WHERE ta.artist_id = artists.artist_id AND s.status = 'CONFIRMED')
        """
    )
    suspend fun recomputeAggregates()

    @Query("UPDATE artists SET pantheon_status = :status, pantheon_date = :date WHERE artist_id = :artistId")
    suspend fun setPantheonStatus(artistId: Long, status: String, date: Long)
}

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Query("SELECT * FROM albums WHERE album_id = :id")
    suspend fun getById(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE title = :title AND artist_id = :artistId LIMIT 1")
    suspend fun findByTitleAndArtist(title: String, artistId: Long): AlbumEntity?

    @Query("SELECT COUNT(*) FROM albums")
    fun countFlow(): Flow<Int>

    @Query(
        """
        SELECT al.*, a.name AS artist_name, al.play_count AS period_plays, al.total_duration_ms AS period_duration_ms
        FROM albums al JOIN artists a ON a.artist_id = al.artist_id
        WHERE al.play_count > 0 AND al.is_compilation = 0
        ORDER BY al.play_count DESC, al.total_duration_ms DESC LIMIT :limit
        """
    )
    fun topAllTime(limit: Int = 300): Flow<List<RankedAlbum>>

    @Query(
        """
        SELECT al.*, a.name AS artist_name,
               SUM(d.play_count) AS period_plays, SUM(d.total_duration_ms) AS period_duration_ms
        FROM daily_plays d
        JOIN albums al ON al.album_id = d.album_id
        JOIN artists a ON a.artist_id = al.artist_id
        WHERE d.date BETWEEN :from AND :to AND al.is_compilation = 0
        GROUP BY d.album_id
        ORDER BY period_plays DESC, period_duration_ms DESC
        LIMIT :limit
        """
    )
    fun topForPeriod(from: String, to: String, limit: Int = 300): Flow<List<RankedAlbum>>

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
}
