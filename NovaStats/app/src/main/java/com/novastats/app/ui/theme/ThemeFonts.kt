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
 * Les familles des 15 thèmes prédéfinis sont embarquées pour garder leur identité hors ligne.
 * Les autres choix (thèmes personnalisés et onboarding) passent par GoogleFont.Provider, puis
 * utilisent une police système proche si Google Play Services Fonts est absent ou le téléchargement échoue.
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
        "Raleway" to Spec(listOf(300, 400, 500, 600, 700, 800, 900), true, LIGHT),
        "Comfortaa" to Spec(listOf(300, 400, 500, 600, 700), false, SANS),
        "Bowlby One SC" to Spec(listOf(400), false, BLACK)
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

    /** Faces locales des polices des 15 thèmes : mêmes dessins avec ou sans réseau/Play Services Fonts. */
    private data class LocalFontFace(val resourceId: Int, val weight: Int, val style: FontStyle = FontStyle.Normal)

    private fun variableFaces(resourceId: Int, weights: List<Int>, italicResourceId: Int? = null): List<LocalFontFace> =
        buildList {
            weights.forEach { add(LocalFontFace(resourceId, it)) }
            italicResourceId?.let { italic ->
                weights.filter { it == 400 || it == 700 }.forEach { add(LocalFontFace(italic, it, FontStyle.Italic)) }
            }
        }

    private fun staticFace(resourceId: Int, weight: Int = 400, style: FontStyle = FontStyle.Normal) =
        LocalFontFace(resourceId, weight, style)

    private val local: Map<String, List<LocalFontFace>> = mapOf(
        "Orbitron" to variableFaces(R.font.orbitron, listOf(400, 500, 600, 700, 800, 900)),
        "Rajdhani" to listOf(staticFace(R.font.rajdhani_light, 300), staticFace(R.font.rajdhani, 400), staticFace(R.font.rajdhani_bold, 700)),
        "Audiowide" to listOf(staticFace(R.font.audiowide)),
        "Poppins" to listOf(
            staticFace(R.font.poppins, 400), staticFace(R.font.poppins_semibold, 600), staticFace(R.font.poppins_bold, 700),
            staticFace(R.font.poppins_italic, 400, FontStyle.Italic), staticFace(R.font.poppins_bold_italic, 700, FontStyle.Italic)
        ),
        "Bebas Neue" to listOf(staticFace(R.font.bebas_neue)),
        "Playfair Display" to variableFaces(R.font.playfair_display, listOf(400, 500, 600, 700, 800, 900), R.font.playfair_display_italic),
        "Montserrat" to variableFaces(R.font.montserrat, listOf(300, 400, 500, 600, 700, 800, 900), R.font.montserrat_italic),
        "Baloo 2" to variableFaces(R.font.baloo_2, listOf(400, 500, 600, 700, 800)),
        "Fredoka" to variableFaces(R.font.fredoka, listOf(300, 400, 500, 600, 700)),
        "Abril Fatface" to listOf(staticFace(R.font.abril_fatface)),
        "Anton" to listOf(staticFace(R.font.anton)),
        "Quicksand" to variableFaces(R.font.quicksand, listOf(300, 400, 500, 600, 700)),
        "DM Serif Display" to listOf(staticFace(R.font.dm_serif_display), staticFace(R.font.dm_serif_display_italic, 400, FontStyle.Italic)),
        "Syne" to variableFaces(R.font.syne, listOf(400, 500, 600, 700, 800)),
        "Unica One" to listOf(staticFace(R.font.unica_one)),
        "Righteous" to listOf(staticFace(R.font.righteous)),
        "Nunito" to variableFaces(R.font.nunito, listOf(300, 400, 500, 600, 700, 800, 900), R.font.nunito_italic),
        "Bungee" to listOf(staticFace(R.font.bungee)),
        "Inter" to variableFaces(R.font.inter, listOf(300, 400, 500, 600, 700, 800, 900)),
        "Fraunces" to variableFaces(R.font.fraunces, listOf(300, 400, 500, 600, 700, 800, 900), R.font.fraunces_italic),
        "Ubuntu" to listOf(
            staticFace(R.font.ubuntu, 400), staticFace(R.font.ubuntu_bold, 700),
            staticFace(R.font.ubuntu_italic, 400, FontStyle.Italic), staticFace(R.font.ubuntu_bold_italic, 700, FontStyle.Italic)
        ),
        "Cinzel" to variableFaces(R.font.cinzel, listOf(400, 500, 600, 700, 800, 900)),
        "Comfortaa" to variableFaces(R.font.comfortaa, listOf(300, 400, 500, 600, 700)),
        "Bowlby One SC" to listOf(staticFace(R.font.bowlby_one_sc)),
        "Cormorant" to variableFaces(R.font.cormorant, listOf(300, 400, 500, 600, 700), R.font.cormorant_italic)
    )

    fun family(name: String, online: Boolean = true): FontFamily {
        local[name]?.let { faces ->
            return cache.getOrPut(name) {
                FontFamily(faces.map { face -> Font(face.resourceId, FontWeight(face.weight), face.style) })
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
