package com.novastats.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.novastats.app.data.db.dao.AlbumDao
import com.novastats.app.data.db.dao.ApiCacheDao
import com.novastats.app.data.db.dao.ArtistDao
import com.novastats.app.data.db.dao.BillboardDao
import com.novastats.app.data.db.dao.BillboardHistoryDao
import com.novastats.app.data.db.dao.CertificationDao
import com.novastats.app.data.db.dao.DailyPlayDao
import com.novastats.app.data.db.dao.DailyStatsDao
import com.novastats.app.data.db.dao.DailyStreakDao
import com.novastats.app.data.db.dao.EditorDao
import com.novastats.app.data.db.dao.HallOfFameDao
import com.novastats.app.data.db.dao.MigrationLogDao
import com.novastats.app.data.db.dao.NotificationFeedDao
import com.novastats.app.data.db.dao.NovaAwardDao
import com.novastats.app.data.db.dao.NowPlayingDao
import com.novastats.app.data.db.dao.PantheonDao
import com.novastats.app.data.db.dao.PendingQueueDao
import com.novastats.app.data.db.dao.RecordDao
import com.novastats.app.data.db.dao.ScrobbleDao
import com.novastats.app.data.db.dao.SessionDao
import com.novastats.app.data.db.dao.SnapshotDao
import com.novastats.app.data.db.dao.TrackDao
import com.novastats.app.data.db.dao.TrackLinkDao
import com.novastats.app.data.db.entity.*

/**
 * Base de données NovaStats — 34 tables (cf. docs/DATABASE.md).
 */
@Database(
    entities = [
        // Module 1 — Entités
        TrackEntity::class, ArtistEntity::class, AlbumEntity::class,
        TrackArtistEntity::class, TrackAlbumEntity::class,
        // Module 2 — Écoutes
        ScrobbleEntity::class, SessionEntity::class, PendingQueueEntity::class,
        DailyPlayEntity::class, DailyStreakEntity::class,
        // Module 3 — Snapshots
        SnapshotEntity::class, SnapshotTrackEntity::class, SnapshotArtistEntity::class, SnapshotAlbumEntity::class,
        // Module 4 — Billboard
        BillboardHistoryTrackEntity::class, BillboardHistoryArtistEntity::class, BillboardHistoryAlbumEntity::class,
        // Module 5 — Certifications
        CertificationEntity::class, CertificationHistoryEntity::class,
        // Module 6 — Hall of Fame
        HallOfFameEntity::class, HallOfFameBadgeEntity::class,
        // Module 7 — Panthéon
        PantheonStatusEntity::class, PantheonHistoryEntity::class,
        // Module 8 — Records
        RecordCacheEntity::class,
        // Module 9 — Nova Awards
        NovaAwardEntity::class, NovaAwardHistoryEntity::class,
        // Module 10 — Accueil
        NowPlayingEntity::class, DailyStatsEntity::class, NotificationFeedEntity::class,
        // Module 11 — APIs & Éditeur
        ApiCacheEntity::class, ApiReliabilityEntity::class, EditHistoryEntity::class, UserCorrectionEntity::class,
        // Import/Export
        MigrationLogEntity::class,
        com.novastats.app.data.db.entity.ArtistExceptionEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun trackLinkDao(): TrackLinkDao
    abstract fun scrobbleDao(): ScrobbleDao
    abstract fun dailyPlayDao(): DailyPlayDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun dailyStreakDao(): DailyStreakDao
    abstract fun sessionDao(): SessionDao
    abstract fun pendingQueueDao(): PendingQueueDao
    abstract fun nowPlayingDao(): NowPlayingDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun billboardHistoryDao(): BillboardHistoryDao
    abstract fun billboardDao(): BillboardDao
    abstract fun certificationDao(): CertificationDao
    abstract fun hallOfFameDao(): HallOfFameDao
    abstract fun pantheonDao(): PantheonDao
    abstract fun recordDao(): RecordDao
    abstract fun novaAwardDao(): NovaAwardDao
    abstract fun notificationFeedDao(): NotificationFeedDao
    abstract fun apiCacheDao(): ApiCacheDao
    abstract fun editorDao(): EditorDao
    abstract fun migrationLogDao(): MigrationLogDao
    abstract fun artistExceptionDao(): com.novastats.app.data.db.dao.ArtistExceptionDao

    companion object {
        const val NAME = "novastats.db"

        /** v2 : badge PEAK (record personnel d'écoutes) sur les snapshots. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("snapshot_tracks", "snapshot_artists", "snapshot_albums").forEach {
                    db.execSQL("ALTER TABLE $it ADD COLUMN is_plays_peak INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        /** v3 : position / durée / état de lecture dans now_playing (barre de progression de l'Accueil). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE now_playing ADD COLUMN position_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE now_playing ADD COLUMN duration_ms INTEGER")
                db.execSQL("ALTER TABLE now_playing ADD COLUMN is_playing INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE now_playing ADD COLUMN raw_album TEXT")
            }
        }

        /** v4 : file « À corriger » — révision par écoute (needs_review, motif, valeurs brutes du lecteur). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scrobbles ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scrobbles ADD COLUMN review_reason TEXT")
                db.execSQL("ALTER TABLE scrobbles ADD COLUMN raw_title TEXT")
                db.execSQL("ALTER TABLE scrobbles ADD COLUMN raw_artist TEXT")
                db.execSQL("ALTER TABLE scrobbles ADD COLUMN raw_album TEXT")
                // Reprise de l'existant : écoutes sans artiste identifié ou à score < 70
                db.execSQL("UPDATE scrobbles SET needs_review = 1, review_reason = 'Artiste manquant' WHERE artist_id IN (SELECT artist_id FROM artists WHERE name = 'Artiste inconnu')")
                db.execSQL("UPDATE scrobbles SET needs_review = 1, review_reason = 'Score de confiance ' || confidence_score || ' %' WHERE needs_review = 0 AND confidence_score < 70")
            }
        }

        /** v5 : titre racine dans daily_plays (fusion remix / version avec invité) + noms d'artistes à ne jamais découper. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_plays ADD COLUMN root_id INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_plays_root_id ON daily_plays(root_id)")
                db.execSQL("UPDATE daily_plays SET root_id = IFNULL((SELECT t.original_track_id FROM tracks t WHERE t.track_id = daily_plays.track_id), track_id)")
                db.execSQL("CREATE TABLE IF NOT EXISTS artist_exceptions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, name_key TEXT NOT NULL, created_at INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_artist_exceptions_name_key ON artist_exceptions(name_key)")
            }
        }

        @Volatile private var instance: NovaDatabase? = null

        fun get(context: Context): NovaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NovaDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
