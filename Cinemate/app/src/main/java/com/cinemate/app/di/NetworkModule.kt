package com.cinemate.app.di

import android.content.Context
import coil.ImageLoader
import com.cinemate.app.data.remote.AuthInterceptor
import com.cinemate.app.data.remote.DnsManager
import com.cinemate.app.data.remote.RoutingCallFactory
import com.cinemate.app.data.remote.TmdbApi
import com.cinemate.app.data.remote.TmdbProxyInterceptor
import com.cinemate.app.util.Constants
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import kotlinx.coroutines.flow.first
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    @Provides
    @Singleton
    fun provideCache(
        @ApplicationContext context: Context,
        tokenStore: com.cinemate.app.data.local.TokenStore
    ): Cache {
        val mb = kotlinx.coroutines.runBlocking { tokenStore.imageCacheMb.first() }
        return Cache(File(context.cacheDir, "http_cache"), mb.toLong() * 1024 * 1024)
    }

    /**
     * Применить DNS из DnsManager к клиенту.
     * Режим auto: если активный не выбран — быстрый авто-выбор первого живого.
     * Режим custom: только юзерские.
     */
    private fun applyDns(
        builder: OkHttpClient.Builder,
        dnsManager: DnsManager
    ): OkHttpClient.Builder {
        val dns = kotlinx.coroutines.runBlocking {
            // Режим и список DNS читаются внутри buildDnsWithFallback()
            dnsManager.buildDnsWithFallback()
        }
        if (dns != null) builder.dns(dns)
        return builder
    }

    @Provides
    @Singleton
    @Named("api")
    fun provideOkHttpClient(
        auth: AuthInterceptor,
        proxy: TmdbProxyInterceptor,
        logging: HttpLoggingInterceptor,
        cache: Cache,
        dnsManager: DnsManager
    ): OkHttpClient = applyDns(
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(proxy)
            .addInterceptor(logging)
            .cache(cache)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS),
        dnsManager
    ).build()

    @Provides
    @Singleton
    @Named("image")
    fun provideImageOkHttpClient(
        proxy: TmdbProxyInterceptor,
        logging: HttpLoggingInterceptor,
        cache: Cache,
        dnsManager: DnsManager
    ): OkHttpClient = applyDns(
        OkHttpClient.Builder()
            .addInterceptor(proxy)
            .addInterceptor(logging)
            .cache(cache)
            .addNetworkInterceptor { chain ->
                // Гарантированный кэш картинок: зеркала/прокси часто не отдают
                // Cache-Control -> OkHttp не пишет их в кэш. 2xx кэшируем 30 дней,
                // ошибки не кэшируем вовсе.
                val response = chain.proceed(chain.request())
                if (response.isSuccessful && response.body?.contentLength() != 0L) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=2592000")
                        .removeHeader("Pragma")
                        .build()
                } else {
                    response.newBuilder()
                        .header("Cache-Control", "no-store")
                        .removeHeader("Pragma")
                        .build()
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS),
        dnsManager
    ).build()

    // ---------- Маршрутизация Wi-Fi / Cellular ----------

    @Provides
    @Singleton
    @Named("apiRouting")
    fun provideApiRouting(
        routing: RoutingCallFactory,
        @Named("api") base: OkHttpClient
    ): Call.Factory = routing.forBase(base)

    @Provides
    @Singleton
    @Named("imageRouting")
    fun provideImageRouting(
        routing: RoutingCallFactory,
        @Named("image") base: OkHttpClient
    ): Call.Factory = routing.forBase(base)

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("imageRouting") callFactory: Call.Factory
    ): ImageLoader = ImageLoader.Builder(context)
        .callFactory(callFactory)
        .crossfade(true)
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi =
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @Named("apiRouting") callFactory: Call.Factory,
        moshi: Moshi
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(Constants.TMDB_BASE_URL)
            .callFactory(callFactory)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideTmdbApi(retrofit: Retrofit): TmdbApi =
        retrofit.create(TmdbApi::class.java)
}
