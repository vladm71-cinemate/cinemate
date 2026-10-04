package com.cinemate.app.data.repository

import android.content.Context
import com.cinemate.app.data.local.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Иконки приложений бокса для кнопок карточки.
 * Хранение: filesDir/icons/<package>.png (по package — пере-назначение
 * на другую программу тянет другой файл; снятие — файл чистится).
 * Загрузка — только в момент назначения/пере-назначения (4 запроса максимум).
 */
@Singleton
class IconStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun dir(): File = File(context.filesDir, "icons").apply { mkdirs() }

    fun iconFile(packageName: String): File? {
        val f = File(dir(), "${packageName}.png")
        return if (f.exists() && f.length() > 0) f else null
    }

    /** Каскад адресов бокса — как у команд (lastGood -> wifi:port -> lan:lanPort). */
    private suspend fun boxCandidates(): List<String> {
        val mainPort = tokenStore.tvBoxPort.first().ifBlank { "8080" }
        val sparePort = tokenStore.tvBoxLanPort.first().ifBlank { mainPort }
        return buildList {
            tokenStore.tvBoxLastGood.first()?.let { add(it) }
            tokenStore.tvBoxWifi.first()?.let { add("$it:$mainPort") }
            tokenStore.tvBoxLan.first()?.let { add("$it:$sparePort") }
        }.distinct()
    }

    /**
     * Скачать иконку пакета с бокса. true = файл сохранён.
     * Тихо возвращает false: старый ресивер/бокс недоступен — останется глаз.
     */
    suspend fun fetchAndSave(packageName: String): Boolean = withContext(Dispatchers.IO) {
        for (address in boxCandidates()) {
            try {
                val request = Request.Builder()
                    .url("http://$address/icon?pkg=${java.net.URLEncoder.encode(packageName, "UTF-8")}")
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    if (bytes.isEmpty()) return@use
                    val tmp = File(dir(), "${packageName}.png.tmp")
                    tmp.writeBytes(bytes)
                    tmp.renameTo(File(dir(), "${packageName}.png"))
                    return@withContext true
                }
            } catch (e: Exception) {
                // следующий кандидат
            }
        }
        false
    }

    /** Удалить иконку пакета (при снятии назначения). */
    fun remove(packageName: String) {
        runCatching { File(dir(), "${packageName}.png").delete() }
    }
}
