package com.novastats.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.novastats.app.ui.theme.NovaColors
import kotlin.math.cos
import kotlin.math.sin
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.RankedTrack
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.ui.screens.CoverArt
import com.novastats.app.ui.screens.DetailPopupHost
import com.novastats.app.ui.screens.DetailTarget
import com.novastats.app.ui.screens.formatCount
import com.novastats.app.ui.theme.LocalNovaFonts
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.ThemedTabIcon
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Icône « essence » de chaque thème (vectorielle, habillée par le style d'icônes du thème via ThemedTabIcon). */
fun brandIcon(theme: NovaTheme): ImageVector = when (theme.id) {
    "cyber_nova" -> Icons.Filled.Memory
    "neon_disco" -> Icons.Filled.Nightlife
    "villain_era" -> Icons.Filled.LocalFireDepartment
    "slay_queen" -> Icons.Filled.Diamond
    "pink_y2k" -> Icons.Filled.Icecream
    "velvet_stage" -> Icons.Filled.TheaterComedy
    "pink_venom" -> Icons.Filled.Whatshot
    "cloud_nine" -> Icons.Filled.Cloud
    "solara" -> Icons.Filled.WbSunny
    "chaos_born" -> Icons.Filled.AutoAwesome
    "survivor" -> Icons.Filled.Transgender
    "rainbow_pop" -> Icons.Filled.Palette
    "pop_revolution" -> Icons.Filled.Mic
    "african_confessions" -> Icons.Filled.Public
    "bad_angel" -> Icons.Filled.Flare
    else -> Icons.Filled.AutoAwesome
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)

/**
 * Barre de marque fixe (ne se replie pas au scroll) : halo + anneau tournant autour de l'icône du thème,
 * NOVASTATS en grand (police titre du thème, dégradé primary → accent avec reflet qui traverse + glow),
 * date · heure repoussées à droite, 🔍 recherche globale.
 */
@Composable
fun BrandBar(onSearch: () -> Unit) {
    val theme = Nova.theme
    val fonts = LocalNovaFonts.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(15_000) } }
    val anim = rememberInfiniteTransition(label = "brand")
    val shimmer by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
    val spin by anim.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val pulse by anim.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val pride = theme.id == "survivor"

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(theme.primary.copy(alpha = 0.16f), theme.surface, theme.accent.copy(alpha = 0.10f))))
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Icône du thème : halo pulsant + anneau conique tournant + étincelles
            Box(
                Modifier.size(48.dp).drawBehind {
                    val c = center; val r = size.minDimension / 2
                    drawCircle(Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.55f * pulse), theme.accent.copy(alpha = 0.18f), Color.Transparent), c, r * 1.1f), r * 1.1f, c)
                    val ringColors = if (pride) NovaColors.PrideCycle + NovaColors.PrideCycle.first() else listOf(Color.Transparent, theme.accent, theme.primary, Color.White, theme.primary, Color.Transparent)
                    rotate(spin, c) { drawCircle(Brush.sweepGradient(ringColors, c), r - 1.5.dp.toPx(), c, style = Stroke(2.dp.toPx())) }
                    // Trois étincelles qui tournent en sens inverse
                    rotate(-spin * 1.4f, c) {
                        listOf(0f, 120f, 240f).forEachIndexed { i, a ->
                            val rad = Math.toRadians(a.toDouble()); val p = Offset(c.x + cos(rad).toFloat() * r * 1.02f, c.y + sin(rad).toFloat() * r * 1.02f)
                            val sz = (1.5f + i * 0.7f).dp.toPx() * pulse
                            drawLine(Color.White.copy(alpha = 0.9f), Offset(p.x - sz, p.y), Offset(p.x + sz, p.y), 1.2.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.9f), Offset(p.x, p.y - sz), Offset(p.x, p.y + sz), 1.2.dp.toPx())
                        }
                    }
                },
                contentAlignment = Alignment.Center
            ) {
                ThemedTabIcon(brandIcon(theme), contentDescription = theme.name, selected = true, modifier = Modifier.scale(1.15f))
            }
            Spacer(Modifier.width(8.dp))
            // ── NOVASTATS : grand, police titre du thème, dégradé + reflet traversant + glow
            val titleColors = if (pride) NovaColors.PrideCycle else listOf(theme.primary, theme.accent, Color.White, theme.accent, theme.primary)
            val w = 900f
            Text(
                "NOVASTATS",
                style = TextStyle(
                    fontFamily = fonts.title, fontWeight = FontWeight.Black, fontSize = 27.sp, letterSpacing = 2.5.sp,
                    brush = Brush.linearGradient(titleColors, start = Offset(shimmer * w * 2 - w, 0f), end = Offset(shimmer * w * 2, 0f), tileMode = TileMode.Mirror),
                    shadow = Shadow(theme.primary.copy(alpha = 0.75f), Offset(0f, 0f), blurRadius = 18f)
                ),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
            // ── Date · heure, repoussées à droite, compactes
            Column(horizontalAlignment = Alignment.End) {
                Text(now.format(timeFmt), color = theme.text, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp, maxLines = 1, lineHeight = 16.sp)
                Text(now.format(dateFmt).replace(".", "").replaceFirstChar { it.uppercase() }, color = theme.textSecondary, fontSize = 10.sp, maxLines = 1, lineHeight = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            // ── Recherche globale : pastille en dégradé avec glow
            Box(
                Modifier.size(38.dp).drawBehind { drawCircle(theme.primary.copy(alpha = 0.35f), size.minDimension / 2 * 1.15f) }
                    .clip(CircleShape).background(Brush.linearGradient(listOf(theme.primary, theme.accent)))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape).clickable(onClick = onSearch),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Search, contentDescription = "Rechercher dans l'app", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        // ── Liseré : fin trait lumineux primary → accent (ou ruban des 7 drapeaux pour Survivor)
        if (pride) PrideRibbon() else Box(Modifier.fillMaxWidth().height(1.5.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, theme.primary, theme.accent, Color.Transparent))))
    }
}

/** Ruban des drapeaux de la communauté : arc-en-ciel, trans, bi, gay, lesbien, pan, non-binaire. */
@Composable
fun PrideRibbon(height: Dp = 4.dp) {
    Row(Modifier.fillMaxWidth().height(height)) {
        NovaColors.PrideFlags.forEach { flag ->
            Row(Modifier.weight(1f).fillMaxHeight()) { flag.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight().background(c)) } }
        }
    }
}

/* ================================ 🔍 Recherche globale ================================ */

@Composable
fun GlobalSearchDialog(onDismiss: () -> Unit) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<RankedTrack>>(emptyList()) }
    var artists by remember { mutableStateOf<List<ArtistEntity>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumEntity>>(emptyList()) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { tracks = emptyList(); artists = emptyList(); albums = emptyList(); return@LaunchedEffect }
        delay(150)
        val db = app.database
        artists = runCatching { db.artistDao().search(q) }.getOrDefault(emptyList())
        albums = runCatching { db.albumDao().search(q) }.getOrDefault(emptyList())
        tracks = runCatching { db.trackDao().search(q) }.getOrDefault(emptyList())
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(theme.background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = theme.primary, fontWeight = FontWeight.Black, fontSize = 28.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(horizontal = 10.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("🔍 Titre, artiste, album…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
            }
            val total = tracks.size + artists.size + albums.size
            LazyColumn(Modifier.fillMaxSize()) {
                if (query.trim().length < 2) {
                    item { Text("Tape au moins 2 lettres — la recherche couvre tous tes titres, artistes et albums. Un appui ouvre la fiche.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
                } else if (total == 0) {
                    item { Text("Aucun résultat pour « ${query.trim()} ».", color = theme.textSecondary, modifier = Modifier.padding(16.dp)) }
                }
                if (artists.isNotEmpty()) {
                    item { SearchSection("🎤 Artistes (${artists.size})", theme.glowSecondary) }
                    items(artists.size) { i ->
                        val a = artists[i]
                        SearchRow(a.photoUrl, a.name, "${formatCount(a.playCount)} écoutes · ${a.distinctTracks} titres", circle = true) { detail = DetailTarget.Artist(a.artistId) }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SearchSection("💿 Albums (${albums.size})", theme.secondary) }
                    items(albums.size) { i ->
                        val al = albums[i]
                        SearchRow(al.coverUrl, al.title, "${formatCount(al.playCount)} écoutes") { detail = DetailTarget.Album(al.albumId) }
                    }
                }
                if (tracks.isNotEmpty()) {
                    item { SearchSection("🎵 Titres (${tracks.size})", theme.primary) }
                    items(tracks.size) { i ->
                        val t = tracks[i]
                        SearchRow(t.track.coverUrl, t.track.title, t.artistName + (t.albumTitle?.let { " · $it" } ?: "") + " · ${formatCount(t.track.playCount)} ▶") { detail = DetailTarget.Track(t.track.trackId) }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
    DetailPopupHost(detail) { detail = null }
}

@Composable
private fun SearchSection(text: String, color: Color) {
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SearchRow(image: String?, title: String, subtitle: String, circle: Boolean = false, onClick: () -> Unit) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(theme.surface).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CoverArt(image, title, size = 42, circle = circle)
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
