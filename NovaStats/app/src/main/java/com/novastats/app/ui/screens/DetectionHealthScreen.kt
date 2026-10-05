package com.novastats.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.service.DetectionState
import com.novastats.app.service.NovaListenerService
import com.novastats.app.service.ServiceHealth
import com.novastats.app.service.Watchdog
import com.novastats.app.ui.theme.Nova
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/* ==================================================================== */
/*  🩺 Santé de la détection — score, vérifications, plan d'action HiOS   */
/* ==================================================================== */

private const val HOUR_MS = 60 * 60 * 1000L
private const val DAY_MS = 24 * HOUR_MS

/** Une vérification affichée : ok = true ✅, false ⚠️, null = information. */
private data class HealthCheck(
    val emoji: String,
    val title: String,
    val ok: Boolean?,
    val detail: String,
    val weight: Int = 0,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

private data class PlanStep(
    val emoji: String,
    val title: String,
    val detail: String,
    val button: String,
    val action: (Context) -> Unit
)

@Composable
fun DetectionHealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme

    val detection by DetectionState.state.collectAsStateWithLifecycle()
    val health by ServiceHealth.state.collectAsStateWithLifecycle()

    // Horloge interne : rafraîchit les « il y a X » et l'état batterie
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); tick++ } }

    val now24 = remember { System.currentTimeMillis() - DAY_MS }
    val now7 = remember { System.currentTimeMillis() - 7 * DAY_MS }
    val plays24 by db.scrobbleDao().countSince(now24).collectAsStateWithLifecycle(initialValue = 0)
    val plays7 by db.scrobbleDao().countSince(now7).collectAsStateWithLifecycle(initialValue = 0)
    val lastPlayAt by db.scrobbleDao().lastPlayAt().collectAsStateWithLifecycle(initialValue = null)
    val sources by db.scrobbleDao().sourceCounts().collectAsStateWithLifecycle(initialValue = emptyList())

    val accessGranted = remember(tick) { NovaListenerService.isEnabled(context) }
    val batteryExempt = remember(tick) { Watchdog.isBatteryExempt(context) }
    val heartbeatOk = remember(tick, health.lastHeartbeat) { !health.isStale() }

    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    /* ---------- Vérifications ---------- */
    val checks = remember(accessGranted, detection.listenerConnected, detection.mediaSessionAvailable, heartbeatOk, batteryExempt, plays7, health.lastHeartbeat) {
        listOf(
            HealthCheck(
                "🔔", "Accès aux notifications", accessGranted,
                if (accessGranted) "NovaStats est autorisé à lire les notifications : c'est la porte d'entrée de la détection."
                else "Sans cet accès, NovaStats ne voit rien. À activer en priorité.",
                weight = 30,
                actionLabel = "Ouvrir l'accès aux notifications",
                action = { openOrToast(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            ),
            HealthCheck(
                "🛡️", "Service de détection connecté", detection.listenerConnected,
                if (detection.listenerConnected) "Le service tourne et écoute les lectures en cours."
                else "Le service n'est pas connecté. Ouvre l'écran Paramètres ci-dessous puis relance le service.",
                weight = 20,
                actionLabel = "Relancer le service maintenant",
                action = { Watchdog.revive(context, "santé-détection") }
            ),
            HealthCheck(
                "🎧", "MediaSession disponible", detection.mediaSessionAvailable,
                if (detection.mediaSessionAvailable) "Les sessions média sont lisibles : les lectures sont captées proprement."
                else "Aucune session média pour l'instant. Lance un titre puis reviens ici.",
                weight = 10
            ),
            HealthCheck(
                "💓", "Signe de vie du service", heartbeatOk,
                if (heartbeatOk) "Dernier battement de cœur ${ServiceHealth.ago(health.lastHeartbeat)}."
                else "Aucun battement depuis ${ServiceHealth.ago(health.lastHeartbeat)} : le service est endormi ou tué par le système.",
                weight = 20
            ),
            HealthCheck(
                "🔋", "Optimisation de la batterie", batteryExempt,
                if (batteryExempt) "NovaStats est exempté : Android ne le gèle plus en arrière-plan."
                else "Android peut encore geler NovaStats. Exclus-le de l'optimisation batterie.",
                weight = 10,
                actionLabel = "Exclure NovaStats de l'optimisation",
                action = {
                    openOrToast(
                        context,
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    )
                }
            ),
            HealthCheck(
                "📈", "Activité récente", plays7 > 0,
                if (plays7 > 0) "$plays7 écoute(s) enregistrée(s) sur 7 jours."
                else "Aucune écoute sur 7 jours : soit tu n'as pas écouté de musique, soit la détection est coupée.",
                weight = 10
            )
        )
    }

    val score = checks.filter { it.ok == true }.sumOf { it.weight }
    val status = when {
        score >= 90 -> "Excellente" to Color(0xFF4ADE80)
        score >= 70 -> "Correcte" to Color(0xFFFFC107)
        score >= 45 -> "Fragile" to Color(0xFFFF9F45)
        else -> "Bloquée" to Color(0xFFFF5A5A)
    }

    /* ---------- Plan d'action HiOS ---------- */
    val man = Build.MANUFACTURER.lowercase(Locale.US)
    val brand = Build.BRAND.lowercase(Locale.US)
    val isHiOS = man.contains("tecno") || man.contains("transsion") || man.contains("infinix") || man.contains("itel") ||
        brand.contains("tecno") || brand.contains("infinix") || brand.contains("itel")

    val steps = remember(context) { planSteps(context, isHiOS) }

    var hiosOnly by remember { mutableStateOf(isHiOS) }
    val shownSteps = if (hiosOnly) steps.filter { it.emoji == "📱" || it.emoji == "🔋" || it.emoji == "🔔" || it.emoji == "🧹" || it.emoji == "🔒" } else steps

    /* ---------- Test en direct ---------- */
    var testStart by remember { mutableLongStateOf(0L) }
    var remaining by remember { mutableIntStateOf(0) }
    val testFlow = remember(testStart) { if (testStart > 0L) db.scrobbleDao().countSince(testStart) else flowOf(0) }
    val testCount by testFlow.collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(testStart) {
        if (testStart == 0L) return@LaunchedEffect
        remaining = 60
        while (remaining > 0) { delay(1000); remaining-- }
    }

    ScreenBackdrop(artUrl) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹ Paramètres", color = theme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("🩺 Santé de la détection", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
            }

            /* ---------- Héros : le score ---------- */
            Appear(delay = 0) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreDial(score, status.second)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        status.first,
                        color = status.second,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (score >= 90) "Tout est en place. NovaStats capte tes écoutes sans rien faire."
                        else "Il reste ${checks.count { it.ok == false }} point(s) à corriger pour une détection sans trou.",
                        color = theme.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            /* ---------- Signes vitaux ---------- */
            Appear(delay = 90) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VitalCard("24 h", plays24.toString(), "écoutes", theme.primary, Modifier.weight(1f))
                        VitalCard("Dernière", if (lastPlayAt == null) "—" else ServiceHealth.ago(lastPlayAt ?: 0L), "écoute captée", theme.secondary, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VitalCard("7 jours", plays7.toString(), "écoutes", theme.accent, Modifier.weight(1f))
                        VitalCard("Service", ServiceHealth.ago(health.lastHeartbeat), "dernier signe", if (heartbeatOk) Color(0xFF4ADE80) else Color(0xFFFF5A5A), Modifier.weight(1f))
                    }
                }
            }

            /* ---------- Vérifications ---------- */
            Appear(delay = 160) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    SectionTitle("🔍 Les 6 vérifications")
                    GlassCard {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            checks.forEach { c ->
                                CheckRow(c)
                                if (c !== checks.last()) Box(
                                    Modifier.fillMaxWidth().height(1.dp).padding(start = 30.dp).background(Color.White.copy(alpha = 0.08f))
                                )
                            }
                        }
                    }
                }
            }

            /* ---------- Sources détectées ---------- */
            if (sources.isNotEmpty()) {
                Appear(delay = 220) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SectionTitle("📡 Applications sources")
                        GlassCard {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "D'où viennent tes écoutes enregistrées :",
                                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                                )
                                sources.forEach { s ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(appLabel(context, s.name), color = theme.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        Text(formatCount(s.plays), color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            /* ---------- Test en direct ---------- */
            Appear(delay = 260) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    SectionTitle("🎯 Test en direct")
                    GlassCard(glow = theme.accent) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                if (testStart == 0L) "Lance un titre dans ton application de musique, puis appuie : je compte tout ce qui est détecté pendant 60 secondes."
                                else if (remaining > 0) "J'écoute… laisse jouer le titre."
                                else "Test terminé.",
                                color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CountUp(
                                    testCount,
                                    MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = (-2).sp),
                                    theme.accent
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (testStart == 0L) "écoute(s)" else if (remaining > 0) "détectée(s) · ${remaining}s" else "détectée(s)",
                                    color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { if (testStart == 0L || remaining == 0) testStart = System.currentTimeMillis() else testStart = 0L },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                            ) {
                                Text(if (testStart == 0L) "▶️ Lancer le test (60 s)" else if (remaining > 0) "⏹ Arrêter" else "🔄 Relancer le test")
                            }
                            if (testStart != 0L && remaining == 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (testCount > 0) "✅ La détection fonctionne : $testCount écoute(s) enregistrée(s)."
                                    else "❌ Rien détecté. Suis le plan d'action ci-dessous, puis relance le test.",
                                    color = if (testCount > 0) Color(0xFF4ADE80) else Color(0xFFFF5A5A),
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            /* ---------- Plan d'action HiOS ---------- */
            Appear(delay = 300) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(if (isHiOS) "📱 Plan d'action HiOS (Tecno / Phone Master)" else "📱 Plan d'action constructeur")
                        Spacer(Modifier.weight(1f))
                    }
                    if (isHiOS) {
                        Text(
                            "Ton téléphone est bien détecté comme HiOS : les étapes ci-dessous sont celles de Phone Master.",
                            color = theme.textSecondary, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                    } else {
                        TextButton(onClick = { hiosOnly = !hiosOnly }) {
                            Text(if (hiosOnly) "Voir le plan complet" else "Je suis sur Tecno / HiOS", color = theme.primary)
                        }
                    }
                    GlassCard {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            shownSteps.forEachIndexed { i, s ->
                                Column(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${i + 1}.", color = theme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.width(8.dp))
                                        Text("${s.emoji} ${s.title}", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(s.detail, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 26.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { s.action(context) },
                                        modifier = Modifier.fillMaxWidth().padding(start = 26.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                                    ) { Text(s.button) }
                                }
                            }
                        }
                    }
                }
            }

            /* ---------- Journal ---------- */
            Appear(delay = 340) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    SectionTitle("🧾 Journal du service")
                    GlassCard {
                        Column(Modifier.padding(18.dp)) {
                            val clipboard = LocalClipboardManager.current
                            if (detection.log.isEmpty()) {
                                Text(
                                    "Vide : le service n'a pas encore démarré. Active l'accès aux notifications puis relance l'app.",
                                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                detection.log.take(14).forEach {
                                    Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { clipboard.setText(AnnotatedString(detection.log.joinToString("\n"))) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                                ) { Text("📋 Copier le journal") }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Le score se recalcule tout seul : laisse cette page ouverte et corrige les points rouges un par un.",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

/* ============================== briques ============================== */

@Composable
private fun ScoreDial(score: Int, color: Color) {
    val theme = Nova.theme
    val anim by animateFloatAsState(score / 100f, tween(1400, easing = EaseOutCubic), label = "score")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(196.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.085f
            drawCircle(
                color = Color.White.copy(alpha = 0.09f),
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(stroke)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.35f), color, theme.secondary, color)),
                startAngle = -90f,
                sweepAngle = 360f * anim,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUp(
                score,
                MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 66.sp, letterSpacing = (-3).sp),
                color,
                suffix = "/100"
            )
            Text("de santé", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun VitalCard(label: String, value: String, caption: String, color: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    GlassCard(modifier = modifier, glow = color) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(caption, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CheckRow(c: HealthCheck) {
    val theme = Nova.theme
    val tint = when (c.ok) { true -> Color(0xFF4ADE80); false -> Color(0xFFFF5A5A); null -> theme.textSecondary }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(c.title, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (c.ok == true) "OK" else if (c.ok == false) "À corriger" else "—",
                color = tint, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
            )
        }
        Text(c.detail, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 30.dp, top = 3.dp))
        if (c.ok == false && c.actionLabel != null && c.action != null) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = c.action,
                modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
            ) { Text(c.actionLabel) }
        }
    }
}

private fun openOrToast(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            Toast.makeText(context, "Écran introuvable sur ce téléphone — suis le chemin indiqué.", Toast.LENGTH_LONG).show()
        }
}

private fun openPhoneMaster(context: Context) {
    val pm = context.packageManager
    val candidates = listOf(
        "com.transsion.phonemanager", "com.transsion.phoneassistant", "com.transsion.phonemaster",
        "com.hios.phonemanager", "com.hios.phoneassistant", "com.tecno.phonemanager", "com.transsion.hios.phonemanager"
    )
    for (pkg in candidates) {
        val launch = pm.getLaunchIntentForPackage(pkg)
        if (launch != null) { openOrToast(context, launch); return }
    }
    Toast.makeText(context, "Phone Master introuvable : ouvre les infos de l'application à la place.", Toast.LENGTH_LONG).show()
    openOrToast(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

/** Nom lisible d'un package source (« com.spotify.music » → « Spotify »). */
private fun appLabel(context: Context, pkg: String?): String {
    if (pkg.isNullOrBlank()) return "Inconnu"
    return runCatching {
        val ai = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(ai).toString()
    }.getOrDefault(pkg)
}

private fun planSteps(context: Context, isHiOS: Boolean): List<PlanStep> = buildList {
    add(
        PlanStep(
            "🔔", "Autoriser l'accès aux notifications",
            "Paramètres → Notifications → Accès aux notifications (ou « Notification access ») → NovaStats → Activer. C'est obligatoire : sans ça, plus rien n'est détecté.",
            "Ouvrir l'accès aux notifications"
        ) { openOrToast(it, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    )
    if (isHiOS) {
        add(
            PlanStep(
                "📱", "Phone Master → autoriser le démarrage automatique",
                "Ouvre Phone Master → Gestionnaire de démarrage automatique (Auto-start) → NovaStats → Activer. Sans ça, HiOS empêche NovaStats de se relancer tout seul après un redémarrage.",
                "Ouvrir Phone Master"
            ) { openPhoneMaster(it) }
        )
        add(
            PlanStep(
                "🔋", "Phone Master → protéger la batterie de NovaStats",
                "Phone Master → Économie d'énergie / Gestion batterie → Applications protégées (ou « Applications en veille ») → NovaStats → Autoriser. Puis Paramètres → Applications → NovaStats → Batterie → Aucune restriction.",
                "Ouvrir les réglages batterie"
            ) {
                openOrToast(
                    it,
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${it.packageName}"))
                )
            }
        )
        add(
            PlanStep(
                "🔒", "Verrouiller NovaStats dans les applications récentes",
                "Ouvre le bouton des applications récentes (carré) → fais glisser NovaStats et appuie sur le cadenas. HiOS ne nettoiera plus l'app quand il libère de la mémoire.",
                "Ouvrir les infos de l'application"
            ) { openOrToast(it, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}"))) }
        )
        add(
            PlanStep(
                "🧹", "Désactiver le nettoyage automatique pour NovaStats",
                "Phone Master → Nettoyeur / Nettoyage automatique → désactive le nettoyage en arrière-plan, ou ajoute NovaStats à la liste blanche. C'est souvent la cause n°1 des trous de détection sur HiOS.",
                "Ouvrir Phone Master"
            ) { openPhoneMaster(it) }
        )
        add(
            PlanStep(
                "🔄", "Après chaque mise à jour système, revérifier",
                "HiOS réactive parfois ses économies d'énergie après une mise à jour. Si des écoutes manquent, reviens sur cette page : le score te dira tout de suite ce qui a sauté.",
                "Ouvrir les infos de l'application"
            ) { openOrToast(it, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}"))) }
        )
    } else {
        add(
            PlanStep(
                "🔋", "Exclure NovaStats de l'optimisation batterie",
                "Paramètres → Batterie → Optimisation de la batterie → Toutes les applications → NovaStats → Ne pas optimiser. Sans ça, Android gèle l'app et la détection s'arrête en arrière-plan.",
                "Exclure de l'optimisation"
            ) { openOrToast(it, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${it.packageName}"))) }
        )
        add(
            PlanStep(
                "📱", "Autoriser le démarrage automatique",
                "Sur Xiaomi/Huawei/Oppo/Vivo/Samsung : Sécurité → Autorisations → Démarrage automatique → NovaStats → Activer. L'app doit pouvoir se relancer seule après un redémarrage.",
                "Ouvrir les infos de l'application"
            ) { openOrToast(it, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}"))) }
        )
        add(
            PlanStep(
                "🔒", "Verrouiller l'app en arrière-plan",
                "Ouvre les applications récentes → maintiens NovaStats → Verrouiller. Empêche le nettoyeur de mémoire de tuer la détection.",
                "Ouvrir les infos de l'application"
            ) { openOrToast(it, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}"))) }
        )
        add(
            PlanStep(
                "🧹", "Désactiver l'économie d'énergie agressive",
                "Si la détection s'arrête au bout de quelques minutes, cherche « Veille intelligente », « Économie d'énergie » ou « Gestion batterie » dans les paramètres et retire NovaStats.",
                "Ouvrir les infos de l'application"
            ) { openOrToast(it, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.packageName}"))) }
        )
    }
}
