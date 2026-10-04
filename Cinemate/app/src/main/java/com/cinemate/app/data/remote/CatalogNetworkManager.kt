package com.cinemate.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cinemate.app.data.local.TokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Режим интернета для каталога:
 *  "wifi"     — как обычно;
 *  "cellular" — трафик TMDb принудительно через сотовую сеть.
 *
 * Надёжность: cellular-сеть считается живой только пока она VALIDATED
 * (система подтвердила реальный интернет в ней). Потеря валидности/сети —
 * мгновенный сброс: трафик падает на обычный путь (Wi-Fi), без залипания.
 */
@Singleton
class CatalogNetworkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore
) {

    companion object {
        const val MODE_WIFI = "wifi"
        const val MODE_CELLULAR = "cellular"
        private const val TAG = "CatalogNet"
        private const val REQUEST_TIMEOUT_MS = 10_000
        private const val RETRY_MS = 30_000L
    }

    private val _cellularAvailable = MutableStateFlow<Boolean?>(null)
    val cellularAvailable: StateFlow<Boolean?> = _cellularAvailable.asStateFlow()

    /** Живая диагностика для экрана настроек. */
    private val _diag = MutableStateFlow<List<String>>(emptyList())
    val diag: StateFlow<List<String>> = _diag.asStateFlow()

    private fun log(line: String) {
        Log.i(TAG, line)
        _diag.value = (_diag.value + line).takeLast(6)
    }

    @Volatile
    var cellularNetwork: Network? = null
        private set

    @Volatile
    private var registered = false

    @Volatile
    private var mode: String = MODE_WIFI

    private val handler = Handler(Looper.getMainLooper())

    private fun setNetwork(n: Network?) {
        cellularNetwork = n
        _cellularAvailable.value = n != null
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            setNetwork(network)
            log("onAvailable: сеть выдана (ждём валидацию)")
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (network != cellularNetwork) return
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!validated && cellularNetwork == network) {
                log("capabilities: сеть НЕ validated — сбрасываю, фолбэк на Wi-Fi")
                setNetwork(null)
            } else if (validated && cellularNetwork == null) {
                setNetwork(network)
                log("capabilities: сеть validated — захват OK")
            }
        }

        override fun onLost(network: Network) {
            if (cellularNetwork == network) {
                log("onLost: сеть потеряна — фолбэк на Wi-Fi")
                setNetwork(null)
            }
        }

        override fun onUnavailable() {
            cellularNetwork = null
            _cellularAvailable.value = false
            registered = false
            log("onUnavailable: система не дала сеть за ${REQUEST_TIMEOUT_MS / 1000} c")
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        handler.postDelayed({
            if (mode == MODE_CELLULAR && !registered) enable()
        }, RETRY_MS)
    }

    /** Вызвать один раз из CinemateApp.onCreate(). */
    fun start() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            mode = tokenStore.catalogNetMode.first()
            if (mode == MODE_CELLULAR) enable()
        }
    }

    @Synchronized
    fun enable() {
        mode = MODE_CELLULAR
        if (registered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        try {
            cm.requestNetwork(request, callback, REQUEST_TIMEOUT_MS)
            registered = true
            log("запрос сети отправлен (только VALIDATED, таймаут 10 c)")

            // Сеть могла быть уже активной и валидной — берём её сразу.
            val active = cm.allNetworks.firstOrNull { n ->
                val caps = cm.getNetworkCapabilities(n)
                caps != null &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            if (active != null && cellularNetwork == null) {
                setNetwork(active)
                log("сеть найдена сканом активных (validated) — захват OK")
            }
        } catch (e: Exception) {
            _cellularAvailable.value = false
            log("requestNetwork ошибка: ${e.message}")
        }
    }

    @Synchronized
    fun disable() {
        mode = MODE_WIFI
        if (registered) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(callback) } catch (e: Exception) { }
            registered = false
        }
        handler.removeCallbacksAndMessages(null)
        setNetwork(null)
    }

    fun setMode(newMode: String) {
        if (newMode == MODE_CELLULAR) enable() else disable()
    }
}
