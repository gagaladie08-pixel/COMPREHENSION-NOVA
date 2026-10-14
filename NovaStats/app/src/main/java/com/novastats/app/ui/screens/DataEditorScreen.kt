package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.novastats.app.domain.TitleNormalizer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.novastats.app.data.db.dao.RecentScrobble
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.data.db.entity.ScrobbleStatus
import com.novastats.app.data.importer.BackupExporter
import com.novastats.app.service.BackupWorker
import com.novastats.app.ui.theme.Nova
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private const val EDITOR_PAGE_SIZE = 80

private enum class EditorTab(val label: String) { OVERVIEW("✦ Atelier Nova"), TRACKS("🎵 Titres"), ARTISTS("🎤 Artistes"), ALBUMS("💿 Albums"), PLAYS("▶ Écoutes"), REVIEW("⚠️ À corriger"), PROTECTED("🔒 Noms protégés"), TRASH("🗑️ Corbeille"), HISTORY("🕘 Historique") }
private enum class TrackEditorFilter(val key: String, val label: String) {
    ALL("ALL", "Tous"), REVIEW("REVIEW", "À vérifier"), NO_ALBUM("NO_ALBUM", "Sans album"), NO_COVER("NO_COVER", "Sans pochette")
}

/**
 * 🛠️ Éditeur de données (Paramètres → Éditeur). Recherche, suggestions de fusion, renommage, fusion,
 * changement d'artiste/album, corbeille restaurable, marquage « correct », historique + annulation quand possible.
 */
@Composable
fun DataEditorScreen(startOnReview: Boolean = false, focusTrackId: Long? = null) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val editor = app.editor
    val scope = rememberCoroutineScope()
    val status by editor.status.collectAsStateWithLifecycle()
    val busy by editor.busy.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(if (startOnReview) EditorTab.REVIEW else EditorTab.OVERVIEW) }
    LaunchedEffect(startOnReview, focusTrackId) { if (startOnReview) { tab = EditorTab.REVIEW; if (focusTrackId == null) ReviewUiState.section = ReviewKind.RED } }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(250); debouncedQuery = query.trim() }
    var trackFilter by remember { mutableStateOf(TrackEditorFilter.ALL) }
    var trackPage by remember { mutableStateOf<List<RankedTrack>>(emptyList()) }
    var playsPage by remember { mutableStateOf<List<RecentScrobble>>(emptyList()) }
    var pageLoading by remember { mutableStateOf(false) }
    var trackHasMore by remember { mutableStateOf(false) }
    var playsHasMore by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var operationMessage by remember { mutableStateOf<String?>(null) }

    val tracks by app.database.trackDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val trackCount by app.database.trackDao().countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val artists by app.database.artistDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by app.database.albumDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    // ⚠️ À corriger : écoutes à réviser (groupées par titre), titres « APIs épuisées », titres 🟡 flaggés (70-89)
    val reviewGroups by app.database.scrobbleDao().reviewGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val lowConfidence by app.database.trackDao().lowConfidence().collectAsStateWithLifecycle(initialValue = emptyList())
    val flagged by app.database.trackDao().flaggedForVerification().collectAsStateWithLifecycle(initialValue = emptyList())
    val trashCount by app.database.scrobbleDao().trashCountFlow().collectAsStateWithLifecycle(initialValue = 0)
    var proposals by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(flagged) { proposals = flagged.associate { it.track.trackId to (proposalLabel(app, it.track.trackId) ?: "Pochette incertaine (${it.track.confidenceScore} %)") } }
    val redItems = remember(reviewGroups, lowConfidence) { buildRedItems(reviewGroups, lowConfidence) }
    val yellowItems = remember(flagged, proposals) { buildYellowItems(flagged, proposals) }
    val history by app.database.editorDao().history(50).collectAsStateWithLifecycle(initialValue = emptyList())
    val protectedNames by app.database.artistExceptionDao().all().collectAsStateWithLifecycle(initialValue = emptyList())
    var newProtected by remember { mutableStateOf("") }

    LaunchedEffect(tab, debouncedQuery, trackFilter, refreshToken) {
        when (tab) {
            EditorTab.TRACKS -> {
                pageLoading = true
                try {
                    trackPage = withContext(Dispatchers.IO) { app.database.trackDao().searchEditorPage(debouncedQuery, trackFilter.key, EDITOR_PAGE_SIZE, 0) }
                    trackHasMore = trackPage.size == EDITOR_PAGE_SIZE
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    operationMessage = "❌ Recherche impossible : ${error.message ?: error.javaClass.simpleName}"
                    trackPage = emptyList(); trackHasMore = false
                } finally { pageLoading = false }
            }
            EditorTab.PLAYS, EditorTab.TRASH -> {
                pageLoading = true
                try {
                    val playStatus = if (tab == EditorTab.TRASH) ScrobbleStatus.TRASHED else ScrobbleStatus.CONFIRMED
                    playsPage = withContext(Dispatchers.IO) { app.database.scrobbleDao().searchEditorPage(playStatus, debouncedQuery, EDITOR_PAGE_SIZE, 0) }
                    playsHasMore = playsPage.size == EDITOR_PAGE_SIZE
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    operationMessage = "❌ Recherche impossible : ${error.message ?: error.javaClass.simpleName}"
                    playsPage = emptyList(); playsHasMore = false
                } finally { pageLoading = false }
            }
            else -> Unit
        }
    }

    var action by remember { mutableStateOf<EditorAction?>(null) }
    var backupInProgress by remember { mutableStateOf(false) }
    fun execute(block: suspend () -> Unit, preflightMessage: String? = null) {
        scope.launch {
            val previousStatus = editor.status.value
            operationMessage = preflightMessage
            backupInProgress = preflightMessage != null
            try {
                withContext(Dispatchers.IO) { block() }
                refreshToken++
                operationMessage = editor.status.value?.takeIf { it != previousStatus } ?: "✅ Action terminée."
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Un lot peut avoir appliqué certaines paires avant une erreur : recharger les pages pour refléter l'état réel.
                refreshToken++
                operationMessage = "❌ ${error.message ?: error.javaClass.simpleName}"
            } finally { backupInProgress = false }
        }
    }
    val exec: (suspend () -> Unit) -> Unit = { block -> execute(block) }
    val execProtected: (suspend () -> Unit) -> Unit = { block -> execute(block, "💾 Création et vérification de la sauvegarde avant fusion…") }
    fun loadMoreTracks() {
        if (!trackHasMore || pageLoading) return
        scope.launch {
            pageLoading = true
            try {
                val next = withContext(Dispatchers.IO) { app.database.trackDao().searchEditorPage(debouncedQuery, trackFilter.key, EDITOR_PAGE_SIZE, trackPage.size) }
                trackPage = trackPage + next
                trackHasMore = next.size == EDITOR_PAGE_SIZE
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operationMessage = "❌ Chargement impossible : ${error.message ?: error.javaClass.simpleName}"
                trackHasMore = false
            } finally { pageLoading = false }
        }
    }
    fun loadMorePlays(trashed: Boolean) {
        if (!playsHasMore || pageLoading) return
        scope.launch {
            pageLoading = true
            try {
                val playStatus = if (trashed) ScrobbleStatus.TRASHED else ScrobbleStatus.CONFIRMED
                val next = withContext(Dispatchers.IO) { app.database.scrobbleDao().searchEditorPage(playStatus, debouncedQuery, EDITOR_PAGE_SIZE, playsPage.size) }
                playsPage = playsPage + next
                playsHasMore = next.size == EDITOR_PAGE_SIZE
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operationMessage = "❌ Chargement impossible : ${error.message ?: error.javaClass.simpleName}"
                playsHasMore = false
            } finally { pageLoading = false }
        }
    }

    // Paires de doublons que l'utilisateur a choisi d'ignorer (persistant)
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("nova_editor", android.content.Context.MODE_PRIVATE) }
    var ignored by remember { mutableStateOf(prefs.getStringSet("ignored_pairs", emptySet())?.toSet() ?: emptySet()) }
    fun ignore(key: String) { ignored = ignored + key; prefs.edit().putStringSet("ignored_pairs", ignored).apply() }
    fun resetIgnored(prefix: String) { ignored = ignored.filterNot { it.startsWith(prefix) }.toSet(); prefs.edit().putStringSet("ignored_pairs", ignored).apply() }
    var dupPopup by remember { mutableStateOf<EditorTab?>(null) }
    var selectedPlayIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmTrashIds by remember { mutableStateOf<List<Long>?>(null) }
    var confirmPurgeIds by remember { mutableStateOf<List<Long>?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    LaunchedEffect(tab, debouncedQuery, refreshToken) { selectedPlayIds = emptySet() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorTab.entries.forEach { t ->
                val label = when (t) {
                    EditorTab.REVIEW -> if (redItems.isNotEmpty()) "⚠️ À corriger 🔴 ${redItems.size}" else t.label
                    EditorTab.TRASH -> if (trashCount > 0) "🗑️ Corbeille $trashCount" else t.label
                    else -> t.label
                }
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
            DuplicateBadge(dupsForTab.size, noun, enabled = !busy && !backupInProgress) { dupPopup = tab }
        }

        if (tab == EditorTab.TRACKS) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrackEditorFilter.entries.forEach { filter ->
                    NovaFilterChip(flagKey = filter, selected = trackFilter == filter, onClick = { trackFilter = filter }, label = { Text(filter.label) })
                }
            }
        }

        if (tab == EditorTab.PLAYS || tab == EditorTab.TRASH) {
            val pageIds = playsPage.map { it.scrobble.scrobbleId }.toSet()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${selectedPlayIds.size} sélectionnée(s)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { selectedPlayIds = if (pageIds.isNotEmpty() && pageIds.all { it in selectedPlayIds }) selectedPlayIds - pageIds else selectedPlayIds + pageIds },
                    enabled = pageIds.isNotEmpty() && !busy && !backupInProgress
                ) { Text(if (pageIds.isNotEmpty() && pageIds.all { it in selectedPlayIds }) "Désélectionner la page" else "Sélectionner la page", color = theme.primary) }
            }
            if (selectedPlayIds.isNotEmpty()) {
                TextButton(onClick = { selectedPlayIds = emptySet() }, modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp), enabled = !busy && !backupInProgress) {
                    Text("Tout désélectionner", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tab == EditorTab.PLAYS) {
                        Button(
                            onClick = { confirmTrashIds = selectedPlayIds.toList() }, enabled = !busy && !backupInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.secondary)
                        ) { Text("🗑️ Mettre en corbeille") }
                    } else {
                        Button(
                            onClick = { exec { editor.restoreTrashed(selectedPlayIds.toList()) }; selectedPlayIds = emptySet() },
                            enabled = !busy && !backupInProgress, colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) { Text("↩️ Restaurer") }
                        Button(
                            onClick = { confirmPurgeIds = selectedPlayIds.toList() }, enabled = !busy && !backupInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                        ) { Text("⚠️ Purger") }
                    }
                }
            }
        }

        if (tab == EditorTab.TRASH && trashCount > 0) {
            TextButton(
                onClick = { confirmEmptyTrash = true }, enabled = !busy && !backupInProgress,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)
            ) { Text("Vider toute la corbeille…", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) }
        }

        if (tab in setOf(EditorTab.TRACKS, EditorTab.ARTISTS, EditorTab.ALBUMS, EditorTab.PLAYS, EditorTab.TRASH)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(if (tab == EditorTab.TRASH) "🔍 Rechercher dans la corbeille…" else "🔍 Rechercher dans la bibliothèque…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (busy || backupInProgress || pageLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = theme.accent)
        (operationMessage ?: status)?.let {
            Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        val q = query.trim().lowercase()
        val historyTop = history.firstOrNull()
        val canUndoLatest = historyTop?.let { com.novastats.app.data.repository.DataEditorManager.canUndo(it) } == true
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                EditorTab.OVERVIEW -> {
                    item(key = "overview") {
                        AtelierOverview(
                            trackCount = trackCount, artistCount = artists.size, albumCount = albums.size,
                            redCount = redItems.size, yellowCount = yellowItems.size, trashCount = trashCount,
                            latestHistory = historyTop, busy = busy || backupInProgress,
                            onOpen = { tab = it }
                        )
                    }
                }
                EditorTab.TRACKS -> {
                    if (trackPage.isEmpty() && !pageLoading) item { EditorEmptyState("Aucun titre ne correspond à cette recherche.") }
                    items(trackPage, key = { "t" + it.track.trackId }) { t ->
                        EditorRow(t.track.title, "${t.artistName}${t.albumTitle?.let { " · $it" } ?: ""} · ${t.periodPlays} ▶", t.track.coverUrl) { action = EditorAction.Track(t) }
                    }
                    item(key = "track-page-footer") {
                        EditorPageFooter(trackPage.size, trackHasMore, pageLoading, "titres") { loadMoreTracks() }
                    }
                }
                EditorTab.ARTISTS -> {
                    val filtered = artists.filter { q.isEmpty() || it.name.lowercase().contains(q) }
                    if (filtered.isEmpty()) item { EditorEmptyState("Aucun artiste ne correspond à cette recherche.") }
                    items(filtered, key = { "a" + it.artistId }) { a ->
                        EditorRow(a.name, "${a.playCount} ▶", a.photoUrl, circle = true) { action = EditorAction.Artist(a) }
                    }
                }
                EditorTab.ALBUMS -> {
                    val filtered = albums.filter { q.isEmpty() || it.album.title.lowercase().contains(q) || it.artistName.lowercase().contains(q) }
                    if (filtered.isEmpty()) item { EditorEmptyState("Aucun album ne correspond à cette recherche.") }
                    items(filtered, key = { "al" + it.album.albumId }) { al ->
                        EditorRow(al.album.title, "${al.artistName} · ${al.periodPlays} ▶", al.album.coverUrl) { action = EditorAction.Album(al) }
                    }
                }
                EditorTab.PLAYS, EditorTab.TRASH -> {
                    val isTrash = tab == EditorTab.TRASH
                    item(key = if (isTrash) "trash-note" else "plays-note") {
                        Text(
                            if (isTrash) "Les écoutes placées ici ne comptent plus dans les statistiques. Tu peux les restaurer ou les supprimer définitivement après confirmation."
                            else "Toutes les écoutes confirmées, recherchables et paginées. Sélectionne celles à déplacer vers la corbeille ; elles pourront être restaurées.",
                            color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 8.dp)
                        )
                    }
                    if (playsPage.isEmpty() && !pageLoading) item { EditorEmptyState(if (isTrash) "La corbeille est vide ou aucun résultat ne correspond." else "Aucune écoute ne correspond à cette recherche.") }
                    items(playsPage, key = { "s" + it.scrobble.scrobbleId }) { s ->
                        PlaySelectionRow(
                            scrobble = s, selected = s.scrobble.scrobbleId in selectedPlayIds,
                            onSelected = { checked ->
                                val id = s.scrobble.scrobbleId
                                selectedPlayIds = if (checked) selectedPlayIds + id else selectedPlayIds - id
                            },
                            onClick = { action = EditorAction.Play(s.scrobble, s.title, isTrash) }
                        )
                    }
                    item(key = if (isTrash) "trash-page-footer" else "plays-page-footer") {
                        EditorPageFooter(playsPage.size, playsHasMore, pageLoading, "écoutes") { loadMorePlays(isTrash) }
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
                    if (protectedNames.isEmpty()) item { EditorEmptyState("Aucun nom protégé.") }
                    items(protectedNames, key = { "p" + it.id }) { e ->
                        Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔒 ${e.name}", color = theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { exec { app.database.artistExceptionDao().delete(e.id); app.library.loadArtistExceptions(seedDefaults = false); app.library.clearCaches() } }, enabled = !busy) { Text("Retirer", color = theme.secondary) }
                        }
                    }
                }
                EditorTab.HISTORY -> {
                    item(key = "undo-action") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Button(
                                onClick = { exec { editor.undo() } }, enabled = canUndoLatest && !busy && !backupInProgress,
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                            ) { Text(when { historyTop == null -> "Aucune action à annuler"; canUndoLatest -> "↩️ Annuler la dernière action"; else -> "Annulation indisponible" }) }
                            if (historyTop == null) Text("Aucune modification pour l'instant.", color = theme.textSecondary, modifier = Modifier.padding(top = 8.dp))
                            else if (!canUndoLatest) Text(
                                com.novastats.app.data.repository.DataEditorManager.undoUnavailableReason(historyTop),
                                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                            )
                            else Text("L'annulation restaure uniquement cette dernière action et conserve les autres entrées de l'historique.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    items(history, key = { "h" + it.id }) { h ->
                        Column(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                            Text("${editTypeEmoji(h.type)} ${editTypeLabel(h.type)} · ${editHistoryTarget(h)}", color = theme.text, fontWeight = FontWeight.SemiBold)
                            Text("${editHistorySummary(h)} · ${playFmt.format(h.createdAt)}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                            if (!com.novastats.app.data.repository.DataEditorManager.canUndo(h)) Text(
                                com.novastats.app.data.repository.DataEditorManager.undoUnavailableReason(h),
                                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    action?.let { a -> EditorActionDialog(a, artists, albums, onDismiss = { action = null }, exec = exec, execProtected = execProtected) }

    confirmTrashIds?.let { ids ->
        val selectedTitles = playsPage.filter { it.scrobble.scrobbleId in ids }.take(4)
        AlertDialog(
            onDismissRequest = { confirmTrashIds = null }, containerColor = theme.surface,
            title = { Text("Mettre ${ids.size} écoute${if (ids.size > 1) "s" else ""} à la corbeille ?", color = theme.text) },
            text = {
                Column {
                    Text("Elles seront immédiatement exclues des statistiques, mais resteront restaurables depuis la corbeille.", color = theme.textSecondary)
                    selectedTitles.forEach { Text("• ${it.title} — ${it.artistName}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall) }
                    if (ids.size > selectedTitles.size) Text("… et ${ids.size - selectedTitles.size} autre(s)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { val chosen = ids.toList(); confirmTrashIds = null; selectedPlayIds = emptySet(); exec { editor.deleteScrobbles(chosen) } }) { Text("Mettre à la corbeille", color = theme.primary, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmTrashIds = null }) { Text("Garder", color = theme.textSecondary) } }
        )
    }
    confirmPurgeIds?.let { ids ->
        val selectedTitles = playsPage.filter { it.scrobble.scrobbleId in ids }.take(4)
        AlertDialog(
            onDismissRequest = { confirmPurgeIds = null }, containerColor = theme.surface,
            title = { Text("Suppression définitive", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("${ids.size} écoute${if (ids.size > 1) "s" else ""} seront supprimées de la base. Cette action ne peut pas être annulée et les écoutes ne seront plus restaurables.", color = theme.textSecondary)
                    selectedTitles.forEach { Text("• ${it.title} — ${it.artistName}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall) }
                    if (ids.size > selectedTitles.size) Text("… et ${ids.size - selectedTitles.size} autre(s)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { val chosen = ids.toList(); confirmPurgeIds = null; selectedPlayIds = emptySet(); exec { editor.permanentlyDeleteTrashed(chosen) } }) { Text("Supprimer définitivement", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmPurgeIds = null }) { Text("Annuler", color = theme.textSecondary) } }
        )
    }
    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false }, containerColor = theme.surface,
            title = { Text("Vider toute la corbeille ?", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) },
            text = { Text("${trashCount} écoute${if (trashCount > 1) "s" else ""} seront supprimées définitivement. Aucune restauration ne sera possible. Cette purge est irréversible.", color = theme.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmEmptyTrash = false; exec { editor.emptyTrash() } }) { Text("Vider définitivement", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmEmptyTrash = false }) { Text("Conserver", color = theme.textSecondary) } }
        )
    }

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
                execProtected {
                    val backupFile = BackupWorker.writeBackup(app)
                    check(BackupExporter.verify(backupFile)) { "La sauvegarde de sécurité est absente ou illisible ; aucune fusion n'a été lancée." }
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

@Composable
private fun AtelierOverview(
    trackCount: Int, artistCount: Int, albumCount: Int, redCount: Int, yellowCount: Int, trashCount: Int,
    latestHistory: com.novastats.app.data.db.entity.EditHistoryEntity?, busy: Boolean, onOpen: (EditorTab) -> Unit
) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(theme.primary.copy(alpha = 0.22f), theme.surface)))
                .border(1.dp, theme.primary.copy(alpha = 0.35f), RoundedCornerShape(22.dp)).padding(18.dp)
        ) {
            Text("ATELIER NOVA", color = theme.accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Text("Une bibliothèque plus juste, en toute sécurité.", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("Recherche dans tout le catalogue, corrections vérifiables et écoutes restaurables.", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("$trackCount titres indexés · $artistCount artistes · $albumCount albums", color = theme.text, fontWeight = FontWeight.SemiBold)
            if (busy) Text("⏳ Opération en cours…", color = theme.accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            else latestHistory?.let { Text("Dernière opération : ${editTypeLabel(it.type).lowercase()}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        }

        Text("État de la bibliothèque", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorMetricCard(Modifier.weight(1f), "🎵", "Titres", trackCount.toString(), "Explorer tout le catalogue", theme.primary) { onOpen(EditorTab.TRACKS) }
            EditorMetricCard(Modifier.weight(1f), "🎤", "Artistes", artistCount.toString(), "Recherche par nom", theme.accent) { onOpen(EditorTab.ARTISTS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorMetricCard(Modifier.weight(1f), "💿", "Albums", albumCount.toString(), "Parcourir les albums", theme.secondary) { onOpen(EditorTab.ALBUMS) }
            EditorMetricCard(Modifier.weight(1f), "⚠️", "À vérifier", "${redCount + yellowCount}", "$redCount priorité · $yellowCount à confirmer", Color(0xFFE6A700)) { onOpen(EditorTab.REVIEW) }
        }

        Text("Accès rapide", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorMetricCard(Modifier.weight(1f), "▶", "Écoutes", "Toute la bibliothèque", "Sélection + corbeille restaurable", theme.primary) { onOpen(EditorTab.PLAYS) }
            EditorMetricCard(Modifier.weight(1f), "🗑️", "Corbeille", trashCount.toString(), "Restaurer ou purger avec confirmation", Color(0xFFB3261E)) { onOpen(EditorTab.TRASH) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorMetricCard(Modifier.weight(1f), "🔍", "Corrections", "Aperçu et contrôle", "Examiner les métadonnées à vérifier", theme.accent) { onOpen(EditorTab.REVIEW) }
            EditorMetricCard(Modifier.weight(1f), "🕘", "Historique", "Actions récentes", "Annulation seulement si restaurable", theme.secondary) { onOpen(EditorTab.HISTORY) }
        }
        Text("Les fusions en lot demandent une sauvegarde de sécurité avant l'exécution. Les suppressions d'écoutes passent par la corbeille ; toute purge est définitive et confirmée.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EditorMetricCard(
    modifier: Modifier, icon: String, title: String, value: String, detail: String, accent: Color, onClick: () -> Unit
) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(theme.surface.copy(alpha = 0.78f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(13.dp)
    ) {
        Text("$icon  $title", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Text(value, color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaySelectionRow(
    scrobble: RecentScrobble, selected: Boolean, onSelected: (Boolean) -> Unit, onClick: () -> Unit
) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = onSelected, colors = CheckboxDefaults.colors(checkedColor = theme.accent))
        CoverArt(scrobble.coverUrl, scrobble.title, size = 40)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(scrobble.title, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${scrobble.artistName} · ${playFmt.format(scrobble.scrobble.startedAt)} · ${formatDuration(scrobble.scrobble.durationListenedMs)}",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text("›", color = theme.primary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun EditorPageFooter(loaded: Int, hasMore: Boolean, loading: Boolean, unit: String, onLoadMore: () -> Unit) {
    val theme = Nova.theme
    val itemLabel = when (unit) {
        "écoutes" -> "$loaded écoute${if (loaded > 1) "s" else ""} chargée${if (loaded > 1) "s" else ""}"
        else -> "$loaded titre${if (loaded > 1) "s" else ""} chargé${if (loaded > 1) "s" else ""}"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(itemLabel, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (hasMore) TextButton(onClick = onLoadMore, enabled = !loading) { Text(if (loading) "Chargement…" else "Charger plus", color = theme.primary) }
        else if (loaded > 0) Text("Fin de la liste", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorEmptyState(message: String) {
    Text(message, color = Nova.theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp))
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
 * 🚫 Ignorer (mémorisé). Chaque paire est traitée dans sa transaction, puis les statistiques sont recalculées ; un lot peut donc être partiellement appliqué si une paire échoue.
 */
@Composable
private fun DuplicatesPopup(
    title: String, pairs: List<DupPair>, ignoredCount: Int,
    onIgnore: (String) -> Unit, onResetIgnored: () -> Unit, onDismiss: () -> Unit, onMerge: (List<Pair<Long, Long>>) -> Unit
) {
    val theme = Nova.theme
    var selected by remember(pairs) { mutableStateOf(emptySet<String>()) }
    var swapped by remember(pairs) { mutableStateOf(emptySet<String>()) }
    var confirmMerge by remember { mutableStateOf(false) }
    val liveKeys = pairs.map { it.key }.toSet()
    val chosenPairs = pairs.filter { it.key in selected }
    val chosenCount = chosenPairs.size

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
                onClick = { confirmMerge = true },
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
            "Aperçu : chaque carte indique l'élément qui disparaît et celui qui sera conservé. Les fusions ne sont pas annulables depuis l'historique ; une sauvegarde vérifiée sera créée avant le lancement.",
            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp)
        )
    }

    if (confirmMerge && chosenPairs.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmMerge = false }, containerColor = theme.surface,
            title = { Text("Confirmer ${chosenPairs.size} fusion${if (chosenPairs.size > 1) "s" else ""} ?", color = theme.text) },
            text = {
                Column {
                    Text("Les éléments indiqués « Disparaît » seront transférés vers « Conservé ». Pour les titres, deux écoutes au même horodatage sont traitées comme un doublon et l'une est supprimée. La fusion n'est pas annulable. Une sauvegarde JSON sera créée et vérifiée avant le lot ; si une paire échoue, les autres peuvent déjà avoir été appliquées et le résultat précisera les réussites/échecs.", color = theme.textSecondary)
                    chosenPairs.take(6).forEach { pair ->
                        val keep = if (pair.key in swapped) pair.from else pair.into
                        val gone = if (pair.key in swapped) pair.into else pair.from
                        Text("• ${gone.title} → ${keep.title}", color = theme.text, style = MaterialTheme.typography.bodySmall)
                    }
                    if (chosenPairs.size > 6) Text("… et ${chosenPairs.size - 6} autre(s) fusion(s)", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val merges = chosenPairs.map { pair -> if (pair.key in swapped) pair.into.id to pair.from.id else pair.from.id to pair.into.id }
                    confirmMerge = false
                    onMerge(merges)
                }) { Text("Sauvegarder puis fusionner", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmMerge = false }) { Text("Revoir la sélection", color = theme.textSecondary) } }
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

private fun editTypeEmoji(t: String) = when (t) {
    "RENAME" -> "✏️"; "MERGE", "LINK_VERSION" -> "🔗"; "DELETE_PLAY", "TRASH_PURGE" -> "🗑️"
    "TRASH_RESTORE" -> "↩️"; "ARTIST_CHANGE" -> "🎤"; "ALBUM_CHANGE", "ALBUM_SHARED" -> "💿"
    "COVER_CHANGE" -> "🖼️"; "GHOST_PURGE" -> "⚠️"; else -> "✅"
}

private fun editTypeLabel(t: String) = when (t) {
    "RENAME" -> "Renommage"; "MERGE" -> "Fusion"; "LINK_VERSION" -> "Lien de version"
    "DELETE_PLAY" -> "Mise à la corbeille"; "TRASH_RESTORE" -> "Restauration de corbeille"
    "TRASH_PURGE" -> "Purge définitive"; "ARTIST_CHANGE" -> "Changement d'artiste"
    "ALBUM_CHANGE" -> "Changement d'album"; "ALBUM_SHARED" -> "Marquage multi-artistes"
    "COVER_CHANGE" -> "Modification d'image"; "REVIEWED" -> "Marqué comme correct"
    "REVIEW_IGNORE" -> "Correction ignorée"; "REVIEW_FIX" -> "Correction des métadonnées"
    "GHOST_PURGE" -> "Nettoyage définitif"; else -> t.replace('_', ' ').lowercase()
}

private fun editHistorySummary(entry: com.novastats.app.data.db.entity.EditHistoryEntity): String = when (entry.type) {
    "DELETE_PLAY" -> "${entry.extraData?.toIntOrNull()?.let { "$it écoute(s)" } ?: "Écoutes sélectionnées"} déplacée(s) vers la corbeille"
    "TRASH_RESTORE" -> "${entry.extraData?.toIntOrNull()?.let { "$it écoute(s)" } ?: "Écoutes sélectionnées"} restaurée(s)"
    "TRASH_PURGE" -> "Purge définitive · ${entry.before ?: "corbeille"}"
    else -> "${entry.before ?: "—"} → ${entry.after ?: "—"}"
}

private fun editHistoryTarget(entry: com.novastats.app.data.db.entity.EditHistoryEntity): String =
    if (entry.type == "TRASH_PURGE" && entry.entityId == 0L) "corbeille entière" else "${entry.entityType} #${entry.entityId}"

private sealed interface EditorAction {
    data class Track(val t: RankedTrack) : EditorAction
    data class Artist(val a: ArtistEntity) : EditorAction
    data class Album(val al: RankedAlbum) : EditorAction
    data class Play(val s: ScrobbleEntity, val title: String, val trashed: Boolean) : EditorAction
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
    action: EditorAction, artists: List<ArtistEntity>, albums: List<RankedAlbum>,
    onDismiss: () -> Unit, exec: (suspend () -> Unit) -> Unit, execProtected: (suspend () -> Unit) -> Unit
) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val editor = app.editor
    var mode by remember { mutableStateOf("MENU") }
    var text by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf("") }
    var trackMergeTargets by remember { mutableStateOf<List<RankedTrack>>(emptyList()) }
    var trackMergeLoading by remember { mutableStateOf(false) }
    var pendingMerge by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var pendingMergeDescription by remember { mutableStateOf("") }
    LaunchedEffect(action) {
        mode = "MENU"
        pick = ""
        pendingMerge = null
        text = when (action) { is EditorAction.Track -> action.t.track.title; is EditorAction.Artist -> action.a.name; is EditorAction.Album -> action.al.album.title; is EditorAction.Play -> "" }
    }
    LaunchedEffect(action, mode, pick) {
        if (action is EditorAction.Track && mode == "MERGE") {
            trackMergeLoading = true
            trackMergeTargets = emptyList()
            try {
                delay(180)
                val sourceId = action.t.track.trackId
                trackMergeTargets = withContext(Dispatchers.IO) {
                    app.database.trackDao().searchEditorPage(pick.trim(), "ALL", 8, 0).filterNot { it.track.trackId == sourceId }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                trackMergeTargets = emptyList()
            } finally { trackMergeLoading = false }
        } else {
            trackMergeTargets = emptyList()
            trackMergeLoading = false
        }
    }
    val title = when (action) {
        is EditorAction.Track -> "🎵 ${action.t.track.title}"; is EditorAction.Artist -> "🎤 ${action.a.name}"
        is EditorAction.Album -> "💿 ${action.al.album.title}"; is EditorAction.Play -> "▶ ${action.title}"
    }
    fun stageMerge(from: Long, into: Long, description: String) {
        pendingMerge = from to into
        pendingMergeDescription = description
        mode = "CONFIRM_MERGE"
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
                                MenuItem("🎤 Changer l'artiste") { text = artists.firstOrNull { it.artistId == action.t.track.artistId }?.name ?: action.t.artistName; mode = "ARTIST" }
                                MenuItem("💿 Changer l'album") { text = action.t.albumTitle ?: ""; mode = "ALBUM" }
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
                                if (action.al.album.isShared) MenuItem("👤 Retirer la marque multi-artistes (redécouper par artiste)") { exec { editor.setAlbumShared(action.al.album.albumId, false) }; onDismiss() }
                                else MenuItem("💿 Marquer multi-artistes (BO, album d'événement)") { exec { editor.setAlbumShared(action.al.album.albumId, true) }; onDismiss() }
                            }
                            is EditorAction.Play -> {
                                if (action.trashed) {
                                    Text("Cette écoute est dans la corbeille et n'est pas incluse dans les statistiques.", color = theme.textSecondary)
                                    MenuItem("↩️ Restaurer cette écoute") { exec { editor.restoreTrashed(listOf(action.s.scrobbleId)) }; onDismiss() }
                                    MenuItem("⚠️ Supprimer définitivement…") { mode = "CONFIRM_PLAY_PURGE" }
                                } else {
                                    Text("Cette écoute sera exclue des statistiques, mais restera restaurable dans la corbeille.", color = theme.textSecondary)
                                    MenuItem("🗑️ Mettre à la corbeille…") { mode = "CONFIRM_PLAY_TRASH" }
                                }
                            }
                        }
                    }
                    "RENAME", "ARTIST", "ALBUM", "IMAGE" -> {
                        val label = when (mode) { "RENAME" -> "Nouveau nom"; "ARTIST" -> "Nom de l'artiste"; "ALBUM" -> "Titre de l'album (vide = aucun)"; else -> "URL de l'image (vide = retirer)" }
                        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val before = when (action) {
                            is EditorAction.Track -> if (mode == "ARTIST") artists.firstOrNull { it.artistId == action.t.track.artistId }?.name ?: action.t.artistName else if (mode == "ALBUM") action.t.albumTitle ?: "(aucun album)" else action.t.track.title
                            is EditorAction.Artist -> if (mode == "IMAGE") action.a.photoUrl ?: "(aucune photo)" else action.a.name
                            is EditorAction.Album -> if (mode == "IMAGE") action.al.album.coverUrl ?: "(aucune pochette)" else action.al.album.title
                            else -> ""
                        }
                        val after = text.trim().ifBlank { if (mode == "ALBUM" || mode == "IMAGE") "(retirer / aucun)" else "(vide)" }
                        Text("Aperçu — Avant : $before", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        Text("Après : $after", color = theme.accent, style = MaterialTheme.typography.bodySmall)
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
                        Text("Choisis la cible qui sera conservée. Une confirmation et une sauvegarde vérifiée précèdent la fusion.", style = MaterialTheme.typography.bodySmall, color = theme.textSecondary)
                        when (action) {
                            is EditorAction.Artist -> artists.filter { it.artistId != action.a.artistId && (q.isEmpty() || it.name.lowercase().contains(q)) }.take(8).forEach { target ->
                                MenuItem("🎤 ${target.name} (${target.playCount} ▶)") {
                                    stageMerge(action.a.artistId, target.artistId, "L'artiste « ${action.a.name} » disparaîtra ; ses titres et écoutes seront transférés vers « ${target.name} ». Les albums et titres homonymes pourront aussi être fusionnés.")
                                }
                            }
                            is EditorAction.Album -> albums.filter { it.album.albumId != action.al.album.albumId && (q.isEmpty() || it.album.title.lowercase().contains(q)) }.take(8).forEach { target ->
                                MenuItem("💿 ${target.album.title} — ${target.artistName}") {
                                    stageMerge(action.al.album.albumId, target.album.albumId, "L'album « ${action.al.album.title} » disparaîtra ; ses titres et écoutes seront transférés vers « ${target.album.title} ».")
                                }
                            }
                            is EditorAction.Track -> {
                                if (trackMergeLoading) Text("Recherche dans tout le catalogue…", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                                else if (trackMergeTargets.isEmpty()) Text("Aucun titre cible trouvé.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                                trackMergeTargets.forEach { target ->
                                    MenuItem("🎵 ${target.track.title} — ${target.artistName}") {
                                        stageMerge(action.t.track.trackId, target.track.trackId, "Le titre « ${action.t.track.title} » sera fusionné dans « ${target.track.title} ». Ses écoutes seront déplacées ; celles partageant le même horodatage que la cible seront traitées comme doublons et supprimées.")
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                    "CONFIRM_MERGE" -> {
                        Text(pendingMergeDescription, color = theme.text)
                        Text("Cette fusion n'est pas annulable depuis l'historique. Une sauvegarde sera créée puis vérifiée ; en cas d'échec, la fusion ne sera pas exécutée.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    "CONFIRM_PLAY_TRASH" -> Text(
                        "Écoute du ${playFmt.format((action as EditorAction.Play).s.startedAt)} : elle sera retirée des statistiques et placée dans la corbeille. Tu pourras la restaurer ensuite.",
                        color = theme.textSecondary
                    )
                    "CONFIRM_PLAY_PURGE" -> Text(
                        "Écoute du ${playFmt.format((action as EditorAction.Play).s.startedAt)} : elle sera supprimée définitivement de la base. Cette action est irréversible.",
                        color = Color(0xFFB3261E)
                    )
                }
            }
        },
        confirmButton = {
            when {
                mode == "CONFIRM_MERGE" && pendingMerge != null -> TextButton(onClick = {
                    val merge = pendingMerge
                    if (merge != null) {
                        pendingMerge = null
                        execProtected {
                            val backupFile = BackupWorker.writeBackup(app)
                            check(BackupExporter.verify(backupFile)) { "La sauvegarde de sécurité est absente ou illisible ; aucune fusion n'a été lancée." }
                            when (action) {
                                is EditorAction.Artist -> editor.mergeArtists(merge.first, merge.second)
                                is EditorAction.Album -> editor.mergeAlbums(merge.first, merge.second)
                                is EditorAction.Track -> editor.mergeTracks(merge.first, merge.second)
                                else -> error("Type de fusion invalide")
                            }
                        }
                        onDismiss()
                    }
                }) { Text("Sauvegarder puis fusionner", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) }
                action is EditorAction.Play && !action.trashed && mode == "CONFIRM_PLAY_TRASH" -> TextButton(onClick = {
                    exec { editor.deleteScrobble(action.s.scrobbleId) }; onDismiss()
                }) { Text("Mettre à la corbeille", color = theme.primary, fontWeight = FontWeight.Bold) }
                action is EditorAction.Play && action.trashed && mode == "CONFIRM_PLAY_PURGE" -> TextButton(onClick = {
                    exec { editor.permanentlyDeleteTrashed(listOf(action.s.scrobbleId)) }; onDismiss()
                }) { Text("Supprimer définitivement", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold) }
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
        dismissButton = {
            TextButton(onClick = {
                when {
                    mode == "MENU" -> onDismiss()
                    mode == "CONFIRM_MERGE" -> { mode = "MERGE"; pendingMerge = null }
                    mode == "CONFIRM_PLAY_TRASH" || mode == "CONFIRM_PLAY_PURGE" -> mode = "MENU"
                    else -> mode = "MENU"
                }
            }) { Text(if (mode == "MENU") "Fermer" else "‹ Retour", color = theme.textSecondary) }
        }
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(label, color = Nova.theme.text, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp))
}
