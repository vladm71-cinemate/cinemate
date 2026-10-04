package com.cinemate.receiver

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP-анонсер: раз в 3 секунды рассылает broadcast в локальную сеть:
 *   «CINEMATE_BOX|<актуальный порт>»
 * Телефон («Найти бокс автоматически») слушает тот же порт 8080
 * и находит приёмник по этому сообщению (порт берётся из сообщения).
 */
class Announcer : Thread("cinemate-announcer") {

    @Volatile
    private var running = true

    override fun run() {
        try {
            val socket = DatagramSocket()
            socket.broadcast = true

            while (running && !isInterrupted) {
                try {
                    val port = BoxServer.actualPort
                    val message = "CINEMATE_BOX|$port".toByteArray()
                    val broadcast = InetAddress.getByName("255.255.255.255")
                    socket.send(DatagramPacket(message, message.size, broadcast, 8080))
                } catch (e: Exception) {
                    Log.w("Announcer", "broadcast failed: ${e.message}")
                }
                sleep(3000)
            }
            socket.close()
        } catch (e: Exception) {
            Log.w("Announcer", "announcer died: ${e.message}")
        }
    }

    fun shutdown() {
        running = false
        interrupt()
    }
}