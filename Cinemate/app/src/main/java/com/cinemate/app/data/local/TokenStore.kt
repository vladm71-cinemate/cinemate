package com.cinemate.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cinemate.app.domain.model.ContentLanguage
import com.cinemate.app.domain.model.HomeViewMode
import com.cinemate.app.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cinemate_settings")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val API_TOKEN = stringPreferencesKey("api_token")
        val THEME = stringPreferencesKey("theme_mode")
        val HOME_VIEW = stringPreferencesKey("home_view_mode")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val ORIGINAL_TITLES = booleanPreferencesKey("show_original_titles")

        // ---------- Просмотр на ТВ ----------
        val TV_ENABLED = booleanPreferencesKey("tv_watch_enabled")
        val TV_PLAYER1 = stringPreferencesKey("tv_watch_player1")
        val TV_PLAYER2 = stringPreferencesKey("tv_watch_player2")
        val TV_PLAYER3 = stringPreferencesKey("tv_watch_player3")
        val TV_PLAYER4 = stringPreferencesKey("tv_watch_player4")
        val TV_BOX_WIFI = stringPreferencesKey("tv_box_wifi")
        val TV_BOX_LAN = stringPreferencesKey("tv_box_lan")
        val TV_BOX_PORT = stringPreferencesKey("tv_box_port")
        val TV_BOX_LAN_PORT = stringPreferencesKey("tv_box_lan_port")
        val TV_BOX_LAST_GOOD = stringPreferencesKey("tv_box_last_good")
        val TV_SEARCH_MODE = stringPreferencesKey("tv_search_mode")
        val TV_RESTART = booleanPreferencesKey("tv_restart_player")

        // ---------- Куда играть ----------
        val PLAY_ON_PHONE = booleanPreferencesKey("play_on_phone")

        // ---------- Раздачи: где показывать список ----------
        val TORRENTS_ON_TV = booleanPreferencesKey("torrents_on_tv")

        // ---------- Ноды парсера ----------
        val PARSER_SELECTED = stringPreferencesKey("parser_selected")
        val PARSER_CUSTOM = stringPreferencesKey("parser_custom_nodes")
        val PARSER_AUTO_SWITCH = booleanPreferencesKey("parser_auto_switch")

        // ---------- Прокси TMDb ----------
        val PROXY_MODE = stringPreferencesKey("tmdb_proxy_mode")
        val PROXY_SCHEME = stringPreferencesKey("tmdb_proxy_scheme")
        val PROXY_HOST = stringPreferencesKey("tmdb_proxy_host")
        val PROXY_CUSTOM_LIST = stringPreferencesKey("tmdb_proxy_custom_list")
        val PROXY_LAST_GOOD = stringPreferencesKey("tmdb_proxy_last_good")
        val IMAGE_LAST_GOOD = stringPreferencesKey("tmdb_image_last_good")

        // ---------- DNS ----------
        /** "auto" (тест всех, лучший активный) | "custom" (только юзерские) */
        val DNS_MODE = stringPreferencesKey("dns_mode")
        val DNS_LIST = stringPreferencesKey("dns_custom_list")
        val DNS_ACTIVE = stringPreferencesKey("dns_active")

        // ---------- Язык интерфейса ----------
        val APP_LANGUAGE = stringPreferencesKey("app_language")

        // ---------- Интернет для каталога ----------
        val CATALOG_NET_MODE = stringPreferencesKey("catalog_net_mode")
        val IMAGE_CACHE_MB = intPreferencesKey("image_cache_mb")
        val TORRENT_RICH_VIEW = booleanPreferencesKey("torrent_rich_view")
    }

    // ================= Общие настройки =================

    val userToken: Flow<String?> = context.dataStore.data.map { it[Keys.API_TOKEN] }

    suspend fun saveUserToken(token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) prefs.remove(Keys.API_TOKEN)
            else prefs[Keys.API_TOKEN] = token.trim()
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.DARK
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME] = mode.name }
    }

    val homeViewMode: Flow<HomeViewMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.HOME_VIEW]?.let { runCatching { HomeViewMode.valueOf(it) }.getOrNull() }
            ?: HomeViewMode.GRID2
    }

    suspend fun saveHomeViewMode(mode: HomeViewMode) {
        context.dataStore.edit { prefs -> prefs[Keys.HOME_VIEW] = mode.name }
    }

    val contentLanguage: Flow<ContentLanguage> = context.dataStore.data.map { prefs ->
        prefs[Keys.CONTENT_LANGUAGE]?.let { runCatching { ContentLanguage.valueOf(it) }.getOrNull() }
            ?: ContentLanguage.RU
    }

    suspend fun saveContentLanguage(language: ContentLanguage) {
        context.dataStore.edit { prefs -> prefs[Keys.CONTENT_LANGUAGE] = language.name }
    }

    val showOriginalTitles: Flow<Boolean> = context.dataStore.data.map { it[Keys.ORIGINAL_TITLES] ?: true }

    suspend fun saveShowOriginalTitles(show: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ORIGINAL_TITLES] = show }
    }

    // ================= Просмотр на ТВ =================

    val tvEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.TV_ENABLED] ?: true }

    suspend fun saveTvEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.TV_ENABLED] = enabled }
    }

    val tvPlayer1: Flow<String?> = context.dataStore.data.map { it[Keys.TV_PLAYER1]?.takeIf { v -> v.isNotBlank() } }
    val tvPlayer2: Flow<String?> = context.dataStore.data.map { it[Keys.TV_PLAYER2]?.takeIf { v -> v.isNotBlank() } }
    val tvPlayer3: Flow<String?> = context.dataStore.data.map { it[Keys.TV_PLAYER3]?.takeIf { v -> v.isNotBlank() } }
    val tvPlayer4: Flow<String?> = context.dataStore.data.map { it[Keys.TV_PLAYER4]?.takeIf { v -> v.isNotBlank() } }

    suspend fun saveTvPlayer1(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) prefs.remove(Keys.TV_PLAYER1)
            else prefs[Keys.TV_PLAYER1] = packageName.trim()
        }
    }

    suspend fun saveTvPlayer2(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) prefs.remove(Keys.TV_PLAYER2)
            else prefs[Keys.TV_PLAYER2] = packageName.trim()
        }
    }

    suspend fun saveTvPlayer3(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) prefs.remove(Keys.TV_PLAYER3)
            else prefs[Keys.TV_PLAYER3] = packageName.trim()
        }
    }

    suspend fun saveTvPlayer4(packageName: String?) {
        context.dataStore.edit { prefs ->
            if (packageName.isNullOrBlank()) prefs.remove(Keys.TV_PLAYER4)
            else prefs[Keys.TV_PLAYER4] = packageName.trim()
        }
    }

    val tvBoxWifi: Flow<String?> = context.dataStore.data.map { it[Keys.TV_BOX_WIFI]?.takeIf { v -> v.isNotBlank() } }

    suspend fun saveTvBoxWifi(ip: String?) {
        context.dataStore.edit { prefs ->
            if (ip.isNullOrBlank()) prefs.remove(Keys.TV_BOX_WIFI)
            else prefs[Keys.TV_BOX_WIFI] = ip.trim()
        }
    }

    val tvBoxLan: Flow<String?> = context.dataStore.data.map { it[Keys.TV_BOX_LAN]?.takeIf { v -> v.isNotBlank() } }

    suspend fun saveTvBoxLan(ip: String?) {
        context.dataStore.edit { prefs ->
            if (ip.isNullOrBlank()) prefs.remove(Keys.TV_BOX_LAN)
            else prefs[Keys.TV_BOX_LAN] = ip.trim()
        }
    }

    val tvBoxPort: Flow<String> = context.dataStore.data.map {
        it[Keys.TV_BOX_PORT]?.takeIf { v -> v.isNotBlank() } ?: "8080"
    }

    suspend fun saveTvBoxPort(port: String) {
        context.dataStore.edit { prefs -> prefs[Keys.TV_BOX_PORT] = port.trim() }
    }

    /** Порт запасного адреса (пусто = использовать порт основного). */
    val tvBoxLanPort: Flow<String> = context.dataStore.data.map {
        it[Keys.TV_BOX_LAN_PORT]?.takeIf { v -> v.isNotBlank() } ?: ""
    }

    suspend fun saveTvBoxLanPort(port: String) {
        context.dataStore.edit { prefs ->
            if (port.isBlank()) prefs.remove(Keys.TV_BOX_LAN_PORT)
            else prefs[Keys.TV_BOX_LAN_PORT] = port.trim()
        }
    }

    val tvBoxLastGood: Flow<String?> = context.dataStore.data.map { it[Keys.TV_BOX_LAST_GOOD]?.takeIf { v -> v.isNotBlank() } }

    suspend fun saveTvBoxLastGood(address: String?) {
        context.dataStore.edit { prefs ->
            if (address.isNullOrBlank()) prefs.remove(Keys.TV_BOX_LAST_GOOD)
            else prefs[Keys.TV_BOX_LAST_GOOD] = address.trim()
        }
    }

    val tvSearchMode: Flow<String> = context.dataStore.data.map { it[Keys.TV_SEARCH_MODE] ?: "title" }

    suspend fun saveTvSearchMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.TV_SEARCH_MODE] = mode }
    }

    val tvRestartPlayer: Flow<Boolean> = context.dataStore.data.map { it[Keys.TV_RESTART] ?: true }

    suspend fun saveTvRestartPlayer(restart: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.TV_RESTART] = restart }
    }

    // ================= Куда играть =================

    val playOnPhone: Flow<Boolean> = context.dataStore.data.map { it[Keys.PLAY_ON_PHONE] ?: false }

    suspend fun savePlayOnPhone(onPhone: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.PLAY_ON_PHONE] = onPhone }
    }

    // ================= Раздачи: где показывать список =================

    val torrentsOnTv: Flow<Boolean> = context.dataStore.data.map { it[Keys.TORRENTS_ON_TV] ?: false }

    suspend fun saveTorrentsOnTv(onTv: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.TORRENTS_ON_TV] = onTv }
    }

    // ================= Ноды парсера =================

    val parserCustomNodes: Flow<String> = context.dataStore.data.map { it[Keys.PARSER_CUSTOM] ?: "[]" }

    suspend fun saveParserCustomNodes(json: String) {
        context.dataStore.edit { prefs -> prefs[Keys.PARSER_CUSTOM] = json }
    }

    val parserSelected: Flow<String?> = context.dataStore.data.map {
        it[Keys.PARSER_SELECTED]?.takeIf { v -> v.isNotBlank() }
    }

    suspend fun saveParserSelected(baseUrl: String?) {
        context.dataStore.edit { prefs ->
            if (baseUrl.isNullOrBlank()) prefs.remove(Keys.PARSER_SELECTED)
            else prefs[Keys.PARSER_SELECTED] = baseUrl.trim()
        }
    }

    val parserAutoSwitch: Flow<Boolean> = context.dataStore.data.map { it[Keys.PARSER_AUTO_SWITCH] ?: true }

    suspend fun saveParserAutoSwitch(auto: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.PARSER_AUTO_SWITCH] = auto }
    }

    // ================= Прокси TMDb =================

    val proxyMode: Flow<String> = context.dataStore.data.map { it[Keys.PROXY_MODE] ?: "auto" }

    suspend fun saveProxyMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.PROXY_MODE] = mode }
    }

    val proxyScheme: Flow<String> = context.dataStore.data.map { it[Keys.PROXY_SCHEME] ?: "https" }

    suspend fun saveProxyScheme(scheme: String) {
        context.dataStore.edit { prefs -> prefs[Keys.PROXY_SCHEME] = scheme }
    }

    val proxyHost: Flow<String?> = context.dataStore.data.map {
        it[Keys.PROXY_HOST]?.takeIf { v -> v.isNotBlank() }
    }

    suspend fun saveProxyHost(host: String?) {
        context.dataStore.edit { prefs ->
            if (host.isNullOrBlank()) prefs.remove(Keys.PROXY_HOST)
            else prefs[Keys.PROXY_HOST] = host.trim()
        }
    }

    /** Свои прокси — JSON-массив строк ("https://host" / "http://host"). */
    val proxyCustomList: Flow<String> = context.dataStore.data.map { it[Keys.PROXY_CUSTOM_LIST] ?: "[]" }

    suspend fun saveProxyCustomList(json: String) {
        context.dataStore.edit { prefs -> prefs[Keys.PROXY_CUSTOM_LIST] = json }
    }

    /** Последний рабочий прокси — проверяется первым при старте/переборе. */
    val proxyLastGood: Flow<String?> = context.dataStore.data.map {
        it[Keys.PROXY_LAST_GOOD]?.takeIf { v -> v.isNotBlank() }
    }

    suspend fun saveProxyLastGood(host: String?) {
        context.dataStore.edit { prefs ->
            if (host.isNullOrBlank()) prefs.remove(Keys.PROXY_LAST_GOOD)
            else prefs[Keys.PROXY_LAST_GOOD] = host.trim()
        }
    }

    /** Последнее рабочее зеркало картинок — старт с него. */
    val imageLastGood: Flow<String?> = context.dataStore.data.map {
        it[Keys.IMAGE_LAST_GOOD]?.takeIf { v -> v.isNotBlank() }
    }

    suspend fun saveImageLastGood(host: String?) {
        context.dataStore.edit { prefs ->
            if (host.isNullOrBlank()) prefs.remove(Keys.IMAGE_LAST_GOOD)
            else prefs[Keys.IMAGE_LAST_GOOD] = host.trim()
        }
    }

    // ================= DNS =================

    /** Режим DNS: "auto" (тест всех) | "custom" (только юзерские) | "system" (подмены нет). */
    val dnsMode: Flow<String> = context.dataStore.data.map { it[Keys.DNS_MODE] ?: "auto" }

    suspend fun saveDnsMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.DNS_MODE] = mode }
    }

    /** Пользовательские DNS — JSON-массив строк. */
    val dnsList: Flow<String> = context.dataStore.data.map { it[Keys.DNS_LIST] ?: "[]" }

    suspend fun saveDnsList(json: String) {
        context.dataStore.edit { prefs -> prefs[Keys.DNS_LIST] = json }
    }

    /** Активный рабочий DNS. */
    val dnsActive: Flow<String?> = context.dataStore.data.map {
        it[Keys.DNS_ACTIVE]?.takeIf { v -> v.isNotBlank() }
    }

    suspend fun saveDnsActive(dns: String?) {
        context.dataStore.edit { prefs ->
            if (dns.isNullOrBlank()) prefs.remove(Keys.DNS_ACTIVE)
            else prefs[Keys.DNS_ACTIVE] = dns.trim()
        }
    }

    // ================= Язык интерфейса =================

    val appLanguage: Flow<String> = context.dataStore.data.map { it[Keys.APP_LANGUAGE] ?: "system" }

    suspend fun saveAppLanguage(lang: String) {
        context.dataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = lang }
    }

    // ================= Интернет для каталога =================

    /** "wifi" (дефолт) | "cellular" (трафик TMDb через мобильную сеть). */
    val catalogNetMode: Flow<String> = context.dataStore.data.map { it[Keys.CATALOG_NET_MODE] ?: "wifi" }

    suspend fun saveCatalogNetMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[Keys.CATALOG_NET_MODE] = mode }
    }

    /** Размер кэша картинок, МБ (0 = ещё не задавали → дефолт 50). */
    val imageCacheMb: Flow<Int> = context.dataStore.data.map {
        it[Keys.IMAGE_CACHE_MB]?.takeIf { v -> v >= 10 } ?: 50
    }

    suspend fun saveImageCacheMb(mb: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.IMAGE_CACHE_MB] = mb.coerceIn(10, 500) }
    }

    /** Расширенный вид списка раздач. */
    val torrentRichView: Flow<Boolean> = context.dataStore.data.map { it[Keys.TORRENT_RICH_VIEW] ?: false }

    suspend fun saveTorrentRichView(rich: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.TORRENT_RICH_VIEW] = rich }
    }
}
