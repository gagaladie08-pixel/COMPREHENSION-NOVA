package com.novastats.app.domain

/**
 * Règles partagées par Perfect All Kill et Most Cumulative #1 — sans accès Android/Room.
 * Les cumuls gardent deux charts indépendants et chaque cadence reste séparée.
 */
object ArtistChartNumberOnes {
    const val HOT_100 = "HOT_100"
    const val ARTIST_50 = "ARTIST_50"
    const val ALBUMS_75 = "ALBUMS_75"

    /** Charts requis pour un PAK : l'Artist 50 reste indispensable à cette distinction. */
    val charts = listOf(HOT_100, ARTIST_50, ALBUMS_75)

    /** Charts du record Most Cumulative #1 : l'Artist 50 n'en fait pas partie. */
    val cumulativeCharts = listOf(HOT_100, ALBUMS_75)

    /** Leaders d'un seul snapshot/cadence. Les artistes crédités et propriétaires sont des ensembles. */
    data class Snapshot(
        val date: String,
        val hot100ArtistIds: Set<Long>,
        val artist50LeaderId: Long?,
        val albums75ArtistIds: Set<Long>
    )

    data class PerfectAllKillEvent(val artistId: Long, val cadence: String, val date: String)

    /**
     * PAK est une simultanéité entre charts : l'artiste n°1 de l'Artist 50 doit aussi être crédité
     * au titre n°1 du Hot 100 et être le propriétaire identifiable de l'album n°1 des Albums 75.
     */
    fun perfectAllKillArtist(snapshot: Snapshot): Long? = snapshot.artist50LeaderId
        ?.takeIf { it in snapshot.hot100ArtistIds && it in snapshot.albums75ArtistIds }

    /** Dates PAK distinctes par artiste pour UNE cadence; une même date ne peut compter deux fois. */
    fun perfectAllKillDates(events: Iterable<PerfectAllKillEvent>, cadence: String): Map<Long, Set<String>> {
        val result = HashMap<Long, MutableSet<String>>()
        for (event in events) {
            if (event.cadence == cadence) result.getOrPut(event.artistId) { linkedSetOf() }.add(event.date)
        }
        return result.mapValues { (_, dates) -> dates.toSet() }
    }

    /**
     * Historique des ancres #1 du record cumulatif, par artiste et par chart.
     * Artist 50 est intentionnellement exclu; les ensembles dédupliquent les snapshots répétés.
     */
    fun cumulativeDates(snapshots: Iterable<Snapshot>): Map<String, Map<Long, Set<String>>> {
        val result = cumulativeCharts.associateWith { HashMap<Long, MutableSet<String>>() }
        for (snapshot in snapshots) {
            snapshot.hot100ArtistIds.forEach { artistId ->
                result.getValue(HOT_100).getOrPut(artistId) { linkedSetOf() }.add(snapshot.date)
            }
            snapshot.albums75ArtistIds.forEach { artistId ->
                result.getValue(ALBUMS_75).getOrPut(artistId) { linkedSetOf() }.add(snapshot.date)
            }
        }
        return result.mapValues { (_, byArtist) -> byArtist.mapValues { (_, dates) -> dates.toSet() } }
    }
}
