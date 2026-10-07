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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.Icons
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
 * Étape 3/4 — Guide constructeur : liste « Réglages » des étapes propres à la marque (détectée, ou choisie),
 * coche verte dès qu'une étape est validée (vérification automatique au retour quand c'est possible).
 * [embedded] = version Réglages (sans en-tête d'étape ni boutons).
 */
@Composable
fun GuideStepScreen(audio: ObAudio, theme: NovaTheme, embedded: Boolean = false, onBack: () -> Unit = {}, onNext: (completed: Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    val detected = remember { BrandGuides.detect() }
    var manual by rememberSaveable { mutableStateOf<String?>(null) }
    val guide = manual?.let { k -> BrandGuides.ALL.firstOrNull { it.key == k } ?: BrandGuides.GENERIC } ?: detected ?: BrandGuides.GENERIC
    var done by rememberSaveable(guide.key) { mutableStateOf(setOf<Int>()) }
    var pending by rememberSaveable { mutableStateOf<Int?>(null) }
    var celebrated by remember { mutableStateOf(false) }
    val beats = if (embedded) 4 else rememberBeats(4, startMs = 150, stepMs = 140)
    val accent = theme.primary

    LifecycleResumeEffect(guide.key) {
        val auto = guide.steps.indices.filter { guide.steps[it].autoCheck?.invoke(ctx) == true }.toSet()
        val p = pending
        val newDone = done + auto + (if (p != null) setOf(p) else emptySet())
        if (newDone.size > done.size) { audio.tick(); audio.success() }
        done = newDone; pending = null
        onPauseOrDispose { }
    }
    val complete = done.size >= guide.steps.size
    LaunchedEffect(complete) { if (complete && !celebrated) { celebrated = true; if (!embedded) { audio.tick(); audio.success() } } }

    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            if (!embedded) {
                Appear(beats >= 1) { ObEyebrow("Ton téléphone", accent) }
                Spacer(Modifier.height(6.dp))
                Appear(beats >= 1) { ObHeadline(guide.name.substringBefore(" /")) }
                Spacer(Modifier.height(12.dp))
            }
            Appear(beats >= 2) { ObSub(guide.intro, size = 15) }
            guide.warning?.let { Spacer(Modifier.height(10.dp)); Appear(beats >= 2) { Text(it, color = ObColors.Orange, fontFamily = ObFonts.inter, fontSize = 13.sp, lineHeight = 18.sp) } }
            if (guide.stock) { Spacer(Modifier.height(10.dp)); Appear(beats >= 2) { Text("Android stock respecte les applications en arrière-plan : NovaStats sera stable sur ton appareil.", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 13.sp, lineHeight = 18.sp) } }
            if (detected == null || manual != null || embedded) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (BrandGuides.ALL + BrandGuides.GENERIC).forEach { g ->
                        val on = guide.key == g.key
                        Text(
                            if (g.key == "generic") "Autre" else g.name.substringBefore(" /"), color = if (on) ObColors.Black else ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.clip(CircleShape).background(if (on) ObColors.Text else ObColors.Surface2).clickable { manual = g.key }.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Appear(beats >= 3) {
                ObCard {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Étapes", color = ObColors.Gray, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        ObTag("${done.size} / ${guide.steps.size}", if (complete) ObColors.Green else accent)
                    }
                    guide.steps.forEachIndexed { i, step ->
                        val isDone = i in done
                        Column {
                            ObRow(Icons.Rounded.Tune, if (isDone) ObColors.Green.copy(alpha = 0.9f) else accent, step.title, step.instruction, trailing = {
                                if (isDone) ObCheck(true) else ObMiniButton("Ouvrir", ObColors.Text) { pending = i; audio.tap(); step.open(ctx) }
                            })
                            if (pending == i && !isDone) Text("En attente de ton retour…", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 12.sp, modifier = Modifier.padding(start = 68.dp, bottom = 10.dp))
                            if (i != guide.steps.lastIndex) ObDivider()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            AnimatedVisibility(visible = complete, enter = fadeIn(tween(400)) + expandVertically(tween(450, easing = ObEasing))) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ObCheck(true, 22.dp); Spacer(Modifier.width(10.dp))
                    Text(guide.success, color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize().background(theme.background)) { Column(Modifier.fillMaxSize().padding(top = 8.dp)) { body() } }
    } else ObStage(base = theme.background, accent = accent) {
        ObLayout(step = 3, accent = accent, bottom = {
            Appear(beats >= 4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ObPrimaryButton(if (complete) "Continuer" else "Continuer sans finir", fill = if (complete) accent else ObColors.Text) { audio.tap(); audio.whoosh(); onNext(complete) }
                    Text(if (complete) "Ton téléphone laissera NovaStats tranquille." else "Tu pourras finir dans Réglages → Guide constructeur.", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    ObLinkButton("Retour", ObColors.Gray) { onBack() }
                }
            }
        }, content = body)
    }
}
