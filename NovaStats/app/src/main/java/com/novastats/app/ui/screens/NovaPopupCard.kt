package com.novastats.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import kotlin.math.sin
import kotlin.random.Random

/* =====================================================================================
 * POPUPS.md — base commune de tous les popups « standard »
 *   • Overlay noir 85 %            • Fermeture : clic extérieur OU bouton FERMER centré en bas
 *   • Carte 92 % de largeur        • Coins arrondis 16 dp
 *   • Hauteur dynamique (max)      • Transition : fade simple
 *   • Scroll interne à la carte    • Bordure fine colorée (thème / statut / niveau)
 *   • Dégradé vertical : couleur de bordure → fond de l'app
 * Bordure / bannière / contenu sont injectés selon le type de popup.
 * ===================================================================================== */

/** Palette holographique (Mythique). */
val HoloColors = listOf(Color(0xFFFF6EC7), Color(0xFFFFD86E), Color(0xFF6EFFB8), Color(0xFF6EC1FF), Color(0xFFC96EFF), Color(0xFFFF6EC7))

@Composable
fun NovaPopupCard(
    borderColor: Color,
    onDismiss: () -> Unit,
    /** Intensité du glow (dp d'ombre colorée). 0 = aucun. */
    glowDp: Int = 12,
    /** Bordure holographique animée + particules (Panthéon Mythique). */
    holographic: Boolean = false,
    widthFraction: Float = 0.92f,
    heightFraction: Float = 0.88f,
    fixedHeight: Boolean = false,
    banner: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val theme = Nova.theme
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val shape = RoundedCornerShape(16.dp)
    val holoShift by rememberInfiniteTransition(label = "holo").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "hs")
    val borderBrush: Brush = if (holographic) {
        val n = HoloColors.size
        val shifted = List(n) { i -> HoloColors[((i + (holoShift * n).toInt()) % n)] }
        Brush.linearGradient(shifted)
    } else Brush.linearGradient(listOf(borderColor, borderColor))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                val maxH = (LocalConfiguration.current.screenHeightDp * heightFraction).dp
                Box(
                    (if (fixedHeight) Modifier.fillMaxWidth(widthFraction).height(maxH) else Modifier.fillMaxWidth(widthFraction).heightIn(max = maxH))
                        .then(if (glowDp > 0) Modifier.shadow(glowDp.dp, shape, ambientColor = borderColor, spotColor = borderColor) else Modifier)
                        .clip(shape)
                        // Dégradé vertical : couleur de bordure → fond de l'app
                        .background(Brush.verticalGradient(listOf(borderColor.copy(alpha = 0.28f), theme.background)))
                        .border(1.5.dp, borderBrush, shape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                ) {
                    if (holographic) HoloParticles(Modifier.matchParentSize())
                    Column(Modifier.fillMaxWidth()) {
                        banner()
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
                        HorizontalDivider(color = borderColor.copy(alpha = 0.3f))
                        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("FERMER", color = if (holographic) Color.White else borderColor, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Particules holographiques flottantes (Mythique). */
@Composable
private fun HoloParticles(modifier: Modifier) {
    val t by rememberInfiniteTransition(label = "hp").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "hpa")
    val pts = remember { List(28) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 1.5f + Random.nextFloat() * 2.5f, Random.nextFloat() * 6.28f, Random.nextInt(HoloColors.size - 1).toFloat()) } }
    Canvas(modifier) {
        pts.forEach { p ->
            val y = ((p[1] - t * 0.6f) % 1f + 1f) % 1f
            val x = p[0] + sin(t * 6.28f * 2 + p[3]) * 0.02f
            val a = 0.25f + 0.55f * ((sin(t * 6.28f * 3 + p[3]) + 1f) / 2f)
            drawCircle(HoloColors[p[4].toInt()].copy(alpha = a), p[2].dp.toPx(), Offset(x * size.width, y * size.height))
        }
    }
}

/* ===================================== Blocs partagés ===================================== */

/** Ligne libellé / valeur. */
@Composable
fun PopupInfoRow(label: String, value: String, valueColor: Color = Nova.theme.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Nova.theme.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** Titre de section dans un popup (couleur injectable : bordure / primary). */
@Composable
fun PopupSection(text: String, color: Color = Nova.theme.primary) {
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** Tableau horizontal des positions par période : Daily · Weekly · Monthly · Yearly · Global. */
@Composable
fun PositionsTable(ranks: Map<Period, Int?>) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Period.entries.forEach { p ->
            val r = ranks[p]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Text(
                    positionLabel(r), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                    color = when (r) { null -> theme.textSecondary.copy(alpha = 0.6f); 1 -> NovaColors.Gold; 2 -> NovaColors.Silver; 3 -> Color(0xFFCD7F32); else -> theme.text }
                )
            }
        }
    }
}

/** Fond flou (photo / pochette) + dégradé noir, pour les bannières. */
@Composable
fun BlurredBackdrop(url: String?, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(24.dp).alpha(0.6f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.75f)))))
    }
}

/** Badge « pastille » (peak, semaines, séries…). */
@Composable
fun PopupBadge(emoji: String, value: String, label: String, color: Color) {
    val theme = Nova.theme
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = theme.textSecondary, fontSize = 10.sp)
    }
}
