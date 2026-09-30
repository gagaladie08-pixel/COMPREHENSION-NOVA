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
        val detectionSource: String
    ) {
        fun listenedMs(now: Long): Long = accumulatedMs + (playingSince?.let { now - it } ?: 0L)
        /** Position estimée dans le morceau (dernière position connue + temps écoulé si en lecture). */
        fun estimatedPositionMs(now: Long): Long {
            val p = if (playingSince != null && lastPositionAt > 0) lastPositionMs + (now - lastPositionAt) else lastPositionMs
            return key.durationMs?.let { p.coerceAtMost(it) } ?: p
        }
        val isValidated get() = validatedAt != null
    }

    sealed interface Event {
        /** Une écoute vient de dépasser le seuil : à enregistrer en base (statut CONFIRMED). */
        data class Validated(val session: Session) : Event
        /** Une écoute est terminée (changement de titre / arrêt / pause longue). */
        data class Ended(val session: Session, val listenedMs: Long, val wasValidated: Boolean) : Event
        /** Une nouvelle écoute démarre (affichage "En cours de lecture"). */
        data class Started(val session: Session) : Event
        /** Rien à faire. */
        data object None : Event
    }

    var current: Session? = null
        private set

    fun updateThreshold(sec: Int) { thresholdSec = sec }

    /** Appelé quand le player signale un nouveau titre (metadata). */
    fun onTrackChanged(key: TrackKey, isPlaying: Boolean, positionMs: Long = 0, source: String): List<Event> {
        val now = clock()
        val events = mutableListOf<Event>()
        val cur = current
        if (cur != null && cur.key == key) {
            // Même titre : détecter le loop (retour en début alors qu'on était avancé)
            val looped = positionMs < 3_000 && cur.lastPositionMs > 15_000 && (cur.key.durationMs == null || cur.lastPositionMs > cur.key.durationMs / 2)
            if (!looped) { cur.lastPositionMs = positionMs; cur.lastPositionAt = now; return events }
            events += end(cur, now)
        } else if (cur != null) {
            events += end(cur, now) // crossfade / changement : clôture de A
        }
        val s = Session(key = key, startedAt = now, playingSince = if (isPlaying) now else null, pausedAt = if (isPlaying) null else now, lastPositionMs = positionMs, lastPositionAt = now, detectionSource = source)
        current = s
        events += Event.Started(s)
        return events
    }

    /** Appelé quand l'état lecture/pause change. */
    fun onPlaybackStateChanged(isPlaying: Boolean, positionMs: Long? = null): List<Event> {
        val now = clock()
        val cur = current ?: return emptyList()
        positionMs?.let { cur.lastPositionMs = it; cur.lastPositionAt = now }
        return if (isPlaying) resume(cur, now) else pause(cur, now)
    }

    /** Appelé périodiquement (tick ~1 s) pour valider le seuil pendant la lecture. */
    fun onTick(): List<Event> {
        val now = clock()
        val cur = current ?: return emptyList()
        if (cur.playingSince == null || cur.isValidated) return emptyList()
        return if (ScrobbleRules.isValidated(cur.listenedMs(now), thresholdSec)) {
            cur.validatedAt = now
            listOf(Event.Validated(cur))
        } else emptyList()
    }

    /** Le player a disparu (fermé) : clôture propre. */
    fun onPlayerGone(): List<Event> {
        val cur = current ?: return emptyList()
        return end(cur, clock())
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
        val listened = cur.listenedMs(now)
        cur.playingSince?.let { cur.accumulatedMs += now - it; cur.playingSince = null }
        current = null
        // Validation tardive (tick manqué)
        if (!cur.isValidated && ScrobbleRules.isValidated(listened, thresholdSec)) cur.validatedAt = now
        return listOf(Event.Ended(cur, listened, cur.isValidated))
    }
}
