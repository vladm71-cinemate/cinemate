package com.cinemate.app.data.remote

import android.util.Log
import com.cinemate.app.data.local.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Управление прокси для TMDb: API и картинки — раздельные цепочки фейловера.
 *
 * Свои прокси — СПИСОК (Настройки → Прокси TMDb): можно добавить сколько нужно.
 * Порядок кандидатов: активный -> прошлый рабочий -> свои -> вшитые.
 * Найденный живой закрепляется, пока работает; перестал — перебор продолжается,
 * новый запоминается (и в DataStore: при следующем запуске проверяется первым).
 *
 * Режимы (TokenStore.proxyMode):
 *  auto   — напрямую; сбой (IOException) -> мгновенный ретрай через запасные;
 *           живой путь запоминается на сессию
 *  always — сразу через запасные (+ прогрев при старте приложения)
 *  off    — только напрямую
 */
@Singleton
class TmdbProxyManager @Inject constructor(
    private val tokenStore: TokenStore
) {

    companion object {
        private const val TAG = "TmdbProxy"

        const val TMDB_API_HOST = "api.themoviedb.org"
        const val TMDB_IMAGE_HOST = "image.tmdb.org"

        const val MODE_AUTO = "auto"
        const val MODE_ALWAYS = "always"
        const val MODE_OFF = "off"

        private val API_BUILTIN = listOf(
            ApiCandidate("https", "apn-latest.onrender.com", ApiFormat.FULL_URL),
            ApiCandidate("https", "tmdb.cub.red", ApiFormat.HOST_PATH)
        )

        private val IMAGE_MIRRORS = listOf(
            "imagetmdb.com",
            "nl.imagetmdb.com",
            "de.imagetmdb.com",
            "pl.imagetmdb.com"
        )

        private const val IMAGE_FALLBACK_WSRV = "wsrv.nl"

        /** Тестовая картинка (маленький постер TMDb). */
        private const val TEST_IMAGE_PATH = "/t/p/w92/9E2y5Q7WlCVNEhP5GiVTjhEhx1o.jpg"

        /** Тестовый запрос API (без ключа — 401 от TMDb тоже означает «прокси жив»). */
        private const val TEST_API_PATH = "/3/configuration"
    }

    enum class ApiFormat {
        FULL_URL,
        HOST_PATH
    }

    data class ApiCandidate(
        val scheme: String,
        val host: String,
        val format: ApiFormat
    ) {
        fun buildUrl(original: HttpUrl): String = when (format) {
            ApiFormat.FULL_URL -> "$scheme://$host/$original"
            ApiFormat.HOST_PATH -> buildString {
                append(scheme).append("://").append(host)
                append('/').append(original.host)
                append(original.encodedPath)
                original.encodedQuery?.let { q -> append('?').append(q) }
            }
        }

        override fun toString(): String = "$host (${format.name.lowercase()})"
    }

    /** Результат теста одной точки. */
    data class ProxyTestResult(
        val name: String,
        val ok: Boolean,
        val detail: String
    )

    // ---------- Состояние ----------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    @Volatile
    private var mode: String = MODE_AUTO

    @Volatile
    private var customApiCandidates: List<ApiCandidate> = emptyList()

    @Volatile
    private var lastGoodHost: String? = null

    @Volatile
    private var currentApiProxy: ApiCandidate? = null

    @Volatile
    private var currentImageHost: String? = null

    @Volatile
    private var imageLastGood: String? = null

    private fun reloadSettings() {
        runBlocking {
            mode = tokenStore.proxyMode.first()
            lastGoodHost = tokenStore.proxyLastGood.first()
            imageLastGood = tokenStore.imageLastGood.first()
            migrateLegacyProxyHost()
            customApiCandidates = parseCustomList(tokenStore.proxyCustomList.first())
        }
    }

    /**
     * Разовая миграция: старое одиночное поле (proxyHost) -> список.
     * Выполняется при каждой инициализации, но перенос делает один раз:
     * после переноса поле очищается.
     */
    private suspend fun migrateLegacyProxyHost() {
        val legacy = tokenStore.proxyHost.first()
        if (legacy.isNullOrBlank()) return

        val current = parseCustomList(tokenStore.proxyCustomList.first())
        if (current.isEmpty()) {
            val origin = if (legacy.contains("://")) legacy.trimEnd('/') else "https://$legacy"
            tokenStore.saveProxyCustomList(org.json.JSONArray(listOf(origin)).toString())
            Log.i(TAG, "миграция: старый прокси $legacy -> список")
        }
        tokenStore.saveProxyHost(null)
    }

    /** "https://host" / "http://host" / "host" (https по умолчанию). */
    private fun candidateFrom(raw: String): ApiCandidate? {
        val cleaned = raw.trim().trimEnd('/')
        if (cleaned.isBlank()) return null
        return if (cleaned.contains("://")) {
            val parts = cleaned.split("://", limit = 2)
            val scheme = if (parts[0] == "http") "http" else "https"
            if (parts.getOrNull(1).isNullOrBlank()) null
            else ApiCandidate(scheme, parts[1], ApiFormat.FULL_URL)
        } else {
            ApiCandidate("https", cleaned, ApiFormat.FULL_URL)
        }
    }

    private fun parseCustomList(json: String): List<ApiCandidate> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i -> candidateFrom(arr.optString(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            reloadSettings()
            initialized = true
        }
    }

    fun invalidate() {
        synchronized(this) {
            initialized = false
            currentApiProxy = null
            currentImageHost = null
        }
    }

    fun isTmdbHost(host: String): Boolean =
        host == TMDB_API_HOST || host == TMDB_IMAGE_HOST

    // ================= Порядок кандидатов =================

    /**
     * Кандидаты API-прокси: активный -> прошлый рабочий -> свои -> вшитые.
     * Дубликаты по host+format убраны.
     */
    private fun apiCandidates(): List<ApiCandidate> {
        val list = mutableListOf<ApiCandidate>()
        currentApiProxy?.let { list.add(it) }
        if (list.isEmpty()) {
            lastGoodHost?.let { good ->
                (customApiCandidates + API_BUILTIN).firstOrNull { it.host == good }
                    ?.let { list.add(it) }
            }
        }
        customApiCandidates.forEach { c ->
            if (list.none { it.host == c.host && it.format == c.format }) list.add(c)
        }
        API_BUILTIN.forEach { b ->
            if (list.none { it.host == b.host && it.format == b.format }) list.add(b)
        }
        return list
    }

    // ================= Прогрев при старте (режим «Всегда») =================

    /**
     * Фоновая проверка при старте приложения (режим always):
     * прошлый рабочий жив? -> используем; мёртв -> ищем первого живого и запоминаем.
     * В режиме auto не вмешивается: там прямой путь приоритетен, прокси
     * подбирается лениво при первом сбое (и тоже начинается с прошлого рабочего).
     */
    fun warmUpAsync() {
        scope.launch {
            ensureInit()
            // Прогрев и в auto: если есть прошлый рабочий — проверим его заранее,
            // чтобы первый запрос не тратил время на прямой таймаут.
            if (mode == MODE_OFF) return@launch
            if (mode == MODE_ALWAYS && currentApiProxy != null) return@launch
            if (mode == MODE_AUTO && lastGoodHost == null) return@launch

            for (candidate in apiCandidates()) {
                if (testApiCandidate(candidate)) {
                    currentApiProxy = candidate
                    runCatching { tokenStore.saveProxyLastGood(candidate.host) }
                    Log.i(TAG, "прогрев: рабочий прокси ${candidate.host}")
                    return@launch
                }
            }
            Log.i(TAG, "прогрев: живых API-прокси не найдено")
        }
    }

    private suspend fun testApiCandidate(candidate: ApiCandidate): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val testUrl = candidate.buildUrl(
                    HttpUrl.Builder()
                        .scheme("https")
                        .host(TMDB_API_HOST)
                        .addPathSegments(TEST_API_PATH.trimStart('/'))
                        .build()
                )
                val request = Request.Builder().url(testUrl).get().build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
                client.newCall(request).execute().use { it.code in 200..499 }
            } catch (e: Exception) {
                false
            }
        }

    // ================= Интерцептор (рабочий путь) =================

    fun intercept(chain: Interceptor.Chain): Response {
        ensureInit()

        val request = chain.request()
        return when (request.url.host) {
            TMDB_API_HOST -> interceptApi(chain, request)
            TMDB_IMAGE_HOST -> interceptImage(chain, request)
            else -> chain.proceed(request)
        }
    }

    private fun interceptApi(chain: Interceptor.Chain, request: Request): Response {
        return when (mode) {
            MODE_OFF -> chain.proceed(request)

            MODE_ALWAYS -> proceedViaApiProxy(chain, request)

            else -> {
                val current = currentApiProxy
                if (current == null && lastGoodHost != null) {
                    // Быстрый старт: прошлый рабочий прокси пробуем ПЕРВЫМ,
                    // до долгого прямого таймаута. Ответил — закрепляем.
                    val candidate = apiCandidates().firstOrNull { it.host == lastGoodHost }
                    if (candidate != null) {
                        try {
                            val response = proceedVia(chain, request, candidate)
                            if (response.code < 500) {
                                setCurrentApi(candidate)
                                return response
                            }
                            response.close()
                        } catch (e: IOException) {
                            Log.w(TAG, "lastGood ${candidate.host} не отвечает — на прямой")
                        }
                    }
                }
                if (current == null) {
                    try {
                        chain.proceed(request)
                    } catch (e: IOException) {
                        Log.w(TAG, "прямой запрос к TMDb API не прошёл (${e.message}) — пробую прокси")
                        proceedViaApiProxy(chain, request)
                    }
                } else {
                    try {
                        proceedVia(chain, request, current)
                    } catch (e: IOException) {
                        Log.w(TAG, "API-прокси $current не отвечает (${e.message}) — возвращаюсь на прямой")
                        currentApiProxy = null
                        try {
                            chain.proceed(request)
                        } catch (e2: IOException) {
                            proceedViaApiProxy(chain, request)
                        }
                    }
                }
            }
        }
    }

    private fun proceedViaApiProxy(chain: Interceptor.Chain, request: Request): Response {
        val candidates = apiCandidates()
        var lastError: IOException? = null

        for (candidate in candidates) {
            try {
                val response = proceedVia(chain, request, candidate)
                if (response.code >= 500) {
                    response.close()
                    continue
                }
                setCurrentApi(candidate)
                return response
            } catch (e: IOException) {
                lastError = e
                continue
            }
        }
        throw lastError ?: IOException("ни один прокси TMDb API не ответил")
    }

    /** Закрепить рабочий прокси на сессию и сохранить как «прошлый рабочий». */
    private fun setCurrentApi(candidate: ApiCandidate) {
        currentApiProxy = candidate
        scope.launch { runCatching { tokenStore.saveProxyLastGood(candidate.host) } }
    }

    private fun proceedVia(
        chain: Interceptor.Chain,
        request: Request,
        candidate: ApiCandidate
    ): Response {
        val proxied = request.newBuilder()
            .url(candidate.buildUrl(request.url))
            .build()
        return chain.proceed(proxied)
    }

    // ================= Картинки =================

    private fun interceptImage(chain: Interceptor.Chain, request: Request): Response {
        return when (mode) {
            MODE_OFF -> chain.proceed(request)

            MODE_ALWAYS -> proceedViaImageMirror(chain, request)

            else -> {
                val current = currentImageHost
                if (current == null && imageLastGood != null) {
                    // Быстрый старт картинок: прошлое живое зеркало ПЕРВЫМ,
                    // до прямого таймаута.
                    try {
                        val response = proceedViaImageHost(chain, request, imageLastGood!!)
                        currentImageHost = imageLastGood
                        return response
                    } catch (e: IOException) {
                        Log.w(TAG, "imageLastGood $imageLastGood не отвечает — на прямой")
                    }
                }
                if (current == null) {
                    try {
                        chain.proceed(request)
                    } catch (e: IOException) {
                        Log.w(TAG, "прямая загрузка картинки не прошла (${e.message}) — пробую зеркала")
                        proceedViaImageMirror(chain, request)
                    }
                } else {
                    try {
                        proceedViaImageHost(chain, request, current)
                    } catch (e: IOException) {
                        Log.w(TAG, "зеркало картинок $current не отвечает — возвращаюсь на прямой")
                        currentImageHost = null
                        try {
                            chain.proceed(request)
                        } catch (e2: IOException) {
                            proceedViaImageMirror(chain, request)
                        }
                    }
                }
            }
        }
    }

    private fun proceedViaImageMirror(chain: Interceptor.Chain, request: Request): Response {
        var lastError: IOException? = null

        for (mirror in IMAGE_MIRRORS) {
            if (mirror == currentImageHost) continue
            try {
                val response = proceedViaImageHost(chain, request, mirror)
                currentImageHost = mirror
                scope.launch { runCatching { tokenStore.saveImageLastGood(mirror) } }
                return response
            } catch (e: IOException) {
                lastError = e
                continue
            }
        }

        try {
            val response = proceedViaImageHost(chain, request, "wsrv")
            currentImageHost = "wsrv"
            scope.launch { runCatching { tokenStore.saveImageLastGood("wsrv") } }
            return response
        } catch (e: IOException) {
            lastError = e
        }

        throw lastError ?: IOException("ни одно зеркало картинок TMDb не ответило")
    }

    private fun proceedViaImageHost(
        chain: Interceptor.Chain,
        request: Request,
        hostKind: String
    ): Response {
        val path = request.url.encodedPath
        val query = request.url.encodedQuery

        val url = when (hostKind) {
            "wsrv" -> buildString {
                append("https://").append(IMAGE_FALLBACK_WSRV)
                append("/?url=image.tmdb.org").append(path)
                query?.let { q -> append('&').append(q) }
            }
            else -> buildString {
                append("https://").append(hostKind).append(path)
                query?.let { q -> append('?').append(q) }
            }
        }

        val proxied = request.newBuilder().url(url).build()
        return chain.proceed(proxied)
    }

    // ================= Тест прокси (кнопка в настройках) =================

    /**
     * Проверить цепочки прокси принудительно (вне зависимости от режима).
     * Отчёт: API-прокси по кандидатам (активный -> прошлый -> свои -> вшитые)
     * + зеркала картинок, с временем ответа.
     */
    suspend fun testProxies(): List<ProxyTestResult> = withContext(Dispatchers.IO) {
        ensureInit()
        val results = mutableListOf<ProxyTestResult>()

        // ---------- Тест API-кандидатов ----------
        for (candidate in apiCandidates()) {
            val start = System.currentTimeMillis()
            try {
                val testUrl = candidate.buildUrl(
                    HttpUrl.Builder()
                        .scheme("https")
                        .host(TMDB_API_HOST)
                        .addPathSegments(TEST_API_PATH.trimStart('/'))
                        .build()
                )
                val request = Request.Builder().url(testUrl).get().build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                client.newCall(request).execute().use { response ->
                    val ms = System.currentTimeMillis() - start
                    // 200-499 = прокси жив (401 от TMDb без ключа — норма)
                    val ok = response.code in 200..499
                    results.add(
                        ProxyTestResult(
                            name = "API: $candidate",
                            ok = ok,
                            detail = if (ok) "✅ ${response.code} (${ms} мс)"
                            else "❌ код ${response.code} (${ms} мс)"
                        )
                    )
                }
            } catch (e: Exception) {
                results.add(
                    ProxyTestResult(
                        name = "API: $candidate",
                        ok = false,
                        detail = "❌ ${e.javaClass.simpleName}: ${e.message ?: "нет связи"}"
                    )
                )
            }
        }

        // ---------- Тест зеркал картинок ----------
        for (mirror in IMAGE_MIRRORS) {
            val start = System.currentTimeMillis()
            try {
                val testUrl = "https://$mirror$TEST_IMAGE_PATH"
                val request = Request.Builder().url(testUrl).get().build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                client.newCall(request).execute().use { response ->
                    val ms = System.currentTimeMillis() - start
                    val ok = response.code in 200..299
                    results.add(
                        ProxyTestResult(
                            name = "Картинки: $mirror",
                            ok = ok,
                            detail = if (ok) "✅ 200 (${ms} мс)"
                            else "❌ код ${response.code} (${ms} мс)"
                        )
                    )
                }
            } catch (e: Exception) {
                results.add(
                    ProxyTestResult(
                        name = "Картинки: $mirror",
                        ok = false,
                        detail = "❌ ${e.javaClass.simpleName}: ${e.message ?: "нет связи"}"
                    )
                )
            }
        }

        // ---------- Тест wsrv.nl ----------
        val startWsrv = System.currentTimeMillis()
        try {
            val testUrl = "https://$IMAGE_FALLBACK_WSRV/?url=image.tmdb.org$TEST_IMAGE_PATH"
            val request = Request.Builder().url(testUrl).get().build()
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                val ms = System.currentTimeMillis() - startWsrv
                val ok = response.code in 200..299
                results.add(
                    ProxyTestResult(
                        name = "Картинки: wsrv.nl (резерв)",
                        ok = ok,
                        detail = if (ok) "✅ 200 (${ms} мс)"
                        else "❌ код ${response.code} (${ms} мс)"
                    )
                )
            }
        } catch (e: Exception) {
            results.add(
                ProxyTestResult(
                    name = "Картинки: wsrv.nl (резерв)",
                    ok = false,
                    detail = "❌ ${e.javaClass.simpleName}: ${e.message ?: "нет связи"}"
                )
            )
        }

        results
    }
}

/**
 * OkHttp-интерцептор: всё, что идёт на api.themoviedb.org и image.tmdb.org,
 * прогоняет через логику TmdbProxyManager.
 */
@Singleton
class TmdbProxyInterceptor @Inject constructor(
    private val manager: TmdbProxyManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = manager.intercept(chain)
}
