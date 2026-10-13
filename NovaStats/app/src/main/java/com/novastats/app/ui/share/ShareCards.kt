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

        val eyebrow = textPaint(bold, 30f, withAlpha(theme.accent.toArgb(), 0.96f)).apply { letterSpacing = 0.2f }
        c.drawText("NOVA RECORDS · DOMINATION", 72f, 122f, eyebrow)
        c.drawText(ellipsize(visual.contextLabel.uppercase(java.util.Locale.FRANCE), textPaint(inter, 24f, theme.textSecondary.toArgb()), W - 144f), 72f, 172f, textPaint(inter, 24f, theme.textSecondary.toArgb()))
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
            style = Paint.Style.STROKE; strokeWidth = 4f; color = withAlpha(theme.text.toArgb(), 0.25f)
        }
        if (isArtist) c.drawCircle(coverRect.centerX(), coverRect.centerY(), coverRect.width() * 0.47f, coverBorder)
        else c.drawRoundRect(coverRect, 34f, 34f, coverBorder)

        val rightX = 444f
        val rightW = W - rightX - 72f
        c.drawText("RECORD", rightX, coverTop + 38f, textPaint(bold, 26f, withAlpha(theme.accent.toArgb(), 0.95f)).apply { letterSpacing = 0.18f })
        val recordBottom = drawWrapped(c, visual.recordTitle, rightX, coverTop + 88f, rightW, textPaint(bold, 38f, theme.text.toArgb()), maxLines = 2, lineH = 44f)
        c.drawText("SCORE · ${visual.itemLabel.uppercase(java.util.Locale.FRANCE)}S DISTINCTS", rightX, recordBottom + 32f, textPaint(bold, 20f, theme.textSecondary.toArgb()))
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
            c.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(theme.surface.toArgb(), 0.72f) })
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
