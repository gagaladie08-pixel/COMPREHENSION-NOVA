package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
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
import com.novastats.app.domain.TitleNormalizer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private enum class EditorTab(val label: String) { TRACKS("🎵 Titres"), ARTISTS("🎤 Artistes"), ALBUMS("💿 Albums"), PLAYS("▶ Écoutes"), REVIEW("⚠️ À corriger"), PROTECTED("🔒 Noms protégés"), HISTORY("🕘 Historique") }

/**
 * 🛠️ Éditeur de données (Paramètres → Éditeur). Recherche, suggestions de fusion, renommage, fusion,
 * changement d'artiste/album, suppression d'écoute, marquage « correct », historique + Undo.
 */
@Composable
fun DataEditorScreen(startOnReview: Boolean = false, focusTrackId: Long? = null) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val editor = app.editor
    val scope = rememberCoroutineScope()
    val status by editor.status.collectAsStateWithLifecycle()
    val busy by editor.busy.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(if (startOnReview) EditorTab.REVIEW else EditorTab.TRACKS) }
    LaunchedEffect(startOnReview, focusTrackId) { if (startOnReview) { tab = EditorTab.REVIEW; if (focusTrackId == null) ReviewUiState.section = ReviewKind.RED } }
    var query by remember { mutableStateOf("") }

    val tracks by app.database.trackDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by app.database.artistDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by app.database.albumDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    // ⚠️ À corriger : écoutes à réviser (groupées par titre), titres « APIs épuisées », titres 🟡 flaggés (70-89)
    val reviewGroups by app.database.scrobbleDao().reviewGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val lowConfidence by app.database.trackDao().lowConfidence().collectAsStateWithLifecycle(initialValue = emptyList())
    val flagged by app.database.trackDao().flaggedForVerification().collectAsStateWithLifecycle(initialValue = emptyList())
    var proposals by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(flagged) { proposals = flagged.associate { it.track.trackId to (proposalLabel(app, it.track.trackId) ?: "Pochette incertaine (${it.track.confidenceScore} %)") } }
    val redItems = remember(reviewGroups, lowConfidence) { buildRedItems(reviewGroups, lowConfidence) }
    val yellowItems = remember(flagged, proposals) { buildYellowItems(flagged, proposals) }
    val history by app.database.editorDao().history(50).collectAsStateWithLifecycle(initialValue = emptyList())
    val protectedNames by app.database.artistExceptionDao().all().collectAsStateWithLifecycle(initialValue = emptyList())
    var newProtected by remember { mutableStateOf("") }
    val recentPlays by app.database.scrobbleDao().recent(200).collectAsStateWithLifecycle(initialValue = emptyList())

    var action by remember { mutableStateOf<EditorAction?>(null) }
    val exec: (suspend () -> Unit) -> Unit = { block -> scope.launch { runCatching { withContext(Dispatchers.IO) { block() } } } }

    // Paires de doublons que l'utilisateur a choisi d'ignorer (persistant)
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("nova_editor", android.content.Context.MODE_PRIVATE) }
    var ignored by remember { mutableStateOf(prefs.getStringSet("ignored_pairs", emptySet())?.toSet() ?: emptySet()) }
    fun ignore(key: String) { ignored = ignored + key; prefs.edit().putStringSet("ignored_pairs", ignored).apply() }
    fun resetIgnored(prefix: String) { ignored = ignored.filterNot { it.startsWith(prefix) }.toSet(); prefs.edit().putStringSet("ignored_pairs", ignored).apply() }
    var dupPopup by remember { mutableStateOf<EditorTab?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorTab.entries.forEach { t ->
                val label = if (t == EditorTab.REVIEW && redItems.isNotEmpty()) "⚠️ À corriger 🔴 ${redItems.size}" else t.label
                NovaFilterChip(flagKey = t, selected = tab == t, onClick = { tab = t }, label = { Text(label) })
            }
        }
        /* ---------- Doublons détectés (compteur sous le sous-onglet → popup de choix) ---------- */
        val trackDups = remember(tracks, ignored) { trackDupsState(tracks, editor, ignored) }
        val artistDups = remember(artists, ignored) { artistDupsState(artists, editor, ignored) }
        val albumDups = remember(albums, ignored) { albumDupsState(albums, editor, ignored) }
        val dupsForTab = when (tab) { EditorTab.TRACKS -> trackDups; EditorTab.ARTISTS -> artistDups; EditorTab.ALBUMS -> albumDups; else -> null }
        if (dupsForTab != null) {
            val noun = when (tab) { EditorTab.TRACKS -> "de titres"; EditorTab.ARTISTS -> "d'artistes"; else -> "d'albums" }
            DuplicateBadge(dupsForTab.size, noun, enabled = !busy) { dupPopup = tab }
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
        LazyColumn(Modifier.fillMaxSize()) {
            when (tab) {
                EditorTab.TRACKS -> {
                    items(tracks.filter { q.isEmpty() || it.track.title.lowercase().contains(q) || it.artistName.lowercase().contains(q) }, key = { "t" + it.track.trackId }) { t ->
                        EditorRow(t.track.title, "${t.artistName}${t.albumTitle?.let { " · $it" } ?: ""} · ${t.periodPlays} ▶", t.track.coverUrl) { action = EditorAction.Track(t) }
                    }
                }
                EditorTab.ARTISTS -> {
                    items(artists.filter { q.isEmpty() || it.name.lowercase().contains(q) }, key = { "a" + it.artistId }) { a ->
                        EditorRow(a.name, "${a.playCount} ▶", a.photoUrl, circle = true) { action = EditorAction.Artist(a) }
                    }
                }
                EditorTab.ALBUMS -> {
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
                    item(key = "review") {
                        ReviewSection(redItems, yellowItems, focusTrackId, artists, albums.map { it.album }, busy = busy, exec = exec)
                    }
                }
                EditorTab.PROTECTED -> {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                            Text("Noms d'artistes jamais découpés", color = theme.text, fontWeight = FontWeight.Bold)
                            Text("Les artistes sont séparés sur « , & / + x feat. with… ». Les noms listés ici sont protégés : « HUNTR/X » reste un seul artiste, même dans « HUNTR/X feat. Future ». Les écoutes existantes sont corrigées par 🔗 Recalculer liens & versions (Réglages → Données).", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = newProtected, onValueChange = { newProtected = it }, singleLine = true, placeholder = { Text("Ex. AC/DC") }, modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        val n = newProtected.trim(); newProtected = ""
                                        if (n.isNotEmpty()) exec {
                                            app.database.artistExceptionDao().insert(com.novastats.app.data.db.entity.ArtistExceptionEntity(name = n, nameKey = TitleNormalizer.normalizeKey(n)))
                                            app.library.loadArtistExceptions(seedDefaults = false); app.library.clearCaches()
                                        }
                                    },
                                    enabled = newProtected.isNotBlank() && !busy, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                                ) { Text("Ajouter") }
                            }
                        }
                    }
                    if (protectedNames.isEmpty()) item { Text("Aucun nom protégé.", color = theme.textSecondary, modifier = Modifier.padding(16.dp)) }
                    items(protectedNames, key = { "p" + it.id }) { e ->
                        Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔒 ${e.name}", color = theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { exec { app.database.artistExceptionDao().delete(e.id); app.library.loadArtistExceptions(seedDefaults = false); app.library.clearCaches() } }, enabled = !busy) { Text("Retirer", color = theme.secondary) }
                        }
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

    dupPopup?.let { kind ->
        val pairs = remember(kind, tracks, artists, albums, ignored) {
            when (kind) {
                EditorTab.TRACKS -> trackDupsState(tracks, editor, ignored)
                EditorTab.ARTISTS -> artistDupsState(artists, editor, ignored)
                else -> albumDupsState(albums, editor, ignored)
            }
        }
        val (title, prefix) = when (kind) {
            EditorTab.TRACKS -> "🎵 Doublons de titres" to "T:"
            EditorTab.ARTISTS -> "🎤 Doublons d'artistes" to "AR:"
            else -> "💿 Doublons d'albums" to "AL:"
        }
        DuplicatesPopup(
            title = title, pairs = pairs, ignoredCount = ignored.count { it.startsWith(prefix) },
            onIgnore = { ignore(it) }, onResetIgnored = { resetIgnored(prefix) },
            onDismiss = { dupPopup = null },
            onMerge = { chosen ->
                dupPopup = null
                exec {
                    when (kind) {
                        EditorTab.TRACKS -> editor.mergeTracksBatch(chosen)
                        EditorTab.ARTISTS -> editor.mergeArtistsBatch(chosen)
                        else -> editor.mergeAlbumsBatch(chosen)
                    }
                }
            }
        )
    }
}

/* ---------------- Doublons : modèle, compteur, popup de choix ---------------- */

private data class DupSide(val id: Long, val title: String, val subtitle: String, val cover: String?, val plays: Int, val circle: Boolean = false)
/** Une paire de doublons : [from] (le moins écouté) proposé à la fusion dans [into] (le plus écouté). */
private data class DupPair(val key: String, val from: DupSide, val into: DupSide)

private fun trackDupsState(tracks: List<RankedTrack>, editor: com.novastats.app.data.repository.DataEditorManager, ignored: Set<String>): List<DupPair> {
    val byId = tracks.associateBy { it.track.trackId }
    return editor.suggestTrackMerges(tracks.map { it.track }).mapNotNull { (a, b) ->
        val ra = byId[a.trackId] ?: return@mapNotNull null; val rb = byId[b.trackId] ?: return@mapNotNull null
        DupPair(
            "T:${a.trackId}-${b.trackId}",
            DupSide(a.trackId, a.title, listOfNotNull(ra.artistName, ra.albumTitle).joinToString(" · "), a.coverUrl, a.playCount),
            DupSide(b.trackId, b.title, listOfNotNull(rb.artistName, rb.albumTitle).joinToString(" · "), b.coverUrl, b.playCount)
        )
    }.filterNot { it.key in ignored }
}
private fun artistDupsState(artists: List<ArtistEntity>, editor: com.novastats.app.data.repository.DataEditorManager, ignored: Set<String>): List<DupPair> =
    editor.suggestArtistMerges(artists).map { (a, b) ->
        DupPair("AR:${a.artistId}-${b.artistId}", DupSide(a.artistId, a.name, "${a.distinctTracks} titres", a.photoUrl, a.playCount, circle = true), DupSide(b.artistId, b.name, "${b.distinctTracks} titres", b.photoUrl, b.playCount, circle = true))
    }.filterNot { it.key in ignored }
private fun albumDupsState(albums: List<RankedAlbum>, editor: com.novastats.app.data.repository.DataEditorManager, ignored: Set<String>): List<DupPair> {
    val byId = albums.associateBy { it.album.albumId }
    return editor.suggestAlbumMerges(albums.map { it.album }).mapNotNull { (a, b) ->
        val ra = byId[a.albumId] ?: return@mapNotNull null; val rb = byId[b.albumId] ?: return@mapNotNull null
        DupPair("AL:${a.albumId}-${b.albumId}", DupSide(a.albumId, a.title, ra.artistName, a.coverUrl, a.playCount), DupSide(b.albumId, b.title, rb.artistName, b.coverUrl, b.playCount))
    }.filterNot { it.key in ignored }
}

/** Compteur de doublons sous le sous-onglet — appui → popup de choix. */
@Composable
private fun DuplicateBadge(count: Int, noun: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = Nova.theme
    val none = count == 0
    val color = if (none) theme.textSecondary else theme.accent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (none) 0.08f else 0.14f))
            .then(if (none || !enabled) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (none) "✅ Aucun doublon $noun détecté" else "🔍 $count doublon${if (count > 1) "s" else ""} $noun détecté${if (count > 1) "s" else ""}",
            color = if (none) theme.textSecondary else theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
        )
        if (!none) Text("Choisir ▸", color = theme.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Popup de doublons : une carte par paire, case à cocher « fusionner », ⇄ pour inverser le sens (lequel on garde),
 * 🚫 Ignorer (mémorisé). Bouton « Fusionner la sélection » → une seule transaction + un seul recalcul.
 */
@Composable
private fun DuplicatesPopup(
    title: String, pairs: List<DupPair>, ignoredCount: Int,
    onIgnore: (String) -> Unit, onResetIgnored: () -> Unit, onDismiss: () -> Unit, onMerge: (List<Pair<Long, Long>>) -> Unit
) {
    val theme = Nova.theme
    var selected by remember { mutableStateOf(pairs.map { it.key }.toSet()) }
    var swapped by remember { mutableStateOf(emptySet<String>()) }
    val liveKeys = pairs.map { it.key }.toSet()
    val chosenCount = selected.count { it in liveKeys }

    NovaPopupCard(
        borderColor = theme.accent, onDismiss = onDismiss, glowDp = 10,
        banner = {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (pairs.isEmpty()) "Plus aucun doublon à traiter" else "${pairs.size} paire${if (pairs.size > 1) "s" else ""} détectée${if (pairs.size > 1) "s" else ""} · coche celles à fusionner",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
                )
            }
        }
    ) {
        // Actions globales
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { selected = if (chosenCount == pairs.size) emptySet() else liveKeys }) {
                Text(if (chosenCount == pairs.size && pairs.isNotEmpty()) "Tout décocher" else "Tout cocher", color = theme.textSecondary)
            }
            Button(
                onClick = { onMerge(pairs.filter { it.key in selected }.map { p -> if (p.key in swapped) p.into.id to p.from.id else p.from.id to p.into.id }) },
                enabled = chosenCount > 0, colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = if (theme.accent.luminance() > 0.5f) Color.Black else Color.White)
            ) { Text("🔗 Fusionner ($chosenCount)", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(4.dp))

        pairs.forEach { p ->
            val isSwapped = p.key in swapped
            val keep = if (isSwapped) p.from else p.into
            val gone = if (isSwapped) p.into else p.from
            val on = p.key in selected
            Column(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) theme.accent.copy(alpha = 0.10f) else theme.surface.copy(alpha = 0.6f))
                    .clickable { selected = if (on) selected - p.key else selected + p.key }
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = on, onCheckedChange = { selected = if (it) selected + p.key else selected - p.key }, colors = CheckboxDefaults.colors(checkedColor = theme.accent))
                    Column(Modifier.weight(1f)) {
                        DupLine(gone, label = "Disparaît", labelColor = theme.textSecondary)
                        Text("   ⬇ fusionné dans", color = theme.accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        DupLine(keep, label = "Conservé", labelColor = theme.accent)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { swapped = if (isSwapped) swapped - p.key else swapped + p.key }) { Text("⇄ Inverser", color = theme.textSecondary, style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = { onIgnore(p.key) }) { Text("🚫 Ignorer", color = theme.textSecondary, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        if (ignoredCount > 0) {
            TextButton(onClick = onResetIgnored, modifier = Modifier.fillMaxWidth()) {
                Text("Réafficher $ignoredCount paire${if (ignoredCount > 1) "s" else ""} ignorée${if (ignoredCount > 1) "s" else ""}", color = theme.textSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            "Les écoutes du doublon sont transférées vers l'élément conservé, puis toutes les statistiques sont recalculées. Annulable depuis 🕘 Historique (dernière action).",
            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun DupLine(side: DupSide, label: String, labelColor: Color) {
    val theme = Nova.theme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        CoverArt(side.cover, side.title, size = 36, circle = side.circle)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(side.title, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${side.subtitle} · ${side.plays} ▶", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(label, color = labelColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
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
