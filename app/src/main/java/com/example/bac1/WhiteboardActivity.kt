package com.example.bac1

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class BoardView(ctx: Context) : View(ctx) {

    enum class Tool { PEN, HIGHLIGHT, ERASER, LINE, ARROW, RECT, CIRCLE, LASER, MOVE }

    private abstract class Item
    private class PathItem(val path: Path, val paint: Paint) : Item()
    private class ShapeItem(val tool: Tool, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val paint: Paint) : Item()

    private class Page { val items = mutableListOf<Item>() }

    private val pages = mutableListOf(Page())
    var pageIndex = 0
        private set
    val pageCount get() = pages.size

    var tool = Tool.PEN
    var color = Color.BLACK
    var width = 6f
    var bg = Color.WHITE
    var onPageChanged: (() -> Unit)? = null

    // تكبير وتحريك
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private val matrix = Matrix()
    private val inv = Matrix()

    // الحالة الحالية
    private var curPath: Path? = null
    private var curPaint: Paint? = null
    private var sx = 0f; private var sy = 0f; private var ex = 0f; private var ey = 0f
    private var drawingShape = false

    // الليزر
    private var laserX = 0f; private var laserY = 0f
    private var laserOn = false
    private var laserFade = 0L

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF1744.toInt() }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FF1744 }

    // قرص
    private var lastDist = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    private fun makePaint(): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        when (tool) {
            Tool.HIGHLIGHT -> { p.color = (color and 0x00FFFFFF) or 0x55000000; p.strokeWidth = width * 3.5f; p.strokeCap = Paint.Cap.SQUARE }
            Tool.ERASER -> { p.color = bg; p.strokeWidth = width * 3f }
            else -> { p.color = color; p.strokeWidth = width }
        }
        return p
    }

    private fun toBoard(x: Float, y: Float): FloatArray {
        val pt = floatArrayOf(x, y)
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(tx, ty)
        matrix.invert(inv)
        inv.mapPoints(pt)
        return pt
    }

    fun undo() {
        val items = pages[pageIndex].items
        if (items.isNotEmpty()) { items.removeAt(items.size - 1); invalidate() }
    }

    fun clearPage() { pages[pageIndex].items.clear(); invalidate() }

    fun newPage() { pages.add(Page()); pageIndex = pages.size - 1; onPageChanged?.invoke(); invalidate() }
    fun prev() { if (pageIndex > 0) { pageIndex--; onPageChanged?.invoke(); invalidate() } }
    fun next() { if (pageIndex < pages.size - 1) { pageIndex++; onPageChanged?.invoke(); invalidate() } }
    fun resetView() { scale = 1f; tx = 0f; ty = 0f; invalidate() }

    fun snapshot(): Bitmap {
        val b = Bitmap.createBitmap(width(), height(), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val s = scale; val x = tx; val y = ty
        scale = 1f; tx = 0f; ty = 0f
        drawContent(c, false)
        scale = s; tx = x; ty = y
        return b
    }

    private fun width() = this.getWidth().coerceAtLeast(1)
    private fun height() = this.getHeight().coerceAtLeast(1)

    private fun drawShape(c: Canvas, t: Tool, x1: Float, y1: Float, x2: Float, y2: Float, p: Paint) {
        when (t) {
            Tool.LINE -> c.drawLine(x1, y1, x2, y2, p)
            Tool.ARROW -> {
                c.drawLine(x1, y1, x2, y2, p)
                val ang = atan2(y2 - y1, x2 - x1)
                val len = 30f + p.strokeWidth * 2
                for (d in listOf(0.5, -0.5)) {
                    c.drawLine(x2, y2,
                        (x2 - len * cos(ang + d)).toFloat(), (y2 - len * sin(ang + d)).toFloat(), p)
                }
            }
            Tool.RECT -> c.drawRect(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2), p)
            Tool.CIRCLE -> {
                val cx = (x1 + x2) / 2; val cy = (y1 + y2) / 2
                c.drawOval(RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)), p)
            }
            else -> {}
        }
    }

    private fun drawContent(c: Canvas, withLive: Boolean) {
        c.drawColor(bg)
        c.save()
        c.scale(scale, scale)
        c.translate(tx / scale, ty / scale)
        for (it in pages[pageIndex].items) {
            when (it) {
                is PathItem -> c.drawPath(it.path, it.paint)
                is ShapeItem -> drawShape(c, it.tool, it.x1, it.y1, it.x2, it.y2, it.paint)
            }
        }
        if (withLive) {
            curPath?.let { c.drawPath(it, curPaint!!) }
            if (drawingShape) drawShape(c, tool, sx, sy, ex, ey, makePaint())
        }
        c.restore()
    }

    override fun onDraw(canvas: Canvas) {
        drawContent(canvas, true)
        if (laserOn) {
            val age = System.currentTimeMillis() - laserFade
            if (age < 1200) {
                canvas.drawCircle(laserX, laserY, 28f, glowPaint)
                canvas.drawCircle(laserX, laserY, 12f, laserPaint)
                postInvalidateDelayed(40)
            } else laserOn = false
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // تحريك/تكبير بإصبعين دائمًا، أو بإصبع واحد في وضع MOVE
        if (e.pointerCount >= 2 || tool == Tool.MOVE) {
            handleMove(e)
            return true
        }
        val p = toBoard(e.x, e.y)
        when (tool) {
            Tool.LASER -> {
                laserX = e.x; laserY = e.y; laserOn = true; laserFade = System.currentTimeMillis()
                invalidate()
                return true
            }
            Tool.PEN, Tool.HIGHLIGHT, Tool.ERASER -> when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    curPaint = makePaint()
                    curPath = Path().also { it.moveTo(p[0], p[1]); it.lineTo(p[0] + 0.1f, p[1] + 0.1f) }
                }
                MotionEvent.ACTION_MOVE -> curPath?.lineTo(p[0], p[1])
                MotionEvent.ACTION_UP -> {
                    curPath?.let { pages[pageIndex].items.add(PathItem(it, curPaint!!)) }
                    curPath = null
                }
            }
            else -> when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = p[0]; sy = p[1]; ex = sx; ey = sy; drawingShape = true }
                MotionEvent.ACTION_MOVE -> { ex = p[0]; ey = p[1] }
                MotionEvent.ACTION_UP -> {
                    ex = p[0]; ey = p[1]
                    pages[pageIndex].items.add(ShapeItem(tool, sx, sy, ex, ey, makePaint()))
                    drawingShape = false
                }
            }
        }
        invalidate()
        return true
    }

    private fun handleMove(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastMidX = e.x; lastMidY = e.y }
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastDist = dist(e)
                lastMidX = (e.getX(0) + e.getX(1)) / 2
                lastMidY = (e.getY(0) + e.getY(1)) / 2
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val d = dist(e)
                    val mx = (e.getX(0) + e.getX(1)) / 2
                    val my = (e.getY(0) + e.getY(1)) / 2
                    if (lastDist > 0) {
                        val f = (d / lastDist)
                        val ns = (scale * f).coerceIn(0.5f, 5f)
                        val real = ns / scale
                        tx = mx - (mx - tx) * real + (mx - lastMidX)
                        ty = my - (my - ty) * real + (my - lastMidY)
                        scale = ns
                    }
                    lastDist = d; lastMidX = mx; lastMidY = my
                } else {
                    tx += e.x - lastMidX; ty += e.y - lastMidY
                    lastMidX = e.x; lastMidY = e.y
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val i = if (e.actionIndex == 0) 1 else 0
                lastMidX = e.getX(i); lastMidY = e.getY(i); lastDist = 0f
            }
        }
    }

    private fun dist(e: MotionEvent): Float {
        val dx = e.getX(0) - e.getX(1); val dy = e.getY(0) - e.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
}

class WhiteboardActivity : AppCompatActivity() {

    private lateinit var board: BoardView
    private lateinit var toolbar: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var pageText: TextView
    private var hidden = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 13f
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(dp(8), 0, dp(8), 0)
        b.setOnClickListener { onClick() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        board = BoardView(this)

        val root = FrameLayout(this)
        root.addView(board, FrameLayout.LayoutParams(-1, -1))

        // الشريط العلوي (أدوات)
        toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.VERTICAL
        toolbar.setBackgroundColor(0xEE303030.toInt())
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4))

        fun row(vararg v: View): HorizontalScrollView {
            val h = HorizontalScrollView(this)
            val l = LinearLayout(this)
            l.orientation = LinearLayout.HORIZONTAL
            v.forEach { l.addView(it) }
            h.addView(l)
            return h
        }

        val tools = listOf(
            "✏️ قلم" to BoardView.Tool.PEN, "🖍️ تظليل" to BoardView.Tool.HIGHLIGHT,
            "🧽 استيكة" to BoardView.Tool.ERASER, "╱ خط" to BoardView.Tool.LINE,
            "➜ سهم" to BoardView.Tool.ARROW, "▭ مستطيل" to BoardView.Tool.RECT,
            "◯ دائرة" to BoardView.Tool.CIRCLE, "🔴 ليزر" to BoardView.Tool.LASER,
            "✋ تحريك" to BoardView.Tool.MOVE
        )
        toolbar.addView(row(*tools.map { (n, t) -> btn(n) { board.tool = t } }.toTypedArray()))

        // الألوان
        val colors = intArrayOf(
            Color.BLACK, 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(),
            0xFFFDD835.toInt(), 0xFFFB8C00.toInt(), 0xFF8E24AA.toInt(), Color.WHITE
        )
        val colorViews = colors.map { c ->
            val v = View(this)
            val g = GradientDrawable()
            g.setColor(c); g.setStroke(dp(2), Color.GRAY); g.shape = GradientDrawable.OVAL
            v.background = g
            v.setOnClickListener { board.color = c }
            v.layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).also { it.setMargins(dp(5), dp(4), dp(5), dp(4)) }
            v
        }
        toolbar.addView(row(*colorViews.toTypedArray()))

        // سُمك الخط
        val sizeRow = LinearLayout(this)
        sizeRow.orientation = LinearLayout.HORIZONTAL
        sizeRow.gravity = Gravity.CENTER_VERTICAL
        val lbl = TextView(this)
        lbl.text = "السُمك"
        lbl.setTextColor(Color.WHITE)
        sizeRow.addView(lbl)
        val seek = SeekBar(this)
        seek.max = 40
        seek.progress = 6
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { board.width = max(1, p).toFloat() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sizeRow.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(sizeRow)

        // إجراءات
        toolbar.addView(row(
            btn("↩️ تراجع") { board.undo() },
            btn("🗑️ مسح") { board.clearPage() },
            btn("🔍 إعادة") { board.resetView() },
            btn("🎨 خلفية") { pickBg() },
            btn("💾 صورة") { saveImage() },
            btn("👁️ إخفاء") { toggleBars() },
            btn("✖ خروج") { finish() }
        ))
        root.addView(toolbar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // شريط الصفحات
        bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.gravity = Gravity.CENTER
        bottom.setBackgroundColor(0xEE303030.toInt())
        pageText = TextView(this)
        pageText.setTextColor(Color.WHITE)
        pageText.textSize = 16f
        pageText.setPadding(dp(16), 0, dp(16), 0)
        bottom.addView(btn("◀") { board.prev() })
        bottom.addView(pageText)
        bottom.addView(btn("▶") { board.next() })
        bottom.addView(btn("➕ صفحة") { board.newPage() })
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        // زرار صغير يرجّع الأدوات
        val show = Button(this)
        show.text = "👁️"
        show.visibility = View.GONE
        show.alpha = 0.25f
        show.setOnClickListener { toggleBars() }
        show.tag = "showBtn"
        root.addView(show, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END))

        board.onPageChanged = { updatePage() }
        updatePage()
        setContentView(root)
    }

    private fun updatePage() { pageText.text = "صفحة ${board.pageIndex + 1} / ${board.pageCount}" }

    private fun toggleBars() {
        hidden = !hidden
        val v = if (hidden) View.GONE else View.VISIBLE
        toolbar.visibility = v
        bottom.visibility = v
        (window.decorView.findViewWithTag<View>("showBtn")).visibility = if (hidden) View.VISIBLE else View.GONE
    }

    private fun pickBg() {
        AlertDialog.Builder(this).setTitle("لون الخلفية")
            .setItems(arrayOf("⚪ أبيض", "🟢 سبورة خضراء", "⚫ أسود")) { _, w ->
                board.bg = when (w) { 0 -> Color.WHITE; 1 -> 0xFF1B5E20.toInt(); else -> Color.BLACK }
                board.invalidate()
            }.show()
    }

    private fun saveImage() {
        try {
            val bmp = board.snapshot()
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "1Bac_board_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/1Bac")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("no uri")
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "تم حفظ الصفحة في المعرض ✅", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر الحفظ", Toast.LENGTH_SHORT).show()
        }
    }
}
