package com.cinemate.receiver

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Что нашла проверка обновления. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseNotes: String
)

/**
 * Автообновление ресивера через GitHub Releases.
 * REPO пуст -> проверка молчит (репозиторий ещё не создан).
 * Заполнить: "ник/CinemateReceiver". Тег релиза с versionCode ("12"/"v12"),
 * в активах — APK. Монорепо: берём свой APK по "receiver" в имени.
 */
class UpdateChecker(private val context: Context) {

    companion object {
        /** "владелец/репозиторий". Пусто = проверка отключена. */
        const val REPO = "vladm71-cinemate/cinemate"

        private const val TIMEOUT_MS = 8000
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    /** Актив в релизе: APK с "receiver" в имени (монорепо: два APK в релизе). */
    private fun isOurApk(name: String): Boolean =
        name.endsWith(".apk", ignoreCase = true) &&
                name.contains("receiver", ignoreCase = true)

    /** Проверка не чаще раза в сутки; найденное кэшируется. */
    fun checkDaily(): UpdateInfo? {
        val now = System.currentTimeMillis()
        val cached = prefs.getString("last_found", null)?.let { fromJson(it) }

        if (now - prefs.getLong("last_check_at", 0) < DAY_MS) return cached

        val fresh = checkForUpdate()
        prefs.edit()
            .putLong("last_check_at", now)
            .putString("last_found", fresh?.toJson())
            .apply()

        return fresh ?: cached
    }

    /** Тихая проверка. null = обновления нет / не удалось / отключена. */
    fun checkForUpdate(): UpdateInfo? {
        if (REPO.isBlank()) return null

        val currentCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).let {
                if (Build.VERSION.SDK_INT >= 28) it.longVersionCode
                else @Suppress("DEPRECATION") it.versionCode.toLong()
            }
        } catch (e: Exception) {
            return null
        }

        return try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "CinemateReceiver-UpdateCheck")

            if (conn.responseCode !in 200..299) return null

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name", "")
            val assets = json.optJSONArray("assets") ?: return null

            val releaseCode = Regex("(\\d+)\\D*$").find(tag.trim())
                ?.groupValues?.get(1)?.toLongOrNull() ?: return null
            if (releaseCode <= currentCode) return null

            val apk = (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { isOurApk(it.optString("name")) }
                ?: return null

            val url = apk.optString("browser_download_url", "")
            if (url.isBlank()) return null

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

    /** Скачать APK во внутреннюю папку. null = сбой. */
    fun downloadApk(info: UpdateInfo): File? = try {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val file = File(dir, "receiver-update-${info.versionCode}.apk")

        val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "CinemateReceiver-Update")

        if (conn.responseCode !in 200..299) null
        else {
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            file
        }
    } catch (e: Exception) {
        null
    }

    /** Запустить системный установщик. */
    fun installApk(file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    /** Удалить скачанные ранее APK. */
    fun cleanup() {
        runCatching {
            File(context.filesDir, "updates").listFiles()?.forEach { it.delete() }
        }
    }

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
