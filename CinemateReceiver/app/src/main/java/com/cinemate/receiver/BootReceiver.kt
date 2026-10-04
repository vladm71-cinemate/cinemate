package com.cinemate.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Поднимает сервис: при загрузке бокса (BOOT/QUICKBOOT) и по будильнику
 * keep-alive (страховка от системного убийства процесса).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.cinemate.receiver.KEEPALIVE" -> {
                ServerService.start(context)
                // Цепочка: после обработки — следующий будильник
                try {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val pi = android.app.PendingIntent.getBroadcast(
                        context, 1001,
                        Intent(context, BootReceiver::class.java).apply {
                            action = "com.cinemate.receiver.KEEPALIVE"
                        },
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAndAllowWhileIdle(
                        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        android.os.SystemClock.elapsedRealtime() + 5 * 60 * 1000,
                        pi
                    )
                } catch (e: Exception) { }
            }
        }
    }
}
