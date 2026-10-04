package com.cinemate.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Результат отправки команды на бокс. */
sealed interface SendResult {
    data object Success : SendResult
    data class Unreachable(val message: String) : SendResult
    data class BoxError(val message: String) : SendResult
}

/** Приложение на боксе (из списка). */
data class BoxApp(
    val packageName: String,
    val label: String
)

/** Результат получения списка приложений с бокса. */
sealed interface AppListResult {
    data class Success(val apps: List<BoxApp>) : AppListResult
    data class Error(val message: String) : AppListResult
}

/**
 * Клиент приёмника на TV Box.
 * Каскад адресов делает вызывающий код (ViewModel) — здесь методы на один адрес.
 */
@Singleton
class BoxClient @Inject constructor() {

    /** Для быстрых команд: ping, open, magnet, torrents, stop. */
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** Для /listjson: ответ из кэша ресивера мгновенный, но запас по таймауту оставляем. */
    private val httpSlow: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Проверка доступности бокса. */
    suspend fun ping(boxAddress: String): SendResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$boxAddress/ping")
            .get()
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) SendResult.Success
                else SendResult.BoxError("Бокс ответил ошибкой ${response.code}")
            }
        } catch (e: Exception) {
            SendResult.Unreachable("Бокс недоступен по адресу $boxAddress")
        }
    }

    /**
     * Список установленных приложений с бокса.
     * Только /listjson — ресивер отдаёт JSON из кэша мгновенно.
     * Парсер толерантный: label | link | package как подпись.
     */
    suspend fun fetchAppList(boxAddress: String): AppListResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$boxAddress/listjson")
            .get()
            .build()

        try {
            httpSlow.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppListResult.Error(
                        "$boxAddress: код ${response.code} (обновите Cinemate Receiver на боксе)"
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext AppListResult.Error("$boxAddress: пустой ответ")

                val json = JSONObject(body)
                val arr = json.optJSONArray("apps")
                    ?: return@withContext AppListResult.Error("$boxAddress: неверный формат ответа")

                val apps = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val pkg = o.optString("package", "").trim()
                    if (pkg.isBlank()) null
                    else BoxApp(
                        pkg,
                        o.optString("label", o.optString("link", pkg))
                    )
                }

                if (apps.isEmpty()) {
                    AppListResult.Error("$boxAddress: список приложений пуст")
                } else {
                    AppListResult.Success(apps)
                }
            }
        } catch (e: Exception) {
            AppListResult.Error(
                "$boxAddress — ${e.javaClass.simpleName}: ${e.message ?: "нет связи"}"
            )
        }
    }

    /**
     * «Кнопка пульта»: запустить приложение на боксе.
     * Тело: { "package": "...", "restart": true|false }
     */
    suspend fun sendOpen(
        boxAddress: String,
        packageName: String,
        restart: Boolean
    ): SendResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("package", packageName)
            put("restart", restart)
        }.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("http://$boxAddress/open")
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
            SendResult.Unreachable(
                "Бокс недоступен по адресу $boxAddress. " +
                        "Проверьте, что приёмник запущен и оба устройства в одной сети."
            )
        }
    }

    /**
     * Отправка magnet на бокс.
     * posterUrl — опционально: ресивер покажет постер на своей заставке.
     */
    suspend fun sendMagnet(
        boxAddress: String,
        magnet: String,
        title: String,
        posterUrl: String? = null
    ): SendResult = withContext(Dispatchers.IO) {
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

    /** Отправить список раздач на ТВ (режим «список на ТВ»). */
    suspend fun sendTorrents(
        boxAddress: String,
        payloadJson: String,
        torrentClientPackage: String? = null
    ): SendResult = withContext(Dispatchers.IO) {
        val json = JSONObject(payloadJson)
        torrentClientPackage?.let { json.put("package", it) }
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("http://$boxAddress/torrents")
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

    /** «Стоп на ТВ»: свернуть текущее приложение на рабочий стол бокса. */
    suspend fun sendStop(boxAddress: String): SendResult = withContext(Dispatchers.IO) {
        val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("http://$boxAddress/stop")
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
}