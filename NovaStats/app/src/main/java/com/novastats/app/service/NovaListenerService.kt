package com.novastats.app.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.entity.NowPlayingEntity
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.data.db.entity.ScrobbleStatus
import com.novastats.app.domain.ReviewRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service de détection NovaStats.
 *
 *  Source principale : MediaSession (via MediaSessionManager — nécessite l'accès NotificationListener).
 *  Fallback          : notifications média (extras du template MediaStyle).
 *  Whitelist         : vide = toutes les apps média sont suivies ; sinon seules les apps listées.
 *  Diagnostic        : tout est journalisé dans [DetectionState] (Réglages → Diagnostic détection).
 */
class NovaListenerService : NotificationListenerService() {

    private val tag = "NovaListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Room writes triggered by separate MediaSession callbacks must never race or reorder. */
    private val persistenceMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracker = ScrobbleTracker()
    private var tickJob: Job? = null
    private var controllers = emptyMap<String, MediaController>()
    private val callbacks = HashMap<String, MediaController.Callback>()
    private var whitelist: Set<String> = emptySet()
    private var blacklistArtists: Set<String> = emptySet()
    private var blacklistKeywords: Set<String> = emptySet()
    private var filterLongTracks = true
    private var activePackage: String? = null

    private val app get() = application as NovaStatsApp
    private lateinit var sessionManager: MediaSessionManager
    private val component by lazy { ComponentName(this, NovaListenerService::class.java) }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> bindControllers(list.orEmpty()) }

    override fun onCreate() {
        super.onCreate()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        DetectionState.bind(this)
        DetectionState.log("Service créé")
        scope.launch {
            launch { app.settings.thresholdSec.collect { tracker.updateThreshold(it); DetectionState.log("Seuil : ${it}s") } }
            launch { app.settings.blacklistArtists.collect { blacklistArtists = it.map { a -> a.trim().lowercase() }.toSet() } }
            launch { app.settings.filterLongTracks.collect { filterLongTracks = it } }
            launch { app.settings.blacklistKeywords.collect { blacklistKeywords = it.map { k -> k.trim().lowercase() }.filter { k -> k.isNotEmpty() }.toSet() } }
            launch {
                app.settings.whitelist.collect {
                    whitelist = it
                    DetectionState.log(if (it.isEmpty()) "Whitelist : toutes les apps" else "Whitelist : ${it.size} app(s)")
                    mainHandler.post { refreshSessions() }
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(tag, "Listener connecté")
        DetectionState.connected(true)
        ServiceHealth.connected(this, true)
        DetectionState.log("Accès aux notifications : connecté")
        // Service premier plan compagnon : empêche le gel du process quand l'app n'est plus à l'écran
        NovaKeepAliveService.start(this)
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component, mainHandler)
            DetectionState.mediaSessionAvailable(true)
            refreshSessions()
        }.onFailure {
            Log.w(tag, "MediaSession indisponible, fallback notifications", it)
            DetectionState.mediaSessionAvailable(false)
            DetectionState.error(it)
            DetectionState.log("MediaSession indisponible → fallback notifications")
        }
        // Rejouer les notifications média déjà affichées
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
        startTicker()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        DetectionState.connected(false)
        ServiceHealth.connected(this, false)
        DetectionState.log("Accès aux notifications : déconnecté → demande de reconnexion")
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        unbindAll()
        tickJob?.cancel()
        // On ne recevra plus d'événements : clôture propre de l'écoute en cours (sinon son chrono continuerait à tourner)
        activePackage = null
        handle(tracker.onPlayerGone())
        requestRebind(component)
    }

    override fun onDestroy() {
        DetectionState.connected(false)
        ServiceHealth.connected(this, false)
        DetectionState.log("Service détruit")
        unbindAll()
        tickJob?.cancel()
        // Arrête d'abord les écritures du scope; la clôture finale est alors persistée sans concurrence.
        scope.cancel()
        tracker.onPlayerGone().let { events ->
            if (events.isNotEmpty()) runCatching {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) { kotlinx.coroutines.withTimeout(3_000) { handleNow(events) } }
            }
        }
        super.onDestroy()
    }

    /* ---------------- MediaSession (source principale) ---------------- */

    private fun refreshSessions() {
        runCatching { bindControllers(sessionManager.getActiveSessions(component)) }
            .onFailure { DetectionState.error(it) }
    }

    private fun isAllowed(pkg: String) = whitelist.isEmpty() || pkg in whitelist

    /** Blacklist artistes (nom exact, insensible à la casse) et mots-clés (contenus dans le titre ou l'artiste : podcast, épisode…). */
    private fun isBlacklisted(title: String, artist: String?): Boolean {
        val a = artist?.trim()?.lowercase()
        if (a != null && a in blacklistArtists) return true
        val hay = (title + " " + (artist ?: "")).lowercase()
        return blacklistKeywords.any { hay.contains(it) }
    }

    private fun bindControllers(list: List<MediaController>) {
        val wanted = list.filter { isAllowed(it.packageName) }.associateBy { it.packageName }
        val ignored = list.map { it.packageName }.filterNot { isAllowed(it) }
        DetectionState.sessions(wanted.keys.toList(), ignored)

        // Désenregistrer ceux disparus
        controllers.forEach { (pkg, c) ->
            if (pkg !in wanted) {
                callbacks.remove(pkg)?.let { runCatching { c.unregisterCallback(it) } }
                DetectionState.log("Session fermée : $pkg")
                if (pkg == activePackage) { activePackage = null; handle(tracker.onPlayerGone()) }
            }
        }
        // Enregistrer les nouveaux
        wanted.forEach { (pkg, c) ->
            if (pkg !in controllers) {
                DetectionState.log("Session détectée : $pkg")
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) { onSession(c, metadata, c.playbackState) }
                    override fun onPlaybackStateChanged(state: PlaybackState?) { onSession(c, c.metadata, state) }
                    override fun onSessionDestroyed() {
                        DetectionState.log("Session détruite : $pkg")
                        if (pkg == activePackage) { activePackage = null; handle(tracker.onPlayerGone()) }
                    }
                }
                c.registerCallback(cb, mainHandler)
                callbacks[pkg] = cb
                onSession(c, c.metadata, c.playbackState)
            }
        }
        controllers = wanted
    }

    private fun unbindAll() {
        controllers.forEach { (pkg, c) -> callbacks.remove(pkg)?.let { runCatching { c.unregisterCallback(it) } } }
        controllers = emptyMap()
    }

    private fun onSession(c: MediaController, metadata: MediaMetadata?, state: PlaybackState?) {
        try {
            if (metadata == null) {
                DetectionState.log("${c.packageName} : pas de métadonnées (état ${stateName(state)})")
                return
            }
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
            if (title == null) {
                DetectionState.log("${c.packageName} : pas de titre dans les métadonnées (état ${stateName(state)})")
                return
            }
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            // Multi-players : premier arrivé prioritaire, switch seulement si l'actif est en pause
            if (activePackage != null && activePackage != c.packageName) {
                val activeState = controllers[activePackage]?.playbackState?.state
                if (activeState == PlaybackState.STATE_PLAYING) return
            }
            if (isPlaying) activePackage = c.packageName

            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
            val tooLong = filterLongTracks && durationMs != null && durationMs > 10 * 60_000L
            if (isBlacklisted(title, artist) || tooLong) {
                if (tracker.current != null) { handle(tracker.onPlayerGone()); DetectionState.log(if (tooLong) "⏭️ > 10 min ignoré (filtre) : $title" else "⛔ Blacklist : $title — ${artist ?: ""}") }
                return
            }
            val key = ScrobbleTracker.TrackKey(
                title = title,
                artist = artist,
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                durationMs = durationMs,
                sourceApp = c.packageName,
                albumArtist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            )
            val position = realPosition(state)
            if (tracker.current?.key != key) {
                DetectionState.lastTrack(key.display)
                DetectionState.log("▶ ${key.display} [${c.packageName}] ${stateName(state)}")
            }
            // onTrackChanged gère aussi le même titre (mise à jour de position + détection de loop)
            val events = tracker.onTrackChanged(key, isPlaying, position, source = "MEDIA_SESSION") +
                tracker.onPlaybackStateChanged(isPlaying, position)
            handle(events)
        } catch (t: Throwable) {
            Log.e(tag, "onSession", t)
            DetectionState.error(t)
        }
    }

    private fun stateName(state: PlaybackState?) = when (state?.state) {
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        null -> "NO_STATE"
        else -> "STATE_${state.state}"
    }

    /* ---------------- Notifications (fallback) ---------------- */

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (controllers.containsKey(sbn.packageName)) return // déjà couvert par MediaSession
            if (!isAllowed(sbn.packageName)) return
            val extras = sbn.notification.extras ?: return
            val template = extras.getString("android.template") ?: ""
            val hasMediaSession = extras.containsKey("android.mediaSession")
            if (!template.contains("MediaStyle") && !hasMediaSession) return
            val title = extras.getCharSequence("android.title")?.toString()?.takeIf { it.isNotBlank() } ?: return
            val artist = extras.getCharSequence("android.text")?.toString()
            if (isBlacklisted(title, artist)) return
            val key = ScrobbleTracker.TrackKey(title, artist, null, null, sbn.packageName)
            if (tracker.current?.key == key) return
            DetectionState.lastTrack(key.display)
            DetectionState.log("▶ ${key.display} [${sbn.packageName}] via notification")
            // Sans état de lecture fiable, on suppose "en lecture" tant que la notification est présente.
            handle(tracker.onTrackChanged(key, isPlaying = true, source = "NOTIFICATION"))
        } catch (t: Throwable) {
            Log.e(tag, "onNotificationPosted", t)
            DetectionState.error(t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (tracker.current?.key?.sourceApp == sbn.packageName && !controllers.containsKey(sbn.packageName)) {
            DetectionState.log("Notification retirée : ${sbn.packageName}")
            handle(tracker.onPlayerGone())
        }
    }

    /* ---------------- Ticker (validation du seuil) ---------------- */

    /**
     * Position réelle du lecteur : position annoncée + temps écoulé depuis sa mise à jour (× vitesse) si en lecture.
     * `lastPositionUpdateTime` est en elapsedRealtime, insensible aux gels du process de NovaStats.
     * null si le lecteur ne fournit pas de position.
     */
    private fun realPosition(state: PlaybackState?): Long? {
        state ?: return null
        val p = state.position
        if (p < 0) return null
        if (state.state != PlaybackState.STATE_PLAYING) return p
        val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1f
        val elapsed = (android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
        return p + (elapsed * speed).toLong()
    }

    /** Position réelle du lecteur actif (pour le garde-fou du ticker). */
    private fun activePosition(): Long? {
        val pkg = tracker.current?.key?.sourceApp ?: return null
        val c = controllers[pkg] ?: return null
        return runCatching { realPosition(c.playbackState) }.getOrNull()
    }

    /**
     * Ticker 1 s — sur le **thread principal**, comme les callbacks MediaSession : le tracker n'est ainsi jamais
     * touché par deux threads à la fois (plus de course entre un tick et un changement de titre).
     */
    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1_000)
                handle(tracker.onTick(positionMs = activePosition()))
                ServiceHealth.heartbeat(this@NovaListenerService)
                val cur = tracker.current
                if (cur != null) {
                    val now = System.currentTimeMillis()
                    // Instantané pris sur le thread principal, persisté hors thread principal
                    val snap = NowPlayingEntity(
                        trackId = null, rawTitle = cur.key.title, rawArtist = cur.key.artist, rawAlbum = cur.key.album, startedAt = cur.startedAt,
                        progressMs = cur.listenedMs(now), positionMs = cur.estimatedPositionMs(now), durationMs = cur.key.durationMs,
                        isPlaying = cur.playingSince != null, sourceApp = cur.key.sourceApp,
                        scrobbleStatus = if (cur.isValidated) "VALIDATED" else "PENDING"
                    )
                    scope.launch(Dispatchers.Main.immediate) { runCatching { persistNowPlaying(snap) }.onFailure { e -> DetectionState.error(e) } }
                }
            }
        }
    }

    /* ---------------- Persistance ---------------- */

    private fun handle(events: List<ScrobbleTracker.Event>) {
        if (events.isEmpty()) return
        // Tous les appels proviennent du thread principal : démarrer ici conserve leur ordre
        // avant que les suspendus Room ne s'entrelacent sur IO.
        scope.launch(Dispatchers.Main.immediate) { handleNow(events) }
    }

    private suspend fun handleNow(events: List<ScrobbleTracker.Event>) = persistenceMutex.withLock {
        for ((eventIndex, e) in events.withIndex()) {
            try {
                when (e) {
                    is ScrobbleTracker.Event.Started -> updateNowPlaying(e.session)
                    is ScrobbleTracker.Event.Validated -> {
                        DetectionState.log("✅ Validé : ${e.session.key.display}")
                        persist(e.session, ended = false)
                    }
                    is ScrobbleTracker.Event.Ended -> {
                        if (e.wasValidated) persist(e.session, ended = true)
                        else DetectionState.log("⏭ Skip avant seuil (${e.listenedMs / 1000}s) : ${e.session.key.display}")
                        // Changement direct A→B : ne publie pas un état vide entre l'ancienne fin et le nouveau départ.
                        val startedLaterInBatch = events.drop(eventIndex + 1).any { it is ScrobbleTracker.Event.Started }
                        val newerSessionAlreadyActive = tracker.current?.let { it !== e.session } == true
                        if (!startedLaterInBatch && !newerSessionAlreadyActive) {
                            app.database.nowPlayingDao().upsert(NowPlayingEntity(scrobbleStatus = "IDLE"))
                        }
                    }
                    is ScrobbleTracker.Event.Gap -> DetectionState.log("🧊 Trou de ${e.gapMs / 1000}s non compté (service gelé / déconnecté) : ${e.session.key.display}")
                    ScrobbleTracker.Event.None -> Unit
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(tag, "handle $e", failure)
                DetectionState.error(failure)
            }
        }
    }

    private suspend fun updateNowPlaying(s: ScrobbleTracker.Session) {
        val now = System.currentTimeMillis()
        writeNowPlaying(
            NowPlayingEntity(
                trackId = null,
                rawTitle = s.key.title, rawArtist = s.key.artist, rawAlbum = s.key.album, startedAt = s.startedAt,
                progressMs = s.listenedMs(now), positionMs = s.estimatedPositionMs(now), durationMs = s.key.durationMs,
                isPlaying = s.playingSince != null, sourceApp = s.key.sourceApp,
                scrobbleStatus = if (s.isValidated) "VALIDATED" else "PENDING"
            )
        )
    }

    private suspend fun persistNowPlaying(snap: NowPlayingEntity) = persistenceMutex.withLock {
        writeNowPlaying(snap)
    }

    /** Caller owns [persistenceMutex], except the final onDestroy drain which runs after scope cancellation. */
    private suspend fun writeNowPlaying(snap: NowPlayingEntity) {
        val trackId = if (snap.scrobbleStatus == "VALIDATED") app.library.peekTrackId(snap.rawTitle ?: return, snap.rawArtist ?: "Artiste inconnu") else null
        app.database.nowPlayingDao().upsert(snap.copy(trackId = trackId))
    }

    /**
     * Enregistre / met à jour l'écoute en base. La clé d'unicité (track_id + started_at) évite les doublons
     * entre l'événement Validated et l'événement Ended.
     */
    private suspend fun persist(s: ScrobbleTracker.Session, ended: Boolean) {
        val artistRaw = s.key.artist ?: "Artiste inconnu"
        val resolved = app.library.resolve(s.key.title, artistRaw, s.key.album, s.key.durationMs, albumArtist = s.key.albumArtist)
        val now = System.currentTimeMillis()
        val listened = s.listenedMs(now)
        // ⚠️ À corriger — score par source : MediaSession 100 · Notification complète 70 · incomplète 50 ;
        // titre / artiste « Unknown » → 50 max. Score < 70 → needs_review (sauf valeur déjà confirmée par l'utilisateur).
        val review = ReviewRules.evaluate(s.key.title, s.key.artist, s.detectionSource)
        val confirmed = review.reason != null && app.library.isConfirmed("TITLE", s.key.title) && (s.key.artist.isNullOrBlank() || app.library.isConfirmed("ARTIST", s.key.artist))
        val entity = ScrobbleEntity(
            trackId = resolved.trackId, artistId = resolved.primaryArtistId, albumId = resolved.albumId,
            startedAt = s.startedAt, validatedAt = s.validatedAt, endedAt = if (ended) now else null,
            durationListenedMs = listened, sourceApp = s.key.sourceApp, detectionSource = s.detectionSource,
            confidenceScore = if (confirmed) 100 else review.score,
            status = ScrobbleStatus.CONFIRMED,
            needsReview = review.score < 70 && !confirmed, reviewReason = if (confirmed) null else review.reason,
            rawTitle = s.key.title, rawArtist = s.key.artist, rawAlbum = s.key.album
        )
        val id = app.database.scrobbleDao().insert(entity)
        if (id == -1L) {
            // Déjà inséré à la validation → mise à jour de la durée / fin
            app.database.scrobbleDao().findByTrackAndStart(resolved.trackId, s.startedAt)?.let {
                app.database.scrobbleDao().update(it.copy(durationListenedMs = listened, endedAt = if (ended) now else it.endedAt))
            }
        }
        if (ended) {
            DetectionState.log("💾 Enregistré (${listened / 1000}s) : ${s.key.display}")
            // 🎉 Micro-événement : toute première écoute → mission « Premier Scrobble » accomplie
            if (app.database.scrobbleDao().countConfirmed() == 1) {
                runCatching { AchievementNotifier.firstScrobble(this, s.key.display) }
                runCatching { NovaAwardsUnlockWorker.schedule(this@NovaListenerService, s.startedAt) }
            }
            scheduleRebuild()
        }
    }

    private var rebuildJob: Job? = null

    /**
     * Recalcul des stats **regroupé** : plusieurs écoutes qui se terminent à quelques secondes d'intervalle
     * (fin de morceau + dégel, rafale d'événements…) ne déclenchent qu'un seul recalcul, 5 s après la dernière.
     * Le recalcul complet par écoute était la principale charge CPU du service en arrière-plan.
     */
    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        val application = app
        val context = applicationContext
        // Le recalcul survit à la destruction/recréation du service : sinon la clôture finale pouvait écrire
        // l'écoute puis annuler le seul recalcul avec scope.cancel(), laissant l'interface dérivée périmée.
        rebuildJob = application.launchIoTask {
            delay(5_000)
            try {
                val news = application.rebuilder.rebuildAll(fullBillboard = false)
                com.novastats.app.util.runCatchingCancellable { AchievementNotifier.notify(context, news) }
                    .onFailure { DetectionState.error(it) }
                EnrichmentWorker.enqueue(context)
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(tag, "rebuild", t); DetectionState.error(t)
            }
        }
    }

    companion object {
        /** L'utilisateur a-t-il accordé l'accès aux notifications à NovaStats ? */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        }
    }
}
