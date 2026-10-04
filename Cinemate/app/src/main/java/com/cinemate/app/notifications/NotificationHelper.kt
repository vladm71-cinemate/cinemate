package com.cinemate.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cinemate.app.MainActivity
import com.cinemate.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Системные локальные уведомления.
 * Канал создаётся на Android 8+; разрешение POST_NOTIFICATIONS
 * требуется только на Android 13+, на 11 и ниже уведомления работают сразу.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "cinemate_releases"
        private const val CHANNEL_NAME = "Релизы и новые серии"
        private const val BASE_NOTIFICATION_ID = 1000
    }

    /** Создать канал (вызывается при старте; на 8+ обязателен). */
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о выходе ожидаемых фильмов и новых серий"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /**
     * Разрешены ли уведомления.
     * Android 13+: проверяем permission. Android 12 и ниже: всегда да.
     */
    fun canNotify(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** Фильм вышел (дата цифрового/физического релиза наступила). */
    fun notifyMovieReleased(id: Int, title: String) {
        send(id, "Фильм вышел", title)
    }

    /** У сериала вышла новая серия. */
    fun notifyNewEpisode(id: Int, title: String) {
        send(id, "Новая серия", title)
    }

    private fun send(entityId: Int, title: String, body: String) {
        if (!canNotify()) return

        // Тап по уведомлению открывает приложение
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            entityId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Разные id — уведомления не затирают друг друга
        NotificationManagerCompat.from(context).notify(BASE_NOTIFICATION_ID + entityId, notification)
    }
}