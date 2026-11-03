package com.novastats.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.novastats.app.R
import com.novastats.app.data.repository.RewindData
import com.novastats.app.data.repository.RewindSpec
import com.novastats.app.data.repository.RecordExplainer
import com.novastats.app.ui.screens.formatCount
import com.novastats.app.ui.screens.formatDuration
import com.novastats.app.ui.screens.EntityHistory
import com.novastats.app.ui.screens.storySentence
import com.novastats.app.domain.ChartAppearance
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.ui.theme.NovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * 🖼️ Cartes partageables — rendu natif (android.graphics) en 1080 × 1920, aux couleurs du thème actif.
 * Le fichier est écrit dans `cacheDir/share/` et partagé via FileProvider (aucune permission de stockage).
 */
object ShareCards {

    const val W = 1080
    const val H = 1920

    /** Partage une image PNG déjà rendue (feuille de partage système). */
    fun share(ctx: Context, file: File, title: String = "Partager") {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Mon Nova Rewind ✨ — NovaStats")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Rend la carte Rewind et renvoie le PNG. */
    suspend fun renderRewind(ctx: Context, data: RewindData, theme: NovaTheme): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val survivor = theme.id == "survivor"

        /* ---- Fond : dégradé thème + halos ---- */
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(theme.background.toArgb(), blend(theme.background.toArgb(), theme.primary.toArgb(), 0.22f), AColor.BLACK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 180f, 320f, 520f, theme.primary.toArgb(), 0.55f)
        halo(c, 960f, 1500f, 620f, theme.secondary.toArgb(), 0.45f)
        halo(c, 540f, 1000f, 420f, theme.accent.toArgb(), 0.25f)

        /* ---- Survivor : bandes des drapeaux en diagonale, très transparentes ---- */
        if (survivor) {
            val bands = NovaColors.PrideFlags.flatten()
            val p = Paint().apply { alpha = 26 }
            c.save(); c.rotate(-18f, W / 2f, H / 2f)
            val bh = 60f
            var y = -600f; var i = 0
            while (y < H + 600f) { p.color = bands[i % bands.size].toArgb(); p.alpha = 30; c.drawRect(-400f, y, W + 400f, y + bh, p); y += bh; i++ }
            c.restore()
        }

        /* ---- En-tête ---- */
        val eyebrow = textPaint(bold, 30f, withAlpha(theme.accent.toArgb(), 0.95f)).apply { letterSpacing = 0.22f }
        c.drawText("NOVA REWIND", 72f, 150f, eyebrow)
        val title = textPaint(bold, 104f, theme.text.toArgb()).apply { letterSpacing = -0.03f }
        c.drawText(data.spec.label, 72f, 270f, title)
        val sub = textPaint(inter, 34f, theme.textSecondary.toArgb())
        c.drawText(if (data.spec.kind == RewindSpec.Kind.MONTH) "Mon mois en musique" else "Mon année en musique", 72f, 330f, sub)

        /* ---- Artiste n°1 : photo + nom ---- */
        val top = data.topArtist
        val coverUrl = top?.artist?.photoUrl ?: data.topTrack?.track?.coverUrl
        val coverBmp = coverUrl?.let { loadBitmap(ctx, it, 700) }
        val coverRect = RectF(72f, 400f, 72f + 460f, 400f + 460f)
        // ombre + bord
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.BLACK; alpha = 110; maskFilter = android.graphics.BlurMaskFilter(40f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
        c.drawRoundRect(RectF(coverRect).apply { offset(0f, 22f) }, 44f, 44f, shadow)
        if (coverBmp != null) drawCover(c, coverBmp, coverRect, 44f) else {
            val ph = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, theme.primary.toArgb(), theme.secondary.toArgb(), Shader.TileMode.CLAMP) }
            c.drawRoundRect(coverRect, 44f, 44f, ph)
            val initial = textPaint(bold, 200f, withAlpha(AColor.WHITE, 0.9f)).apply { textAlign = Paint.Align.CENTER }
            c.drawText((top?.artist?.name ?: data.spec.label).take(1).uppercase(), coverRect.centerX(), coverRect.centerY() + 70f, initial)
        }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = withAlpha(theme.text.toArgb(), 0.25f) }
        c.drawRoundRect(coverRect, 44f, 44f, border)

        // Bloc texte à droite de la photo
        val x2 = 572f
        val lab = textPaint(bold, 26f, theme.textSecondary.toArgb()).apply { letterSpacing = 0.16f }
        c.drawText("ARTISTE N°1", x2, 450f, lab)
        val name = textPaint(bold, 56f, theme.text.toArgb())
        drawWrapped(c, top?.artist?.name ?: "—", x2, 520f, W - 72f - x2, name, maxLines = 2, lineH = 62f)
        val share = textPaint(inter, 32f, theme.accent.toArgb())
        if (top != null) c.drawText("${formatCount(top.periodPlays)} écoutes · ${data.topArtistShare} % de ${if (data.spec.kind == RewindSpec.Kind.MONTH) "mon mois" else "mon année"}", x2, 690f, share.apply { textSize = 28f })
        c.drawText("TITRE N°1", x2, 760f, lab)
        val tName = textPaint(bold, 40f, theme.text.toArgb())
        drawWrapped(c, data.topTrack?.track?.title ?: "—", x2, 812f, W - 72f - x2, tName, maxLines = 2, lineH = 46f)

        /* ---- Grands chiffres ---- */
        val statsTop = 960f
        val cells = listOf(
            formatCount(data.totals.plays) to "écoutes",
            formatDuration(data.totals.durationMs) to "de musique",
            formatCount(data.totals.artists) to "artistes",
            formatCount(data.totals.tracks) to "titres"
        )
        val cw = (W - 144f - 3 * 20f) / 4f
        cells.forEachIndexed { i, (v, l) ->
            val left = 72f + i * (cw + 20f)
            val r = RectF(left, statsTop, left + cw, statsTop + 190f)
            val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.75f) }
            c.drawRoundRect(r, 30f, 30f, card)
            c.drawRoundRect(r, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(theme.primary.toArgb(), 0.35f) })
            val vp = textPaint(bold, if (v.length > 6) 40f else 50f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER }
            c.drawText(v, r.centerX(), r.top + 92f, vp)
            val lp = textPaint(inter, 26f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER }
            c.drawText(l, r.centerX(), r.top + 145f, lp)
        }

        /* ---- Top 5 titres ---- */
        var y = 1240f
        c.drawText("MON TOP 5", 72f, y, lab); y += 30f
        val rowPaint = textPaint(inter, 34f, theme.text.toArgb())
        val numPaint = textPaint(bold, 34f, theme.primary.toArgb())
        val plays = textPaint(inter, 28f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.RIGHT }
        data.topTracks.take(5).forEachIndexed { i, t ->
            val rowY = y + 56f + i * 74f
            c.drawText("${i + 1}", 72f, rowY, numPaint)
            val txt = ellipsize("${t.track.title} — ${t.artistName}", rowPaint, W - 72f - 140f - 180f)
            c.drawText(txt, 140f, rowY, rowPaint)
            c.drawText(formatCount(t.periodPlays), W - 72f, rowY, plays)
            val sep = Paint().apply { color = withAlpha(theme.text.toArgb(), 0.08f) }
            c.drawRect(140f, rowY + 22f, W - 72f, rowY + 23f, sep)
        }

        /* ---- Pied : lignes signatures ---- */
        y = 1700f
        val foot = textPaint(inter, 28f, theme.textSecondary.toArgb())
        val lines = buildList {
            data.bestDay?.let { add("🔥 Record du ${it.dayOfMonth}/${"%02d".format(it.monthValue)} · ${formatCount(data.bestDayPlays)} écoutes") }
            if (data.longestStreak > 1) add("📆 ${data.longestStreak} jours d'affilée")
            if (data.certifications.isNotEmpty()) add("🏅 ${data.certifications.size} certification${if (data.certifications.size > 1) "s" else ""}")
            if (data.newArtistCount > 0) add("✨ ${data.newArtistCount} nouveaux artistes")
        }
        lines.take(2).forEachIndexed { i, s -> c.drawText(s, 72f, y + i * 44f, foot) }

        val brand = textPaint(bold, 30f, theme.primary.toArgb()).apply { letterSpacing = 0.3f; textAlign = Paint.Align.RIGHT }
        c.drawText("NOVASTATS", W - 72f, 1850f, brand)
        if (survivor) {
            // Ruban arc-en-ciel discret en bas
            val flags = NovaColors.PrideFlags.flatten()
            val bw = W / flags.size.toFloat()
            flags.forEachIndexed { i, col -> c.drawRect(i * bw, H - 10f, (i + 1) * bw, H.toFloat(), Paint().apply { color = col.toArgb() }) }
        } else {
            c.drawRect(0f, H - 8f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, theme.primary.toArgb(), theme.secondary.toArgb(), Shader.TileMode.CLAMP) })
        }

        writePng(ctx, bmp, "nova_rewind_${data.spec.key}.png")
    }

    /** 📈 Affiche verticale dédiée aux archives de mouvement de position. */
    suspend fun renderMovementCard(
        ctx: Context,
        recordId: String,
        entityId: Long,
        name: String,
        subtitle: String?,
        imageUrl: String?,
        isArtist: Boolean,
        recordTitle: String,
        metric: String,
        rank: Int?,
        context: String,
        period: Period?,
        zoneLimit: Int?,
        zoneLabel: String?,
        headline: String,
        facts: List<Pair<String, String>>,
        timeline: List<RecordExplainer.Point>,
        rule: String,
        accent: Color,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val accentArgb = accent.toArgb()
        val gold = NovaColors.Gold.toArgb()
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(0xFF080A10.toInt(), blend(0xFF080A10.toInt(), accentArgb, 0.20f), AColor.BLACK),
                floatArrayOf(0f, 0.57f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 170f, 390f, 560f, accentArgb, 0.34f)
        halo(c, 930f, 1160f, 520f, accentArgb, 0.18f)
        c.drawRoundRect(RectF(28f, 28f, W - 28f, H - 28f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(accentArgb, 0.48f)
        })
        c.drawRoundRect(RectF(43f, 43f, W - 43f, H - 43f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(AColor.WHITE, 0.14f)
        })

        c.drawText("NOVA RECORDS  ·  ARCHIVES DE TRAJECTOIRE", 72f, 116f, textPaint(bold, 25f, withAlpha(accentArgb, 0.98f)).apply { letterSpacing = 0.12f })
        c.drawText(ellipsize(context.uppercase(java.util.Locale.FRANCE), textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.70f)), W - 144f), 72f, 157f, textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.70f)))
        rank?.let {
            val badge = if (it == 1) "✦ DÉTENTEUR N°1 ✦" else "RANG N°$it DU RECORD"
            val badgeRect = RectF(72f, 181f, W - 72f, 228f)
            c.drawRoundRect(badgeRect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(if (it == 1) gold else accentArgb, 0.16f) })
            c.drawRoundRect(badgeRect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = withAlpha(if (it == 1) gold else accentArgb, 0.44f) })
            c.drawText(badge, badgeRect.centerX(), 212f, textPaint(bold, 19f, if (it == 1) gold else AColor.WHITE).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.10f })
        }

        val coverRect = RectF(340f, 258f, 740f, 658f)
        halo(c, coverRect.centerX(), coverRect.centerY(), 275f, accentArgb, 0.26f)
        val cover = imageUrl?.let { loadBitmap(ctx, it, 620) }
        if (cover != null) {
            if (isArtist) drawCircleCover(c, cover, coverRect.centerX(), coverRect.centerY(), 194f)
            else drawCover(c, cover, coverRect, 38f)
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, accentArgb, theme.secondary.toArgb(), Shader.TileMode.CLAMP) }
            if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 194f, placeholder) else c.drawRoundRect(coverRect, 38f, 38f, placeholder)
            c.drawText(name.take(1).uppercase(java.util.Locale.FRANCE), coverRect.centerX(), coverRect.centerY() + 36f, textPaint(bold, 135f, withAlpha(AColor.WHITE, 0.94f)).apply { textAlign = Paint.Align.CENTER })
        }
        val imageFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = withAlpha(accentArgb, 0.88f) }
        if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 199f, imageFrame) else c.drawRoundRect(coverRect, 38f, 38f, imageFrame)

        var nameBottom = drawWrapped(c, name, 72f, 715f, W - 144f, textPaint(bold, 56f, AColor.WHITE), maxLines = 2, lineH = 64f)
        subtitle?.takeIf { it.isNotBlank() }?.let {
            nameBottom = drawWrapped(c, it, 72f, nameBottom + 2f, W - 144f, textPaint(inter, 25f, withAlpha(AColor.WHITE, 0.72f)), maxLines = 1, lineH = 30f)
        }
        c.drawText(ellipsize(recordTitle.uppercase(java.util.Locale.FRANCE), textPaint(bold, 23f, accentArgb), W - 144f), 72f, maxOf(850f, nameBottom + 25f), textPaint(bold, 23f, accentArgb).apply { letterSpacing = 0.13f })
        val heroBottom = drawWrapped(c, headline, 72f, 916f, W - 144f, textPaint(bold, 54f, AColor.WHITE), maxLines = 2, lineH = 62f)
        c.drawText("VALEUR DU RECORD  ·  ${ellipsize(metric, textPaint(inter, 23f, withAlpha(AColor.WHITE, 0.72f)), W - 300f)}", 72f, maxOf(1055f, heroBottom + 14f), textPaint(inter, 23f, withAlpha(AColor.WHITE, 0.72f)))

        val plot = RectF(95f, 1157f, W - 72f, 1412f)
        c.drawRoundRect(RectF(72f, 1121f, W - 72f, 1450f), 27f, 27f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF171A23.toInt(), 0.92f) })
        c.drawRoundRect(RectF(72f, 1121f, W - 72f, 1450f), 27f, 27f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(accentArgb, 0.30f) })
        val zoneCaption = if (zoneLimit != null && zoneLimit < Int.MAX_VALUE) " · ${zoneLabel ?: "ZONE"} / #$zoneLimit" else ""
        c.drawText("CARTE DU MOUVEMENT$zoneCaption", 102f, 1151f, textPaint(bold, 21f, accentArgb).apply { letterSpacing = 0.10f })
        val finalHighlightIndex = timeline.indexOfLast { it.highlight }
        val nodes = timeline.mapIndexedNotNull { index, point ->
            point.position?.let { position ->
                val periodIndex = if (period == null || period == Period.GLOBAL) index else runCatching { RecordCatalog.periodIndex(Dates.parse(point.date), period) }.getOrDefault(index)
                MovementCardNode(index, point, position, periodIndex)
            }
        }
        if (nodes.isEmpty()) {
            c.drawText("Aucune trajectoire détaillée disponible", plot.centerX(), plot.centerY(), textPaint(inter, 22f, withAlpha(AColor.WHITE, 0.72f)).apply { textAlign = Paint.Align.CENTER })
        } else {
            val maxPosition = maxOf(10, nodes.maxOf { it.position }, zoneLimit?.takeIf { it < Int.MAX_VALUE } ?: 10)
            val minIndex = nodes.minOf { it.periodIndex }
            val maxIndex = nodes.maxOf { it.periodIndex }
            val ordinal = nodes.size < 2 || minIndex == maxIndex
            val span = if (ordinal) (nodes.size - 1).coerceAtLeast(1).toFloat() else (maxIndex - minIndex).coerceAtLeast(1).toFloat()
            fun nodeX(node: MovementCardNode): Float = if (ordinal) plot.left + plot.width() * node.sourceIndex / (nodes.size - 1).coerceAtLeast(1).toFloat() else plot.left + plot.width() * (node.periodIndex - minIndex) / span
            fun nodeY(position: Int): Float = plot.top + (position - 1).toFloat() / (maxPosition - 1).coerceAtLeast(1) * plot.height()
            listOf(1, (maxPosition / 2).coerceAtLeast(2), maxPosition).distinct().forEach { rankMark ->
                val yy = nodeY(rankMark)
                c.drawLine(plot.left, yy, plot.right, yy, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(AColor.WHITE, 0.16f); strokeWidth = 1.5f })
                c.drawText("#$rankMark", 87f, yy + 7f, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.70f)).apply { textAlign = Paint.Align.RIGHT })
            }
            zoneLimit?.takeIf { it < Int.MAX_VALUE }?.let { targetRank ->
                val targetY = nodeY(targetRank)
                c.drawLine(plot.left, targetY, plot.right, targetY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(gold, 0.72f); strokeWidth = 3f
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(11f, 9f), 0f)
                })
            }
            nodes.zipWithNext().forEach { (from, to) ->
                val startX = nodeX(from); val startY = nodeY(from.position)
                val endX = nodeX(to); val endY = nodeY(to.position)
                val gap = to.periodIndex - from.periodIndex > 1
                val outsideZone = zoneLimit != null && from.position > zoneLimit && to.position > zoneLimit
                if (!gap && !outsideZone) c.drawLine(startX, startY, endX, endY, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accentArgb, 0.20f); strokeWidth = 14f; strokeCap = Paint.Cap.ROUND })
                c.drawLine(startX, startY, endX, endY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when { gap -> withAlpha(accentArgb, 0.78f); outsideZone -> withAlpha(AColor.WHITE, 0.45f); else -> withAlpha(accentArgb, 0.98f) }; strokeWidth = if (gap) 4f else 6f; strokeCap = Paint.Cap.ROUND
                    if (gap) pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 11f), 0f)
                })
            }
            nodes.forEach { node ->
                val x = nodeX(node); val y = nodeY(node.position)
                val heroMarker = node.point.highlight && (recordId == "MOST_REENTRIES" || node.sourceIndex == finalHighlightIndex)
                val nodeColor = when {
                    heroMarker -> gold
                    node.point.highlight -> accentArgb
                    zoneLimit != null && node.position > zoneLimit -> withAlpha(AColor.WHITE, 0.56f)
                    else -> accentArgb
                }
                if (heroMarker) c.drawCircle(x, y, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(gold, 0.22f) })
                c.drawCircle(x, y, if (heroMarker) 9f else 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = nodeColor })
                c.drawCircle(x, y, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.WHITE })
            }
            val first = nodes.first().point
            val last = nodes.last().point
            c.drawText(ellipsize(first.label, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.70f)), 300f), plot.left, 1434f, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.70f)))
            c.drawText(ellipsize(last.label, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.70f)), 300f), plot.right, 1434f, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.70f)).apply { textAlign = Paint.Align.RIGHT })
        }

        drawWrapped(c, rule, 72f, 1481f, W - 144f, textPaint(inter, 17f, withAlpha(AColor.WHITE, 0.78f)), maxLines = 2, lineH = 22f)
        c.drawText("REPÈRES VÉRIFIÉS", 72f, 1544f, textPaint(bold, 20f, withAlpha(AColor.WHITE, 0.90f)).apply { letterSpacing = 0.10f })
        facts.take(4).forEachIndexed { index, (label, value) ->
            val column = index % 2
            val row = index / 2
            val left = 72f + column * 478f
            val top = 1563f + row * 111f
            val rect = RectF(left, top, left + 458f, top + 98f)
            c.drawRoundRect(rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF171A23.toInt(), 0.92f) })
            c.drawRoundRect(rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(accentArgb, 0.24f) })
            c.drawText(ellipsize(label.uppercase(java.util.Locale.FRANCE), textPaint(bold, 14f, withAlpha(AColor.WHITE, 0.64f)), rect.width() - 30f), left + 16f, top + 26f, textPaint(bold, 14f, withAlpha(AColor.WHITE, 0.64f)))
            drawWrapped(c, value, left + 16f, top + 55f, rect.width() - 30f, textPaint(bold, 19f, AColor.WHITE), maxLines = 2, lineH = 21f)
        }
        c.drawText("NOVASTATS", W - 72f, 1855f, textPaint(bold, 27f, accentArgb).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.25f })
        c.drawRect(0f, H - 9f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, accentArgb, gold, Shader.TileMode.CLAMP) })
        writePng(ctx, bmp, "nova_movement_${recordId.lowercase(java.util.Locale.ROOT)}_${entityId}_${period?.dbName ?: "chart"}.png")
    }

    /** 💎 Affiche « Le Trésor Nova » : une certification mise en scène comme une pièce de collection. */
    suspend fun renderCertificationArchiveCard(
        ctx: Context,
        entityId: Long,
        name: String,
        subtitle: String?,
        imageUrl: String?,
        isArtist: Boolean,
        categoryLabel: String,
        scope: String,
        tierLabel: String,
        tierEmoji: String,
        score: Int,
        rank: Int?,
        facts: List<Pair<String, String>>,
        items: List<RecordExplainer.Item>,
        accent: Color,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val accentArgb = accent.toArgb()
        val gold = NovaColors.Gold.toArgb()
        val deep = 0xFF08070C.toInt()
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(deep, blend(deep, accentArgb, 0.23f), 0xFF07080D.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        halo(c, 190f, 360f, 610f, accentArgb, 0.39f)
        halo(c, 950f, 1250f, 610f, gold, 0.16f)
        c.drawRoundRect(RectF(28f, 28f, W - 28f, H - 28f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(accentArgb, 0.48f) })
        c.drawRoundRect(RectF(44f, 44f, W - 44f, H - 44f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(AColor.WHITE, 0.14f) })

        c.drawText("NOVA  ·  LE TRÉSOR DES CERTIFICATIONS", 72f, 112f, textPaint(bold, 27f, withAlpha(accentArgb, 0.98f)).apply { letterSpacing = 0.12f })
        c.drawText(ellipsize(scope.uppercase(java.util.Locale.FRANCE), textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.72f)), W - 144f), 72f, 154f, textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.72f)))
        val tierRect = RectF(72f, 184f, W - 72f, 240f)
        c.drawRoundRect(tierRect, 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accentArgb, 0.16f) })
        c.drawRoundRect(tierRect, 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; color = withAlpha(accentArgb, 0.52f) })
        c.drawText("$tierEmoji  NIVEAU ${tierLabel.uppercase(java.util.Locale.FRANCE)}  ·  ${categoryLabel.uppercase(java.util.Locale.FRANCE)}", tierRect.centerX(), 220f, textPaint(bold, 22f, accentArgb).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.06f })
        rank?.let {
            val rankLabel = if (it == 1) "✦ N°1 DE LA COLLECTION ✦" else "COLLECTION CLASSÉE N°$it"
            c.drawText(rankLabel, W - 72f, 279f, textPaint(bold, 18f, if (it == 1) gold else withAlpha(AColor.WHITE, 0.8f)).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.08f })
        }

        val coverRect = RectF(367f, 282f, 713f, 628f)
        halo(c, coverRect.centerX(), coverRect.centerY(), 292f, accentArgb, 0.30f)
        val cover = imageUrl?.let { loadBitmap(ctx, it, 580) }
        if (cover != null) {
            if (isArtist) drawCircleCover(c, cover, coverRect.centerX(), coverRect.centerY(), 160f) else drawCover(c, cover, coverRect, 38f)
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, accentArgb, theme.secondary.toArgb(), Shader.TileMode.CLAMP) }
            if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 160f, placeholder) else c.drawRoundRect(coverRect, 38f, 38f, placeholder)
            c.drawText(name.take(1).uppercase(java.util.Locale.FRANCE), coverRect.centerX(), coverRect.centerY() + 38f, textPaint(bold, 132f, withAlpha(AColor.WHITE, 0.94f)).apply { textAlign = Paint.Align.CENTER })
        }
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = withAlpha(accentArgb, 0.90f) }
        if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 164f, frame) else c.drawRoundRect(coverRect, 38f, 38f, frame)
        c.drawText("✦", coverRect.centerX(), 651f, textPaint(bold, 35f, gold).apply { textAlign = Paint.Align.CENTER })

        val nameBottom = drawWrapped(c, name, 72f, 690f, W - 144f, textPaint(bold, 56f, AColor.WHITE), maxLines = 2, lineH = 60f)
        var identityBottom = nameBottom
        subtitle?.takeIf { it.isNotBlank() }?.let {
            val subtitleY = maxOf(772f, nameBottom + 28f)
            c.drawText(ellipsize(it, textPaint(inter, 25f, withAlpha(AColor.WHITE, 0.72f)), W - 144f), 72f, subtitleY, textPaint(inter, 25f, withAlpha(AColor.WHITE, 0.72f)))
            identityBottom = subtitleY
        }
        val collectionLabelY = maxOf(850f, identityBottom + 50f)
        val scoreY = maxOf(938f, collectionLabelY + 88f)
        val scoreCaptionY = maxOf(980f, scoreY + 42f)
        c.drawText("LA COLLECTION CERTIFIÉE", 72f, collectionLabelY, textPaint(bold, 23f, accentArgb).apply { letterSpacing = 0.12f })
        c.drawText(formatCount(score), 72f, scoreY, textPaint(bold, 78f, AColor.WHITE))
        c.drawText("PIÈCES ATTEINTES AU NIVEAU ${tierLabel.uppercase(java.util.Locale.FRANCE)}", 72f, scoreCaptionY, textPaint(bold, 19f, withAlpha(AColor.WHITE, 0.72f)).apply { letterSpacing = 0.07f })

        val shown = items.take(3)
        if (shown.isNotEmpty()) {
            c.drawText("LES PIÈCES QUI COMPOSENT LE TRÉSOR", 72f, 1084f, textPaint(bold, 21f, accentArgb).apply { letterSpacing = 0.06f })
            val gap = 18f
            val cardWidth = (W - 144f - gap * (shown.size - 1)) / shown.size
            shown.forEachIndexed { index, item ->
                val left = 72f + index * (cardWidth + gap)
                val rect = RectF(left, 1102f, left + cardWidth, 1516f)
                c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF171821.toInt(), 0.94f) })
                c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.7f; color = withAlpha(accentArgb, 0.32f) })
                val art = item.imageUrl?.let { loadBitmap(ctx, it, 260) }
                val artRect = RectF(left + (cardWidth - 154f) / 2f, 1121f, left + (cardWidth + 154f) / 2f, 1275f)
                if (art != null) drawCover(c, art, artRect, 19f) else {
                    c.drawRoundRect(artRect, 19f, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(artRect.left, artRect.top, artRect.right, artRect.bottom, accentArgb, blend(accentArgb, deep, 0.65f), Shader.TileMode.CLAMP) })
                    c.drawText(item.name.take(1).uppercase(java.util.Locale.FRANCE), artRect.centerX(), artRect.centerY() + 23f, textPaint(bold, 72f, AColor.WHITE).apply { textAlign = Paint.Align.CENTER })
                }
                c.drawText("PIÈCE  ${index + 1}", left + 17f, 1308f, textPaint(bold, 15f, withAlpha(AColor.WHITE, 0.68f)))
                drawWrapped(c, item.name, left + 17f, 1342f, cardWidth - 34f, textPaint(bold, 21f, AColor.WHITE), maxLines = 2, lineH = 25f)
                drawWrapped(c, item.detail, left + 17f, 1410f, cardWidth - 34f, textPaint(inter, 16f, withAlpha(AColor.WHITE, 0.70f)), maxLines = 3, lineH = 20f)
            }
        } else {
            c.drawText("UNE COLLECTION QUI PORTE LA SIGNATURE NOVA", 72f, 1200f, textPaint(bold, 21f, accentArgb))
        }
        c.drawText("REPÈRES DU COFFRE", 72f, 1581f, textPaint(bold, 20f, withAlpha(AColor.WHITE, 0.88f)).apply { letterSpacing = 0.08f })
        facts.take(2).forEachIndexed { index, (label, value) ->
            val left = 72f + index * 478f
            val rect = RectF(left, 1600f, left + 458f, 1704f)
            c.drawRoundRect(rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF171821.toInt(), 0.93f) })
            c.drawRoundRect(rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(accentArgb, 0.25f) })
            c.drawText(ellipsize(label.uppercase(java.util.Locale.FRANCE), textPaint(bold, 14f, withAlpha(AColor.WHITE, 0.64f)), rect.width() - 30f), left + 16f, 1628f, textPaint(bold, 14f, withAlpha(AColor.WHITE, 0.64f)))
            drawWrapped(c, value, left + 16f, 1664f, rect.width() - 30f, textPaint(bold, 23f, AColor.WHITE), maxLines = 1, lineH = 25f)
        }
        c.drawText("NOVASTATS  ·  COLLECTIONS QUI BRILLENT", W - 72f, 1856f, textPaint(bold, 23f, accentArgb).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.10f })
        c.drawRect(0f, H - 10f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, accentArgb, gold, Shader.TileMode.CLAMP) })
        writePng(ctx, bmp, "nova_certified_treasure_${categoryLabel.lowercase(java.util.Locale.ROOT)}_${entityId}_${tierLabel.lowercase(java.util.Locale.ROOT)}.png")
    }

    /** ✹ Affiche « L'Instant Absolu » : un seul pic, à sa cadence, dans la lumière. */
    suspend fun renderBiggestPeriodCard(
        ctx: Context,
        entityId: Long,
        name: String,
        subtitle: String?,
        imageUrl: String?,
        isArtist: Boolean,
        period: Period,
        categoryLabel: String,
        context: String,
        score: Int,
        rank: Int?,
        peakDate: String?,
        peakPosition: Int?,
        facts: List<Pair<String, String>>,
        timeline: List<RecordExplainer.Point>,
        accent: Color,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val accentArgb = accent.toArgb()
        val gold = NovaColors.Gold.toArgb()
        val deep = 0xFF08090D.toInt()
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(deep, blend(deep, accentArgb, 0.22f), AColor.BLACK), floatArrayOf(0f, 0.50f, 1f), Shader.TileMode.CLAMP)
        })
        halo(c, 150f, 320f, 670f, accentArgb, 0.40f)
        halo(c, 980f, 1210f, 590f, gold, 0.15f)
        val beam = Path().apply { moveTo(340f, 0f); lineTo(740f, 0f); lineTo(1040f, 920f); lineTo(40f, 920f); close() }
        c.drawPath(beam, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(360f, 0f, 720f, 920f, intArrayOf(withAlpha(accentArgb, 0f), withAlpha(accentArgb, 0.08f), withAlpha(accentArgb, 0f)), null, Shader.TileMode.CLAMP) })
        c.drawRoundRect(RectF(28f, 28f, W - 28f, H - 28f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(accentArgb, 0.48f) })
        c.drawRoundRect(RectF(44f, 44f, W - 44f, H - 44f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(AColor.WHITE, 0.14f) })

        val periodName = when (period) { Period.DAILY -> "JOUR"; Period.WEEKLY -> "SEMAINE"; Period.MONTHLY -> "MOIS"; else -> period.frLabel.uppercase(java.util.Locale.FRANCE) }
        val bucketPhrase = when (period) { Period.DAILY -> "UNE JOURNÉE"; Period.WEEKLY -> "UNE SEMAINE"; Period.MONTHLY -> "UN MOIS"; else -> "UNE PÉRIODE" }
        val parsedPeakDate = peakDate?.let { runCatching { Dates.parse(it) }.getOrNull() }
        val peakDateLabel = when {
            parsedPeakDate == null -> "Date d'archive indisponible"
            period == Period.DAILY -> "Le " + parsedPeakDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRANCE))
            period == Period.WEEKLY -> "Semaine du " + parsedPeakDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRANCE))
            period == Period.MONTHLY -> parsedPeakDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.FRANCE)).replaceFirstChar { it.uppercase(java.util.Locale.FRANCE) }
            else -> parsedPeakDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRANCE))
        }
        c.drawText("NOVA  ·  L'INSTANT ABSOLU", 72f, 112f, textPaint(bold, 27f, withAlpha(accentArgb, 0.98f)).apply { letterSpacing = 0.12f })
        val contextLine = context.ifBlank { "$periodName · $categoryLabel" }
        c.drawText(ellipsize(contextLine.uppercase(java.util.Locale.FRANCE), textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.72f)), W - 144f), 72f, 154f, textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.72f)))
        val badgeRect = RectF(72f, 184f, W - 72f, 238f)
        c.drawRoundRect(badgeRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accentArgb, 0.15f) })
        c.drawRoundRect(badgeRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.7f; color = withAlpha(accentArgb, 0.48f) })
        c.drawText("✹  PIC HISTORIQUE · $periodName  ✹", badgeRect.centerX(), 219f, textPaint(bold, 21f, accentArgb).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.10f })
        rank?.let { c.drawText(if (it == 1) "✦ N°1 DU CLASSEMENT ✦" else "MOMENT CLASSÉ N°$it", W - 72f, 276f, textPaint(bold, 18f, if (it == 1) gold else AColor.WHITE).apply { textAlign = Paint.Align.RIGHT }) }

        val coverRect = RectF(375f, 285f, 705f, 615f)
        halo(c, coverRect.centerX(), coverRect.centerY(), 285f, accentArgb, 0.29f)
        val cover = imageUrl?.let { loadBitmap(ctx, it, 560) }
        if (cover != null) {
            if (isArtist) drawCircleCover(c, cover, coverRect.centerX(), coverRect.centerY(), 157f) else drawCover(c, cover, coverRect, 38f)
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, accentArgb, theme.secondary.toArgb(), Shader.TileMode.CLAMP) }
            if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 157f, placeholder) else c.drawRoundRect(coverRect, 38f, 38f, placeholder)
            c.drawText(name.take(1).uppercase(java.util.Locale.FRANCE), coverRect.centerX(), coverRect.centerY() + 37f, textPaint(bold, 130f, withAlpha(AColor.WHITE, 0.94f)).apply { textAlign = Paint.Align.CENTER })
        }
        val imageFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = withAlpha(accentArgb, 0.90f) }
        if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 161f, imageFrame) else c.drawRoundRect(coverRect, 38f, 38f, imageFrame)
        val nameBottom = drawWrapped(c, name, 72f, 692f, W - 144f, textPaint(bold, 56f, AColor.WHITE), maxLines = 2, lineH = 62f)
        var identityBottom = nameBottom
        subtitle?.takeIf { it.isNotBlank() }?.let {
            val subtitleY = maxOf(779f, nameBottom + 26f)
            c.drawText(ellipsize(it, textPaint(inter, 24f, withAlpha(AColor.WHITE, 0.70f)), W - 144f), 72f, subtitleY, textPaint(inter, 24f, withAlpha(AColor.WHITE, 0.70f)))
            identityBottom = subtitleY
        }
        val dateY = maxOf(858f, identityBottom + 42f)
        val scoreY = maxOf(958f, dateY + 96f)
        val scoreCaptionY = maxOf(1005f, scoreY + 47f)
        c.drawText(peakDateLabel.uppercase(java.util.Locale.FRANCE), 72f, dateY, textPaint(bold, 23f, accentArgb).apply { letterSpacing = 0.10f })
        c.drawText(formatCount(score), 72f, scoreY, textPaint(bold, 88f, AColor.WHITE))
        c.drawText("ÉCOUTES SUR $bucketPhrase", 72f, scoreCaptionY, textPaint(bold, 19f, withAlpha(AColor.WHITE, 0.72f)).apply { letterSpacing = 0.06f })
        peakPosition?.let { c.drawText("POSITION AU CHART  ·  #$it", 72f, maxOf(1050f, scoreCaptionY + 44f), textPaint(bold, 20f, gold)) }

        val chartRect = RectF(72f, 1120f, W - 72f, 1500f)
        c.drawRoundRect(chartRect, 27f, 27f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF151821.toInt(), 0.94f) })
        c.drawRoundRect(chartRect, 27f, 27f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; color = withAlpha(accentArgb, 0.31f) })
        c.drawText("LA COURBE DU MOMENT  ·  $periodName", chartRect.left + 24f, chartRect.top + 35f, textPaint(bold, 19f, accentArgb).apply { letterSpacing = 0.06f })
        val nodes = timeline.mapIndexedNotNull { index, point -> point.plays?.let { Triple(index, point, it) } }
        if (nodes.isEmpty()) {
            c.drawText("Aucune trajectoire détaillée disponible", chartRect.centerX(), chartRect.centerY(), textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.72f)).apply { textAlign = Paint.Align.CENTER })
        } else {
            val plot = RectF(chartRect.left + 35f, chartRect.top + 62f, chartRect.right - 26f, chartRect.bottom - 48f)
            val maxPlays = maxOf(1, nodes.maxOf { it.third })
            fun x(index: Int) = if (timeline.size <= 1) plot.centerX() else plot.left + plot.width() * index / timeline.lastIndex.toFloat()
            fun y(plays: Int) = plot.bottom - (plays.toFloat() / maxPlays) * plot.height()
            listOf(plot.top, (plot.top + plot.bottom) / 2f, plot.bottom).forEach { yLine -> c.drawLine(plot.left, yLine, plot.right, yLine, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(AColor.WHITE, 0.12f); strokeWidth = 1.5f }) }
            nodes.zipWithNext().forEach { (from, to) -> c.drawLine(x(from.first), y(from.third), x(to.first), y(to.third), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accentArgb, 0.88f); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }) }
            nodes.forEach { (index, point, plays) ->
                val selected = point.date == peakDate || point.highlight
                val marker = if (selected) gold else accentArgb
                c.drawCircle(x(index), y(plays), if (selected) 15f else 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(marker, 0.23f) })
                c.drawCircle(x(index), y(plays), if (selected) 8f else 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = marker })
            }
            c.drawText(ellipsize(nodes.first().second.label, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.68f)), 280f), plot.left, chartRect.bottom - 18f, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.68f)))
            c.drawText(ellipsize(nodes.last().second.label, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.68f)), 280f), plot.right, chartRect.bottom - 18f, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.68f)).apply { textAlign = Paint.Align.RIGHT })
        }
        c.drawText("REPÈRES DE L'ARCHIVE", 72f, 1570f, textPaint(bold, 20f, withAlpha(AColor.WHITE, 0.87f)).apply { letterSpacing = 0.08f })
        facts.take(2).forEachIndexed { index, (label, value) ->
            val left = 72f + index * 478f
            val rect = RectF(left, 1590f, left + 458f, 1700f)
            c.drawRoundRect(rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF151821.toInt(), 0.93f) })
            c.drawRoundRect(rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(accentArgb, 0.25f) })
            c.drawText(ellipsize(label.uppercase(java.util.Locale.FRANCE), textPaint(bold, 14f, withAlpha(AColor.WHITE, 0.64f)), rect.width() - 30f), left + 16f, 1620f, textPaint(bold, 14f, withAlpha(AColor.WHITE, 0.64f)))
            drawWrapped(c, value, left + 16f, 1660f, rect.width() - 30f, textPaint(bold, 21f, AColor.WHITE), maxLines = 1, lineH = 24f)
        }
        c.drawText("NOVASTATS  ·  LE SOMMET D'UN INSTANT", W - 72f, 1856f, textPaint(bold, 22f, accentArgb).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.08f })
        c.drawRect(0f, H - 10f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, accentArgb, gold, Shader.TileMode.CLAMP) })
        writePng(ctx, bmp, "nova_peak_${period.dbName.lowercase(java.util.Locale.ROOT)}_${categoryLabel.lowercase(java.util.Locale.ROOT)}_${entityId}_${peakDate ?: "archive"}.png")
    }

    /** 🎟️ Affiche verticale « La Nuit des Premières », avec la première entrée comme preuve de l'exploit. */
    suspend fun renderDebutCard(
        ctx: Context,
        recordId: String,
        entityId: Long,
        name: String,
        subtitle: String?,
        imageUrl: String?,
        isArtist: Boolean,
        recordTitle: String,
        metric: String,
        rank: Int?,
        context: String,
        headline: String,
        narrative: String,
        facts: List<Pair<String, String>>,
        items: List<RecordExplainer.Item>,
        timeline: List<RecordExplainer.Point>,
        accent: Color,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val accentArgb = accent.toArgb()
        val gold = NovaColors.Gold.toArgb()
        val deep = 0xFF080911.toInt()

        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(deep, blend(deep, accentArgb, 0.16f), 0xFF07080D.toInt()), floatArrayOf(0f, 0.54f, 1f), Shader.TileMode.CLAMP)
        })
        halo(c, 170f, 240f, 630f, accentArgb, 0.34f)
        halo(c, 900f, 970f, 680f, gold, 0.15f)
        halo(c, 510f, 1770f, 540f, accentArgb, 0.12f)
        val stageBeam = Path().apply {
            moveTo(350f, 0f); lineTo(730f, 0f); lineTo(1020f, 960f); lineTo(70f, 960f); close()
        }
        c.drawPath(stageBeam, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(360f, 0f, 730f, 960f, intArrayOf(withAlpha(accentArgb, 0f), withAlpha(accentArgb, 0.07f), withAlpha(accentArgb, 0f)), null, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(RectF(28f, 28f, W - 28f, H - 28f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(accentArgb, 0.48f)
        })
        c.drawRoundRect(RectF(43f, 43f, W - 43f, H - 43f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(AColor.WHITE, 0.13f)
        })

        c.drawText("NOVA  ·  LA NUIT DES PREMIÈRES", 72f, 112f, textPaint(bold, 27f, withAlpha(accentArgb, 0.98f)).apply { letterSpacing = 0.12f })
        c.drawText(ellipsize(context.uppercase(java.util.Locale.FRANCE), textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.72f)), W - 144f), 72f, 154f, textPaint(inter, 21f, withAlpha(AColor.WHITE, 0.72f)))
        rank?.let {
            val badgeColor = if (it == 1) gold else accentArgb
            val rect = RectF(72f, 180f, W - 72f, 232f)
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(badgeColor, 0.15f) })
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.7f; color = withAlpha(badgeColor, 0.48f) })
            c.drawText(
                if (it == 1) "✦ DÉTENTEUR N°1 ✦" else "ARCHIVE CLASSÉE N°$it",
                rect.centerX(), 214f,
                textPaint(bold, 21f, badgeColor).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.10f }
            )
        }

        val coverRect = RectF(374f, 265f, 706f, 597f)
        halo(c, coverRect.centerX(), coverRect.centerY(), 300f, accentArgb, 0.29f)
        val cover = imageUrl?.let { loadBitmap(ctx, it, 620) }
        if (cover != null) {
            if (isArtist) drawCircleCover(c, cover, coverRect.centerX(), coverRect.centerY(), 158f)
            else drawCover(c, cover, coverRect, 36f)
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, accentArgb, theme.secondary.toArgb(), Shader.TileMode.CLAMP) }
            if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 158f, placeholder) else c.drawRoundRect(coverRect, 36f, 36f, placeholder)
            c.drawText(name.take(1).uppercase(java.util.Locale.FRANCE), coverRect.centerX(), coverRect.centerY() + 40f, textPaint(bold, 142f, withAlpha(AColor.WHITE, 0.92f)).apply { textAlign = Paint.Align.CENTER })
        }
        val coverFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = withAlpha(accentArgb, 0.88f)
        }
        if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), 161f, coverFrame)
        else c.drawRoundRect(coverRect, 36f, 36f, coverFrame)

        drawWrapped(c, name, 72f, 665f, W - 144f, textPaint(bold, 58f, AColor.WHITE), maxLines = 2, lineH = 68f)
        subtitle?.takeIf { it.isNotBlank() }?.let { c.drawText(ellipsize(it, textPaint(inter, 25f, withAlpha(AColor.WHITE, 0.72f)), W - 144f), 72f, 805f, textPaint(inter, 25f, withAlpha(AColor.WHITE, 0.72f))) }
        c.drawText(ellipsize(recordTitle.uppercase(java.util.Locale.FRANCE), textPaint(bold, 23f, accentArgb), W - 144f), 72f, 866f, textPaint(bold, 23f, accentArgb).apply { letterSpacing = 0.12f })
        drawWrapped(c, headline, 72f, 926f, W - 144f, textPaint(bold, 48f, AColor.WHITE), maxLines = 2, lineH = 57f)
        c.drawText("SCORE D'ARCHIVE  ·  ${ellipsize(metric, textPaint(bold, 28f, gold), W - 144f)}", 72f, 1063f, textPaint(bold, 28f, gold))
        drawWrapped(c, narrative, 72f, 1108f, W - 144f, textPaint(inter, 20f, withAlpha(AColor.WHITE, 0.76f)), maxLines = 2, lineH = 25f)

        if (items.isNotEmpty()) {
            c.drawText("LE REGISTRE DES PREMIÈRES ENTRÉES", 72f, 1213f, textPaint(bold, 21f, accentArgb).apply { letterSpacing = 0.09f })
            val shown = items.take(3)
            val gap = 18f
            val cardWidth = (W - 144f - gap * (shown.size - 1)) / shown.size
            shown.forEachIndexed { index, entry ->
                val left = 72f + index * (cardWidth + gap)
                val top = 1234f
                val rect = RectF(left, top, left + cardWidth, 1590f)
                c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF171A23.toInt(), 0.94f) })
                c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; color = withAlpha(accentArgb, 0.29f) })
                val art = entry.imageUrl?.let { loadBitmap(ctx, it, 260) }
                val artRect = RectF(left + (cardWidth - 142f) / 2f, top + 20f, left + (cardWidth + 142f) / 2f, top + 162f)
                if (art != null) drawCover(c, art, artRect, 19f) else {
                    c.drawRoundRect(artRect, 19f, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(artRect.left, artRect.top, artRect.right, artRect.bottom, accentArgb, blend(accentArgb, 0xFF080911.toInt(), 0.65f), Shader.TileMode.CLAMP) })
                    c.drawText(entry.name.take(1).uppercase(java.util.Locale.FRANCE), artRect.centerX(), artRect.centerY() + 24f, textPaint(bold, 76f, AColor.WHITE).apply { textAlign = Paint.Align.CENTER })
                }
                c.drawText("ARCHIVE  ${index + 1}", left + 17f, top + 189f, textPaint(bold, 15f, withAlpha(AColor.WHITE, 0.64f)))
                drawWrapped(c, entry.name, left + 17f, top + 222f, cardWidth - 34f, textPaint(bold, 21f, AColor.WHITE), maxLines = 2, lineH = 25f)
                val entryRank = entry.entryPosition?.let { "DÉBUT  ·  #$it" } ?: "PREMIÈRE ENTRÉE"
                c.drawText(ellipsize(entryRank, textPaint(bold, 16f, accentArgb), cardWidth - 34f), left + 17f, top + 286f, textPaint(bold, 16f, accentArgb))
                entry.entryDateLabel?.let { c.drawText(ellipsize(it, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.64f)), cardWidth - 34f), left + 17f, top + 313f, textPaint(inter, 15f, withAlpha(AColor.WHITE, 0.64f))) }
                entry.entryPlays?.let { c.drawText("${formatCount(it)} écoutes", left + 17f, top + 338f, textPaint(bold, 17f, gold)) }
            }
        } else if (timeline.isNotEmpty()) {
            c.drawText("LE PREMIER IMPACT  ·  TRAJECTOIRE DU CHART", 72f, 1213f, textPaint(bold, 21f, accentArgb).apply { letterSpacing = 0.07f })
            val rect = RectF(72f, 1234f, W - 72f, 1590f)
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF171A23.toInt(), 0.94f) })
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; color = withAlpha(accentArgb, 0.29f) })
            val plot = RectF(rect.left + 50f, rect.top + 36f, rect.right - 25f, rect.bottom - 46f)
            val nodes = timeline.mapIndexedNotNull { index, point -> point.position?.let { Triple(index, point, it) } }
            if (nodes.isEmpty()) {
                c.drawText("Aucune trajectoire détaillée", rect.centerX(), rect.centerY(), textPaint(inter, 21f, AColor.WHITE).apply { textAlign = Paint.Align.CENTER })
            } else {
                val maxRank = maxOf(10, nodes.maxOf { it.third })
                fun x(index: Int) = if (timeline.size < 2) plot.centerX() else plot.left + plot.width() * index / (timeline.size - 1).toFloat()
                fun y(position: Int) = plot.top + (position - 1).toFloat() / (maxRank - 1).coerceAtLeast(1) * plot.height()
                nodes.zipWithNext().forEach { (from, to) ->
                    c.drawLine(x(from.first), y(from.third), x(to.first), y(to.third), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accentArgb, 0.72f); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND })
                }
                nodes.forEach { (index, point, position) ->
                    val marker = if (point.highlight) gold else accentArgb
                    c.drawCircle(x(index), y(position), if (point.highlight) 13f else 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(marker, 0.28f) })
                    c.drawCircle(x(index), y(position), if (point.highlight) 7f else 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = marker })
                }
                val first = nodes.first().second
                val last = nodes.last().second
                c.drawText(ellipsize("${first.label}  ·  ${nodes.first().third.let { "#$it" }}", textPaint(inter, 16f, withAlpha(AColor.WHITE, 0.68f)), 400f), plot.left, rect.bottom - 16f, textPaint(inter, 16f, withAlpha(AColor.WHITE, 0.68f)))
                c.drawText(ellipsize("${last.label}  ·  ${nodes.last().third.let { "#$it" }}", textPaint(inter, 16f, withAlpha(AColor.WHITE, 0.68f)), 400f), plot.right, rect.bottom - 16f, textPaint(inter, 16f, withAlpha(AColor.WHITE, 0.68f)).apply { textAlign = Paint.Align.RIGHT })
            }
        } else {
            drawWrapped(c, recordTitle, 72f, 1320f, W - 144f, textPaint(bold, 30f, accentArgb), maxLines = 2, lineH = 36f)
        }

        c.drawText("REPÈRES DE L'ARCHIVE", 72f, 1630f, textPaint(bold, 20f, withAlpha(AColor.WHITE, 0.84f)).apply { letterSpacing = 0.08f })
        facts.take(4).forEachIndexed { index, (label, value) ->
            val column = index % 2
            val row = index / 2
            val left = 72f + column * 478f
            val top = 1650f + row * 99f
            val rect = RectF(left, top, left + 458f, top + 86f)
            c.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(0xFF171A23.toInt(), 0.92f) })
            c.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(accentArgb, 0.22f) })
            c.drawText(ellipsize(label.uppercase(java.util.Locale.FRANCE), textPaint(bold, 13f, withAlpha(AColor.WHITE, 0.62f)), rect.width() - 30f), left + 15f, top + 24f, textPaint(bold, 13f, withAlpha(AColor.WHITE, 0.62f)))
            drawWrapped(c, value, left + 15f, top + 51f, rect.width() - 30f, textPaint(bold, 18f, AColor.WHITE), maxLines = 2, lineH = 20f)
        }
        c.drawText("NOVASTATS  ·  PREMIÈRES QUI RESTENT", W - 72f, 1865f, textPaint(bold, 23f, accentArgb).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.10f })
        c.drawRect(0f, H - 10f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, accentArgb, gold, Shader.TileMode.CLAMP) })
        writePng(ctx, bmp, "nova_debut_${recordId.lowercase(java.util.Locale.ROOT)}_${entityId}.png")
    }

    private data class MovementCardNode(val sourceIndex: Int, val point: RecordExplainer.Point, val position: Int, val periodIndex: Int)

    /** 🎼 Carte verticale partageable dédiée aux records de titres distincts (Chart → Top 10 → Top 5 → #1). */
    suspend fun renderSongReachCard(
        ctx: Context,
        entityId: Long,
        name: String,
        subtitle: String?,
        imageUrl: String?,
        isArtist: Boolean,
        visual: RecordExplainer.SongReachVisual,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val gold = NovaColors.Gold.toArgb()
        val colors = listOf(theme.primary.toArgb(), 0xFF5DADE2.toInt(), 0xFFBA68C8.toInt(), gold)
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(theme.background.toArgb(), blend(theme.background.toArgb(), colors[3], 0.20f), AColor.BLACK),
                floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 180f, 320f, 560f, if (visual.rank == 1) gold else theme.primary.toArgb(), 0.36f)
        halo(c, 990f, 1240f, 500f, theme.secondary.toArgb(), 0.22f)
        val frameColor = if (visual.rank == 1) gold else theme.primary.toArgb()
        c.drawRoundRect(RectF(28f, 28f, W - 28f, H - 28f), 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(frameColor, 0.42f)
        })
        c.drawRoundRect(RectF(42f, 42f, W - 42f, H - 42f), 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(theme.text.toArgb(), 0.12f)
        })

        val eyebrow = textPaint(bold, 30f, withAlpha(theme.accent.toArgb(), 0.96f)).apply { letterSpacing = 0.2f }
        c.drawText("NOVA RECORDS · ÉDITION PRESTIGE", 72f, 122f, eyebrow)
        c.drawText(ellipsize(visual.contextLabel.uppercase(java.util.Locale.FRANCE), textPaint(inter, 24f, theme.textSecondary.toArgb()), W - 144f), 72f, 172f, textPaint(inter, 24f, theme.textSecondary.toArgb()))
        val badgeRect = RectF(72f, 197f, W - 72f, 245f)
        c.drawRoundRect(badgeRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(frameColor, if (visual.rank == 1) 0.24f else 0.16f) })
        c.drawRoundRect(badgeRect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(frameColor, 0.52f) })
        val badge = if (visual.rank == 1 && visual.tiedAtTop > 1) "✦ COURONNE PARTAGÉE · ${visual.tiedAtTop} CO-DÉTENTEURS ✦" else if (visual.rank == 1) "✦ LÉGENDE DU CHART · RECORD DÉTENU ✦" else "✧ TOP ${visual.rank} · À LA CONQUÊTE DU SOMMET ✧"
        c.drawText(badge, badgeRect.centerX(), 229f, textPaint(bold, 22f, if (visual.rank == 1) gold else theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f })
        var titleBottom = drawWrapped(c, name, 72f, 270f, W - 144f, textPaint(bold, 68f, theme.text.toArgb()), maxLines = 2, lineH = 78f)
        subtitle?.takeIf { it.isNotBlank() }?.let {
            titleBottom = drawWrapped(c, it, 72f, titleBottom + 4f, W - 144f, textPaint(inter, 30f, theme.textSecondary.toArgb()), maxLines = 1, lineH = 38f)
        }

        val coverTop = maxOf(350f, titleBottom + 24f)
        val coverRect = RectF(72f, coverTop, 402f, coverTop + 330f)
        val cover = imageUrl?.let { loadBitmap(ctx, it, 520) }
        if (cover != null) {
            if (isArtist) {
                c.drawCircle(coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.primary.toArgb(), 0.18f) })
                drawCircleCover(c, cover, coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.47f)
            } else drawCover(c, cover, coverRect, 34f)
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, theme.primary.toArgb(), theme.secondary.toArgb(), Shader.TileMode.CLAMP)
            }
            if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.47f, placeholder)
            else c.drawRoundRect(coverRect, 34f, 34f, placeholder)
            val initial = textPaint(bold, 150f, withAlpha(theme.text.toArgb(), 0.9f)).apply { textAlign = Paint.Align.CENTER }
            c.drawText(name.take(1).uppercase(), coverRect.centerX(), coverRect.centerY() + 48f, initial)
        }
        val coverBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = withAlpha(frameColor, 0.82f)
        }
        if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.47f, coverBorder)
        else c.drawRoundRect(coverRect, 34f, 34f, coverBorder)

        val rightX = 444f
        val rightW = W - rightX - 72f
        c.drawText("RECORD", rightX, coverTop + 38f, textPaint(bold, 26f, withAlpha(theme.accent.toArgb(), 0.95f)).apply { letterSpacing = 0.18f })
        val recordBottom = drawWrapped(c, visual.recordTitle, rightX, coverTop + 88f, rightW, textPaint(bold, 38f, theme.text.toArgb()), maxLines = 2, lineH = 44f)
        c.drawText("SCORE · ${visual.itemLabel.uppercase(java.util.Locale.FRANCE)}S DISTINCTS", rightX, recordBottom + 32f, textPaint(bold, 20f, theme.textSecondary.toArgb()))
        val scoreCx = rightX + 86f
        val scoreCy = recordBottom + 78f
        c.drawCircle(scoreCx, scoreCy, 92f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(scoreCx, scoreCy, 92f, intArrayOf(withAlpha(frameColor, 0.30f), withAlpha(frameColor, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawText(visual.score.toString(), rightX, recordBottom + 102f, textPaint(bold, 82f, if (visual.rank == 1) gold else theme.primary.toArgb()))
        c.drawText(
            if (visual.rank == 1 && visual.tiedAtTop > 1) "🥇 #1 · ${visual.tiedAtTop} ex æquo" else if (visual.rank == 1) "🥇 DÉTENTEUR #1" else "#${visual.rank} DU RECORD",
            rightX, recordBottom + 148f, textPaint(bold, 21f, if (visual.rank == 1) gold else theme.textSecondary.toArgb())
        )

        /* Les quatre paliers en regard : chaque barre reste un compte d'éléments distincts. */
        val metricTop = coverRect.bottom + 48f
        c.drawText("LA MONTÉE EN PUISSANCE", 72f, metricTop, textPaint(bold, 27f, theme.textSecondary.toArgb()).apply { letterSpacing = 0.12f })
        val chartBase = visual.chartCount.coerceAtLeast(1)
        val ratios = listOf("base", visual.top10ConversionPercent?.let { "$it% du chart" } ?: "—", visual.top5ConversionPercent?.let { "$it% du Top 10" } ?: "—", visual.numberOneConversionPercent?.let { "$it% du Top 5" } ?: "—")
        val counts = listOf(visual.chartCount, visual.top10Count, visual.top5Count, visual.numberOneCount)
        val labels = listOf("CHART", "TOP 10", "TOP 5", "#1")
        val cardGap = 16f
        val cardW = (W - 144f - cardGap * 3f) / 4f
        val cardTop = metricTop + 28f
        val cardBottom = cardTop + 166f
        for (i in labels.indices) {
            val left = 72f + i * (cardW + cardGap)
            val rect = RectF(left, cardTop, left + cardW, cardBottom)
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.80f) })
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(colors[i], 0.55f) })
            c.drawText(labels[i], rect.left + 18f, rect.top + 38f, textPaint(bold, 23f, colors[i]))
            c.drawText(counts[i].toString(), rect.left + 18f, rect.top + 101f, textPaint(bold, 48f, theme.text.toArgb()))
            val bar = RectF(rect.left + 18f, rect.top + 119f, rect.right - 18f, rect.top + 127f)
            c.drawRoundRect(bar, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.textSecondary.toArgb(), 0.16f) })
            val fraction = (counts[i].toFloat() / chartBase).coerceIn(0f, 1f)
            if (fraction > 0f) c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * fraction, bar.bottom), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[i] })
            c.drawText(ellipsize(ratios[i], textPaint(inter, 18f, theme.textSecondary.toArgb()), cardW - 28f), rect.left + 14f, rect.bottom - 17f, textPaint(inter, 18f, theme.textSecondary.toArgb()))
        }

        var nextY = cardBottom + 30f
        visual.rival?.let { rival ->
            c.drawText("⚔ FACE-À-FACE · TOP 2", 72f, nextY, textPaint(bold, 25f, gold))
            val left = if (visual.rank < rival.rank) name else rival.name
            val right = if (visual.rank < rival.rank) rival.name else name
            val leftScore = if (visual.rank < rival.rank) visual.score else rival.score
            val rightScore = if (visual.rank < rival.rank) rival.score else visual.score
            val summary = if (rival.tied) "égalité parfaite" else "$leftScore — $rightScore"
            c.drawText(ellipsize("$left  vs  $right", textPaint(bold, 27f, theme.text.toArgb()), W - 144f), 72f, nextY + 45f, textPaint(bold, 27f, theme.text.toArgb()))
            c.drawText(summary, 72f, nextY + 82f, textPaint(inter, 23f, theme.textSecondary.toArgb()))
            c.drawText(
                "Chart ${visual.chartCount}/${rival.chartCount} · Top 10 ${visual.top10Count}/${rival.top10Count} · Top 5 ${visual.top5Count}/${rival.top5Count} · #1 ${visual.numberOneCount}/${rival.numberOneCount}",
                72f, nextY + 118f, textPaint(inter, 20f, theme.textSecondary.toArgb())
            )
            nextY += 150f
        }

        val signatureHeading = if (visual.itemLabel == "album") "ALBUMS SIGNATURES" else "TITRES SIGNATURES"
        c.drawText("$signatureHeading · PREMIÈRE ATTEINTE DE CHAQUE PALIER", 72f, nextY + 24f, textPaint(bold, 24f, theme.textSecondary.toArgb()))
        var rowY = nextY + 54f
        visual.featuredTracks.take(3).forEachIndexed { index, track ->
            val rect = RectF(72f, rowY, W - 72f, rowY + 164f)
            c.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.82f) })
            c.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(frameColor, 0.28f) })
            val trackCoverRect = RectF(rect.left + 16f, rect.top + 22f, rect.left + 124f, rect.top + 130f)
            val trackCover = track.imageUrl?.let { loadBitmap(ctx, it, 220) }
            if (trackCover != null) drawCover(c, trackCover, trackCoverRect, 16f) else {
                c.drawRoundRect(trackCoverRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(trackCoverRect.left, trackCoverRect.top, trackCoverRect.right, trackCoverRect.bottom, colors[index % colors.size], theme.secondary.toArgb(), Shader.TileMode.CLAMP)
                })
            }
            val tx = rect.left + 148f
            val maxW = rect.right - tx - 18f
            c.drawText("${index + 1}. " + ellipsize(track.name, textPaint(bold, 27f, theme.text.toArgb()), maxW - 55f), tx, rect.top + 40f, textPaint(bold, 27f, theme.text.toArgb()))
            c.drawText("Meilleur rang #${track.peakPosition} · ${track.peakDate} · ${track.chartPeriods} apparitions", tx, rect.top + 76f, textPaint(inter, 21f, theme.textSecondary.toArgb()))
            val path = "Chart ${track.firstChartDate} → Top 10 ${track.firstTop10Date ?: "—"} → Top 5 ${track.firstTop5Date ?: "—"} → #1 ${track.firstNumberOneDate ?: "—"}"
            drawWrapped(c, path, tx, rect.top + 118f, maxW, textPaint(inter, 18f, theme.accent.toArgb()), maxLines = 2, lineH = 22f)
            rowY += 178f
        }
        if (visual.featuredTracks.isEmpty()) {
            c.drawText("Aucun titre signature disponible.", 72f, rowY + 35f, textPaint(inter, 24f, theme.textSecondary.toArgb()))
        }
        c.drawText("NOVASTATS", W - 72f, H - 74f, textPaint(bold, 30f, withAlpha(theme.primary.toArgb(), 0.96f)).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.28f })
        c.drawRect(0f, H - 9f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), 0f, theme.primary.toArgb(), theme.secondary.toArgb(), Shader.TileMode.CLAMP)
        })
        writePng(ctx, bmp, "nova_records_songs_${visual.recordTitle.hashCode()}_$entityId.png")
    }

    /** 📊 Carte de prestige pour le cumul d'artiste : trois compteurs disjoints, duel et périodes récentes. */
    suspend fun renderArtistCumulativeNoOneCard(
        ctx: Context,
        entityId: Long,
        name: String,
        subtitle: String?,
        imageUrl: String?,
        visual: RecordExplainer.ArtistCumulativeNoOneVisual,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val violet = 0xFFB39AFF.toInt()
        val cyan = 0xFF62C8FF.toInt()
        val gold = NovaColors.Gold.toArgb()
        val chartColors = mapOf("HOT_100" to cyan, "ALBUMS_75" to 0xFFFFC76A.toInt())
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(theme.background.toArgb(), blend(theme.background.toArgb(), violet, 0.30f), AColor.BLACK), floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 180f, 420f, 520f, violet, 0.36f)
        halo(c, 900f, 1180f, 520f, cyan, 0.20f)
        halo(c, 540f, 1570f, 440f, gold, 0.16f)
        c.drawRoundRect(RectF(30f, 30f, W - 30f, H - 30f), 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(violet, 0.64f)
        })
        c.drawText("NOVA RECORDS · ÉDITION PRESTIGE", 72f, 120f, textPaint(bold, 27f, withAlpha(theme.accent.toArgb(), 0.98f)).apply { letterSpacing = 0.18f })
        c.drawText("MOST CUMULATIVE #1", W / 2f, 243f, textPaint(bold, 68f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = -0.01f })
        c.drawText("${visual.selectedChartLabel.uppercase()}  ·  ${visual.period.label.uppercase()}", W / 2f, 304f, textPaint(bold, 27f, withAlpha(cyan, 0.98f)).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f })
        val avatar = RectF(445f, 354f, 635f, 544f)
        val artistBitmap = imageUrl?.let { loadBitmap(ctx, it, 320) }
        if (artistBitmap != null) drawCircleCover(c, artistBitmap, avatar.centerX(), avatar.centerY(), 90f)
        else {
            c.drawCircle(avatar.centerX(), avatar.centerY(), 90f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(avatar.left, avatar.top, avatar.right, avatar.bottom, violet, cyan, Shader.TileMode.CLAMP) })
            c.drawText(name.take(1).uppercase(), avatar.centerX(), avatar.centerY() + 31f, textPaint(bold, 80f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }
        c.drawCircle(avatar.centerX(), avatar.centerY(), 96f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = withAlpha(gold, 0.90f) })
        drawWrapped(c, name, 96f, 594f, W - 192f, textPaint(bold, 42f, theme.text.toArgb()), maxLines = 2, lineH = 48f)
        subtitle?.takeIf { it.isNotBlank() }?.let { c.drawText(ellipsize(it, textPaint(inter, 20f, theme.textSecondary.toArgb()), W - 180f), W / 2f, 680f, textPaint(inter, 20f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER }) }

        val rankRect = RectF(305f, 704f, 775f, 767f)
        c.drawRoundRect(rankRect, 31f, 31f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(gold, 0.19f) })
        c.drawRoundRect(rankRect, 31f, 31f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(gold, 0.72f) })
        val tied = if (visual.tiedAtTop > 1) " · ${visual.tiedAtTop} EX ÆQUO" else ""
        c.drawText("RANG #${visual.rank} / ${visual.boardSize}$tied", rankRect.centerX(), 745f, textPaint(bold, 24f, gold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.07f })

        c.drawText("DEUX CHARTS · AUCUN TOTAL MÉLANGÉ", W / 2f, 824f, textPaint(bold, 23f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.06f })
        val cardTop = 860f
        val cardBottom = 1242f
        val gap = 22f
        val cardWidth = (W - 144f - gap) / 2f
        visual.pillars.forEachIndexed { index, pillar ->
            val accent = chartColors[pillar.key] ?: violet
            val left = 72f + index * (cardWidth + gap)
            val rect = RectF(left, cardTop, left + cardWidth, cardBottom)
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.86f) })
            c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = withAlpha(accent, 0.72f) })
            c.drawText(pillar.label.uppercase(), rect.centerX(), rect.top + 40f, textPaint(bold, 21f, accent).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.045f })
            c.drawText(pillar.count.toString(), rect.centerX(), rect.top + 115f, textPaint(bold, 62f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
            val unit = when (visual.period) {
                com.novastats.app.domain.Period.DAILY -> "JOURS"
                com.novastats.app.domain.Period.WEEKLY -> "SEMAINES"
                com.novastats.app.domain.Period.MONTHLY -> "MOIS"
                com.novastats.app.domain.Period.YEARLY -> "ANNÉES"
                com.novastats.app.domain.Period.GLOBAL -> "PÉRIODES"
            }
            c.drawText(unit, rect.centerX(), rect.top + 150f, textPaint(bold, 17f, withAlpha(accent, 0.95f)).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.10f })
            val imageRect = RectF(rect.centerX() - 58f, rect.top + 174f, rect.centerX() + 58f, rect.top + 290f)
            val cover = pillar.latestImageUrl?.let { loadBitmap(ctx, it, 260) }
            if (cover != null) {
                drawCover(c, cover, imageRect, 14f)
            } else {
                c.drawRoundRect(imageRect, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom, accent, violet, Shader.TileMode.CLAMP) })
                c.drawText(pillar.label.take(1), imageRect.centerX(), imageRect.centerY() + 17f, textPaint(bold, 45f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
            }
            drawWrapped(c, pillar.latestNumberOne ?: "Aucun n°1 récent", rect.left + 14f, rect.top + 326f, rect.width() - 28f, textPaint(bold, 17f, theme.text.toArgb()), maxLines = 2, lineH = 21f)
            c.drawText(pillar.latestDate ?: "—", rect.centerX(), rect.bottom - 17f, textPaint(inter, 15f, withAlpha(accent, 0.95f)).apply { textAlign = Paint.Align.CENTER })
        }
        val currentCounts = visual.pillars.joinToString("   ·   ") { "${it.label}: ${it.count}" }
        c.drawText(currentCounts, W / 2f, 1288f, textPaint(inter, 18f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })

        visual.rival?.let { rival ->
            val rivalRect = RectF(72f, 1320f, W - 72f, 1490f)
            c.drawRoundRect(rivalRect, 23f, 23f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.83f) })
            c.drawRoundRect(rivalRect, 23f, 23f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(gold, 0.46f) })
            c.drawText("RIVAL · ${if (rival.tied) "À ÉGALITÉ" else "PROCHAIN AU CLASSEMENT"}", rivalRect.centerX(), rivalRect.top + 37f, textPaint(bold, 18f, gold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.07f })
            c.drawText("#${visual.rank}  $name", 110f, rivalRect.top + 83f, textPaint(bold, 24f, theme.text.toArgb()))
            c.drawText("${visual.score}", 110f, rivalRect.top + 124f, textPaint(bold, 29f, cyan))
            c.drawText("VS", rivalRect.centerX(), rivalRect.top + 90f, textPaint(bold, 22f, gold).apply { textAlign = Paint.Align.CENTER })
            c.drawText("#${rival.rank}  ${ellipsize(rival.name, textPaint(bold, 24f, theme.text.toArgb()), 390f)}", rivalRect.right - 110f, rivalRect.top + 83f, textPaint(bold, 24f, theme.text.toArgb()).apply { textAlign = Paint.Align.RIGHT })
            c.drawText("${rival.score}", rivalRect.right - 110f, rivalRect.top + 124f, textPaint(bold, 29f, violet).apply { textAlign = Paint.Align.RIGHT })
            val pillarDuel = visual.pillars.joinToString("   ·   ") { pillar ->
                "${pillar.label}: ${pillar.count}/${rival.pillarCounts[pillar.key] ?: 0}"
            }
            c.drawText(ellipsize(pillarDuel, textPaint(inter, 16f, theme.textSecondary.toArgb()), rivalRect.width() - 50f), rivalRect.centerX(), rivalRect.bottom - 13f, textPaint(inter, 16f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }

        c.drawText("PÉRIODES RÉCENTES AU N°1", W / 2f, 1520f, textPaint(bold, 24f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f })
        val recent = visual.heatmap.takeLast(28)
        val cell = 47f
        val cellGap = 10f
        val gridWidth = 7f * cell + 6f * cellGap
        val startX = (W - gridWidth) / 2f
        val startY = 1563f
        recent.forEachIndexed { index, period ->
            val col = index % 7
            val row = index / 7
            val left = startX + col * (cell + cellGap)
            val top = startY + row * (cell + cellGap)
            val rect = RectF(left, top, left + cell, top + cell)
            val colors = period.activeCharts.mapNotNull { chartColors[it] }
            val heatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                when {
                    colors.isEmpty() -> color = withAlpha(theme.surface.toArgb(), 0.52f)
                    colors.size == 1 -> color = colors.first()
                    else -> shader = LinearGradient(left, top, left + cell, top + cell, colors.toIntArray(), null, Shader.TileMode.CLAMP)
                }
            }
            c.drawRoundRect(rect, 10f, 10f, heatPaint)
            c.drawRoundRect(rect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(if (visual.selectedChart in period.activeCharts) gold else theme.text.toArgb(), if (visual.selectedChart in period.activeCharts) 0.88f else 0.18f) })
            c.drawText(period.date.takeLast(2), rect.centerX(), rect.centerY() + 6f, textPaint(bold, 16f, if (colors.isEmpty()) theme.textSecondary.toArgb() else AColor.WHITE).apply { textAlign = Paint.Align.CENTER })
        }
        if (recent.isNotEmpty()) {
            val rowCount = (recent.size + 6) / 7
            c.drawText("${recent.first().date}  →  ${recent.last().date}", W / 2f, startY + rowCount * (cell + cellGap) + 20f, textPaint(inter, 17f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }
        var legendX = 106f
        visual.pillars.forEach { pillar ->
            val accent = chartColors[pillar.key] ?: violet
            c.drawCircle(legendX, 1845f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            val labelPaint = textPaint(inter, 15f, theme.textSecondary.toArgb())
            c.drawText(pillar.label, legendX + 15f, 1851f, labelPaint)
            legendX += 15f + labelPaint.measureText(pillar.label) + 32f
        }
        c.drawText("NOVASTATS", W - 72f, H - 27f, textPaint(bold, 20f, withAlpha(violet, 0.98f)).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.16f })
        c.drawRect(0f, H - 8f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, violet, cyan, Shader.TileMode.CLAMP) })
        writePng(ctx, bmp, "nova_artist_cumulative_no1_${entityId}_${visual.period.dbName}_${visual.selectedChart}.png")
    }

    /** 💎 Affiche verticale dédiée aux trois #1 Perfect All Kill d'une même cadence. */
    suspend fun renderPerfectAllKillCard(
        ctx: Context,
        visual: RecordExplainer.PerfectAllKillVisual,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val violet = 0xFFA98BFF.toInt()
        val cyan = 0xFF69D8FF.toInt()
        val gold = NovaColors.Gold.toArgb()
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(theme.background.toArgb(), blend(theme.background.toArgb(), violet, 0.28f), AColor.BLACK),
                floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 540f, 470f, 560f, violet, 0.42f)
        halo(c, 145f, 1120f, 500f, cyan, 0.22f)
        halo(c, 945f, 1120f, 520f, gold, 0.17f)
        val frame = RectF(30f, 30f, W - 30f, H - 30f)
        c.drawRoundRect(frame, 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(violet, 0.62f)
        })
        c.drawRoundRect(RectF(45f, 45f, W - 45f, H - 45f), 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(theme.text.toArgb(), 0.14f)
        })

        c.drawText("NOVA HALL OF FAME · ÉDITION PRESTIGE", 72f, 122f, textPaint(bold, 27f, withAlpha(theme.accent.toArgb(), 0.98f)).apply { letterSpacing = 0.18f })
        c.drawText("PERFECT ALL KILL", W / 2f, 250f, textPaint(bold, 83f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = -0.025f })
        val cadenceRect = RectF(210f, 292f, W - 210f, 350f)
        c.drawRoundRect(cadenceRect, 29f, 29f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(violet, 0.22f) })
        c.drawRoundRect(cadenceRect, 29f, 29f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(violet, 0.72f) })
        c.drawText("${visual.cadenceLabel.uppercase()}  ·  ${visual.dateLabel.uppercase()}", cadenceRect.centerX(), 330f, textPaint(bold, 23f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.09f })

        val artistRect = RectF(388f, 390f, 692f, 694f)
        val artist = visual.artistImageUrl?.let { loadBitmap(ctx, it, 500) }
        if (artist != null) drawCircleCover(c, artist, artistRect.centerX(), artistRect.centerY(), 145f)
        else {
            c.drawCircle(artistRect.centerX(), artistRect.centerY(), 145f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(artistRect.left, artistRect.top, artistRect.right, artistRect.bottom, violet, cyan, Shader.TileMode.CLAMP)
            })
            c.drawText(visual.artistName.take(1).uppercase(), artistRect.centerX(), artistRect.centerY() + 45f, textPaint(bold, 120f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }
        c.drawCircle(artistRect.centerX(), artistRect.centerY(), 151f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7f; color = withAlpha(gold, 0.90f)
        })
        c.drawCircle(artistRect.centerX(), artistRect.centerY(), 163f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(violet, 0.70f)
        })
        drawWrapped(c, visual.artistName, 100f, 735f, W - 200f, textPaint(bold, 52f, theme.text.toArgb()), maxLines = 2, lineH = 59f)
        c.drawText("UN ARTISTE · TROIS CHARTS · UNE MÊME PÉRIODE", W / 2f, 865f, textPaint(bold, 22f, withAlpha(cyan, 0.96f)).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f })

        val cardTop = 930f
        val cardBottom = 1475f
        val gap = 21f
        val cardWidth = (W - 144f - gap * 2f) / 3f
        val pillars = listOf(
            Triple("HOT 100", visual.trackName, visual.trackImageUrl) to 0xFF65C7FF.toInt(),
            Triple("ARTIST 50", visual.artistName, visual.artistImageUrl) to violet,
            Triple("ALBUMS 75", visual.albumName, visual.albumImageUrl) to 0xFFFFC86B.toInt()
        )
        pillars.forEachIndexed { index, (pillar, color) ->
            val left = 72f + index * (cardWidth + gap)
            val rect = RectF(left, cardTop, left + cardWidth, cardBottom)
            c.drawRoundRect(rect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(theme.surface.toArgb(), 0.82f) })
            c.drawRoundRect(rect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; this.color = withAlpha(color, 0.74f) })
            c.drawText(pillar.first, rect.centerX(), rect.top + 54f, textPaint(bold, 23f, color).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f })
            val imageRect = RectF(rect.left + 35f, rect.top + 82f, rect.right - 35f, rect.top + 322f)
            val cover = pillar.third?.let { loadBitmap(ctx, it, 420) }
            if (cover != null) {
                if (index == 1) drawCircleCover(c, cover, imageRect.centerX(), imageRect.centerY(), imageRect.width() * 0.5f)
                else drawCover(c, cover, imageRect, 22f)
            } else {
                val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(imageRect.left, imageRect.top, imageRect.right, imageRect.bottom, color, violet, Shader.TileMode.CLAMP) }
                if (index == 1) c.drawCircle(imageRect.centerX(), imageRect.centerY(), imageRect.width() * 0.5f, placeholder)
                else c.drawRoundRect(imageRect, 22f, 22f, placeholder)
                c.drawText(pillar.second.take(1).uppercase(), imageRect.centerX(), imageRect.centerY() + 32f, textPaint(bold, 92f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
            }
            val coverBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; this.color = withAlpha(color, 0.70f) }
            if (index == 1) c.drawCircle(imageRect.centerX(), imageRect.centerY(), imageRect.width() * 0.5f, coverBorder)
            else c.drawRoundRect(imageRect, 22f, 22f, coverBorder)
            drawWrapped(c, pillar.second, rect.left + 18f, rect.top + 354f, rect.width() - 36f, textPaint(bold, 24f, theme.text.toArgb()), maxLines = 3, lineH = 31f)
            c.drawText("#1", rect.centerX(), rect.bottom - 30f, textPaint(bold, 31f, color).apply { textAlign = Paint.Align.CENTER })
        }
        listOf(72f + cardWidth + gap / 2f, 72f + 2f * (cardWidth + gap) - gap / 2f).forEach { x ->
            c.drawCircle(x, (cardTop + cardBottom) / 2f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(violet, 0.94f) })
            c.drawText("→", x, (cardTop + cardBottom) / 2f + 10f, textPaint(bold, 25f, AColor.WHITE).apply { textAlign = Paint.Align.CENTER })
        }
        c.drawText("N°1 TITRE CRÉDITÉ  →  N°1 ARTISTE  →  ALBUM DU MÊME ARTISTE", W / 2f, 1555f, textPaint(bold, 21f, withAlpha(gold, 0.96f)).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.035f })
        if (visual.margins.isNotEmpty()) {
            c.drawText("DAUPHIN & ÉCART · PAR CHART", W / 2f, 1605f, textPaint(bold, 19f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.07f })
            val marginGap = 16f
            val marginWidth = (W - 144f - 2f * marginGap) / 3f
            visual.margins.forEachIndexed { index, margin ->
                val left = 72f + index * (marginWidth + marginGap)
                val rect = RectF(left, 1625f, left + marginWidth, 1774f)
                val accent = when (margin.chartLabel) {
                    "Hot 100" -> cyan
                    "Artist 50" -> violet
                    else -> 0xFFFFC86B.toInt()
                }
                c.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.84f) })
                c.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(accent, 0.68f) })
                c.drawText(margin.chartLabel.uppercase(), rect.centerX(), rect.top + 32f, textPaint(bold, 16f, accent).apply { textAlign = Paint.Align.CENTER })
                c.drawText("#1 · ${formatCount(margin.leaderPlays)} écoutes", rect.centerX(), rect.top + 62f, textPaint(inter, 15f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
                drawWrapped(c, "#2 ${margin.runnerUpName ?: "—"}", rect.left + 10f, rect.top + 93f, rect.width() - 20f, textPaint(inter, 14f, theme.textSecondary.toArgb()), maxLines = 2, lineH = 18f)
                val gapText = margin.gap?.let { "+${formatCount(it)} écoutes d'avance" } ?: "Pas de dauphin"
                c.drawText(ellipsize(gapText, textPaint(bold, 13f, accent), rect.width() - 20f), rect.centerX(), rect.bottom - 13f, textPaint(bold, 13f, accent).apply { textAlign = Paint.Align.CENTER })
            }
        }
        c.drawText("PERFECT ALL KILL · DISTINCTION INTER-CHARTS", W / 2f, 1815f, textPaint(inter, 20f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f })
        c.drawText("NOVASTATS", W - 72f, H - 75f, textPaint(bold, 30f, withAlpha(violet, 0.98f)).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.25f })
        c.drawRect(0f, H - 9f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, violet, cyan, Shader.TileMode.CLAMP) })
        writePng(ctx, bmp, "nova_perfect_all_kill_${visual.periodType.lowercase()}_${visual.artistId}_${visual.dateIso}.png")
    }

    /** 💎 Carte de record Most Perfect All Kill pour une cadence donnée. */
    suspend fun renderPerfectAllKillRecordCard(
        ctx: Context,
        entityId: Long,
        name: String,
        imageUrl: String?,
        visual: RecordExplainer.PerfectAllKillRecordVisual,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val violet = 0xFFA98BFF.toInt()
        val cyan = 0xFF69D8FF.toInt()
        val gold = NovaColors.Gold.toArgb()
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(theme.background.toArgb(), blend(theme.background.toArgb(), violet, 0.30f), AColor.BLACK), floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 170f, 390f, 540f, violet, 0.38f)
        halo(c, 940f, 1040f, 490f, cyan, 0.20f)
        halo(c, 500f, 1560f, 520f, gold, 0.17f)
        c.drawRoundRect(RectF(30f, 30f, W - 30f, H - 30f), 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(violet, 0.66f)
        })
        c.drawText("NOVA RECORDS · CÉRÉMONIE PRESTIGE", 72f, 115f, textPaint(bold, 25f, withAlpha(theme.accent.toArgb(), 0.98f)).apply { letterSpacing = 0.15f })
        c.drawText("MOST PERFECT ALL KILL", W / 2f, 230f, textPaint(bold, 57f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = -0.01f })
        c.drawText("${visual.period.label.uppercase()} · TRINITÉ PARFAITE", W / 2f, 285f, textPaint(bold, 23f, withAlpha(cyan, 0.98f)).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f })

        val avatar = RectF(445f, 315f, 635f, 505f)
        val artistBitmap = imageUrl?.let { loadBitmap(ctx, it, 320) }
        if (artistBitmap != null) drawCircleCover(c, artistBitmap, avatar.centerX(), avatar.centerY(), 90f)
        else {
            c.drawCircle(avatar.centerX(), avatar.centerY(), 90f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(avatar.left, avatar.top, avatar.right, avatar.bottom, violet, cyan, Shader.TileMode.CLAMP) })
            c.drawText(name.take(1).uppercase(), avatar.centerX(), avatar.centerY() + 30f, textPaint(bold, 78f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }
        c.drawCircle(avatar.centerX(), avatar.centerY(), 96f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = withAlpha(gold, 0.90f) })
        drawWrapped(c, name, 100f, 568f, W - 200f, textPaint(bold, 42f, theme.text.toArgb()), maxLines = 2, lineH = 48f)

        val summary = RectF(72f, 635f, W - 72f, 750f)
        c.drawRoundRect(summary, 23f, 23f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(violet, 0.20f) })
        c.drawRoundRect(summary, 23f, 23f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(gold, 0.65f) })
        c.drawText("${visual.score} PERFECT ALL KILL", summary.centerX(), summary.top + 48f, textPaint(bold, 31f, gold).apply { textAlign = Paint.Align.CENTER })
        val tie = if (visual.tiedAtTop > 1) " · ${visual.tiedAtTop} EX ÆQUO" else ""
        c.drawText("RANG #${visual.rank}$tie · ${visual.period.label.uppercase()}", summary.centerX(), summary.bottom - 22f, textPaint(bold, 19f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.06f })

        val rival = visual.rival
        val rivalRect = RectF(72f, 775f, W - 72f, 930f)
        c.drawRoundRect(rivalRect, 21f, 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.82f) })
        c.drawRoundRect(rivalRect, 21f, 21f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(gold, 0.46f) })
        c.drawText("RIVAL · ${if (rival?.tied == true) "À ÉGALITÉ" else "SUR CETTE CADENCE"}", rivalRect.centerX(), rivalRect.top + 32f, textPaint(bold, 17f, gold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f })
        if (rival != null) {
            c.drawText("#${visual.rank}  ${ellipsize(name, textPaint(bold, 22f, theme.text.toArgb()), 340f)} · ${visual.score}", rivalRect.left + 22f, rivalRect.top + 78f, textPaint(bold, 22f, theme.text.toArgb()))
            c.drawText("VS", rivalRect.centerX(), rivalRect.top + 120f, textPaint(bold, 20f, gold).apply { textAlign = Paint.Align.CENTER })
            c.drawText("#${rival.rank}  ${ellipsize(rival.name, textPaint(bold, 22f, theme.text.toArgb()), 340f)} · ${rival.score}", rivalRect.right - 22f, rivalRect.top + 78f, textPaint(bold, 22f, theme.text.toArgb()).apply { textAlign = Paint.Align.RIGHT })
            val gap = kotlin.math.abs(visual.score - rival.score)
            c.drawText("ÉCART · $gap événement${if (gap == 1) "" else "s"}", rivalRect.centerX(), rivalRect.bottom - 15f, textPaint(inter, 15f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        } else {
            c.drawText("Aucun rival classé sur cette cadence", rivalRect.centerX(), rivalRect.centerY() + 8f, textPaint(inter, 20f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }

        c.drawText("DERNIER ÉVÉNEMENT · LES TROIS #1", W / 2f, 980f, textPaint(bold, 21f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.06f })
        val event = visual.featuredEvents.firstOrNull()
        if (event != null) {
            c.drawText("${event.cadenceLabel.uppercase()} · ${event.dateLabel.uppercase()}", W / 2f, 1013f, textPaint(bold, 17f, cyan).apply { textAlign = Paint.Align.CENTER })
            val cardTop = 1035f
            val cardBottom = 1290f
            val gap = 16f
            val cardWidth = (W - 144f - 2f * gap) / 3f
            val pillars = listOf(
                Triple("HOT 100", event.trackName, event.trackImageUrl) to cyan,
                Triple("ARTIST 50", event.artistName, event.artistImageUrl) to violet,
                Triple("ALBUMS 75", event.albumName, event.albumImageUrl) to 0xFFFFC86B.toInt()
            )
            pillars.forEachIndexed { index, (pillar, accent) ->
                val left = 72f + index * (cardWidth + gap)
                val rect = RectF(left, cardTop, left + cardWidth, cardBottom)
                c.drawRoundRect(rect, 19f, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.83f) })
                c.drawRoundRect(rect, 19f, 19f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = withAlpha(accent, 0.65f) })
                c.drawText(pillar.first, rect.centerX(), rect.top + 29f, textPaint(bold, 16f, accent).apply { textAlign = Paint.Align.CENTER })
                val coverRect = RectF(rect.left + 31f, rect.top + 43f, rect.right - 31f, rect.top + 173f)
                val cover = pillar.third?.let { loadBitmap(ctx, it, 260) }
                if (cover != null) {
                    if (index == 1) drawCircleCover(c, cover, coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.5f)
                    else drawCover(c, cover, coverRect, 13f)
                } else {
                    c.drawRoundRect(coverRect, 13f, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, accent, violet, Shader.TileMode.CLAMP) })
                    c.drawText(pillar.second.take(1).uppercase(), coverRect.centerX(), coverRect.centerY() + 17f, textPaint(bold, 44f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
                }
                drawWrapped(c, pillar.second, rect.left + 10f, rect.top + 205f, rect.width() - 20f, textPaint(bold, 16f, theme.text.toArgb()), maxLines = 2, lineH = 19f)
                c.drawText("#1", rect.centerX(), rect.bottom - 12f, textPaint(bold, 19f, accent).apply { textAlign = Paint.Align.CENTER })
            }
            if (event.margins.isNotEmpty()) {
                val margins = event.margins.joinToString("   ·   ") { margin ->
                    val second = margin.runnerUpPlays?.let { formatCount(it) } ?: "—"
                    val gapText = margin.gap?.let { "+${formatCount(it)}" } ?: "—"
                    "${margin.chartLabel}: #2 ${margin.runnerUpName ?: "—"} ($second) · $gapText"
                }
                c.drawText(ellipsize(margins, textPaint(inter, 13f, theme.textSecondary.toArgb()), W - 144f), W / 2f, 1318f, textPaint(inter, 13f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
            }
        } else {
            c.drawText("Aucun événement détaillé n'est disponible.", W / 2f, 1120f, textPaint(inter, 18f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }

        c.drawText("ÉPHÉMÉRIDE · DATES DE LA CADENCE", W / 2f, 1370f, textPaint(bold, 21f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.06f })
        val recent = visual.heatmap.takeLast(28)
        val cell = 43f
        val cellGap = 9f
        val gridWidth = 7f * cell + 6f * cellGap
        val startX = (W - gridWidth) / 2f
        val startY = 1405f
        recent.forEachIndexed { index, heatCell ->
            val left = startX + (index % 7) * (cell + cellGap)
            val top = startY + (index / 7) * (cell + cellGap)
            val rect = RectF(left, top, left + cell, top + cell)
            val heatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                if (heatCell.isPak) shader = LinearGradient(left, top, left + cell, top + cell, withAlpha(violet, 0.92f), withAlpha(gold, 0.84f), Shader.TileMode.CLAMP)
                else color = withAlpha(theme.surface.toArgb(), 0.46f)
            }
            c.drawRoundRect(rect, 10f, 10f, heatPaint)
            c.drawRoundRect(rect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(AColor.WHITE, if (heatCell.isPak) 0.40f else 0.13f) })
            c.drawText(heatCell.dateIso.takeLast(2), rect.centerX(), rect.centerY() + 5f, textPaint(bold, 15f, if (heatCell.isPak) AColor.WHITE else theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }
        if (recent.isNotEmpty()) {
            val pakCount = recent.count { it.isPak }
            c.drawText("${recent.first().dateIso}  →  ${recent.last().dateIso} · $pakCount PAK récents / ${visual.dates.size} au total", W / 2f, 1647f, textPaint(inter, 15f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        } else c.drawText("Aucun snapshot conservé", W / 2f, 1515f, textPaint(inter, 18f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
        c.drawText("UNE DATE · HOT 100 + ARTIST 50 + ALBUMS 75", W / 2f, 1745f, textPaint(bold, 18f, withAlpha(gold, 0.96f)).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.04f })
        c.drawText("NOVASTATS", W - 72f, H - 75f, textPaint(bold, 30f, withAlpha(violet, 0.98f)).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.25f })
        c.drawRect(0f, H - 9f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), 0f, violet, cyan, Shader.TileMode.CLAMP) })
        writePng(ctx, bmp, "nova_most_perfect_all_kill_${entityId}_${visual.period.dbName}.png")
    }

    /** 👑 Carte de prestige dédiée au Hall of Fame (galerie) et aux distinctions Global (constellation). */
    suspend fun renderHonorCard(
        ctx: Context,
        entityId: Long,
        entityType: String,
        name: String,
        subtitle: String?,
        imageUrl: String?,
        isArtist: Boolean,
        recordTitle: String,
        contextLabel: String,
        visual: RecordExplainer.HonorVisual,
        theme: NovaTheme
    ): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val global = visual.isGlobal
        val accent = if (global) 0xFF67DFF5.toInt() else NovaColors.Gold.toArgb()
        val secondary = if (global) 0xFF7769FF.toInt() else 0xFFC87737.toInt()
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, H.toFloat(),
                intArrayOf(theme.background.toArgb(), blend(theme.background.toArgb(), secondary, if (global) 0.24f else 0.18f), AColor.BLACK),
                floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 210f, 330f, 590f, accent, 0.31f)
        halo(c, 930f, 1010f, 550f, secondary, 0.25f)
        if (global) {
            // Quelques points reliés en constellation, en arrière-plan du contenu.
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0.24f); strokeWidth = 2f }
            val stars = listOf(830f to 300f, 1000f to 440f, 870f to 610f, 980f to 890f, 840f to 1070f)
            stars.zipWithNext().forEach { (a, b) -> c.drawLine(a.first, a.second, b.first, b.second, starPaint) }
            stars.forEach { (x, y) -> c.drawCircle(x, y, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0.56f) }) }
        } else {
            // Ornement de galerie : double filet et points de lumière façon cadre cérémoniel.
            val ornament = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0.58f) }
            listOf(90f, W - 90f).forEach { x ->
                c.drawCircle(x, 286f, 5f, ornament)
                c.drawCircle(x, 750f, 5f, ornament)
            }
        }
        val frame = RectF(28f, 28f, W - 28f, H - 28f)
        c.drawRoundRect(frame, 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = withAlpha(accent, 0.55f)
        })
        c.drawRoundRect(RectF(43f, 43f, W - 43f, H - 43f), 38f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(theme.text.toArgb(), 0.13f)
        })

        val brand = textPaint(bold, 28f, withAlpha(theme.accent.toArgb(), 0.98f)).apply { letterSpacing = 0.24f }
        c.drawText(if (global) "NOVA GLOBAL · CONSTELLATION" else "NOVA HALL OF FAME · GALERIE D'HONNEUR", 72f, 120f, brand)
        val contextPaint = textPaint(inter, 23f, theme.textSecondary.toArgb())
        c.drawText(ellipsize(contextLabel.uppercase(java.util.Locale.FRANCE), contextPaint, W - 144f), 72f, 165f, contextPaint)
        val medalRect = RectF(72f, 190f, W - 72f, 239f)
        c.drawRoundRect(medalRect, 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0.14f) })
        c.drawRoundRect(medalRect, 25f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.7f; color = withAlpha(accent, 0.52f) })
        val ceremony = when {
            visual.rank == 1 && visual.tiedAtTop > 1 -> "✦ SOMMET PARTAGÉ · ${visual.tiedAtTop} EX ÆQUO ✦"
            visual.rank == 1 && global -> "✦ AU SOMMET DE LA CONSTELLATION ✦"
            visual.rank == 1 -> "✦ INTRONISÉ AU SOMMET ✦"
            global -> "✧ CONSTELLATION · RANG #${visual.rank} ✧"
            else -> "✧ GALERIE D'HONNEUR · RANG #${visual.rank} ✧"
        }
        c.drawText(ceremony, medalRect.centerX(), 222f, textPaint(bold, 21f, accent).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f })

        var titleBottom = drawWrapped(c, name, 72f, 280f, W - 144f, textPaint(bold, 65f, theme.text.toArgb()), maxLines = 2, lineH = 74f)
        subtitle?.takeIf { it.isNotBlank() }?.let {
            titleBottom = drawWrapped(c, it, 72f, titleBottom + 2f, W - 144f, textPaint(inter, 28f, theme.textSecondary.toArgb()), maxLines = 1, lineH = 35f)
        }
        val coverTop = maxOf(378f, titleBottom + 22f)
        val coverRect = RectF(72f, coverTop, 390f, coverTop + 318f)
        val cover = imageUrl?.let { loadBitmap(ctx, it, 540) }
        if (cover != null) {
            if (isArtist) drawCircleCover(c, cover, coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.48f)
            else drawCover(c, cover, coverRect, 30f)
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(coverRect.left, coverRect.top, coverRect.right, coverRect.bottom, secondary, accent, Shader.TileMode.CLAMP)
            }
            if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.48f, placeholder)
            else c.drawRoundRect(coverRect, 30f, 30f, placeholder)
            c.drawText(name.take(1).uppercase(), coverRect.centerX(), coverRect.centerY() + 50f, textPaint(bold, 150f, theme.text.toArgb()).apply { textAlign = Paint.Align.CENTER })
        }
        val coverFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = withAlpha(accent, 0.88f) }
        if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.48f, coverFrame)
        else c.drawRoundRect(coverRect, 30f, 30f, coverFrame)

        val rightX = 432f
        val rightW = W - rightX - 72f
        c.drawText("DISTINCTION", rightX, coverTop + 42f, textPaint(bold, 25f, withAlpha(accent, 0.95f)).apply { letterSpacing = 0.16f })
        val titleEnd = drawWrapped(c, recordTitle, rightX, coverTop + 91f, rightW, textPaint(bold, 39f, theme.text.toArgb()), maxLines = 2, lineH = 46f)
        c.drawText(if (global) "SCEAUX GLOBAL OBTENUS" else "INTRONISATIONS DISTINCTES", rightX, titleEnd + 38f, textPaint(bold, 20f, theme.textSecondary.toArgb()))
        val scoreCenterY = titleEnd + 111f
        c.drawCircle(rightX + 85f, scoreCenterY - 17f, 91f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(rightX + 85f, scoreCenterY - 17f, 91f, intArrayOf(withAlpha(accent, 0.32f), withAlpha(accent, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawText("${visual.score}", rightX, scoreCenterY + 7f, textPaint(bold, 82f, accent))
        c.drawText("/ ${visual.maxScore}", rightX + 140f, scoreCenterY + 1f, textPaint(bold, 32f, theme.textSecondary.toArgb()))
        c.drawText("RANG #${visual.rank}  ·  ${visual.boardSize.coerceAtLeast(1)} ENTITÉS DISTINGUÉES", rightX, scoreCenterY + 59f, textPaint(bold, 18f, theme.textSecondary.toArgb()))

        val sectionTop = coverRect.bottom + 54f
        val sectionLabel = if (global) "LES TROIS SCEAUX · LUMINEUX SI OBTENUS" else "FRISE DES INTRONISATIONS"
        c.drawText(sectionLabel, 72f, sectionTop, textPaint(bold, 25f, withAlpha(accent, 0.95f)).apply { letterSpacing = 0.10f })
        var sectionBottom: Float
        if (global) {
            val gap = 15f
            val cardW = (W - 144f - gap * 2f) / 3f
            val cardTop = sectionTop + 27f
            val cardH = 279f
            visual.seals.take(3).forEachIndexed { index, seal ->
                val left = 72f + index * (cardW + gap)
                val rect = RectF(left, cardTop, left + cardW, cardTop + cardH)
                val earned = seal.earnedDate != null
                c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(if (earned) accent else theme.surface.toArgb(), if (earned) 0.14f else 0.55f) })
                c.drawRoundRect(rect, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = if (earned) 2.5f else 1.5f; color = withAlpha(if (earned) accent else theme.textSecondary.toArgb(), if (earned) 0.64f else 0.22f) })
                val cx = rect.centerX()
                val cy = rect.top + 50f
                c.drawCircle(cx, cy, 35f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, cy, 35f, intArrayOf(withAlpha(accent, if (earned) 0.46f else 0.08f), withAlpha(accent, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
                })
                val glyph = if (earned) seal.emoji else "◇"
                c.drawText(glyph, cx, cy + 14f, textPaint(bold, if (earned) 38f else 42f, if (earned) theme.text.toArgb() else withAlpha(theme.textSecondary.toArgb(), 0.43f)).apply { textAlign = Paint.Align.CENTER })
                drawWrapped(c, seal.title.uppercase(java.util.Locale.FRANCE), rect.left + 12f, rect.top + 105f, rect.width() - 24f, textPaint(bold, 21f, if (earned) accent else theme.textSecondary.toArgb()), maxLines = 2, lineH = 25f)
                val status = if (earned) "OBTENU · ${seal.earnedDate}" else "À CONQUÉRIR"
                c.drawText(ellipsize(status, textPaint(bold, 18f, if (earned) accent else theme.textSecondary.toArgb()), rect.width() - 22f), rect.centerX(), rect.top + 177f, textPaint(bold, 18f, if (earned) accent else theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
                drawWrapped(c, seal.description, rect.left + 13f, rect.top + 207f, rect.width() - 26f, textPaint(inter, 15f, theme.textSecondary.toArgb()), maxLines = 2, lineH = 19f)
                c.drawText("${seal.distinctAwardees} ENTITÉS", rect.centerX(), rect.bottom - 14f, textPaint(bold, 17f, if (earned) theme.text.toArgb() else theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.CENTER })
            }
            sectionBottom = cardTop + cardH
        } else {
            var rowTop = sectionTop + 22f
            val rows = visual.entries.take(4)
            rows.forEach { entry ->
                val rect = RectF(72f, rowTop, W - 72f, rowTop + 99f)
                c.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.80f) })
                c.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(accent, 0.30f) })
                c.drawCircle(rect.left + 42f, rect.centerY(), 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(accent, 0.17f) })
                val seal = visual.seals.firstOrNull { it.key == entry.sealKey }
                c.drawText(seal?.emoji ?: "🏅", rect.left + 42f, rect.centerY() + 9f, textPaint(bold, 25f, accent).apply { textAlign = Paint.Align.CENTER })
                val tx = rect.left + 83f
                val maxW = rect.right - tx - 18f
                c.drawText(ellipsize(entry.title, textPaint(bold, 21f, theme.text.toArgb()), maxW - 185f), tx, rect.top + 29f, textPaint(bold, 21f, theme.text.toArgb()))
                c.drawText("${entry.periodLabel.uppercase(java.util.Locale.FRANCE)} · ${entry.date}", tx, rect.top + 60f, textPaint(bold, 17f, accent))
                val details = buildList {
                    entry.reignDuration?.let { add("Règne continu : $it") }
                    entry.reign?.let { add(it) }
                    if (entry.weeksAtOne > 0) add("${entry.weeksAtOne} semaines cumulées au #1")
                    if (isEmpty()) seal?.detail?.let { add(it) }
                    entry.playsAtEntry.takeIf { it > 0 }?.let { add("$it ▶ à l'entrée") }
                    seal?.takeIf { it.distinctAwardees > 0 }?.let { add("${it.distinctAwardees} distingués") }
                }.joinToString(" · ")
                c.drawText(ellipsize(details, textPaint(inter, 16f, theme.textSecondary.toArgb()), maxW), tx, rect.bottom - 12f, textPaint(inter, 16f, theme.textSecondary.toArgb()))
                rowTop += 110f
            }
            if (rows.isEmpty()) {
                c.drawText("Aucune intronisation détaillée.", 72f, sectionTop + 62f, textPaint(inter, 21f, theme.textSecondary.toArgb()))
                rowTop = sectionTop + 92f
            }
            sectionBottom = rowTop - 11f
        }

        val contentBottom = sectionBottom
        visual.rival?.let { rival ->
            val top = contentBottom + 33f
            c.drawText("⚔ DUEL DES DISTINCTIONS", 72f, top, textPaint(bold, 24f, withAlpha(accent, 0.96f)))
            val rect = RectF(72f, top + 18f, W - 72f, top + 226f)
            c.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.82f) })
            c.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = withAlpha(accent, 0.31f) })
            val leftName = ellipsize("#${visual.rank} · $name", textPaint(bold, 22f, theme.text.toArgb()), 385f)
            val rightName = ellipsize("#${rival.rank} · ${rival.name}", textPaint(bold, 22f, theme.text.toArgb()), 385f)
            c.drawText(leftName, rect.left + 22f, rect.top + 43f, textPaint(bold, 22f, theme.text.toArgb()))
            c.drawText("${visual.score}", rect.left + 22f, rect.top + 87f, textPaint(bold, 36f, accent))
            c.drawText("VS", rect.centerX(), rect.top + 65f, textPaint(bold, 20f, NovaColors.Gold.toArgb()).apply { textAlign = Paint.Align.CENTER })
            c.drawText(rightName, rect.right - 22f, rect.top + 43f, textPaint(bold, 22f, theme.text.toArgb()).apply { textAlign = Paint.Align.RIGHT })
            c.drawText("${rival.score}", rect.right - 22f, rect.top + 87f, textPaint(bold, 36f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.RIGHT })
            val marksY = rect.top + 118f
            val seals = visual.seals
            val cellW = (rect.width() - 36f) / seals.size.coerceAtLeast(1)
            seals.forEachIndexed { index, seal ->
                val x = rect.left + 18f + index * cellW
                val ours = visual.entries.any { it.sealKey == seal.key }
                val theirs = seal.key in rival.earnedKeys
                val p = textPaint(inter, 15f, theme.textSecondary.toArgb())
                c.drawText(ellipsize(seal.title, p, cellW - 8f), x, marksY + 16f, p)
                c.drawText("${if (ours) "✦" else "—"} / ${if (theirs) "✦" else "—"}", x, marksY + 42f, textPaint(bold, 19f, accent))
            }
            c.drawText(if (rival.tied) "ÉGALITÉ · même total de distinctions" else "TOTAL · toi ${visual.score}  /  rival ${rival.score}", rect.centerX(), rect.bottom - 14f, textPaint(bold, 18f, accent).apply { textAlign = Paint.Align.CENTER })
        }

        c.drawText("Données de distinctions distinctes · classement #${visual.rank} sur ${visual.boardSize.coerceAtLeast(1)} entités", 72f, H - 118f, textPaint(inter, 18f, theme.textSecondary.toArgb()))
        c.drawText("NOVASTATS", W - 72f, H - 72f, textPaint(bold, 30f, withAlpha(accent, 0.96f)).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.28f })
        c.drawRect(0f, H - 9f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), 0f, accent, secondary, Shader.TileMode.CLAMP)
        })
        writePng(ctx, bmp, "nova_honor_${if (global) "global" else "hof"}_${entityType.lowercase()}_${visual.selectedSub ?: "all"}_${entityId}.png")
    }

    /** 📤 Carte du parcours d'une entité dans son chart (+ face-à-face éventuel), 1080×1920, aux couleurs du thème. */
    suspend fun renderChartStory(ctx: Context, h: EntityHistory, compare: EntityHistory? = null, theme: NovaTheme): File = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val inter = ResourcesCompat.getFont(ctx, R.font.inter) ?: Typeface.SANS_SERIF
        val bold = Typeface.create(inter, Typeface.BOLD)
        val gold = NovaColors.Gold.toArgb()
        val silver = NovaColors.Silver.toArgb()

        /* Fond dégradé + halo or */
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(theme.background.toArgb(), blend(theme.background.toArgb(), gold, 0.18f), AColor.BLACK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        halo(c, 200f, 300f, 520f, gold, 0.35f)

        /* En-tête */
        c.drawText("NOVA BILLBOARD · ${h.chart.label.uppercase()}", 72f, 140f, textPaint(bold, 30f, withAlpha(theme.accent.toArgb(), 0.95f)).apply { letterSpacing = 0.2f })
        var y = drawWrapped(c, h.item.name, 72f, 250f, W - 144f, textPaint(bold, 84f, theme.text.toArgb()), 2, 96f)
        h.item.secondary?.let { y = drawWrapped(c, it, 72f, y + 10f, W - 144f, textPaint(inter, 40f, theme.textSecondary.toArgb()), 1, 52f) }

        /* Pochette + peak en grand */
        val coverBmp = h.item.coverUrl?.let { loadBitmap(ctx, it, 700) }
        val coverRect = RectF(72f, y + 40f, 72f + 420f, y + 460f)
        if (coverBmp != null) drawCover(c, coverBmp, coverRect, 40f)
        h.stats.peak?.let { pk ->
            c.drawText("PEAK", 560f, y + 140f, textPaint(bold, 34f, withAlpha(gold, 0.8f)).apply { letterSpacing = 0.2f })
            c.drawText("#${pk.position}", 560f, y + 260f, textPaint(bold, 110f, gold))
            if (h.stats.timesAtPeak > 1) c.drawText("×${h.stats.timesAtPeak}", 560f, y + 330f, textPaint(bold, 44f, withAlpha(gold, 0.9f)))
        }

        /* La phrase-histoire */
        val sy = drawWrapped(c, storySentence(h), 72f, coverRect.bottom + 90f, W - 144f, textPaint(inter, 40f, theme.text.toArgb()), 4, 56f)

        /* Courbe des positions (#1 en haut) + face-à-face argent */
        val plot = RectF(72f, sy + 60f, W - 72f, sy + 620f)
        val sorted = h.appearances.sortedBy { it.date }
        val maxPos = (sorted.maxOfOrNull { it.position } ?: 5).coerceAtLeast(5).toFloat()
        fun px(i: Int) = plot.left + if (sorted.size < 2) plot.width() / 2 else plot.width() * i / (sorted.size - 1)
        fun py(v: Float) = plot.top + plot.height() * ((v - 1f) / (maxPos - 1f)).coerceIn(0f, 1f)
        listOf(1f, maxPos).forEach { g ->
            c.drawLine(plot.left, py(g), plot.right, py(g), Paint().apply { color = withAlpha(theme.textSecondary.toArgb(), 0.25f); strokeWidth = 2f })
            c.drawText("#${g.toInt()}", plot.left, py(g) - 8f, textPaint(inter, 26f, theme.textSecondary.toArgb()))
        }
        fun drawSeries(ap: List<ChartAppearance>, color: Int, width: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = width; style = Paint.Style.STROKE }
            var prev: android.graphics.PointF? = null
            ap.forEachIndexed { i, a ->
                val cur = android.graphics.PointF(px(i), py(a.position.toFloat()))
                prev?.let { c.drawLine(it.x, it.y, cur.x, cur.y, p) }
                prev = cur
            }
            ap.forEachIndexed { i, a -> c.drawCircle(px(i), py(a.position.toFloat()), 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }) }
        }
        val dFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yy")
        if (sorted.size >= 2) {
            compare?.let { cmp -> drawSeries(cmp.appearances.sortedBy { it.date }, silver, 6f) }
            drawSeries(sorted, gold, 9f)
            h.stats.peak?.let { pk ->
                val i = sorted.indexOfFirst { it.date == pk.date }
                if (i >= 0) c.drawText("⭐", px(i), py(pk.position.toFloat()) - 24f, textPaint(inter, 44f, gold).apply { textAlign = Paint.Align.CENTER })
            }
            sorted.first().let { c.drawText(it.date.format(dFmt), plot.left, plot.bottom + 48f, textPaint(inter, 26f, theme.textSecondary.toArgb())) }
            sorted.last().let { c.drawText(it.date.format(dFmt), plot.right, plot.bottom + 48f, textPaint(inter, 26f, theme.textSecondary.toArgb()).apply { textAlign = Paint.Align.RIGHT }) }
        }

        /* Pied */
        compare?.let { c.drawText("⚔ vs ${it.item.name}", 72f, H - 60f, textPaint(bold, 34f, silver)) }
        c.drawText("NovaStats", W - 72f, H - 60f, textPaint(bold, 30f, withAlpha(theme.textSecondary.toArgb(), 0.8f)).apply { textAlign = Paint.Align.RIGHT })

        writePng(ctx, bmp, "nova_chart_${h.item.entityId}.png")
    }

    /* ----------------------------- helpers ----------------------------- */

    suspend fun loadBitmap(ctx: Context, url: String, size: Int): Bitmap? = runCatching {
        val req = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(size).build()
        ImageLoader(ctx).execute(req).drawable?.toBitmap()
    }.getOrNull()

    fun writePng(ctx: Context, bmp: Bitmap, name: String): File {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }

    fun textPaint(tf: Typeface, size: Float, color: Int): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = tf; textSize = size; this.color = color }

    fun halo(c: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, r, intArrayOf(withAlpha(color, alpha), withAlpha(color, 0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP) }
        c.drawCircle(cx, cy, r, p)
    }

    fun drawCover(c: Canvas, bmp: Bitmap, rect: RectF, radius: Float) {
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(rect.width() / bmp.width, rect.height() / bmp.height)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(rect.left + (rect.width() - bmp.width * scale) / 2f, rect.top + (rect.height() - bmp.height * scale) / 2f)
        }
        shader.setLocalMatrix(m)
        c.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    }

    fun drawCircleCover(c: Canvas, bmp: Bitmap, cx: Float, cy: Float, r: Float) {
        val path = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        c.save(); c.clipPath(path)
        drawCover(c, bmp, RectF(cx - r, cy - r, cx + r, cy + r), 0f)
        c.restore()
    }

    fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    /** Dessine du texte sur plusieurs lignes (coupe aux espaces), au plus `maxLines`, la dernière en « … ». */
    fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, maxW: Float, p: Paint, maxLines: Int, lineH: Float): Float {
        val words = text.split(' ')
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (w in words) {
            val probe = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(probe) <= maxW || cur.isEmpty()) cur = StringBuilder(probe)
            else { lines += cur.toString(); cur = StringBuilder(w) }
            if (lines.size == maxLines) break
        }
        if (lines.size < maxLines && cur.isNotEmpty()) lines += cur.toString()
        val shown = lines.take(maxLines).toMutableList()
        if (lines.size > maxLines || (lines.size == maxLines && cur.isNotEmpty() && cur.toString() != lines.last())) shown[shown.lastIndex] = ellipsize(shown.last() + " …", p, maxW)
        shown.forEachIndexed { i, l -> c.drawText(ellipsize(l, p, maxW), x, y + i * lineH, p) }
        return y + min(shown.size, maxLines) * lineH
    }

    fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

    fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(sh: Int) = (((a shr sh) and 0xFF) * (1 - t) + ((b shr sh) and 0xFF) * t).toInt().coerceIn(0, 255)
        return AColor.argb(255, ch(16), ch(8), ch(0))
    }
}
