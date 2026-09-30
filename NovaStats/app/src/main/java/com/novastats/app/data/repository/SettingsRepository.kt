package com.novastats.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    }

    /** Apps musicales reconnues par défaut (opt-in : l'utilisateur confirme dans les paramètres). */
    val defaultWhitelist = setOf(
        "com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music",
        "deezer.android.app", "com.aspiro.tidal", "com.amazon.mp3", "com.soundcloud.android",
        "com.google.android.youtube", "com.boomplay.music" , "com.transsion.boomplayer"
    )

    val themeId: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: NovaThemes.DEFAULT.id }
    val thresholdSec: Flow<Int> = context.dataStore.data.map { it[Keys.THRESHOLD] ?: ScrobbleRules.DEFAULT_THRESHOLD_SEC }
    val whitelist: Flow<Set<String>> = context.dataStore.data.map { it[Keys.WHITELIST] ?: defaultWhitelist }
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
}
