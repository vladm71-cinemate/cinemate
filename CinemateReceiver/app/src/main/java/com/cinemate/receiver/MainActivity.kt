package com.cinemate.receiver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.net.Uri
import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var addressText: TextView
    private lateinit var rootLayout: LinearLayout
    private lateinit var overlayContainer: FrameLayout
    private lateinit var posterView: ImageView
    private lateinit var overlayHint: TextView

    private lateinit var updateTitle: TextView
    private lateinit var updateButton: Button
    private var pendingUpdate: UpdateInfo? = null
    private lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------- Основной экран ----------
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            setBackgroundColor(0xFF101014.toInt())
        }

        // Заголовок + постер последней раздачи в строке
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }

        val title = TextView(this).apply {
            text = if (appVersion.isBlank()) getString(R.string.server_title)
                   else getString(R.string.server_title) + "  v$appVersion"
            textSize = 32f
            setTextColor(0xFFFFB43A.toInt())
        }
        titleRow.addView(
            title,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        posterView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(90, 135)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = android.view.View.GONE
        }
        titleRow.addView(posterView)
        rootLayout.addView(titleRow)

        addressText = TextView(this).apply {
            textSize = 48f
            setTextColor(0xFFFFFFFF.toInt())
        }
        statusText = TextView(this).apply {
            textSize = 18f
            setTextColor(0xFF9E9E9E.toInt())
        }
        overlayHint = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFB43A.toInt())
            setPadding(0, 12, 0, 0)
            isFocusable = true
            isClickable = true
            isFocusableInTouchMode = true
        }
        val diagButton = Button(this).apply {
            text = getString(R.string.btn_diag)
            textSize = 16f
        }
        diagButton.setOnClickListener { safeRun("diag") { showPortDiagnostics() } }

        val changePortButton = Button(this).apply {
            text = getString(R.string.btn_change_port)
            textSize = 16f
        }
        changePortButton.setOnClickListener { safeRun("port") { showChangePortDialog() } }

        logText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF948F99.toInt())
            setPadding(0, 48, 0, 0)
        }

        rootLayout.addView(addressText)
        rootLayout.addView(overlayHint)
        rootLayout.addView(statusText)
        rootLayout.addView(diagButton)
        rootLayout.addView(changePortButton)
        rootLayout.addView(logText)

        // ---------- Карточка обновления (видна, когда найдена новая версия) ----------
        updateTitle = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFFFB43A.toInt())
            visibility = android.view.View.GONE
        }
        updateButton = Button(this).apply {
            text = getString(R.string.log_update_btn)
            visibility = android.view.View.GONE
        }
        updateButton.setOnClickListener { safeRun("update") { startUpdateDownload() } }
        rootLayout.addView(updateTitle)
        rootLayout.addView(updateButton)

        // ---------- Контейнер оверлея поверх всего ----------
        overlayContainer = FrameLayout(this)

        val root = FrameLayout(this).apply {
            addView(
                rootLayout,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                overlayContainer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ServerHolder.onLog = { line ->
            android.util.Log.i("BoxServer", line)
            runOnUiThread {
                appendLog(line)
                // Постер может прийти с новой командой — обновляем заставку
                updatePoster()
            }
        }

        updateChecker = UpdateChecker(this)
        updateChecker.cleanup()
        checkAppUpdate()

        askNotificationPermissionIfNeeded()
        ServerService.start(this)

        updateAddress()
        updatePoster()
        statusText.text = getString(R.string.status_waiting)

        // D-pad: фокус на первую кнопку сразу после старта
        diagButton.requestFocus()
    }

    /** Android 13+: нотификация foreground-сервиса видна только с разрешением. */
    private fun askNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
    }

    override fun onResume() {
        super.onResume()
        updateAddress()
        updatePoster()
        updateOverlayHint()
    }

    /** Без overlay-права запуск приложений из фона блокирует система. */
    private fun updateOverlayHint() {
        val ok = android.provider.Settings.canDrawOverlays(this)
        overlayHint.text = if (ok) {
            "✅ Поверх окон: разрешено — кнопки работают всегда"
        } else {
            "⚠ Разрешите «Поверх других окон» — иначе кнопки работают только при открытом ресивере"
        }
        overlayHint.setOnClickListener {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                try {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e2: Exception) { }
                }
            }
        }
    }

    // ================= Обновление приложения =================

    private fun checkAppUpdate() {
        thread(name = "update-check") {
            val info = updateChecker.checkDaily() ?: return@thread
            pendingUpdate = info
            runOnUiThread {
                updateTitle.text = getString(R.string.log_update_title, info.versionName)
                updateTitle.visibility = android.view.View.VISIBLE
                updateButton.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun startUpdateDownload() {
        val info = pendingUpdate ?: return
        updateButton.isEnabled = false
        updateButton.text = getString(R.string.log_update_downloading)
        thread(name = "update-download") {
            val file = updateChecker.downloadApk(info)
            runOnUiThread {
                if (file != null && updateChecker.installApk(file)) {
                    updateButton.text = getString(R.string.log_update_confirm)
                } else {
                    updateButton.isEnabled = true
                    updateButton.text = getString(R.string.log_update_failed)
                }
            }
        }
    }

    /** Загрузка постера последней раздачи (URL прислал телефон). */
    private fun updatePoster() {
        val url = LastPoster.url
        if (url.isBlank()) {
            posterView.visibility = android.view.View.GONE
            return
        }
        thread(name = "poster-refresh") {
            val drawable: android.graphics.drawable.Drawable? = try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "CinemateReceiver/1.0")
                if (conn.responseCode in 200..299) {
                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    if (bmp != null) BitmapDrawable(resources, bmp) else null
                } else null
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (drawable != null) {
                    posterView.setImageDrawable(drawable)
                    posterView.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    /** Обёртка: любое исключение в кнопке — в лог на экране, не краш. */
    private fun safeRun(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "crash in $tag", e)
            appendLog(getString(R.string.common_error, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun updateAddress() {
        try {
            val port = if (ServerHolder.isRunning) BoxServer.actualPort else ServerHolder.savedPort(this)
            addressText.text = "${getLocalIp()}:$port"
            if (!ServerHolder.isRunning && ServerHolder.lastStartError != null) {
                statusText.text = getString(R.string.common_error, ServerHolder.lastStartError)
                statusText.setTextColor(0xFFC62828.toInt())
            } else {
                statusText.text = getString(R.string.status_waiting)
                statusText.setTextColor(0xFF9E9E9E.toInt())
            }
        } catch (e: Throwable) {
            appendLog("updateAddress: ${e.message}")
        }
    }

    // ================= Оверлей =================

    private fun hideOverlay() {
        overlayContainer.removeAllViews()
    }

    private fun showOverlay(titleText: String, content: LinearLayout) {
        try {
            overlayContainer.removeAllViews()

            val close = Button(this).apply {
                text = getString(R.string.common_close)
                isFocusableInTouchMode = true
                setOnClickListener { hideOverlay() }
            }
            content.addView(
                close,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24 }
            )

            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(this@MainActivity).apply {
                        text = titleText
                        textSize = 24f
                        setTextColor(0xFFFFB43A.toInt())
                    }
                )
                addView(
                    content,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 24 }
                )
            }

            val panel = ScrollView(this).apply {
                setBackgroundColor(0xFF1A1A20.toInt())
                setPadding(40, 32, 40, 32)
                addView(
                    inner,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            val dim = FrameLayout(this).apply {
                setBackgroundColor(0xCC000000.toInt())
                setPadding(32, 32, 32, 32)
                addView(
                    panel,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                setOnClickListener { hideOverlay() }
            }

            panel.setOnClickListener { }

            overlayContainer.addView(
                dim,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            // D-pad: начальный фокус в оверлей — пультом доступна прокрутка и кнопки
            close.requestFocus()
        } catch (e: Throwable) {
            appendLog("overlay: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ================= Диагностика =================

    private fun showPortDiagnostics() {
        val lines = ServerHolder.portDiagnostics(this, ServerHolder.savedPort(this))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            lines.forEach { line ->
                addView(
                    TextView(this@MainActivity).apply {
                        text = line
                        textSize = 16f
                        setTextColor(
                            if (line.contains(getString(R.string.port_free_short)) ||
                                line.contains(getString(R.string.port_receiver_here)))
                                0xFF2E9E4F.toInt()
                            else 0xFFE6E1E5.toInt()
                        )
                        setPadding(0, 8, 0, 8)
                    }
                )
            }
            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.diag_hint)
                    textSize = 14f
                    setTextColor(0xFF948F99.toInt())
                    setPadding(0, 24, 0, 0)
                }
            )
        }
        showOverlay(getString(R.string.diag_title, BoxServer.actualPort.toString()), content)
    }

    private fun showChangePortDialog() {
        val candidates = BoxServer.PORT_CANDIDATES.toMutableList()
        val saved = ServerHolder.savedPort(this)
        if (!candidates.contains(saved)) candidates.add(0, saved)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            candidates.forEach { port ->
                val busy = ServerHolder.isPortBusy(port)
                val status = when {
                    busy && port == BoxServer.actualPort -> getString(R.string.port_now)
                    busy -> getString(R.string.port_busy)
                    else -> getString(R.string.port_free)
                }
                addView(
                    Button(this@MainActivity).apply {
                        text = "$port$status"
                        setOnClickListener {
                            hideOverlay()
                            safeRun("applyPort") { applyNewPort(port) }
                        }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 12 }
                )
            }

            addView(
                Button(this@MainActivity).apply {
                    text = getString(R.string.port_manual)
                    setOnClickListener {
                        hideOverlay()
                        safeRun("manual") { showManualPortDialog() }
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 12 }
            )
        }
        showOverlay(getString(R.string.port_title, BoxServer.actualPort.toString()), content)
    }

    private fun showManualPortDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.port_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(BoxServer.actualPort.toString())
            setTextColor(Color.WHITE)
        }
        val apply = Button(this).apply {
            text = getString(R.string.port_apply)
            setOnClickListener {
                val port = input.text.toString().toIntOrNull()
                if (port != null && port in 1024..65535) {
                    hideOverlay()
                    safeRun("applyManual") { applyNewPort(port) }
                } else {
                    input.error = getString(R.string.port_hint)
                }
            }
        }
        val cancel = Button(this).apply {
            text = getString(R.string.common_cancel)
            setOnClickListener { hideOverlay() }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            addView(apply)
            addView(cancel)
        }
        showOverlay(getString(R.string.port_manual_title), content)
    }

    private fun applyNewPort(port: Int) {
        ServerHolder.restartOnPort(this, port)
        updateAddress()
        appendLog(getString(R.string.port_changed, port))
    }

    // ================= Сеть / лог =================

    private fun getLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (e: Exception) {
            // fallback ниже
        }
        return "0.0.0.0"
    }

    private fun appendLog(line: String) {
        val current = logText.text.toString()
        val lines = if (current.isBlank()) listOf() else current.lines()
        logText.text = (listOf(line) + lines).take(8).joinToString("\n")
    }
}
