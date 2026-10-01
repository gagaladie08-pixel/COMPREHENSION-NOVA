package com.novastats.app.ui.theme

import androidx.compose.ui.graphics.Color

/* =====================================================================================
 * Système de thèmes NovaStats — THEMES.md (statut VALIDÉ ✅)
 *
 * 15 thèmes, chacun transforme entièrement l'app : couleurs (8 rôles fixes), polices
 * (titre + corps, Google Fonts), icônes, animations (durée + easing), graphiques (style de
 * courbe) et composants UI (formes). Aucun toggle clair/sombre : chaque thème garde son mode.
 * Ce fichier est du Kotlin pur (pas d'API Android) pour rester utilisable dans les tests JVM.
 * ===================================================================================== */

/** Rôle fonctionnel des 8 couleurs (clé → usage dans l'app). */
enum class ColorKey { PRIMARY, SECONDARY, GLOW_SECONDARY, BACKGROUND, SURFACE, TEXT, TEXT_SECONDARY, ACCENT }

/** Easing des transitions, par thème. */
enum class MotionEasing {
    EASE_IN_OUT, EASE_OUT,
    /** Villain Era : cuts secs, pas de fondu. */
    CUT,
    /** Slay Queen : léger rebond élégant. */
    EASE_IN_OUT_BACK,
    /** Pink Y2K : rebond bubblegum. */
    EASE_OUT_BOUNCE,
    /** Pink Venom / Pop Revolution : rapide et sec. */
    EASE_OUT_EXPO,
    /** Chaos Born : 100-400 ms, imprévisible. */
    RANDOM
}

/** Effet signature — déclenché sur événements précis (changement d'onglet, tap, déblocage) ou en continu léger. */
enum class Signature {
    SCANLINES_GLITCH,   // Cyber Nova : scanlines + glitch pixel
    CHROME_SWEEP,       // Neon Disco : reflets chromés balayants (boule à facettes)
    JUMP_CUT,           // Villain Era : glitch / jump-cut, pas de fondu
    GOLD_SHIMMER,       // Slay Queen : shimmer doré traversant les badges
    RISING_BUBBLES,     // Pink Y2K : bulles / étoiles qui montent en continu
    CURTAIN,            // Velvet Stage : ouverture de rideau depuis le centre
    BW_FLASH,           // Pink Venom : flash N&B → couleur au tap
    CLOUD_FADE,         // Cloud Nine : fondu doux façon nuage
    WARM_PULSE,         // Solara : glow qui respire lentement
    RANDOM_GLITCH,      // Chaos Born : position / opacité qui saute aléatoirement
    CONFETTI,           // Survivor : burst de confettis au déblocage d'un palier
    SPARKLES,           // Rainbow Pop : paillettes qui scintillent en continu
    BLUE_FLASH,         // Pop Revolution : flash bleu / blanc au changement d'onglet
    PATTERN_REVEAL,     // African Confessions : motif géométrique qui se dessine en fondu
    DUAL_CONTRAST       // Bad Angel : contraste qui bascule clair / sombre
}

/** Style d'icônes (rendu des icônes d'onglets et des pastilles). */
enum class IconStyle {
    HUD, CHROME_ROUND, RAW, ROYAL, BUBBLE, STAGE, SPIKY, CLOUD, SUN, METAL, PRIDE, CANDY, POLAROID, GEOMETRIC, DUAL
}

/** Forme du tracé des courbes. */
enum class CurveShape {
    /** Bézier lissé. */ SMOOTH,
    /** Segments droits, anguleux. */ ANGULAR,
    /** Très arrondi. */ VERY_ROUND,
    /** Volontairement irrégulier / asymétrique. */ IRREGULAR
}

/** Décoration posée sur le tracé. */
enum class CurveDeco { NONE, BLINK_DOTS, STARS, DISCO_DOTS }

/** Style de graphique courbe (Canvas + Path + dégradé + glow) d'un thème. */
data class ChartStyle(
    /** Couleur du trait (clé) — null = couleur contextuelle passée par l'écran. */
    val stroke: ColorKey? = ColorKey.PRIMARY,
    val strokeWidthDp: Float = 2.5f,
    /** Rayon du glow (dp). 0 = pas de glow. */
    val glowDp: Float = 4f,
    /** Dégradé de remplissage : haut → bas (null = transparent). */
    val fillTop: ColorKey? = ColorKey.PRIMARY,
    val fillBottom: ColorKey? = null,
    val fillAlpha: Float = 0.4f,
    val shape: CurveShape = CurveShape.SMOOTH,
    val deco: CurveDeco = CurveDeco.NONE,
    /** Survivor : dégradé arc-en-ciel animé (hue-rotate continu). */
    val rainbow: Boolean = false,
    /** Bad Angel : glow alterné, moitié du tracé plus lumineuse. */
    val alternatingGlow: Boolean = false,
    /** Cyber Nova : tracé révélé en « scan » gauche → droite. */
    val scanReveal: Boolean = false,
    /** Velvet Stage : glow façon projecteur (large, chaud). */
    val spotlight: Boolean = false
)

data class NovaTheme(
    val id: String,
    val emoji: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val glowSecondary: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    /** Description courte de l'effet signature (affichée dans Apparence / onboarding). */
    val effects: String,
    val inspiration: String = "",
    /** Police des titres (nom Google Fonts). */
    val titleFont: String = "Orbitron",
    /** Police du corps (nom Google Fonts). */
    val bodyFont: String = "Rajdhani",
    /** Description du style d'icônes. */
    val iconsDescription: String = "",
    val icons: IconStyle = IconStyle.HUD,
    /** Durée des transitions (ms). */
    val transitionMs: Int = 280,
    val easing: MotionEasing = MotionEasing.EASE_IN_OUT,
    val signature: Signature = Signature.SCANLINES_GLITCH,
    /** Rayon des coins des cartes / boutons (dp). 0 = angles vifs. */
    val cornerDp: Int = 16,
    val chart: ChartStyle = ChartStyle(),
    /** Survivor : le texte secondaire est un dégradé arc-en-ciel rotatif. */
    val rainbowTextSecondary: Boolean = false
) {
    /** Thème clair si le fond est lumineux (Pink Y2K, Cloud Nine). */
    val isLight: Boolean get() = background.luminance() > 0.5f

    fun color(key: ColorKey): Color = when (key) {
        ColorKey.PRIMARY -> primary
        ColorKey.SECONDARY -> secondary
        ColorKey.GLOW_SECONDARY -> glowSecondary
        ColorKey.BACKGROUND -> background
        ColorKey.SURFACE -> surface
        ColorKey.TEXT -> text
        ColorKey.TEXT_SECONDARY -> textSecondary
        ColorKey.ACCENT -> accent
    }

    /** Libellé « Transition » lisible (Apparence). */
    val transitionLabel: String
        get() = when (easing) {
            MotionEasing.RANDOM -> "100-400 ms variable"
            MotionEasing.CUT -> "$transitionMs ms, cuts secs"
            MotionEasing.EASE_IN_OUT -> "$transitionMs ms ease-in-out"
            MotionEasing.EASE_OUT -> "$transitionMs ms ease-out"
            MotionEasing.EASE_IN_OUT_BACK -> "$transitionMs ms ease-in-out-back"
            MotionEasing.EASE_OUT_BOUNCE -> "$transitionMs ms ease-out-bounce"
            MotionEasing.EASE_OUT_EXPO -> "$transitionMs ms ease-out-expo"
        }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
/** "#RRGGBB" → Color, sans dépendance Android (utilisable en tests JVM). */
private fun hex(h: String): Color {
    val v = h.removePrefix("#").toLong(16)
    return Color(red = ((v shr 16) and 0xFF).toInt(), green = ((v shr 8) and 0xFF).toInt(), blue = (v and 0xFF).toInt())
}

object NovaThemes {
    // 1. 🌌 Cyber Nova — aespa + Lisa — cyberpunk / IA / métavers
    val CYBER_NOVA = NovaTheme(
        "cyber_nova", "🌌", "Cyber Nova",
        hex("#FF006E"), hex("#00B4FF"), hex("#BD00FF"), hex("#050510"), hex("#0D0D2B"), hex("#F0F0FF"), hex("#A0A0C0"), hex("#00FFF0"),
        effects = "Scanlines + glitch pixel", inspiration = "aespa + Lisa — cyberpunk / IA / métavers",
        titleFont = "Orbitron", bodyFont = "Rajdhani", iconsDescription = "Lignes HUD futuristes, reflets dorés glossy", icons = IconStyle.HUD,
        transitionMs = 280, easing = MotionEasing.EASE_IN_OUT, signature = Signature.SCANLINES_GLITCH, cornerDp = 6,
        chart = ChartStyle(stroke = ColorKey.ACCENT, glowDp = 8f, fillTop = ColorKey.PRIMARY, fillBottom = null, scanReveal = true)
    )

    // 2. 🟣 Neon Disco — Beyoncé Renaissance + Dua Lipa Future Nostalgia
    val NEON_DISCO = NovaTheme(
        "neon_disco", "🟣", "Neon Disco",
        hex("#9B00FF"), hex("#FFD700"), hex("#FF69B4"), hex("#0A0010"), hex("#1A0030"), hex("#E8E8FF"), hex("#B0A0C0"), hex("#C0C0C0"),
        effects = "Reflets chromés balayants (boule à facettes)", inspiration = "Beyoncé Renaissance + Dua Lipa Future Nostalgia — discothèque futuriste",
        titleFont = "Audiowide", bodyFont = "Poppins", iconsDescription = "Rondes et chromées, néon roller-skate", icons = IconStyle.CHROME_ROUND,
        transitionMs = 300, easing = MotionEasing.EASE_OUT, signature = Signature.CHROME_SWEEP, cornerDp = 24,
        chart = ChartStyle(stroke = ColorKey.SECONDARY, glowDp = 6f, fillTop = ColorKey.PRIMARY, deco = CurveDeco.BLINK_DOTS)
    )

    // 3. 🖤 Villain Era — Taylor Swift Reputation + Rihanna Anti
    val VILLAIN_ERA = NovaTheme(
        "villain_era", "🖤", "Villain Era",
        hex("#CC0000"), hex("#4A4A4A"), hex("#8B0000"), hex("#080808"), hex("#111111"), hex("#EEEEEE"), hex("#888888"), hex("#FF0000"),
        effects = "Glitch / jump-cut, pas de fondu", inspiration = "Taylor Swift Reputation + Rihanna Anti — sombre / rebelle",
        titleFont = "Bebas Neue", bodyFont = "Oswald", iconsDescription = "Traits bruts, effet papier journal déchiré", icons = IconStyle.RAW,
        transitionMs = 150, easing = MotionEasing.CUT, signature = Signature.JUMP_CUT, cornerDp = 0,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 1f, fillTop = ColorKey.PRIMARY, fillAlpha = 0.3f, shape = CurveShape.ANGULAR)
    )

    // 4. 👑 Slay Queen — Beyoncé Lemonade + Nicki Minaj + BLACKPINK
    val SLAY_QUEEN = NovaTheme(
        "slay_queen", "👑", "Slay Queen",
        hex("#FFD700"), hex("#FF1493"), hex("#FFA500"), hex("#0A0A0A"), hex("#1A1500"), hex("#FFFFFF"), hex("#D4AF37"), hex("#B76E79"),
        effects = "Shimmer doré traversant les badges", inspiration = "Beyoncé Lemonade + Nicki Minaj + BLACKPINK — luxe / royauté",
        titleFont = "Playfair Display", bodyFont = "Montserrat", iconsDescription = "Couronnes / bijoux stylisés, contours dorés épais", icons = IconStyle.ROYAL,
        transitionMs = 350, easing = MotionEasing.EASE_IN_OUT_BACK, signature = Signature.GOLD_SHIMMER, cornerDp = 18,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 12f, fillTop = ColorKey.SECONDARY, fillBottom = ColorKey.PRIMARY)
    )

    // 5. 🍭 Pink Y2K — Katy Perry + Nicki Minaj Pink Friday 2 (thème clair)
    val PINK_Y2K = NovaTheme(
        "pink_y2k", "🍭", "Pink Y2K",
        hex("#FF69B4"), hex("#87CEEB"), hex("#FF1493"), hex("#FFF0F5"), hex("#FFE4E1"), hex("#8B0045"), hex("#C06080"), hex("#DA70D6"),
        effects = "Bulles / étoiles qui montent en continu", inspiration = "Katy Perry + Nicki Minaj Pink Friday 2 — bubblegum / Y2K",
        titleFont = "Baloo 2", bodyFont = "Fredoka", iconsDescription = "Bulles / cœurs / étoiles gonflées, très arrondies", icons = IconStyle.BUBBLE,
        transitionMs = 320, easing = MotionEasing.EASE_OUT_BOUNCE, signature = Signature.RISING_BUBBLES, cornerDp = 28,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 3f, fillTop = ColorKey.PRIMARY, fillAlpha = 0.3f, shape = CurveShape.VERY_ROUND, deco = CurveDeco.STARS)
    )

    // 6. 🎭 Velvet Stage — Jennie Ruby + Taylor Swift The Life of a Showgirl
    val VELVET_STAGE = NovaTheme(
        "velvet_stage", "🎭", "Velvet Stage",
        hex("#8B0000"), hex("#722F37"), hex("#CFB53B"), hex("#0C0008"), hex("#1A0010"), hex("#FFF8DC"), hex("#D4AF37"), hex("#FFD700"),
        effects = "Ouverture de rideau depuis le centre", inspiration = "Jennie Ruby + Taylor Swift The Life of a Showgirl — cabaret / glamour",
        titleFont = "Abril Fatface", bodyFont = "Cormorant", iconsDescription = "Rideaux / plumes stylisés, fins traits dorés sur fond sombre", icons = IconStyle.STAGE,
        transitionMs = 500, easing = MotionEasing.EASE_IN_OUT, signature = Signature.CURTAIN, cornerDp = 12,
        chart = ChartStyle(stroke = ColorKey.GLOW_SECONDARY, glowDp = 14f, fillTop = ColorKey.SECONDARY, fillBottom = ColorKey.PRIMARY, spotlight = true)
    )

    // 7. 🖤 Pink Venom — BLACKPINK Born Pink + Rihanna Loud
    val PINK_VENOM = NovaTheme(
        "pink_venom", "🖤", "Pink Venom",
        hex("#FF0080"), hex("#FF0030"), hex("#FF69B4"), hex("#000000"), hex("#0D0008"), hex("#FFFFFF"), hex("#FFB6C1"), hex("#FF007F"),
        effects = "Flash de contraste N&B → couleur au tap", inspiration = "BLACKPINK Born Pink + Rihanna Loud — rock / girl crush",
        titleFont = "Anton", bodyFont = "Archivo Black", iconsDescription = "Formes pointues épaisses, éclaboussures de couleur vive", icons = IconStyle.SPIKY,
        transitionMs = 150, easing = MotionEasing.EASE_OUT_EXPO, signature = Signature.BW_FLASH, cornerDp = 4,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, strokeWidthDp = 4f, glowDp = 2f, fillTop = ColorKey.PRIMARY, fillAlpha = 0.35f, shape = CurveShape.ANGULAR)
    )

    // 8. ☁️ Cloud Nine — Ariana Grande Thank U, Next (thème clair)
    val CLOUD_NINE = NovaTheme(
        "cloud_nine", "☁️", "Cloud Nine",
        hex("#FFB6C1"), hex("#E6E6FA"), hex("#FFC0CB"), hex("#FAFAFA"), hex("#FFF5F7"), hex("#555555"), hex("#9090A0"), hex("#C8A2C8"),
        effects = "Fondu doux façon nuage qui se dissipe", inspiration = "Ariana Grande Thank U, Next — pastel / calme",
        titleFont = "Baloo 2", bodyFont = "Quicksand", iconsDescription = "Nuages / bulles très arrondies, contours très doux", icons = IconStyle.CLOUD,
        transitionMs = 450, easing = MotionEasing.EASE_OUT, signature = Signature.CLOUD_FADE, cornerDp = 26,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, strokeWidthDp = 1.5f, glowDp = 16f, fillTop = ColorKey.PRIMARY, fillAlpha = 0.12f, shape = CurveShape.VERY_ROUND)
    )

    // 9. ☀️ Solara — Aya Nakamura
    val SOLARA = NovaTheme(
        "solara", "☀️", "Solara",
        hex("#FF6B35"), hex("#C4622D"), hex("#FFA500"), hex("#1A0A00"), hex("#2D1200"), hex("#FFF3E0"), hex("#D4A056"), hex("#FFD700"),
        effects = "Pulsation chaude (glow qui respire lentement)", inspiration = "Aya Nakamura — été / Afrique / soleil",
        titleFont = "DM Serif Display", bodyFont = "Poppins", iconsDescription = "Motifs solaires / bijoux dorés arrondis", icons = IconStyle.SUN,
        transitionMs = 300, easing = MotionEasing.EASE_IN_OUT, signature = Signature.WARM_PULSE, cornerDp = 20,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 10f, fillTop = ColorKey.ACCENT, fillBottom = ColorKey.PRIMARY)
    )

    // 10. 🦋 Chaos Born — Lady Gaga Born This Way
    val CHAOS_BORN = NovaTheme(
        "chaos_born", "🦋", "Chaos Born",
        hex("#C0C0C0"), hex("#1A1A1A"), hex("#E8E8E8"), hex("#050505"), hex("#111111"), hex("#F0F0F0"), hex("#909090"), hex("#DFDFDF"),
        effects = "Glitch aléatoire (position / opacité qui saute)", inspiration = "Lady Gaga Born This Way — avant-garde / chaos",
        titleFont = "Syne", bodyFont = "Unica One", iconsDescription = "Rivets / chaînes stylisées, formes métalliques asymétriques", icons = IconStyle.METAL,
        transitionMs = 250, easing = MotionEasing.RANDOM, signature = Signature.RANDOM_GLITCH, cornerDp = 2,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 3f, fillTop = ColorKey.PRIMARY, fillAlpha = 0.2f, shape = CurveShape.IRREGULAR)
    )

    // 11. 🌈 Survivor — communauté LGBTQ+ / Ballroom
    val SURVIVOR = NovaTheme(
        "survivor", "🌈", "Survivor",
        hex("#FF69B4"), hex("#8B00FF"), hex("#FF8C00"), hex("#080808"), hex("#111111"), hex("#FFFFFF"), hex("#FF8CC8"), hex("#00C800"),
        effects = "Burst de confettis au déblocage d'un palier", inspiration = "Communauté LGBTQ+ / Ballroom — fierté",
        titleFont = "Righteous", bodyFont = "Nunito", iconsDescription = "Trophées / podiums stylisés, dégradé arc-en-ciel", icons = IconStyle.PRIDE,
        transitionMs = 300, easing = MotionEasing.EASE_OUT, signature = Signature.CONFETTI, cornerDp = 16,
        chart = ChartStyle(stroke = null, glowDp = 6f, fillTop = ColorKey.PRIMARY, fillBottom = ColorKey.SECONDARY, rainbow = true),
        rainbowTextSecondary = true
    )

    // 12. 🌈 Rainbow Pop — Lady Gaga Chromatica
    val RAINBOW_POP = NovaTheme(
        "rainbow_pop", "🌈", "Rainbow Pop",
        hex("#FF4DA6"), hex("#4D79FF"), hex("#9B4DFF"), hex("#0A0010"), hex("#100020"), hex("#FFFFFF"), hex("#FFB3D9"), hex("#FFD700"),
        effects = "Paillettes qui scintillent en continu", inspiration = "Lady Gaga Chromatica — pop / camp / inclusif",
        titleFont = "Bungee", bodyFont = "Baloo 2", iconsDescription = "Formes planétaires / cristal, contours glossy bonbon", icons = IconStyle.CANDY,
        transitionMs = 250, easing = MotionEasing.EASE_OUT, signature = Signature.SPARKLES, cornerDp = 22,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 12f, fillTop = ColorKey.PRIMARY, fillBottom = ColorKey.SECONDARY, deco = CurveDeco.DISCO_DOTS)
    )

    // 13. 🎤 Pop Revolution — BABYMONSTER + Taylor Swift 1989
    val POP_REVOLUTION = NovaTheme(
        "pop_revolution", "🎤", "Pop Revolution",
        hex("#4A90D9"), hex("#FF6B9D"), hex("#00BFFF"), hex("#050A1A"), hex("#0A1428"), hex("#FFFFFF"), hex("#A0C4FF"), hex("#F0F8FF"),
        effects = "Flash rapide bleu / blanc au changement d'onglet", inspiration = "BABYMONSTER + Taylor Swift 1989 — K-pop / énergie",
        titleFont = "Montserrat", bodyFont = "Inter", iconsDescription = "Mix polaroid minimal + accents streetwear saturés", icons = IconStyle.POLAROID,
        transitionMs = 180, easing = MotionEasing.EASE_OUT_EXPO, signature = Signature.BLUE_FLASH, cornerDp = 10,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, strokeWidthDp = 3f, glowDp = 1f, fillTop = ColorKey.PRIMARY, fillAlpha = 0.3f)
    )

    // 14. 🌍 African Confessions — Ebony — Menelik
    val AFRICAN_CONFESSIONS = NovaTheme(
        "african_confessions", "🌍", "African Confessions",
        hex("#C4622D"), hex("#2D5A27"), hex("#D4A017"), hex("#120800"), hex("#1E0E00"), hex("#FFF3E0"), hex("#C4922D"), hex("#FFB347"),
        effects = "Motif géométrique qui se dessine en fondu", inspiration = "Ebony — Menelik (24 avril 2026, mythologie éthiopienne / Reine de Saba)",
        titleFont = "Fraunces", bodyFont = "Ubuntu", iconsDescription = "Motifs géométriques éthiopiens / royaux, couronnes terre cuite", icons = IconStyle.GEOMETRIC,
        transitionMs = 320, easing = MotionEasing.EASE_IN_OUT, signature = Signature.PATTERN_REVEAL, cornerDp = 8,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 5f, fillTop = ColorKey.TEXT_SECONDARY, fillBottom = ColorKey.SECONDARY)
    )

    // 15. 😇 Bad Angel — Ariana Grande Dangerous Woman + Rihanna Good Girl Gone Bad
    val BAD_ANGEL = NovaTheme(
        "bad_angel", "😇", "Bad Angel",
        hex("#CC0033"), hex("#FFFFFF"), hex("#FF3366"), hex("#080808"), hex("#141414"), hex("#FFFFFF"), hex("#AAAAAA"), hex("#FF0044"),
        effects = "Contraste qui bascule clair / sombre", inspiration = "Ariana Grande Dangerous Woman + Rihanna Good Girl Gone Bad — dualité / élégance",
        titleFont = "Cinzel", bodyFont = "Cormorant", iconsDescription = "Duales (halo fin vs cornes épaisses)", icons = IconStyle.DUAL,
        transitionMs = 300, easing = MotionEasing.EASE_IN_OUT, signature = Signature.DUAL_CONTRAST, cornerDp = 14,
        chart = ChartStyle(stroke = ColorKey.PRIMARY, glowDp = 8f, fillTop = ColorKey.PRIMARY, alternatingGlow = true)
    )

    val ALL: List<NovaTheme> = listOf(
        CYBER_NOVA, NEON_DISCO, VILLAIN_ERA, SLAY_QUEEN, PINK_Y2K, VELVET_STAGE, PINK_VENOM, CLOUD_NINE,
        SOLARA, CHAOS_BORN, SURVIVOR, RAINBOW_POP, POP_REVOLUTION, AFRICAN_CONFESSIONS, BAD_ANGEL
    )

    val DEFAULT = CYBER_NOVA

    fun byId(id: String?): NovaTheme = ALL.firstOrNull { it.id == id } ?: DEFAULT
}

/** Couleurs transverses (indépendantes du thème). */
object NovaColors {
    val Gold = Color(0xFFFFD700)
    val Silver = Color(0xFFC0C0C0)
    val Platinum = Color(0xFFE5E4E2)
    val Diamond = Color(0xFF00FFFF)
    val Up = Color(0xFF2ECC71)
    val Down = Color(0xFFE74C3C)
    val Neutral = Color(0xFF95A5A6)
    val DirectDebut = Color(0xFF7B2FBE)
    /** Arc-en-ciel pride (Survivor / Rainbow Pop). */
    val Rainbow = listOf(Color(0xFFE40303), Color(0xFFFF8C00), Color(0xFFFFED00), Color(0xFF008026), Color(0xFF004DFF), Color(0xFF750787))
}
