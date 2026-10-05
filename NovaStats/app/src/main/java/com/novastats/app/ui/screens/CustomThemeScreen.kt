package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.novastats.app.ui.theme.CUSTOM_THEME_ID
import com.novastats.app.ui.theme.CustomThemeSpec
import com.novastats.app.ui.theme.CustomThemeStore
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaStatsTheme
import com.novastats.app.ui.theme.NovaThemes
import com.novastats.app.ui.theme.Signature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* ==================================================================== */
/*  ✨ Mon thème — le 16ᵉ thème, créé par toi                            */
/* ==================================================================== */

private data class ThemePreset(val label: String, val primary: Float, val secondary: Float, val accent: Float, val sat: Float, val dark: Boolean = true)

private val PRESETS = listOf(
    ThemePreset("🌌 Néon", 320f, 200f, 175f, 0.88f),
    ThemePreset("🌅 Coucher de soleil", 18f, 340f, 45f, 0.85f),
    ThemePreset("🌊 Océan", 200f, 230f, 160f, 0.80f),
    ThemePreset("🌿 Forêt", 140f, 95f, 60f, 0.70f),
    ThemePreset("🍷 Rubis", 350f, 10f, 300f, 0.85f),
    ThemePreset("🍬 Pastel", 320f, 190f, 45f, 0.55f, dark = false)
)

private val FONT_PAIRS = listOf(
    "Orbitron" to "Rajdhani",
    "Audiowide" to "Poppins",
    "Playfair Display" to "Montserrat",
    "Bebas Neue" to "Oswald",
    "Righteous" to "Poppins"
)

private val SIGNATURES = listOf(
    Signature.GOLD_SHIMMER to "Shimmer doré",
    Signature.SCANLINES_GLITCH to "Scanlines",
    Signature.CHROME_SWEEP to "Reflets chromés",
    Signature.RISING_BUBBLES to "Bulles",
    Signature.SPARKLES to "Paillettes",
    Signature.WARM_PULSE to "Glow chaud",
    Signature.CLOUD_FADE to "Fondu nuage",
    Signature.CONFETTI to "Confettis"
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

    val artUrl by produceState<String?>(null) {
        value = runCatching { app.database.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    fun applyTheme() {
        CustomThemeStore.save(context, spec)
        NovaThemes.customTheme = spec.toTheme()
        scope.launch { app.settings.setTheme(CUSTOM_THEME_ID) }
    }

    ScreenBackdrop(artUrl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Paramètres", color = theme.primary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("✨ Mon thème", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
            }

            /* ---------- Aperçu vivant ---------- */
            Appear(delay = 0) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        if (existing) "Aperçu en direct — ton thème, tel qu'il sera partout dans l'app"
                        else "Aperçu en direct — bouge les curseurs, l'aperçu suit",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    ThemePreview(candidate)
                }
            }

            /* ---------- Palettes ---------- */
            Appear(delay = 60) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SectionTitle("🎨 Partir d'une palette")
                    GlassCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PRESETS.take(3).forEach { p -> PresetChip(p, Modifier.weight(1f)) { spec = spec.copy(primaryHue = p.primary, secondaryHue = p.secondary, accentHue = p.accent, saturation = p.sat, dark = p.dark) } }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PRESETS.drop(3).forEach { p -> PresetChip(p, Modifier.weight(1f)) { spec = spec.copy(primaryHue = p.primary, secondaryHue = p.secondary, accentHue = p.accent, saturation = p.sat, dark = p.dark) } }
                            }
                        }
                    }
                }
            }

            /* ---------- Couleurs ---------- */
            Appear(delay = 100) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SectionTitle("🌈 Tes couleurs")
                    GlassCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            HueSlider("Couleur principale", spec.primaryHue, candidate.primary) { spec = spec.copy(primaryHue = it) }
                            HueSlider("Couleur secondaire", spec.secondaryHue, candidate.secondary) { spec = spec.copy(secondaryHue = it) }
                            HueSlider("Couleur d'accent", spec.accentHue, candidate.accent) { spec = spec.copy(accentHue = it) }
                            SimpleSlider("Intensité", spec.saturation, 0.25f, 1f, candidate.primary) { spec = spec.copy(saturation = it) }
                            SimpleSlider("Luminosité", spec.lightness, 0.35f, 0.80f, candidate.accent) { spec = spec.copy(lightness = it) }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                ChoiceChip("🌙 Sombre", spec.dark, Modifier.weight(1f)) { spec = spec.copy(dark = true) }
                                ChoiceChip("☀️ Clair", !spec.dark, Modifier.weight(1f)) { spec = spec.copy(dark = false) }
                            }
                        }
                    }
                }
            }

            /* ---------- Formes, polices, effet ---------- */
            Appear(delay = 140) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SectionTitle("🧩 Formes & typographie")
                    GlassCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SimpleSlider("Arrondi des cartes", spec.cornerDp.toFloat(), 0f, 28f, candidate.secondary) { spec = spec.copy(cornerDp = it.toInt()) }
                            Text(
                                "${spec.cornerDp} dp — ${if (spec.cornerDp == 0) "angles vifs" else if (spec.cornerDp >= 24) "très arrondi" else "arrondi moyen"}",
                                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                            )
                            FONT_PAIRS.forEach { (t, b) ->
                                val selected = spec.titleFont == t && spec.bodyFont == b
                                ChoiceChip("$t · $b", selected, Modifier.fillMaxWidth()) { spec = spec.copy(titleFont = t, bodyFont = b) }
                            }
                        }
                    }
                }
            }

            Appear(delay = 180) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    SectionTitle("✨ Effet signature")
                    GlassCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SIGNATURES.take(4).forEach { (sig, label) ->
                                    ChoiceChip(label, spec.signature == sig, Modifier.weight(1f)) { spec = spec.copy(signature = sig) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SIGNATURES.drop(4).forEach { (sig, label) ->
                                    ChoiceChip(label, spec.signature == sig, Modifier.weight(1f)) { spec = spec.copy(signature = sig) }
                                }
                            }
                        }
                    }
                }
            }

            /* ---------- Identité ---------- */
            Appear(delay = 220) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    SectionTitle("🏷️ Nom & emblème")
                    GlassCard {
                        Column(Modifier.padding(16.dp)) {
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
                }
            }

            /* ---------- Actions ---------- */
            Appear(delay = 260) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { applyTheme() }, modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.background)
                    ) {
                        Text(if (existing) "✨ Enregistrer et appliquer" else "✨ Créer mon thème", fontWeight = FontWeight.Black, fontSize = 16.sp)
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
                    Text(
                        "Ton thème devient le 16ᵉ de la liste : tu peux revenir aux 15 thèmes d'origine à tout moment depuis Apparence. Le changement ne ferme pas l'app.",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/* ============================== briques ============================== */

@Composable
private fun ThemePreview(t: com.novastats.app.ui.theme.NovaTheme) {
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
            Spacer(Modifier.height(14.dp))
            CountUp(
                1284,
                MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 44.sp, letterSpacing = (-2).sp),
                p.primary
            )
            Text("écoutes cette semaine", color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewPill("🥇 Or", p.primary)
                PreviewPill("🔥 Série 12 j", p.secondary)
                PreviewPill("💎 ×2", p.accent)
            }
            Spacer(Modifier.height(16.dp))
            val bars = listOf(0.35f, 0.62f, 0.48f, 0.9f, 0.7f, 0.55f, 0.8f, 0.42f, 0.66f, 0.95f)
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
