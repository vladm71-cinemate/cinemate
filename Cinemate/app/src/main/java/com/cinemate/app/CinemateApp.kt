package com.cinemate.app

import android.app.Application
import coil.Coil
import com.cinemate.app.data.remote.CatalogNetworkManager
import com.cinemate.app.data.remote.TmdbProxyManager
import com.cinemate.app.notifications.StartupChecker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CinemateApp : Application() {

    @Inject
    lateinit var startupChecker: StartupChecker

    @Inject
    lateinit var imageLoader: coil.ImageLoader

    @Inject
    lateinit var catalogNetworkManager: CatalogNetworkManager

    @Inject
    lateinit var tmdbProxyManager: TmdbProxyManager

    override fun onCreate() {
        super.onCreate()
        // Проверка релизов при запуске (по ТЗ — только здесь, без фоновых задач).
        startupChecker.checkOnStartup()

        // Восстановить режим «Интернет для каталога» (Wi-Fi / сотовая).
        catalogNetworkManager.start()

        // Прогрев прокси (режим «Всегда»): прошлый рабочий жив? Нет — найти живой.
        tmdbProxyManager.warmUpAsync()

        // Coil: постеры через наш клиент (прокси TMDb внутри).
        Coil.setImageLoader(imageLoader)
    }
}
