package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.api.MetadataEnricher
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.domain.TitleNormalizer
import com.novastats.app.service.EnrichmentWorker
import com.novastats.app.ui.theme.Nova

/** Section de la page « Ré-enrichir… ». */
private enum class PickSection(val emoji: String, val label: String, val type: String) {
    ARTISTS("🎤", "Artistes", EntityType.ARTIST),
    ALBUMS("💿", "Albums", EntityType.ALBUM),
    TRACKS("🎵", "Titres", EntityType.TRACK)
}

private data class PickItem(val type: String, val id: Long, val title: String, val subtitle: String, val imageUrl: String?, val circle: Boolean = false) {
    val key get() = "$type:$id"
}

/**
 * Page « 🔄 Ré-enrichir… » : trois sections (Artistes / Albums / Titres) + icône recherche, cases à cocher,
 * puis « Lancer (N) » → EnrichmentWorker en mode sélection (cache ignoré, cascade complète par élément).
 */
@Composable
fun ReenrichPicker(onBack: () -> Unit) {
    val theme = Nova.theme
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp

    val tracks by app.database.trackDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by app.database.artistDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by app.database.albumDao().allForEditor().collectAsStateWithLifecycle(initialValue = emptyList())

    var section by rememberSaveable { mutableStateOf(PickSection.ARTISTS) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }

    val all = remember(section, tracks, artists, albums) {
        when (section) {
            PickSection.ARTISTS -> artists.map { PickItem(EntityType.ARTIST, it.artistId, it.name, "${it.playCount} écoutes${if (it.photoUrl == null) " · sans photo" else ""}", it.photoUrl, circle = true) }
            PickSection.ALBUMS -> albums.map { PickItem(EntityType.ALBUM, it.album.albumId, it.album.title, "${it.artistName} · ${it.periodPlays} écoutes${if (it.album.coverUrl == null) " · sans pochette" else ""}", it.album.coverUrl) }
            PickSection.TRACKS -> tracks.map { PickItem(EntityType.TRACK, it.track.trackId, it.track.title, "${it.artistName} · ${it.periodPlays} écoutes${if (it.track.coverUrl == null) " · sans pochette" else ""}", it.track.coverUrl) }
        }
    }
    val shown = remember(all, query) {
        val q = TitleNormalizer.normalizeKey(query)
        if (q.isBlank()) all else all.filter { TitleNormalizer.normalizeKey(it.title).contains(q) || TitleNormalizer.normalizeKey(it.subtitle).contains(q) }
    }
    val countFor = { s: PickSection -> selected.count { it.startsWith(s.type + ":") } }
    val shownKeys = shown.map { it.key }
    val allShownChecked = shownKeys.isNotEmpty() && shownKeys.all { it in selected }

    Column(Modifier.fillMaxSize()) {
        /* ---- En-tête : retour · titre · 🔍 ---- */
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ APIs", color = theme.primary, fontWeight = FontWeight.Bold) }
            Text("🔄 Ré-enrichir", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Text("🔍", color = if (searching) theme.primary else theme.text, style = MaterialTheme.typography.titleMedium)
            }
        }
        /* ---- Trois sections ---- */
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickSection.entries.forEach { s ->
                val n = countFor(s)
                NovaFilterChip(
                    flagKey = s, selected = section == s, onClick = { section = s }, modifier = Modifier.weight(1f),
                    label = { Text("${s.emoji} ${s.label}${if (n > 0) " ($n)" else ""}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()) }
                )
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Rechercher dans ${section.label.lowercase()}…", color = theme.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = theme.primary, unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f), focusedTextColor = theme.text, unfocusedTextColor = theme.text, cursorColor = theme.primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        /* ---- Tout cocher / décocher (sur la liste affichée) ---- */
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} élément${if (shown.size > 1) "s" else ""}", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { selected = if (allShownChecked) selected - shownKeys.toSet() else selected + shownKeys }) {
                Text(if (allShownChecked) "Tout décocher" else "Tout cocher", color = theme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.15f))

        /* ---- Liste ---- */
        LazyColumn(Modifier.weight(1f)) {
            if (shown.isEmpty()) item { Text(if (query.isBlank()) "Rien dans cette section." else "Aucun résultat pour « $query »", color = theme.textSecondary, modifier = Modifier.padding(16.dp)) }
            items(shown, key = { it.key }) { item ->
                val checked = item.key in selected
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (checked) theme.primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { selected = if (checked) selected - item.key else selected + item.key }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { selected = if (it) selected + item.key else selected - item.key }, colors = CheckboxDefaults.colors(checkedColor = theme.primary))
                    CoverArt(item.imageUrl, item.title, size = 44, circle = item.circle)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.subtitle, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        /* ---- Barre basse ---- */
        HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
        Row(Modifier.fillMaxWidth().background(theme.surface).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("Tout effacer", color = theme.textSecondary) }
            Text(
                "🎤 ${countFor(PickSection.ARTISTS)} · 💿 ${countFor(PickSection.ALBUMS)} · 🎵 ${countFor(PickSection.TRACKS)}",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
            )
            Button(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val targets = selected.mapNotNull { MetadataEnricher.RefreshTarget.decode(it) }
                        .sortedBy { when (it.type) { EntityType.ARTIST -> 0; EntityType.ALBUM -> 1; else -> 2 } }
                    EnrichmentWorker.enqueueRefresh(context, targets)
                    selected = emptySet()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
            ) { Text("🔄 Lancer (${selected.size})", fontWeight = FontWeight.Bold) }
        }
    }
}
