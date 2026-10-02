package com.novastats.app.data.api

import android.util.Base64
import com.novastats.app.data.ApiKeys
import com.novastats.app.data.api.HttpJson.enc
import com.novastats.app.domain.ApiSource
import com.novastats.app.domain.MetaCandidate
import com.novastats.app.domain.MetadataMatching
import kotlinx.serialization.json.JsonObject

/**
 * Contrat commun des sources de la cascade (toutes gratuites). Chaque méthode renvoie des candidats bruts (jamais d'exception "métier" :
 * liste vide si rien). Les erreurs réseau/HTTP remontent pour être comptées dans api_reliability.
 */
interface MusicApi {
    val source: ApiSource
    suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> = emptyList()
    suspend fun searchAlbum(title: String, artist: String): List<MetaCandidate> = emptyList()
    suspend fun searchArtist(name: String): List<MetaCandidate> = emptyList()
}

private fun String.stripHtml() = replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }

/* ======================================================================= */
/* 1. iTunes Search — pochettes HD, métadonnées. Gratuit, ~20 req/min       */
/* ======================================================================= */
class ITunesApi : MusicApi {
    override val source = ApiSource.ITUNES
    private val limiter = RateLimiter(3_100)

    private fun hd(url: String?) = url?.replace("100x100bb", "600x600bb")

    override suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> = limiter.run {
        val r = HttpJson.get("https://itunes.apple.com/search?term=${enc("$artist $title")}&media=music&entity=song&limit=10")
        r.arr("results").objs().map {
            MetaCandidate(
                source, it.str("trackName") ?: "", it.str("artistName"), it.str("collectionName"), hd(it.str("artworkUrl100")),
                it.long("trackTimeMillis"), it.str("primaryGenreName"), it.str("releaseDate")?.take(10)
            )
        }
    }

    override suspend fun searchAlbum(title: String, artist: String): List<MetaCandidate> = limiter.run {
        val r = HttpJson.get("https://itunes.apple.com/search?term=${enc("$artist $title")}&media=music&entity=album&limit=10")
        r.arr("results").objs().map {
            MetaCandidate(
                source, it.str("collectionName") ?: "", it.str("artistName"), null, hd(it.str("artworkUrl100")),
                null, it.str("primaryGenreName"), it.str("releaseDate")?.take(10)
            )
        }
    }
}

/* ======================================================================= */
/* 2. Spotify — RETIRÉ de la cascade (0.8.5) : Development Mode réservé aux   */
/*    comptes Premium depuis le 9 mars 2026 (HTTP 403). Classe conservée.   */
/* ======================================================================= */
class SpotifyApi : MusicApi {
    override val source = ApiSource.SPOTIFY
    private val limiter = RateLimiter(350)
    @Volatile private var token: String? = null
    @Volatile private var tokenExpiresAt = 0L

    private suspend fun token(force: Boolean = false): String {
        val t = token
        if (!force && t != null && System.currentTimeMillis() < tokenExpiresAt - 30_000) return t
        val basic = Base64.encodeToString("${ApiKeys.spotifyClientId}:${ApiKeys.spotifyClientSecret}".toByteArray(), Base64.NO_WRAP)
        val r = HttpJson.postForm("https://accounts.spotify.com/api/token", mapOf("grant_type" to "client_credentials"), mapOf("Authorization" to "Basic $basic"))
        val access = r.str("access_token") ?: throw IllegalStateException("Spotify : pas de token")
        token = access
        tokenExpiresAt = System.currentTimeMillis() + (r.long("expires_in") ?: 3600L) * 1000
        return access
    }

    private suspend fun search(q: String, type: String): JsonObject? = limiter.run {
        suspend fun call(force: Boolean) = HttpJson.get(
            "https://api.spotify.com/v1/search?q=${enc(q)}&type=$type&limit=10", mapOf("Authorization" to "Bearer ${token(force)}")
        ).obj
        try { call(false) } catch (e: HttpJson.HttpException) { if (e.code == 401) call(true) else throw e }
    }

    private fun image(o: JsonObject?) = o.arr("images").firstObj().str("url")
    private fun firstArtist(o: JsonObject?) = o.arr("artists").firstObj().str("name")

    override suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> =
        search("track:$title artist:$artist", "track").obj("tracks").arr("items").objs().map {
            val album = it.obj("album")
            MetaCandidate(source, it.str("name") ?: "", firstArtist(it), album.str("name"), image(album), it.long("duration_ms"), null, album.str("release_date"), spotifyId = it.str("id"))
        }

    override suspend fun searchAlbum(title: String, artist: String): List<MetaCandidate> =
        search("album:$title artist:$artist", "album").obj("albums").arr("items").objs().map {
            MetaCandidate(source, it.str("name") ?: "", firstArtist(it), null, image(it), null, null, it.str("release_date"), spotifyId = it.str("id"))
        }

    override suspend fun searchArtist(name: String): List<MetaCandidate> =
        search(name, "artist").obj("artists").arr("items").objs().map {
            MetaCandidate(source, it.str("name") ?: "", genre = it.arr("genres")?.firstOrNull().string, imageUrl = image(it), spotifyId = it.str("id"))
        }
}

/* ======================================================================= */
/* 3. Last.fm — infos titres / artistes (bio), tags                         */
/* ======================================================================= */
class LastFmApi : MusicApi {
    override val source = ApiSource.LASTFM
    private val limiter = RateLimiter(300)
    private val base = "https://ws.audioscrobbler.com/2.0/?format=json&autocorrect=1&api_key="

    /** Dernière image de la liste (la plus grande) ; les étoiles grises placeholder sont écartées. */
    private fun image(o: JsonObject?): String? = o.arr("image")?.objs()?.lastOrNull()?.str("#text")?.takeIf { !MetadataMatching.isSuspiciousImage(it) }

    override suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> = limiter.run<List<MetaCandidate>> {
        val t = HttpJson.get("$base${ApiKeys.lastFm}&method=track.getInfo&artist=${enc(artist)}&track=${enc(title)}").obj("track") ?: return@run emptyList()
        val album = t.obj("album")
        listOf(
            MetaCandidate(
                source, t.str("name") ?: "", t.obj("artist").str("name"), album.str("title"), image(album),
                t.long("duration")?.takeIf { it > 0 }, t.obj("toptags").arr("tag").firstObj().str("name"), mbid = t.str("mbid")
            )
        )
    }

    override suspend fun searchAlbum(title: String, artist: String): List<MetaCandidate> = limiter.run<List<MetaCandidate>> {
        val a = HttpJson.get("$base${ApiKeys.lastFm}&method=album.getInfo&artist=${enc(artist)}&album=${enc(title)}").obj("album") ?: return@run emptyList()
        listOf(MetaCandidate(source, a.str("name") ?: "", a.str("artist"), null, image(a), null, a.obj("tags").arr("tag").firstObj().str("name"), mbid = a.str("mbid")))
    }

    override suspend fun searchArtist(name: String): List<MetaCandidate> = limiter.run<List<MetaCandidate>> {
        val a = HttpJson.get("$base${ApiKeys.lastFm}&method=artist.getInfo&artist=${enc(name)}&lang=fr").obj("artist") ?: return@run emptyList()
        listOf(
            MetaCandidate(
                source, a.str("name") ?: "", imageUrl = image(a), bio = a.obj("bio").str("summary")?.stripHtml(),
                genre = a.obj("tags").arr("tag").firstObj().str("name"), mbid = a.str("mbid")
            )
        )
    }
}

/* ======================================================================= */
/* 4. MusicBrainz + Cover Art Archive — 1 req/s, User-Agent obligatoire     */
/* ======================================================================= */
class MusicBrainzApi : MusicApi {
    override val source = ApiSource.MUSICBRAINZ
    val limiter = RateLimiter(1_100)
    private val base = "https://musicbrainz.org/ws/2"

    private fun q(s: String) = "\"" + s.replace("\"", "") + "\""

    /** Pochette "front" d'une release via Cover Art Archive (null si aucune). */
    private suspend fun coverArt(releaseId: String): String? = try {
        val r = HttpJson.get("https://coverartarchive.org/release/$releaseId")
        val front = r.arr("images").objs().firstOrNull { it["front"].string == "true" } ?: r.arr("images").firstObj()
        front.obj("thumbnails").str("500") ?: front.obj("thumbnails").str("large") ?: front.str("image")
    } catch (e: HttpJson.HttpException) { null }

    override suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> {
        val recs = limiter.run {
            HttpJson.get("$base/recording?query=${enc("recording:${q(title)} AND artist:${q(artist)}")}&fmt=json&limit=5").arr("recordings").objs()
        }
        return recs.take(3).map { rec ->
            val release = rec.arr("releases").firstObj()
            val cover = release.str("id")?.let { coverArt(it) }
            MetaCandidate(
                source, rec.str("title") ?: "", rec.arr("artist-credit").firstObj().str("name"), release.str("title"), cover,
                rec.long("length"), null, release.str("date"), mbid = rec.str("id")
            )
        }
    }

    override suspend fun searchAlbum(title: String, artist: String): List<MetaCandidate> {
        val rels = limiter.run {
            HttpJson.get("$base/release?query=${enc("release:${q(title)} AND artist:${q(artist)}")}&fmt=json&limit=5").arr("releases").objs()
        }
        return rels.take(3).map { rel ->
            MetaCandidate(source, rel.str("title") ?: "", rel.arr("artist-credit").firstObj().str("name"), null, rel.str("id")?.let { coverArt(it) }, null, null, rel.str("date"), mbid = rel.str("id"))
        }
    }

    /** Pas de photo chez MusicBrainz : sert à obtenir le mbid (Fanart.tv). */
    override suspend fun searchArtist(name: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("$base/artist?query=${enc("artist:${q(name)}")}&fmt=json&limit=3").arr("artists").objs().map {
            MetaCandidate(source, it.str("name") ?: "", mbid = it.str("id"), genre = it.arr("tags").firstObj().str("name"))
        }
    }

    suspend fun artistMbid(name: String): String? =
        searchArtist(name).firstOrNull { MetadataMatching.similarity(it.name, name) >= 0.85 }?.mbid
}

/* ======================================================================= */
/* 5. TheAudioDB — clé publique gratuite "123" : 30 req/min, 1 résultat     */
/*    par recherche. Artistes (photo, bio) et albums seulement : la          */
/*    recherche de titres gratuite ne renvoie presque jamais de vignette.    */
/* ======================================================================= */
class TheAudioDbApi : MusicApi {
    override val source = ApiSource.THEAUDIODB
    private val limiter = RateLimiter(2_100) // ≈ 28 req/min < quota gratuit 30/min (sinon HTTP 429)
    private val base get() = "https://www.theaudiodb.com/api/v1/json/${ApiKeys.theAudioDb.ifBlank { "123" }}"

    override suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("$base/searchtrack.php?s=${enc(artist)}&t=${enc(title)}").arr("track").objs().map {
            MetaCandidate(source, it.str("strTrack") ?: "", it.str("strArtist"), it.str("strAlbum"), it.str("strTrackThumb"), it.long("intDuration"), it.str("strGenre"), mbid = it.str("strMusicBrainzID"))
        }
    }

    override suspend fun searchAlbum(title: String, artist: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("$base/searchalbum.php?s=${enc(artist)}&a=${enc(title)}").arr("album").objs().map {
            MetaCandidate(source, it.str("strAlbum") ?: "", it.str("strArtist"), null, it.str("strAlbumThumb"), null, it.str("strGenre"), it.str("intYearReleased"), mbid = it.str("strMusicBrainzID"))
        }
    }

    override suspend fun searchArtist(name: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("$base/search.php?s=${enc(name)}").arr("artists").objs().map {
            MetaCandidate(
                source, it.str("strArtist") ?: "", imageUrl = it.str("strArtistThumb"), genre = it.str("strGenre"),
                bio = (it.str("strBiographyFR") ?: it.str("strBiographyEN"))?.take(1500), mbid = it.str("strMusicBrainzID")
            )
        }
    }
}

/* ======================================================================= */
/* 6. Deezer — public, sans clé                                             */
/* ======================================================================= */
class DeezerApi : MusicApi {
    override val source = ApiSource.DEEZER
    private val limiter = RateLimiter(300)

    override suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("https://api.deezer.com/search/track?q=${enc("artist:\"$artist\" track:\"$title\"")}&limit=10").arr("data").objs().map {
            MetaCandidate(source, it.str("title") ?: "", it.obj("artist").str("name"), it.obj("album").str("title"), it.obj("album").str("cover_xl") ?: it.obj("album").str("cover_big"), it.long("duration")?.times(1000))
        }
    }

    override suspend fun searchAlbum(title: String, artist: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("https://api.deezer.com/search/album?q=${enc("artist:\"$artist\" album:\"$title\"")}&limit=10").arr("data").objs().map {
            MetaCandidate(source, it.str("title") ?: "", it.obj("artist").str("name"), null, it.str("cover_xl") ?: it.str("cover_big"))
        }
    }

    override suspend fun searchArtist(name: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("https://api.deezer.com/search/artist?q=${enc(name)}&limit=5").arr("data").objs().map {
            MetaCandidate(source, it.str("name") ?: "", imageUrl = it.str("picture_xl") ?: it.str("picture_big"))
        }
    }
}

/* ======================================================================= */
/* 7. Discogs — pochettes (fallback collectionneurs). Token OBLIGATOIRE :    */
/*    sans authentification, /database/search renvoie HTTP 401.             */
/* ======================================================================= */
class DiscogsApi : MusicApi {
    override val source = ApiSource.DISCOGS
    private val limiter = RateLimiter(1_500)

    private fun headers() = if (ApiKeys.has(ApiKeys.discogsToken)) mapOf("Authorization" to "Discogs token=${ApiKeys.discogsToken}") else emptyMap()

    private suspend fun search(q: String): List<MetaCandidate> = limiter.run {
        HttpJson.get("https://api.discogs.com/database/search?q=${enc(q)}&type=release&per_page=5", headers()).arr("results").objs().map {
            val full = it.str("title") ?: ""
            val parts = full.split(" - ", limit = 2)
            MetaCandidate(
                source, parts.getOrElse(1) { full }.trim(), parts.getOrNull(0)?.trim()?.replace(Regex("\\s\\(\\d+\\)$"), ""), null,
                it.str("cover_image")?.takeIf { u -> !u.contains("spacer.gif") }, null, it.arr("genre")?.firstOrNull().string, it.str("year")
            )
        }
    }

    override suspend fun searchTrack(title: String, artist: String) = search("$artist - $title")
    override suspend fun searchAlbum(title: String, artist: String) = search("$artist - $title")
}

/* ======================================================================= */
/* 8. Fanart.tv — photos d'artistes HD (nécessite le mbid MusicBrainz)      */
/* ======================================================================= */
class FanartApi(private val musicBrainz: MusicBrainzApi) : MusicApi {
    override val source = ApiSource.FANART
    private val limiter = RateLimiter(300)

    override suspend fun searchArtist(name: String): List<MetaCandidate> {
        val mbid = musicBrainz.artistMbid(name) ?: return emptyList()
        return limiter.run<List<MetaCandidate>> {
            val r = HttpJson.get("https://webservice.fanart.tv/v3/music/$mbid?api_key=${ApiKeys.fanart}") ?: return@run emptyList()
            val url = r.arr("artistthumb").firstObj().str("url") ?: r.arr("artistbackground").firstObj().str("url") ?: return@run emptyList()
            listOf(MetaCandidate(source, r.str("name") ?: name, imageUrl = url, mbid = mbid))
        }
    }
}

/* ======================================================================= */
/* 9. Wikidata + Wikimedia Commons — photos d'artistes libres, sans clé     */
/*    wbsearchentities → entités « musicien / groupe » → P18 (image),        */
/*    P434 (mbid). URL Commons via Special:FilePath (redirection suivie).   */
/* ======================================================================= */
class WikidataApi : MusicApi {
    override val source = ApiSource.WIKIDATA
    private val limiter = RateLimiter(1_000)
    private val base = "https://www.wikidata.org/w/api.php"
    private val musicWords = Regex("(singer|musician|band|group|rapper|songwriter|idol|duo|trio|quartet|producer|\\bdj\\b|composer|vocalist|artist|chanteu|musicien|groupe|rappeu)", RegexOption.IGNORE_CASE)

    override suspend fun searchArtist(name: String): List<MetaCandidate> {
        val hits = limiter.run {
            HttpJson.get("$base?action=wbsearchentities&search=${enc(name)}&language=en&uselang=en&type=item&limit=7&format=json").arr("search").objs()
        }.filter { (it.str("description") ?: "").contains(musicWords) }.take(3)
        if (hits.isEmpty()) return emptyList()
        val ids = hits.mapNotNull { it.str("id") }
        val entities = limiter.run {
            HttpJson.get("$base?action=wbgetentities&ids=${enc(ids.joinToString("|"))}&props=claims&format=json").obj("entities")
        } ?: return emptyList()
        return hits.mapNotNull { hit ->
            val id = hit.str("id") ?: return@mapNotNull null
            val claims = entities.obj(id).obj("claims")
            val file = claims.arr("P18").firstObj().obj("mainsnak").obj("datavalue").str("value") ?: return@mapNotNull null
            val mbid = claims.arr("P434").firstObj().obj("mainsnak").obj("datavalue").str("value")
            MetaCandidate(
                source, hit.str("label") ?: name,
                imageUrl = "https://commons.wikimedia.org/wiki/Special:FilePath/${enc(file.replace(' ', '_'))}?width=800",
                bio = hit.str("description"), mbid = mbid
            )
        }
    }
}

/* ======================================================================= */
/* 10. Genius — pochettes (song_art) et photos d'artistes, token gratuit    */
/* ======================================================================= */
class GeniusApi : MusicApi {
    override val source = ApiSource.GENIUS
    private val limiter = RateLimiter(400)

    private suspend fun hits(q: String): List<JsonObject> = limiter.run {
        HttpJson.get("https://api.genius.com/search?q=${enc(q)}&per_page=10", mapOf("Authorization" to "Bearer ${ApiKeys.genius}"))
            .obj("response").arr("hits").objs().mapNotNull { it.obj("result") }
    }

    override suspend fun searchTrack(title: String, artist: String): List<MetaCandidate> = hits("$artist $title").map {
        MetaCandidate(
            source, it.str("title") ?: "", it.obj("primary_artist").str("name"), null,
            it.str("song_art_image_url") ?: it.str("header_image_url"), null, null, it.str("release_date_for_display")
        )
    }

    override suspend fun searchArtist(name: String): List<MetaCandidate> = hits(name)
        .mapNotNull { it.obj("primary_artist") }
        .distinctBy { it.str("id") }
        .mapNotNull { a -> a.str("image_url")?.let { MetaCandidate(source, a.str("name") ?: "", imageUrl = it) } }
}

/* ======================================================================= */
/* 11. YouTube Data API v3 — DERNIER RECOURS (clé gratuite, 10 000 unités  */
/*     par jour = ~100 recherches). Titres/albums : vignette du clip         */
/*     (catégorie Musique) ; artistes : avatar de la chaîne officielle.      */
/*     Résultat accepté au score forfaitaire 70 → toujours « 🟡 À vérifier ».*/
/* ======================================================================= */
class YouTubeApi(private val quotaAvailable: suspend () -> Boolean) : MusicApi {
    override val source = ApiSource.YOUTUBE
    private val limiter = RateLimiter(1_000)
    private val base = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=3&safeSearch=moderate&key="

    private suspend fun search(query: String, type: String): JsonObject? {
        if (!quotaAvailable()) return null
        return limiter.run {
            val extra = if (type == "video") "&videoCategoryId=10" else ""
            HttpJson.get("$base${ApiKeys.youtube}&type=$type$extra&q=${enc(query)}").arr("items").firstObj()
        }
    }

    private fun JsonObject?.thumb(): String? {
        val t = this.obj("snippet").obj("thumbnails")
        return t.obj("high").str("url") ?: t.obj("medium").str("url") ?: t.obj("default").str("url")
    }

    /** YouTube ne peut rien vérifier : le candidat reprend les valeurs demandées (score forfaitaire côté enricher). */
    override suspend fun searchTrack(title: String, artist: String) =
        search("$artist $title official", "video")?.thumb()?.let { listOf(MetaCandidate(source, title, artist, imageUrl = it)) }.orEmpty()

    override suspend fun searchAlbum(title: String, artist: String) =
        search("$artist $title album", "video")?.thumb()?.let { listOf(MetaCandidate(source, title, artist, imageUrl = it)) }.orEmpty()

    override suspend fun searchArtist(name: String) =
        search("$name official", "channel")?.thumb()?.let { listOf(MetaCandidate(source, name, imageUrl = it)) }.orEmpty()
}
