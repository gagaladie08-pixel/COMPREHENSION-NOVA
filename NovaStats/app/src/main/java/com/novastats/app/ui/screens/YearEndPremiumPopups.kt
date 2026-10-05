package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.domain.Chart
import com.novastats.app.domain.Period
import com.novastats.app.domain.YearEndRules
import com.novastats.app.ui.theme.Nova

@Composable
internal fun YearEndRulesPremiumPopup(
    windowLabel: String?,
    weeksCounted: Int,
    imageUrl: String?,
    onDismiss: () -> Unit
) {
    val theme = Nova.theme
    val gold = Color(0xFFFFD36A)
    NovaPopupCard(
        borderColor = gold,
        onDismiss = onDismiss,
        glowDp = 26,
        widthFraction = 0.96f,
        heightFraction = 0.86f,
        backdropUrl = imageUrl,
        banner = {
            Box(Modifier.fillMaxWidth().height(214.dp)) {
                BlurredBackdrop(imageUrl, gold, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.84f))
                        )
                    )
                )
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlowRing(imageUrl, "Year-End", 112.dp, circle = false)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "GUIDE · YEAR-END NOVA",
                            color = gold,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 2.4.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Comment ton bilan est calculé",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Le barème, les seuils et ce que les points veulent dire.",
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    ) {
        GlassCard(glow = gold) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("À RETENIR", color = gold, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.8.sp, fontWeight = FontWeight.Black)
                Text(
                    "C'est ton classement personnel, calculé à partir des écoutes stockées par NovaStats. Il s'inspire de la logique Year-End, mais ne reprend pas les mesures américaines de ventes, radio et streaming de Billboard.",
                    color = theme.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        PopupSection("01 · FENÊTRE ET DONNÉES", gold)
        if (!windowLabel.isNullOrBlank()) PopupInfoRow("Période affichée", windowLabel)
        if (weeksCounted > 0) PopupInfoRow("Semaines examinées", "$weeksCounted")
        Text(
            "La fenêtre par défaut est décembre → novembre ; tu peux choisir l'année civile. Les rangs sont recalculés sur des semaines lundi–dimanche. Une semaine qui chevauche une borne est prise en entier, et la semaine en cours peut encore évoluer.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        PopupSection("02 · COMMENT LES POINTS SONT ATTRIBUÉS", gold)
        Text(
            "Pour chaque semaine, Nova classe tes écoutes locales par nombre d'écoutes ; la durée départage les égalités. La position obtenue rapporte des points, qui s'ajoutent sur toute la fenêtre.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        GlassCard(glow = gold) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FORMULE NOVA", color = gold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, fontWeight = FontWeight.Black)
                Text(
                    "Points de la semaine = limite du chart + 1 − rang",
                    color = theme.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text("Exemple : Hot 100, rang #10 → 100 + 1 − 10 = 91 points cette semaine.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        PopupInfoRow("Titres", "#1 = 100 pts · #100 = 1 pt")
        PopupInfoRow("Artistes", "#1 = 50 pts · #50 = 1 pt")
        PopupInfoRow("Albums", "#1 = 75 pts · #75 = 1 pt")
        Text(
            "Le score annuel n'est pas la somme brute des écoutes : c'est la somme des points des semaines créditées. Ainsi, une présence régulière peut dépasser un pic isolé.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        PopupSection("03 · ORDRE FINAL ET DÉPARTAGES", gold)
        Text(
            "Le classement annuel est trié par points cumulés décroissants. En cas d'égalité : d'abord les écoutes locales cumulées, puis le nombre de semaines créditées.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        PopupInfoRow("Titres", "Top ${Chart.HOT_100.limit(Period.WEEKLY)} par semaine")
        PopupInfoRow("Artistes", "Top ${Chart.ARTIST_50.limit(Period.WEEKLY)} par semaine")
        PopupInfoRow("Albums", "Top ${Chart.ALBUMS_75.limit(Period.WEEKLY)} par semaine")

        PopupSection("04 · RÉCURRENCE RENFORCÉE — TITRES", gold)
        Text(
            "Après assez de semaines créditées, un titre qui descend sous le rang protégé cesse de marquer des points. Le contrôle se fait au début de chaque nouvelle semaine : la semaine qui déclenche la sortie et les suivantes ne rapportent rien ; les points précédents restent acquis.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        YearEndRules.recurrentThresholds.forEach { threshold ->
            PopupInfoRow(
                "Après ${threshold.minimumWeeks} sem. créditées",
                "si rang > #${threshold.belowPosition}",
                valueColor = theme.accent
            )
        }
        Text(
            "Le compteur repart à zéro pour chaque fenêtre Year-End et ne reprend pas l'historique complet d'un chart. Les seuils ne s'appliquent qu'aux titres ; artistes et albums n'ont pas de règle de récurrence. Le palier de 78 semaines est écarté car une fenêtre annuelle Nova ne peut pas l'atteindre.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        PopupSection("05 · COMMENT LES ENTRÉES SONT REGROUPÉES", gold)
        Text(
            "Titres : les versions liées à un même titre racine sont regroupées. Artistes : les écoutes sont attribuées à chaque crédit ; les artistes fusionnés sont écartés. Albums : les compilations sont exclues, et un album sans artiste unique s'affiche comme « Artistes variés ».",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "En bref : un Year-End Nova transparent, fondé sur tes habitudes d'écoute — pas un résultat officiel Billboard.",
            color = gold,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

@Composable
internal fun YearEndEntryPremiumPopup(entry: YearEndPopupEntry, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val row = entry.row
    val accent = when (entry.chart) {
        Chart.HOT_100 -> Color(0xFFFFD36A)
        Chart.ARTIST_50 -> theme.secondary
        Chart.ALBUMS_75 -> theme.primary
    }
    val limit = entry.chart.limit(Period.WEEKLY)
    val story = yearEndStory(entry)
    NovaPopupCard(
        borderColor = accent,
        onDismiss = onDismiss,
        glowDp = 24,
        widthFraction = 0.96f,
        heightFraction = 0.86f,
        backdropUrl = row.imageUrl,
        banner = {
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                BlurredBackdrop(row.imageUrl, accent, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.82f))
                        )
                    )
                )
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlowRing(row.imageUrl, row.name, 108.dp, circle = entry.chart == Chart.ARTIST_50)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${entry.chart.emoji} ${entry.chart.label.uppercase()} · YEAR-END",
                            color = accent,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            row.name,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!row.subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(row.subtitle, color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("RANG ANNUEL  #${entry.rank}", color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
                    }
                }
            }
        }
    ) {
        GlassCard(glow = accent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SCORE CUMULÉ", color = theme.textSecondary, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    formatCount(row.points),
                    color = accent,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 48.sp),
                    fontWeight = FontWeight.Black
                )
                Text("points Year-End · position calculée sur le total des points", color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }

        GlassCard(glow = accent) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✦", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("ANECDOTE DE CETTE ÉDITION", color = accent, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.7.sp, fontWeight = FontWeight.Black)
                        Text("La signature de son parcours", color = theme.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Text(story.anecdote, color = theme.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                Text("SON EXPLOIT · ${entry.chart.label.uppercase()}", color = accent, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                Text(story.achievement, color = theme.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Phrase dédiée à cette entrée, construite à partir de ses propres relevés Nova — sans anecdote inventée.",
                    color = theme.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        PopupSection("COMMENT CE SCORE EST FORMÉ", accent)
        Text(
            "Chaque semaine où l'entrée est créditée, elle reçoit limite du chart + 1 − son rang. Ces points sont additionnés. Pour ${entry.chart.label}, la limite est $limit : #1 rapporte $limit points et #$limit rapporte 1 point.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        PopupInfoRow("Classement annuel", "#${entry.rank}", valueColor = accent)
        PopupInfoRow("Points obtenus", "${formatCount(row.points)} pts", valueColor = accent)
        PopupInfoRow("Écoutes créditées", formatCount(row.plays))
        Text(
            "Les écoutes servent à construire les rangs hebdomadaires et à départager une égalité finale ; elles ne remplacent pas le score en points.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodySmall
        )

        PopupSection("PARCOURS DANS LE CHART", accent)
        PopupInfoRow("Semaines créditées", "${row.weeks} sur ${entry.weeksCounted} examinées")
        PopupInfoRow("Meilleur rang hebdomadaire", if (row.peak > 0) "#${row.peak} sur $limit" else "—")
        PopupInfoRow("Semaines au rang #1", "${row.weeksAt1}")
        Text(
            "Une semaine est dite créditée si elle a effectivement ajouté des points. Si la règle de récurrence a arrêté le titre, les semaines qui suivent ne sont pas incluses dans ce compteur.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        if (row.recurrent && row.recurrentThreshold != null) {
            PopupSection("SEUIL DE RÉCURRENCE ATTEINT", theme.accent)
            PopupInfoRow(
                "Palier appliqué",
                "${row.recurrentThreshold.minimumWeeks} sem. · rang > #${row.recurrentThreshold.belowPosition}",
                valueColor = theme.accent
            )
            PopupInfoRow("Rang déclencheur", row.recurrentAtPosition?.let { "#$it" } ?: "—", valueColor = theme.accent)
            PopupInfoRow("Semaines déjà créditées", row.recurrentAfterWeeks?.toString() ?: "—")
            Text(
                "La semaine au rang déclencheur n'a pas rapporté de points. Les points antérieurs sont conservés ; le titre n'en marque plus pour la suite de cette fenêtre Year-End.",
                color = theme.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (entry.chart == Chart.HOT_100) {
            PopupSection("RÉCURRENCE", accent)
            Text(
                "Aucun seuil de récurrence n'a arrêté ce titre dans cette fenêtre. Les seuils sont calculés sur les semaines créditées de cette année, pas sur toute son histoire.",
                color = theme.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        PopupSection("ACTIVITÉ LOCALE", accent)
        PopupInfoRow("Temps d'écoute crédité", formatDuration(row.durationMs))
        if (row.extra > 0) PopupInfoRow("Titres distincts · pic hebdo", formatCount(row.extra))
        Spacer(Modifier.height(8.dp))
        Text(
            "Toutes ces valeurs viennent de ton historique NovaStats. Elles ne représentent ni les ventes, ni la radio, ni le streaming du public américain.",
            color = theme.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
