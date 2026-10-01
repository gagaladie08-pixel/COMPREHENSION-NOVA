package com.novastats.app.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.novastats.app.ui.theme.NovaTheme
import kotlinx.coroutines.delay

/* ===================================== Guides par marque ===================================== */

data class GuideStep(val title: String, val instruction: String, val autoCheck: ((Context) -> Boolean)? = null, val open: (Context) -> Unit)

data class BrandGuide(val key: String, val emoji: String, val name: String, val intro: String, val warning: String?, val steps: List<GuideStep>, val success: String, val stock: Boolean = false)

object BrandGuides {
    private fun appDetails(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    private fun launch(ctx: Context, vararg candidates: Intent) {
        for (i in candidates) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return
        runCatching { ctx.startActivity(appDetails(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
    private val batteryStep = GuideStep("Optimisation batterie", "Optimisation batterie → NovaStats → « Non optimisée » ✅", { PermissionChecks.battery(it) }) { PermissionChecks.requestBattery(it) }

    val SAMSUNG = BrandGuide(
        "samsung", "🔵", "Samsung", "Samsung est connu pour endormir agressivement les apps en arrière-plan. 3 étapes et tu es libre.", null,
        listOf(
            GuideStep("Maintenance → Batterie", "⚙️ Paramètres → Maintenance de l'appareil → Batterie") { launch(it, component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) },
            batteryStep,
            GuideStep("Batterie illimitée", "Paramètres → Applications → NovaStats → Batterie → « Illimitée » ✅") { launch(it, appDetails(it)) }
        ),
        "Ton Galaxy est maintenant libéré. NovaStats ne s'arrêtera plus jamais."
    )
    val XIAOMI = BrandGuide(
        "xiaomi", "🔴", "Xiaomi / MIUI", "MIUI est le système le plus restrictif du marché. Mais on connaît ses secrets.",
        "Sans ces étapes, NovaStats sera coupé en moins de 5 min.",
        listOf(
            GuideStep("Pas de restriction", "⚙️ Paramètres → Applications → Gérer → NovaStats → Économiseur de batterie → « Pas de restriction » ✅", { PermissionChecks.battery(it) }) { launch(it, component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity").putExtra("package_name", it.packageName).putExtra("package_label", "NovaStats"), appDetails(it)) },
            GuideStep("Autostart", "Autostart (Démarrage automatique) → Activer NovaStats ✅") { launch(it, component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")) },
            GuideStep("Verrouiller dans les récentes", "Ouvre les applications récentes → maintiens NovaStats → 🔒 Verrouiller ✅") { launch(it, appDetails(it)) }
        ),
        "Tu as dompté MIUI. Peu y arrivent. NovaStats est libre."
    )
    val HUAWEI = BrandGuide(
        "huawei", "🟡", "Huawei / EMUI", "EMUI protège très fortement la batterie. Ces 2 étapes suffisent à libérer NovaStats.", null,
        listOf(
            GuideStep("Lancement des applications", "⚙️ Paramètres → Batterie → Lancement des applications → NovaStats → désactiver « Gérer automatiquement » → tout activer manuellement ✅") { launch(it, component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"), component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")) },
            batteryStep.copy(title = "Ignorer l'optimisation batterie", instruction = "Ignorer l'optimisation batterie pour NovaStats ✅")
        ),
        "EMUI n'est plus un obstacle. NovaStats est désormais immortel."
    )
    val OPPO = BrandGuide(
        "oppo", "🟢", "Oppo / ColorOS", "ColorOS est strict mais prévisible. Suis ces étapes et NovaStats sera immortel.", null,
        listOf(
            GuideStep("Fonctionnement en arrière-plan", "⚙️ Paramètres → Gestion des applications → NovaStats → Batterie → « Fonctionnement en arrière-plan » → Activé ✅", { PermissionChecks.battery(it) }) { launch(it, appDetails(it)) },
            GuideStep("Démarrage automatique", "Démarrage automatique → Activé ✅") { launch(it, component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"), component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"), component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")) }
        ),
        "ColorOS est maîtrisé. Chaque note sera comptée."
    )
    val STOCK = BrandGuide(
        "stock", "⚪", "Android Stock / Pixel", "Bonne nouvelle ! Ton téléphone est parmi les plus compatibles avec NovaStats.", null,
        listOf(GuideStep("Une seule précaution", "⚙️ Paramètres → Applications → NovaStats → Batterie → « Non restreinte » ✅", { PermissionChecks.battery(it) }) { PermissionChecks.requestBattery(it) }),
        "Ton Pixel et NovaStats sont faits pour s'entendre.", stock = true
    )
    val ALL = listOf(SAMSUNG, XIAOMI, HUAWEI, OPPO, STOCK)

    /** Détection via Build.MANUFACTURER (null = marque inconnue → guide générique + sélecteur). */
    fun detect(): BrandGuide? {
        val m = Build.MANUFACTURER.lowercase(); val b = Build.BRAND.lowercase()
        return when {
            "samsung" in m -> SAMSUNG
            "xiaomi" in m || "redmi" in b || "poco" in b -> XIAOMI
            "huawei" in m || "honor" in m -> HUAWEI
            "oppo" in m || "realme" in m || "oneplus" in m -> OPPO
            "google" in m -> STOCK
            else -> null
        }
    }
    val GENERIC = BrandGuide(
        "generic", "📱", Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, "Marque non reconnue : voici les réglages universels qui libèrent NovaStats sur la plupart des téléphones.", null,
        listOf(
            batteryStep,
            GuideStep("Batterie non restreinte", "Paramètres → Applications → NovaStats → Batterie → « Non restreinte » ✅") { launch(it, appDetails(it)) },
            GuideStep("Démarrage automatique", "Si ton téléphone propose un réglage « Démarrage automatique » ou « Autostart », active-le pour NovaStats ✅") { launch(it, appDetails(it)) }
        ),
        "Ton téléphone est prêt. NovaStats peut veiller sur chaque note."
    )
}

/**
 * 📱 Étape 3/4 — Guide constructeur. [embedded] = version intégrée à ⚙️ Paramètres (sans indicateur d'étape ni transition).
 */
@Composable
fun GuideStepScreen(audio: ObAudio, theme: NovaTheme, embedded: Boolean = false, onBack: () -> Unit = {}, onNext: (completed: Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    val detected = remember { BrandGuides.detect() }
    var manual by rememberSaveable { mutableStateOf<String?>(null) }
    val guide = manual?.let { k -> BrandGuides.ALL.firstOrNull { it.key == k } ?: BrandGuides.GENERIC } ?: detected ?: BrandGuides.GENERIC
    var done by rememberSaveable(guide.key) { mutableStateOf(setOf<Int>()) }
    var pending by rememberSaveable { mutableStateOf<Int?>(null) }
    var phase by remember { mutableIntStateOf(0) }  // hologramme : 0 trace, 1 scan, 2 logo, 3 texte
    var leaving by remember { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (embedded) { phase = 3; return@LaunchedEffect }
        delay(900); phase = 1; audio.play("scan"); audio.tap(); delay(900); phase = 2; delay(600); phase = 3
    }
    // Validation auto au retour des paramètres
    LifecycleResumeEffect(guide.key) {
        val auto = guide.steps.indices.filter { guide.steps[it].autoCheck?.invoke(ctx) == true }.toSet()
        val p = pending
        val newDone = done + auto + (if (p != null) setOf(p) else emptySet())
        if (newDone.size > done.size) { audio.play("chime"); audio.tap() } else if (p != null) audio.play("low", 0.4f)
        done = newDone; pending = null
        onPauseOrDispose { }
    }
    val complete = done.size >= guide.steps.size
    LaunchedEffect(complete) { if (complete && !celebrate) { celebrate = true; if (!embedded || done.isNotEmpty()) { audio.play("chord"); audio.triplePulse() } } }

    val fill by animateFloatAsState(if (leaving) 1f else 0f, tween(400), label = "fill")
    Box(Modifier.fillMaxSize().background(theme.background)) {
        if (!embedded) ParticleField(Modifier.fillMaxSize(), listOf(theme.primary, ObColors.Gold), mode = if (complete) ParticleMode.BURST else ParticleMode.FLOAT, emitting = true, intensity = if (complete) 0.8f else 0.2f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!embedded) {
                StepIndicator(3, theme.primary, theme.text)
                Spacer(Modifier.height(12.dp))
                CinzelTitle("Ton téléphone a ses secrets", theme.primary, size = 22, letterSpacing = 2, modifier = Modifier.padding(horizontal = 24.dp))
            } else Spacer(Modifier.height(8.dp))

            HologramPhone(theme, phase, done.size, guide.steps.size, guide.emoji, Modifier.size(150.dp, 190.dp).padding(top = 8.dp))
            if (phase >= 3) TypewriterText("✦ ${guide.name.uppercase()} DÉTECTÉ ✦", charDelayMs = if (embedded) 0 else 40) { t ->
                Text(t, color = ObColors.Gold, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 15.sp)
            }
            if (detected == null || manual != null) Row(Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (BrandGuides.ALL + BrandGuides.GENERIC).forEach { g -> FilterChip(selected = guide.key == g.key, onClick = { manual = g.key }, label = { Text("${g.emoji} ${if (g.key == "generic") "Autre" else g.name.substringBefore(" /")}", fontSize = 12.sp) }) }
            }
            PoeticText(guide.intro, theme.textSecondary, 15, Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            guide.warning?.let { Text("⚠️ IMPORTANT : $it", color = ObColors.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp)) }
            if (guide.stock) Text("✅ Android Stock respecte les applications en arrière-plan. NovaStats sera parfaitement stable sur ton appareil.", color = ObColors.Green, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))

            // Barre de progression des étapes
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                guide.steps.indices.forEach { i ->
                    val isDone = i in done
                    val active = !isDone && (done.size == i)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ÉTAPE ${i + 1}", color = if (isDone) ObColors.Green else if (active) theme.primary else theme.textSecondary, fontFamily = ObFonts.rajdhani, fontSize = 11.sp, letterSpacing = 1.sp)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (isDone) ObColors.Green else if (active) theme.primary.copy(alpha = 0.5f) else theme.textSecondary.copy(alpha = 0.25f)))
                    }
                }
            }
            guide.steps.forEachIndexed { i, step -> GuideStepCard(theme, i, step, i in done, pending == i) { pending = i; audio.tap(); step.open(ctx) } }

            AnimatedVisibility(visible = complete, enter = fadeIn(tween(600)) + scaleIn(tween(600), initialScale = 0.8f)) {
                Box(Modifier.padding(20.dp).pulsingGlow(ObColors.Gold, 0.2f, 0.6f)) { PoeticText(guide.success, ObColors.Gold, 18, Modifier.padding(horizontal = 12.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            if (!embedded) {
                ObButton(if (complete) "Continuer" else "Je le ferai plus tard", listOf(theme.primary, theme.secondary, theme.glowSecondary), enabled = !leaving, big = complete) {
                    leaving = true; audio.play(if (complete) "boom" else "whoosh", if (complete) 1f else 0.5f); if (complete) audio.impact() else audio.tap()
                }
                if (!complete) Text("Tu pourras finir ça dans ⚙️ → Guide constructeur", color = theme.textSecondary.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)) { Text("← Retour", color = theme.textSecondary) }
            } else Spacer(Modifier.height(24.dp))
        }
        if (fill > 0f) Box(Modifier.fillMaxSize().alpha(fill).background(if (complete) theme.primary else theme.background))
    }
    LaunchedEffect(leaving) { if (leaving) { delay(if (complete) 700 else 350); onNext(complete) } }
}

@Composable
private fun GuideStepCard(theme: NovaTheme, index: Int, step: GuideStep, done: Boolean, pending: Boolean, onOpen: () -> Unit) {
    val finger by rememberInfiniteTransition(label = "f").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "fa")
    var zoom by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (zoom) 1.03f else 1f, tween(200), label = "z")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).scale(sc).clip(RoundedCornerShape(14.dp)).background(theme.surface)
            .border(1.dp, if (done) ObColors.Green else if (zoom) theme.primary else theme.textSecondary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable { zoom = !zoom }.padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ÉTAPE ${index + 1}", color = if (done) ObColors.Green else theme.primary, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (done) Text("✅", fontSize = 18.sp) else if (pending) Text("⏳", fontSize = 16.sp)
        }
        Text(step.title, color = theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        Text(step.instruction, color = theme.textSecondary, fontFamily = ObFonts.raleway, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        // Illustration animée : chemin lumineux qui se trace + doigt qui se déplace (loop 4 s)
        Canvas(Modifier.fillMaxWidth().height(if (zoom) 56.dp else 34.dp).padding(top = 8.dp)) {
            val pts = listOf(Offset(0f, size.height * 0.5f), Offset(size.width * 0.33f, size.height * 0.2f), Offset(size.width * 0.66f, size.height * 0.8f), Offset(size.width, size.height * 0.5f))
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            val pm = PathMeasure().apply { setPath(path, false) }
            val sub = Path(); pm.getSegment(0f, pm.length * finger, sub, true)
            drawPath(path, theme.textSecondary.copy(alpha = 0.15f), style = Stroke(3f))
            drawPath(sub, Brush.linearGradient(listOf(theme.primary, theme.secondary)), style = Stroke(3f))
            pts.forEach { drawCircle(theme.primary.copy(alpha = 0.5f), 4f, it) }
            val pos = pm.getPosition(pm.length * finger)
            drawCircle(ObColors.Gold.copy(alpha = 0.35f), 12f, pos); drawCircle(Color.White, 5f, pos)
        }
        if (!done) Box(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))).clickable { onOpen() }.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text("📱 OUVRIR LES PARAMÈTRES", color = Color.White, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        }
    }
}

/** Silhouette de téléphone holographique : trace → scan → logo → réagit au nombre d'étapes validées. */
@Composable
private fun HologramPhone(theme: NovaTheme, phase: Int, done: Int, total: Int, brandEmoji: String, modifier: Modifier) {
    val trace by animateFloatAsState(if (phase >= 0) 1f else 0f, tween(900), label = "tr")
    val t by rememberInfiniteTransition(label = "hp").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "hpa")
    val ratio = if (total == 0) 1f else done.toFloat() / total
    val screenColor = when { ratio >= 1f -> ObColors.Gold; ratio > 0f -> theme.primary; else -> ObColors.OrbOff }
    val glow = if (ratio >= 1f) 0.9f else 0.25f + 0.5f * ratio
    Box(modifier.pulsingGlow(screenColor, glow * 0.4f, glow, if (ratio >= 1f) 600 else 2000, 30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val body = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.1f, h * 0.05f, w * 0.9f, h * 0.95f, CornerRadius(w * 0.1f))) }
            val pm = PathMeasure().apply { setPath(body, false) }
            val seg = Path(); pm.getSegment(0f, pm.length * trace, seg, true)
            drawPath(seg, theme.primary.copy(alpha = 0.9f), style = Stroke(3f))
            drawRoundRect(screenColor.copy(alpha = 0.15f + 0.35f * ratio), Offset(w * 0.16f, h * 0.12f), Size(w * 0.68f, h * 0.74f), CornerRadius(w * 0.04f))
            if (phase == 1) { val y = h * 0.1f + (h * 0.8f) * t; drawRect(Brush.verticalGradient(listOf(Color.Transparent, theme.accent, Color.Transparent), startY = y - 14f, endY = y + 14f), Offset(w * 0.12f, y - 14f), Size(w * 0.76f, 28f)) }
            if (ratio in 0.01f..0.99f) drawCircle(theme.primary.copy(alpha = 0.6f * (1f - t)), w * 0.15f + w * 0.3f * t, Offset(w / 2, h / 2), style = Stroke(2f))
            if (ratio <= 0f) drawCircle(theme.textSecondary.copy(alpha = 0.3f * (1f - t)), w * 0.1f * t, Offset(w / 2, h * 0.9f), style = Stroke(1.5f))
            drawCircle(theme.primary, 3f, Offset(w / 2, h * 0.92f))
        }
        if (phase >= 2) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(brandEmoji, fontSize = 26.sp)
            if (ratio > 0f) Text(if (ratio >= 1f) "💥" else "NOVA", color = screenColor, fontFamily = ObFonts.cinzel, fontWeight = FontWeight.Bold, fontSize = if (ratio >= 1f) 30.sp else 13.sp, letterSpacing = 2.sp)
        }
    }
}
