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
import androidx.compose.animation.expandVertically
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
 * Étape 3/4 — Guide constructeur en checklist : silhouette de téléphone monochrome, une étape visible à la fois,
 * ligne verticale qui s'illumine. [embedded] = version Réglages (sans en-tête d'étape ni bouton).
 */
@Composable
fun GuideStepScreen(audio: ObAudio, theme: NovaTheme, embedded: Boolean = false, onBack: () -> Unit = {}, onNext: (completed: Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    val detected = remember { BrandGuides.detect() }
    var manual by rememberSaveable { mutableStateOf<String?>(null) }
    val guide = manual?.let { k -> BrandGuides.ALL.firstOrNull { it.key == k } ?: BrandGuides.GENERIC } ?: detected ?: BrandGuides.GENERIC
    var done by rememberSaveable(guide.key) { mutableStateOf(setOf<Int>()) }
    var pending by rememberSaveable { mutableStateOf<Int?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var celebrated by remember { mutableStateOf(false) }
    var intro by remember { mutableStateOf(embedded) }
    LaunchedEffect(Unit) { if (!embedded) { delay(500); intro = true; audio.whoosh(0.12f) } }

    LifecycleResumeEffect(guide.key) {
        val auto = guide.steps.indices.filter { guide.steps[it].autoCheck?.invoke(ctx) == true }.toSet()
        val p = pending
        val newDone = done + auto + (if (p != null) setOf(p) else emptySet())
        if (newDone.size > done.size) { audio.tick(); audio.success() }
        done = newDone; pending = null
        onPauseOrDispose { }
    }
    val complete = done.size >= guide.steps.size
    LaunchedEffect(complete) { if (complete && !celebrated) { celebrated = true; if (!embedded || done.isNotEmpty()) { audio.tick(); audio.success() } } }
    val fill by animateFloatAsState(if (leaving) 1f else 0f, tween(700, easing = ObEasing), label = "fill")
    LaunchedEffect(leaving) { if (leaving) { delay(750); onNext(complete) } }
    val accent = theme.primary

    Box(Modifier.fillMaxSize().background(theme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!embedded) {
                StepIndicator(3, accent, theme.text)
                Spacer(Modifier.height(30.dp))
                Text("TON TÉLÉPHONE", color = theme.textSecondary, fontFamily = ObFonts.body, fontSize = 11.sp, letterSpacing = 5.sp)
                Spacer(Modifier.height(8.dp))
                CinzelTitle(guide.name.substringBefore(" /"), theme.text, size = 26, letterSpacing = 1, modifier = Modifier.padding(horizontal = 28.dp))
            } else Spacer(Modifier.height(10.dp))

            RiseIn(visible = intro) { PhoneSilhouette(theme, done.size, guide.steps.size, Modifier.size(120.dp, 150.dp).padding(top = 14.dp)) }
            if (detected == null || manual != null) Row(Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (BrandGuides.ALL + BrandGuides.GENERIC).forEach { g ->
                    val on = guide.key == g.key
                    Text(
                        if (g.key == "generic") "AUTRE" else g.name.substringBefore(" /").uppercase(), color = if (on) theme.background else theme.text, fontFamily = ObFonts.body, fontSize = 10.sp, letterSpacing = 2.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else theme.surface).clickable { manual = g.key }.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            RiseIn(visible = intro, delayMs = 200) { PoeticText(guide.intro, theme.textSecondary, 14, Modifier.padding(horizontal = 36.dp, vertical = 10.dp)) }
            guide.warning?.let { Text(it, color = ObColors.Red, fontFamily = ObFonts.body, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 36.dp, end = 36.dp, bottom = 6.dp)) }
            if (guide.stock) Text("Android stock respecte les applications en arrière-plan : NovaStats sera stable sur ton appareil.", color = theme.textSecondary, fontFamily = ObFonts.body, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 36.dp, end = 36.dp, bottom = 6.dp))

            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                guide.steps.forEachIndexed { i, step ->
                    val isDone = i in done
                    val active = !isDone && done.size == i
                    ChecklistItem(
                        theme = theme, index = i, last = i == guide.steps.lastIndex, active = active || (!isDone && pending == i), done = isDone, required = true,
                        title = step.title, body = step.instruction, help = if (pending == i) "En attente de ton retour des paramètres…" else null,
                        buttonLabel = "Ouvrir les paramètres", onAction = { pending = i; audio.tap(); step.open(ctx) }
                    )
                }
            }
            AnimatedVisibility(visible = complete, enter = fadeIn(tween(700)) + expandVertically(tween(700, easing = ObEasing))) {
                PoeticText(guide.success, accent, 15, Modifier.padding(horizontal = 36.dp, vertical = 18.dp))
            }
            Spacer(Modifier.height(16.dp))
            if (!embedded) {
                ObButton(if (complete) "Continuer" else "Je le ferai plus tard", listOf(accent), enabled = !leaving, ghost = !complete) { leaving = true; audio.whoosh(); audio.tap() }
                if (!complete) Text("Tu pourras finir dans Réglages → Guide constructeur", color = theme.textSecondary.copy(alpha = 0.7f), fontFamily = ObFonts.body, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 6.dp, bottom = 32.dp)) { Text("Retour", color = theme.textSecondary, fontFamily = ObFonts.body, letterSpacing = 2.sp, fontSize = 12.sp) }
            } else Spacer(Modifier.height(24.dp))
        }
        if (fill > 0f) Box(Modifier.fillMaxSize().alpha(fill).background(Color.Black))
    }
}

/** Silhouette de téléphone monochrome ; l'écran s'éclaire avec la proportion d'étapes validées. */
@Composable
private fun PhoneSilhouette(theme: NovaTheme, done: Int, total: Int, modifier: Modifier) {
    val ratio by animateFloatAsState(if (total == 0) 1f else done.toFloat() / total, tween(900, easing = ObEasing), label = "ratio")
    Box(modifier.breathingGlow(theme.primary, 0.05f + 0.15f * ratio, 0.12f + 0.25f * ratio, 3600, 40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRoundRect(theme.text.copy(alpha = 0.55f), Offset(w * 0.1f, h * 0.05f), Size(w * 0.8f, h * 0.9f), CornerRadius(w * 0.1f), style = Stroke(1.5.dp.toPx()))
            drawRoundRect(theme.primary.copy(alpha = 0.08f + 0.5f * ratio), Offset(w * 0.17f, h * 0.12f), Size(w * 0.66f, h * 0.72f), CornerRadius(w * 0.04f))
            drawCircle(theme.text.copy(alpha = 0.5f), 2.5f, Offset(w / 2, h * 0.915f))
        }
        Text(if (ratio >= 0.999f) "✓" else "${(ratio * 100).toInt()} %", color = theme.text, fontFamily = ObFonts.display, fontSize = if (ratio >= 0.999f) 28.sp else 14.sp, letterSpacing = 1.sp)
    }
}
