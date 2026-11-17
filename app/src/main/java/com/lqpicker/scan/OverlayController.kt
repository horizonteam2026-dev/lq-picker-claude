package com.lqpicker.scan

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lqpicker.Lane
import com.lqpicker.Suggestion
import kotlin.math.abs

/**
 * Bong bóng nổi nhẹ (View thuần, không Compose, không ảnh) hiện trên game.
 *  - Bong bóng nhỏ kéo được; chạm để mở bảng.
 *  - Bảng: chọn vị trí, nút "Phân tích", kết quả + lý giải.
 *  - Sau khi có kết quả tự thu về bong bóng mờ; nút "Ẩn" gỡ hẳn khỏi màn hình.
 * Không chặn thao tác vào game ngoài vùng của chính nó.
 */
class OverlayController(
    private val ctx: Context,
    private val onAnalyze: () -> Unit,
    private val onHide: () -> Unit
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val cBg = Color.parseColor("#F2161B26")
    private val cSurface = Color.parseColor("#FF262E40")
    private val cText = Color.parseColor("#FFE8ECF4")
    private val cSub = Color.parseColor("#FF8E98AD")
    private val cGold = Color.parseColor("#FFF5B63A")
    private val cGood = Color.parseColor("#FF34D399")
    private val cWarn = Color.parseColor("#FFF59E0B")
    private val cDark = Color.parseColor("#FF0F1218")

    private var bubble: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var bubbleShown = false
    private var panelShown = false

    private val laneChips = LinkedHashMap<Lane, TextView>()
    private var analyzeBtn: TextView? = null
    private var statusView: TextView? = null
    private var resultHost: LinearLayout? = null
    private var resultScroll: ScrollView? = null
    private var title: TextView? = null

    private var posX = dp(8)
    private var posY = dp(56)
    private val autoCollapse = Runnable { collapse() }

    // ---------- Hiện/ẩn ----------

    fun show() {
        if (bubble == null) buildBubble()
        if (!bubbleShown && !panelShown) addBubble()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        removeBubble()
        removePanel()
    }

    /** Ẩn tạm để không lọt vào ảnh chụp (tránh OCR đọc nhầm chữ của chính bong bóng). */
    fun hideForCapture() {
        bubble?.visibility = View.INVISIBLE
        panel?.visibility = View.INVISIBLE
    }

    fun restoreAfterCapture() {
        bubble?.visibility = View.VISIBLE
        panel?.visibility = View.VISIBLE
    }

    private fun expand() {
        handler.removeCallbacks(autoCollapse)
        if (panel == null) buildPanel()
        removeBubble()
        refreshLanes()
        addPanel()
    }

    private fun collapse() {
        handler.removeCallbacks(autoCollapse)
        removePanel()
        addBubble()
    }

    private fun hide() {
        handler.removeCallbacks(autoCollapse)
        removePanel()
        removeBubble()
        onHide()
    }

    private fun scheduleCollapse() {
        handler.removeCallbacks(autoCollapse)
        handler.postDelayed(autoCollapse, AUTO_COLLAPSE_MS)
    }

    // ---------- Trạng thái hiển thị ----------

    fun promptLane() {
        if (!panelShown) expand()
        statusView?.text = "Chọn vị trí bạn sẽ đánh trước, rồi bấm Phân tích."
        statusView?.setTextColor(cWarn)
    }

    fun setBusy() {
        analyzeBtn?.text = "Đang quét…"
        statusView?.text = "Đang đọc màn hình…"
        statusView?.setTextColor(cSub)
    }

    fun showMessage(msg: String) {
        if (!panelShown) expand()
        analyzeBtn?.text = "Phân tích"
        statusView?.text = msg
        statusView?.setTextColor(cWarn)
        resultHost?.removeAllViews()
        resultScroll?.visibility = View.GONE
    }

    fun showResults(status: String, items: List<Suggestion>, notes: List<String>) {
        if (!panelShown) expand()
        analyzeBtn?.text = "Phân tích lại"
        statusView?.text = status
        statusView?.setTextColor(cSub)

        val host = resultHost ?: return
        host.removeAllViews()
        if (notes.isNotEmpty()) {
            host.addView(tv("Bối cảnh: " + notes.take(2).joinToString(" "), 11f, cSub).apply {
                setPadding(0, 0, 0, dp(6))
            })
        }
        items.take(3).forEachIndexed { i, s ->
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(cSurface, 10)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(6) }
            }
            val scoreColor = when {
                s.score >= 70 -> cGood
                s.score >= 50 -> cGold
                else -> cWarn
            }
            val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            head.addView(tv("#${i + 1} ${s.hero.name}", 14f, cText, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(tv("${s.score}", 14f, scoreColor, bold = true))
            card.addView(head)
            card.addView(tv(s.hero.cls.label, 10f, cSub))
            card.addView(tv(s.summary, 12f, cText).apply { setPadding(0, dp(4), 0, 0) })
            if (i == 0) {
                s.pros.forEach { card.addView(tv("+ $it", 11f, cGood).apply { setPadding(0, dp(3), 0, 0) }) }
                s.cons.forEach { card.addView(tv("– $it", 11f, cWarn).apply { setPadding(0, dp(3), 0, 0) }) }
            }
            host.addView(card)
        }
        resultScroll?.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        resultScroll?.scrollTo(0, 0)
        scheduleCollapse()
    }

    // ---------- Dựng View ----------

    private fun buildBubble() {
        bubble = TextView(ctx).apply {
            text = "LQ"
            setTextColor(cDark)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cGold)
            }
            alpha = 0.75f
        }
        bubbleLp = baseParams(dp(44), dp(44))
        attachDrag(bubble!!, bubbleLp!!) { expand() }
    }

    private fun buildPanel() {
        val p = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cBg, 14)
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        // Tiêu đề (kéo để di chuyển) + Thu nhỏ + Ẩn
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title = tv("LQ Picker", 13f, cGold, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title!!)
        header.addView(chip("Thu nhỏ", false) { collapse() })
        header.addView(chip("Ẩn", false) { hide() })
        p.addView(header)

        // Vị trí
        val laneRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        Lane.values().forEach { lane ->
            val c = chip(lane.label, false) {
                ScanBus.lane = if (ScanBus.lane == lane) null else lane
                refreshLanes()
            }
            laneChips[lane] = c
            laneRow.addView(c)
        }
        p.addView(laneRow)

        analyzeBtn = tv("Phân tích", 14f, cDark, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(cGold, 10)
            setPadding(0, dp(9), 0, dp(9))
            setOnClickListener {
                handler.removeCallbacks(autoCollapse)
                onAnalyze()
            }
        }
        p.addView(analyzeBtn!!)

        statusView = tv("Chọn vị trí rồi bấm Phân tích.", 11f, cSub).apply { setPadding(0, dp(6), 0, dp(4)) }
        p.addView(statusView!!)

        resultHost = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val maxH = (ctx.resources.displayMetrics.heightPixels * 0.5f).toInt()
        resultScroll = ScrollView(ctx).apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = false
            addView(resultHost!!)
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) scheduleCollapse()
                false
            }
        }
        p.addView(resultScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxH))

        panel = p
        panelLp = baseParams(dp(PANEL_W_DP), WindowManager.LayoutParams.WRAP_CONTENT)
        attachDrag(header, panelLp!!, target = p) { }
    }

    private fun refreshLanes() {
        laneChips.forEach { (lane, v) ->
            val on = ScanBus.lane == lane
            v.background = rounded(if (on) cGold else cSurface, 50)
            v.setTextColor(if (on) cDark else cText)
        }
        title?.text = "LQ Picker" + (ScanBus.lane?.let { " · ${it.label}" } ?: "")
    }

    // ---------- Thêm/gỡ window ----------

    private fun addBubble() {
        val v = bubble ?: return
        val lp = bubbleLp ?: return
        lp.x = posX; lp.y = posY
        clamp(lp, dp(44), dp(44))
        try {
            wm.addView(v, lp)
            bubbleShown = true
        } catch (_: Exception) {
        }
    }

    private fun removeBubble() {
        val v = bubble ?: return
        if (bubbleShown) {
            try { wm.removeView(v) } catch (_: Exception) {}
            bubbleShown = false
        }
    }

    private fun addPanel() {
        val v = panel ?: return
        val lp = panelLp ?: return
        lp.x = posX; lp.y = posY
        clamp(lp, dp(PANEL_W_DP), dp(120))
        try {
            wm.addView(v, lp)
            panelShown = true
        } catch (_: Exception) {
        }
    }

    private fun removePanel() {
        val v = panel ?: return
        if (panelShown) {
            try { wm.removeView(v) } catch (_: Exception) {}
            panelShown = false
        }
    }

    private fun clamp(lp: WindowManager.LayoutParams, w: Int, minH: Int) {
        val dm = ctx.resources.displayMetrics
        lp.x = lp.x.coerceIn(0, (dm.widthPixels - w).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (dm.heightPixels - minH).coerceAtLeast(0))
        posX = lp.x; posY = lp.y
    }

    private fun baseParams(w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    /** Kéo [handle] để di chuyển cửa sổ [lp]; chạm nhẹ (không kéo) thì gọi [onTap]. */
    private fun attachDrag(
        handle: View,
        lp: WindowManager.LayoutParams,
        target: View = handle,
        onTap: () -> Unit
    ) {
        handle.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx
                        val dy = e.rawY - sy
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        if (moved) {
                            lp.x = ox + dx.toInt()
                            lp.y = oy + dy.toInt()
                            val dm = ctx.resources.displayMetrics
                            lp.x = lp.x.coerceIn(0, (dm.widthPixels - target.width).coerceAtLeast(0))
                            lp.y = lp.y.coerceIn(0, (dm.heightPixels - target.height).coerceAtLeast(0))
                            posX = lp.x; posY = lp.y
                            try { wm.updateViewLayout(target, lp) } catch (_: Exception) {}
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) onTap()
                }
                return true
            }
        })
    }

    // ---------- Tiện ích dựng View ----------

    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(if (selected) cDark else cText)
        background = rounded(if (selected) cGold else cSurface, 50)
        setPadding(dp(9), dp(5), dp(9), dp(5))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.marginEnd = dp(5) }
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    companion object {
        private const val PANEL_W_DP = 310
        private const val AUTO_COLLAPSE_MS = 15_000L
    }
}
