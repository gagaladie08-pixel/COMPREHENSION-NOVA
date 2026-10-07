package com.novastats.app.domain

/**
 * 💽 Règle 13 — albums normaux coupés par les duos.
 *
 * Un titre se rattache à l'album existant (même titre normalisé) d'UN de ses artistes — principal ou invité — et
 * pas forcément à celui de l'artiste écrit en premier par le lecteur. « One Kiss » (Calvin Harris & Dua Lipa) rejoint
 * « Dua Lipa (Complete Edition) » de Dua Lipa au lieu de créer un album homonyme au nom de Calvin Harris.
 *
 * Logique pure (sans base) pour être testable : [pick] au moment de la résolution, [plan] à la consolidation.
 * Les albums partagés (règle 12, `artist_id` NULL) ne passent jamais par ici.
 */
object AlbumOwnership {

    /** Album candidat (même titre, propriétaire parmi les artistes du titre). */
    data class Candidate(val albumId: Long, val ownerId: Long, val tracksCreditingOwner: Int, val playCount: Int)

    /**
     * Plusieurs albums possibles → celui dont le propriétaire est l'artiste commun (présent sur le plus de titres de
     * son album) ; à égalité, le plus écouté. Null si aucun candidat.
     */
    fun pick(candidates: List<Candidate>): Long? =
        candidates.maxWithOrNull(compareBy<Candidate> { it.tracksCreditingOwner }.thenBy { it.playCount }.thenBy { -it.albumId })?.albumId

    /** Album normal existant : propriétaire + artistes crédités sur chacun de ses titres (principal et invités). */
    data class AlbumInfo(val albumId: Long, val ownerId: Long, val trackArtists: Map<Long, Set<Long>>, val playCount: Int)

    /** Fusion décidée : [absorbed] rejoignent [keepId] ; [newOwner] non nul si le propriétaire doit changer. */
    data class MergePlan(val keepId: Long, val newOwner: Long?, val absorbed: List<Long>)

    /**
     * Albums homonymes (même clé normalisée) → groupes à fusionner.
     * Garde-fou : deux albums ne sont liés que si le propriétaire de l'un figure parmi les artistes des titres de
     * l'autre, ou s'ils partagent un artiste crédité (« Greatest Hits » de Queen et d'ABBA restent séparés). Dans un groupe lié, le propriétaire retenu est
     * l'artiste commun : celui crédité sur le plus de titres distincts de l'ensemble ; l'album conservé est le sien
     * (sinon le plus écouté, dont le propriétaire devient l'artiste commun).
     */
    fun plan(albums: List<AlbumInfo>): List<MergePlan> {
        if (albums.size < 2) return emptyList()
        val parent = albums.indices.toMutableList()
        fun find(i: Int): Int { var x = i; while (parent[x] != x) x = parent[x]; return x }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }
        for (i in albums.indices) for (j in i + 1 until albums.size) {
            val a = albums[i]; val b = albums[j]
            val creditsA = a.trackArtists.values.flatten().toSet(); val creditsB = b.trackArtists.values.flatten().toSet()
            val linked = b.ownerId in creditsA || a.ownerId in creditsB || (creditsA intersect creditsB).isNotEmpty()
            if (linked) union(i, j)
        }
        return albums.indices.groupBy { find(it) }.values.filter { it.size > 1 }.map { idx ->
            val group = idx.map { albums[it] }
            // Artiste commun : crédité sur le plus de titres distincts du groupe (à égalité : propriétaire le plus écouté, puis plus petit id)
            val credits = HashMap<Long, Int>()
            group.forEach { al -> al.trackArtists.values.forEach { set -> set.forEach { credits[it] = (credits[it] ?: 0) + 1 } } }
            val ownerPlays = group.groupBy { it.ownerId }.mapValues { (_, v) -> v.sumOf { it.playCount } }
            val common = credits.entries.maxWithOrNull(
                compareBy<Map.Entry<Long, Int>> { it.value }.thenBy { ownerPlays[it.key] ?: -1 }.thenBy { -it.key }
            )!!.key
            val keep = group.filter { it.ownerId == common }.maxByOrNull { it.playCount }
                ?: group.maxWithOrNull(compareBy<AlbumInfo> { it.playCount }.thenBy { -it.albumId })!!
            MergePlan(keep.albumId, if (keep.ownerId == common) null else common, group.filter { it.albumId != keep.albumId }.map { it.albumId })
        }
    }
}
