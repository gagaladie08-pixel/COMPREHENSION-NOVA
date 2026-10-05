package com.novastats.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.NovaStatsApp
import com.novastats.app.domain.CertLevel
import com.novastats.app.ui.theme.CUSTOM_THEME_ID
import com.novastats.app.ui.theme.CurveDeco
import com.novastats.app.ui.theme.CurveShape
import com.novastats.app.ui.theme.CustomThemeSpec
import com.novastats.app.ui.theme.CustomThemeStore
import com.novastats.app.ui.theme.IconStyle
import com.novastats.app.ui.theme.MotionEasing
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaStatsTheme
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.NovaThemes
import com.novastats.app.ui.theme.Signature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* ==================================================================== */
/*  ✨ Mon thème — chaque élément est indépendant et combinable           */
/* ==================================================================== */

private data class ThemePreset(
    val label: String, val primary: Float, val secondary: Float, val accent: Float,
    val sat: Float, val backgroundHue: Float, val dark: Boolean = true, val signature: Signature = Signature.GOLD_SHIMMER
)

private val PRESETS = listOf(
    ThemePreset("🌌 Néon", 320f, 200f, 175f, 0.88f, 320f, signature = Signature.SCANLINES_GLITCH),
    ThemePreset("🌅 Coucher", 18f, 340f, 45f, 0.85f, 350f, signature = Signature.WARM_PULSE),
    ThemePreset("🌊 Océan", 200f, 230f, 160f, 0.80f, 210f, signature = Signature.CLOUD_FADE),
    ThemePreset("🌿 Forêt", 140f, 95f, 60f, 0.70f, 150f, signature = Signature.SPARKLES),
    ThemePreset("🍷 Rubis", 350f, 10f, 300f, 0.85f, 340f, signature = Signature.GOLD_SHIMMER),
    ThemePreset("🍬 Pastel", 320f, 190f, 45f, 0.55f, 320f, dark = false, signature = Signature.RISING_BUBBLES),
    ThemePreset("🖤 Myrtille", 265f, 220f, 300f, 0.75f, 260f, signature = Signature.CHROME_SWEEP),
    ThemePreset("🥂 Champagne", 45f, 20f, 55f, 0.70f, 30f, signature = Signature.GOLD_SHIMMER),
    ThemePreset("🩸 Sang", 0f, 350f, 20f, 0.90f, 355f, signature = Signature.JUMP_CUT),
    ThemePreset("🧊 Glacier", 190f, 215f, 175f, 0.65f, 200f, signature = Signature.CLOUD_FADE),
    ThemePreset("🌸 Sakura", 335f, 300f, 20f, 0.72f, 330f, signature = Signature.CONFETTI),
    ThemePreset("🥑 Sauge", 90f, 150f, 45f, 0.55f, 120f, signature = Signature.SPARKLES)
)

private val TITLE_FONTS = listOf(
    "Orbitron", "Audiowide", "Bebas Neue", "Anton", "Bungee", "Cinzel", "Playfair Display",
    "Righteous", "Abril Fatface", "DM Serif Display", "Fraunces", "Syne", "Montserrat", "Baloo 2"
)

private val BODY_FONTS = listOf(
    "Rajdhani", "Poppins", "Montserrat", "Oswald", "Inter", "Nunito", "Quicksand",
    "Ubuntu", "Fredoka", "Baloo 2", "Cormorant", "Archivo Black", "Unica One"
)

private val SIGNATURES = listOf(
    Signature.GOLD_SHIMMER to "Shimmer doré", Signature.SCANLINES_GLITCH to "Scanlines",
    Signature.CHROME_SWEEP to "Reflets chromés", Signature.RISING_BUBBLES to "Bulles",
    Signature.SPARKLES to "Paillettes", Signature.WARM_PULSE to "Glow chaud",
    Signature.CLOUD_FADE to "Fondu nuage", Signature.CONFETTI to "Confettis",
    Signature.JUMP_CUT to "Jump-cut", Signature.CURTAIN to "Rideau",
    Signature.BW_FLASH to "Flash N&B", Signature.BLUE_FLASH to "Flash bleu",
    Signature.PATTERN_REVEAL to "Motifs", Signature.DUAL_CONTRAST to "Double contraste",
    Signature.RANDOM_GLITCH to "Glitch aléatoire"
)

private val EASINGS = listOf(
    MotionEasing.EASE_OUT to "Rapide", MotionEasing.EASE_IN_OUT to "Doux",
    MotionEasing.EASE_IN_OUT_BACK to "Rebond", MotionEasing.EASE_OUT_BOUNCE to "Bubblegum",
    MotionEasing.EASE_OUT_EXPO to "Sec & net", MotionEasing.CUT to "Cuts secs",
    MotionEasing.RANDOM to "Imprévisible"
)

private val ICON_STYLES = listOf(
    IconStyle.HUD to "HUD", IconStyle.CHROME_ROUND to "Chromé", IconStyle.RAW to "Brut",
    IconStyle.ROYAL to "Royal", IconStyle.BUBBLE to "Bulle", IconStyle.STAGE to "Scène",
    IconStyle.SPIKY to "Coins coupés", IconStyle.CLOUD to "Nuage", IconStyle.SUN to "Soleil",
    IconStyle.METAL to "Métal", IconStyle.CANDY to "Bonbon", IconStyle.POLAROID to "Polaroid",
    IconStyle.GEOMETRIC to "Géométrique", IconStyle.DUAL to "Double"
)

private val CHART_SHAPES = listOf(
    CurveShape.SMOOTH to "Lissé", CurveShape.ANGULAR to "Anguleux",
    CurveShape.VERY_ROUND to "Très arrondi", CurveShape.IRREGULAR to "Irrégulier"
)

private val CHART_DECOS = listOf(
    CurveDeco.NONE to "Aucune", CurveDeco.BLINK_DOTS to "Points clignotants",
    CurveDeco.STARS to "Étoiles", CurveDeco.DISCO_DOTS to "Points disco"
)

private val EMOJIS = listOf("✨", "🌌", "🔥", "🌊", "🍇", "🌙", "💜", "⚡", "🦋", "🎧")

@Composable
fun CustomThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val scope = rememberCoroutineScope()
    val theme = Nova.theme

    var spec by remember { mutableStateOf(CustomThemeStore.load(context) ?: CustomThemeSpec()) }
    val candidate = remember(spec) { spec.toTheme() }
    val existing = remember { CustomThemeStore.load(context) != null }
    var previewing by remember { mutableStateOf(false) }

    val artUrl by produceState<String?>(null) {
        value = runCatching { app.database.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    fun create() {
        CustomThemeStore.save(context, spec)
        NovaThemes.customTheme = spec.toTheme()
        scope.launch { app.settings.setTheme(CUSTOM_THEME_ID) }
    }

    // 👁️ Aperçu plein écran : rien n'est enregistré tant que tu n'as pas validé
    if (previewing) {
        BackHandler { previewing = false }
        ThemePreviewGate(spec = spec, candidate = candidate, current = theme, onCreate = { create(); previewing = false }, onEdit = { previewing = false })
        return
    }

    ScreenBackdrop(artUrl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Paramètres", color = theme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("✨ Mon thème", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
            }

            Appear(delay = 0) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Chaque bloc est indépendant : combine une palette, un fond, des formes, des polices et un effet différents. L'aperçu final te montre le résultat avant de valider.",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    MiniPreview(candidate)
                }
            }

            /* ---------- Palettes ---------- */
            Appear(delay = 50) {
                EditorSection("🎨 Palettes de départ", "12 ambiances — choisis-en une puis ajuste chaque détail") {
                    PRESETS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { p ->
                                PresetChip(p, Modifier.weight(1f)) {
                                    spec = spec.copy(
                                        primaryHue = p.primary, secondaryHue = p.secondary, accentHue = p.accent,
                                        saturation = p.sat, backgroundHue = p.backgroundHue, dark = p.dark, signature = p.signature
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            /* ---------- Couleurs ---------- */
            Appear(delay = 90) {
                EditorSection("🌈 Couleurs", "Teintes principales, intensité et luminosité") {
                    HueSlider("Couleur principale", spec.primaryHue, candidate.primary) { spec = spec.copy(primaryHue = it) }
                    HueSlider("Couleur secondaire", spec.secondaryHue, candidate.secondary) { spec = spec.copy(secondaryHue = it) }
                    HueSlider("Couleur d'accent", spec.accentHue, candidate.accent) { spec = spec.copy(accentHue = it) }
                    SimpleSlider("Intensité", spec.saturation, 0.25f, 1f, candidate.primary) { spec = spec.copy(saturation = it) }
                    SimpleSlider("Luminosité", spec.lightness, 0.35f, 0.80f, candidate.accent) { spec = spec.copy(lightness = it) }
                }
            }

            /* ---------- Fond & surfaces ---------- */
            Appear(delay = 120) {
                EditorSection("🖤 Fond & surfaces", "Le fond a sa propre teinte, indépendante des couleurs") {
                    HueSlider("Teinte du fond", spec.backgroundHue, candidate.background) { spec = spec.copy(backgroundHue = it) }
                    SimpleSlider("Profondeur du fond", spec.backgroundLevel, 0f, 1f, candidate.textSecondary) { spec = spec.copy(backgroundLevel = it) }
                    SimpleSlider("Relief des cartes", spec.surfaceLevel, 0f, 1f, candidate.surface) { spec = spec.copy(surfaceLevel = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceChip("🌙 Sombre", spec.dark, Modifier.weight(1f)) { spec = spec.copy(dark = true) }
                        ChoiceChip("☀️ Clair", !spec.dark, Modifier.weight(1f)) { spec = spec.copy(dark = false) }
                    }
                    ChoiceChip("🌈 Texte secondaire arc-en-ciel", spec.rainbowTextSecondary, Modifier.fillMaxWidth()) { spec = spec.copy(rainbowTextSecondary = !spec.rainbowTextSecondary) }
                }
            }

            /* ---------- Formes ---------- */
            Appear(delay = 150) {
                EditorSection("🧩 Formes", "Arrondi des cartes et style des icônes") {
                    SimpleSlider("Arrondi des cartes", spec.cornerDp.toFloat(), 0f, 28f, candidate.secondary) { spec = spec.copy(cornerDp = it.toInt()) }
                    Text(
                        "${spec.cornerDp} dp — ${if (spec.cornerDp == 0) "angles vifs" else if (spec.cornerDp >= 24) "très arrondi" else "arrondi moyen"}",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                    )
                    ICON_STYLES.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (style, label) ->
                                ChoiceChip(label, spec.icons == style, Modifier.weight(1f)) { spec = spec.copy(icons = style) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            /* ---------- Typographie ---------- */
            Appear(delay = 180) {
                EditorSection("🔤 Typographie", "Police des titres et du corps, à combiner librement") {
                    Text("Titres", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    TITLE_FONTS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { f -> ChoiceChip(f, spec.titleFont == f, Modifier.weight(1f)) { spec = spec.copy(titleFont = f) } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Corps de texte", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    BODY_FONTS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { f -> ChoiceChip(f, spec.bodyFont == f, Modifier.weight(1f)) { spec = spec.copy(bodyFont = f) } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            /* ---------- Mouvement ---------- */
            Appear(delay = 210) {
                EditorSection("🎞️ Mouvement", "Durée et style des transitions") {
                    SimpleSlider("Durée", spec.transitionMs.toFloat(), 120f, 600f, candidate.primary) { spec = spec.copy(transitionMs = (it / 20).toInt() * 20) }
                    Text("${spec.transitionMs} ms", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    EASINGS.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (e, label) -> ChoiceChip(label, spec.easing == e, Modifier.weight(1f)) { spec = spec.copy(easing = e) } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            /* ---------- Effet signature ---------- */
            Appear(delay = 240) {
                EditorSection("✨ Effet signature", "L'animation propre à ton thème") {
                    SIGNATURES.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (sig, label) -> ChoiceChip(label, spec.signature == sig, Modifier.weight(1f)) { spec = spec.copy(signature = sig) } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            /* ---------- Graphiques ---------- */
            Appear(delay = 270) {
                EditorSection("📈 Courbes & graphiques", "Forme du tracé, décoration et glow") {
                    CHART_SHAPES.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (s, label) -> ChoiceChip(label, spec.chartShape == s, Modifier.weight(1f)) { spec = spec.copy(chartShape = s) } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    CHART_DECOS.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (d, label) -> ChoiceChip(label, spec.chartDeco == d, Modifier.weight(1f)) { spec = spec.copy(chartDeco = d) } }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    SimpleSlider("Glow des courbes", spec.chartGlowDp, 0f, 16f, candidate.accent) { spec = spec.copy(chartGlowDp = it) }
                }
            }

            /* ---------- Identité ---------- */
            Appear(delay = 300) {
                EditorSection("🏷️ Nom & emblème") {
                    OutlinedTextField(
                        value = spec.name, onValueChange = { spec = spec.copy(name = it.take(18)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nom du thème", color = theme.textSecondary) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                            cursorColor = theme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        EMOJIS.forEach { e ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (spec.emoji == e) candidate.primary else candidate.surface)
                                    .clickable { spec = spec.copy(emoji = e) },
                                contentAlignment = Alignment.Center
                            ) { Text(e, fontSize = 17.sp) }
                        }
                    }
                }
            }

            /* ---------- Actions ---------- */
            Appear(delay = 330) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { previewing = true }, modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.background)
                    ) {
                        Text("👁️ Voir l'aperçu complet", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                    Text(
                        "Rien n'est créé tant que tu n'as pas validé dans l'aperçu.",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existing) {
                        Button(
                            onClick = { create() }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                        ) { Text("⚡ Enregistrer directement") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { spec = CustomThemeSpec() }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                        ) { Text("🔄 Réinitialiser") }
                        Button(
                            onClick = {
                                CustomThemeStore.clear(context)
                                NovaThemes.customTheme = null
                                spec = CustomThemeSpec()
                                scope.launch { app.settings.setTheme(NovaThemes.DEFAULT.id) }
                            }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                        ) { Text("🗑️ Supprimer") }
                    }
                }
            }
        }
    }
}

/* ============================== aperçu final ============================== */

@Composable
private fun ThemePreviewGate(
    spec: CustomThemeSpec,
    candidate: NovaTheme,
    current: NovaTheme,
    onCreate: () -> Unit,
    onEdit: () -> Unit
) {
    var after by remember { mutableStateOf(true) }
    val shown = if (after) candidate else current
    NovaStatsTheme(theme = shown) {
        val theme = Nova.theme
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.background, theme.surface)))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onEdit) { Text("✏️ Modifier", color = theme.primary, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (after) "👁️ Ton thème" else "👁️ Thème actuel",
                        color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${shown.emoji} ${shown.name} · ${shown.titleFont} / ${shown.bodyFont} · ${shown.transitionLabel}",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceChip("👁️ Après", after, Modifier.weight(1f)) { after = true }
                        ChoiceChip("📱 Avant", !after, Modifier.weight(1f)) { after = false }
                    }
                }

                Spacer(Modifier.height(14.dp))
                PreviewHero(spec)
                Spacer(Modifier.height(14.dp))
                PreviewRanking()
                Spacer(Modifier.height(14.dp))
                PreviewPopup()
                Spacer(Modifier.height(14.dp))
                PreviewChart()
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.background)
                ) { Text("✅ Créer mon thème", fontWeight = FontWeight.Black, fontSize = 17.sp) }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.surface, contentColor = theme.text)
                ) { Text("✏️ Continuer à modifier") }
            }
        }
    }
}

@Composable
private fun PreviewHero(spec: CustomThemeSpec) {
    val theme = Nova.theme
    Column(Modifier.padding(horizontal = 16.dp)) {
        GlassCard {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${spec.emoji} ${spec.name}", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("NOVASTATS", color = theme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(14.dp))
                CountUp(
                    1284,
                    MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 46.sp, letterSpacing = (-2).sp),
                    theme.primary
                )
                Text("écoutes cette semaine", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewPill("🥇 Or", theme.primary)
                    PreviewPill("🔥 Série 12 j", theme.secondary)
                    PreviewPill("💎 ×2", theme.accent)
                }
            }
        }
    }
}

@Composable
private fun PreviewRanking() {
    val theme = Nova.theme
    Column(Modifier.padding(horizontal = 16.dp)) {
        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("BOOMPALA" to "137", "Supernova" to "96", "Golden Hour" to "74").forEachIndexed { i, (t, p) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = theme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(theme.primary.copy(alpha = 0.7f), theme.secondary.copy(alpha = 0.7f))))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Artiste $t", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(p, color = theme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewPopup() {
    val theme = Nova.theme
    Column(Modifier.padding(horizontal = 16.dp)) {
        GlassCard(glow = theme.accent) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Fiche titre", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MedalBadge(CertLevel.PLATINUM, 2, size = 58.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        CountUp(
                            137,
                            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 40.sp, letterSpacing = (-1.5).sp),
                            theme.primary
                        )
                        Text("écoutes", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewChart() {
    val theme = Nova.theme
    Column(Modifier.padding(horizontal = 16.dp)) {
        GlassCard {
            Column(Modifier.padding(16.dp)) {
                Text("Heures d'écoute", color = theme.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                HourChart(
                    listOf(2, 1, 0, 0, 1, 3, 6, 9, 12, 14, 11, 8, 10, 13, 16, 18, 15, 12, 20, 24, 22, 17, 9, 4),
                    theme.accent,
                    Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* ============================== briques ============================== */

@Composable
private fun EditorSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionTitle(title)
        if (subtitle != null) Text(
            subtitle, color = Nova.theme.textSecondary, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        GlassCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun MiniPreview(t: NovaTheme) {
    NovaStatsTheme(theme = t) {
        val p = Nova.theme
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(p.background, p.surface)))
                .border(1.dp, p.primary.copy(alpha = 0.55f), RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${t.emoji} ${t.name}", color = p.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("NOVASTATS", color = p.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(12.dp))
            CountUp(
                1284,
                MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 40.sp, letterSpacing = (-2).sp),
                p.primary
            )
            Text("écoutes cette semaine", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewPill("🥇 Or", p.primary)
                PreviewPill("🔥 Série 12 j", p.secondary)
                PreviewPill("💎 ×2", p.accent)
            }
            Spacer(Modifier.height(14.dp))
            val bars = listOf(0.35f, 0.62f, 0.48f, 0.9f, 0.7f, 0.55f, 0.8f, 0.42f, 0.66f, 0.95f)
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bars.forEach { v ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight(v).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Brush.verticalGradient(listOf(p.accent, p.primary)))
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPill(text: String, color: Color) {
    val p = Nova.theme
    Box(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.22f)).border(1.dp, color.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, color = p.text, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun PresetChip(preset: ThemePreset, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    val c1 = previewHue(preset.primary, preset.sat)
    val c2 = previewHue(preset.secondary, preset.sat)
    val c3 = previewHue(preset.accent, preset.sat)
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(c1.copy(alpha = 0.35f), c2.copy(alpha = 0.25f), c3.copy(alpha = 0.35f))))
            .border(1.dp, c1.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c1))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(c2))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(c3))
            }
            Spacer(Modifier.height(4.dp))
            Text(preset.label, color = theme.text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Aperçu d'une teinte HSL (même algorithme que [CustomThemeSpec.toTheme]). */
private fun previewHue(hue: Float, sat: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val l = 0.58f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * sat
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}

@Composable
private fun HueSlider(label: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = theme.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Box(Modifier.size(18.dp).clip(CircleShape).background(color))
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Color.White.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun SimpleSlider(label: String, value: Float, min: Float, max: Float, color: Color, onChange: (Float) -> Unit) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = theme.text, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = Color.White.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = Nova.theme
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(theme.primary, theme.secondary)) else Brush.horizontalGradient(listOf(theme.surface.copy(alpha = 0.9f), theme.surface.copy(alpha = 0.7f))))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) theme.background else theme.text,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
}
