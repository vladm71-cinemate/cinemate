package com.cinemate.app.util

import android.content.Context
import android.net.Uri
import com.cinemate.app.data.local.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Экспорт/импорт настроек одним JSON-файлом (через системный SAF):
 * ноды, прокси, адреса бокса, глаза, API-ключ, тема, вид, язык и пр.
 * История поиска и «Смотрел» НЕ переносятся — только настройки.
 */
@Singleton
class SettingsBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore
) {

    companion object {
        const val MIME_TYPE = "application/json"

        /** Рекомендуемое имя файла для диалога сохранения. */
        fun suggestedFileName(): String =
            "cinemate-backup-${System.currentTimeMillis() / 1000}.json"
    }

    // ================= Экспорт =================

    /** Собрать JSON всех настроек и записать в Uri. null = успех, иначе текст ошибки. */
    suspend fun exportTo(uri: Uri): String? {
        return try {
            val json = JSONObject()

            // Общие
            tokenStore.userToken.first()?.let { json.put("apiToken", it) }
            json.put("theme", tokenStore.themeMode.first().name)
            json.put("homeView", tokenStore.homeViewMode.first().name)
            json.put("contentLanguage", tokenStore.contentLanguage.first().name)
            json.put("showOriginalTitles", tokenStore.showOriginalTitles.first())

            // ТВ: адреса и глаза
            json.put("tvEnabled", tokenStore.tvEnabled.first())
            tokenStore.tvPlayer1.first()?.let { json.put("tvPlayer1", it) }
            tokenStore.tvPlayer2.first()?.let { json.put("tvPlayer2", it) }
            tokenStore.tvPlayer3.first()?.let { json.put("tvPlayer3", it) }
            tokenStore.tvPlayer4.first()?.let { json.put("tvPlayer4", it) }
            tokenStore.tvBoxWifi.first()?.let { json.put("tvBoxWifi", it) }
            tokenStore.tvBoxLan.first()?.let { json.put("tvBoxLan", it) }
            json.put("tvBoxPort", tokenStore.tvBoxPort.first())
            tokenStore.tvBoxLastGood.first()?.let { json.put("tvBoxLastGood", it) }
            json.put("tvSearchMode", tokenStore.tvSearchMode.first())
            json.put("tvRestartPlayer", tokenStore.tvRestartPlayer.first())
            json.put("torrentsOnTv", tokenStore.torrentsOnTv.first())

            // Ноды и прокси
            json.put("parserCustom", tokenStore.parserCustomNodes.first())
            tokenStore.parserSelected.first()?.let { json.put("parserSelected", it) }
            json.put("parserAutoSwitch", tokenStore.parserAutoSwitch.first())
            json.put("proxyMode", tokenStore.proxyMode.first())
            json.put("proxyScheme", tokenStore.proxyScheme.first())
            tokenStore.proxyHost.first()?.let { json.put("proxyHost", it) }
            json.put("proxyCustomList", tokenStore.proxyCustomList.first())
            json.put("catalogNetMode", tokenStore.catalogNetMode.first())

            // Мета
            json.put("app", "cinemate")
            json.put("backupVersion", 1)

            val out = context.contentResolver.openOutputStream(uri)
            if (out == null) {
                "Не удалось открыть файл для записи"
            } else {
                out.use { stream ->
                    stream.write(json.toString(2).toByteArray(Charsets.UTF_8))
                }
                null
            }
        } catch (e: Exception) {
            "Ошибка записи: ${e.message}"
        }
    }

    // ================= Импорт =================

    /**
     * Прочитать JSON из Uri и применить настройки. null = успех, иначе ошибка.
     * Неизвестные/недостающие поля пропускаются — текущие настройки остаются.
     */
    suspend fun importFrom(uri: Uri): String? {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (text == null) {
                "Не удалось открыть файл"
            } else {
                applyJson(text)
            }
        } catch (e: Exception) {
            "Ошибка чтения: ${e.message}"
        }
    }

    private suspend fun applyJson(text: String): String? {
        return try {
            val json = JSONObject(text)
            if (json.optString("app") != "cinemate") {
                return "Это не файл настроек Cinemate"
            }

            // Общие
            if (json.has("apiToken")) tokenStore.saveUserToken(json.optString("apiToken").ifBlank { null })
            if (json.has("theme")) {
                runCatching { com.cinemate.app.domain.model.ThemeMode.valueOf(json.getString("theme")) }
                    .getOrNull()?.let { tokenStore.saveThemeMode(it) }
            }
            if (json.has("homeView")) {
                runCatching { com.cinemate.app.domain.model.HomeViewMode.valueOf(json.getString("homeView")) }
                    .getOrNull()?.let { tokenStore.saveHomeViewMode(it) }
            }
            if (json.has("contentLanguage")) {
                runCatching { com.cinemate.app.domain.model.ContentLanguage.valueOf(json.getString("contentLanguage")) }
                    .getOrNull()?.let { tokenStore.saveContentLanguage(it) }
            }
            if (json.has("showOriginalTitles")) {
                tokenStore.saveShowOriginalTitles(json.getBoolean("showOriginalTitles"))
            }

            // ТВ
            if (json.has("tvEnabled")) tokenStore.saveTvEnabled(json.getBoolean("tvEnabled"))
            if (json.has("tvPlayer1")) tokenStore.saveTvPlayer1(json.optString("tvPlayer1").ifBlank { null })
            if (json.has("tvPlayer2")) tokenStore.saveTvPlayer2(json.optString("tvPlayer2").ifBlank { null })
            if (json.has("tvPlayer3")) tokenStore.saveTvPlayer3(json.optString("tvPlayer3").ifBlank { null })
            if (json.has("tvPlayer4")) tokenStore.saveTvPlayer4(json.optString("tvPlayer4").ifBlank { null })
            if (json.has("tvBoxWifi")) tokenStore.saveTvBoxWifi(json.optString("tvBoxWifi").ifBlank { null })
            if (json.has("tvBoxLan")) tokenStore.saveTvBoxLan(json.optString("tvBoxLan").ifBlank { null })
            if (json.has("tvBoxPort")) tokenStore.saveTvBoxPort(json.getString("tvBoxPort"))
            if (json.has("tvBoxLastGood")) tokenStore.saveTvBoxLastGood(json.optString("tvBoxLastGood").ifBlank { null })
            if (json.has("tvSearchMode")) tokenStore.saveTvSearchMode(json.getString("tvSearchMode"))
            if (json.has("tvRestartPlayer")) tokenStore.saveTvRestartPlayer(json.getBoolean("tvRestartPlayer"))
            if (json.has("torrentsOnTv")) tokenStore.saveTorrentsOnTv(json.getBoolean("torrentsOnTv"))

            // Ноды и прокси
            if (json.has("parserCustom")) tokenStore.saveParserCustomNodes(json.getString("parserCustom"))
            if (json.has("parserSelected")) tokenStore.saveParserSelected(json.optString("parserSelected").ifBlank { null })
            if (json.has("parserAutoSwitch")) tokenStore.saveParserAutoSwitch(json.getBoolean("parserAutoSwitch"))
            if (json.has("proxyMode")) tokenStore.saveProxyMode(json.getString("proxyMode"))
            if (json.has("proxyScheme")) tokenStore.saveProxyScheme(json.getString("proxyScheme"))
            if (json.has("proxyHost")) tokenStore.saveProxyHost(json.optString("proxyHost").ifBlank { null })
            if (json.has("proxyCustomList")) tokenStore.saveProxyCustomList(json.getString("proxyCustomList"))
            if (json.has("catalogNetMode")) tokenStore.saveCatalogNetMode(json.getString("catalogNetMode"))

            null
        } catch (e: Exception) {
            "Файл повреждён: ${e.message}"
        }
    }
}