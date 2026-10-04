package com.cinemate.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Прозрачный делегат запуска: стартует из сервиса (same-package — разрешено
 * всегда), а из своего foreground-контекста выполняет то, что прошивки
 * блокируют из фона:
 *  - EXTRA_PACKAGE — запуск приложения (глаза);
 *  - EXTRA_MAGNET  — открытие magnet в торрент-клиенте (Смотреть).
 *
 * Все запуски — с EXCLUDE_FROM_RECENTS: программы, запущенные глазами,
 * не оставляют карточек в «недавних» — после последней под ресивером пусто.
 */
class LaunchDelegate : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val magnet = intent?.getStringExtra(EXTRA_MAGNET).orEmpty()

        try {
            when {
                pkg.isNotBlank() && magnet.isBlank() -> {
                    // Home: текущее приложение уходит с экрана
                    startActivity(
                        Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    Thread.sleep(350)

                    packageManager.getLaunchIntentForPackage(pkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }?.let { startActivity(it) }
                }
                magnet.isNotBlank() -> {
                    fun fire(target: String?): Boolean = try {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(magnet)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                if (target != null) setPackage(target)
                            }
                        ); true
                    } catch (e: Exception) {
                        false
                    }

                    var done = fire(pkg.ifBlank { null })
                    if (!done) done = fire(null)
                    if (!done) {
                        val probe = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("magnet:?xt=urn:btih:${"0".repeat(40)}")
                        )
                        packageManager.queryIntentActivities(probe, 0)
                            .map { it.activityInfo.packageName }
                            .distinct()
                            .forEach { h -> if (fire(h)) return }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LaunchDelegate", "ошибка: ${e.message}")
        }

        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_MAGNET = "magnet"

        fun intent(context: Context, packageName: String): Intent =
            Intent(context, LaunchDelegate::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
