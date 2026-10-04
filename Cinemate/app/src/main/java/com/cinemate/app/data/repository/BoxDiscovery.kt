package com.cinemate.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/** Найденный бокс. */
data class DiscoveredBox(
    val address: String,
    val port: Int
)

/**
 * Поиск приёмников на TV Box:
 *  1. Активный TCP-скан подсети телефона: подключение к порту из настроек,
 *     проба GET /ping — наш ресивер отвечает "pong".
 *  2. Пассивное прослушивание UDP-анонсов приёмника (порт берётся из анонса).
 * Подсеть определяется автоматически из IP телефона.
 */
@Singleton
class BoxDiscovery @Inject constructor() {

    companion object {
        const val DISCOVERY_PORT = 8080
        private const val PREFIX = "CINEMATE_BOX|"
        private const val CONNECT_TIMEOUT_MS = 400
        private const val READ_TIMEOUT_MS = 600
        private const val THREADS = 25
    }

    /** Локальный IPv4 телефона. null = не удалось определить. */
    fun getLocalIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    /** Полный поиск. Подсеть определяется из IP телефона автоматически. */
    suspend fun discover(durationMs: Long = 8000): List<DiscoveredBox> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val base = subnetBase(getLocalIp())

                val scanJob = async { scanSubnet(base, durationMs) }
                val announceJob = async { listenAnnouncements(durationMs) }

                val (scanned, announced) = scanJob.await() to announceJob.await()

                (scanned + announced).distinctBy { it.address }
            }
        }

    /** База подсети из IP: "192.168.1.7" -> "192.168.1". */
    private fun subnetBase(myIp: String?): String? {
        if (myIp.isNullOrBlank()) return null
        val parts = myIp.split(".")
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    private suspend fun scanSubnet(base: String?, durationMs: Long): List<DiscoveredBox> {
        if (base.isNullOrBlank()) return emptyList()
        val deadline = System.currentTimeMillis() + durationMs

        val found = mutableListOf<DiscoveredBox>()
        var start = 1

        while (start <= 254 && System.currentTimeMillis() < deadline) {
            val batch = (start until minOf(start + THREADS, 255)).toList()
            val results = coroutineScope {
                batch.map { last ->
                    async {
                        val ip = "$base.$last"
                        if (ip == getLocalIp()) null
                        else probeHost(ip)
                    }
                }.awaitAll()
            }
            results.filterNotNull().forEach { found += it }
            start += THREADS
        }
        return found.distinctBy { it.address }
    }

    /**
     * Проба одного хоста: TCP-коннект к порту, GET /ping — ждём "pong".
     */
    private fun probeHost(ip: String, port: Int = DISCOVERY_PORT): DiscoveredBox? {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS

            val request = "GET /ping HTTP/1.0\r\nHost: $ip\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray())
            socket.getOutputStream().flush()

            val buf = ByteArray(512)
            val read = socket.getInputStream().read(buf)
            socket.close()

            if (read > 0) {
                val response = String(buf, 0, read)
                if (response.contains("HTTP/") && response.lowercase().contains("pong")) {
                    return DiscoveredBox(ip, port)
                }
            }
        } catch (e: Exception) {
            // не наш хост или молчит
        }
        return null
    }

    private suspend fun listenAnnouncements(durationMs: Long): List<DiscoveredBox> =
        withContext(Dispatchers.IO) {
            val found = mutableMapOf<String, DiscoveredBox>()
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null)
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(DISCOVERY_PORT))
                socket.broadcast = true
                socket.soTimeout = 300

                val deadline = System.currentTimeMillis() + durationMs
                val buffer = ByteArray(256)

                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        if (text.startsWith(PREFIX)) {
                            val senderIp = packet.address.hostAddress ?: continue
                            // Порт берём из анонса: "CINEMATE_BOX|8095"
                            val announcedPort = text.removePrefix(PREFIX).trim().toIntOrNull()
                                ?: DISCOVERY_PORT
                            found[senderIp] = DiscoveredBox(senderIp, announcedPort)
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // окно ожидания
                    }
                }
            } catch (e: Exception) {
                // порт занят или запрещён — не страшно, есть TCP-скан
            } finally {
                socket?.close()
            }
            found.values.toList()
        }
}