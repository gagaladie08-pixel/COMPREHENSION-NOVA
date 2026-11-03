package com.novastats.app.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.service.DetectionState
import com.novastats.app.ui.theme.Nova
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class SourceApplication(val packageName: String, val displayName: String)

/** Choix des apps sources — nom/icône visibles; le nom de package reste un identifiant interne. */
@Composable
internal fun SourceAppsPage() {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val theme = Nova.theme
    val scope = rememberCoroutineScope()
    val whitelist by app.settings.whitelist.collectAsStateWithLifecycle(initialValue = emptySet())
    val knownApps = app.settings.knownMusicApps
    val playedSources by app.database.scrobbleDao().distinctSources().collectAsStateWithLifecycle(initialValue = emptyList())
    val detection by DetectionState.state.collectAsStateWithLifecycle()
    val seenPackages = remember(playedSources, detection.activeSessions, detection.ignoredSessions) {
        (playedSources + detection.activeSessions + detection.ignoredSessions).toSet()
    }
    val additionalPackages = remember(playedSources, detection.activeSessions, detection.ignoredSessions) {
        (playedSources + detection.activeSessions + detection.ignoredSessions)
            .filterNot { it == context.packageName }.toSet()
    }
    val requiredPackages = remember(detection.activeSessions, detection.ignoredSessions) {
        (detection.activeSessions + detection.ignoredSessions).filterNot { it == context.packageName }.toSet()
    }
    var query by rememberSaveable { mutableStateOf("") }
    var applications by remember { mutableStateOf<List<SourceApplication>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(additionalPackages, requiredPackages) {
        loading = true
        try {
            val savedWhitelist = app.settings.whitelist.first()
            applications = withContext(Dispatchers.IO) {
                loadSourceApplications(context, additionalPackages, requiredPackages + savedWhitelist, knownApps)
            }
        } finally {
            loading = false
        }
    }

    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filteredApplications = remember(applications, normalizedQuery) {
        if (normalizedQuery.isBlank()) applications
        else applications.filter { it.displayName.lowercase(Locale.ROOT).contains(normalizedQuery) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                if (whitelist.isEmpty()) "Toutes les applications"
                else "${whitelist.size} application(s) choisie(s)",
                color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Pour limiter le suivi, coche les applications voulues. Sans sélection, toutes restent suivies.",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    label = { Text("Rechercher une application") },
                    placeholder = { Text("Nom de l’application…") },
                    trailingIcon = {
                        if (query.isNotBlank()) TextButton(onClick = { query = "" }) { Text("Effacer") }
                    }
                )
                if (whitelist.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { app.settings.setWhitelist(emptySet()) } }) {
                        Text("Tout effacer", color = theme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                "${filteredApplications.size} application(s) · coche celles à suivre",
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = theme.accent)
        if (applications.isEmpty() && !loading) {
            Text(
                "Aucune application trouvée sur cet appareil.",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        } else if (filteredApplications.isEmpty() && !loading) {
            Text(
                "Aucune application ne correspond à cette recherche.",
                color = theme.textSecondary, modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(filteredApplications, key = { it.packageName }) { choice ->
                    SourceApplicationRow(
                        choice = choice,
                        selected = choice.packageName in whitelist,
                        seen = choice.packageName in seenPackages,
                        onSelected = { checked ->
                            scope.launch { app.settings.setWhitelistApp(choice.packageName, checked) }
                        }
                    )
                }
            }
        }
    }
}

private fun loadSourceApplications(
    context: Context,
    additionalPackages: Set<String>,
    requiredPackages: Set<String>,
    knownApps: Map<String, String>
): List<SourceApplication> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val installed = runCatching {
        pm.queryIntentActivities(launcherIntent, 0).mapNotNull { resolve ->
            val packageName = resolve.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            val displayName = resolve.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: knownApps[packageName]
                ?: "Application sans nom"
            SourceApplication(packageName, displayName)
        }
    }.getOrDefault(emptyList())
    val installedPackages = installed.mapTo(HashSet()) { it.packageName }
    val observedOrKnown = (knownApps.keys + additionalPackages + requiredPackages).asSequence()
        .filterNot { it in installedPackages || it == context.packageName }
        .mapNotNull { packageName ->
            val applicationInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            if (applicationInfo == null && packageName !in requiredPackages) return@mapNotNull null
            val displayName = applicationInfo?.let { pm.getApplicationLabel(it).toString().trim() }
                ?.takeIf { it.isNotEmpty() }
                ?: knownApps[packageName]
                ?: "Application indisponible"
            SourceApplication(packageName, displayName)
        }.toList()
    return (installed + observedOrKnown)
        .distinctBy { it.packageName }
        .sortedWith(compareBy<SourceApplication> { it.displayName.lowercase(Locale.ROOT) }.thenBy { it.packageName })
}

@Composable
private fun SourceApplicationRow(
    choice: SourceApplication,
    selected: Boolean,
    seen: Boolean,
    onSelected: (Boolean) -> Unit
) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp)).background(theme.surface.copy(alpha = 0.68f))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onSelected)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceApplicationIcon(choice.packageName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(choice.displayName, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (seen) Text("Déjà détectée", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

@Composable
private fun SourceApplicationIcon(packageName: String) {
    val context = LocalContext.current
    val theme = Nova.theme
    val icon = remember(packageName) { runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() }
    if (icon != null) {
        AndroidView(
            factory = { viewContext -> ImageView(viewContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
            update = { imageView -> imageView.setImageDrawable(icon) },
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(theme.surface)
        )
    } else {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(theme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { Text("🎵", style = MaterialTheme.typography.titleMedium) }
    }
}
