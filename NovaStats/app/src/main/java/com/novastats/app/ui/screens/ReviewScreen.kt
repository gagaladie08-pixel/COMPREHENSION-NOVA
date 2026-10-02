package com.novastats.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.RankedTrack
import com.novastats.app.data.db.dao.ReviewGroup
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.domain.DataType
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.data.repository.DataEditorManager
import com.novastats.app.domain.ReviewRules
import com.novastats.app.domain.ScoredCandidate
import com.novastats.app.domain.TitleNormalizer
import com.novastats.app.ui.theme.Nova
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * ⚠️ À corriger — file d'attente des écoutes douteuses ou incomplètes (acceptées et comptées, mais à vérifier).
 *   🔴 À corriger : score < 70, titre/artiste inconnu, métadonnées incomplètes, APIs épuisées (< 70)
 *   🟡 À vérifier : correspondance API acceptée avec flag (70-89)
 * Deux segments côte à côte, liste de cartes, popup plein écran (bandeau · champs · propositions · écoutes cochables).
 */

val ReviewRed = Color(0xFFE74C3C)
val ReviewYellow = Color(0xFFF1C40F)

enum class ReviewKind(val emoji: String, val label: String, val color: Color) {
    RED("🔴", "À corriger", ReviewRed), YELLOW("🟡", "À vérifier", ReviewYellow)
}

/** Dernier segment choisi (gardé pendant la session). */
object ReviewUiState { var section: ReviewKind = ReviewKind.RED }

/** Une carte de la liste : un titre + ses écoutes concernées. */
data class ReviewItem(
    val kind: ReviewKind,
    val trackId: Long, val title: String, val artist: String, val album: String?, val cover: String?,
    val score: Int, val source: String?, val sourceApp: String?,
    /** Problème détecté (🔴) ou valeur proposée (🟡). */
    val detail: String,
    val count: Int, val lastAt: Long?,
    val rawTitle: String?, val rawArtist: String?, val rawAlbum: String?,
    val durationMs: Long?,
    /** Écoutes marquées needs_review (vide → toutes les écoutes du titre sont concernées). */
    val flaggedOnly: Boolean
)

private val dateFmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale.FRANCE)

/** Construit les cartes 🔴 : groupes d'écoutes à réviser + titres « APIs épuisées » (score < 70) non déjà présents. */
fun buildRedItems(groups: List<ReviewGroup>, lowConfidence: List<RankedTrack>): List<ReviewItem> {
    val fromGroups = groups.map { g ->
        ReviewItem(
            ReviewKind.RED, g.trackId, g.title, g.artistName, g.albumTitle, g.coverUrl, g.minScore, g.detectionSource, g.sourceApp,
            g.reason ?: "Score de confiance ${g.minScore} %", g.count, g.lastAt, g.rawTitle, g.rawArtist, g.rawAlbum, null, flaggedOnly = true
        )
    }
    val seen = fromGroups.map { it.trackId }.toSet()
    val fromTracks = lowConfidence.filter { it.track.trackId !in seen }.map { t ->
        ReviewItem(
            ReviewKind.RED, t.track.trackId, t.track.title, t.artistName, t.albumTitle, t.track.coverUrl, t.track.confidenceScore, null, null,
            "APIs épuisées · pochette incertaine (${t.track.confidenceScore} %)", t.periodPlays, t.track.lastPlayedAt, t.track.titleRaw, null, null, t.track.durationMs, flaggedOnly = false
        )
    }
    return fromGroups + fromTracks
}

fun buildYellowItems(flagged: List<RankedTrack>, proposals: Map<Long, String>): List<ReviewItem> = flagged.map { t ->
    ReviewItem(
        ReviewKind.YELLOW, t.track.trackId, t.track.title, t.artistName, t.albumTitle, t.track.coverUrl, t.track.confidenceScore, null, null,
        proposals[t.track.trackId] ?: "Pochette ${t.track.coverSource ?: "API"} (${t.track.confidenceScore} %)", t.periodPlays, t.track.lastPlayedAt,
        t.track.titleRaw, null, null, t.track.durationMs, flaggedOnly = false
    )
}

/* ====================================================================================== */
/*  Section (segments + liste)                                                              */
/* ====================================================================================== */

@Composable
fun ReviewSection(
    red: List<ReviewItem>, yellow: List<ReviewItem>, focusTrackId: Long?,
    artists: List<ArtistEntity>, albums: List<AlbumEntity>, busy: Boolean, exec: (suspend () -> Unit) -> Unit
) {
    val theme = Nova.theme
    var section by remember { mutableStateOf(ReviewUiState.section) }
    var open by remember { mutableStateOf<ReviewItem?>(null) }
    // Ouverture directe depuis la notification 🟡 / le raccourci
    LaunchedEffect(focusTrackId, red.size, yellow.size) {
        if (focusTrackId != null && open == null) {
            (yellow.firstOrNull { it.trackId == focusTrackId } ?: red.firstOrNull { it.trackId == focusTrackId })?.let { open = it; section = it.kind; ReviewUiState.section = it.kind }
        }
    }
    val items = if (section == ReviewKind.RED) red else yellow

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReviewKind.entries.forEach { k ->
                val n = if (k == ReviewKind.RED) red.size else yellow.size
                val on = section == k
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (on) k.color.copy(alpha = 0.22f) else theme.surface)
                        .border(1.dp, if (on) k.color else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { section = k; ReviewUiState.section = k }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${k.emoji} ${k.label} ($n)", color = if (on) theme.text else theme.textSecondary, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = theme.textSecondary.copy(alpha = 0.2f))
        if (items.isEmpty()) {
            Text(
                if (section == ReviewKind.RED) "Rien à corriger ✅" else "Rien à vérifier ✅",
                color = theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        } else items.forEach { item -> ReviewCard(item) { open = item } }
    }

    open?.let { item ->
        ReviewPopup(item, artists, albums, busy = busy, onDismiss = { open = null }, exec = exec)
    }
}

@Composable
private fun ReviewCard(item: ReviewItem, onClick: () -> Unit) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(theme.surface).border(1.dp, item.kind.color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(item.cover, item.title, size = 48)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.artist, item.album).joinToString(" · "), color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.kind.emoji} ${item.score} % · ${ReviewRules.sourceLabel(item.source)}${item.sourceApp?.let { " (${it.substringAfterLast('.')})" } ?: ""} · ${item.detail}",
                color = item.kind.color, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${item.count} ▶", color = theme.primary, fontWeight = FontWeight.Bold)
            item.lastAt?.let { Text(SimpleDateFormat("d MMM", Locale.FRANCE).format(it), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/* ====================================================================================== */
/*  Popup plein écran                                                                       */
/* ====================================================================================== */

@Composable
private fun ReviewPopup(
    item: ReviewItem, artists: List<ArtistEntity>, albums: List<AlbumEntity>, busy: Boolean,
    onDismiss: () -> Unit, exec: (suspend () -> Unit) -> Unit
) {
    val theme = Nova.theme
    val ctx = LocalContext.current
    val app = ctx.applicationContext as NovaStatsApp
    val editor = app.editor
    val color = item.kind.color

    // Champs modifiables (vides si « Inconnu »)
    var title by remember(item.trackId) { mutableStateOf(if (ReviewRules.isUnknown(item.title)) "" else item.title) }
    var artistText by remember(item.trackId) { mutableStateOf(if (ReviewRules.isUnknown(item.artist)) "" else item.artist) }
    var albumText by remember(item.trackId) { mutableStateOf(item.album ?: "") }
    var coverUrl by remember(item.trackId) { mutableStateOf(item.cover ?: "") }
    var artistFocus by remember { mutableStateOf(false) }
    var albumFocus by remember { mutableStateOf(false) }

    // Écoutes concernées
    var plays by remember(item.trackId) { mutableStateOf<List<ScrobbleEntity>>(emptyList()) }
    var checked by remember(item.trackId) { mutableStateOf<Set<Long>>(emptySet()) }
    var playsExpanded by remember { mutableStateOf(false) }
    var playsShown by remember { mutableIntStateOf(20) }
    LaunchedEffect(item.trackId) {
        val dao = app.database.scrobbleDao()
        val flagged = if (item.flaggedOnly) dao.reviewOfTrack(item.trackId) else emptyList()
        plays = flagged.ifEmpty { dao.allOfTrack(item.trackId) }
        checked = plays.map { it.scrobbleId }.toSet()
    }

    // Propositions de l'app (jusqu'à 3)
    var proposals by remember(item.trackId) { mutableStateOf<List<ScoredCandidate>?>(null) }
    LaunchedEffect(item.trackId) {
        proposals = runCatching { app.enricher.proposeTrack(item.rawTitle ?: item.title, item.rawArtist ?: item.artist, item.album, item.durationMs) }.getOrDefault(emptyList())
    }

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            coverUrl = uri.toString()
        }
    }

    val canValidate = checked.isNotEmpty() && title.isNotBlank() && !busy
    val allChecked = checked.size == plays.size && plays.isNotEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(theme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            /* ---- Barre haute fixe ---- */
            Row(Modifier.fillMaxWidth().background(theme.surface).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Text("✕", color = theme.text, fontWeight = FontWeight.Bold) }
                Text("Corriger ce titre", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${item.kind.emoji} ${item.score} %", color = color, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp))
                Box {
                    IconButton(onClick = { menu = true }) { Text("⋮", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (item.kind == ReviewKind.YELLOW) DropdownMenuItem(text = { Text("✅ C'est correct") }, onClick = { menu = false; exec { editor.confirmReview(item.trackId) }; onDismiss() })
                        DropdownMenuItem(text = { Text("🚫 Ignorer (garder tel quel)") }, onClick = { menu = false; exec { editor.ignoreReview(item.trackId, item.rawTitle ?: item.title, item.rawArtist ?: item.artist) }; onDismiss() })
                        DropdownMenuItem(text = { Text("🗑️ Supprimer les écoutes cochées", color = ReviewRed) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }

            /* ---- Contenu défilant ---- */
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // 1. Bandeau d'info
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(coverUrl.ifBlank { null }, item.title, size = 56)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${item.kind.emoji} ${item.kind.label} · score ${item.score} %", color = color, fontWeight = FontWeight.Bold)
                            Text("Source : ${ReviewRules.sourceLabel(item.source)}${item.sourceApp?.let { " · $it" } ?: ""}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    InfoLine(if (item.kind == ReviewKind.RED) "Problème détecté" else "Valeur proposée", item.detail)
                    InfoLine("Valeur brute (lecteur)", listOfNotNull(item.rawArtist, item.rawTitle).joinToString(" — ").ifBlank { "—" } + (item.rawAlbum?.let { " · $it" } ?: ""))
                    InfoLine("Écoutes concernées", "${plays.size}${plays.firstOrNull()?.let { " · dernière le ${dateFmt.format(it.startedAt)}" } ?: ""}")
                }

                // 2. Champs modifiables
                SectionLabel("✏️ Champs modifiables", color)
                val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = color, unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f), focusedTextColor = theme.text, unfocusedTextColor = theme.text, cursorColor = color)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("✏️ Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = artistText, onValueChange = { artistText = it; artistFocus = true }, label = { Text("🎤 Artiste(s) — « A feat. B », « A & B »…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = fieldColors
                )
                val parsedArtists = remember(artistText) { TitleNormalizer.splitArtists(artistText) }
                if (parsedArtists.size > 1) Text("→ principal : ${parsedArtists.first()} · feat. ${parsedArtists.drop(1).joinToString(", ")}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                if (artistFocus) {
                    val q = artistText.trim().lowercase()
                    artists.filter { q.length >= 2 && it.name.lowercase().contains(q) && !it.name.equals(artistText.trim(), true) }.take(5).forEach { a ->
                        SuggestionChip("🎤 ${a.name} (${a.playCount} ▶)") { artistText = a.name; artistFocus = false }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = albumText, onValueChange = { albumText = it; albumFocus = true }, label = { Text("💿 Album (vide = aucun)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                if (albumFocus) {
                    val q = albumText.trim().lowercase()
                    val primary = parsedArtists.firstOrNull()?.let { n -> artists.firstOrNull { it.name.equals(n, true) }?.artistId }
                    albums.filter { q.length >= 2 && it.title.lowercase().contains(q) && !it.title.equals(albumText.trim(), true) }
                        .sortedByDescending { (if (it.artistId == primary) 1_000_000 else 0) + it.playCount }.take(5)
                        .forEach { al -> SuggestionChip("💿 ${al.title}") { albumText = al.title; albumFocus = false } }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = coverUrl, onValueChange = { coverUrl = it }, label = { Text("🖼️ Pochette (URL ou image de la galerie)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { gallery.launch(arrayOf("image/*")) }) { Text("🖼️ Galerie", color = theme.primary) }
                    TextButton(onClick = {
                        val q = Uri.encode("${artistText.ifBlank { item.artist }} ${title.ifBlank { item.title }} cover")
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("🌐 Rechercher sur le web", color = theme.primary) }
                    if (coverUrl.isNotBlank()) TextButton(onClick = { coverUrl = "" }) { Text("Retirer", color = theme.textSecondary) }
                }
                Text("La photo d'artiste se modifie dans l'onglet 🎤 Artistes.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)

                // 3. Propositions de l'app
                val props = proposals
                if (props == null) {
                    SectionLabel("💡 Propositions de l'app", color)
                    Text("Recherche en cours…", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                } else if (props.isNotEmpty()) {
                    SectionLabel("💡 Propositions de l'app — un appui remplit tous les champs", color)
                    props.forEach { p ->
                        val c = p.candidate
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(theme.surface)
                                .clickable { title = c.name; c.artist?.let { artistText = it }; c.album?.let { albumText = it }; c.imageUrl?.let { coverUrl = it } }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArt(c.imageUrl, c.name, size = 40)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name + (c.artist?.let { " — $it" } ?: ""), color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${c.source.emoji} ${c.source.label}${c.album?.let { " · $it" } ?: ""}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${p.score} %", color = if (p.score >= 90) Color(0xFF2ECC71) else ReviewYellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 4. Écoutes concernées (pliable)
                SectionLabel("", color)
                Row(Modifier.fillMaxWidth().clickable { playsExpanded = !playsExpanded }, verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (playsExpanded) "▾" else "▸"} ${plays.size} écoute${if (plays.size > 1) "s" else ""} concernée${if (plays.size > 1) "s" else ""} · ${checked.size} cochée${if (checked.size > 1) "s" else ""}", color = theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { checked = if (allChecked) emptySet() else plays.map { it.scrobbleId }.toSet() }) { Text(if (allChecked) "Tout décocher" else "Tout cocher", color = theme.primary, style = MaterialTheme.typography.labelMedium) }
                }
                if (playsExpanded) {
                    plays.take(playsShown).forEach { s ->
                        val on = s.scrobbleId in checked
                        Row(Modifier.fillMaxWidth().clickable { checked = if (on) checked - s.scrobbleId else checked + s.scrobbleId }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = { checked = if (it) checked + s.scrobbleId else checked - s.scrobbleId }, colors = CheckboxDefaults.colors(checkedColor = color))
                            Column(Modifier.weight(1f)) {
                                Text(dateFmt.format(s.startedAt), color = theme.text, style = MaterialTheme.typography.bodyMedium)
                                Text("${s.sourceApp?.substringAfterLast('.') ?: "—"} · ${ReviewRules.sourceLabel(s.detectionSource)} · ${formatDuration(s.durationListenedMs)} · ${s.confidenceScore} %${s.reviewReason?.let { " · $it" } ?: ""}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (plays.size > playsShown) TextButton(onClick = { playsShown += 20 }, modifier = Modifier.fillMaxWidth()) { Text("Voir plus (${plays.size - playsShown} restantes)", color = theme.primary) }
                }
                if (checked.isEmpty() && plays.isNotEmpty()) Text("Coche au moins une écoute pour pouvoir valider.", color = ReviewRed, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(16.dp))
            }

            /* ---- Barre basse fixe ---- */
            HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth().background(theme.surface).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Annuler", color = theme.textSecondary) }
                Button(
                    enabled = canValidate,
                    onClick = {
                        exec {
                            editor.applyReviewFix(
                                DataEditorManager.ReviewFix(
                                    trackId = item.trackId, scrobbleIds = checked.toList(), allChecked = allChecked,
                                    title = title, artists = artistText, album = albumText.ifBlank { null }, coverUrl = coverUrl.ifBlank { null },
                                    rawTitle = item.rawTitle ?: item.title, rawArtist = item.rawArtist ?: item.artist, rawAlbum = item.rawAlbum ?: item.album
                                )
                            )
                        }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                ) { Text("✅ Valider${if (checked.isNotEmpty() && !allChecked) " (${checked.size})" else ""}", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = theme.surface, titleContentColor = theme.text, textContentColor = theme.textSecondary,
            title = { Text("🗑️ Supprimer ${checked.size} écoute${if (checked.size > 1) "s" else ""} ?") },
            text = { Text("Les écoutes cochées seront définitivement supprimées et toutes les statistiques recalculées. Cette action est irréversible.") },
            confirmButton = {
                TextButton(enabled = checked.isNotEmpty(), onClick = { confirmDelete = false; exec { editor.deleteScrobbles(checked.toList()) }; onDismiss() }) {
                    Text("Supprimer", color = ReviewRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text("$label : ", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = theme.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    if (text.isBlank()) { Spacer(Modifier.height(12.dp)); return }
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val theme = Nova.theme
    Text(
        text, color = theme.text, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(theme.surface).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** Valeur proposée (🟡) : dernière correspondance positive en cache pour ce titre. */
suspend fun proposalLabel(app: NovaStatsApp, trackId: Long): String? =
    app.database.apiCacheDao().valid(EntityType.TRACK, trackId, DataType.COVER)?.let { "Pochette ${it.source} (${it.confidenceScore} %)" }
