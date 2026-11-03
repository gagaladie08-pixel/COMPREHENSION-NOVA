package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.ScrobbleStatus
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.Certification
import com.novastats.app.domain.CertificationRules
import com.novastats.app.domain.Dates

/**
 * 🎤 Artistes invités des versions fusionnées — crédit « décisif » pour les trois familles d'honneurs.
 *
 * Un invité est un artiste crédité sur au moins une fiche VERSION de la famille mais absent de la
 * fiche RACINE. Il est décisif pour un honneur quand le test contrefactuel passe : SANS les écoutes
 * de sa version, l'honneur n'existerait pas.
 *  - Certification : total famille sans lui < palier requis ;
 *  - Hall of Fame : sur le jour / la période d'ancrage, la famille sans lui ne bat plus le dauphin ;
 *  - Record : la valeur sans lui passe sous le dauphin de la catégorie.
 * Aucun crédit manuel : tout est recalculé depuis les écoutes, à chaque affichage.
 */
data class HonorGuest(
    val artistId: Long,
    val artistName: String,
    /** Fiches versions sur lesquelles il est crédité. */
    val cardIds: List<Long>,
    /** Total de ses écoutes sur ces fiches. */
    val plays: Long,
    /** Version par laquelle il arrive (affichage). */
    val viaTitle: String
)

object HonorGuests {

    /** Artistes présents uniquement sur des versions, triés par apport. */
    suspend fun guestsOf(db: NovaDatabase, rootId: Long): List<HonorGuest> {
        val root = db.trackDao().getById(rootId) ?: return emptyList()
        val rootIds = (db.trackLinkDao().artistIdsForTrack(rootId) + root.artistId).toSet()
        val out = LinkedHashMap<Long, HonorGuest>()
        for (v in db.trackDao().versionsOf(rootId)) {
            val plays = db.trackDao().ownPlays(v.trackId).toLong()
            if (plays <= 0L) continue
            val ids = (db.trackLinkDao().artistIdsForTrack(v.trackId) + v.artistId).distinct()
            for (id in ids) {
                if (id in rootIds) continue
                val name = db.artistDao().getById(id)?.name ?: continue
                val g = out[id]
                out[id] = if (g == null) HonorGuest(id, name, listOf(v.trackId), plays, v.title)
                else g.copy(cardIds = g.cardIds + v.trackId, plays = g.plays + plays)
            }
        }
        return out.values.sortedByDescending { it.plays }
    }

    /** Écoutes confirmées de l'invité sur un jour précis (via ses fiches). */
    suspend fun playsOnDay(db: NovaDatabase, guest: HonorGuest, dayIso: String): Long {
        var sum = 0L
        for (cardId in guest.cardIds) {
            sum += db.scrobbleDao().allOfTrack(cardId).count { s ->
                s.status == ScrobbleStatus.CONFIRMED && Dates.toIso(s.startedAt) == dayIso
            }
        }
        return sum
    }

    /** Écoutes confirmées de l'invité sur une période (bornes ISO inclusives). */
    suspend fun playsInRange(db: NovaDatabase, guest: HonorGuest, fromIso: String, toIso: String): Long {
        var sum = 0L
        for (cardId in guest.cardIds) {
            sum += db.scrobbleDao().allOfTrack(cardId).count { s ->
                s.status == ScrobbleStatus.CONFIRMED && Dates.toIso(s.startedAt).let { it in fromIso..toIso }
            }
        }
        return sum
    }

    /** 🏅 Certification : décisif si sans lui le titre reste sous le palier requis. */
    suspend fun decisiveForCert(db: NovaDatabase, rootId: Long, cert: CertificationEntity): List<HonorGuest> {
        if (cert.entityType != "TRACK") return emptyList()
        val level = CertLevel.entries.firstOrNull { it.dbName == cert.level } ?: return emptyList()
        val required = CertificationRules.TRACK.required(Certification(level, cert.multiplier))
        val root = db.trackDao().getById(rootId) ?: return emptyList()
        return guestsOf(db, rootId).filter { root.playCount - it.plays < required }
    }

    /** 🌍 Hall of Fame ancré sur un jour (Triple Début) : décisif si sans lui le titre ne bat plus le dauphin du jour. */
    suspend fun decisiveOnDay(db: NovaDatabase, rootId: Long, dayIso: String): List<HonorGuest> {
        val family = db.dailyPlayDao().playsOnDay(rootId, dayIso)
        val runnerUp = db.dailyPlayDao().runnerUpOnDay(rootId, dayIso)
        return guestsOf(db, rootId).filter { g -> family - playsOnDay(db, g, dayIso) <= runnerUp }
    }

    /** 🌍 Hall of Fame ancré sur une période (Direct Début / Long Run). */
    suspend fun decisiveInRange(db: NovaDatabase, rootId: Long, fromIso: String, toIso: String): List<HonorGuest> {
        val family = db.dailyPlayDao().playsInRange(rootId, fromIso, toIso)
        val runnerUp = db.dailyPlayDao().runnerUpInRange(rootId, fromIso, toIso)
        return guestsOf(db, rootId).filter { g -> family - playsInRange(db, g, fromIso, toIso) <= runnerUp }
    }

    /** 📊 Record : décisif si la valeur passe sous le dauphin sans ses écoutes du jour d'ancrage. */
    suspend fun decisiveForRecord(
        db: NovaDatabase, rootId: Long, value: Double, runnerUpValue: Double?, valueDateIso: String?
    ): List<HonorGuest> {
        val runnerUp = runnerUpValue ?: 1.0
        return guestsOf(db, rootId).filter { g ->
            val gp = if (valueDateIso != null) playsOnDay(db, g, valueDateIso).toDouble() else g.plays.toDouble()
            value - gp < runnerUp
        }
    }
}
