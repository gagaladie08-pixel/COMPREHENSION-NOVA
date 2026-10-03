package com.novastats.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.novastats.app.domain.ScrobbleRules
import com.novastats.app.ui.theme.NovaThemes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

/** Paramètres utilisateur (onglet ⚙️). Hot-reload : le service observe ces flows. */
class SettingsRepository(private val context: Context) {
    companion object { const val FONT_MIN = 80; const val FONT_MAX = 140; const val FONT_STEP = 10 }

    private object Keys {
        val THEME = stringPreferencesKey("theme_id")
        val THRESHOLD = intPreferencesKey("scrobble_threshold_sec")
        val WHITELIST = stringSetPreferencesKey("whitelist_apps")
        val BLACKLIST_ARTISTS = stringSetPreferencesKey("blacklist_artists")
        val BLACKLIST_KEYWORDS = stringSetPreferencesKey("blacklist_keywords")
        val FILTER_LONG_TRACKS = booleanPreferencesKey("filter_long_tracks")
        val TRACK_MUTED = booleanPreferencesKey("track_when_muted")
        val WATCHDOG = booleanPreferencesKey("watchdog_enabled")
        val FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
        val AUTO_ENRICH = booleanPreferencesKey("auto_enrich")
        val ENRICH_WIFI_ONLY = booleanPreferencesKey("enrich_wifi_only")
        val GOOGLE_QUOTA_DAY = stringPreferencesKey("google_quota_day")
        val GOOGLE_QUOTA_COUNT = intPreferencesKey("google_quota_count")
        val NOTIF_DISABLED = stringSetPreferencesKey("notif_disabled")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val HAPTICS = booleanPreferencesKey("haptics")
        val FONT_SCALE = intPreferencesKey("font_scale_pct")
        val COMPACT_ROWS = booleanPreferencesKey("compact_rows")
        val AWARDS_REVEALED = stringSetPreferencesKey("awards_revealed_years")
    }

    /** Clés des 11 notifications (cahier des charges — toutes actives par défaut). */
    object Notif {
        const val CERT_SILVER = "CERT_SILVER"; const val CERT_GOLD = "CERT_GOLD"; const val CERT_PLATINUM = "CERT_PLATINUM"; const val CERT_DIAMOND = "CERT_DIAMOND"
        const val CERT_MULTIPLIERS = "CERT_MULTIPLIERS"
        const val P_STAR = "P_STAR"; const val P_SUPERSTAR = "P_SUPERSTAR"; const val P_MEGASTAR = "P_MEGASTAR"; const val P_LEGENDE = "P_LEGENDE"; const val P_MYTHIQUE = "P_MYTHIQUE"
        const val HOF = "HOF"
        val ALL = listOf(CERT_SILVER, CERT_GOLD, CERT_PLATINUM, CERT_DIAMOND, CERT_MULTIPLIERS, P_STAR, P_SUPERSTAR, P_MEGASTAR, P_LEGENDE, P_MYTHIQUE, HOF)
    }

    /**
     * Apps musicales connues (proposées dans l'écran Whitelist).
     * Par défaut la whitelist est VIDE = toutes les apps média sont suivies.
     */
    val knownMusicApps = linkedMapOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.google.android.youtube" to "YouTube",
        "com.apple.android.music" to "Apple Music",
        "deezer.android.app" to "Deezer",
        "com.afmobi.boomplayer" to "Boomplay",
        "com.aspiro.tidal" to "Tidal",
        "com.amazon.mp3" to "Amazon Music",
        "com.soundcloud.android" to "SoundCloud",
        "com.audiomack" to "Audiomack",
        "com.shazam.android" to "Shazam",
        "com.maxmpz.audioplayer" to "Poweramp",
        "com.samsung.android.app.music.chn" to "Samsung Music",
        "com.sec.android.app.music" to "Samsung Music",
        "com.miui.player" to "Mi Music",
        "com.transsion.tpen" to "Vishaplayer",
        "com.transsion.music" to "Boomplayer (Transsion)",
        "org.videolan.vlc" to "VLC"
    )

    val themeId: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: NovaThemes.DEFAULT.id }
    val thresholdSec: Flow<Int> = context.dataStore.data.map { it[Keys.THRESHOLD] ?: ScrobbleRules.DEFAULT_THRESHOLD_SEC }
    val whitelist: Flow<Set<String>> = context.dataStore.data.map { it[Keys.WHITELIST] ?: emptySet() }
    val blacklistArtists: Flow<Set<String>> = context.dataStore.data.map { it[Keys.BLACKLIST_ARTISTS] ?: emptySet() }
    val blacklistKeywords: Flow<Set<String>> = context.dataStore.data.map { it[Keys.BLACKLIST_KEYWORDS] ?: setOf("podcast", "episode", "épisode") }
    val filterLongTracks: Flow<Boolean> = context.dataStore.data.map { it[Keys.FILTER_LONG_TRACKS] ?: true }
    val trackWhenMuted: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRACK_MUTED] ?: false }
    val watchdogEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.WATCHDOG] ?: true }
    val firstLaunchDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_LAUNCH_DONE] ?: false }

    suspend fun setTheme(id: String) = context.dataStore.edit { it[Keys.THEME] = id }
    suspend fun setThreshold(sec: Int) {
        require(sec in ScrobbleRules.ALLOWED_THRESHOLDS_SEC)
        context.dataStore.edit { it[Keys.THRESHOLD] = sec }
    }
    suspend fun setWhitelist(apps: Set<String>) = context.dataStore.edit { it[Keys.WHITELIST] = apps }
    suspend fun setBlacklistArtists(v: Set<String>) = context.dataStore.edit { it[Keys.BLACKLIST_ARTISTS] = v }
    suspend fun setBlacklistKeywords(v: Set<String>) = context.dataStore.edit { it[Keys.BLACKLIST_KEYWORDS] = v }
    suspend fun setFilterLongTracks(v: Boolean) = context.dataStore.edit { it[Keys.FILTER_LONG_TRACKS] = v }
    suspend fun setTrackWhenMuted(v: Boolean) = context.dataStore.edit { it[Keys.TRACK_MUTED] = v }
    suspend fun setWatchdog(v: Boolean) = context.dataStore.edit { it[Keys.WATCHDOG] = v }
    suspend fun setFirstLaunchDone() = context.dataStore.edit { it[Keys.FIRST_LAUNCH_DONE] = true }

    /* ---- Nova Awards ---- */
    /** Années dont la cérémonie de révélation (cartes une par une) a déjà été jouée. */
    val awardsRevealedYears: Flow<Set<String>> = context.dataStore.data.map { it[Keys.AWARDS_REVEALED] ?: emptySet() }
    suspend fun markAwardsRevealed(year: Int) = context.dataStore.edit { it[Keys.AWARDS_REVEALED] = (it[Keys.AWARDS_REVEALED] ?: emptySet()) + year.toString() }

    /* ---- Notifications ---- */
    /** Notifications désactivées (toutes actives par défaut). */
    val disabledNotifications: Flow<Set<String>> = context.dataStore.data.map { it[Keys.NOTIF_DISABLED] ?: emptySet() }
    suspend fun setNotificationEnabled(key: String, enabled: Boolean) = context.dataStore.edit {
        val cur = it[Keys.NOTIF_DISABLED] ?: emptySet()
        it[Keys.NOTIF_DISABLED] = if (enabled) cur - key else cur + key
    }

    /* ---- Premium / avancé ---- */
    val autoBackup: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_BACKUP] ?: false }
    val lastBackupAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_BACKUP_AT] }
    val haptics: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS] ?: true }
    /** 🔠 Taille du texte en % (80…140, pas de 10) — appliquée à toute l'app via LocalDensity.fontScale. */
    val fontScalePct: Flow<Int> = context.dataStore.data.map { it[Keys.FONT_SCALE] ?: 100 }
    suspend fun setFontScalePct(pct: Int) = context.dataStore.edit { it[Keys.FONT_SCALE] = pct.coerceIn(FONT_MIN, FONT_MAX) }
    suspend fun setAutoBackup(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_BACKUP] = v }
    suspend fun setLastBackupAt(v: Long) = context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = v }

    /* ---- Enrichissement APIs ---- */
    val autoEnrich: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_ENRICH] ?: true }
    val enrichWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[Keys.ENRICH_WIFI_ONLY] ?: false }
    suspend fun setAutoEnrich(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_ENRICH] = v }
    suspend fun setEnrichWifiOnly(v: Boolean) = context.dataStore.edit { it[Keys.ENRICH_WIFI_ONLY] = v }

    /** Quota YouTube Data API (dernier recours) : 10 000 unités/jour = 100 recherches — on s'arrête à [GOOGLE_DAILY_CAP]. */
    suspend fun tryConsumeGoogleQuota(today: String): Boolean {
        var allowed = false
        context.dataStore.edit { p ->
            val count = if (p[Keys.GOOGLE_QUOTA_DAY] == today) p[Keys.GOOGLE_QUOTA_COUNT] ?: 0 else 0
            if (count < GOOGLE_DAILY_CAP) {
                allowed = true
                p[Keys.GOOGLE_QUOTA_DAY] = today
                p[Keys.GOOGLE_QUOTA_COUNT] = count + 1
            }
        }
        return allowed
    }

    companion object { const val GOOGLE_DAILY_CAP = 80 }
}
