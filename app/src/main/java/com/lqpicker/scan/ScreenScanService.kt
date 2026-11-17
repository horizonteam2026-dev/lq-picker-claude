package com.lqpicker.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lqpicker.Hero
import com.lqpicker.HeroRepository
import com.lqpicker.MainActivity
import com.lqpicker.R
import com.lqpicker.Suggestion
import com.lqpicker.SuggestionEngine

/**
 * Quét màn hình theo yêu cầu của người chơi.
 *
 * - Chỉ chạy sau khi người dùng đồng ý hộp thoại ghi màn hình của hệ thống; luôn có thông báo.
 * - Chỉ chụp MỘT khung hình khi bấm "Phân tích"; giữa các lần màn hình ảo tạm ngắt.
 * - Chỉ OCR hai dải cột trái/phải (nơi hiện tên tướng đồng đội/đối thủ) nên nhanh và ít nhầm.
 * - Bong bóng tự ẩn trong lúc chụp để không bị OCR đọc nhầm.
 * - OCR chạy trên máy, ảnh không lưu, không gửi đi. Không chạm/bấm vào game.
 */
class ScreenScanService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var heroes: List<Hero> = emptyList()
    private var scanning = false
    private var pendingReader: ImageReader? = null
    private var timeout: Runnable? = null
    private var overlay: OverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_SCAN -> scan()
            ACTION_SHOW -> overlay?.show()
            ACTION_STOP -> stopAll()
            else -> if (projection == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---------- Vòng đời ----------

    private fun start(intent: Intent) {
        createChannel()
        val n = buildNotification("Sẵn sàng. Vào màn chọn tướng rồi bấm Phân tích.")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTI_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTI_ID, n)
        }

        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            stopAll()
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        // Android 14+: bắt buộc đăng ký callback trước khi tạo màn hình ảo.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopAll() }
            }
        }, main)
        projection = p

        val m = realMetrics()
        val (w, h) = scaledSize(m)
        display = p.createVirtualDisplay(
            "lq-scan", w, h, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, null
        )
        heroes = HeroRepository.load(this)

        if (Settings.canDrawOverlays(this)) {
            overlay = OverlayController(
                ctx = this,
                onAnalyze = { scan() },
                onHide = { notify("Bong bóng đã ẩn. Bấm “Hiện” để mở lại.") }
            ).also { it.show() }
        }
        ScanBus.running = true
        ScanBus.status = if (overlay != null) {
            "Đang bật. Bong bóng “LQ” đã hiện trên màn hình: chạm vào để chọn vị trí và phân tích."
        } else {
            "Đang bật (chưa cấp quyền bong bóng). Kéo thanh thông báo xuống và bấm Phân tích."
        }
    }

    private fun stopAll() {
        timeout?.let { main.removeCallbacks(it) }
        overlay?.destroy()
        overlay = null
        pendingReader?.close()
        pendingReader = null
        display?.release()
        display = null
        projection?.stop()
        projection = null
        ScanBus.running = false
        ScanBus.status = "Đã tắt quét màn hình."
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (projection != null) stopAll()
        super.onDestroy()
    }

    // ---------- Quét một lần ----------

    private fun scan() {
        val d = display ?: return
        if (scanning) return
        if (overlay != null && ScanBus.lane == null) {
            overlay?.promptLane()
            return
        }
        scanning = true
        overlay?.setBusy()
        overlay?.hideForCapture()
        notify("Đang quét…")

        // Chờ một nhịp cho bong bóng ẩn hẳn khỏi màn hình rồi mới chụp.
        main.postDelayed({ capture(d) }, if (overlay != null) HIDE_DELAY_MS else 0L)
    }

    private fun capture(d: VirtualDisplay) {
        // Hướng màn hình có thể đổi (app dọc, game ngang) nên đổi kích thước theo hiện tại.
        val m = realMetrics()
        val (w, h) = scaledSize(m)
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        pendingReader = reader

        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            r.setOnImageAvailableListener(null, null)
            timeout?.let { main.removeCallbacks(it) }
            val bmp = try {
                toBitmap(img, w, h)
            } finally {
                img.close()
            }
            d.surface = null
            r.close()
            pendingReader = null
            overlay?.restoreAfterCapture()
            process(bmp)
        }, main)

        d.resize(w, h, m.densityDpi)
        d.surface = reader.surface

        timeout = Runnable {
            if (pendingReader === reader) {
                d.surface = null
                reader.close()
                pendingReader = null
                finish("Không nhận được hình từ màn hình. Thử bấm lại.")
            }
        }.also { main.postDelayed(it, 3000) }
    }

    private fun process(bmp: Bitmap) {
        if (isBlank(bmp)) {
            bmp.recycle()
            finish("Màn hình trả về hình đen: game có thể chặn ghi màn hình. Hãy nhập tay trong app.")
            return
        }
        val w = bmp.width
        val h = bmp.height
        // Chỉ đọc hai dải: cột đồng đội (trái) và cột đối thủ (phải). Bỏ qua lưới tướng ở giữa.
        val leftW = (w * 0.30f).toInt()
        val rightX = (w * 0.72f).toInt()
        val leftBmp = Bitmap.createBitmap(bmp, 0, 0, leftW, h)
        val rightBmp = Bitmap.createBitmap(bmp, rightX, 0, w - rightX, h)
        bmp.recycle()

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val all = ArrayList<OcrLine>()

        fun fail() {
            leftBmp.recycle()
            rightBmp.recycle()
            recognizer.close()
            finish("Lỗi khi đọc chữ trên màn hình.")
        }

        recognizer.process(InputImage.fromBitmap(leftBmp, 0))
            .addOnSuccessListener { t1 ->
                all.addAll(toLines(t1, 0))
                recognizer.process(InputImage.fromBitmap(rightBmp, 0))
                    .addOnSuccessListener { t2 ->
                        all.addAll(toLines(t2, rightX))
                        leftBmp.recycle()
                        rightBmp.recycle()
                        recognizer.close()
                        onRecognized(all, w.toFloat())
                    }
                    .addOnFailureListener { fail() }
            }
            .addOnFailureListener { fail() }
    }

    private fun toLines(t: Text, offsetX: Int): List<OcrLine> =
        t.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val b = l.boundingBox ?: return@mapNotNull null
            OcrLine(l.text, b.exactCenterX() + offsetX, b.exactCenterY(), b.height().toFloat())
        }

    private fun onRecognized(lines: List<OcrLine>, width: Float) {
        val r = NameMatcher.match(lines, heroes, width)
        val byId = heroes.associateBy { it.id }
        val allies = r.allyIds.mapNotNull { byId[it] }
        val enemies = r.enemyIds.mapNotNull { byId[it] }
        val taken = (r.allyIds + r.enemyIds).toSet()

        if (allies.isNotEmpty() || enemies.isNotEmpty()) {
            ScanBus.latest = ScanResult(r.allyIds, r.enemyIds, System.currentTimeMillis())
        }

        val top = SuggestionEngine.suggest(heroes, allies, enemies, ScanBus.lane, taken, limit = 4)
        val notes = SuggestionEngine.analyze(allies, enemies)
        val conf = SuggestionEngine.confidence(allies.size, enemies.size)
        val status = if (allies.isEmpty() && enemies.isEmpty()) {
            "Chưa thấy tướng nào được chọn · gợi ý chung theo vị trí (độ tin cậy $conf)"
        } else {
            "Thấy ${allies.size} đồng đội, ${enemies.size} đối thủ · độ tin cậy $conf"
        }
        val extra = if (top.isEmpty()) null else "Gợi ý: " + top.joinToString(" · ") { "${it.hero.name} ${it.score}" }
        finish(status, extra, top, notes)
    }

    private fun finish(
        status: String,
        extra: String? = null,
        top: List<Suggestion>? = null,
        notes: List<String> = emptyList()
    ) {
        scanning = false
        overlay?.restoreAfterCapture()
        ScanBus.status = status
        notify(status, extra)
        val o = overlay ?: return
        if (top != null) o.showResults(status, top, notes) else o.showMessage(status)
    }

    // ---------- Tiện ích ảnh ----------

    private fun realMetrics(): DisplayMetrics {
        val m = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    /** Giới hạn cạnh dài ≤ 1600px: đủ nét để đọc chữ, nhẹ cho máy yếu. */
    private fun scaledSize(m: DisplayMetrics): Pair<Int, Int> {
        val longEdge = maxOf(m.widthPixels, m.heightPixels)
        val s = if (longEdge > MAX_EDGE) MAX_EDGE.toFloat() / longEdge else 1f
        return Pair((m.widthPixels * s).toInt().coerceAtLeast(2), (m.heightPixels * s).toInt().coerceAtLeast(2))
    }

    private fun toBitmap(img: Image, w: Int, h: Int): Bitmap {
        val plane = img.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * w
        val padded = Bitmap.createBitmap(w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        if (padded.width == w) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, w, h)
        padded.recycle()
        return cropped
    }

    /** Lấy mẫu lưới điểm; nếu gần như đen hết thì coi là bị chặn. */
    private fun isBlank(b: Bitmap): Boolean {
        val stepX = maxOf(1, b.width / 24)
        val stepY = maxOf(1, b.height / 24)
        var y = 0
        while (y < b.height) {
            var x = 0
            while (x < b.width) {
                val c = b.getPixel(x, y)
                val lum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                if (lum > 30) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }

    // ---------- Thông báo ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Quét màn hình", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun notify(text: String, extra: String? = null) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTI_ID, buildNotification(text, extra))
    }

    private fun buildNotification(text: String, extra: String? = null) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("LQ Picker")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (extra != null) "$text\n$extra" else text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(0, "Phân tích", servicePi(ACTION_SCAN, 1))
            .addAction(0, "Hiện", servicePi(ACTION_SHOW, 3))
            .addAction(0, "Tắt", servicePi(ACTION_STOP, 2))
            .build()

    private fun servicePi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, ScreenScanService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        const val ACTION_START = "com.lqpicker.START"
        const val ACTION_SCAN = "com.lqpicker.SCAN"
        const val ACTION_SHOW = "com.lqpicker.SHOW"
        const val ACTION_STOP = "com.lqpicker.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "scan"
        private const val NOTI_ID = 42
        private const val MAX_EDGE = 1600
        private const val HIDE_DELAY_MS = 150L
    }
}
