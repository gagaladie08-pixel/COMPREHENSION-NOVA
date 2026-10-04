package com.novastats.app.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🧯 Journal des plantages : toute exception non rattrapée (fermeture brutale de l'app) et les erreurs
 * non fatales notées par l'app sont écrites dans `files/crash_journal.txt` (les ~40 Ko les plus récents).
 * Lisible / copiable depuis Réglages → Service & diagnostic → Plantages, pour pouvoir me coller la trace.
 */
object CrashJournal {
    private const val FILE = "crash_journal.txt"
    private const val MAX_CHARS = 40_000
    @Volatile private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { append(app, "💥 PLANTAGE · thread ${thread.name}", e) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Erreur non fatale (rattrapée) mais utile au diagnostic. */
    fun note(ctx: Context, tag: String, e: Throwable) { runCatching { append(ctx.applicationContext, "⚠️ $tag", e) } }

    private fun append(ctx: Context, title: String, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())
        val entry = "=== $stamp · v$version · Android ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n$title\n$sw\n"
        val f = file(ctx)
        val old = if (f.exists()) f.readText() else ""
        val merged = entry + old
        f.writeText(if (merged.length > MAX_CHARS) merged.take(MAX_CHARS) else merged)
    }

    fun read(ctx: Context): String = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
}
