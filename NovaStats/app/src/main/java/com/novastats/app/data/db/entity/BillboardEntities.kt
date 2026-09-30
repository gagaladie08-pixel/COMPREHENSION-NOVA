package com.novastats.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * MODULE 4 — Billboard (tables 15 à 17)
 * Historique cumulé par entité et par type de période. Alimente les 24 Records.
 */

object PeriodType {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
    const val GLOBAL = "GLOBAL"
    val ALL = listOf(DAILY, WEEKLY, MONTHLY, YEARLY, GLOBAL)
}

/** Table 15 — billboard_history_tracks */
@Entity(
    tableName = "billboard_history_tracks",
    indices = [Index(value = ["track_id", "period_type"], unique = true), Index("period_type")]
)
data class BillboardHistoryTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "period_type") val periodType: String,
    @ColumnInfo(name = "first_entry_date") val firstEntryDate: String,
    @ColumnInfo(name = "first_entry_position") val firstEntryPosition: Int,
    @ColumnInfo(name = "peak_position") val peakPosition: Int,
    @ColumnInfo(name = "peak_date") val peakDate: String,
    @ColumnInfo(name = "times_at_peak") val timesAtPeak: Int = 1,
    @ColumnInfo(name = "total_days_in_chart") val totalDaysInChart: Int = 0,
    @ColumnInfo(name = "total_weeks_in_chart") val totalWeeksInChart: Int = 0,
    @ColumnInfo(name = "total_months_in_chart") val totalMonthsInChart: Int = 0,
    @ColumnInfo(name = "total_days_top10") val totalDaysTop10: Int = 0,
    @ColumnInfo(name = "total_weeks_top10") val totalWeeksTop10: Int = 0,
    @ColumnInfo(name = "total_months_top10") val totalMonthsTop10: Int = 0,
    @ColumnInfo(name = "total_days_top5") val totalDaysTop5: Int = 0,
    @ColumnInfo(name = "total_weeks_top5") val totalWeeksTop5: Int = 0,
    @ColumnInfo(name = "total_months_top5") val totalMonthsTop5: Int = 0,
    @ColumnInfo(name = "total_days_at_1") val totalDaysAt1: Int = 0,
    @ColumnInfo(name = "total_weeks_at_1") val totalWeeksAt1: Int = 0,
    @ColumnInfo(name = "total_months_at_1") val totalMonthsAt1: Int = 0,
    @ColumnInfo(name = "consecutive_days_top5") val consecutiveDaysTop5: Int = 0,
    @ColumnInfo(name = "consecutive_weeks_top10") val consecutiveWeeksTop10: Int = 0,
    @ColumnInfo(name = "consecutive_days_in_chart") val consecutiveDaysInChart: Int = 0,
    @ColumnInfo(name = "reentry_count") val reentryCount: Int = 0,
    @ColumnInfo(name = "last_position") val lastPosition: Int? = null,
    @ColumnInfo(name = "last_seen_date") val lastSeenDate: String? = null,
    @ColumnInfo(name = "exit_date") val exitDate: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "debut_play_count") val debutPlayCount: Int = 0,
    @ColumnInfo(name = "biggest_jump") val biggestJump: Int = 0,
    @ColumnInfo(name = "biggest_fall") val biggestFall: Int = 0,
    @ColumnInfo(name = "weeks_blocked_top5") val weeksBlockedTop5: Int = 0
)

/** Table 16 — billboard_history_artists */
@Entity(
    tableName = "billboard_history_artists",
    indices = [Index(value = ["artist_id", "period_type"], unique = true), Index("period_type")]
)
data class BillboardHistoryArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "period_type") val periodType: String,
    @ColumnInfo(name = "first_entry_date") val firstEntryDate: String,
    @ColumnInfo(name = "first_entry_position") val firstEntryPosition: Int,
    @ColumnInfo(name = "peak_position") val peakPosition: Int,
    @ColumnInfo(name = "peak_date") val peakDate: String,
    @ColumnInfo(name = "times_at_peak") val timesAtPeak: Int = 1,
    @ColumnInfo(name = "total_days_in_chart") val totalDaysInChart: Int = 0,
    @ColumnInfo(name = "total_weeks_in_chart") val totalWeeksInChart: Int = 0,
    @ColumnInfo(name = "total_months_in_chart") val totalMonthsInChart: Int = 0,
    @ColumnInfo(name = "total_days_top10") val totalDaysTop10: Int = 0,
    @ColumnInfo(name = "total_weeks_top10") val totalWeeksTop10: Int = 0,
    @ColumnInfo(name = "total_days_at_1") val totalDaysAt1: Int = 0,
    @ColumnInfo(name = "total_weeks_at_1") val totalWeeksAt1: Int = 0,
    @ColumnInfo(name = "consecutive_weeks_top10") val consecutiveWeeksTop10: Int = 0,
    @ColumnInfo(name = "reentry_count") val reentryCount: Int = 0,
    @ColumnInfo(name = "distinct_songs_in_chart") val distinctSongsInChart: Int = 0,
    @ColumnInfo(name = "distinct_albums_in_chart") val distinctAlbumsInChart: Int = 0,
    @ColumnInfo(name = "distinct_songs_top10") val distinctSongsTop10: Int = 0,
    @ColumnInfo(name = "distinct_albums_top10") val distinctAlbumsTop10: Int = 0,
    @ColumnInfo(name = "distinct_songs_at_1") val distinctSongsAt1: Int = 0,
    @ColumnInfo(name = "distinct_albums_at_1") val distinctAlbumsAt1: Int = 0,
    @ColumnInfo(name = "debut_songs_at_1") val debutSongsAt1: Int = 0,
    @ColumnInfo(name = "debut_songs_top10") val debutSongsTop10: Int = 0,
    @ColumnInfo(name = "debut_albums_at_1") val debutAlbumsAt1: Int = 0,
    @ColumnInfo(name = "debut_albums_top10") val debutAlbumsTop10: Int = 0,
    @ColumnInfo(name = "max_simultaneous_songs") val maxSimultaneousSongs: Int = 0,
    @ColumnInfo(name = "max_simultaneous_albums") val maxSimultaneousAlbums: Int = 0,
    @ColumnInfo(name = "max_successive_1_songs") val maxSuccessive1Songs: Int = 0,
    @ColumnInfo(name = "max_successive_1_albums") val maxSuccessive1Albums: Int = 0,
    @ColumnInfo(name = "biggest_jump") val biggestJump: Int = 0,
    @ColumnInfo(name = "biggest_fall") val biggestFall: Int = 0,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "last_seen_date") val lastSeenDate: String? = null
)

/** Table 17 — billboard_history_albums */
@Entity(
    tableName = "billboard_history_albums",
    indices = [Index(value = ["album_id", "period_type"], unique = true), Index("period_type")]
)
data class BillboardHistoryAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "period_type") val periodType: String,
    @ColumnInfo(name = "first_entry_date") val firstEntryDate: String,
    @ColumnInfo(name = "first_entry_position") val firstEntryPosition: Int,
    @ColumnInfo(name = "peak_position") val peakPosition: Int,
    @ColumnInfo(name = "peak_date") val peakDate: String,
    @ColumnInfo(name = "times_at_peak") val timesAtPeak: Int = 1,
    @ColumnInfo(name = "total_days_in_chart") val totalDaysInChart: Int = 0,
    @ColumnInfo(name = "total_weeks_in_chart") val totalWeeksInChart: Int = 0,
    @ColumnInfo(name = "total_months_in_chart") val totalMonthsInChart: Int = 0,
    @ColumnInfo(name = "total_days_top10") val totalDaysTop10: Int = 0,
    @ColumnInfo(name = "total_weeks_top10") val totalWeeksTop10: Int = 0,
    @ColumnInfo(name = "total_days_at_1") val totalDaysAt1: Int = 0,
    @ColumnInfo(name = "total_weeks_at_1") val totalWeeksAt1: Int = 0,
    @ColumnInfo(name = "consecutive_weeks_top10") val consecutiveWeeksTop10: Int = 0,
    @ColumnInfo(name = "reentry_count") val reentryCount: Int = 0,
    @ColumnInfo(name = "distinct_songs_in_chart") val distinctSongsInChart: Int = 0,
    @ColumnInfo(name = "distinct_songs_top10") val distinctSongsTop10: Int = 0,
    @ColumnInfo(name = "distinct_songs_at_1") val distinctSongsAt1: Int = 0,
    @ColumnInfo(name = "debut_songs_at_1") val debutSongsAt1: Int = 0,
    @ColumnInfo(name = "debut_songs_top10") val debutSongsTop10: Int = 0,
    @ColumnInfo(name = "biggest_jump") val biggestJump: Int = 0,
    @ColumnInfo(name = "biggest_fall") val biggestFall: Int = 0,
    @ColumnInfo(name = "weeks_blocked_top5") val weeksBlockedTop5: Int = 0,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "last_seen_date") val lastSeenDate: String? = null
)
