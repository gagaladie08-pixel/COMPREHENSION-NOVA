package com.novastats.app.domain

/**
 * Règles partagées par Perfect All Kill et Most Cumulative #1 — sans accès Android/Room.
 * Les trois colonnes restent séparées : un jour, une semaine et un mois ne sont jamais additionnés.
 */
object ArtistChartNumberOnes {
    const val HOT_100 = "HOT_100"
    const val ARTIST_50 = "ARTIST_50"
    const val ALBUMS_75 = "ALBUMS_75"

    val charts = listOf(HOT_100, ARTIST_50, ALBUMS_75)

    /** Leaders d'un seul snapshot/cadence. Les artistes crédités et propriétaires sont des ensembles. */
    data class Snapshot(
        val date: String,
        val hot100ArtistIds: Set<Long>,
        val artist50LeaderId: Long?,
        val albums75ArtistIds: Set<Long>
    )

    /**
     * PAK est une simultanéité entre charts : l'artiste n°1 de l'Artist 50 doit aussi être crédité
     * au titre n°1 du Hot 100 et être le propriétaire identifiable de l'album n°1 des Albums 75.
     */
    fun perfectAllKillArtist(snapshot: Snapshot): Long? = snapshot.artist50LeaderId
        ?.takeIf { it in snapshot.hot100ArtistIds && it in snapshot.albums75ArtistIds }

    /**
     * Historique des ancres #1, par artiste et par chart.
     * Les ensembles de dates rendent le calcul idempotent même si une source contient une ligne répétée.
     */
    fun cumulativeDates(snapshots: Iterable<Snapshot>): Map<String, Map<Long, Set<String>>> {
        val result = charts.associateWith { HashMap<Long, MutableSet<String>>() }
        for (snapshot in snapshots) {
            snapshot.hot100ArtistIds.forEach { artistId ->
                result.getValue(HOT_100).getOrPut(artistId) { linkedSetOf() }.add(snapshot.date)
            }
            snapshot.artist50LeaderId?.let { artistId ->
                result.getValue(ARTIST_50).getOrPut(artistId) { linkedSetOf() }.add(snapshot.date)
            }
            snapshot.albums75ArtistIds.forEach { artistId ->
                result.getValue(ALBUMS_75).getOrPut(artistId) { linkedSetOf() }.add(snapshot.date)
            }
        }
        return result.mapValues { (_, byArtist) -> byArtist.mapValues { (_, dates) -> dates.toSet() } }
    }
}
