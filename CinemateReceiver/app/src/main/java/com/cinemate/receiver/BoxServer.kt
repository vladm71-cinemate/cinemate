package com.cinemate.receiver

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class BoxServer(
    private val context: Context,
    port: Int,
    private val onLog: (String) -> Unit
) : NanoHTTPD(port) {

    companion object {
        const val DEFAULT_PORT = 8080
        val PORT_CANDIDATES = intArrayOf(8080, 8081, 8085, 8096, 8100)
        const val TORRSERVE_PACKAGE = "ru.yourok.torrserve"
        const val TORRSERVE_API_PORT = 8090

        @Volatile
        var actualPort: Int = DEFAULT_PORT
            private set

        fun setActualPort(port: Int) {
            actualPort = port
        }
    }

    @Volatile
    private var appsJsonCache: String? = null

    @Volatile
    private var appsCount: Int = 0

    init {
        thread(name = "apps-cache-builder", isDaemon = true) {
            buildAppsJson()
            onLog("кэш приложений готов ($appsCount)")
        }
    }

    private fun buildAppsJson() {
        val pm = context.packageManager
        val arr = JSONArray()
        pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .forEach { app ->
                arr.put(
                    JSONObject().apply {
                        put("package", app.packageName)
                        put("label", pm.getApplicationLabel(app).toString())
                    }
                )
            }
        appsCount = arr.length()
        appsJsonCache = JSONObject().apply { put("apps", arr) }.toString()
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/icon" -> handleIcon(session)
            "/ping" -> {
                onLog("ping <- ${session.remoteIpAddress}")
                newFixedLengthResponse(Response.Status.OK, "text/plain", "pong")
            }
            "/discover" -> {
                val json = JSONObject().apply {
                    put("app", "cinemate-receiver")
                    put("port", actualPort)
                }
                newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }
            "/list" -> handleListText()
            "/listjson" -> handleListJson()
            "/open" -> handleOpen(session)
            "/magnet" -> handleMagnet(session)
            "/torrents" -> handleTorrents(session)
            "/stop" -> handleStop()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    /**
     * Иконка приложения: GET /icon?pkg=...
     * PNG 96x96 в ответе. 404 — пакета нет/нет иконки.
     */
    private fun handleIcon(session: IHTTPSession): Response {
        val pkg = session.parameters["pkg"]?.firstOrNull()?.trim() ?: ""
        if (pkg.isBlank()) return badRequest("pkg обязателен")

        val drawable = try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "no icon")
        }

        return try {
            val size = 96
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)

            val out = java.io.ByteArrayOutputStream()
            android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)

            newChunkedResponse(Response.Status.OK, "image/png", java.io.ByteArrayInputStream(out.toByteArray()))
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "icon error")
        }
    }

    private fun handleListText(): Response {
        val json = appsJsonCache ?: run { buildAppsJson(); appsJsonCache ?: "{}" }
        return try {
            val arr = JSONObject(json).optJSONArray("apps") ?: JSONArray()
            val text = (0 until arr.length()).joinToString("\n") { i ->
                val o = arr.getJSONObject(i)
                "${o.optString("package")}  =  ${o.optString("label")}"
            }
            onLog("list: выдано $appsCount приложений")
            newFixedLengthResponse(Response.Status.OK, "text/plain", text)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "list error")
        }
    }

    private fun handleListJson(): Response {
        val json = appsJsonCache
        if (json == null) {
            buildAppsJson()
        }
        onLog("listjson: выдано $appsCount приложений")
        return newFixedLengthResponse(
            Response.Status.OK, "application/json",
            appsJsonCache ?: "{\"apps\":[]}"
        )
    }

    private fun handleOpen(session: IHTTPSession): Response {
        val bodyMap = HashMap<String, String>()
        try {
            session.parseBody(bodyMap)
        } catch (e: Exception) {
            return badRequest("не удалось прочитать тело: ${e.message}")
        }
        val json = JSONObject(bodyMap["postData"] ?: "{}")

        val packageName = json.optString("package", "")
        val restart = json.optBoolean("restart", false)

        if (packageName.isBlank()) {
            return badRequest("package обязателен")
        }

        val label = AppLauncher.label(context, packageName)
        onLog("open: [$packageName] ($label) restart=$restart")

        val launched = AppLauncher.launch(context, packageName, restart)

        return if (launched) {
            newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain",
                "Приложение $packageName не найдено на боксе"
            )
        }
    }

    private fun handleMagnet(session: IHTTPSession): Response {
        val bodyMap = HashMap<String, String>()
        try {
            session.parseBody(bodyMap)
        } catch (e: Exception) {
            return badRequest("не удалось прочитать тело: ${e.message}")
        }
        val json = JSONObject(bodyMap["postData"] ?: "{}")

        val magnet = json.optString("magnet", "")
        val title = json.optString("title", "").ifBlank { "torrent" }
        val requestedPackage = json.optString("package", "").ifBlank { null }
        val posterUrl = json.optString("posterUrl", "").ifBlank { null }

        if (magnet.isBlank() || !magnet.startsWith("magnet:")) {
            return badRequest("требуется корректный magnet")
        }

        // Постер последней раздачи — для экрана ресивера
        posterUrl?.let { LastPoster.set(it, title) }

        onLog("magnet: «$title»${requestedPackage?.let { " → $it" } ?: ""}")

        val launched = TorrServeLauncher.launch(context, magnet, requestedPackage, onLog)

        return if (launched) {
            newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain",
                "Торрент-клиент не найден или движок не запущен"
            )
        }
    }

    private fun handleTorrents(session: IHTTPSession): Response {
        val bodyMap = HashMap<String, String>()
        try {
            session.parseBody(bodyMap)
        } catch (e: Exception) {
            return badRequest("не удалось прочитать тело: ${e.message}")
        }
        val json = JSONObject(bodyMap["postData"] ?: "{}")

        val query = json.optString("query", "").ifBlank { "Раздачи" }
        val posterUrl = json.optString("posterUrl", "")
        val clientPackage = json.optString("package", "").ifBlank { null }
        val arr: JSONArray = json.optJSONArray("torrents") ?: JSONArray()
        if (arr.length() == 0) {
            return badRequest("пустой список torrents")
        }

        val items = mutableListOf<TorrentItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val magnet = o.optString("magnet", "")
            val title = o.optString("title", "").ifBlank { "раздача" }
            if (!magnet.startsWith("magnet:")) continue
            val seasons = o.optJSONArray("seasons")?.let { ja ->
                    (0 until ja.length()).mapNotNull { j ->
                        val v = ja.optInt(j, -1)
                        if (v > 0) v else null
                    }
                } ?: emptyList()
            val langs = o.optJSONArray("languages")?.let { ja ->
                (0 until ja.length()).mapNotNull { j ->
                    when (val el = ja.opt(j)) {
                        is String -> el.trim().takeIf { it.isNotEmpty() }
                        is JSONObject -> el.optString("name", "").trim().takeIf { it.isNotEmpty() }
                        else -> null
                    }
                }
            } ?: emptyList()
                items.add(
                TorrentItem(
                    title = title,
                    tracker = o.optString("tracker", ""),
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    sizeName = o.optString("sizeName", ""),
                    seeders = o.optInt("seeders", 0),
                    leechers = o.optInt("leechers", 0),
                    date = o.optString("date", ""),
                    quality = o.optInt("quality", 0),
                    seasons = seasons,
                    languages = langs,
                    voices = o.optString("voices", ""),
                    magnet = magnet
                )
            )
        }

        if (items.isEmpty()) {
            return badRequest("в списке нет ни одной раздачи с magnet")
        }

        // Постер последнего запроса — для экрана ресивера
        if (posterUrl.isNotBlank()) {
            LastPoster.set(posterUrl, query)
        }

        TorrentList.set(query, posterUrl, clientPackage, items, json.optBoolean("richView", false))
        onLog("torrents: «$query» — ${items.size} раздач, открываю на ТВ")

        return try {
            val intent = Intent(context, TorrentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "text/plain",
                "не удалось открыть экран списка: ${e.message}"
            )
        }
    }

    private fun handleStop(): Response {
        onLog("stop: сворачиваю на рабочий стол")
        AppLauncher.closeAllLaunched(context)
        BoxCleaner.goHome(context)
        return newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
    }

    private fun badRequest(message: String): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", message)
}

/** Постер и название последней раздачи — для заставки экрана ресивера. */
object LastPoster {
    @Volatile var url: String = ""
    @Volatile var title: String = ""

    fun set(url: String, title: String) {
        this.url = url
        this.title = title
    }
}

object BoxCleaner {

    fun goHome(context: Context) {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(home)
        } catch (e: Exception) { }
    }

    fun closeBackgroundApps(context: Context, except: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = context.packageManager
            val self = context.packageName
            for (app in pm.getInstalledApplications(0)) {
                val pkg = app.packageName
                if (pkg == except || pkg == self) continue
                val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: continue
                if (launchIntent.categories?.contains(Intent.CATEGORY_HOME) == true) continue
                try {
                    am.killBackgroundProcesses(pkg)
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }
    }
}

object AppLauncher {

    /** Наши приложения (порядок запуска). */
    private val launchedStack = mutableListOf<String>()

    fun label(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }

    /**
     * Запуск через делегата. Предыдущие «наши» приложения при включённом
     * тумблере закрываются через 3 c (killBackgroundProcesses — фоновые
     * процессы; их карточки исчезают из недавних на большинстве прошивок).
     */
    fun launch(context: Context, packageName: String, restart: Boolean): Boolean {
        val pm = context.packageManager
        val installed = try {
            pm.getPackageInfo(packageName, 0); true
        } catch (e: Exception) { false }
        if (!installed) return false

        synchronized(launchedStack) {
            launchedStack.remove(packageName)
            launchedStack.add(packageName)
        }

        try {
            context.startActivity(LaunchDelegate.intent(context, packageName))
        } catch (e: Exception) {
            android.util.Log.w("AppLauncher", "делегат не стартовал: ${e.message}")
            return false
        }

        if (restart) {
            thread(name = "launch-cleanup") {
                Thread.sleep(3000)
                closePrevious(context, except = packageName)
            }
        }
        return true
    }

    /** Закрыть все наши, кроме except. */
    fun closePrevious(context: Context, except: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val targets = synchronized(launchedStack) { launchedStack.filter { it != except } }
        for (pkg in targets) {
            try { am.killBackgroundProcesses(pkg) } catch (e: Exception) { }
            android.util.Log.i("AppLauncher", "закрыт фоновый $pkg")
        }
        synchronized(launchedStack) { launchedStack.removeAll { it != except } }
    }

    /** Закрыть все наши (Стоп на ТВ). */
    fun closeAllLaunched(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val targets = synchronized(launchedStack) { launchedStack.toList() }
        for (pkg in targets) {
            try { am.killBackgroundProcesses(pkg) } catch (e: Exception) { }
        }
        synchronized(launchedStack) { launchedStack.clear() }
    }
}

object TorrServeLauncher {

    fun launch(
        context: Context,
        magnet: String,
        requestedPackage: String?,
        onLog: (String) -> Unit
    ): Boolean {
        // Уровень 1: HTTP API MatriX на localhost — Activity-ограничений нет.
        val hash = addTorrentViaApi(magnet, onLog)
        if (hash != null) {
            // Показать MatriX через делегата (foreground-механика)
            try {
                context.startActivity(LaunchDelegate.intent(context, BoxServer.TORRSERVE_PACKAGE))
            } catch (e: Exception) { }
            onLog("раздача добавлена через API (хэш ${hash.take(8)}…)")
            return true
        }

        // Уровень 2: magnet через делегата (интент из фона прошивки блокируют).
        // Перебор клиентов делает сам делегат, где исключения значимы.
        try {
            val i = Intent(context, LaunchDelegate::class.java).apply {
                putExtra(LaunchDelegate.EXTRA_MAGNET, magnet)
                requestedPackage?.let { putExtra(LaunchDelegate.EXTRA_PACKAGE, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
            onLog("magnet отправлен через делегата")
            return true
        } catch (e: Exception) {
            onLog("делегат не стартовал: ${e.message}")
        }

        onLog("движок MatriX (8090) не отвечает — запусти MatriX и нажми Start")
        return false
    }

    /** Добавить раздачу через HTTP API MatriX (порт 8090). */
    private fun addTorrentViaApi(magnet: String, onLog: (String) -> Unit): String? {
        try {
            val conn = URL("http://127.0.0.1:${BoxServer.TORRSERVE_API_PORT}/torrent/add")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(conn.outputStream).use {
                it.write(JSONObject().apply { put("link", magnet) }.toString())
            }
            val code = conn.responseCode
            if (code in 200..299) {
                onLog("API add: $code")
                return Regex("xt=urn:btih:([A-Fa-f0-9]{40}|[A-Fa-f0-9]{32})")
                    .find(magnet)?.groupValues?.get(1)?.uppercase() ?: "unknown"
            } else {
                onLog("API add: код $code")
            }
        } catch (e: Exception) {
            onLog("API add (json): ${e.message}")
        }

        try {
            val conn = URL("http://127.0.0.1:${BoxServer.TORRSERVE_API_PORT}/torrent/add")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            OutputStreamWriter(conn.outputStream).use {
                it.write("link=${java.net.URLEncoder.encode(magnet, "UTF-8")}")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                return Regex("xt=urn:btih:([A-Fa-f0-9]{40}|[A-Fa-f0-9]{32})")
                    .find(magnet)?.groupValues?.get(1)?.uppercase() ?: "unknown"
            } else {
                onLog("API add (form): код $code")
            }
        } catch (e: Exception) {
            onLog("API add (form): ${e.message}")
        }

        return null
    }
}
