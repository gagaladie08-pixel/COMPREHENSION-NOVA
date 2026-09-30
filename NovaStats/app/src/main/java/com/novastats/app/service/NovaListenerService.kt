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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracker = ScrobbleTracker()
    private var tickJob: Job? = null
    private var controllers = emptyMap<String, MediaController>()
    private val callbacks = HashMap<String, MediaController.Callback>()
    private var whitelist: Set<String> = emptySet()
    private var activePackage: String? = null

    private val app get() = application as NovaStatsApp
    private lateinit var sessionManager: MediaSessionManager
    private val component by lazy { ComponentName(this, NovaListenerService::class.java) }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> bindControllers(list.orEmpty()) }

    override fun onCreate() {
        super.onCreate()
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        DetectionState.log("Service créé")
        scope.launch {
            launch { app.settings.thresholdSec.collect { tracker.updateThreshold(it); DetectionState.log("Seuil : ${it}s") } }
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
        DetectionState.log("Accès aux notifications : connecté")
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
        DetectionState.log("Accès aux notifications : déconnecté → demande de reconnexion")
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        unbindAll()
        tickJob?.cancel()
        requestRebind(component)
    }

    override fun onDestroy() {
        DetectionState.connected(false)
        DetectionState.log("Service détruit")
        unbindAll()
        scope.cancel()
        super.onDestroy()
    }

    /* ---------------- MediaSession (source principale) ---------------- */

    private fun refreshSessions() {
        runCatching { bindControllers(sessionManager.getActiveSessions(component)) }
            .onFailure { DetectionState.error(it) }
    }

    private fun isAllowed(pkg: String) = whitelist.isEmpty() || pkg in whitelist

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
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
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
            val key = ScrobbleTracker.TrackKey(
                title = title,
                artist = artist,
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
                sourceApp = c.packageName
            )
            val position = state?.position ?: 0L
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

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                handle(tracker.onTick())
                tracker.current?.let { runCatching { updateNowPlaying(it) }.onFailure { e -> DetectionState.error(e) } }
            }
        }
    }

    /* ---------------- Persistance ---------------- */

    private fun handle(events: List<ScrobbleTracker.Event>) {
        if (events.isEmpty()) return
        scope.launch {
            for (e in events) {
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
                            app.database.nowPlayingDao().upsert(NowPlayingEntity(scrobbleStatus = "IDLE"))
                        }
                        ScrobbleTracker.Event.None -> Unit
                    }
                } catch (t: Throwable) {
                    Log.e(tag, "handle $e", t)
                    DetectionState.error(t)
                }
            }
        }
    }

    private suspend fun updateNowPlaying(s: ScrobbleTracker.Session) {
        app.database.nowPlayingDao().upsert(
            NowPlayingEntity(
                rawTitle = s.key.title, rawArtist = s.key.artist, startedAt = s.startedAt,
                progressMs = s.listenedMs(System.currentTimeMillis()), sourceApp = s.key.sourceApp,
                scrobbleStatus = if (s.isValidated) "VALIDATED" else "PENDING"
            )
        )
    }

    /**
     * Enregistre / met à jour l'écoute en base. La clé d'unicité (track_id + started_at) évite les doublons
     * entre l'événement Validated et l'événement Ended.
     */
    private suspend fun persist(s: ScrobbleTracker.Session, ended: Boolean) {
        val artistRaw = s.key.artist ?: "Artiste inconnu"
        val resolved = app.library.resolve(s.key.title, artistRaw, s.key.album, s.key.durationMs)
        val now = System.currentTimeMillis()
        val listened = s.listenedMs(now)
        val entity = ScrobbleEntity(
            trackId = resolved.trackId, artistId = resolved.primaryArtistId, albumId = resolved.albumId,
            startedAt = s.startedAt, validatedAt = s.validatedAt, endedAt = if (ended) now else null,
            durationListenedMs = listened, sourceApp = s.key.sourceApp, detectionSource = s.detectionSource,
            confidenceScore = if (s.detectionSource == "MEDIA_SESSION") 100 else 70,
            status = ScrobbleStatus.CONFIRMED
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
            // TODO(perf) : remplacer par une mise à jour incrémentale (titre/artiste/album + jour courant)
            // et un check de certification / Panthéon ciblé. Le rebuild complet est correct mais coûteux.
            app.rebuilder.rebuildAll()
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
