package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.RankedAlbum
import com.novastats.app.data.db.dao.RankedTrack
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.ui.theme.Nova
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private enum class EditorTab(val label: String) { TRACKS("🎵 Titres"), ARTISTS("🎤 Artistes"), ALBUMS("💿 Albums"), PLAYS("▶ Écoutes"), REVIEW("⚠️ À corriger"), HISTORY("🕘 Historique") }

/**
 * 🛠️ Éditeur de données (Paramètres → Éditeur). Recherche, suggestions de fusion, renommage, fusion,
 * changement d'artiste/album, suppression d'écoute, marquage « correct », historique + Undo.
 */
@Composable
fun DataEditorScreen() {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val editor = app.editor
    val scope = rememberCoroutineScope()
    val status by editor.status.collectAsStateWithLifecycle()
    val busy by editor.busy.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(EditorTab.TRACKS) }
    var query by remember { mutableStateOf("") }

    val tracks by app.database.trackDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by app.database.artistDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by app.database.albumDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val review by app.database.trackDao().needingReview().collectAsStateWithLifecycle(initialValue = emptyList())
    val history by app.database.editorDao().history(50).collectAsStateWithLifecycle(initialValue = emptyList())
    val recentPlays by app.database.scrobbleDao().recent(200).collectAsStateWithLifecycle(initialValue = emptyList())

    var action by remember { mutableStateOf<EditorAction?>(null) }
    val exec: (suspend () -> Unit) -> Unit = { block -> scope.launch { runCatching { withContext(Dispatchers.IO) { block() } } } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorTab.entries.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) }) }
        }
        if (tab == EditorTab.TRACKS || tab == EditorTab.ARTISTS || tab == EditorTab.ALBUMS) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("🔍 Rechercher…") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = theme.accent)
        status?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        val q = query.trim().lowercase()
        val trackSugg = remember(tracks) { editor.suggestTrackMerges(tracks.map { it.track }) }
        val artistSugg = remember(artists) { editor.suggestArtistMerges(artists) }
        val albumSugg = remember(albums) { editor.suggestAlbumMerges(albums.map { it.album }) }
        LazyColumn(Modifier.fillMaxSize()) {
            when (tab) {
                EditorTab.TRACKS -> {
                    val sugg = trackSugg
                    if (q.isEmpty() && sugg.isNotEmpty()) item {
                        SuggestionCard("🔍 ${sugg.size} doublon(s) de titres détecté(s)", sugg.map { (a, b) -> "« ${a.title} » → « ${b.title} »" }) {
                            exec { sugg.forEach { (a, b) -> editor.mergeTracks(a.trackId, b.trackId) } }
                        }
                    }
                    items(tracks.filter { q.isEmpty() || it.track.title.lowercase().contains(q) || it.artistName.lowercase().contains(q) }, key = { "t" + it.track.trackId }) { t ->
                        EditorRow(t.track.title, "${t.artistName}${t.albumTitle?.let { " · $it" } ?: ""} · ${t.periodPlays} ▶", t.track.coverUrl) { action = EditorAction.Track(t) }
                    }
                }
                EditorTab.ARTISTS -> {
                    val sugg = artistSugg
                    if (q.isEmpty() && sugg.isNotEmpty()) item {
                        SuggestionCard("🔍 ${sugg.size} fusion(s) d'artistes suggérée(s)", sugg.map { (a, b) -> "« ${a.name} » → « ${b.name} »" }) {
                            exec { sugg.forEach { (a, b) -> editor.mergeArtists(a.artistId, b.artistId) } }
                        }
                    }
                    items(artists.filter { q.isEmpty() || it.name.lowercase().contains(q) }, key = { "a" + it.artistId }) { a ->
                        EditorRow(a.name, "${a.playCount} ▶", a.photoUrl, circle = true) { action = EditorAction.Artist(a) }
                    }
                }
                EditorTab.ALBUMS -> {
                    val sugg = albumSugg
                    if (q.isEmpty() && sugg.isNotEmpty()) item {
                        SuggestionCard("🔍 ${sugg.size} fusion(s) d'albums suggérée(s)", sugg.map { (a, b) -> "« ${a.title} » → « ${b.title} »" }) {
                            exec { sugg.forEach { (a, b) -> editor.mergeAlbums(a.albumId, b.albumId) } }
                        }
                    }
                    items(albums.filter { q.isEmpty() || it.album.title.lowercase().contains(q) || it.artistName.lowercase().contains(q) }, key = { "al" + it.album.albumId }) { al ->
                        EditorRow(al.album.title, "${al.artistName} · ${al.periodPlays} ▶", al.album.coverUrl) { action = EditorAction.Album(al) }
                    }
                }
                EditorTab.PLAYS -> {
                    item { Text("200 dernières écoutes — appuie pour supprimer (irréversible).", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 8.dp)) }
                    items(recentPlays, key = { "s" + it.scrobble.scrobbleId }) { s ->
                        EditorRow(s.title, "${s.artistName} · ${playFmt.format(s.scrobble.startedAt)} · ${formatDuration(s.scrobble.durationListenedMs)}", s.coverUrl) { action = EditorAction.Play(s.scrobble, s.title) }
                    }
                }
                EditorTab.REVIEW -> {
                    if (review.isEmpty()) item { Text("✅ Aucun titre à corriger (score de confiance ≥ 70).", color = theme.textSecondary, modifier = Modifier.padding(16.dp)) }
                    items(review, key = { "r" + it.track.trackId }) { t ->
                        EditorRow(t.track.title, "${t.artistName} · confiance ${t.track.confidenceScore} % · ${t.periodPlays} ▶", t.track.coverUrl) { action = EditorAction.Track(t) }
                    }
                }
                EditorTab.HISTORY -> {
                    item {
                        Button(onClick = { exec { editor.undo() } }, enabled = history.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp), colors = ButtonDefaults.buttonColors(containerColor = theme.primary)) {
                            Text("↩️ Annuler la dernière action")
                        }
                    }
                    if (history.isEmpty()) item { Text("Aucune modification pour l'instant.", color = theme.textSecondary, modifier = Modifier.padding(16.dp)) }
                    items(history, key = { "h" + it.id }) { h ->
                        Column(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                            Text("${editTypeEmoji(h.type)} ${h.type} · ${h.entityType} #${h.entityId}", color = theme.text, fontWeight = FontWeight.SemiBold)
                            Text("${h.before ?: "—"} → ${h.after ?: "—"} · ${playFmt.format(h.createdAt)}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    action?.let { a -> EditorActionDialog(a, artists, albums, tracks, onDismiss = { action = null }, exec = exec) }
}

private val playFmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

private fun editTypeEmoji(t: String) = when (t) { "RENAME" -> "✏️"; "MERGE" -> "🔗"; "DELETE_PLAY" -> "🗑️"; "ARTIST_CHANGE" -> "🎤"; "ALBUM_CHANGE" -> "💿"; "COVER_CHANGE" -> "🖼️"; else -> "✅" }

private sealed interface EditorAction {
    data class Track(val t: RankedTrack) : EditorAction
    data class Artist(val a: ArtistEntity) : EditorAction
    data class Album(val al: RankedAlbum) : EditorAction
    data class Play(val s: ScrobbleEntity, val title: String) : EditorAction
}

@Composable
private fun EditorRow(title: String, subtitle: String, cover: String?, circle: Boolean = false, onClick: () -> Unit) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CoverArt(cover, title, size = 40, circle = circle)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("✏️", color = theme.primary)
    }
}

@Composable
private fun SuggestionCard(title: String, lines: List<String>, onApplyAll: () -> Unit) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(12.dp)).background(theme.primary.copy(alpha = 0.12f)).padding(12.dp)) {
        Text(title, color = theme.text, fontWeight = FontWeight.SemiBold)
        lines.take(5).forEach { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (lines.size > 5) Text("… et ${lines.size - 5} autre(s)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onApplyAll) { Text("🔗 Tout fusionner", color = theme.primary, fontWeight = FontWeight.Bold) }
    }
}

/* ---------------- Dialogue d'action ---------------- */

@Composable
private fun EditorActionDialog(
    action: EditorAction, artists: List<ArtistEntity>, albums: List<RankedAlbum>, tracks: List<RankedTrack>,
    onDismiss: () -> Unit, exec: (suspend () -> Unit) -> Unit
) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val editor = app.editor
    var mode by remember { mutableStateOf("MENU") }
    var text by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf("") }
    LaunchedEffect(action) {
        text = when (action) { is EditorAction.Track -> action.t.track.title; is EditorAction.Artist -> action.a.name; is EditorAction.Album -> action.al.album.title; is EditorAction.Play -> "" }
    }
    val title = when (action) {
        is EditorAction.Track -> "🎵 ${action.t.track.title}"; is EditorAction.Artist -> "🎤 ${action.a.name}"
        is EditorAction.Album -> "💿 ${action.al.album.title}"; is EditorAction.Play -> "▶ ${action.title}"
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = theme.surface, titleContentColor = theme.text, textContentColor = theme.textSecondary,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                when (mode) {
                    "MENU" -> {
                        when (action) {
                            is EditorAction.Track -> {
                                MenuItem("✏️ Renommer") { mode = "RENAME" }
                                MenuItem("🎤 Changer l'artiste") { mode = "ARTIST" }
                                MenuItem("💿 Changer l'album") { mode = "ALBUM" }
                                MenuItem("🔗 Fusionner avec un autre titre") { mode = "MERGE" }
                                MenuItem("✅ Marquer comme correct") { exec { editor.markReviewed(action.t.track.trackId) }; onDismiss() }
                            }
                            is EditorAction.Artist -> {
                                MenuItem("✏️ Renommer") { mode = "RENAME" }
                                MenuItem("🔗 Fusionner dans un autre artiste") { mode = "MERGE" }
                                MenuItem("🖼️ Photo (URL)") { text = action.a.photoUrl ?: ""; mode = "IMAGE" }
                            }
                            is EditorAction.Album -> {
                                MenuItem("✏️ Renommer") { mode = "RENAME" }
                                MenuItem("🔗 Fusionner dans un autre album") { mode = "MERGE" }
                                MenuItem("🖼️ Pochette (URL)") { text = action.al.album.coverUrl ?: ""; mode = "IMAGE" }
                            }
                            is EditorAction.Play -> {
                                Text("Supprimer cette écoute du ${playFmt.format(action.s.startedAt)} ? Les statistiques seront recalculées. Action irréversible.")
                            }
                        }
                    }
                    "RENAME", "ARTIST", "ALBUM", "IMAGE" -> {
                        val label = when (mode) { "RENAME" -> "Nouveau nom"; "ARTIST" -> "Nom de l'artiste"; "ALBUM" -> "Titre de l'album (vide = aucun)"; else -> "URL de l'image (vide = retirer)" }
                        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (mode == "ARTIST" || mode == "ALBUM") {
                            val q = text.trim().lowercase()
                            val hints = if (mode == "ARTIST") artists.filter { q.isNotEmpty() && it.name.lowercase().contains(q) }.take(5).map { it.name }
                            else albums.filter { q.isNotEmpty() && it.album.title.lowercase().contains(q) }.take(5).map { it.album.title }
                            hints.forEach { h -> MenuItem("→ $h") { text = h } }
                        }
                    }
                    "MERGE" -> {
                        OutlinedTextField(value = pick, onValueChange = { pick = it }, label = { Text("Rechercher la cible…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val q = pick.trim().lowercase()
                        Text("La cible garde son nom ; écoutes et titres sont transférés.", style = MaterialTheme.typography.bodySmall)
                        when (action) {
                            is EditorAction.Artist -> artists.filter { it.artistId != action.a.artistId && (q.isEmpty() || it.name.lowercase().contains(q)) }.take(8).forEach { t ->
                                MenuItem("🎤 ${t.name} (${t.playCount} ▶)") { exec { editor.mergeArtists(action.a.artistId, t.artistId) }; onDismiss() }
                            }
                            is EditorAction.Album -> albums.filter { it.album.albumId != action.al.album.albumId && (q.isEmpty() || it.album.title.lowercase().contains(q)) }.take(8).forEach { t ->
                                MenuItem("💿 ${t.album.title} — ${t.artistName}") { exec { editor.mergeAlbums(action.al.album.albumId, t.album.albumId) }; onDismiss() }
                            }
                            is EditorAction.Track -> tracks.filter { it.track.trackId != action.t.track.trackId && (q.isEmpty() || it.track.title.lowercase().contains(q)) }.take(8).forEach { t ->
                                MenuItem("🎵 ${t.track.title} — ${t.artistName}") { exec { editor.mergeTracks(action.t.track.trackId, t.track.trackId) }; onDismiss() }
                            }
                            else -> {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                action is EditorAction.Play -> TextButton(onClick = { exec { editor.deleteScrobble(action.s.scrobbleId) }; onDismiss() }) { Text("🗑️ Supprimer", color = Color(0xFFE74C3C), fontWeight = FontWeight.Bold) }
                mode == "RENAME" -> TextButton(onClick = {
                    exec {
                        when (action) {
                            is EditorAction.Track -> editor.renameTrack(action.t.track.trackId, text)
                            is EditorAction.Artist -> editor.renameArtist(action.a.artistId, text)
                            is EditorAction.Album -> editor.renameAlbum(action.al.album.albumId, text)
                            else -> {}
                        }
                    }; onDismiss()
                }) { Text("Renommer", color = theme.primary, fontWeight = FontWeight.Bold) }
                mode == "ARTIST" && action is EditorAction.Track -> TextButton(onClick = { exec { editor.setTrackArtistByName(action.t.track.trackId, text) }; onDismiss() }) { Text("Appliquer", color = theme.primary, fontWeight = FontWeight.Bold) }
                mode == "ALBUM" && action is EditorAction.Track -> TextButton(onClick = {
                    exec { if (text.isBlank()) editor.setTrackAlbum(action.t.track.trackId, null) else editor.setTrackAlbumByName(action.t.track.trackId, text) }; onDismiss()
                }) { Text("Appliquer", color = theme.primary, fontWeight = FontWeight.Bold) }
                mode == "IMAGE" -> TextButton(onClick = {
                    exec {
                        val url = text.trim().ifBlank { null }
                        when (action) { is EditorAction.Artist -> editor.setArtistPhoto(action.a.artistId, url); is EditorAction.Album -> editor.setAlbumCover(action.al.album.albumId, url); else -> {} }
                    }; onDismiss()
                }) { Text("Appliquer", color = theme.primary, fontWeight = FontWeight.Bold) }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = { if (mode == "MENU") onDismiss() else mode = "MENU" }) { Text(if (mode == "MENU") "Fermer" else "‹ Retour", color = theme.textSecondary) } }
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(label, color = Nova.theme.text, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp))
}
