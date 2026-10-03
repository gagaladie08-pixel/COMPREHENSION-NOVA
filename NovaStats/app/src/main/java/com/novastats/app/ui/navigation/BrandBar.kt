package com.novastats.app.ui.navigation

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
import androidx.compose.material.icons.filled.Favorite
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
    "survivor" -> Icons.Filled.Favorite
    "rainbow_pop" -> Icons.Filled.Palette
    "pop_revolution" -> Icons.Filled.Mic
    "african_confessions" -> Icons.Filled.Public
    "bad_angel" -> Icons.Filled.Flare
    else -> Icons.Filled.AutoAwesome
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE)
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)

/**
 * Barre de marque fixe (ne se replie pas au scroll) : icône du thème + NOVASTATS (police titre du thème, dégradé
 * primary → accent), date et heure à côté, 🔍 recherche globale au fond à droite.
 */
@Composable
fun BrandBar(onSearch: () -> Unit) {
    val theme = Nova.theme
    val fonts = LocalNovaFonts.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(15_000) } }
    Row(
        Modifier.fillMaxWidth().background(theme.surface).statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemedTabIcon(brandIcon(theme), contentDescription = theme.name, selected = true)
        Spacer(Modifier.width(6.dp))
        Text(
            "NOVASTATS",
            style = TextStyle(
                fontFamily = fonts.title, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 2.sp,
                brush = Brush.horizontalGradient(listOf(theme.primary, theme.accent))
            ),
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        // Date et heure, à l'horizontale à côté du nom
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(now.format(dateFmt).replace(".", "").replaceFirstChar { it.uppercase() }, color = theme.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(now.format(timeFmt), color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(theme.primary.copy(alpha = 0.12f)).border(1.dp, theme.primary.copy(alpha = 0.45f), CircleShape).clickable(onClick = onSearch),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Search, contentDescription = "Rechercher dans l'app", tint = theme.primary) }
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
