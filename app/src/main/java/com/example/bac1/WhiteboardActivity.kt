package com.example.bac1

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

class BoardView(ctx: Context) : View(ctx) {

    enum class Tool { PEN, HIGHLIGHT, ERASER, LINE, ARROW, RECT, CIRCLE, LASER, MOVE }

    private abstract class Item
    private class PathItem(val path: Path, val paint: Paint) : Item()
    private class ShapeItem(val tool: Tool, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val paint: Paint) : Item()

    private class Page {
        val items = mutableListOf<Item>()
        var bgPath: String? = null
    }

    private val pages = mutableListOf(Page())
    var pageIndex = 0
        private set
    val pageCount get() = pages.size

    var tool = Tool.PEN
    var color = Color.BLACK
    var strokeW = 6f
    var bg = Color.WHITE
    var laserWithPen = false
    var onPageChanged: (() -> Unit)? = null

    private val boardW = 3f
    private val boardH = 3f
    private val minScale = 0.3f
    private val maxScale = 5f

    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private val m = Matrix()
    private val inv = Matrix()

    private var curPath: Path? = null
    private var curPaint: Paint? = null
    private var sx = 0f; private var sy = 0f; private var ex = 0f; private var ey = 0f
    private var drawingShape = false

    private var laserX = 0f; private var laserY = 0f
    private var laserOn = false
    private var laserStamp = 0L
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF1744.toInt() }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FF1744 }
    private val borderPaint = Paint().apply {
        color = 0x66888888; style = Paint.Style.STROKE; strokeWidth = 3f
    }

    private var lastDist = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    private var curBg: Bitmap? = null
    private var curBgPath: String? = null

    private fun refreshBg() {
        val path = pages[pageIndex].bgPath
        if (path == curBgPath) return
        curBg = null
        curBgPath = path
        if (path != null) {
            curBg = try { BitmapFactory.decodeFile(path) } catch (e: Throwable) { null }
        }
    }

    fun addBackgrounds(paths: List<String>) {
        if (paths.isEmpty()) return
        val first = pages[pageIndex]
        var start = pageIndex
        var from = 0
        if (first.items.isEmpty() && first.bgPath == null) {
            first.bgPath = paths[0]
            from = 1
        } else {
            start = pages.size
        }
        for (i in from until paths.size) {
            val p = Page()
            p.bgPath = paths[i]
            pages.add(p)
        }
        pageIndex = start
        refreshBg()
        onPageChanged?.invoke()
        invalidate()
    }

    private fun makePaint(): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        when (tool) {
            Tool.HIGHLIGHT -> { p.color = (color and 0x00FFFFFF) or 0x55000000; p.strokeWidth = strokeW * 3.5f; p.strokeCap = Paint.Cap.SQUARE }
            Tool.ERASER -> {
                p.color = Color.TRANSPARENT
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                p.strokeWidth = strokeW * 3f
            }
            else -> { p.color = color; p.strokeWidth = strokeW }
        }
        return p
    }

    private fun toBoard(x: Float, y: Float): FloatArray {
        val pt = floatArrayOf(x, y)
        m.reset()
        m.postScale(scale, scale)
        m.postTranslate(tx, ty)
        m.invert(inv)
        inv.mapPoints(pt)
        return pt
    }

    fun undo() {
        val items = pages[pageIndex].items
        if (items.isNotEmpty()) { items.removeAt(items.size - 1); invalidate() }
    }
    fun clearPage() { pages[pageIndex].items.clear(); invalidate() }
    fun newPage() {
        pages.add(Page()); pageIndex = pages.size - 1
        refreshBg(); onPageChanged?.invoke(); invalidate()
    }
    fun prev() { if (pageIndex > 0) { pageIndex--; refreshBg(); onPageChanged?.invoke(); invalidate() } }
    fun next() { if (pageIndex < pages.size - 1) { pageIndex++; refreshBg(); onPageChanged?.invoke(); invalidate() } }
    fun resetView() { scale = 1f; tx = 0f; ty = 0f; invalidate() }

    fun snapshot(): Bitmap {
        val w = this.width.coerceAtLeast(1)
        val h = this.height.coerceAtLeast(1)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawContent(Canvas(b), false)
        return b
    }

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
            Tool.CIRCLE -> c.drawOval(RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)), p)
            else -> {}
        }
    }

    private fun drawContent(c: Canvas, withLive: Boolean) {
        c.drawColor(bg)
        c.save()
        c.translate(tx, ty)
        c.scale(scale, scale)

        curBg?.let { b ->
            val w = this.width.toFloat().coerceAtLeast(1f)
            val h = w * b.height / b.width
            c.drawBitmap(b, null, RectF(0f, 0f, w, h), null)
        }

        val layer = c.saveLayer(null, null)
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
        c.restoreToCount(layer)

        if (withLive) c.drawRect(0f, 0f, width * boardW, height * boardH, borderPaint)
        c.restore()
    }

    override fun onDraw(canvas: Canvas) {
        drawContent(canvas, true)
        if (laserOn) {
            val age = System.currentTimeMillis() - laserStamp
            if (age < 1200) {
                canvas.drawCircle(laserX, laserY, 28f, glowPaint)
                canvas.drawCircle(laserX, laserY, 12f, laserPaint)
                postInvalidateDelayed(40)
            } else laserOn = false
        }
    }

    private fun showLaser(x: Float, y: Float) {
        laserX = x; laserY = y; laserOn = true; laserStamp = System.currentTimeMillis()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.pointerCount >= 2 || tool == Tool.MOVE) {
            handleMove(e)
            return true
        }
        if (laserWithPen && tool != Tool.LASER && e.actionMasked != MotionEvent.ACTION_UP) {
            showLaser(e.x, e.y)
        }
        val p = toBoard(e.x, e.y)
        when (tool) {
            Tool.LASER -> {
                showLaser(e.x, e.y)
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
                        val ns = (scale * (d / lastDist)).coerceIn(minScale, maxScale)
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
    private var floating: LinearLayout? = null
    private val toolButtons = mutableMapOf<BoardView.Tool, Button>()
    private val colorViews = mutableListOf<Pair<Int, View>>()

    private val maxBookPages = 30
    private val bookWidth = 1400

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startPdf(uri)
    }
    private val imgPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) startImages(uris.take(maxBookPages))
    }

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

    private fun selectTool(t: BoardView.Tool) {
        board.tool = t
        toolButtons.forEach { (k, b) ->
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (k == t) 0xFFFFD600.toInt() else 0xFFBDBDBD.toInt())
            b.setTextColor(Color.BLACK)
        }
    }

    private fun selectColor(c: Int) {
        board.color = c
        colorViews.forEach { (col, v) ->
            val g = GradientDrawable()
            g.setColor(col)
            g.shape = GradientDrawable.OVAL
            if (col == c) g.setStroke(dp(4), Color.WHITE) else g.setStroke(dp(1), Color.GRAY)
            v.background = g
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        board = BoardView(this)

        val root = FrameLayout(this)
        root.addView(board, FrameLayout.LayoutParams(-1, -1))

        toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.VERTICAL
        toolbar.setBackgroundColor(0xEE303030.toInt())
        toolbar.setPadding(dp(2), dp(2), dp(2), dp(2))

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
        val toolViews = tools.map { (n, t) ->
            val b = btn(n) { selectTool(t) }
            toolButtons[t] = b
            b as View
        }
        toolbar.addView(row(*toolViews.toTypedArray()))

        val colors = intArrayOf(
            Color.BLACK, 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(),
            0xFFFDD835.toInt(), 0xFFFB8C00.toInt(), 0xFF8E24AA.toInt(), Color.WHITE
        )
        val cv = colors.map { c ->
            val v = View(this)
            v.setOnClickListener { selectColor(c) }
            v.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).also { it.setMargins(dp(4), dp(2), dp(4), dp(2)) }
            colorViews.add(c to v)
            v
        }
        toolbar.addView(row(*cv.toTypedArray()))

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
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { board.strokeW = max(1, p).toFloat() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sizeRow.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(sizeRow)

        val laserBtn = btn("🔴 ليزر مع القلم: موقوف") { }
        laserBtn.setOnClickListener {
            board.laserWithPen = !board.laserWithPen
            laserBtn.text = if (board.laserWithPen) "🔴 ليزر مع القلم: شغال" else "🔴 ليزر مع القلم: موقوف"
        }

        toolbar.addView(row(
            btn("↩️ تراجع") { board.undo() },
            btn("🗑️ مسح") { board.clearPage() },
            btn("🔍 الحجم الأصلي") { board.resetView() },
            laserBtn,
            btn("📖 كتاب PDF") { pdfPicker.launch(arrayOf("application/pdf")) },
            btn("🖼️ صور") { imgPicker.launch("image/*") },
            btn("🎨 خلفية") { pickBg() },
            btn("💾 صورة") { saveImage() },
            btn("🎬 وضع الشرح") { toggleBars() },
            btn("✖ خروج") { finish() }
        ))
        root.addView(toolbar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).also { it.bottomMargin = dp(48) })

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

        val show = Button(this)
        show.text = "🎬"
        show.visibility = View.GONE
        show.alpha = 0.5f
        show.setOnClickListener { toggleBars() }
        show.tag = "showBtn"
        root.addView(show, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM or Gravity.END)
            .also { it.bottomMargin = dp(24); it.rightMargin = dp(12) })

        buildFloating(root)

        board.onPageChanged = { updatePage() }
        updatePage()
        selectTool(BoardView.Tool.PEN)
        selectColor(Color.BLACK)
        setContentView(root)
    }

    private fun buildFloating(root: FrameLayout) {
        val f = LinearLayout(this)
        f.orientation = LinearLayout.VERTICAL
        f.setBackgroundColor(0xDD303030.toInt())
        f.setPadding(dp(4), dp(4), dp(4), dp(4))
        f.visibility = View.GONE

        val handle = TextView(this)
        handle.text = "✥ اسحب من هنا"
        handle.setTextColor(Color.WHITE)
        handle.textSize = 12f
        handle.gravity = Gravity.CENTER
        handle.setPadding(0, dp(4), 0, dp(4))
        f.addView(handle)

        fun mini(text: String, onClick: () -> Unit): Button {
            val b = btn(text, onClick)
            b.textSize = 12f
            b.layoutParams = LinearLayout.LayoutParams(-1, dp(38))
            return b
        }
        f.addView(mini("✏️ قلم") { selectTool(BoardView.Tool.PEN) })
        f.addView(mini("🖍️ تظليل") { selectTool(BoardView.Tool.HIGHLIGHT) })
        f.addView(mini("🧽 استيكة") { selectTool(BoardView.Tool.ERASER) })
        f.addView(mini("🔴 ليزر") { selectTool(BoardView.Tool.LASER) })
        f.addView(mini("✋ تحريك") { selectTool(BoardView.Tool.MOVE) })
        f.addView(mini("↩️ تراجع") { board.undo() })

        val cr = LinearLayout(this)
        cr.orientation = LinearLayout.HORIZONTAL
        cr.gravity = Gravity.CENTER
        intArrayOf(Color.BLACK, 0xFFE53935.toInt(), 0xFF1E88E5.toInt()).forEach { c ->
            val v = View(this)
            val g = GradientDrawable()
            g.setColor(c); g.shape = GradientDrawable.OVAL; g.setStroke(dp(2), Color.WHITE)
            v.background = g
            v.setOnClickListener { selectColor(c) }
            cr.addView(v, LinearLayout.LayoutParams(dp(28), dp(28)).also { it.setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        f.addView(cr)

        val pr = LinearLayout(this)
        pr.orientation = LinearLayout.HORIZONTAL
        pr.addView(mini("◀") { board.prev() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        pr.addView(mini("▶") { board.next() }, LinearLayout.LayoutParams(0, dp(38), 1f))
        f.addView(pr)

        f.addView(mini("📋 الأدوات كاملة") { toggleBars() })
                f.addView(mini("✖ إخفاء اللوحة") { setFloatingShown(false) })

        var dx = 0f
        var dy = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = f.x - e.rawX; dy = f.y - e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    f.x = (e.rawX + dx).coerceIn(0f, (root.width - f.width).toFloat().coerceAtLeast(0f))
                    f.y = (e.rawY + dy).coerceIn(0f, (root.height - f.height).toFloat().coerceAtLeast(0f))
                }
            }
            true
        }
        root.addView(f, FrameLayout.LayoutParams(dp(110), -2, Gravity.TOP or Gravity.START)
            .also { it.leftMargin = dp(8); it.topMargin = dp(80) })
                val reopen = Button(this)
        reopen.text = "🛠️"
        reopen.alpha = 0.6f
        reopen.visibility = View.GONE
        reopen.setOnClickListener { setFloatingShown(true) }
        reopen.tag = "reopenBtn"
        root.addView(reopen, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.END or Gravity.CENTER_VERTICAL)
            .also { it.rightMargin = dp(4) })
        floating = f
    }

    override fun onDestroy() {
        cacheDir.listFiles()?.filter { it.name.startsWith("bg_") || it.name == "book.pdf" }
            ?.forEach { it.delete() }
        super.onDestroy()
    }
        private var floatingWanted = true

    private fun setFloatingShown(show: Boolean) {
        floatingWanted = show
        floating?.visibility = if (show && hidden) View.VISIBLE else View.GONE
        window.decorView.findViewWithTag<View>("reopenBtn")?.visibility =
            if (!show && hidden) View.VISIBLE else View.GONE
    }

    private fun updatePage() { pageText.text = "صفحة ${board.pageIndex + 1} / ${board.pageCount}" }
    private fun toggleBars() {
        hidden = !hidden
        val v = if (hidden) View.GONE else View.VISIBLE
        toolbar.visibility = v
        bottom.visibility = v
        floating?.visibility = if (hidden && floatingWanted) View.VISIBLE else View.GONE
        window.decorView.findViewWithTag<View>("reopenBtn")?.visibility =
            if (hidden && !floatingWanted) View.VISIBLE else View.GONE
        window.decorView.findViewWithTag<View>("showBtn").visibility = if (hidden) View.VISIBLE else View.GONE
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

    private fun progress(msg: String): AlertDialog {
        val d = AlertDialog.Builder(this).setMessage(msg).setCancelable(false).create()
        d.show()
        return d
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun startPdf(uri: Uri) {
        val dlg = progress("جارٍ فتح الكتاب...")
        Thread {
            try {
                val f = File(cacheDir, "book.pdf")
                contentResolver.openInputStream(uri)?.use { i ->
                    f.outputStream().use { o -> i.copyTo(o) }
                } ?: throw Exception("no stream")
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                val n = r.pageCount
                r.close()
                pfd.close()
                runOnUiThread { dlg.dismiss(); askRange(f, n) }
            } catch (e: Throwable) {
                runOnUiThread { dlg.dismiss(); toast("تعذّر فتح الملف (قد يكون محميًا أو تالفًا)") }
            }
        }.start()
    }

    private fun askRange(file: File, total: Int) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val info = TextView(this)
        info.text = "الكتاب فيه $total صفحة. اختر النطاق (حتى $maxBookPages صفحة في المرة)"
        box.addView(info)
        fun et(hint: String, v: String): EditText {
            val e = EditText(this)
            e.hint = hint
            e.inputType = InputType.TYPE_CLASS_NUMBER
            e.setText(v)
            box.addView(e)
            return e
        }
        val from = et("من صفحة", "1")
        val to = et("إلى صفحة", minOf(total, maxBookPages).toString())

        AlertDialog.Builder(this)
            .setTitle("📖 اختر الصفحات")
            .setView(box)
            .setPositiveButton("استيراد", null)
            .setNegativeButton("إلغاء", null)
            .create().also { d ->
                d.show()
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val a = from.text.toString().toIntOrNull() ?: 0
                    val b = to.text.toString().toIntOrNull() ?: 0
                    if (a < 1 || b > total || a > b || b - a + 1 > maxBookPages) {
                        toast("النطاق غير صحيح (من 1 إلى $total، وبحد أقصى $maxBookPages صفحة)")
                    } else {
                        d.dismiss()
                        renderPdf(file, a, b)
                    }
                }
            }
    }

    private fun renderPdf(file: File, from: Int, to: Int) {
        val dlg = progress("جارٍ تجهيز الصفحات...")
        Thread {
            val paths = mutableListOf<String>()
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                for (i in from..to) {
                    val page = r.openPage(i - 1)
                    val h = (bookWidth.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(bookWidth, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    paths.add(saveJpeg(bmp))
                    bmp.recycle()
                    val k = i - from + 1
                    runOnUiThread { dlg.setMessage("جارٍ تجهيز الصفحة $k من ${to - from + 1}") }
                }
                r.close()
                pfd.close()
                runOnUiThread {
                    dlg.dismiss()
                    board.addBackgrounds(paths)
                    toast("تم استيراد ${paths.size} صفحة ✅ اكتب فوقها بحرية")
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    dlg.dismiss()
                    if (paths.isNotEmpty()) board.addBackgrounds(paths)
                    toast("حدث خطأ أثناء تجهيز الصفحات")
                }
            }
        }.start()
    }

    private fun startImages(uris: List<Uri>) {
        val dlg = progress("جارٍ تجهيز الصور...")
        Thread {
            val paths = mutableListOf<String>()
            try {
                uris.forEachIndexed { idx, uri ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    var s = 1
                    while (opts.outWidth / s > bookWidth * 2) s *= 2
                    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                    var bmp = contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, o2)
                    } ?: return@forEachIndexed
                    if (bmp.width > bookWidth) {
                        val nh = (bookWidth.toLong() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bmp, bookWidth, nh, true)
                        if (scaled != bmp) bmp.recycle()
                        bmp = scaled
                    }
                    paths.add(saveJpeg(bmp))
                    bmp.recycle()
                    runOnUiThread { dlg.setMessage("جارٍ تجهيز الصورة ${idx + 1} من ${uris.size}") }
                }
            } catch (e: Throwable) {
            }
            runOnUiThread {
                dlg.dismiss()
                if (paths.isEmpty()) toast("تعذّر استيراد الصور")
                else {
                    board.addBackgrounds(paths)
                    toast("تم استيراد ${paths.size} صفحة ✅")
                }
            }
        }.start()
    }

    private fun saveJpeg(bmp: Bitmap): String {
        val f = File(cacheDir, "bg_${System.nanoTime()}.jpg")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return f.absolutePath
    }
}
