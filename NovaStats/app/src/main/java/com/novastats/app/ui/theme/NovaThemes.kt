package com.novastats.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Les 15 thèmes NovaStats. Chaque thème garde son propre mode (pas de toggle clair/sombre).
 * Palette exacte issue du cahier des charges.
 */
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
    val effects: String
) {
    /** Thème clair si le fond est lumineux (Pink Y2K, Cloud Nine). */
    val isLight: Boolean get() = background.luminance() > 0.5f
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
/** "#RRGGBB" → Color, sans dépendance Android (utilisable en tests JVM). */
private fun hex(h: String): Color {
    val v = h.removePrefix("#").toLong(16)
    return Color(red = ((v shr 16) and 0xFF).toInt(), green = ((v shr 8) and 0xFF).toInt(), blue = (v and 0xFF).toInt())
}

object NovaThemes {
    val CYBER_NOVA = NovaTheme("cyber_nova", "🌌", "Cyber Nova", hex("#FF006E"), hex("#00B4FF"), hex("#BD00FF"), hex("#050510"), hex("#0D0D2B"), hex("#F0F0FF"), hex("#A0A0C0"), hex("#00FFF0"), "Hologrammes, glitch, particules digitales, glow pulsant")
    val NEON_DISCO = NovaTheme("neon_disco", "🟣", "Neon Disco", hex("#9B00FF"), hex("#FFD700"), hex("#FF69B4"), hex("#0A0010"), hex("#1A0030"), hex("#E8E8FF"), hex("#B0A0C0"), hex("#C0C0C0"), "Boule à facettes, reflets métalliques, néons")
    val VILLAIN_ERA = NovaTheme("villain_era", "🖤", "Villain Era", hex("#CC0000"), hex("#4A4A4A"), hex("#8B0000"), hex("#080808"), hex("#111111"), hex("#EEEEEE"), hex("#888888"), hex("#FF0000"), "Glitch, coins anguleux, graphiques agressifs")
    val SLAY_QUEEN = NovaTheme("slay_queen", "👑", "Slay Queen", hex("#FFD700"), hex("#FF1493"), hex("#FFA500"), hex("#0A0A0A"), hex("#1A1500"), hex("#FFFFFF"), hex("#D4AF37"), hex("#B76E79"), "Or, couronnes, paillettes")
    val PINK_Y2K = NovaTheme("pink_y2k", "🍭", "Pink Y2K", hex("#FF69B4"), hex("#87CEEB"), hex("#FF1493"), hex("#FFF0F5"), hex("#FFE4E1"), hex("#8B0045"), hex("#C06080"), hex("#DA70D6"), "Bulles, brillance, années 2000")
    val VELVET_STAGE = NovaTheme("velvet_stage", "🎭", "Velvet Stage", hex("#8B0000"), hex("#722F37"), hex("#CFB53B"), hex("#0C0008"), hex("#1A0010"), hex("#FFF8DC"), hex("#D4AF37"), hex("#FFD700"), "Rideaux, velours, projecteurs")
    val PINK_VENOM = NovaTheme("pink_venom", "🖤", "Pink Venom", hex("#FF0080"), hex("#FF0030"), hex("#FF69B4"), hex("#000000"), hex("#0D0008"), hex("#FFFFFF"), hex("#FFB6C1"), hex("#FF007F"), "Noir & rose venimeux, contrastes durs")
    val CLOUD_NINE = NovaTheme("cloud_nine", "☁️", "Cloud Nine", hex("#FFB6C1"), hex("#E6E6FA"), hex("#FFC0CB"), hex("#FAFAFA"), hex("#FFF5F7"), hex("#555555"), hex("#9090A0"), hex("#C8A2C8"), "Pastels, douceur, nuages")
    val SOLARA = NovaTheme("solara", "☀️", "Solara", hex("#FF6B35"), hex("#C4622D"), hex("#FFA500"), hex("#1A0A00"), hex("#2D1200"), hex("#FFF3E0"), hex("#D4A056"), hex("#FFD700"), "Soleil, chaleur, dégradés orangés")
    val CHAOS_BORN = NovaTheme("chaos_born", "🦋", "Chaos Born", hex("#C0C0C0"), hex("#1A1A1A"), hex("#E8E8E8"), hex("#050505"), hex("#111111"), hex("#F0F0F0"), hex("#909090"), hex("#DFDFDF"), "Monochrome, chrome, chaos structuré")
    val SURVIVOR = NovaTheme("survivor", "🌈", "Survivor", hex("#FF69B4"), hex("#8B00FF"), hex("#FF8C00"), hex("#080808"), hex("#111111"), hex("#FFFFFF"), hex("#B0B0B0"), hex("#00C800"), "Multicolore, énergie, résilience")
    val RAINBOW_POP = NovaTheme("rainbow_pop", "🌈", "Rainbow Pop", hex("#FF4DA6"), hex("#4D79FF"), hex("#9B4DFF"), hex("#0A0010"), hex("#100020"), hex("#FFFFFF"), hex("#FFB3D9"), hex("#FFD700"), "Arc-en-ciel, pop, fun")
    val POP_REVOLUTION = NovaTheme("pop_revolution", "🎤", "Pop Revolution", hex("#4A90D9"), hex("#FF6B9D"), hex("#00BFFF"), hex("#050A1A"), hex("#0A1428"), hex("#FFFFFF"), hex("#A0C4FF"), hex("#F0F8FF"), "Bleu électrique, scène, micro")
    val AFRICAN_CONFESSIONS = NovaTheme("african_confessions", "🌍", "African Confessions", hex("#C4622D"), hex("#2D5A27"), hex("#D4A017"), hex("#120800"), hex("#1E0E00"), hex("#FFF3E0"), hex("#C4922D"), hex("#FFB347"), "Terre, motifs, chaleur")
    val BAD_ANGEL = NovaTheme("bad_angel", "😇", "Bad Angel", hex("#CC0033"), hex("#FFFFFF"), hex("#FF3366"), hex("#080808"), hex("#141414"), hex("#FFFFFF"), hex("#AAAAAA"), hex("#FF0044"), "Rouge & blanc, ange/démon")

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
}
