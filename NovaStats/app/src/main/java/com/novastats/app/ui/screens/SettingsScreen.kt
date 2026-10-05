package com.novastats.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.BuildConfig
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.api.EnrichmentState
import com.novastats.app.data.importer.BackupExporter
import com.novastats.app.data.importer.LegacyBackupImporter
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.domain.ScrobbleRules
import com.novastats.app.service.BackupWorker
import com.novastats.app.service.DetectionState
import com.novastats.app.service.EnrichmentWorker
import com.novastats.app.service.NovaListenerService
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Sous-pages de ⚙️ Paramètres. */
enum class SettingsPage(val emoji: String, val title: String, val subtitle: String) {
    DETECTION("🎵", "Détection", "Seuil, apps sources, blacklist, filtres"),
    APPEARANCE("🎨", "Apparence", "15 thèmes néon · taille du texte · retour haptique"),
    NOTIFICATIONS("🔔", "Notifications", "Certifications, Panthéon, Hall of Fame"),
    DATA("🗄️", "Données", "Export / import JSON, sauvegarde auto, suppression"),
    EDITOR("🛠️", "Éditeur de données", "Renommer, fusionner, corriger, annuler"),
    SERVICE("🛡️", "Service & diagnostic", "État du service, batterie, journal"),
    GUIDE("📱", "Guide constructeur", "Samsung, Xiaomi, Huawei, Oppo, Pixel — libérer NovaStats"),
    APIS("🌐", "APIs & enrichissement", "Pochettes, photos — 9 sources"),
    ABOUT("ℹ️", "À propos", "Version, nouveautés, crédits")
}

/**
 * ⚙️ Paramètres — écran d'accueil (résumé + liste) et sous-pages. Retour Android ferme la sous-page.
 */
@Composable
fun SettingsScreen() {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var editorReview by rememberSaveable { mutableStateOf(false) }
    var editorFocus by rememberSaveable { mutableStateOf<Long?>(null) }
    // Navigation différée (notification 🟡 À vérifier / raccourci) → Éditeur, section ⚠️ À corriger
    val pending by com.novastats.app.ui.navigation.PendingNav.target.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val t = pending ?: return@LaunchedEffect
        if (t.name == com.novastats.app.ui.navigation.PendingNav.TARGET_REVIEW) { editorReview = true; editorFocus = t.trackId; page = SettingsPage.EDITOR }
        com.novastats.app.ui.navigation.PendingNav.consume()
    }
    BackHandler(enabled = page != null) { page = null; editorReview = false; editorFocus = null }
    val current = page
    if (current == null) SettingsHome { page = it }
    else Column(Modifier.fillMaxSize()) {
        SubPageHeader(current) { page = null }
        when (current) {
            SettingsPage.DETECTION -> DetectionPage(onOpenReview = { editorReview = true; editorFocus = null; page = SettingsPage.EDITOR })
            SettingsPage.APPEARANCE -> AppearancePage()
            SettingsPage.NOTIFICATIONS -> NotificationsPage()
            SettingsPage.DATA -> DataPage()
            SettingsPage.EDITOR -> DataEditorScreen(startOnReview = editorReview, focusTrackId = editorFocus)
            SettingsPage.SERVICE -> ServicePage()
            SettingsPage.GUIDE -> com.novastats.app.ui.onboarding.GuideStepScreen(com.novastats.app.ui.onboarding.rememberObAudio(), Nova.theme, embedded = true)
            SettingsPage.APIS -> ApisPage()
            SettingsPage.ABOUT -> AboutPage()
        }
    }
}

@Composable
private fun SubPageHeader(page: SettingsPage, onBack: () -> Unit) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(theme.primary.copy(alpha = 0.25f), Color.Transparent))).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹ Paramètres", color = theme.primary, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
        Text("${page.emoji} ${page.title}", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
    }
}

/* ================================ ACCUEIL ================================ */

@Composable
private fun SettingsHome(onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val theme = Nova.theme
    val scrobbles by app.database.scrobbleDao().countConfirmedFlow().collectAsStateWithLifecycle(initialValue = 0)
    val tracks by app.database.trackDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val artists by app.database.artistDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val certs by app.database.certificationDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val firstAt by app.database.scrobbleDao().firstScrobbleAt().collectAsStateWithLifecycle(initialValue = null)
    val lastBackup by app.settings.lastBackupAt.collectAsStateWithLifecycle(initialValue = null)
    var accessGranted by remember { mutableStateOf(NovaListenerService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) { accessGranted = NovaListenerService.isEnabled(context); onPauseOrDispose { } }
    val detection by DetectionState.state.collectAsStateWithLifecycle()
    val running = accessGranted && detection.listenerConnected

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("⚙️ Paramètres", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
            Text("NovaStats ${BuildConfig.VERSION_NAME} · 100 % local", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (running) "🟢" else if (accessGranted) "🟠" else "🔴")
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when { running -> "Détection active"; accessGranted -> "Service pas encore connecté"; else -> "Détection inactive — appuie sur Service" },
                        color = theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatPill(formatCount(scrobbles), "écoutes")
                    StatPill(formatCount(tracks), "titres", accent = theme.secondary)
                    StatPill(formatCount(artists), "artistes", accent = theme.accent)
                    StatPill(formatCount(certs), "certifs", accent = theme.glowSecondary)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Depuis ${formatDate(firstAt)} · base ${dbSizeLabel(context)} · dernière sauvegarde ${lastBackup?.let { formatDate(it) } ?: "jamais"}",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsPage.entries.forEach { p -> NavRow(p) { onOpen(p) } }
    }
}

@Composable
private fun NavRow(p: SettingsPage, onClick: () -> Unit) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(theme.surface)
            .border(1.dp, theme.primary.copy(alpha = 0.18f), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(theme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text(p.emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, color = theme.text, fontWeight = FontWeight.SemiBold)
            Text(p.subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Text("›", color = theme.primary, fontSize = 24.sp)
    }
}

private fun dbSizeLabel(context: android.content.Context): String {
    val f = context.getDatabasePath("novastats.db")
    val bytes = f.length() + File(f.path + "-wal").length()
    return when {
        bytes < 1024 * 1024 -> "${bytes / 1024} Ko"
        else -> String.format(Locale.FRANCE, "%.1f Mo", bytes / 1024.0 / 1024.0)
    }
}

/* ================================ DÉTECTION ================================ */

private val KNOWN_PLAYERS = linkedMapOf(
    "com.spotify.music" to "Spotify", "com.google.android.apps.youtube.music" to "YouTube Music", "com.google.android.youtube" to "YouTube",
    "deezer.android.app" to "Deezer", "com.apple.android.music" to "Apple Music", "com.soundcloud.android" to "SoundCloud",
    "com.sec.android.app.music" to "Samsung Music", "com.amazon.mp3" to "Amazon Music", "com.aspiro.tidal" to "TIDAL",
    "com.maxmpz.audioplayer" to "Poweramp", "com.shazam.android" to "Shazam", "com.boomplay.app" to "Boomplay", "com.audiomack" to "Audiomack"
)

@Composable
private fun DetectionPage(onOpenReview: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val theme = Nova.theme
    val threshold by settings.thresholdSec.collectAsStateWithLifecycle(initialValue = ScrobbleRules.DEFAULT_THRESHOLD_SEC)
    val filterLong by settings.filterLongTracks.collectAsStateWithLifecycle(initialValue = true)
    val trackMuted by settings.trackWhenMuted.collectAsStateWithLifecycle(initialValue = false)
    val whitelist by settings.whitelist.collectAsStateWithLifecycle(initialValue = emptySet())
    val blArtists by settings.blacklistArtists.collectAsStateWithLifecycle(initialValue = emptySet())
    val blKeywords by settings.blacklistKeywords.collectAsStateWithLifecycle(initialValue = emptySet())
    val sources by app.database.scrobbleDao().distinctSources().collectAsStateWithLifecycle(initialValue = emptyList())
    val detection by DetectionState.state.collectAsStateWithLifecycle()
    val reviewCount by app.database.scrobbleDao().reviewCountFlow().collectAsStateWithLifecycle(initialValue = 0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("⚠️ Section à corriger")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                .background((if (reviewCount > 0) com.novastats.app.ui.screens.ReviewRed else theme.textSecondary).copy(alpha = 0.12f))
                .clickable(onClick = onOpenReview).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (reviewCount > 0) "🔴 $reviewCount titre${if (reviewCount > 1) "s" else ""} à corriger" else "✅ Rien à corriger", color = theme.text, fontWeight = FontWeight.SemiBold)
                Text("Écoutes douteuses ou incomplètes (score < 70, artiste manquant…) — comptées, mais à vérifier.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("Ouvrir ▸", color = theme.primary, fontWeight = FontWeight.Bold)
        }
        SectionTitle("⏱️ Seuil de scrobble")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("Durée minimale d'écoute pour compter une lecture (appliqué immédiatement).", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScrobbleRules.ALLOWED_THRESHOLDS_SEC.forEach { s ->
                        NovaFilterChip(flagKey = s, selected = threshold == s, onClick = { scope.launch { settings.setThreshold(s) } }, label = { Text("${s}s") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Filtre titres > 10 min", "Ignore podcasts / mixes longs à la première détection", filterLong) { scope.launch { settings.setFilterLongTracks(it) } }
                ToggleRow("Tracking volume = 0 %", "Continuer à compter quand le son est coupé", trackMuted) { scope.launch { settings.setTrackWhenMuted(it) } }
            }
        }

        SectionTitle("📱 Applications sources")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (whitelist.isEmpty()) "Toutes les applications sont écoutées. Coche des apps pour n'écouter qu'elles."
                    else "${whitelist.size} app(s) autorisée(s) — les autres sont ignorées.",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                val candidates = (KNOWN_PLAYERS.keys + sources + detection.activeSessions + detection.ignoredSessions + whitelist).distinct()
                candidates.forEach { pkg ->
                    val label = KNOWN_PLAYERS[pkg] ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    val seen = pkg in sources || pkg in detection.activeSessions
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = theme.text, style = MaterialTheme.typography.bodyMedium)
                            Text(pkg + if (seen) " · détectée" else "", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = pkg in whitelist, onCheckedChange = { on -> scope.launch { settings.setWhitelist(if (on) whitelist + pkg else whitelist - pkg) } })
                    }
                }
            }
        }

        SectionTitle("⛔ Blacklist")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                ChipEditor("Artistes ignorés", "Nom exact d'un artiste (ex. un podcast)", blArtists) { scope.launch { settings.setBlacklistArtists(it) } }
                Spacer(Modifier.height(12.dp))
                ChipEditor("Mots-clés ignorés", "Si le titre ou l'artiste contient ce mot (podcast, épisode…)", blKeywords) { scope.launch { settings.setBlacklistKeywords(it) } }
            }
        }
    }
}

@Composable
private fun ChipEditor(title: String, hint: String, values: Set<String>, onChange: (Set<String>) -> Unit) {
    val theme = Nova.theme
    var input by remember { mutableStateOf("") }
    Text(title, color = theme.text, fontWeight = FontWeight.SemiBold)
    Text(hint, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("Ajouter…") })
        TextButton(onClick = { val v = input.trim(); if (v.isNotEmpty()) { onChange(values + v); input = "" } }) { Text("＋", color = theme.primary, fontSize = 22.sp) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column {
            values.sorted().chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { v -> FilterChip(selected = true, onClick = { onChange(values - v) }, label = { Text("$v ✕") }) }
                }
            }
        }
    }
    if (values.isEmpty()) Text("— aucun —", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
}

/* ================================ APPARENCE ================================ */

@Composable
private fun AppearancePage() {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val theme = Nova.theme
    val themeId by settings.themeId.collectAsStateWithLifecycle(initialValue = NovaThemes.DEFAULT.id)
    val haptics by settings.haptics.collectAsStateWithLifecycle(initialValue = true)
    val dynamicIcon by settings.dynamicIcon.collectAsStateWithLifecycle(initialValue = true)

    val fontPct by settings.fontScalePct.collectAsStateWithLifecycle(initialValue = 100)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        // 🔠 Taille du texte : − / + par pas de 10 %, aperçu immédiat (toute l'app suit)
        SectionTitle("🔠 Taille du texte")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Police de l'app", color = theme.text, fontWeight = FontWeight.SemiBold)
                        Text(if (fontPct == 100) "Taille normale" else "$fontPct % de la taille normale", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    @Composable
                    fun Step(label: String, enabled: Boolean, onClick: () -> Unit) {
                        Text(
                            label, color = if (enabled) theme.background else theme.textSecondary, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(if (enabled) theme.primary else theme.surface)
                                .clickable(enabled = enabled, onClick = onClick).wrapContentHeight()
                        )
                    }
                    Step("−", fontPct > SettingsRepository.FONT_MIN) { scope.launch { settings.setFontScalePct(fontPct - SettingsRepository.FONT_STEP) } }
                    Text("$fontPct %", color = theme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                    Step("+", fontPct < SettingsRepository.FONT_MAX) { scope.launch { settings.setFontScalePct(fontPct + SettingsRepository.FONT_STEP) } }
                }
                Text("Aperçu : Les classements, popups et réglages s'adaptent immédiatement.", color = theme.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                if (fontPct != 100) Text("↺ Revenir à 100 %", color = theme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp).clickable { scope.launch { settings.setFontScalePct(100) } })
            }
        }
        SectionTitle("🎨 Thèmes — ${NovaThemes.ALL.size}")
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
                                ) { Row { Dot(t.primary); Dot(t.secondary); Dot(t.accent) } }
                                Text(t.emoji, style = MaterialTheme.typography.bodySmall)
                                Text(t.name, style = MaterialTheme.typography.labelSmall, color = if (selected) theme.primary else theme.textSecondary, maxLines = 2, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        val current = NovaThemes.ALL.firstOrNull { it.id == themeId } ?: NovaThemes.DEFAULT
        SectionTitle("${current.emoji} ${current.name}")
        NovaCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(current.inspiration, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text("🔤 Titres : ${current.titleFont} · Corps : ${current.bodyFont}", color = theme.text, style = MaterialTheme.typography.bodyMedium)
                Text("✨ Effet signature : ${current.effects}", color = theme.text, style = MaterialTheme.typography.bodyMedium)
                Text("🎞️ Transition : ${current.transitionLabel}", color = theme.text, style = MaterialTheme.typography.bodyMedium)
                Text("🧩 Icônes : ${current.iconsDescription}", color = theme.text, style = MaterialTheme.typography.bodyMedium)
                val fontsOnline = remember { com.novastats.app.ui.theme.NovaFonts.isProviderAvailable(app) }
                if (!fontsOnline) Text("⚠️ Google Play Services Fonts indisponible : polices système de remplacement.", color = theme.accent, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Chanson" to current.primary, "Album" to current.secondary, "Artiste" to current.glowSecondary, "Accent" to current.accent).forEach { (label, c) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(22.dp).background(c, CircleShape).border(1.dp, theme.textSecondary.copy(alpha = 0.3f), CircleShape))
                            Text(label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        SectionTitle("✨ Confort")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                ToggleRow("Retour haptique", "Vibration légère sur les actions importantes", haptics) { scope.launch { settings.setHaptics(it) } }
                ToggleRow("Icône dynamique", "L'icône de l'app suit le thème. Le changement se fait quand tu quittes l'app (certains téléphones relancent alors NovaStats au retour).", dynamicIcon) { scope.launch { settings.setDynamicIcon(it) } }
            }
        }
        SectionTitle("🎬 Introduction")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("Revoir l'introduction", color = theme.text, fontWeight = FontWeight.SemiBold)
                Text("Rejoue l'onboarding complet (accueil, thèmes, permissions, guide). Tes données et réglages sont conservés.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { scope.launch { settings.replayOnboarding() } }, modifier = Modifier.fillMaxWidth()) { Text("Revoir l'introduction", color = theme.primary) }
            }
        }
    }
}

/* ================================ NOTIFICATIONS ================================ */

@Composable
private fun NotificationsPage() {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val theme = Nova.theme
    val disabled by settings.disabledNotifications.collectAsStateWithLifecycle(initialValue = emptySet())
    val feed by app.database.notificationFeedDao().recent(10).collectAsStateWithLifecycle(initialValue = emptyList())

    val groups = listOf(
        "🏆 Certifications" to listOf(
            SettingsRepository.Notif.CERT_SILVER to "🥉 Argent (25 écoutes · album 50) — son léger", SettingsRepository.Notif.CERT_GOLD to "🥈 Or (50 · 100) — son léger",
            SettingsRepository.Notif.CERT_PLATINUM to "🥇 Platine (100 · 200) — son intermédiaire", SettingsRepository.Notif.CERT_DIAMOND to "💎 Diamant (350 · 700) — son épique + vibration",
            SettingsRepository.Notif.CERT_MULTIPLIERS to "✖️ Multi-Diamant (2x, 3x…) — son épique + vibration"
        ),
        "👑 Panthéon" to listOf(
            SettingsRepository.Notif.P_STAR to "⭐ Star", SettingsRepository.Notif.P_SUPERSTAR to "🌟 Superstar", SettingsRepository.Notif.P_MEGASTAR to "💫 Megastar",
            SettingsRepository.Notif.P_LEGENDE to "👑 Légende", SettingsRepository.Notif.P_MYTHIQUE to "🔱 Mythique"
        ),
        "🏛️ Hall of Fame" to listOf(SettingsRepository.Notif.HOF to "Nouvelle intronisation (Direct Debut, Long Run, Triple Debut, Legendary Run)")
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val allOn = disabled.isEmpty()
                ToggleRow("Tout activer", "Interrupteur général des ${SettingsRepository.Notif.ALL.size} notifications", allOn) { on ->
                    scope.launch { SettingsRepository.Notif.ALL.forEach { settings.setNotificationEnabled(it, on) } }
                }
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("🔔 Réglages Android (son, importance)") }
            }
        }
        groups.forEach { (title, items) ->
            SectionTitle(title)
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    items.forEach { (key, label) ->
                        ToggleRow(label, if (key in disabled) "désactivée" else "activée", key !in disabled) { on -> scope.launch { settings.setNotificationEnabled(key, on) } }
                    }
                }
            }
        }
        SectionTitle("🕘 Dernières notifications")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                if (feed.isEmpty()) Text("Aucune pour l'instant — elles apparaîtront ici et sur l'Accueil.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                feed.forEach { n -> Text("• ${n.message}", color = theme.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}

/* ================================ DONNÉES ================================ */

@Composable
private fun DataPage() {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val theme = Nova.theme
    val threshold by settings.thresholdSec.collectAsStateWithLifecycle(initialValue = ScrobbleRules.DEFAULT_THRESHOLD_SEC)
    val autoBackup by settings.autoBackup.collectAsStateWithLifecycle(initialValue = false)
    val lastBackup by settings.lastBackupAt.collectAsStateWithLifecycle(initialValue = null)
    val scrobbles by app.database.scrobbleDao().countConfirmedFlow().collectAsStateWithLifecycle(initialValue = 0)
    val tracks by app.database.trackDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val artists by app.database.artistDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val albums by app.database.albumDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val records by app.database.recordDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)

    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true; status = "Lecture du fichier…"
        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    val version = BackupExporter.formatVersion(text)
                    if (BackupExporter.isNewerThanSupported(version)) error("Ce fichier vient d'une version plus récente de NovaStats ($version). Mets à jour l'app avant d'importer.")
                    val backup = LegacyBackupImporter.parse(text.byteInputStream())
                    LegacyBackupImporter.import(app.database, backup, threshold) { status = it }
                }
                EnrichmentWorker.enqueue(context)
                status = report.summary()
            } catch (e: Exception) { status = "❌ Import échoué : ${e.message}" } finally { working = false }
        }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = pendingExport
        if (uri == null || text == null) { pendingExport = null; return@rememberLauncherForActivityResult }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } } }
                .onSuccess { status = "✅ Export enregistré (${text.length / 1024} Ko)"; settings.setLastBackupAt(System.currentTimeMillis()) }
                .onFailure { status = "❌ Export échoué : ${it.message}" }
            pendingExport = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatPill(formatCount(scrobbles), "écoutes")
                    StatPill(formatCount(tracks), "titres", accent = theme.secondary)
                    StatPill(formatCount(artists), "artistes", accent = theme.accent)
                    StatPill(formatCount(albums), "albums", accent = theme.glowSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text("Base SQLite : ${dbSizeLabel(context)} · ${formatCount(records)} entrées de records en cache", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                if (working) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = theme.accent) }
                status?.let { Spacer(Modifier.height(8.dp)); Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall) }
            }
        }

        SectionTitle("📤 Export / 📥 Import JSON")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text("Format NovaStats v2 : compatible avec l'ancien format v1 (songs + plays) et enrichi (artistes, albums, certifications, Panthéon, Hall of Fame, historique).", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        working = true; status = "Préparation de l'export…"
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { BackupExporter.build(app.database, BuildConfig.VERSION_NAME) } }
                                .onSuccess { (text, s) -> pendingExport = text; status = "${s.songs} titres · ${s.plays} écoutes · ${s.bytes / 1024} Ko"; saver.launch(BackupExporter.fileName()) }
                                .onFailure { status = "❌ ${it.message}" }
                            working = false
                        }
                    },
                    enabled = !working && scrobbles > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) { Text("📤 Exporter mes données (JSON)") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { importer.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                    enabled = !working, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("📥 Importer un backup JSON (v1 / v2)") }
                Text("L'import fusionne avec l'existant : doublons ignorés, puis recalcul complet (Billboard, certifs, records…).", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }

        SectionTitle("🔗 Liens artistes & versions")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val relinkState by com.novastats.app.data.repository.RelinkJob.state.collectAsStateWithLifecycle()
                val relinkResult by com.novastats.app.data.repository.RelinkJob.lastResult.collectAsStateWithLifecycle()
                Text("Re-lit chaque écoute depuis les infos brutes du lecteur : artistes invités manquants créés, versions « Titre (with Invité) » et remix featuring rattachés à l'original (total fusionné dans classements, records et certifications), noms protégés appliqués (🔒 Éditeur). Fait automatiquement une fois après la mise à jour.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scope.launch { withContext(Dispatchers.IO) { com.novastats.app.data.repository.RelinkJob.run(app) } } },
                    enabled = relinkState == null && !working && scrobbles > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) { Text(if (relinkState != null) "⏳ ${relinkState}" else "🔗 Recalculer liens & versions") }
                relinkResult?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        SectionTitle("💾 Sauvegarde automatique")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                ToggleRow("Sauvegarde quotidienne", "Export JSON dans Android/data/com.novastats.app/files/backups (7 conservés)", autoBackup) { scope.launch { settings.setAutoBackup(it) } }
                Text("Dernière sauvegarde : ${lastBackup?.let { formatDate(it) } ?: "jamais"}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                val files = remember(lastBackup) { BackupWorker.existing(context) }
                if (files.isNotEmpty()) Text("${files.size} fichier(s) · dernier : ${files.first().name}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { BackupWorker.runNow(context); status = "💾 Sauvegarde lancée en arrière-plan" },
                    enabled = scrobbles > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("💾 Sauvegarder maintenant") }
            }
        }

        SectionTitle("🔧 Maintenance")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = { working = true; scope.launch { status = "Recalcul…"; withContext(Dispatchers.IO) { app.rebuilder.rebuildAll { status = it } }; status = "✅ Statistiques recalculées"; working = false } },
                    enabled = !working && scrobbles > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("🔄 Recalculer toutes les statistiques") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scope.launch { withContext(Dispatchers.IO) { app.database.apiCacheDao().clearAll() }; status = "🧹 Cache API vidé — le prochain enrichissement réinterrogera les sources" } },
                    enabled = !working, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("🧹 Vider le cache API") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirmDelete = true; confirmText = "" },
                    enabled = !working, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C).copy(alpha = 0.85f), contentColor = Color.White)
                ) { Text("🗑️ Supprimer toutes les données") }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = theme.surface, titleContentColor = theme.text, textContentColor = theme.textSecondary,
        title = { Text("⚠️ Tout supprimer ?") },
        text = {
            Column {
                Text("Écoutes, titres, artistes, certifications, records… seront effacés définitivement. Pense à exporter d'abord.\n\nÉcris SUPPRIMER pour confirmer.")
                OutlinedTextField(value = confirmText, onValueChange = { confirmText = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = confirmText.trim().equals("SUPPRIMER", ignoreCase = true), onClick = {
                confirmDelete = false; working = true
                scope.launch {
                    withContext(Dispatchers.IO) { app.database.clearAllTables() }
                    status = "🗑️ Toutes les données ont été supprimées"; working = false
                }
            }) { Text("Supprimer", color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler", color = theme.textSecondary) } }
    )
}

/* ================================ SERVICE ================================ */

@Composable
private fun ServicePage() {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val theme = Nova.theme
    val watchdog by settings.watchdogEnabled.collectAsStateWithLifecycle(initialValue = true)
    var accessGranted by remember { mutableStateOf(NovaListenerService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) { accessGranted = NovaListenerService.isEnabled(context); onPauseOrDispose { } }
    val detection by DetectionState.state.collectAsStateWithLifecycle()
    val running = accessGranted && detection.listenerConnected

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("🛡️ Service")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (running) "🟢" else if (accessGranted) "🟠" else "🔴")
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(when { running -> "Service actif"; accessGranted -> "Accès accordé, service pas encore connecté"; else -> "Service inactif" }, color = theme.text, fontWeight = FontWeight.SemiBold)
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
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (accessGranted) theme.surface else theme.primary, contentColor = if (accessGranted) theme.text else MaterialTheme.colorScheme.onPrimary)
                ) { Text(if (accessGranted) "Gérer l'accès aux notifications" else "Activer la détection") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("🔋 Désactiver l'optimisation batterie") }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Watchdog", "Vérification toutes les 15 min + à l'ouverture : relance le listener s'il est endormi", watchdog) {
                    scope.launch { settings.setWatchdog(it); if (it) com.novastats.app.service.Watchdog.schedule(context) else com.novastats.app.service.Watchdog.cancel(context) }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { com.novastats.app.service.Watchdog.revive(context, "manuel") }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("🐕 Relancer le service maintenant") }
            }
        }
        SectionTitle("🔎 Diagnostic détection")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                val health by com.novastats.app.service.ServiceHealth.state.collectAsStateWithLifecycle()
                val batteryExempt = remember { com.novastats.app.service.Watchdog.isBatteryExempt(context) }
                DiagLine("Listener connecté", if (detection.listenerConnected) "oui" else "non")
                DiagLine("Dernier signe de vie", com.novastats.app.service.ServiceHealth.ago(health.lastHeartbeat), error = health.isStale() && accessGranted)
                DiagLine("Service premier plan", "notification « NovaStats veille »")
                DiagLine("Optimisation batterie", if (batteryExempt) "désactivée ✅" else "ACTIVE ⚠️ (risque de gel)", error = !batteryExempt)
                if (health.restarts > 0) DiagLine("Relances watchdog", "${health.restarts} · dernière ${com.novastats.app.service.ServiceHealth.ago(health.lastRestart)} (${health.lastRestartReason ?: "?"})")
                DiagLine("MediaSession", if (detection.mediaSessionAvailable) "disponible" else "indisponible")
                DiagLine("Sessions média actives", detection.activeSessions.ifEmpty { listOf("aucune") }.joinToString(", "))
                if (detection.ignoredSessions.isNotEmpty()) DiagLine("Ignorées (whitelist)", detection.ignoredSessions.joinToString(", "))
                DiagLine("Dernier titre capté", detection.lastTrack ?: "—")
                detection.lastError?.let { DiagLine("Dernière erreur", it, error = true) }
                Spacer(Modifier.height(8.dp))
                Text("Journal", color = theme.text, fontWeight = FontWeight.SemiBold)
                if (detection.log.isEmpty()) Text(
                    "Vide : le service n'a pas démarré. Vérifie l'accès aux notifications puis relance l'app. Si le journal reste vide, ton téléphone (Xiaomi/Huawei/Oppo/Tecno…) bloque peut-être le service : autorise le démarrage automatique de NovaStats.",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
                detection.log.forEach { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
        SectionTitle("🧯 Plantages")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                var journal by remember { mutableStateOf(com.novastats.app.util.CrashJournal.read(context)) }
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Text(
                    "Si l'app se ferme toute seule, la trace est enregistrée ici. Copie-la et colle-la dans la discussion pour que je corrige.",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                if (journal.isBlank()) Text("Aucun plantage enregistré ✅", color = theme.text)
                else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(journal)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.background)) { Text("📋 Copier") }
                        Button(onClick = { com.novastats.app.util.CrashJournal.clear(context); journal = "" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)) { Text("🗑️ Effacer") }
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(journal.take(6000), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

/* ================================ APIs ================================ */

@Composable
private fun ApisPage() {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val theme = Nova.theme
    val enrich by EnrichmentState.state.collectAsStateWithLifecycle()
    val autoEnrich by settings.autoEnrich.collectAsStateWithLifecycle(initialValue = true)
    val wifiOnly by settings.enrichWifiOnly.collectAsStateWithLifecycle(initialValue = false)
    val missingTracks by app.database.trackDao().missingCoverCount().collectAsStateWithLifecycle(initialValue = 0)
    val missingArtists by app.database.artistDao().missingPhotoCount().collectAsStateWithLifecycle(initialValue = 0)
    val missingAlbums by app.database.albumDao().missingCoverCount().collectAsStateWithLifecycle(initialValue = 0)
    val reliability by app.database.apiCacheDao().reliability().collectAsStateWithLifecycle(initialValue = emptyList())
    val sources = remember { app.enricher.configuredSources() }
    var confirmAll by remember { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = picker) { picker = false }
    if (picker) { ReenrichPicker(onBack = { picker = false }); return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        SectionTitle("🌐 Enrichissement")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Cascade 100 % gratuite. Titres / albums : iTunes → Deezer → MusicBrainz → Last.fm → Discogs → Genius → YouTube (dernier recours, toujours 🟡 À vérifier). Artistes : Deezer → Fanart.tv → Wikidata → TheAudioDB → Last.fm → Genius → YouTube. Une pochette n'est acceptée que si l'artiste est confirmé.",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatPill("$missingTracks", "titres sans pochette")
                    StatPill("$missingAlbums", "albums", accent = theme.secondary)
                    StatPill("$missingArtists", "artistes sans photo", accent = theme.accent)
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Enrichissement automatique", "Après chaque écoute et une fois par jour", autoEnrich) { scope.launch { settings.setAutoEnrich(it) } }
                ToggleRow("Wi-Fi uniquement", "Pas de requêtes API en données mobiles", wifiOnly) { scope.launch { settings.setEnrichWifiOnly(it) } }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { EnrichmentWorker.enqueue(context, manual = true) }, enabled = !enrich.running,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary), modifier = Modifier.fillMaxWidth()
                ) { Text(if (enrich.running) "Enrichissement en cours…" else "🌐 Enrichir maintenant") }
                Spacer(Modifier.height(6.dp))
                // Ré-enrichissement forcé : tout, ou une sélection (page dédiée)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { confirmAll = true }, enabled = !enrich.running, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondary)
                    ) { Text("🔄 Tout ré-enrichir", maxLines = 1) }
                    Button(
                        onClick = { picker = true }, enabled = !enrich.running, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                    ) { Text("🔄 Ré-enrichir…", maxLines = 1) }
                }
                Text(
                    "Ré-enrichir ignore le cache et relance la cascade complète ; l'image actuelle n'est remplacée que si un résultat ≥ 70 est trouvé. « Tout » garde les images choisies à la main.",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)
                )
                if (enrich.running) {
                    Spacer(Modifier.height(6.dp))
                    if (enrich.total > 0) LinearProgressIndicator(progress = { enrich.processed.toFloat() / enrich.total }, modifier = Modifier.fillMaxWidth(), color = theme.primary)
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = theme.primary)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (enrich.mode?.let { "$it · " } ?: "") + "${enrich.current ?: ""} · ${enrich.processed}${if (enrich.total > 0) "/${enrich.total}" else ""} traités, ${enrich.found} trouvés",
                            color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { EnrichmentWorker.cancel(context) }) { Text("⏹️ Arrêter", color = Color(0xFFE74C3C), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        if (confirmAll) AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("🔄 Tout ré-enrichir ?") },
            text = { Text("Toutes les photos d'artistes, pochettes d'albums et de titres seront recherchées à nouveau (les plus écoutés d'abord). Les images choisies à la main sont conservées. Cela peut prendre plus d'une heure et consomme les quotas des APIs ; tu peux arrêter à tout moment.") },
            confirmButton = { TextButton(onClick = { confirmAll = false; EnrichmentWorker.enqueueRefreshAll(context) }) { Text("Lancer", color = theme.primary, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annuler") } }
        )
        SectionTitle("📡 Sources")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                sources.forEach { (src, configured) ->
                    val r = reliability.firstOrNull { it.apiName == src.label }
                    val error = enrich.errors[src.label]
                    val stats = if (r == null || r.successCount + r.failCount == 0) "pas encore utilisée" else "${r.successRate.toInt()} % de succès (${r.successCount}/${r.successCount + r.failCount})"
                    val dot = when {
                        src.retired -> Color(0xFF95A5A6)
                        !configured -> Color(0xFF95A5A6)
                        error != null -> Color(0xFFE74C3C)
                        else -> Color(0xFF2ECC71)
                    }
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Dot(dot)
                            Spacer(Modifier.width(8.dp))
                            Text("${src.emoji} ${src.label}", color = if (src.retired) theme.textSecondary else theme.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(130.dp))
                            Text(
                                when {
                                    src.retired -> src.retiredReason ?: "retirée"
                                    !configured -> "clé manquante (secret GitHub ${secretName(src)})"
                                    else -> stats
                                },
                                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                            )
                        }
                        if (error != null && configured && !src.retired) {
                            Text("⛔ $error", color = Color(0xFFE74C3C), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        scope.launch { app.database.apiCacheDao().clearReliability(); EnrichmentState.clearAllErrors() }
                    }) { Text("↺ Remettre les compteurs à zéro", color = theme.primary, style = MaterialTheme.typography.bodySmall) }
                }
                if (enrich.log.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Journal", color = theme.text, fontWeight = FontWeight.SemiBold)
                    enrich.log.take(15).forEach { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

/** Nom du secret GitHub Actions attendu pour une source (affiché quand la clé manque). */
private fun secretName(src: com.novastats.app.domain.ApiSource): String = when (src) {
    com.novastats.app.domain.ApiSource.LASTFM -> "LASTFM_API_KEY"
    com.novastats.app.domain.ApiSource.FANART -> "FANART_API_KEY"
    com.novastats.app.domain.ApiSource.DISCOGS -> "DISCOGS_TOKEN"
    com.novastats.app.domain.ApiSource.GENIUS -> "GENIUS_ACCESS_TOKEN"
    com.novastats.app.domain.ApiSource.YOUTUBE -> "YOUTUBE_API_KEY"
    else -> "—"
}

/* ================================ À PROPOS ================================ */

private val CHANGELOG = listOf(
    "0.15.1" to listOf(
        "\ud83d\udd0a **Les fonds artistiques \u00e9taient trop discrets** (quasiment invisibles) : l'opacit\u00e9 de l'image et la force du voile se multipliaient. Nouveau dosage : l'art flout\u00e9 est nettement pr\u00e9sent en haut de l'\u00e9cran et s'efface vers le bas pour garder les listes lisibles.",
        "\ud83c\udf1f Nappes de couleur plus larges et plus lumineuses, particules plus visibles, image de fond en meilleure d\u00e9finition (96 px au lieu de 64 px avant \u00e9tirement)."
    ),
    "0.15.0" to listOf(
        "\ud83c\udf0c **Tous les onglets** (Stats, Billboard, Certifications, Records, Panth\u00e9on, Nova Awards, Hall of Fame) ont d\u00e9sormais un **fond artistique** : pochette ou photo mise en avant, flout\u00e9e, tr\u00e8s assombrie, avec les particules qui montent. Stats suit ta p\u00e9riode (l'artiste n\u00b01 du moment), Billboard suit le n\u00b01 du chart affich\u00e9.",
        "\ud83e\udea1 **Certifications** : chaque ligne porte maintenant une **m\u00e9daille m\u00e9tallique** (Argent / Or / Platine / Diamant) pos\u00e9e sur le coin de la pochette, avec le multiplicateur \u00d7n quand il y en a un.",
        "\ud83d\udee0\ufe0f Nouvelle brique partag\u00e9e `ScreenBackdrop` (fond d'onglet) dans `PremiumUi.kt` : un seul endroit pour r\u00e9gler l'intensit\u00e9 du flou, du voile et des particules de tout ce qui n'est pas un popup."
    ),
    "0.14.0" to listOf(
        "\ud83c\udfa8 Le langage visuel du Rewind s'\u00e9tend \u00e0 toute l'app. **Accueil** : fond artistique plein \u00e9cran (photo de ton artiste du moment des 7 derniers jours, flout\u00e9e et tr\u00e8s assombrie) + particules + bloc \u00ab Aujourd'hui \u00bb en verre avec le compteur du jour en tr\u00e8s gros.",
        "\ud83e\udea1 **Popups de fiche** (titre / artiste / album) : les statistiques passent en carte de verre avec le nombre d'\u00e9coutes en tr\u00e8s gros et d\u00e9filant, le parcours hebdomadaire et la pr\u00e9vision sont encapsul\u00e9s dans le verre.",
        "\ud83c\udfc5 **M\u00e9dailles** : Argent / Or / Platine / Diamant en d\u00e9grad\u00e9s m\u00e9talliques (avec halo et multiplicateur \u00d7n) affich\u00e9es dans les fiches certifi\u00e9es.",
        "\u2728 Briques partag\u00e9es (nouveau fichier `PremiumUi.kt`) : fond flout\u00e9, particules, confettis, cascade \u00e0 ressort, cartes et pastilles de verre, compteurs anim\u00e9s, graphiques, pochettes cercl\u00e9es."
    ),
    "0.13.0" to listOf(
        "\ud83c\udfaf Nova Rewind refait en mode \u00ab poster \u00bb : fond plein \u00e9cran flout\u00e9 \u00e0 partir de la pochette ou de la photo de ton artiste n\u00b01 (avec travelling lent), plus aucune liste s\u00e8che \u2014 tout est en cartes de verre bord\u00e9es de lumi\u00e8re, typographie XXL et chiffres g\u00e9ants.",
        "\ud83d\udcca Rythme : vrai graphique de tes 24 heures d'\u00e9coute + barres des 7 jours de la semaine + ta journ\u00e9e record et ta plus longue session.",
        "\ud83d\udc31 Artiste / Titre n\u00b01 : nom en tr\u00e8s gros, chiffre g\u00e9ant en d\u00e9grad\u00e9 balayant, anneau qui tourne autour de la pochette, carte de pr\u00e9sence (X jours sur Y).",
        "\ud83c\udfc5 R\u00e9compenses : vraies m\u00e9dailles (Argent / Or / Platine / Diamant) d\u00e9filantes au lieu de simples lignes ; d\u00e9couvertes en grille de portraits ; top 5 avec podium (le n\u00b01 en plus grand).",
        "\u2728 Entr\u00e9es en cascade avec l\u00e9ger rebond (ressort), transitions en profondeur, confettis \u00e0 la finale."
    ),
    "0.12.3" to listOf(
        "\ud83c\udf08 Rewind encore plus spectaculaire : aurores de couleur qui d\u00e9rivent derri\u00e8re les slides, grands chiffres en d\u00e9grad\u00e9 balayant (couleurs des drapeaux pour Survivor), titre de la couverture qui s'\u00e9crit lettre par lettre, pochettes en travelling lent (\u00ab Ken Burns \u00bb) dans leur anneau qui tourne.",
        "\ud83c\udf8a Confettis \u00e0 la finale (or, ou couleurs des drapeaux pour Survivor) + pastilles r\u00e9cap (artiste n\u00b01, titre n\u00b01, certifications, s\u00e9rie) qui apparaissent en cascade.",
        "\ud83c\udf9b\ufe0f Transitions en profondeur (zoom + gliss\u00e9 + l\u00e9g\u00e8re rotation au glisser), bouton d'action \u00e0 d\u00e9grad\u00e9 balayant, sons distincts (souffle en avant, tick en arri\u00e8re, impact \u00e0 la finale), 7 s par slide en lecture auto."
    ),
    "0.12.2" to listOf(
        "\u2728 Nova Rewind devient vraiment anim\u00e9 : sc\u00e8ne vivante (halo qui d\u00e9rive + particules aux couleurs du th\u00e8me, voile des drapeaux pour Survivor), entr\u00e9e en cascade de chaque \u00e9l\u00e9ment, compteurs qui d\u00e9filent de 0, anneau lumineux qui tourne autour des pochettes, barres de progression anim\u00e9es (top 5, grands chiffres).",
        "\ud83d\udc46 D\u00e9filement au doigt : glisse vers la gauche ou la droite pour changer de slide (la slide suit ton doigt), appui \u00e0 gauche pour reculer, \u00e0 droite pour avancer.",
        "\u25b6\ufe0f Lecture automatique type story avec barre de progression segment\u00e9e et bouton \u23f8 / \u25b6 ; petit son discret et vibration l\u00e9g\u00e8re \u00e0 chaque slide (vibration selon ton r\u00e9glage).",
        "\ud83d\udc48 Carte d'entr\u00e9e \u00ab Nova Rewind \u00bb anim\u00e9e sur l'Accueil."
    ),
    "0.12.1" to listOf(
        "\ud83d\udcc8 Parcours hebdomadaire dans les fiches titre / artiste / album : meilleur rang, nombre de semaines class\u00e9es, semaines pass\u00e9es n\u00b01, date d'entr\u00e9e et de pic, puis les 14 derni\u00e8res semaines en bandeau avec la variation \u25b2 / \u25bc (donn\u00e9es Billboard d\u00e9j\u00e0 enregistr\u00e9es).",
        "\u23f1 Pr\u00e9visions : le prochain palier (certification ou statut Panth\u00e9on) avec les \u00e9coutes restantes, une barre de progression et une estimation en jours bas\u00e9e sur ton rythme des 14 derniers jours."
    ),
    "0.12.0" to listOf(
        "\u2728 Nova Rewind : ton mois (ou ton année) en récap façon keynote \u2014 écoutes, artiste et titre n°1, top 5, rythme (record du jour, série, heure de prédilection), découvertes, certifications et Panthéon. Sélecteur de période en haut, on avance en touchant l'écran.",
        "\ud83d\uddbc\ufe0f Carte à partager : un visuel 1080 × 1920 généré aux couleurs de ton thème (bandes des drapeaux pour Survivor) avec tes chiffres et ton top 5, partageable en story. Aucune permission de stockage \u2014 le fichier passe par le partage système.",
        "\ud83d\udccc Accès depuis l'Accueil : carte \u00ab Nova Rewind \u00bb en haut de la page."
    ),
    "0.11.4" to listOf(
        "🩹 Éditeur — fusions qui « ne faisaient rien » : (1) une écoute journalisée deux fois au même instant sous les deux titres bloquait toute la fusion (contrainte d'unicité) → dédoublonnée ; (2) un lot est traité paire par paire, un échec n'annule plus les autres et le message indique lequel ; (3) le doublon renaissait à l'écoute suivante (« Can't Stop the High » / « …The High ») → la fusion est mémorisée comme correction et les recherches de titres/albums ignorent la casse.",
        "🩹 Les versions liées (« (with X) », remix) suivent le titre conservé lors d'une fusion ; les caches de résolution sont vidés après chaque action de l'éditeur.",
        "🖼️ Pochettes : tous les titres d'un album prennent la pochette de l'album à chaque recalcul (après fusions, déplacements, albums multi-artistes) — sauf pochette choisie par toi. Un titre pouvait garder la pochette trouvée seul avant que son album ne soit résolu, ou celle d'un ancien album."
    ),
    "0.11.3" to listOf(
        "🩹 Fiche album : les versions d'un titre (« Free (with Jinu) », « ExtraL (with Doechii) », remix lié) n'apparaissent plus en double sous leur original. Une ligne par titre, écoutes cumulées (comme dans les classements), artistes réunis (« Rumi, Jinu »). Le compteur « titres écoutés » suit la même règle.",
        "ℹ️ Deux titres non liés (ex. « EXTANCY » et « EXTANCY (Wumuti&Rui) ») restent séparés : fusionne-les dans l'Éditeur si c'est le même morceau."
    ),
    "0.11.2" to listOf(
        "💽 Albums coupés par les duos (règle 13) : un titre rejoint l'album existant d'UN de ses artistes, principal ou invité. « One Kiss » (Calvin Harris & Dua Lipa) va dans « Dua Lipa (Complete Edition) » de Dua Lipa — plus d'album homonyme d'un seul titre au nom du partenaire (reputation / Zayn, BEYONCÉ / Megan Thee Stallion, Ruby / Tame Impala & Zico, ANTI, Loud, My Everything, So Good…).",
        "🧭 Plusieurs albums possibles → celui de l'artiste commun (présent sur le plus de titres), à égalité le plus écouté. Si le duo arrive avant les titres solo, les deux albums sont fusionnés dès le recalcul suivant et le propriétaire devient l'artiste commun.",
        "🛡️ Garde-fou : deux albums homonymes d'artistes sans aucun lien (« Greatest Hits », « Ruby », « Rise ») ne sont jamais fusionnés. Les albums multi-artistes (règle 12) ne sont pas concernés.",
        "📊 Totaux, certifications et records d'albums par artiste attribués à l'artiste commun ; fusion des albums déjà coupés au premier lancement (recalcul automatique)."
    ),
    "0.11.1" to listOf(
        "🩹 « THE ALBUM » (BLACKPINK) n'est plus pris pour un album multi-artistes : le mot-clé « The Album » ne compte qu'accolé à un nom (« F1 The Album », « Barbie The Album »). L'album est rendu à BLACKPINK au premier lancement.",
        "🩹 Fiche d'un album partagé vide (« Chansons (0) », « 0 artiste ») alors qu'il comptait des écoutes : les titres suivent désormais leurs écoutes dans l'album fusionné (Listen Up!, K-Pop Demon Hunters…). Le nombre d'artistes compte les artistes principaux des titres.",
        "💾 Un album partagé grâce à l'artiste d'album « Various Artists » est mémorisé comme marque : il le reste après une re-liaison ou un ré-import.",
        "♻️ Recalcul automatique relancé une fois au premier lancement."
    ),
    "0.11.0" to listOf(
        "💿 Albums multi-artistes (BO, albums d'événements) : « K-Pop Demon Hunters: Soundtrack… », « F1 The Album », « …FIFA World Cup Album » existent une seule fois, sans artiste propriétaire (étiquette « Artistes variés »). Tous leurs titres s'y rattachent quel que soit l'artiste principal ; le total et la certification de l'album portent sur l'ensemble.",
        "🔎 Déclencheurs : mots-clés du titre (Soundtrack, Motion Picture, Music From, The Album, World Cup, Bande originale), artiste d'album « Various Artists », ou marquage manuel dans l'Éditeur → album → 💿 Marquer multi-artistes / 👤 Retirer (mémorisé, survit aux ré-imports, exporté dans le JSON).",
        "🎤 Chaque artiste garde ses propres titres ; un album partagé n'est jamais crédité à un artiste (records / certifs d'albums par artiste, Panthéon). « Artistes variés » n'est pas un artiste : absent des stats, du Billboard et des records d'artistes.",
        "🪟 Fiche album partagé : « Artistes variés · N artistes », total, certification, et la liste des titres avec leurs artistes — un clic ouvre la fiche du titre.",
        "♻️ Les albums déjà coupés en plusieurs entrées sont fusionnés au premier lancement (recalcul automatique), puis stats, certifs et records recalculés."
    ),
    "0.10.9" to listOf(
        "💎 Fiche certification : tous les artistes crédités (plus seulement le principal) et la mention « 🔗 Ce total inclut N version(s) liée(s) » avec la répartition Original / versions, comme dans la fiche titre."
    ),
    "0.10.8" to listOf(
        "🐛 « Goals — LISA : Original 0 ▶, with Anitta, Rema, Fifa Sound 97 ▶ » : un root solo sans aucune écoute propre n'est pas une version solo. À chaque recalcul, ces roots vides sont repliés : les versions y sont fusionnées (écoutes, artistes, album, pochette) et le titre redevient unique, crédité à tous les artistes."
    ),
    "0.10.7" to listOf(
        "🎯 Règle précisée : la « version avec invité » n'existe que face à une version SOLO du même titre. Deux featurings différents sans version solo (A & B puis A & C) = un seul titre crédité à tous les artistes (comme avant 0.10.5).",
        "🐛 BOOMPALA : les écoutes importées depuis le JSON n'avaient pas de valeurs brutes → le recalcul ne pouvait pas retrouver « Santos Bravos ». L'import mémorise désormais titre / artistes / album du fichier, et ré-importer un ancien JSON répare les écoutes existantes (déplacées vers la bonne version, jamais dupliquées).",
        "♻️ Recalcul : sans valeur brute, tous les artistes liés au titre sont utilisés (pas seulement le principal)."
    ),
    "0.10.6" to listOf(
        "🐛 Recalcul des liens interrompu par « UNIQUE constraint failed: scrobbles.track_id, started_at » : quand deux écoutes au même instant convergent vers le même titre, le doublon est supprimé au lieu de faire échouer le recalcul."
    ),
    "0.10.5" to listOf(
        "🔒 Noms protégés : « HUNTR/X », « AC/DC », « Tyler, The Creator »… ne sont plus découpés en plusieurs artistes, même dans « HUNTR/X feat. Future ». Liste modifiable dans l'Éditeur (onglet 🔒 Noms protégés).",
        "🔗 Remix featuring + original = un seul titre dans les classements, records, certifications et Stats (écoutes additionnées). Le remix reste une version distincte, liée à l'original ; la fiche du titre montre la répartition par version.",
        "🎤 Invité absent de la base : « BOOMPALA » par « LE SSERAFIM & Santos Bravos » crée désormais Santos Bravos ET une version « BOOMPALA (with Santos Bravos) » liée à l'original (87 + 47 = 134 écoutes sur la fiche).",
        "♻️ Recalcul automatique des liens au premier lancement (boîte de progression) + bouton 🔗 Recalculer liens & versions dans Réglages → Données.",
        "💾 Les noms protégés sont inclus dans l'export JSON et restaurés avant l'import."
    ),
    "0.10.4" to listOf(
        "🐛 Stats : plus de fermeture de l'app en passant en Daily sur Artistes / Albums (liste tronquée pendant le rechargement — IndexOutOfBounds)",
        "🐛 Billboard : même protection sur le bouton « Voir plus »"
    ),
    "0.10.3" to listOf(
        "🎵 Popup artiste (Stats) : toutes ses chansons de la période avec leur position dans le classement titres (même hors Top 300), Top 5 puis « Voir plus » +5",
        "🧯 Journal des plantages (Réglages → Service & diagnostic) : copie la trace en cas de fermeture de l'app",
        "🛡️ Popups artiste / album : chargement défensif, une requête en échec ne ferme plus l'app"
    ),
    "0.10.2" to listOf(
        "🧐 Fiche record : accroche-portrait unique par élément (écoutes, découverte, rang général, certification / Panthéon, série, fraîcheur) + formulations variées",
        "⚡ Fastest Certification / Panthéon affichés en jours, heures et minutes",
        "🏅 Périodes Daily / Weekly / Monthly / Yearly en segments pleine largeur"
    ),
    "0.10.1" to listOf(
        "🧐 Fiche record : « Pourquoi il est là » unique à chaque élément (place dans le classement, profil dans le chart, autres records détenus)",
        "🏅 Sous-sections en segments pleine largeur, comme Titres / Artistes / Albums"
    ),
    "0.10.0" to listOf(
        "🏅 6 nouveaux records : Longest Lifespan, Most Re-Entries, Longest Absence Return, Longest Listening Streak, Podium Sweep (Solo / Standard), Most Records (fiche détaillée)",
        "🏎️ Fastest Rise : sections Top 1 / 3 / 5 / 10 / 20, entrées directes exclues"
    ),
    "0.9.9" to listOf(
        "🎤 Onboarding refait façon keynote : appareil 3D, carrousel de thèmes, polices Inter embarquées, rythme rapide"
    ),
    "0.9.8" to listOf(
        "🖼️ 15 icônes d'app uniques, une par univers — Survivor aux couleurs de la fierté (arc-en-ciel, Progress, trans)"
    ),
    "0.9.7" to listOf(
        "🎬 Onboarding « Cinéma » : noir + une couleur, thèmes plein écran, parallaxe gyroscopique, 3 sons discrets (sans musique)",
        "🔒 Permissions bloquantes : notifications + optimisation batterie obligatoires, chemins manuels HiOS / Phone Master",
        "🖼️ Changer de thème ne ferme plus l'app : l'icône change en arrière-plan (réglage « Icône dynamique »)",
        "🔁 « Revoir l'introduction » dans Apparence et À propos"
    ),
    "0.2.0" to listOf(
        "🏅 Onglet Records complet : 24 records, popup 90 % avec périodes / sections / sous-sections",
        "⚙️ Paramètres refaits : sous-pages, notifications (11 interrupteurs), blacklist, apps sources",
        "📤 Export JSON v2 (compatible v1) · 💾 sauvegarde auto quotidienne · 🗑️ suppression totale",
        "🛠️ Éditeur de données : renommer, fusionner, changer artiste/album, supprimer une écoute, Undo",
        "🔔 Notifications de succès (certifs, Panthéon, Hall of Fame) après chaque écoute"
    ),
    "0.1.x" to listOf(
        "🏠 Accueil 8 sections · 📊 Stats complètes avec popups et recherche",
        "📈 Billboard Daily/Weekly/Monthly/Yearly/Global, Hall of Fame",
        "🌐 Cascade de 10 sources gratuites pour pochettes et photos",
        "🎧 Détection MediaSession + fallback notifications, import JSON v1"
    )
)

@Composable
private fun AboutPage() {
    val theme = Nova.theme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        NovaCard {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎧", fontSize = 48.sp)
                Text("NovaStats", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", color = theme.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text("Tes statistiques d'écoute, façon Billboard. 100 % local : aucune donnée n'est envoyée, hors requêtes d'images vers les APIs publiques.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                val aboutScope = rememberCoroutineScope()
                val aboutSettings = (LocalContext.current.applicationContext as com.novastats.app.NovaStatsApp).settings
                OutlinedButton(onClick = { aboutScope.launch { aboutSettings.replayOnboarding() } }) { Text("🎬 Revoir l'introduction", color = theme.primary) }
            }
        }
        SectionTitle("🆕 Nouveautés")
        CHANGELOG.forEach { (v, lines) ->
            NovaCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Version $v", color = theme.primary, fontWeight = FontWeight.Bold)
                    lines.forEach { Text("• $it", color = theme.text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
        SectionTitle("🙏 Crédits & licences")
        NovaCard {
            Column(Modifier.padding(16.dp)) {
                listOf(
                    "Kotlin · Jetpack Compose · Room · WorkManager · DataStore (Apache 2.0)",
                    "Coil (Apache 2.0) · Retrofit / OkHttp (Apache 2.0) · kotlinx.serialization (Apache 2.0)",
                    "Données images : iTunes, Spotify, Last.fm, MusicBrainz / Cover Art Archive, TheAudioDB, Deezer, Discogs, Fanart.tv, Google",
                    "Cahier des charges & idée : le fichier DEBUT — merci ✨"
                ).forEach { Text("• $it", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}

/* ================================ Helpers ================================ */

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
