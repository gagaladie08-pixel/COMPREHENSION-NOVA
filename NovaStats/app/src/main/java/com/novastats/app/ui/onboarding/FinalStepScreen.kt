package com.novastats.app.ui.onboarding

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.novastats.app.NovaStatsApp
import com.novastats.app.service.DetectionState
import com.novastats.app.ui.theme.NovaTheme

/** Étape 4/4 — résumé personnalisé, statuts exacts et première mission facultative. */
@Composable
fun FinalStepScreen(
    audio: ObAudio,
    theme: NovaTheme,
    perms: Permissions,
    guideCompleted: Boolean,
    effects: OnboardingEffects,
    onEffectsChange: (OnboardingEffects) -> Unit,
    onLaunch: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaStatsApp
    val detection by DetectionState.state.collectAsStateWithLifecycle()
    val thresholdSec by app.settings.thresholdSec.collectAsStateWithLifecycle(initialValue = 30)
    val beats = rememberBeats(4, startMs = 180, stepMs = 150)
    var testStarted by rememberSaveable { mutableStateOf(false) }
    var baselineLog by rememberSaveable { mutableStateOf<String?>(null) }
    var detectedTrack by rememberSaveable { mutableStateOf<String?>(null) }
    val latestLog = detection.log.firstOrNull()
    val newPlayLog = detection.log.takeWhile { it != baselineLog }.firstOrNull { "▶" in it }

    LaunchedEffect(testStarted, baselineLog, newPlayLog, detection.lastTrack, detectedTrack) {
        if (testStarted && detectedTrack == null && newPlayLog != null) {
            detection.lastTrack?.takeIf { it.isNotBlank() }?.let { detectedTrack = it }
        }
    }
    LaunchedEffect(detectedTrack) {
        if (detectedTrack != null) { audio.tick(); audio.success() }
    }
    LaunchedEffect(Unit) { audio.whoosh(0.12f) }

    val accent = theme.primary
    ObStage(base = theme.background, accent = accent, ambient = theme, ambientAlpha = 0.28f) {
        ObLayout(step = 4, accent = accent, bottom = {
            Appear(beats >= 4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ObPrimaryButton("Commencer", fill = accent) { audio.tap(); audio.boom(); onLaunch() }
                    OnboardingAccessibilityOptions(effects, accent, onEffectsChange)
                }
            }
        }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                Appear(beats >= 1) { ObEyebrow("TA CONFIGURATION", accent) }
                Spacer(Modifier.height(6.dp))
                Appear(beats >= 1) { ObHeadline("Ton univers.\nTes règles.", size = 36) }
                Spacer(Modifier.height(8.dp))
                Appear(beats >= 2) {
                    ObSub("Tu as choisi ${theme.name}. Voici exactement ce qui est actif — et ce qui reste à ton choix.", size = 14)
                }
                Spacer(Modifier.height(16.dp))

                Appear(beats >= 2) {
                    ObGlowCard(accent) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                ObEyebrow("THÈME CHOISI", accent)
                                Text(theme.name, color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
                                Text("Modifiable à tout moment dans Réglages → Apparence", color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
                                Text(
                                    "Effets : ${if (effects.reducedMotion) "mouvement réduit" else "mouvement standard"} · sons ${if (effects.soundEffects) "activés" else "coupés"} · vibrations ${if (effects.haptics) "activées" else "coupées"}",
                                    color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 10.sp, lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Swatches(theme, size = 16.dp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Appear(beats >= 3) {
                    ObCard {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("TES RÉGLAGES", color = ObColors.Gray, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                            ObTag("Statut", accent)
                        }
                        FinalStatusRow("Accès aux notifications", "Requis pour la détection des écoutes", perms.listener, "À vérifier", required = true)
                        ObDivider()
                        FinalStatusRow("Batterie sans restriction", "Conseillée pour la fiabilité en arrière-plan", perms.battery, "Conseillée", recommended = true)
                        ObDivider()
                        FinalStatusRow("Relance au redémarrage", "Optionnelle · relance NovaStats après un redémarrage", perms.boot, "Facultatif")
                        ObDivider()
                        FinalStatusRow("Notifications de NovaStats", "Optionnelles · alertes et bilans", perms.appNotifications, "Facultatif")
                        ObDivider()
                        FinalStatusRow("Guide constructeur", "Aide supplémentaire, jamais requise", guideCompleted, "Facultatif")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Appear(beats >= 3) {
                    Text(
                        "Seul l'accès Android aux notifications est requis pour détecter les écoutes. Batterie sans restriction, relance automatique, alertes et guide constructeur restent facultatifs ou conseillés; tu peux terminer sans eux.",
                        color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 11.sp, lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))

                Appear(beats >= 4) {
                    ObGlowCard(if (detectedTrack != null) ObColors.Green else accent) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape)
                                        .background(if (detectedTrack != null) ObColors.Green.copy(alpha = 0.18f) else accent.copy(alpha = 0.16f))
                                        .border(1.dp, if (detectedTrack != null) ObColors.Green.copy(alpha = 0.5f) else accent.copy(alpha = 0.45f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (detectedTrack != null) "✓" else "♫", color = if (detectedTrack != null) ObColors.Green else accent, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 23.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (detectedTrack != null) "Mission accomplie" else "Première mission", color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(if (detectedTrack != null) "Ton premier titre est reconnu." else "Une écoute réelle. Aucune fausse donnée.", color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 11.sp, lineHeight = 15.sp)
                                }
                                ObTag(if (detectedTrack != null) "Réussie" else "Bonus", if (detectedTrack != null) ObColors.Green else accent)
                            }
                            Spacer(Modifier.height(14.dp))
                            if (detectedTrack != null) {
                                Text("« $detectedTrack »", color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 23.sp)
                                Text("Laisse-le jouer encore ${thresholdSec}s pour atteindre le seuil actuel et valider l'écoute.", color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
                            } else {
                                Text(
                                    if (testStarted) "Observation lancée · démarre ou change de morceau dans ton lecteur."
                                    else "Lance ou change de morceau après le test. NovaStats félicitera la première détection réelle.",
                                    color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 13.sp, lineHeight = 18.sp
                                )
                                Spacer(Modifier.height(11.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (detection.listenerConnected) ObColors.Green else ObColors.Orange))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (detection.listenerConnected) "Service connecté" else "Service en attente", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    ObMiniButton(if (testStarted) "Réessayer" else "Lancer le test", accent) {
                                        baselineLog = latestLog
                                        detectedTrack = null
                                        testStarted = true
                                        audio.tap()
                                    }
                                }
                            }
                            if (!detection.listenerConnected) {
                                Text(
                                    "Si le service ne se connecte pas après l'installation, réactive son accès aux notifications dans Android ou redémarre le téléphone. Tu peux terminer sans ce test.",
                                    color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 10.sp, lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FinalStatusRow(
    title: String,
    detail: String,
    enabled: Boolean,
    inactiveLabel: String,
    required: Boolean = false,
    recommended: Boolean = false
) {
    val badgeColor = when {
        enabled -> ObColors.Green
        required -> ObColors.Orange
        recommended -> ObColors.Orange
        else -> ObColors.Gray
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        ObCheck(enabled, 18.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(8.dp))
        ObTag(if (enabled) "Actif" else inactiveLabel, badgeColor)
    }
}
