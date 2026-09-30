package com.novastats.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.novastats.app.data.db.dao.AlbumDao
import com.novastats.app.data.db.dao.ApiCacheDao
import com.novastats.app.data.db.dao.ArtistDao
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
        MigrationLogEntity::class
    ],
    version = 1,
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
    abstract fun certificationDao(): CertificationDao
    abstract fun hallOfFameDao(): HallOfFameDao
    abstract fun pantheonDao(): PantheonDao
    abstract fun recordDao(): RecordDao
    abstract fun novaAwardDao(): NovaAwardDao
    abstract fun notificationFeedDao(): NotificationFeedDao
    abstract fun apiCacheDao(): ApiCacheDao
    abstract fun editorDao(): EditorDao
    abstract fun migrationLogDao(): MigrationLogDao

    companion object {
        const val NAME = "novastats.db"

        @Volatile private var instance: NovaDatabase? = null

        fun get(context: Context): NovaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NovaDatabase::class.java, NAME)
                // Version 1 : pas encore de migrations. À remplacer par addMigrations() dès la v2.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
