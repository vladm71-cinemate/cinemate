package com.cinemate.app.data.remote

import android.net.Network
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Фабрика вызовов с двумя путями:
 *  1) cellular — сокеты и DNS сотовой сети, если режим включён и сеть жива.
 *     Поверх работает пользовательский DNS из DnsManager:
 *       - DoH-серверы бутстрапятся через сотовую сеть;
 *       - plain-адреса списка резолвятся DNS оператора;
 *       - режим DNS «system» — DNS оператора напрямую.
 *     Прокси-интерцептор и авторизация наследуются от базового клиента:
 *     связка «оператор + прокси + свой DNS» работает вместе.
 *  2) обычный клиент (Wi-Fi / дефолтная сеть).
 *
 * Фолбэк: IOException на cellular-пути -> автоматический повтор через обычный
 * клиент. Только для GET — ретраить POST опасно задвоением (у TMDb все запросы GET).
 */
@Singleton
class RoutingCallFactory @Inject constructor(
    private val networkManager: CatalogNetworkManager,
    private val dnsManager: DnsManager
) {

    /** Кастомный DNS строится один раз на каждую сеть (suspend-чтение настроек). */
    @Volatile
    private var dnsForNetwork: Network? = null

    @Volatile
    private var cachedDns: Dns? = null

    /** Системный резолвер конкретной сети (cellular -> DNS оператора). */
    private fun systemDnsOf(network: Network): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            network.getAllByName(hostname).toList()
    }

    private fun cellularDns(network: Network): Dns? {
        if (dnsForNetwork === network) return cachedDns
        synchronized(this) {
            if (dnsForNetwork === network) return cachedDns

            // Бутстрап-клиент: сам привязан к сотовой сети — им резолвится
            // имя DoH-сервера и ходят сами DoH-запросы.
            val bootstrap = OkHttpClient.Builder()
                .socketFactory(network.socketFactory)
                .dns(systemDnsOf(network))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            val dns = kotlinx.coroutines.runBlocking {
                dnsManager.buildDnsWithFallback(
                    bootstrapClient = bootstrap,
                    systemDns = systemDnsOf(network)
                )
            }

            dnsForNetwork = network
            cachedDns = dns
            return dns
        }
    }

    fun forBase(base: OkHttpClient): Call.Factory = object : Call.Factory {
        override fun newCall(request: Request): Call {
            val network = networkManager.cellularNetwork ?: return base.newCall(request)

            val cellularClient = base.newBuilder()
                .socketFactory(network.socketFactory)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return try {
                            cellularDns(network)?.lookup(hostname)
                                ?: systemDnsOf(network).lookup(hostname)
                        } catch (e: Exception) {
                            // Кастомный DNS не ответил — DNS оператора напрямую
                            systemDnsOf(network).lookup(hostname)
                        }
                    }
                })
                .build()

            val cellularCall = cellularClient.newCall(request)
            return if (request.method == "GET") {
                FailoverCall(cellularCall) { base.newCall(request) }
            } else {
                cellularCall
            }
        }
    }
}

/** Call с фолбэком на резервный при сетевом сбое первичного. */
private class FailoverCall(
    private val primary: Call,
    private val fallback: () -> Call
) : Call by primary {

    @Volatile
    private var current: Call = primary

    override fun execute(): Response = try {
        current.execute()
    } catch (e: IOException) {
        if (current === primary && !current.isCanceled()) {
            current = fallback()
            current.execute()
        } else {
            throw e
        }
    }

    override fun enqueue(responseCallback: Callback) {
        current.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (current === primary && !current.isCanceled()) {
                    current = fallback()
                    current.enqueue(this)
                } else {
                    responseCallback.onFailure(call, e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                responseCallback.onResponse(call, response)
            }
        })
    }

    override fun cancel() {
        current.cancel()
    }
}
