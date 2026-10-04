package com.cinemate.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemate.app.data.local.TokenStore
import com.cinemate.app.ui.ThemeViewModel
import com.cinemate.app.ui.navigation.AppNavHost
import com.cinemate.app.ui.theme.CinemateTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

    /** Лончер запроса разрешения на уведомления (Android 13+). */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Отказ ≠ проблема: останется только баннер в приложении (по ТЗ)
            if (!granted) {
                android.util.Log.i("MainActivity", "Уведомления отключены — работает баннер в приложении")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermissionIfNeeded()

        // Применяем сохранённый язык интерфейса (при каждом запуске)
        applySavedLanguage()

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

            // Живое переключение языка: при смене в настройках применяем сразу
            LaunchedEffect(Unit) {
                tokenStore.appLanguage.collect { lang ->
                    applyLanguage(lang)
                }
            }

            CinemateTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }

    private fun applySavedLanguage() {
        val lang = kotlinx.coroutines.runBlocking {
            tokenStore.appLanguage.first()
        }
        applyLanguage(lang)
    }

    private fun applyLanguage(lang: String) {
        val locales = if (lang == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(lang)
        }
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /**
     * Android 13+: сначала наш русский диалог-пояснение,
     * затем системный диалог разрешения.
     * Android 12 и ниже: уведомления включены по умолчанию.
     */
    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            // Уже отказывали — по ТЗ не дожимаем, работает баннер
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notif_dialog_title))
            .setMessage(getString(R.string.notif_dialog_text))
            .setPositiveButton(getString(R.string.notif_allow)) { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(getString(R.string.notif_later)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}