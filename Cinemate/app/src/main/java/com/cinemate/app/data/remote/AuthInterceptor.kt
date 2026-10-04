package com.cinemate.app.data.remote

import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.domain.model.ContentLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Аутентификация + язык по умолчанию для всех запросов.
 *  - Ключ: v4 -> заголовок Bearer, v3 -> query api_key;
 *  - Язык: из настроек (TokenStore), по умолчанию ru-RU.
 * Запросы с явным language (fallback поиска на en-US) не перезаписываются.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider,
    private val tokenStore: TokenStore
) : Interceptor {

    /** Кэш языка: читаем DataStore один раз, потом мгновенно. */
    @Volatile
    private var cachedLanguage: String? = null

    private val currentLanguage: String
        get() {
            cachedLanguage?.let { return it }
            return runBlocking {
                val language = tokenStore.contentLanguage.first().apiTag
                cachedLanguage = language
                language
            }
        }

    /** Вызывается при смене языка в настройках. */
    fun invalidateLanguage() {
        cachedLanguage = null
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val urlBuilder = original.url.newBuilder()
        if (original.url.queryParameter("language") == null) {
            urlBuilder.addQueryParameter("language", currentLanguage)
        }

        val requestBuilder = original.newBuilder()

        when (apiKeyProvider.type) {
            KeyType.V4_BEARER -> requestBuilder
                .header("Authorization", "Bearer ${apiKeyProvider.activeToken}")
                .header("Accept", "application/json")

            KeyType.V3_QUERY -> urlBuilder.addQueryParameter("api_key", apiKeyProvider.activeToken)
        }

        return chain.proceed(requestBuilder.url(urlBuilder.build()).build())
    }
}