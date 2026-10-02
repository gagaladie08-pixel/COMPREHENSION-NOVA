package com.novastats.app.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.novastats.app.data.db.entity.SnapshotAlbumEntity
import com.novastats.app.data.db.entity.SnapshotArtistEntity
import com.novastats.app.data.db.entity.SnapshotTrackEntity
import kotlinx.coroutines.flow.Flow

/* ---------- Résultats génériques utilisés par le moteur Billboard ---------- */

/** Une entité classée sur une période (source : daily_plays). */
data class RankedEntry(
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "plays") val plays: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "distinct_tracks") val distinctTracks: Int = 0,
    @ColumnInfo(name = "distinct_albums") val distinctAlbums: Int = 0
)

/** Apparition passée d'une entité dans un chart (pour peak, compteur, record d'écoutes, séries). */
data class PriorRow(
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "play_count") val playCount: Int
)

/* ---------- Lignes d'affichage (snapshot + noms / pochettes) ---------- */

data class ChartTrackRow(
    @Embedded val row: SnapshotTrackEntity,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?
)

data class ChartArtistRow(
    @Embedded val row: SnapshotArtistEntity,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "photo_url") val photoUrl: String?,
    @ColumnInfo(name = "pantheon_status") val pantheonStatus: String?
)

data class ChartAlbumRow(
    @Embedded val row: SnapshotAlbumEntity,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?
)

@Dao
interface BillboardDao {

    /* ===== Classements bruts par intervalle de dates (inclusif) ===== */

    @Query(
        """
        SELECT d.track_id AS entity_id, SUM(d.play_count) AS plays, SUM(d.total_duration_ms) AS duration_ms,
               0 AS distinct_tracks, 0 AS distinct_albums
        FROM daily_plays d WHERE d.date BETWEEN :from AND :to
        GROUP BY d.track_id ORDER BY plays DESC, duration_ms DESC LIMIT :limit
        """
    )
    suspend fun rankTracks(from: String, to: String, limit: Int): List<RankedEntry>

    @Query(
        """
        SELECT ta.artist_id AS entity_id, SUM(d.play_count) AS plays, SUM(d.total_duration_ms) AS duration_ms,
               COUNT(DISTINCT d.track_id) AS distinct_tracks, COUNT(DISTINCT d.album_id) AS distinct_albums
        FROM daily_plays d
        JOIN track_artists ta ON ta.track_id = d.track_id
        JOIN artists a ON a.artist_id = ta.artist_id AND a.is_merged = 0
        WHERE d.date BETWEEN :from AND :to
        GROUP BY ta.artist_id ORDER BY plays DESC, duration_ms DESC LIMIT :limit
        """
    )
    suspend fun rankArtists(from: String, to: String, limit: Int): List<RankedEntry>

    @Query(
        """
        SELECT d.album_id AS entity_id, SUM(d.play_count) AS plays, SUM(d.total_duration_ms) AS duration_ms,
               COUNT(DISTINCT d.track_id) AS distinct_tracks, 0 AS distinct_albums
        FROM daily_plays d
        JOIN albums al ON al.album_id = d.album_id AND al.is_compilation = 0
        WHERE d.date BETWEEN :from AND :to AND d.album_id IS NOT NULL
        GROUP BY d.album_id ORDER BY plays DESC, duration_ms DESC LIMIT :limit
        """
    )
    suspend fun rankAlbums(from: String, to: String, limit: Int): List<RankedEntry>

    /* ===== Apparitions passées (strictement avant une date) ===== */

    @Query(
        """
        SELECT st.track_id AS entity_id, s.date AS date, st.position AS position, st.play_count AS play_count
        FROM snapshot_tracks st JOIN snapshots s ON s.snapshot_id = st.snapshot_id
        WHERE s.type = :type AND s.date < :before
        """
    )
    suspend fun priorTrackRows(type: String, before: String): List<PriorRow>

    @Query(
        """
        SELECT sa.artist_id AS entity_id, s.date AS date, sa.position AS position, sa.play_count AS play_count
        FROM snapshot_artists sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type AND s.date < :before
        """
    )
    suspend fun priorArtistRows(type: String, before: String): List<PriorRow>

    @Query(
        """
        SELECT sa.album_id AS entity_id, s.date AS date, sa.position AS position, sa.play_count AS play_count
        FROM snapshot_albums sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type AND s.date < :before
        """
    )
    suspend fun priorAlbumRows(type: String, before: String): List<PriorRow>

    /* ===== Historique complet d'une entité dans un chart (fiche appui long) ===== */

    @Query(
        """
        SELECT st.track_id AS entity_id, s.date AS date, st.position AS position, st.play_count AS play_count
        FROM snapshot_tracks st JOIN snapshots s ON s.snapshot_id = st.snapshot_id
        WHERE s.type = :type AND st.track_id = :id ORDER BY s.date
        """
    )
    suspend fun trackHistory(type: String, id: Long): List<PriorRow>

    @Query(
        """
        SELECT sa.artist_id AS entity_id, s.date AS date, sa.position AS position, sa.play_count AS play_count
        FROM snapshot_artists sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type AND sa.artist_id = :id ORDER BY s.date
        """
    )
    suspend fun artistHistory(type: String, id: Long): List<PriorRow>

    @Query(
        """
        SELECT sa.album_id AS entity_id, s.date AS date, sa.position AS position, sa.play_count AS play_count
        FROM snapshot_albums sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type AND sa.album_id = :id ORDER BY s.date
        """
    )
    suspend fun albumHistory(type: String, id: Long): List<PriorRow>

    /** Dates de tous les snapshots d'un type (Multi-Chart : jours observés). */
    @Query("SELECT DISTINCT date FROM snapshots WHERE type = :type ORDER BY date")
    suspend fun snapshotDates(type: String): List<String>

    /* ===== Séries complètes d'un type de chart (moteur des Records) ===== */

    @Query(
        """
        SELECT st.track_id AS entity_id, s.date AS date, st.position AS position, st.play_count AS play_count
        FROM snapshot_tracks st JOIN snapshots s ON s.snapshot_id = st.snapshot_id
        WHERE s.type = :type ORDER BY s.date, st.position
        """
    )
    suspend fun allTrackRows(type: String): List<PriorRow>

    @Query(
        """
        SELECT sa.artist_id AS entity_id, s.date AS date, sa.position AS position, sa.play_count AS play_count
        FROM snapshot_artists sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type ORDER BY s.date, sa.position
        """
    )
    suspend fun allArtistRows(type: String): List<PriorRow>

    @Query(
        """
        SELECT sa.album_id AS entity_id, s.date AS date, sa.position AS position, sa.play_count AS play_count
        FROM snapshot_albums sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type ORDER BY s.date, sa.position
        """
    )
    suspend fun allAlbumRows(type: String): List<PriorRow>

    /* ===== Lignes d'affichage d'un snapshot ===== */

    @Query(
        """
        SELECT st.*, t.title AS title, (SELECT GROUP_CONCAT(n, ', ') FROM (SELECT a2.name AS n FROM track_artists ta2 JOIN artists a2 ON a2.artist_id = ta2.artist_id WHERE ta2.track_id = t.track_id ORDER BY ta2.is_primary DESC, ta2.id)) AS artist_name, t.cover_url AS cover_url
        FROM snapshot_tracks st
        JOIN tracks t ON t.track_id = st.track_id
        JOIN artists a ON a.artist_id = t.artist_id
        WHERE st.snapshot_id = :snapshotId ORDER BY st.position
        """
    )
    fun trackRows(snapshotId: Long): Flow<List<ChartTrackRow>>

    @Query(
        """
        SELECT sa.*, a.name AS name, a.photo_url AS photo_url, a.pantheon_status AS pantheon_status
        FROM snapshot_artists sa JOIN artists a ON a.artist_id = sa.artist_id
        WHERE sa.snapshot_id = :snapshotId ORDER BY sa.position
        """
    )
    fun artistRows(snapshotId: Long): Flow<List<ChartArtistRow>>

    @Query(
        """
        SELECT sa.*, al.title AS title, a.name AS artist_name, al.cover_url AS cover_url
        FROM snapshot_albums sa
        JOIN albums al ON al.album_id = sa.album_id
        JOIN artists a ON a.artist_id = al.artist_id
        WHERE sa.snapshot_id = :snapshotId ORDER BY sa.position
        """
    )
    fun albumRows(snapshotId: Long): Flow<List<ChartAlbumRow>>

    /* ===== Maintenance ===== */

    @Query("DELETE FROM snapshot_tracks WHERE snapshot_id = :snapshotId") suspend fun clearTrackRows(snapshotId: Long)
    @Query("DELETE FROM snapshot_artists WHERE snapshot_id = :snapshotId") suspend fun clearArtistRows(snapshotId: Long)
    @Query("DELETE FROM snapshot_albums WHERE snapshot_id = :snapshotId") suspend fun clearAlbumRows(snapshotId: Long)

    /** Observe l'identifiant du snapshot (type, date) — null tant qu'il n'existe pas. */
    @Query("SELECT snapshot_id FROM snapshots WHERE type = :type AND date = :date")
    fun observeSnapshotId(type: String, date: String): Flow<Long?>

    /** Positions de l'entité #1 des snapshots DAILY/WEEKLY/MONTHLY couvrant une date (Triple Debut). */
    @Query(
        """
        SELECT st.track_id FROM snapshot_tracks st JOIN snapshots s ON s.snapshot_id = st.snapshot_id
        WHERE s.type = :type AND s.date = :date AND st.position = 1 LIMIT 1
        """
    )
    suspend fun numberOneTrack(type: String, date: String): Long?

    @Query(
        """
        SELECT sa.artist_id FROM snapshot_artists sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type AND s.date = :date AND sa.position = 1 LIMIT 1
        """
    )
    suspend fun numberOneArtist(type: String, date: String): Long?

    @Query(
        """
        SELECT sa.album_id FROM snapshot_albums sa JOIN snapshots s ON s.snapshot_id = sa.snapshot_id
        WHERE s.type = :type AND s.date = :date AND sa.position = 1 LIMIT 1
        """
    )
    suspend fun numberOneAlbum(type: String, date: String): Long?
}
