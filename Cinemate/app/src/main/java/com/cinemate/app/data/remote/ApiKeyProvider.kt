package com.cinemate.app.data.remote

import com.cinemate.app.BuildConfig
import com.cinemate.app.data.local.TokenStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

enum class KeyType { V4_BEARER, V3_QUERY }

/**
 * Единая точка получения активного ключа:
 * 1) пользовательский (DataStore), если задан;
 * 2) вшитый из BuildConfig.
 *
 * Интерцептор OkHttp синхронный, поэтому первое чтение — runBlocking
 * (один раз за жизнь процесса, дальше значение в кэше).
 */
@Singleton
class ApiKeyProvider @Inject constructor(
    private val tokenStore: TokenStore
) {
    @Volatile
    private var cached: String? = null

    val activeToken: String
        get() {
            cached?.let { return it }
            return runBlocking {
                val user = tokenStore.userToken.first()
                val resolved = if (!user.isNullOrBlank()) user else BuildConfig.TMDB_ACCESS_TOKEN
                cached = resolved
                resolved
            }
        }

    /** Вызывать после сохранения нового ключа в настройках */
    fun invalidate() { cached = null }

    /** v4-токен — длинный JWT (начинается с "eyJ"), v3-ключ — 32 hex-символа */
    val type: KeyType
        get() = if (activeToken.length > 60 && activeToken.startsWith("eyJ")) KeyType.V4_BEARER
                else KeyType.V3_QUERY
}
