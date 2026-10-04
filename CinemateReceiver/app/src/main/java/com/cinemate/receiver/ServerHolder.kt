package com.cinemate.receiver

import android.content.Context
import android.util.Log
import java.net.ServerSocket

/** Держит сервер и анонсер запущенными на уровне процесса. */
object ServerHolder {

    /** Куда писать лог команд. По умолчанию — системный журнал. */
    @Volatile
    var onLog: (String) -> Unit = { Log.i("BoxServer", it) }

    @Volatile
    private var server: BoxServer? = null

    @Volatile
    private var announcer: Announcer? = null

    /** Результат последнего старта: null = ОК, иначе текст ошибки. */
    @Volatile
    var lastStartError: String? = null
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    private const val PREFS = "receiver_prefs"
    private const val KEY_PORT = "preferred_port"

    /** Сохранённый пользователем порт (8080 = не сохранён, пробуем цепочку). */
    fun savedPort(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PORT, BoxServer.DEFAULT_PORT)
    }

    /** Запомнить выбранный порт. */
    fun savePort(context: Context, port: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PORT, port).apply()
    }

    /**
     * Диагностика портов: ключевые порты бокса со статусом.
     * preferred — порт, выбранный пользователем (помечаем).
     */
    fun portDiagnostics(context: Context, preferred: Int): List<String> {
        val result = mutableListOf<String>()

        // Порты-кандидаты + сохранённый пользователем (если не в цепочке)
        val ports = BoxServer.PORT_CANDIDATES.toMutableList()
        if (!ports.contains(preferred)) ports.add(0, preferred)

        for (port in ports) {
            val busy = isPortBusy(port)
            val note = when {
                busy && port == BoxServer.actualPort -> context.getString(R.string.port_receiver_here)
                busy && port == preferred -> context.getString(R.string.port_busy_chosen)
                busy && guessOwner(port) != null -> context.getString(R.string.port_busy_owner, guessOwner(port))
                busy -> context.getString(R.string.port_busy_plain)
                port == preferred -> context.getString(R.string.port_free_recommended)
                else -> context.getString(R.string.port_free_short)
            }
            result.add("$port — $note")
        }

        // Известные сервисы бокса
        val known = mapOf(
            8090 to "TorrServe MatriX",
            5555 to "ADB (отладка по сети)"
        )
        for ((port, owner) in known) {
            if (ports.contains(port)) continue
            if (isPortBusy(port)) result.add("$port — ${context.getString(R.string.port_busy_owner, owner)}")
        }

        val free = ports.filter { !isPortBusy(it) }
        result.add(
            if (free.isNotEmpty()) context.getString(R.string.diag_free_list, free.joinToString(", "))
            else context.getString(R.string.diag_no_free)
        )

        return result
    }

    /** Занят ли порт (локально, bind-пробой). */
    fun isPortBusy(port: Int): Boolean = try {
        ServerSocket(port).use { false }
    } catch (e: Exception) {
        true
    }

    /** Попытка угадать владельца занятого порта. */
    private fun guessOwner(port: Int): String? = when (port) {
        8090 -> "TorrServe MatriX"
        5555 -> "ADB"
        8080 -> "web-интерфейс прошивки?"
        8083 -> "потоковое видео"
        9943 -> "AirPlay/AirReceiver"
        else -> null
    }

    @Synchronized
    fun start(context: Context) {
        if (server != null) {
            isRunning = true
            lastStartError = null
            return
        }

        // Порядок портов: сохранённый пользователем -> цепочка кандидатов
        val preferred = savedPort(context)
        val ordered = buildList {
            add(preferred)
            for (p in BoxServer.PORT_CANDIDATES) if (p != preferred) add(p)
        }.distinct()

        var usedPort: Int? = null
        for (port in ordered) {
            if (isPortBusy(port)) {
                if (port == preferred && preferred != BoxServer.DEFAULT_PORT) {
                    onLog("порт $port (твой выбор) занят, пробую следующий…")
                }
                continue
            }
            try {
                val s = BoxServer(context, port) { line -> onLog(line) }
                s.start()
                server = s
                usedPort = port
                break
            } catch (e: Exception) {
                Log.w("ServerHolder", "порт $port не поднялся: ${e.message}")
            }
        }

        if (usedPort != null) {
            BoxServer.setActualPort(usedPort)
            isRunning = true
            lastStartError = null
            if (usedPort != BoxServer.DEFAULT_PORT) {
                onLog("⚠ работаю на порту $usedPort (8080 был занят или выбран вручную)")
            }
            Log.i("ServerHolder", "Сервер запущен на порту $usedPort")
        } else {
            isRunning = false
            lastStartError = "Не удалось занять ни один порт"
            Log.w("ServerHolder", lastStartError!!)
        }

        if (announcer == null) {
            announcer = Announcer().apply { start() }
        }
    }

    /**
     * Смена порта «на живую»: остановить сервер, поднять на новом порту.
     * Анонсер перезапускается — телефон узнает новый порт из анонса.
     */
    @Synchronized
    fun restartOnPort(context: Context, port: Int) {
        savePort(context, port)
        server?.stop()
        server = null
        BoxServer.setActualPort(port)
        start(context)
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        announcer?.shutdown()
        announcer = null
        isRunning = false
    }
}
