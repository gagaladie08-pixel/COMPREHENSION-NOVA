package com.novastats.app.service

import com.novastats.app.BuildConfig
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.importer.BackupExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 🤖 Synchro agent — FONCTION TEMPORAIRE (à retirer une fois le peaufinage terminé).
 * Pousse l'export JSON COMPLET (BackupExporter v2.3 : écoutes, titres, artistes, albums,
 * certifications, Panthéon, Hall of Fame, éditions, corrections) vers un Gist GitHub privé,
 * afin que l'agent de développement dispose d'un miroir fidèle de la base.
 * Le token ne quitte l'appareil que vers api.github.com ; la synchro se désactive d'un interrupteur.
 */
object AgentSync {

    private const val THROTTLE_MS = 10 * 60_000L
    private val MEDIA = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()

    /** Poussée après un recalcul de stats : rien si la synchro est désactivée ou si la dernière pousse date de moins de 10 min. */
    suspend fun autoPush(app: NovaStatsApp): String? {
        val s = app.settings
        if (!s.agentSync.first()) return null
        if (System.currentTimeMillis() - (s.agentLastSync.first() ?: 0L) < THROTTLE_MS) return null
        return push(app, force = true)
    }

    /** Pousse complète. Crée le Gist au premier envoi, puis met toujours à jour le même Gist. */
    suspend fun push(app: NovaStatsApp, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val s = app.settings
        if (!force && !s.agentSync.first()) return@withContext null
        val token = s.agentToken.first()?.trim().orEmpty()
        if (token.isEmpty()) return@withContext "⚠️ Aucun token GitHub enregistré (Réglages → Synchro agent)"
        val (text, sum) = BackupExporter.build(app.database, BuildConfig.VERSION_NAME)
        // L'API GitHub tronque tout fichier > 1 Mo : le miroir part compressé (gzip + base64, ~5x plus petit),
        // lisible intégralement via api.github.com. nova_status.txt reste du texte clair pour un coup d'œil humain.
        val gz = java.io.ByteArrayOutputStream().use { bos ->
            java.util.zip.GZIPOutputStream(bos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            bos.toByteArray()
        }
        val b64 = android.util.Base64.encodeToString(gz, android.util.Base64.NO_WRAP)
        val statusTxt = "NovaStats ${BuildConfig.VERSION_NAME} · export ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())} (heure appareil)\n" +
            "${sum.plays} écoutes · ${sum.songs} titres · ${sum.artists} artistes · ${sum.albums} albums\n" +
            "Miroir complet : nova_backup.b64 (gzip+base64, ${gz.size / 1024} Ko)\n" +
            "Système temporaire — synchro agent."
        val gistId = s.agentGistId.first().orEmpty()
        val payload = buildJsonObject {
            put("description", "NovaStats — miroir agent (temporaire)")
            put("public", false)
            putJsonObject("files") {
                putJsonObject("nova_status.txt") { put("content", statusTxt) }
                putJsonObject("nova_backup.b64") { put("content", b64) }
                if (gistId.isNotEmpty()) put("nova_backup.json", JsonNull) // purge l'ancien fichier non compressé (> 1 Mo)
            }
        }.toString()
        val request = (
            if (gistId.isEmpty()) Request.Builder().url("https://api.github.com/gists").post(payload.toRequestBody(MEDIA))
            else Request.Builder().url("https://api.github.com/gists/$gistId").patch(payload.toRequestBody(MEDIA))
            )
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        val resp = client.newCall(request).execute()
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            val hint = when (resp.code) {
                401, 403 -> " — token invalide ou sans le scope « gist »"
                422 -> " — export trop volumineux pour l'API Gist (> 10 Mo)"
                else -> ""
            }
            return@withContext "⚠️ Synchro échouée (HTTP ${resp.code}$hint)"
        }
        if (gistId.isEmpty()) {
            val id = runCatching { Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
            if (id != null) s.setAgentGistId(id)
        }
        s.setAgentLastSync(System.currentTimeMillis())
        "✅ Synchronisé : ${sum.plays} écoutes · ${sum.songs} titres · ${sum.artists} artistes · ${sum.bytes / 1024} Ko"
    }
}
