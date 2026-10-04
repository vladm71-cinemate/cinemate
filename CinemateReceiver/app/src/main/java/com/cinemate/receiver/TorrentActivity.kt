package com.cinemate.receiver

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Одна раздача в списке на ТВ. */
data class TorrentItem(
    val title: String,
    val tracker: String,
    val sizeBytes: Long,
    val sizeName: String,
    val seeders: Int,
    val leechers: Int,
    val date: String,
    val quality: Int,
    val voices: String,
    val magnet: String,
    /** Сезоны раздачи (для фильтра; пусто — фильм/полный пак). */
    val seasons: List<Int> = emptyList(),
    /** Язык фильма (из payload телефона). */
    val languages: List<String> = emptyList()
)

/** Текущий список раздач, присланный телефоном. */
object TorrentList {
    @Volatile var query: String = ""
    @Volatile var posterUrl: String = ""
    /** Расширенный вид ряда (задаётся телефоном в payload). */
    @Volatile var richView: Boolean = false
    @Volatile var clientPackage: String? = null
    @Volatile var items: List<TorrentItem> = emptyList()

    fun set(
        query: String,
        posterUrl: String,
        clientPackage: String?,
        items: List<TorrentItem>,
        richView: Boolean = false
    ) {
        this.query = query
        this.posterUrl = posterUrl
        this.clientPackage = clientPackage
        this.items = items
        this.richView = richView
    }
}

/**
 * Экран списка раздач на ТВ.
 *
 * Пульт:
 *  вверх/вниз — выбор раздачи; вверх с первой позиции — в чипы фильтров;
 *  ОК на раздаче — запустить; ОК на чипе — применить фильтр;
 *  влево/вправо на чипах — переключение чипов;
 *  кнопка меню — сортировка (сиды → размер → дата); Назад — выход.
 *
 * Фильтры «Сезон» и «Качество» — каскадом, как в приложении на телефоне:
 * выбор сезона сужает качества, выбор качества — сезоны.
 */
class TorrentActivity : Activity() {

    private enum class Sort { SEEDS, SIZE, DATE }

    private fun sortLabel(s: Sort): String = getString(
        when (s) {
            Sort.SEEDS -> R.string.tv_sort_seeds
            Sort.SIZE -> R.string.tv_sort_size
            Sort.DATE -> R.string.tv_sort_date
        }
    )

    private var sort: Sort = Sort.SEEDS
    private var seasonFilter: Int? = null
    private var qualityFilter: Int? = null

    private lateinit var adapter: TorrentAdapter
    private lateinit var sortLabel: TextView
    private lateinit var posterView: ImageView
    private lateinit var titleView: TextView
    private lateinit var seasonsScroll: HorizontalScrollView
    private lateinit var qualitiesScroll: HorizontalScrollView
    private lateinit var seasonsRow: LinearLayout
    private lateinit var qualitiesRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101014"))
            setPadding(48, 40, 48, 24)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        posterView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(92, 138).apply { rightMargin = 20 }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(ColorDrawable(Color.parseColor("#1A1A20")))
            visibility = ViewGroup.GONE
        }
        header.addView(posterView)
        titleView = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
            maxLines = 2
        }
        header.addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        root.addView(header)

        sortLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#FFB43A"))
            setPadding(0, 16, 0, 8)
        }
        root.addView(sortLabel)

        // ---------- Ряд чипов «Сезон» ----------
        seasonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        seasonsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(seasonsRow)
        }
        root.addView(
            seasonsScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        )

        // ---------- Ряд чипов «Качество» ----------
        qualitiesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        qualitiesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(qualitiesRow)
        }
        root.addView(
            qualitiesScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6 }
        )

        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.parseColor("#3D3D4A"))
            dividerHeight = 3
            selector = ColorDrawable(Color.parseColor("#23232B"))
        }
        adapter = TorrentAdapter()
        list.adapter = adapter
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            play(position)
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        root.addView(
            TextView(this).apply {
                text = getString(R.string.tv_footer)
                textSize = 13f
                setTextColor(Color.parseColor("#948F99"))
                setPadding(0, 16, 0, 0)
            }
        )

        setContentView(root)
        list.requestFocus()
        render()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        // Новый список от телефона — сброс фильтров (мог не приехать ни один сезон)
        seasonFilter = null
        qualityFilter = null
        render()
    }

    // ================= Данные/фильтрация =================

    private fun availableSeasons(): List<Int> = TorrentList.items
        .filter { t -> qualityFilter == null || t.quality == qualityFilter }
        .flatMap { it.seasons }
        .distinct().sorted()

    private fun availableQualities(): List<Int> = TorrentList.items
        .filter { t -> seasonFilter == null || t.seasons.isEmpty() || seasonFilter in t.seasons }
        .mapNotNull { if (it.quality > 0) it.quality else null }
        .distinct().sortedDescending()

    private fun filteredItems(): List<TorrentItem> = TorrentList.items.filter { t ->
        val seasonOk = seasonFilter == null || t.seasons.isEmpty() || seasonFilter in t.seasons
        val qualityOk = qualityFilter == null || t.quality == qualityFilter
        seasonOk && qualityOk
    }

    private fun render() {
        titleView.text = TorrentList.query
        // Фильтр выпал из каскада — сброс
        if (seasonFilter != null && seasonFilter !in availableSeasons()) seasonFilter = null
        if (qualityFilter != null && qualityFilter !in availableQualities()) qualityFilter = null
        rebuildFilterRows()
        adapter.reload()
        updateSortLabel()
        loadPoster()
    }

    private fun updateSortLabel() {
        val n = adapter.count
        val countWord = resources.getQuantityString(R.plurals.tv_items, n)
        sortLabel.text = getString(R.string.tv_sort_label, sortLabel(sort), n, countWord)
    }

    private fun setSeason(v: Int?) {
        seasonFilter = v
        if (qualityFilter != null && qualityFilter !in availableQualities()) qualityFilter = null
        rebuildFilterRows(focusRow = 0)
        adapter.reload()
        updateSortLabel()
    }

    private fun setQuality(v: Int?) {
        qualityFilter = v
        if (seasonFilter != null && seasonFilter !in availableSeasons()) seasonFilter = null
        rebuildFilterRows(focusRow = 1)
        adapter.reload()
        updateSortLabel()
    }

    // ================= Чипы =================

    private fun makeChip(
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ): TextView {
        val tv = TextView(this).apply {
            text = label
            textSize = 14f
            setPadding(28, 12, 28, 12)
            isFocusable = true
            isClickable = true
            tag = selected
            setOnClickListener {
                onClick()
            }
            setOnFocusChangeListener { v, _ -> updateChipVisual(v as TextView) }
            updateChipVisual(this)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = 10 }
        tv.layoutParams = lp
        return tv
    }

    private fun updateChipVisual(tv: TextView) {
        val selected = tv.tag as Boolean
        val focused = tv.isFocused
        val bg = GradientDrawable().apply {
            cornerRadius = 20f
            when {
                focused -> {
                    setColor(0xFF3A3A44.toInt())
                    setStroke(3, 0xFFFFB43A.toInt())
                }
                selected -> setColor(0xFFFFB43A.toInt())
                else -> setColor(0xFF23232B.toInt())
            }
        }
        tv.background = bg
        tv.setTextColor(if (selected) 0xFF2E2000.toInt() else 0xFFFFFFFF.toInt())
    }

    /** focusRow: 0 — вернуть фокус в ряд сезонов, 1 — в ряд качеств, -1 — не трогать. */
    private fun rebuildFilterRows(focusRow: Int = -1) {
        val seasons = availableSeasons()
        val qualities = availableQualities()

        seasonsRow.removeAllViews()
        qualitiesRow.removeAllViews()

        if (seasons.isNotEmpty()) {
            seasonsScroll.visibility = View.VISIBLE
            seasonsRow.addView(
                makeChip(getString(R.string.filter_all), seasonFilter == null) { setSeason(null) }
            )
            seasons.forEach { sn ->
                seasonsRow.addView(
                    makeChip("$sn", seasonFilter == sn) { setSeason(sn) }
                )
            }
        } else {
            seasonsScroll.visibility = View.GONE
        }

        if (qualities.isNotEmpty()) {
            qualitiesScroll.visibility = View.VISIBLE
            qualitiesRow.addView(
                makeChip(getString(R.string.filter_all), qualityFilter == null) { setQuality(null) }
            )
            qualities.forEach { q ->
                qualitiesRow.addView(
                    makeChip("${q}p", qualityFilter == q) { setQuality(q) }
                )
            }
        } else {
            qualitiesScroll.visibility = View.GONE
        }

        if (focusRow >= 0) {
            val row = if (focusRow == 0) seasonsRow else qualitiesRow
            val scroll = if (focusRow == 0) seasonsScroll else qualitiesScroll
            if (row.childCount > 0 && scroll.visibility == View.VISIBLE) {
                row.getChildAt(0).post { row.getChildAt(0).requestFocus() }
            }
        }
    }

    private fun play(position: Int) {
        val item = adapter.itemAt(position) ?: return
        TorrServeLauncher.launch(this, item.magnet, TorrentList.clientPackage) { }
    }

    private fun cycleSort() {
        sort = when (sort) {
            Sort.SEEDS -> Sort.SIZE
            Sort.SIZE -> Sort.DATE
            Sort.DATE -> Sort.SEEDS
        }
        adapter.resort(sort)
        updateSortLabel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            cycleSort()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadPoster() {
        val url = TorrentList.posterUrl
        if (url.isBlank()) {
            posterView.visibility = ViewGroup.GONE
            return
        }
        thread(name = "poster-loader") {
            val drawable: Drawable? = try {
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
                    posterView.visibility = ViewGroup.VISIBLE
                }
            }
        }
    }

    private inner class TorrentAdapter : BaseAdapter() {

        private var sorted: List<TorrentItem> = applySort()

        fun itemAt(position: Int): TorrentItem? = sorted.getOrNull(position)

        fun reload() {
            sorted = applySort()
            notifyDataSetChanged()
        }

        fun resort(sort: Sort) {
            sorted = applySort()
            notifyDataSetChanged()
        }

        private fun applySort(): List<TorrentItem> {
            val filtered = filteredItems()
            return when (sort) {
                Sort.SEEDS -> filtered.sortedByDescending { it.seeders }
                Sort.SIZE -> filtered.sortedByDescending { it.sizeBytes }
                Sort.DATE -> filtered.sortedByDescending { it.date }
            }
        }

        override fun getCount(): Int = sorted.size
        override fun getItem(position: Int): TorrentItem = sorted[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rich = TorrentList.richView
            // convertView кэшируется — при смене вида пересоздаём структуру
            val row = (convertView as? LinearLayout)?.takeIf { it.childCount == (if (rich) 4 else 2) }
                ?: (if (rich) createRowRich() else createRow())
            val item = sorted[position]
            try {
                if (rich) {
                    (row.getChildAt(0) as TextView).text = item.title
                    (row.getChildAt(1) as TextView).text = buildRichLine(item)
                    val bottom = row.getChildAt(2) as LinearLayout
                    (bottom.getChildAt(0) as TextView).text = buildRichLeft(item)
                    (bottom.getChildAt(1) as TextView).text = buildRichRight(item)
                } else {
                    (row.getChildAt(0) as TextView).text = item.title
                    (row.getChildAt(1) as TextView).text = buildMeta(item)
                }
            } catch (e: Exception) {
                android.util.Log.e("TorrentAdapter", "row bind error: ${e.message}")
            }
            return row
        }

        private fun createRow(): LinearLayout {
            val row = LinearLayout(this@TorrentActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
            }
            val title = TextView(this@TorrentActivity).apply {
                textSize = 16f
                setTextColor(Color.WHITE)
                maxLines = 2
            }
            val meta = TextView(this@TorrentActivity).apply {
                textSize = 13f
                setTextColor(Color.parseColor("#948F99"))
                maxLines = 1
            }
            row.addView(title)
            row.addView(meta)
            return row
        }

        private fun buildMeta(item: TorrentItem): CharSequence {
            val sb = SpannableStringBuilder()

            val head = listOf(item.tracker, item.sizeName)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            sb.append(head)

            if (item.languages.isNotEmpty()) {
                sb.append("   ")
                val lStart = sb.length
                sb.append("🌐 ").append(item.languages.joinToString(" · "))
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor("#FFB43A")),
                    lStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (sb.isNotEmpty()) sb.append("   ")
            val seedStart = sb.length
            sb.append("↑${item.seeders}")
            sb.setSpan(
                ForegroundColorSpan(Color.parseColor("#2E9E4F")),
                seedStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            sb.append(" ")
            val leechStart = sb.length
            sb.append("↓${item.leechers}")
            sb.setSpan(
                ForegroundColorSpan(Color.parseColor("#C62828")),
                leechStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            if (item.date.isNotBlank()) {
                sb.append("   ")
                sb.append(item.date.take(10))
            }

            if (item.quality > 0) {
                sb.append("   ")
                val qStart = sb.length
                sb.append("${item.quality}p")
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor("#FFB43A")),
                    qStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (item.voices.isNotBlank()) {
                sb.append("   ")
                sb.append(item.voices)
            }

            return sb
        }

        private fun createRowRich(): LinearLayout {
            val row = LinearLayout(this@TorrentActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
            }
            val title = TextView(this@TorrentActivity).apply {
                textSize = 17f
                setTextColor(Color.WHITE)
                maxLines = 2
            }
            val voices = TextView(this@TorrentActivity).apply {
                textSize = 14f
                setPadding(0, 6, 0, 0)
            }
            // Нижний ряд: [качество] [растущий отступ] [сид/пир/размер — вправо]
            val bottom = LinearLayout(this@TorrentActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val left = TextView(this@TorrentActivity).apply {
                textSize = 17f
            }
            val right = TextView(this@TorrentActivity).apply {
                textSize = 15f
                gravity = android.view.Gravity.END
            }
            bottom.addView(
                left,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            bottom.addView(
                right,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            row.addView(title)
            row.addView(voices)
            row.addView(bottom)
            return row
        }

        /** Строка 2: дата · трекеры · озвучки (микрофон перед каждой). */
        private fun buildRichLine(item: TorrentItem): CharSequence {
            val sb = SpannableStringBuilder()

            // дата словами (27 августа) по локали системы
            val datePart = try {
                val ld = java.time.LocalDate.parse(item.date.take(10))
                ld.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM"))
            } catch (e: Exception) { item.date.take(10) }
            val gray = ForegroundColorSpan(Color.parseColor("#948F99"))

            val dStart = sb.length
            sb.append(datePart)
            sb.setSpan(gray, dStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (item.tracker.isNotBlank()) {
                sb.append("  ·  ")
                val tStart = sb.length
                sb.append(item.tracker)
                sb.setSpan(gray, tStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            if (item.voices.isNotBlank()) {
                item.voices.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(4).forEach { v ->
                    sb.append("   ")
                    val cStart = sb.length
                    sb.append("🎙 $v")
                    sb.setSpan(
                        ForegroundColorSpan(Color.WHITE),
                        cStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        android.text.style.BackgroundColorSpan(Color.parseColor("#23232B")),
                        cStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            if (item.languages.isNotEmpty()) {
                sb.append("   ")
                val lStart = sb.length
                sb.append("🌐 ").append(item.languages.joinToString(" · "))
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor("#948F99")),
                    lStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        /** Левая часть нижнего ряда: качество + битрейт. */
        private fun buildRichLeft(item: TorrentItem): CharSequence {
            val sb = SpannableStringBuilder()
            if (item.quality > 0) {
                val s = sb.length
                sb.append("${item.quality}p")
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor("#FFB43A")),
                    s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }

        /** Правая часть нижнего ряда: Раздают ↑N Качают ↓N + размер (крупно, жирно). */
        private fun buildRichRight(item: TorrentItem): CharSequence {
            val sb = SpannableStringBuilder()
            val bold = android.text.style.StyleSpan(android.graphics.Typeface.BOLD)

            sb.append("Раздают ")
            val s1 = sb.length
            sb.append("↑${item.seeders}")
            sb.setSpan(
                ForegroundColorSpan(Color.parseColor("#2E9E4F")),
                s1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(bold, s1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                android.text.style.AbsoluteSizeSpan(18, true),
                s1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            sb.append("  Качают ")
            val s2 = sb.length
            sb.append("↓${item.leechers}")
            sb.setSpan(
                ForegroundColorSpan(Color.parseColor("#C62828")),
                s2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(bold, s2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                android.text.style.AbsoluteSizeSpan(18, true),
                s2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            if (item.sizeName.isNotBlank()) {
                sb.append("   ")
                val s3 = sb.length
                sb.append(item.sizeName)
                sb.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    s3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.setSpan(bold, s3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(
                    android.text.style.AbsoluteSizeSpan(18, true),
                    s3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return sb
        }
    }
}
