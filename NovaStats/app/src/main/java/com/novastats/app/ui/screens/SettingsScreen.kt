package com.novastats.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontFamily
import com.novastats.app.service.DetectionState
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.importer.LegacyBackupImporter
import com.novastats.app.domain.ScrobbleRules
import com.novastats.app.service.NovaListenerService
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ⚙️ Paramètres — sections : Détection · Apparence (15 thèmes) · Données (import JSON) · Service · À propos.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val theme = Nova.theme

    val themeId by settings.themeId.collectAsStateWithLifecycle(initialValue = NovaThemes.DEFAULT.id)
    val threshold by settings.thresholdSec.collectAsStateWithLifecycle(initialValue = ScrobbleRules.DEFAULT_THRESHOLD_SEC)
    val filterLong by settings.filterLongTracks.collectAsStateWithLifecycle(initialValue = true)
    val trackMuted by settings.trackWhenMuted.collectAsStateWithLifecycle(initialValue = false)
    val watchdog by settings.watchdogEnabled.collectAsStateWithLifecycle(initialValue = true)
    val scrobbles by app.database.scrobbleDao().countConfirmedFlow().collectAsStateWithLifecycle(initialValue = 0)
    val tracks by app.database.trackDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val artists by app.database.artistDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val certs by app.database.certificationDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)

    var importing by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        importStatus = "Lecture du fichier…"
        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    val backup = context.contentResolver.openInputStream(uri)!!.use { LegacyBackupImporter.parse(it) }
                    LegacyBackupImporter.import(app.database, backup, threshold) { importStatus = it }
                }
                importStatus = report.summary()
            } catch (e: Exception) {
                importStatus = "❌ Import échoué : ${e.message}"
            } finally {
                importing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

        /* ---------- 🎵 Détection ---------- */
        SectionTitle("🎵 Détection")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("Seuil de scrobble", color = theme.text, fontWeight = FontWeight.SemiBold)
                Text("Durée minimale pour compter une écoute (hot-reload)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScrobbleRules.ALLOWED_THRESHOLDS_SEC.forEach { s ->
                        FilterChip(selected = threshold == s, onClick = { scope.launch { settings.setThreshold(s) } }, label = { Text("${s}s") })
                    }
                }
                Spacer(Modifier.height(12.dp))
                ToggleRow("Filtre titres > 10 min", "Confirmation à la première détection (podcasts)", filterLong) { scope.launch { settings.setFilterLongTracks(it) } }
                ToggleRow("Tracking volume = 0 %", "Continuer à compter quand le son est coupé", trackMuted) { scope.launch { settings.setTrackWhenMuted(it) } }
            }
        }

        /* ---------- 🎨 Apparence ---------- */
        SectionTitle("🎨 Apparence — 15 thèmes")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                NovaThemes.ALL.chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { t ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(60.dp).clickable { scope.launch { settings.setTheme(t.id) } }.padding(vertical = 6.dp)
                            ) {
                                val selected = t.id == themeId
                                Column(
                                    Modifier.size(40.dp).background(t.background, CircleShape)
                                        .border(if (selected) 3.dp else 1.dp, if (selected) theme.accent else t.textSecondary.copy(alpha = 0.4f), CircleShape),
                                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row { Dot(t.primary); Dot(t.secondary); Dot(t.accent) }
                                }
                                Text(t.emoji, style = MaterialTheme.typography.bodySmall)
                                Text(t.name, style = MaterialTheme.typography.labelSmall, color = if (selected) theme.primary else theme.textSecondary, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        /* ---------- 🗄️ Données ---------- */
        SectionTitle("🗄️ Données")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatPill(formatCount(scrobbles), "écoutes")
                    StatPill(formatCount(tracks), "titres", accent = theme.secondary)
                    StatPill(formatCount(artists), "artistes", accent = theme.accent)
                    StatPill(formatCount(certs), "certifs", accent = theme.glowSecondary)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { picker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) { Text(if (importing) "Import en cours…" else "📥 Importer un backup JSON (v1 / NovaStats)") }
                if (importing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = theme.accent)
                }
                importStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scope.launch { importStatus = "Recalcul…"; withContext(Dispatchers.IO) { app.rebuilder.rebuildAll { importStatus = it } }; importStatus = "✅ Statistiques recalculées" } },
                    enabled = !importing && scrobbles > 0,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("🔄 Recalculer toutes les statistiques") }
            }
        }

        /* ---------- 🛡️ Service ---------- */
        SectionTitle("🛡️ Service")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                // Statut recalculé à chaque retour dans l'app (après l'écran Android d'accès aux notifications)
                var accessGranted by remember { mutableStateOf(NovaListenerService.isEnabled(context)) }
                LifecycleResumeEffect(Unit) {
                    accessGranted = NovaListenerService.isEnabled(context)
                    onPauseOrDispose { }
                }
                val detection by DetectionState.state.collectAsStateWithLifecycle()
                val running = accessGranted && detection.listenerConnected

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (running) "🟢" else if (accessGranted) "🟠" else "🔴")
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                running -> "Service actif"
                                accessGranted -> "Accès accordé, service pas encore connecté"
                                else -> "Service inactif"
                            },
                            color = theme.text, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when {
                                running -> "MediaSession : ${if (detection.mediaSessionAvailable) "OK" else "indisponible (fallback notifications)"}"
                                accessGranted -> "Relance l'app ou désactive/réactive l'accès aux notifications"
                                else -> "Accès aux notifications requis pour détecter la musique"
                            },
                            color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (accessGranted) theme.surface else theme.primary, contentColor = if (accessGranted) theme.text else MaterialTheme.colorScheme.onPrimary)
                ) { Text(if (accessGranted) "Gérer l'accès aux notifications" else "Activer la détection") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("🔋 Désactiver l'optimisation batterie") }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Watchdog", "Vérification toutes les 5 min + relance auto", watchdog) { scope.launch { settings.setWatchdog(it) } }
            }
        }

        /* ---------- 🔎 Diagnostic détection ---------- */
        SectionTitle("🔎 Diagnostic détection")
        NovaCard {
            val detection by DetectionState.state.collectAsStateWithLifecycle()
            Column(Modifier.padding(16.dp)) {
                DiagLine("Listener connecté", if (detection.listenerConnected) "oui" else "non")
                DiagLine("MediaSession", if (detection.mediaSessionAvailable) "disponible" else "indisponible")
                DiagLine("Sessions média actives", detection.activeSessions.ifEmpty { listOf("aucune") }.joinToString(", "))
                if (detection.ignoredSessions.isNotEmpty()) DiagLine("Ignorées (whitelist)", detection.ignoredSessions.joinToString(", "))
                DiagLine("Dernier titre capté", detection.lastTrack ?: "—")
                detection.lastError?.let { DiagLine("Dernière erreur", it, error = true) }
                Spacer(Modifier.height(8.dp))
                Text("Journal", color = theme.text, fontWeight = FontWeight.SemiBold)
                if (detection.log.isEmpty()) {
                    Text(
                        "Vide : le service n'a pas démarré. Vérifie l'accès aux notifications puis relance l'app. " +
                            "Si le journal reste vide, ton téléphone (Xiaomi/Huawei/Oppo/Tecno…) bloque peut-être le service : " +
                            "autorise le démarrage automatique de NovaStats dans ses paramètres.",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
                detection.log.forEach {
                    Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }

        /* ---------- ℹ️ À propos ---------- */
        SectionTitle("ℹ️ À propos")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("NovaStats 0.1.0", color = theme.text, fontWeight = FontWeight.Bold)
                Text("100 % local · aucune donnée envoyée · export JSON", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DiagLine(label: String, value: String, error: Boolean = false) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(150.dp))
        Text(value, color = if (error) Color(0xFFE74C3C) else theme.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Dot(color: Color) {
    Column(Modifier.padding(1.dp).size(7.dp).background(color, CircleShape)) {}
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.text)
            Text(subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
