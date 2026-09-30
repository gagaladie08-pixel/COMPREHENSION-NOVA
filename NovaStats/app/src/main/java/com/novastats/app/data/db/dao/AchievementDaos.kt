package com.novastats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.novastats.app.data.db.entity.ApiCacheEntity
import com.novastats.app.data.db.entity.ApiReliabilityEntity
import com.novastats.app.data.db.entity.BillboardHistoryAlbumEntity
import com.novastats.app.data.db.entity.BillboardHistoryArtistEntity
import com.novastats.app.data.db.entity.BillboardHistoryTrackEntity
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.CertificationHistoryEntity
import com.novastats.app.data.db.entity.EditHistoryEntity
import com.novastats.app.data.db.entity.HallOfFameBadgeEntity
import com.novastats.app.data.db.entity.HallOfFameEntity
import com.novastats.app.data.db.entity.MigrationLogEntity
import com.novastats.app.data.db.entity.NotificationFeedEntity
import com.novastats.app.data.db.entity.NovaAwardEntity
import com.novastats.app.data.db.entity.NovaAwardHistoryEntity
import com.novastats.app.data.db.entity.PantheonHistoryEntity
import com.novastats.app.data.db.entity.PantheonStatusEntity
import com.novastats.app.data.db.entity.RecordCacheEntity
import com.novastats.app.data.db.entity.SnapshotAlbumEntity
import com.novastats.app.data.db.entity.SnapshotArtistEntity
import com.novastats.app.data.db.entity.SnapshotEntity
import com.novastats.app.data.db.entity.SnapshotTrackEntity
import com.novastats.app.data.db.entity.UserCorrectionEntity
import kotlinx.coroutines.flow.Flow

/* ===== Lignes "Dernières actualités" (Accueil §4) ===== */
data class CertificationNews(@Embedded val h: CertificationHistoryEntity, val name: String?)
data class PantheonNews(@Embedded val h: PantheonHistoryEntity, val name: String?)
data class HallOfFameNews(@Embedded val h: HallOfFameEntity, val name: String?)

@Dao
interface CertificationDao {
    @Upsert suspend fun upsert(cert: CertificationEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHistory(h: CertificationHistoryEntity): Long
    @Query("SELECT * FROM certifications WHERE entity_type = :type ORDER BY CASE level WHEN 'DIAMOND' THEN 0 WHEN 'PLATINUM' THEN 1 WHEN 'GOLD' THEN 2 ELSE 3 END, multiplier DESC, play_count_at_cert DESC")
    fun byType(type: String): Flow<List<CertificationEntity>>
    @Query("SELECT * FROM certifications WHERE entity_id = :entityId AND entity_type = :type") suspend fun current(entityId: Long, type: String): CertificationEntity?
    @Query("SELECT * FROM certification_history WHERE entity_id = :entityId AND entity_type = :type ORDER BY certified_at") suspend fun history(entityId: Long, type: String): List<CertificationHistoryEntity>
    @Query("SELECT COUNT(*) FROM certifications") fun countFlow(): Flow<Int>
    @Query("SELECT * FROM certifications WHERE entity_id = :entityId AND entity_type = :type") fun observe(entityId: Long, type: String): Flow<CertificationEntity?>
    @Query("SELECT h.*, CASE h.entity_type WHEN 'TRACK' THEN (SELECT title FROM tracks WHERE track_id = h.entity_id) WHEN 'ALBUM' THEN (SELECT title FROM albums WHERE album_id = h.entity_id) ELSE (SELECT name FROM artists WHERE artist_id = h.entity_id) END AS name FROM certification_history h ORDER BY h.certified_at DESC LIMIT :limit")
    fun latestHistory(limit: Int = 10): Flow<List<CertificationNews>>
    @Query("DELETE FROM certifications") suspend fun clear()
    @Query("DELETE FROM certification_history") suspend fun clearHistory()
}

@Dao
interface PantheonDao {
    @Upsert suspend fun upsert(status: PantheonStatusEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHistory(h: PantheonHistoryEntity): Long
    @Query("SELECT * FROM pantheon_status WHERE artist_id = :artistId") suspend fun forArtist(artistId: Long): PantheonStatusEntity?
    @Query("SELECT * FROM pantheon_status ORDER BY CASE current_status WHEN 'MYTHIQUE' THEN 0 WHEN 'LEGENDE' THEN 1 WHEN 'MEGASTAR' THEN 2 WHEN 'SUPERSTAR' THEN 3 ELSE 4 END, status_date")
    fun all(): Flow<List<PantheonStatusEntity>>
    @Query("SELECT * FROM pantheon_history WHERE artist_id = :artistId ORDER BY date_reached") suspend fun history(artistId: Long): List<PantheonHistoryEntity>
    @Query("SELECT h.*, (SELECT name FROM artists WHERE artist_id = h.artist_id) AS name FROM pantheon_history h ORDER BY h.date_reached DESC LIMIT :limit")
    fun latestHistory(limit: Int = 10): Flow<List<PantheonNews>>
    @Query("DELETE FROM pantheon_status") suspend fun clear()
    @Query("DELETE FROM pantheon_history") suspend fun clearHistory()
}

@Dao
interface SnapshotDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(snapshot: SnapshotEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTracks(rows: List<SnapshotTrackEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertArtists(rows: List<SnapshotArtistEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAlbums(rows: List<SnapshotAlbumEntity>)
    @Query("SELECT * FROM snapshots WHERE type = :type AND date = :date") suspend fun find(type: String, date: String): SnapshotEntity?
    @Query("SELECT * FROM snapshots WHERE type = :type ORDER BY date DESC") fun byType(type: String): Flow<List<SnapshotEntity>>
    @Query("SELECT * FROM snapshots WHERE type = :type AND date < :date ORDER BY date DESC LIMIT 1") suspend fun previous(type: String, date: String): SnapshotEntity?
    @Query("SELECT * FROM snapshot_tracks WHERE snapshot_id = :snapshotId ORDER BY position") fun tracks(snapshotId: Long): Flow<List<SnapshotTrackEntity>>
    @Query("SELECT * FROM snapshot_tracks WHERE snapshot_id = :snapshotId ORDER BY position") suspend fun tracksOnce(snapshotId: Long): List<SnapshotTrackEntity>
    @Query("SELECT * FROM snapshot_artists WHERE snapshot_id = :snapshotId ORDER BY position") fun artists(snapshotId: Long): Flow<List<SnapshotArtistEntity>>
    @Query("SELECT * FROM snapshot_artists WHERE snapshot_id = :snapshotId ORDER BY position") suspend fun artistsOnce(snapshotId: Long): List<SnapshotArtistEntity>
    @Query("SELECT * FROM snapshot_albums WHERE snapshot_id = :snapshotId ORDER BY position") fun albums(snapshotId: Long): Flow<List<SnapshotAlbumEntity>>
    @Query("SELECT * FROM snapshot_albums WHERE snapshot_id = :snapshotId ORDER BY position") suspend fun albumsOnce(snapshotId: Long): List<SnapshotAlbumEntity>
    @Query("DELETE FROM snapshots") suspend fun clearAll()
    @Query("DELETE FROM snapshot_tracks") suspend fun clearTracks()
    @Query("DELETE FROM snapshot_artists") suspend fun clearArtists()
    @Query("DELETE FROM snapshot_albums") suspend fun clearAlbums()
}

@Dao
interface BillboardHistoryDao {
    @Upsert suspend fun upsertTrack(row: BillboardHistoryTrackEntity)
    @Upsert suspend fun upsertArtist(row: BillboardHistoryArtistEntity)
    @Upsert suspend fun upsertAlbum(row: BillboardHistoryAlbumEntity)
    @Query("SELECT * FROM billboard_history_tracks WHERE track_id = :trackId AND period_type = :period") suspend fun track(trackId: Long, period: String): BillboardHistoryTrackEntity?
    @Query("SELECT * FROM billboard_history_artists WHERE artist_id = :artistId AND period_type = :period") suspend fun artist(artistId: Long, period: String): BillboardHistoryArtistEntity?
    @Query("SELECT * FROM billboard_history_albums WHERE album_id = :albumId AND period_type = :period") suspend fun album(albumId: Long, period: String): BillboardHistoryAlbumEntity?
    @Query("SELECT * FROM billboard_history_tracks WHERE period_type = :period ORDER BY total_weeks_in_chart DESC LIMIT :limit") fun topTracksByWeeks(period: String, limit: Int = 10): Flow<List<BillboardHistoryTrackEntity>>
    @Query("DELETE FROM billboard_history_tracks") suspend fun clearTracks()
    @Query("DELETE FROM billboard_history_artists") suspend fun clearArtists()
    @Query("DELETE FROM billboard_history_albums") suspend fun clearAlbums()
}

@Dao
interface HallOfFameDao {
    @Insert suspend fun insert(entry: HallOfFameEntity): Long
    @Insert suspend fun insertBadge(badge: HallOfFameBadgeEntity): Long
    @Query("SELECT * FROM hall_of_fame WHERE period_type = :period AND entity_type = :entityType ORDER BY entry_date DESC") fun list(period: String, entityType: String): Flow<List<HallOfFameEntity>>
    @Query("SELECT COUNT(*) FROM hall_of_fame WHERE entity_id = :entityId AND entity_type = :entityType AND period_type = :period AND entry_type = :entryType") suspend fun exists(entityId: Long, entityType: String, period: String, entryType: String): Int
    @Query("SELECT COUNT(*) FROM hall_of_fame") fun countFlow(): Flow<Int>
    @Query("SELECT h.*, CASE h.entity_type WHEN 'TRACK' THEN (SELECT title FROM tracks WHERE track_id = h.entity_id) WHEN 'ALBUM' THEN (SELECT title FROM albums WHERE album_id = h.entity_id) ELSE (SELECT name FROM artists WHERE artist_id = h.entity_id) END AS name FROM hall_of_fame h ORDER BY h.created_at DESC LIMIT :limit")
    fun latest(limit: Int = 10): Flow<List<HallOfFameNews>>
    @Query("DELETE FROM hall_of_fame") suspend fun clear()
    @Query("DELETE FROM hall_of_fame_badges") suspend fun clearBadges()
}

@Dao
interface RecordDao {
    @Insert suspend fun insertAll(rows: List<RecordCacheEntity>)
    @Query("SELECT * FROM records_cache WHERE record_type = :type AND (:period IS NULL OR period_type = :period) AND category = :category AND (:sub IS NULL OR subcategory = :sub) ORDER BY value DESC LIMIT 10")
    fun top10(type: String, period: String?, category: String, sub: String?): Flow<List<RecordCacheEntity>>
    @Query("DELETE FROM records_cache WHERE record_type = :type") suspend fun clearType(type: String)
    @Query("DELETE FROM records_cache") suspend fun clear()
}

@Dao
interface NovaAwardDao {
    @Upsert suspend fun upsert(award: NovaAwardEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHistory(h: NovaAwardHistoryEntity)
    @Query("SELECT * FROM nova_awards WHERE year = :year") fun forYear(year: Int): Flow<List<NovaAwardEntity>>
    @Query("SELECT * FROM nova_awards_history WHERE year = :year") fun historyForYear(year: Int): Flow<List<NovaAwardHistoryEntity>>
    @Query("SELECT DISTINCT year FROM nova_awards_history ORDER BY year DESC") fun archivedYears(): Flow<List<Int>>
}

@Dao
interface NotificationFeedDao {
    @Insert suspend fun insert(item: NotificationFeedEntity): Long
    @Query("SELECT * FROM notifications_feed ORDER BY created_at DESC LIMIT :limit") fun recent(limit: Int = 10): Flow<List<NotificationFeedEntity>>
    @Query("UPDATE notifications_feed SET is_read = 1 WHERE id = :id") suspend fun markRead(id: Long)
    @Query("DELETE FROM notifications_feed") suspend fun clear()
}

@Dao
interface ApiCacheDao {
    @Insert suspend fun insert(row: ApiCacheEntity): Long
    @Query("SELECT * FROM api_cache WHERE entity_type = :entityType AND entity_id = :entityId AND data_type = :dataType AND is_rejected = 0 AND expires_at > :now ORDER BY confidence_score DESC LIMIT 1")
    suspend fun valid(entityType: String, entityId: Long, dataType: String, now: Long = System.currentTimeMillis()): ApiCacheEntity?
    @Query("UPDATE api_cache SET is_rejected = 1, is_blacklisted = 1 WHERE id = :id") suspend fun blacklist(id: Long)
    @Query("DELETE FROM api_cache WHERE expires_at <= :now AND is_blacklisted = 0") suspend fun purgeExpired(now: Long = System.currentTimeMillis())
    @Upsert suspend fun upsertReliability(row: ApiReliabilityEntity)
    @Query("SELECT * FROM api_reliability ORDER BY current_priority") fun reliability(): Flow<List<ApiReliabilityEntity>>
    @Query("SELECT * FROM api_reliability WHERE api_name = :name") suspend fun reliabilityFor(name: String): ApiReliabilityEntity?
    @Query("SELECT * FROM api_reliability") suspend fun allReliability(): List<ApiReliabilityEntity>
    /** URLs à ne plus jamais proposer pour cette entité (stratégie 11). */
    @Query("SELECT cached_url FROM api_cache WHERE entity_type = :entityType AND entity_id = :entityId AND is_blacklisted = 1 AND cached_url IS NOT NULL")
    suspend fun blacklistedUrls(entityType: String, entityId: Long): List<String>
    @Query("DELETE FROM api_cache WHERE entity_type = :entityType AND entity_id = :entityId AND data_type = :dataType AND is_blacklisted = 0")
    suspend fun clearEntity(entityType: String, entityId: Long, dataType: String)
    @Query("SELECT COUNT(*) FROM api_cache WHERE is_rejected = 0 AND source != 'NONE' AND expires_at > :now") fun cachedCount(now: Long = System.currentTimeMillis()): Flow<Int>
}

@Dao
interface EditorDao {
    @Insert suspend fun insertEdit(row: EditHistoryEntity): Long
    @Query("SELECT * FROM edit_history ORDER BY created_at DESC LIMIT :limit") fun history(limit: Int = 50): Flow<List<EditHistoryEntity>>
    @Query("SELECT * FROM edit_history ORDER BY created_at DESC LIMIT 1") suspend fun last(): EditHistoryEntity?
    @Query("DELETE FROM edit_history WHERE id = :id") suspend fun deleteEdit(id: Long)
    @Query("DELETE FROM edit_history WHERE id NOT IN (SELECT id FROM edit_history ORDER BY created_at DESC LIMIT 50)") suspend fun trimTo50()
    @Upsert suspend fun upsertCorrection(row: UserCorrectionEntity)
    @Query("SELECT * FROM user_corrections WHERE original_value = :original AND correction_type = :type") suspend fun correction(original: String, type: String): UserCorrectionEntity?
    @Query("UPDATE user_corrections SET times_applied = times_applied + 1 WHERE id = :id") suspend fun bumpCorrection(id: Long)
}

@Dao
interface MigrationLogDao {
    @Insert suspend fun insert(row: MigrationLogEntity): Long
    @Query("SELECT * FROM migration_log ORDER BY migrated_at DESC") fun all(): Flow<List<MigrationLogEntity>>
}
