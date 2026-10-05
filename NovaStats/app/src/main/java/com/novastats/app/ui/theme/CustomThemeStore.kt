package com.novastats.app.ui.theme

import android.content.Context

/**
 * Persistance du thème personnalisé (SharedPreferences — simple, synchrone, sans coroutine).
 * `null` = l'utilisateur n'a pas encore créé son thème.
 */
object CustomThemeStore {

    private const val P = "nova_custom_theme"
    private const val K_EXISTS = "exists"
    private const val K_NAME = "name"
    private const val K_EMOJI = "emoji"
    private const val K_PRIMARY_HUE = "primary_hue"
    private const val K_SECONDARY_HUE = "secondary_hue"
    private const val K_ACCENT_HUE = "accent_hue"
    private const val K_SAT = "saturation"
    private const val K_LIGHT = "lightness"
    private const val K_DARK = "dark"
    private const val K_CORNER = "corner_dp"
    private const val K_TITLE_FONT = "title_font"
    private const val K_BODY_FONT = "body_font"
    private const val K_SIGNATURE = "signature"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun load(context: Context): CustomThemeSpec? {
        val p = prefs(context)
        if (!p.getBoolean(K_EXISTS, false)) return null
        val signature = runCatching {
            Signature.valueOf(p.getString(K_SIGNATURE, Signature.GOLD_SHIMMER.name) ?: Signature.GOLD_SHIMMER.name)
        }.getOrDefault(Signature.GOLD_SHIMMER)
        return CustomThemeSpec(
            name = p.getString(K_NAME, "Mon thème") ?: "Mon thème",
            emoji = p.getString(K_EMOJI, "✨") ?: "✨",
            primaryHue = p.getFloat(K_PRIMARY_HUE, 320f),
            secondaryHue = p.getFloat(K_SECONDARY_HUE, 200f),
            accentHue = p.getFloat(K_ACCENT_HUE, 175f),
            saturation = p.getFloat(K_SAT, 0.88f),
            lightness = p.getFloat(K_LIGHT, 0.58f),
            dark = p.getBoolean(K_DARK, true),
            cornerDp = p.getInt(K_CORNER, 16),
            titleFont = p.getString(K_TITLE_FONT, "Orbitron") ?: "Orbitron",
            bodyFont = p.getString(K_BODY_FONT, "Rajdhani") ?: "Rajdhani",
            signature = signature
        )
    }

    fun save(context: Context, spec: CustomThemeSpec) {
        prefs(context).edit()
            .putBoolean(K_EXISTS, true)
            .putString(K_NAME, spec.name)
            .putString(K_EMOJI, spec.emoji)
            .putFloat(K_PRIMARY_HUE, spec.primaryHue)
            .putFloat(K_SECONDARY_HUE, spec.secondaryHue)
            .putFloat(K_ACCENT_HUE, spec.accentHue)
            .putFloat(K_SAT, spec.saturation)
            .putFloat(K_LIGHT, spec.lightness)
            .putBoolean(K_DARK, spec.dark)
            .putInt(K_CORNER, spec.cornerDp)
            .putString(K_TITLE_FONT, spec.titleFont)
            .putString(K_BODY_FONT, spec.bodyFont)
            .putString(K_SIGNATURE, spec.signature.name)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
