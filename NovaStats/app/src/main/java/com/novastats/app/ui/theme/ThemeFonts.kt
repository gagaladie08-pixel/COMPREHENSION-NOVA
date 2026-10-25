package com.novastats.app.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.isAvailableOnDevice
import com.novastats.app.R

/**
 * Polices des thèmes — familles sélectionnées embarquées dans l'APK, autres familles chargées via
 * GoogleFont.Provider (Google Play Services Fonts). Pour ces dernières, une police système proche
 * (DeviceFontFamilyName) prend le relais si le téléchargement échoue ou si Play Services est absent.
 */
object NovaFonts {
    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

    private class Spec(val weights: List<Int>, val italic: Boolean, val fallback: String)

    private const val SERIF = "serif"
    private const val SANS = "sans-serif"
    private const val COND = "sans-serif-condensed"
    private const val BLACK = "sans-serif-black"
    private const val LIGHT = "sans-serif-light"

    private val specs: Map<String, Spec> = mapOf(
        // Thèmes
        "Orbitron" to Spec(listOf(400, 500, 600, 700, 800, 900), false, BLACK),
        "Rajdhani" to Spec(listOf(300, 400, 500, 600, 700), false, COND),
        "Audiowide" to Spec(listOf(400), false, BLACK),
        "Poppins" to Spec(listOf(300, 400, 500, 600, 700, 800, 900), true, SANS),
        "Bebas Neue" to Spec(listOf(400), false, COND),
        "Oswald" to Spec(listOf(300, 400, 500, 600, 700), false, COND),
        "Playfair Display" to Spec(listOf(400, 500, 600, 700, 800, 900), true, SERIF),
        "Montserrat" to Spec(listOf(300, 400, 500, 600, 700, 800, 900), true, SANS),
        "Baloo 2" to Spec(listOf(400, 500, 600, 700, 800), false, SANS),
        "Fredoka" to Spec(listOf(300, 400, 500, 600, 700), false, SANS),
        "Abril Fatface" to Spec(listOf(400), false, SERIF),
        "Cormorant" to Spec(listOf(300, 400, 500, 600, 700), true, SERIF),
        "Anton" to Spec(listOf(400), false, COND),
        "Archivo Black" to Spec(listOf(400), false, BLACK),
        "Quicksand" to Spec(listOf(300, 400, 500, 600, 700), false, SANS),
        "DM Serif Display" to Spec(listOf(400), true, SERIF),
        "Syne" to Spec(listOf(400, 500, 600, 700, 800), false, SANS),
        "Unica One" to Spec(listOf(400), false, COND),
        "Righteous" to Spec(listOf(400), false, BLACK),
        "Nunito" to Spec(listOf(300, 400, 500, 600, 700, 800, 900), true, SANS),
        "Bungee" to Spec(listOf(400), false, BLACK),
        "Inter" to Spec(listOf(300, 400, 500, 600, 700, 800, 900), false, SANS),
        "Fraunces" to Spec(listOf(300, 400, 500, 600, 700, 800, 900), true, SERIF),
        "Ubuntu" to Spec(listOf(300, 400, 500, 700), true, SANS),
        "Cinzel" to Spec(listOf(400, 500, 600, 700, 800, 900), false, SERIF),
        // Onboarding
        "Cinzel Decorative" to Spec(listOf(400, 700, 900), false, SERIF),
        "Cormorant Garamond" to Spec(listOf(300, 400, 500, 600, 700), true, SERIF),
        "Raleway" to Spec(listOf(300, 400, 500, 600, 700, 800, 900), true, LIGHT)
    )

    private val cache = HashMap<String, FontFamily>()
    private val deviceCache = HashMap<String, FontFamily>()

    @Volatile private var providerAvailable: Boolean? = null

    /** Vrai si Google Play Services Fonts est présent et signé correctement (vérifié une fois). */
    fun isProviderAvailable(context: Context): Boolean {
        providerAvailable?.let { return it }
        val ok = runCatching { provider.isAvailableOnDevice(context) }.getOrDefault(false)
        providerAvailable = ok
        return ok
    }

    /** Les familles locales gardent leur identité même hors ligne ou sans Google Play Services Fonts. */
    private data class LocalFont(
        val resourceId: Int,
        val weights: List<Int>,
        val italicResourceId: Int? = null
    )
    private val local: Map<String, LocalFont> = mapOf(
        "Inter" to LocalFont(R.font.inter, listOf(300, 400, 500, 600, 700, 800, 900)),
        "Cinzel" to LocalFont(R.font.cinzel, listOf(400, 500, 600, 700, 800, 900)),
        "Righteous" to LocalFont(R.font.righteous, listOf(400)),
        "Nunito" to LocalFont(R.font.nunito, listOf(300, 400, 500, 600, 700, 800, 900), R.font.nunito_italic)
    )

    fun family(name: String, online: Boolean = true): FontFamily {
        local[name]?.let { localFont ->
            return cache.getOrPut(name) {
                val faces = localFont.weights.map { weight -> Font(localFont.resourceId, FontWeight(weight)) }.toMutableList()
                localFont.italicResourceId?.let { italicResource ->
                    localFont.weights.forEach { weight ->
                        faces += Font(italicResource, FontWeight(weight), FontStyle.Italic)
                    }
                }
                FontFamily(faces)
            }
        }
        val spec = specs[name] ?: Spec(listOf(400, 700), false, SANS)
        if (!online) return deviceFamily(spec.fallback)
        return cache.getOrPut(name) {
            val gf = GoogleFont(name)
            val fonts = ArrayList<Font>()
            spec.weights.forEach { w -> fonts += Font(googleFont = gf, fontProvider = provider, weight = FontWeight(w), style = FontStyle.Normal) }
            if (spec.italic) listOf(400, 700).filter { it in spec.weights }.forEach { w -> fonts += Font(googleFont = gf, fontProvider = provider, weight = FontWeight(w), style = FontStyle.Italic) }
            // Repli : police système la plus proche, toutes graisses
            val dev = DeviceFontFamilyName(spec.fallback)
            listOf(300, 400, 500, 600, 700, 800, 900).forEach { w ->
                fonts += Font(familyName = dev, weight = FontWeight(w), style = FontStyle.Normal)
                fonts += Font(familyName = dev, weight = FontWeight(w), style = FontStyle.Italic)
            }
            FontFamily(fonts)
        }
    }

    private fun deviceFamily(fallback: String): FontFamily = deviceCache.getOrPut(fallback) {
        val dev = DeviceFontFamilyName(fallback)
        FontFamily(listOf(300, 400, 500, 600, 700, 800, 900).flatMap { w ->
            listOf(Font(familyName = dev, weight = FontWeight(w), style = FontStyle.Normal), Font(familyName = dev, weight = FontWeight(w), style = FontStyle.Italic))
        })
    }
}
