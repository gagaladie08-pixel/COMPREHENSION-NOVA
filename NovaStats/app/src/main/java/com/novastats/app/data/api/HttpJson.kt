package com.novastats.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client HTTP minimaliste partagé par toutes les APIs : OkHttp + arbre JSON kotlinx.
 * Pas de DTO par API — on navigue dans le JSON, ce qui rend les clients courts et tolérants.
 */
object HttpJson {

    const val USER_AGENT = "NovaStats/1.0 (novastats.app@gmail.com)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    class HttpException(val code: Int, url: String) : Exception("HTTP $code · $url")

    /** GET → JSON (null si 404 / corps vide). Lève HttpException pour les autres codes d'erreur. */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): JsonElement? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json")
        headers.forEach { (k, v) -> req.header(k, v) }
        client.newCall(req.build()).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw HttpException(resp.code, url)
            val body = resp.body?.string()?.takeIf { it.isNotBlank() } ?: return@withContext null
            json.parseToJsonElement(body)
        }
    }

    /** POST formulaire → JSON. */
    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): JsonElement? =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            val req = Request.Builder().url(url).post(form).header("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> req.header(k, v) }
            client.newCall(req.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw HttpException(resp.code, url)
                resp.body?.string()?.takeIf { it.isNotBlank() }?.let { json.parseToJsonElement(it) }
            }
        }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** Limiteur de débit par source (MusicBrainz 1 req/s, iTunes ~20 req/min, Discogs 25-60 req/min…). */
class RateLimiter(private val minIntervalMs: Long) {
    private val mutex = Mutex()
    private var last = 0L
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = last + minIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try { block() } finally { last = System.currentTimeMillis() }
    }
}

/* ---------- Navigation JSON tolérante ---------- */

val JsonElement?.obj: JsonObject? get() = (this as? JsonObject)
val JsonElement?.arr: JsonArray? get() = (this as? JsonArray)
fun JsonElement?.obj(key: String): JsonObject? = this.obj?.get(key)?.let { if (it is JsonNull) null else it.obj }
fun JsonElement?.arr(key: String): JsonArray? = this.obj?.get(key)?.let { if (it is JsonNull) null else it.arr }
fun JsonElement?.str(key: String): String? = this.obj?.get(key)?.let { if (it is JsonPrimitive) it.contentOrNull?.takeIf { s -> s.isNotBlank() } else null }
fun JsonElement?.num(key: String): Double? = this.obj?.get(key)?.let { if (it is JsonPrimitive) it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() else null }
fun JsonElement?.long(key: String): Long? = num(key)?.toLong()
val JsonElement?.string: String? get() = (this as? JsonPrimitive)?.contentOrNull
fun JsonArray?.firstObj(): JsonObject? = this?.firstOrNull()?.obj
fun JsonArray?.objs(): List<JsonObject> = this?.mapNotNull { it.obj }.orEmpty()
