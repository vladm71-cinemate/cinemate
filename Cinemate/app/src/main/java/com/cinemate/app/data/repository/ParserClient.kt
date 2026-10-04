package com.cinemate.app.data.repository

import com.cinemate.app.data.local.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Раздача из парсера. */
data class TorrentResult(
    val title: String,
    val magnetUri: String,
    val sizeName: String,
    val sizeBytes: Long,
    val seeders: Int,
    val leechers: Int,
    val date: String,
    val tracker: String,
    val quality: Int?,
    val voices: List<String>,
    val year: Int?,
    val category: String?,
    val seasons: List<Int>,
    /** Битрейт строкой (если нода отдаёт). */
    val bitrate: String = "",
    /** Язык фильма/дорожки (languages из ноды). */
    val languages: List<String> = emptyList()
)

/**
 * Нода парсера (публичный сервер поиска раздач).
 * scheme — "https" | "http": у некоторых узлов есть только HTTP-зеркало.
 */
data class ParserNode(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val scheme: String = "https",
    val builtIn: Boolean = false
)

/** Результат поиска с указанием, какая нода ответила. */
data class ParserSearchOutcome(
    val results: List<TorrentResult>,
    val answeredBy: ParserNode?,
    val fellBack: Boolean
)

@Singleton
class ParserClient @Inject constructor(
    private val tokenStore: TokenStore
) {

    companion object {
        /**
         * Вшитые ноды. Схема учитывается (HTTPS/HTTP — разные зеркала).
         */
        val BUILTIN_NODES = listOf(
            ParserNode("Jacred", "jac.red", "", scheme = "https", builtIn = true),
            ParserNode("Jacred RU", "jac-red.ru", "", scheme = "https", builtIn = true),
            ParserNode("Jacred Maxvol", "jr.maxvol.pro", "", scheme = "https", builtIn = true),
            ParserNode("Jacred Black", "ru.jac.black", "", scheme = "https", builtIn = true),
            ParserNode("Jacred Stream", "jacred.stream", "pp", scheme = "https", builtIn = true),
            ParserNode("Jacred Stream RU", "ru.jacred.stream", "pp", scheme = "https", builtIn = true),
            ParserNode("Jacred Stream RU HTTP", "ru.jacred.stream", "pp", scheme = "http", builtIn = true)
        )

        private const val CONNECT_TIMEOUT_S = 8L
        private const val READ_TIMEOUT_S = 15L
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                    )
                    .build()
            )
        }
        .build()

    private val httpPing: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
                    )
                    .build()
            )
        }
        .build()

    // ================= Адреса =================

    /** Полный адрес ноды с учётом схемы ноды (если pathAndQuery сам не содержит "://"). */
    fun fullUrl(node: ParserNode, pathAndQuery: String): String {
        val base = node.baseUrl.trim().trimEnd('/')
        return "${node.scheme}://$base$pathAndQuery"
    }

    /** Обратная совместимость: строка-адрес (если схема уже в строке — уважаем её). */
    fun fullUrl(baseUrl: String, pathAndQuery: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.contains("://")) "$base$pathAndQuery"
        else "https://$base$pathAndQuery"
    }

    /**
     * Полный список нод: пользовательские (из TokenStore) + вшитые.
     * Дедуп по host+scheme; пользовательские идут первыми.
     */
    suspend fun allNodes(): List<ParserNode> {
        val custom = parseCustomNodes(tokenStore.parserCustomNodes.first())
        val all = custom + BUILTIN_NODES
        return all.distinctBy { it.baseUrl.trim().lowercase() + "|" + it.scheme }
    }

    /** Прочитать JSON пользовательских нод; битый JSON — пустой список. */
    private fun parseCustomNodes(json: String?): List<ParserNode> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("baseUrl", "").trim()
                if (url.isBlank()) null
                else {
                    // Схема: явное поле, иначе — если в baseUrl было "://", из него
                    val scheme = o.optString("scheme", "")
                        .takeIf { it == "http" || it == "https" }
                        ?: if (url.contains("://")) url.substringBefore("://") else "https"
                    val host = if (url.contains("://")) url.substringAfter("://") else url
                    ParserNode(
                        name = o.optString("name", "").ifBlank { host },
                        baseUrl = host,
                        apiKey = o.optString("apiKey", "").trim(),
                        scheme = scheme,
                        builtIn = false
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ================= Пре-пинг ноды =================

    /** Быстрая проверка живости: HEAD-запрос. true = отвечает. */
    suspend fun pingNode(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = fullUrl(baseUrl, "/api/v2.0/indexers/all/results?query=test")
            val request = Request.Builder().url(url).head().build()
            httpPing.newCall(request).execute().use { it.code > 0 }
        } catch (e: Exception) {
            false
        }
    }

    // ================= Поиск =================

    suspend fun search(node: ParserNode, query: String): List<TorrentResult> =
        withContext(Dispatchers.IO) {
            val url = buildSearchUrl(node, query)
            val request = Request.Builder().url(url).get().build()
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string() ?: return@use emptyList()
                    parseResults(body)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** Обратная совместимость: поиск по host (схема по умолчанию https). */
    suspend fun search(nodeBaseUrl: String, apiKey: String, query: String): List<TorrentResult> {
        val node = ParserNode(nodeBaseUrl, nodeBaseUrl.trimEnd('/'), apiKey)
        return search(node, query)
    }

    /**
     * Поиск с пре-пингом и авто-переключением.
     */
    suspend fun searchWithFallback(
        selectedBaseUrl: String?,
        query: String,
        onAutoSwitched: (ParserNode) -> Unit = { }
    ): ParserSearchOutcome = withContext(Dispatchers.IO) {
        val nodes = allNodes()
        if (nodes.isEmpty()) return@withContext ParserSearchOutcome(emptyList(), null, false)

        val autoSwitch = tokenStore.parserAutoSwitch.first()
        val selected = selectedBaseUrl?.trim()?.lowercase()
        val primary = nodes.firstOrNull { it.baseUrl.trim().lowercase() == selected }
        val ordered = if (primary != null) listOf(primary) + nodes.filter { it != primary }
        else nodes

        var fellBack = false
        for ((index, node) in ordered.withIndex()) {
            if (!pingNode(fullUrl(node, "").removeSuffix("/"))) {
                fellBack = true
                if (!autoSwitch) continue
                continue
            }

            val results = search(node, query)
            if (results.isNotEmpty()) {
                if (index > 0) onAutoSwitched(node)
                return@withContext ParserSearchOutcome(results, node, fellBack = index > 0)
            }
            fellBack = true
            if (!autoSwitch) break
        }
        ParserSearchOutcome(emptyList(), null, fellBack)
    }

    /** Собрать URL поиска по ноде. */
    fun buildSearchUrl(node: ParserNode, query: String): String {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val path = "/api/v2.0/indexers/all/results?query=$encoded"
        val key = if (node.apiKey.isBlank()) ""
        else "&apikey=${URLEncoder.encode(node.apiKey, "UTF-8")}"
        return fullUrl(node, "$path$key")
    }

    /** Обратная совместимость. */
    fun buildSearchUrl(baseUrl: String, apiKey: String, query: String): String {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val path = "/api/v2.0/indexers/all/results?query=$encoded"
        val key = if (apiKey.isBlank()) ""
        else "&apikey=${URLEncoder.encode(apiKey, "UTF-8")}"
        return fullUrl(baseUrl, "$path$key")
    }

    /** Парсинг ответа парсера (Jackett v2.0: Results[]). */
    fun parseResults(json: String): List<TorrentResult> = try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("Results") ?: return emptyList()

        (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null

            val magnet = item.optString("MagnetUri", "")
                .ifBlank { item.optString("magnetUrl", "") }
                .ifBlank { item.optString("downloadUrl", "") }
            val title = item.optString("Title", "").ifBlank { item.optString("title", "") }
            if (magnet.isBlank() || title.isBlank()) return@mapNotNull null

            val info = item.optJSONObject("info")
            val seeders = item.optInt("Seeders", item.optInt("seeders", 0))
            val leechers = item.optInt("Peers", item.optInt("peers", 0))
            val tracker = item.optString("indexer", item.optString("Tracker", "—"))
            val quality = info?.optInt("quality", 0)?.takeIf { it > 0 }
            val voices = info?.optJSONArray("voices")?.let { ja ->
                (0 until ja.length()).mapNotNull { ja.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            val year = info?.optInt("relased", 0)?.takeIf { it > 0 }
            val sizeBytes = item.optLong("Size", item.optLong("size", 0L))
            val sizeName = info?.optString("sizeName", "")
                ?.takeIf { it.isNotBlank() }
                ?: formatBytes(sizeBytes)
            val date = item.optString("PublishDate", item.optString("publishDate", ""))
            val category = item.optString("CategoryDesc", "").ifBlank { null }
            val seasons = info?.optJSONArray("seasons")?.let { ja ->
                (0 until ja.length()).mapNotNull { j -> ja.optInt(j).takeIf { it > 0 } }
            } ?: emptyList()

            // Битрейт: число (Кбит/с) или строка как есть
            val bitrate = when (val bv = info?.opt("bitrate")) {
                is Number -> if (bv.toDouble() > 0) "%.2f Мбит/с".format(bv.toDouble() / 1000.0) else ""
                is String -> bv.takeIf { it.isNotBlank() } ?: ""
                else -> ""
            }

            // Языки фильма: массив строк или объектов {name}
            val langs = item.optJSONArray("languages")?.let { ja ->
                (0 until ja.length()).mapNotNull { j ->
                    when (val el = ja.opt(j)) {
                        is String -> el.trim().takeIf { it.isNotEmpty() }
                        is JSONObject -> el.optString("name", "")
                            .ifBlank { el.optString("title", "") }
                            .trim()
                            .takeIf { it.isNotEmpty() }
                        else -> null
                    }
                }
            } ?: emptyList()

            TorrentResult(
                title = title,
                magnetUri = magnet,
                sizeName = sizeName,
                sizeBytes = sizeBytes,
                seeders = seeders,
                leechers = leechers,
                date = date,
                tracker = tracker,
                quality = quality,
                voices = voices,
                year = year,
                category = category,
                seasons = seasons,
                bitrate = bitrate,
                languages = langs
            )
        }.sortedByDescending { it.seeders }
    } catch (e: Exception) {
        emptyList()
    }

    // ================= Отправка на бокс =================

    suspend fun sendMagnet(
        boxAddress: String,
        magnet: String,
        title: String,
        posterUrl: String? = null
    ): SendResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("magnet", magnet)
                put("title", title)
                posterUrl?.let { put("posterUrl", it) }
            }.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("http://$boxAddress/magnet")
                .post(body)
                .build()

            try {
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        SendResult.Success
                    } else {
                        val errorText = response.body?.string()?.take(200)
                        SendResult.BoxError(
                            "Бокс ответил ошибкой ${response.code}" +
                                    (errorText?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                        )
                    }
                }
            } catch (e: Exception) {
                SendResult.Unreachable("Бокс недоступен по адресу $boxAddress")
            }
        }

    fun buildTorrentsPayload(
        query: String,
        posterUrl: String?,
        results: List<TorrentResult>,
        richView: Boolean = false
    ): String {
        val arr = JSONArray()
        results.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("title", t.title)
                    put("tracker", t.tracker)
                    put("sizeBytes", t.sizeBytes)
                    put("sizeName", t.sizeName)
                    put("seeders", t.seeders)
                    put("leechers", t.leechers)
                    put("date", t.date)
                    put("quality", t.quality ?: 0)
                    put("voices", t.voices.joinToString(", "))
                    put("magnet", t.magnetUri)
                    put("seasons", org.json.JSONArray(t.seasons))
                    put("languages", org.json.JSONArray(t.languages))
                }
            )
        }
        return JSONObject().apply {
            put("query", query)
            put("posterUrl", posterUrl ?: "")
            put("richView", richView)
            put("torrents", arr)
        }.toString()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.2f ГБ".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.0f МБ".format(bytes / 1_048_576.0)
        else -> "$bytes Б"
    }
}
