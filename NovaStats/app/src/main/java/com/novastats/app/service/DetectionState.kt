package com.novastats.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * État observable du service de détection — alimente l'indicateur 🟢/🔴 et le panneau
 * « Diagnostic détection » des Réglages. Vit dans le process de l'app (le service tourne dans le même process).
 */
object DetectionState {

    data class Snapshot(
        /** onListenerConnected reçu et pas encore déconnecté */
        val listenerConnected: Boolean = false,
        /** MediaSessionManager utilisable (permission OK) */
        val mediaSessionAvailable: Boolean = false,
        /** Packages des sessions média actives vues par le service */
        val activeSessions: List<String> = emptyList(),
        /** Packages ignorés à cause de la whitelist */
        val ignoredSessions: List<String> = emptyList(),
        /** Dernier titre capté */
        val lastTrack: String? = null,
        /** Journal des derniers événements (le plus récent en premier) */
        val log: List<String> = emptyList(),
        val lastError: String? = null
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)
    private var appContext: android.content.Context? = null

    /** À appeler au démarrage de l'app / du service : recharge le journal persisté (survit à la mort du process). */
    fun bind(context: android.content.Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val persisted = ServiceHealth.persistedLog(context)
        ServiceHealth.load(context)
        if (persisted.isNotEmpty()) _state.update { it.copy(log = (it.log + persisted).distinct().take(60)) }
    }

    fun connected(value: Boolean) = _state.update { it.copy(listenerConnected = value) }
    fun mediaSessionAvailable(value: Boolean) = _state.update { it.copy(mediaSessionAvailable = value) }
    fun sessions(active: List<String>, ignored: List<String>) = _state.update { it.copy(activeSessions = active, ignoredSessions = ignored) }
    fun lastTrack(value: String) = _state.update { it.copy(lastTrack = value) }
    fun error(t: Throwable) = _state.update { it.copy(lastError = "${t.javaClass.simpleName}: ${t.message}") }

    fun log(message: String) {
        val line = "${fmt.format(Date())}  $message"
        _state.update { it.copy(log = (listOf(line) + it.log).take(60)) }
        appContext?.let { runCatching { ServiceHealth.appendLog(it, line) } }
    }
}
