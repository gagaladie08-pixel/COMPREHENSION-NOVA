package com.novastats.app.service

import com.novastats.app.domain.ScrobbleRules

/**
 * Machine à états d'une écoute — Kotlin pur (testable sans Android).
 *
 * Règles (Module Détection v3.0) :
 *  - Timer cumulé : le temps est conservé après une pause courte (< 10 min).
 *  - Pause longue (≥ 10 min) : la reprise démarre une NOUVELLE écoute.
 *  - Skip avant seuil : ignoré, rien de loggé.
 *  - Loop (repeat one) : chaque relecture = nouvelle écoute (détection via retour de position à ~0).
 *  - Crossfade : titre A clôturé si titre B détecté en < 10 s.
 *  - Seuil : 15 / 30 / 60 / 90 s (hot-reload).
 *
 * Robustesse (0.7.1) — le temps écouté n'est plus une simple différence d'horloge :
 *  1. **Plafond durée** : jamais plus que la durée du morceau (+ 5 s) — ou 20 min si inconnue.
 *  2. **Anti-gel** : si aucun tick / événement n'arrive pendant > 8 s (process gelé par le système,
 *     listener déconnecté…), le trou n'est pas compté ; un trou ≥ pause longue clôture l'écoute.
 *  3. **Position réelle** : quand le lecteur fournit sa position, le temps écouté est borné par
 *     l'avancée réelle de la position (+ 15 s) et un lecteur dont la position n'avance plus est traité comme en pause.
 */
class ScrobbleTracker(
    private var thresholdSec: Int = ScrobbleRules.DEFAULT_THRESHOLD_SEC,
    private val clock: () -> Long = System::currentTimeMillis
) {
    data class TrackKey(
        val title: String, val artist: String?, val album: String?, val durationMs: Long?, val sourceApp: String,
        val albumArtist: String? = null
    ) {
        val display get() = if (artist.isNullOrBlank()) title else "$artist — $title"
    }

    /** Écoute en cours. */
    data class Session(
        val key: TrackKey,
        val startedAt: Long,
        var accumulatedMs: Long = 0,
        var playingSince: Long? = null,
        var pausedAt: Long? = null,
        var validatedAt: Long? = null,
        var lastPositionMs: Long = 0,
        var lastPositionAt: Long = 0,
        val detectionSource: String,
        /** Position du lecteur au début de l'écoute (pour mesurer l'avancée réelle). */
        var startPositionMs: Long = 0,
        /** Position maximale réellement observée (lecteur) ; 0 = jamais reçue. */
        var maxPositionMs: Long = 0,
        /** Au moins une position > 0 reçue du lecteur → le plafond par position est fiable. */
        var positionKnown: Boolean = false,
        /** Dernier instant où la position réelle a avancé. */
        var positionAdvancedAt: Long = 0
    ) {
        /** Temps écouté brut (horloge), sans plafonds. */
        fun rawListenedMs(now: Long): Long = accumulatedMs + (playingSince?.let { now - it } ?: 0L)

        /** Temps écouté plafonné : durée du morceau, et avancée réelle de la position quand elle est connue. */
        fun listenedMs(now: Long): Long {
            var l = ScrobbleRules.capListened(rawListenedMs(now), key.durationMs)
            if (positionKnown && maxPositionMs > startPositionMs) {
                l = l.coerceAtMost(maxPositionMs - startPositionMs + ScrobbleRules.POSITION_MARGIN_MS)
            }
            return l.coerceAtLeast(0L)
        }

        /** Position estimée dans le morceau (dernière position connue + temps écoulé si en lecture). */
        fun estimatedPositionMs(now: Long): Long {
            val p = if (playingSince != null && lastPositionAt > 0) lastPositionMs + (now - lastPositionAt) else lastPositionMs
            return key.durationMs?.let { p.coerceAtMost(it) } ?: p
        }
        val isValidated get() = validatedAt != null

        internal fun observePosition(positionMs: Long?, now: Long) {
            if (positionMs == null || positionMs < 0) return
            lastPositionMs = positionMs; lastPositionAt = now
            if (positionMs > 0) {
                if (!positionKnown) {
                    // Première position connue : on en déduit la position de départ (position − temps déjà compté)
                    positionKnown = true
                    startPositionMs = maxOf(0L, positionMs - rawListenedMs(now))
                    maxPositionMs = positionMs
                    positionAdvancedAt = now
                } else if (positionMs > maxPositionMs) {
                    maxPositionMs = positionMs
                    positionAdvancedAt = now
                }
            }
        }
    }

    sealed interface Event {
        /** Une écoute vient de dépasser le seuil : à enregistrer en base (statut CONFIRMED). */
        data class Validated(val session: Session) : Event
        /** Une écoute est terminée (changement de titre / arrêt / pause longue). */
        data class Ended(val session: Session, val listenedMs: Long, val wasValidated: Boolean) : Event
        /** Une nouvelle écoute démarre (affichage "En cours de lecture"). */
        data class Started(val session: Session) : Event
        /** Trou temporel détecté (process gelé / listener déconnecté) : le temps [gapMs] n'a pas été compté. */
        data class Gap(val gapMs: Long, val session: Session) : Event
        /** Rien à faire. */
        data object None : Event
    }

    var current: Session? = null
        private set

    /** Dernier instant où le tracker a eu signe de vie (tick ou événement). */
    private var lastSeenAt: Long = 0

    fun updateThreshold(sec: Int) { thresholdSec = sec }

    /** Appelé quand le player signale un nouveau titre (metadata). */
    fun onTrackChanged(key: TrackKey, isPlaying: Boolean, positionMs: Long? = null, source: String): List<Event> {
        val now = clock()
        val events = mutableListOf<Event>()
        events += syncClock(now)
        val cur = current
        if (cur != null && cur.key == key) {
            // Même titre : détecter le loop (retour en début alors qu'on était avancé)
            val looped = positionMs != null && positionMs < 3_000 && cur.lastPositionMs > 15_000 && (cur.key.durationMs == null || cur.lastPositionMs > cur.key.durationMs / 2)
            if (!looped) { cur.observePosition(positionMs, now); return events }
            events += end(cur, now)
        } else if (cur != null) {
            events += end(cur, now) // crossfade / changement : clôture de A
        }
        val s = Session(
            key = key, startedAt = now, playingSince = if (isPlaying) now else null, pausedAt = if (isPlaying) null else now,
            lastPositionMs = positionMs?.coerceAtLeast(0L) ?: 0L, lastPositionAt = if (positionMs != null) now else 0L,
            detectionSource = source
        )
        s.observePosition(positionMs, now)
        current = s
        events += Event.Started(s)
        return events
    }

    /** Appelé quand l'état lecture/pause change. */
    fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long? = null): List<Event> {
        val now = clock()
        val gap = syncClock(now)
        val cur = current ?: return gap
        cur.observePosition(positionMs, now)
        return gap + if (isPlaying) resume(cur, now) else pause(cur, now)
    }

    /**
     * Appelé périodiquement (tick ~1 s) pour valider le seuil pendant la lecture.
     * [positionMs] = position réelle du lecteur si disponible (extrapolée côté service) : sert de garde-fou.
     */
    fun onTick(positionMs: Long? = null): List<Event> {
        val now = clock()
        val events = mutableListOf<Event>()
        events += syncClock(now)
        val cur = current ?: return events
        if (positionMs != null && positionMs >= 0 && cur.positionKnown) {
            if (cur.playingSince != null && positionMs <= cur.maxPositionMs && now - cur.positionAdvancedAt > ScrobbleRules.POSITION_MARGIN_MS) {
                // Lecteur figé : la position n'avance plus depuis > 15 s alors qu'on compte → pause implicite (événement manqué)
                cur.observePosition(positionMs, now)
                events += pause(cur, maxOf(cur.positionAdvancedAt, cur.playingSince ?: now))
                return events
            }
            if (cur.playingSince == null && positionMs > cur.maxPositionMs) {
                // La position repart alors qu'on était en pause implicite → reprise
                events += resume(cur, now)
            }
        }
        val c2 = current ?: return events
        c2.observePosition(positionMs, now)
        if (c2.playingSince == null || c2.isValidated) return events
        if (ScrobbleRules.isValidated(c2.listenedMs(now), thresholdSec)) {
            c2.validatedAt = now
            events += Event.Validated(c2)
        }
        return events
    }

    /** Le player a disparu (fermé) : clôture propre. */
    fun onPlayerGone(): List<Event> {
        val now = clock()
        val gap = syncClock(now)
        val cur = current ?: return gap
        return gap + end(cur, now)
    }

    /**
     * Anti-gel : compare [now] au dernier signe de vie. Si le trou dépasse [ScrobbleRules.FREEZE_GAP_MS] pendant
     * une lecture, le temps du trou est retiré (on compte jusqu'au dernier signe de vie, puis on repart de maintenant).
     * Un trou ≥ pause longue clôture l'écoute (le lecteur a très probablement enchaîné d'autres titres sans qu'on le voie).
     */
    private fun syncClock(now: Long): List<Event> {
        val last = lastSeenAt
        lastSeenAt = now
        val cur = current ?: return emptyList()
        if (last <= 0L) return emptyList()
        val gap = now - last
        if (gap <= ScrobbleRules.FREEZE_GAP_MS) return emptyList()
        val since = cur.playingSince ?: return emptyList()
        // Compter seulement jusqu'au dernier signe de vie
        cur.accumulatedMs += (last - since).coerceAtLeast(0L)
        val events = mutableListOf<Event>(Event.Gap(gap, cur))
        if (gap >= ScrobbleRules.LONG_PAUSE_MS) {
            cur.playingSince = null
            cur.pausedAt = last
            events += end(cur, last)
        } else {
            cur.playingSince = now
        }
        return events
    }

    private fun resume(cur: Session, now: Long): List<Event> {
        if (cur.playingSince != null) return emptyList()
        val pausedAt = cur.pausedAt
        if (pausedAt != null && now - pausedAt >= ScrobbleRules.LONG_PAUSE_MS) {
            // Pause longue → nouvelle écoute du même titre
            val ended = end(cur, pausedAt)
            val s = Session(key = cur.key, startedAt = now, playingSince = now, detectionSource = cur.detectionSource)
            current = s
            return ended + Event.Started(s)
        }
        cur.playingSince = now
        cur.pausedAt = null
        return emptyList()
    }

    private fun pause(cur: Session, now: Long): List<Event> {
        val since = cur.playingSince ?: return emptyList()
        cur.accumulatedMs += now - since
        cur.playingSince = null
        cur.pausedAt = now
        return emptyList()
    }

    private fun end(cur: Session, now: Long): List<Event> {
        cur.playingSince?.let { cur.accumulatedMs += now - it; cur.playingSince = null }
        val listened = cur.listenedMs(now)
        current = null
        // Validation tardive (tick manqué)
        if (!cur.isValidated && ScrobbleRules.isValidated(listened, thresholdSec)) cur.validatedAt = now
        return listOf(Event.Ended(cur, listened, cur.isValidated))
    }
}
