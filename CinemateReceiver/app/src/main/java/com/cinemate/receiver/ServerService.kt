package com.cinemate.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Foreground-сервис: держит HTTP-сервер и UDP-анонсер, пока бокс работает.
 * Постоянная (тихая) нотификация не даёт системе убить процесс.
 *
 * Запуск: ServerService.start(context) — из MainActivity и из BootReceiver.
 */
class ServerService : Service() {

    companion object {
        private const val CHANNEL_ID = "cinemate_receiver_service"
        private const val NOTIFICATION_ID = 1

        /** Единая точка запуска сервиса. */
        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Android старше 8: startForegroundService нет — обычный запуск
                Log.w("ServerService", "startForegroundService failed: ${e.message}")
                try {
                    context.startService(intent)
                } catch (e2: Exception) {
                    Log.w("ServerService", "startService failed: ${e2.message}")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        showForegroundNotification()
        // Поднимаем сервер и анонсер. ServerHolder идемпотентен:
        // если уже работает — просто подтвердит, задвоения не будет.
        ServerHolder.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: если система всё-таки убила процесс — перезапустит сервис,
        // а onCreate снова поднимет сервер.
        return START_STICKY
    }

    override fun onDestroy() {
        ServerHolder.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Сервис приёмника",
                NotificationManager.IMPORTANCE_MIN // тихая, без звука, внизу списка
            )
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Cinemate Receiver")
                .setContentText("Сервер работает — ожидание команд с телефона")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Cinemate Receiver")
                .setContentText("Сервер работает — ожидание команд с телефона")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .build()
        }

        try {
            startForeground(NOTIFICATION_ID, notification)
            android.util.Log.i("ServerService", "startForeground OK")
        } catch (e: Exception) {
            // НЕ глотаем: без foreground процесс умрёт с закрытием Activity!
            android.util.Log.e("ServerService", "startForeground FAILED: ${e.message}")
        }
    }
}