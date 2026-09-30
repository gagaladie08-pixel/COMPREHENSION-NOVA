package com.novastats.app.data

import com.novastats.app.BuildConfig

/**
 * Clés API — injectées depuis local.properties via BuildConfig.
 * JAMAIS de clé en dur ici. Si une clé est vide, l'API correspondante est simplement sautée dans la cascade.
 */
object ApiKeys {
    val lastFm: String get() = BuildConfig.LASTFM_API_KEY
    val spotifyClientId: String get() = BuildConfig.SPOTIFY_CLIENT_ID
    val spotifyClientSecret: String get() = BuildConfig.SPOTIFY_CLIENT_SECRET
    val fanart: String get() = BuildConfig.FANART_API_KEY
    val googleApiKey: String get() = BuildConfig.GOOGLE_API_KEY
    val googleEngineId: String get() = BuildConfig.GOOGLE_ENGINE_ID
    val discogsToken: String get() = BuildConfig.DISCOGS_TOKEN
    val theAudioDb: String get() = BuildConfig.THEAUDIODB_API_KEY

    fun has(key: String) = key.isNotBlank()
}
