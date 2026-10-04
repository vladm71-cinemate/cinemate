package com.cinemate.app.util

import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Что нашла проверка обновления. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseNotes: String
)

/**
 * Автообновление через GitHub Releases.
 *
 * REPO пуст -> проверка молчит (репозиторий ещё не создан).
 * Заполнить: "ник/Cinemate". Релиз помечать тегом с versionCode ("12"/"v12"),
 * в активах — APK. В монорепо релиз содержит оба APK:
 * телефон берёт свой ("cinemate-…"), ресивер — свой ("…receiver…").
 * Сравнение строго по versionCode, не по имени версии.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** "владелец/репозиторий". Пусто = проверка отключена. */
        const val REPO = "vladm71-cinemate/cinemate"

        private const val TIMEOUT_MS = 8000
    }

    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    private val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * Актив в релизе: APK, чьё имя начинается с "cinemate" и НЕ содержит
     * "receiver" (монорепо: в релизе два APK).
     */
    private fun isOurApk(name: String): Boolean =
        name.endsWith(".apk", ignoreCase = true) &&
                name.startsWith("cinemate", ignoreCase = true) &&
                !name.contains("receiver", ignoreCase = true)

    /**
     * Проверка не чаще раза в сутки.
     * Внутри окна: сохранённый результат (если был). Вне окна: запрос в сеть;
     * при сбое сохранённый результат остаётся.
     */
    suspend fun checkDaily(): UpdateInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = prefs.getString("last_found", null)?.let { fromJson(it) }

        if (now - prefs.getLong("last_check_at", 0) < DAY_MS) {
            return@withContext cached
        }

        val fresh = checkForUpdate()
        prefs.edit()
            .putLong("last_check_at", now)
            .putString("last_found", fresh?.toJson())
            .apply()

        fresh ?: cached
    }

    /** Тихая проверка. null = обновления нет / проверка не удалась / отключена. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (REPO.isBlank()) return@withContext null

        val currentCode = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode
            else @Suppress("DEPRECATION") pi.versionCode.toLong()
        } catch (e: Exception) {
            return@withContext null
        }

        try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Cinemate-UpdateCheck")

            if (conn.responseCode !in 200..299) return@withContext null

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "")
            val assets = json.optJSONArray("assets") ?: return@withContext null

            // Код релиза: из тега "v12" / "12" / "2.3.1" берём последнее число
            val releaseCode = extractVersionCode(tag) ?: return@withContext null
            if (releaseCode <= currentCode) return@withContext null

            val apk = (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { isOurApk(it.optString("name")) }
                ?: return@withContext null

            val url = apk.optString("browser_download_url", "")
            if (url.isBlank()) return@withContext null

            UpdateInfo(
                versionName = json.optString("name", tag).ifBlank { tag },
                versionCode = releaseCode,
                downloadUrl = url,
                releaseNotes = json.optString("body", "").take(500)
            )
        } catch (e: Exception) {
            null
        }
    }

    /** "v12" -> 12; "2.3.1" -> 1 (последняя группа); "rc-3" -> 3. */
    private fun extractVersionCode(tag: String): Long? =
        Regex("(\\d+)\\D*$").find(tag.trim())?.groupValues?.get(1)?.toLongOrNull()

    // ---------- Скачивание и установка ----------

    /** Скачать APK во внутреннюю папку. Возвращает файл или null (сбой). */
    @Volatile var lastError: String = ""

    suspend fun downloadApk(info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        lastError = ""
        try {
            val dir = File(context.filesDir, "updates").apply { mkdirs() }
            val file = File(dir, "cinemate-update-${info.versionCode}.apk")

            val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = 120_000
            conn.setRequestProperty("User-Agent", "Cinemate-Update")

            val code = conn.responseCode
            if (code !in 200..299) {
                lastError = "HTTP $code"
                return@withContext null
            }

            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            val size = file.length()
            if (total > 0 && size < total) {
                lastError = "недокачано: $size из $total"
                file.delete()
                return@withContext null
            }
            Log.i("UpdateChecker", "скачано ${size / (1024 * 1024)} МБ -> ${file.name}")
            file
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.w("UpdateChecker", "downloadApk error: ${e.message}")
            null
        }
    }

    /** Запустить системный установщик для скачанного APK. */
    fun installApk(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() == 0L) {
                lastError = "файл не существует"
                return false
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Log.i("UpdateChecker", "installApk: uri=${'$'}uri, size=${'$'}{file.length()}")

            val installer = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(installer)
                Log.i("UpdateChecker", "installApk: установщик запущен")
            } catch (e: Exception) {
                Log.w("UpdateChecker", "INSTALL_PACKAGE не сработал (${e.message}) — fallback VIEW")
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(view)
            }
            true
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e("UpdateChecker", "installApk failed: ${e.message}")
            false
        }
    }


    /** Удалить скачанные ранее APK (при новом запуске). */
    fun cleanup() {
        runCatching {
            File(context.filesDir, "updates").listFiles()?.forEach { it.delete() }
        }
    }

    // ---------- Кэш проверки ----------

    private fun UpdateInfo.toJson(): String =
        JSONObject()
            .put("versionName", versionName)
            .put("versionCode", versionCode)
            .put("downloadUrl", downloadUrl)
            .put("releaseNotes", releaseNotes)
            .toString()

    private fun fromJson(s: String): UpdateInfo? = try {
        val o = JSONObject(s)
        UpdateInfo(
            versionName = o.getString("versionName"),
            versionCode = o.getLong("versionCode"),
            downloadUrl = o.getString("downloadUrl"),
            releaseNotes = o.optString("releaseNotes", "")
        )
    } catch (e: Exception) {
        null
    }
}
