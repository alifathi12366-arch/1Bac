package com.example.bac1

import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class SortStudentActivity : AppCompatActivity() {

    private class Cat(val name: String, val items: List<String>)
    private class Game(val id: String, val title: String, val cats: List<Cat>, val createdAt: Long)
    private class Item(val text: String, val cat: Int)

    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())

    private var teacherCode = ""
    private var studentName = ""
    private var playerKey = ""

    private var games: List<Game> = emptyList()
    private var played: Set<String> = emptySet()

    private var screen = "load"
    private var game: Game? = null
    private var practice = false
    private var items: List<Item> = emptyList()
    private var placed = IntArray(0)
    private var pos = 0
    private var correctCount = 0
    private var mistakes = 0
    private var locked = false
    private var finished = false
    private var outcome = ""
    private var startMs = 0L
    private var finalTimeMs = 0L
    private var confettiAdded = false

    private var tvTimer: TextView? = null
    private var tvStatus: TextView? = null
    private var itemCard: TextView? = null
    private val catCells = mutableListOf<TextView>()

    private lateinit var body: LinearLayout

    private fun dp(v: Int) = LeagueUi.dp(this, v)
    private fun resultRef(gameId: String) = db.collection("sortResults").document("${gameId}_$playerKey")

    private val clock = object : Runnable {
        override fun run() {
            if (screen != "play" || finished) return
            tvTimer?.text = "⏱ " + fmtTime(elapsed())
            handler.postDelayed(this, 500L)
        }
    }

    private fun elapsed() = System.currentTimeMillis() - startMs

    private fun fmtTime(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun score(): Long = maxOf(0L, correctCount * 10L - mistakes * 3L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(Color.parseColor(LeagueUi.BG))
        val scroll = ScrollView(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(12), dp(16), dp(24))
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    "play" -> confirmQuit()
                    "final" -> {
                        screen = "list"
                        render()
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = prefs.all["student_id"]?.toString().orEmpty()
        var studentKey = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }
        if (studentKey.isEmpty()) studentKey = "anon"
        playerKey = studentKey + "_" + studentName.replace("/", "-")
        teacherCode = intent.getStringExtra("teacherCode") ?: ""

        showMessage("جارٍ التحميل...")
        loadGames()
    }

    private fun header() {
        body.addView(LeagueUi.tv(this, "🗂️ صنّف المعلومات", 24f, LeagueUi.GOLD, true))
    }

    private fun showMessage(msg: String) {
        body.removeAllViews()
        header()
        body.addView(LeagueUi.note(this, msg))
    }

    // ---------- التحميل ----------
    private fun parseGame(d: DocumentSnapshot): Game? {
        val raw = d.get("categories") as? List<*> ?: return null
        val cats = raw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            val its = (m["items"] as? List<*>)?.mapNotNull { it as? String } ?: return@mapNotNull null
            Cat(name, its)
        }
        if (cats.size < 2 || cats.sumOf { it.items.size } < 2) return null
        return Game(d.id, d.getString("title") ?: "", cats, d.getLong("createdAt") ?: 0L)
    }

    private fun loadGames() {
        if (teacherCode.isEmpty()) {
            showMessage("تعذّر تحديد المدرس")
            return
        }
        db.collection("sortGames").whereEqualTo("teacherCode", teacherCode).get()
            .addOnSuccessListener { snap ->
                games = snap.documents.mapNotNull { parseGame(it) }.sortedByDescending { it.createdAt }
                db.collection("sortResults").whereEqualTo("studentId", playerKey).get()
                    .addOnSuccessListener { r ->
                        played = r.documents.mapNotNull { it.getString("gameId") }.toSet()
                        screen = "list"
                        render()
                    }
                    .addOnFailureListener {
                        played = emptySet()
                        screen = "list"
                        render()
                    }
            }
            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    // ---------- العرض ----------
    private fun render() {
        body.removeAllViews()
        tvTimer = null
        tvStatus = null
        itemCard = null
        header()
        when (screen) {
            "list" -> renderList()
            "play" -> renderPlay()
            "final" -> renderFinal()
            else -> body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        }
    }

    private fun renderList() {
        if (games.isEmpty()) {
            body.addView(LeagueUi.note(this, "لا توجد ألعاب دلوقتي 🙂\nأول ما المدرس ينشر لعبة هتلاقيها هنا."))
            return
        }
        body.addView(LeagueUi.note(this, "اختر اللعبة:", LeagueUi.MUTED))
        games.forEach { g ->
            val done = g.id in played
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(16), dp(14), dp(16), dp(14))
            card.background = LeagueUi.bg(this, LeagueUi.CARD, 16, if (done) LeagueUi.GREEN else "#34428F")
            card.addView(LeagueUi.tv(this, g.title, 19f, LeagueUi.TEXT, true))
            val total = g.cats.sumOf { it.items.size }
            card.addView(
                LeagueUi.tv(
                    this,
                    "$total عنصر · ${g.cats.size} تصنيفات" + if (done) "  ·  ✅ لعبتها" else "",
                    13f, LeagueUi.MUTED
                )
            )
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(10)
            card.layoutParams = lp
            card.setOnClickListener { onPick(g) }
            body.addView(card)
        }
    }

    private fun onPick(g: Game) {
        if (g.id in played) {
            AlertDialog.Builder(this)
                .setTitle(g.title)
                .setMessage("لعبت اللعبة دي قبل كده ونتيجتك مسجلة في لوحة المتصدرين.")
                .setPositiveButton("🎯 تدريب (بدون حساب)") { _, _ -> beginGame(g, true) }
                .setNeutralButton("🏆 المتصدرين") { _, _ -> openBoard(g) }
                .setNegativeButton("إلغاء", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle(g.title)
                .setMessage(
                    "محاولة واحدة بس محسوبة في لوحة المتصدرين.\n" +
                            "• اسحب كل عنصر على تصنيفه الصح (أو اضغط على التصنيف).\n" +
                            "• +10 لكل تصنيف صح، و-3 لكل غلط.\n" +
                            "• لو اتعادلتوا الأسرع يكسب.\n" +
                            "• لو خرجت من التطبيق المحاولة بتنتهي."
                )
                .setPositiveButton("▶ ابدأ") { _, _ -> startScored(g) }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun openBoard(g: Game) {
        startActivity(Intent(this, SortBoardActivity::class.java).putExtra("gameId", g.id))
    }

    // ---------- اللعب ----------
    private fun startScored(g: Game) {
        resultRef(g.id).set(
            hashMapOf<String, Any>(
                "gameId" to g.id, "teacherCode" to teacherCode,
                "studentId" to playerKey, "studentName" to studentName,
                "score" to 0L, "correct" to 0L, "mistakes" to 0L,
                "total" to g.cats.sumOf { it.items.size }.toLong(), "timeMs" to 0L,
                "finished" to false, "outcome" to "playing",
                "startedAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            played = played + g.id
            beginGame(g, false)
        }.addOnFailureListener {
            Toast.makeText(this, "تعذّر البدء، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun beginGame(g: Game, prac: Boolean) {
        game = g
        practice = prac
        items = g.cats.flatMapIndexed { ci, c -> c.items.map { Item(it, ci) } }.shuffled()
        placed = IntArray(g.cats.size)
        pos = 0
        correctCount = 0
        mistakes = 0
        locked = false
        finished = false
        outcome = ""
        finalTimeMs = 0L
        confettiAdded = false
        startMs = System.currentTimeMillis()
        screen = "play"
        render()
        handler.removeCallbacks(clock)
        handler.post(clock)
    }

    // 0 عادي، 1 فوق التصنيف أثناء السحب، 2 صح، 3 غلط
    private fun styleCat(tv: TextView, state: Int) {
        val (fill, stroke) = when (state) {
            1 -> Pair("#0E5F76", LeagueUi.CYAN)
            2 -> Pair("#14532D", LeagueUi.GREEN)
            3 -> Pair("#7F1D1D", LeagueUi.RED)
            else -> Pair("#1E2A5E", "#34428F")
        }
        tv.background = LeagueUi.bg(this, fill, 16, stroke)
    }

    private fun updateStatus() {
        tvStatus?.text = "✅ $correctCount   ·   ❌ $mistakes   ·   🏅 ${score()}   ·   باقي ${items.size - pos}"
    }

    private fun renderPlay() {
        val g = game ?: return
        if (pos >= items.size) return
        body.addView(LeagueUi.tv(this, g.title, 16f, LeagueUi.MUTED))
        if (practice) body.addView(LeagueUi.note(this, "🎯 وضع التدريب (مش محسوب)", LeagueUi.CYAN))

        val t = LeagueUi.tv(this, "⏱ " + fmtTime(elapsed()), 30f, LeagueUi.GOLD, true)
        t.gravity = Gravity.CENTER
        tvTimer = t
        body.addView(t)

        val st = LeagueUi.note(this, "", LeagueUi.CYAN)
        tvStatus = st
        body.addView(st)

        // العنصر الحالي
        val card = TextView(this)
        card.text = items[pos].text
        card.textSize = 24f
        card.setTypeface(null, android.graphics.Typeface.BOLD)
        card.setTextColor(Color.WHITE)
        card.gravity = Gravity.CENTER
        card.minHeight = dp(90)
        card.setPadding(dp(14), dp(14), dp(14), dp(14))
        card.background = LeagueUi.bg(this, "#2B3A8F", 18, LeagueUi.GOLD)
        val clp = LinearLayout.LayoutParams(-1, -2)
        clp.topMargin = dp(8)
        card.layoutParams = clp
        setupDrag(card)
        itemCard = card
        body.addView(card)
        body.addView(LeagueUi.note(this, "☝️ اسحب العنصر على تصنيفه (أو اضغط على التصنيف)", LeagueUi.MUTED))

        // التصنيفات
        catCells.clear()
        var i = 0
        while (i < g.cats.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in 0..1) {
                val ci = i + k
                if (ci < g.cats.size) {
                    val c = catCell(g, ci)
                    row.addView(c)
                    catCells.add(c)
                } else {
                    val spacer = View(this)
                    row.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
            i += 2
        }
        updateStatus()
    }

    private fun catCell(g: Game, ci: Int): TextView {
        val t = TextView(this)
        t.text = "${g.cats[ci].name}\n✅ ${placed[ci]}"
        t.textSize = 17f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER
        t.minHeight = dp(96)
        t.setPadding(dp(8), dp(12), dp(8), dp(12))
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        t.layoutParams = lp
        styleCat(t, 0)
        t.setOnClickListener { place(ci) }
        t.setOnDragListener { _, ev ->
            when (ev.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!locked) styleCat(t, 1)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    if (!locked) styleCat(t, 0)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    place(ci)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    itemCard?.alpha = 1f
                    if (!locked) styleCat(t, 0)
                    true
                }
                else -> true
            }
        }
        return t
    }

    private fun setupDrag(card: TextView) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f
        var sy = 0f
        var dragging = false
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX
                    sy = e.rawY
                    dragging = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && !locked &&
                        (Math.abs(e.rawX - sx) > slop || Math.abs(e.rawY - sy) > slop)
                    ) {
                        dragging = true
                        val clip = ClipData.newPlainText("item", (v as TextView).text)
                        v.startDragAndDrop(clip, View.DragShadowBuilder(v), null, 0)
                        v.alpha = 0.4f
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            true
        }
    }

    private fun place(ci: Int) {
        if (locked || finished || screen != "play") return
        if (pos >= items.size) return
        val cur = items[pos]
        locked = true
        if (cur.cat == ci) {
            correctCount++
            placed[ci]++
            pos++
            catCells.getOrNull(ci)?.let { styleCat(it, 2) }
            updateStatus()
            handler.postDelayed({
                locked = false
                if (screen != "play") return@postDelayed
                if (pos >= items.size) completeGame() else render()
            }, 280L)
        } else {
            mistakes++
            catCells.getOrNull(ci)?.let { styleCat(it, 3) }
            itemCard?.background = LeagueUi.bg(this, "#7F1D1D", 18, LeagueUi.RED)
            updateStatus()
            handler.postDelayed({
                locked = false
                if (screen == "play") render()
            }, 600L)
        }
    }

    private fun completeGame() {
        finishGame("complete")
        screen = "final"
        render()
    }

    private fun finishGame(why: String) {
        if (finished) return
        finished = true
        outcome = why
        finalTimeMs = elapsed()
        handler.removeCallbacks(clock)
        val g = game ?: return
        if (practice) return
        resultRef(g.id).update(
            hashMapOf<String, Any>(
                "score" to score(),
                "correct" to correctCount.toLong(),
                "mistakes" to mistakes.toLong(),
                "timeMs" to finalTimeMs,
                "finished" to true,
                "outcome" to why
            )
        )
    }

    private fun confirmQuit() {
        AlertDialog.Builder(this)
            .setTitle("تخرج من اللعبة؟")
            .setMessage(if (practice) "هتخرج من التدريب." else "هتتسجل نتيجتك الحالية وتنتهي المحاولة.")
            .setPositiveButton("اخرج") { _, _ ->
                if (screen != "play") return@setPositiveButton
                finishGame("left")
                screen = if (practice) "list" else "final"
                render()
            }
            .setNegativeButton("كمّل", null)
            .show()
    }

    private fun renderFinal() {
        val total = items.size
        val done = outcome == "complete"
        val head = when {
            done && mistakes == 0 -> "🏆 ممتاز! من غير أي غلطة"
            done -> "🎉 خلصت اللعبة!"
            else -> "📵 خرجت من اللعبة"
        }
        val h = LeagueUi.tv(this, head, 24f, if (done) LeagueUi.GOLD else LeagueUi.RED, true)
        h.gravity = Gravity.CENTER
        h.setPadding(0, dp(12), 0, dp(6))
        body.addView(h)

        val p = LeagueUi.tv(this, "🏅 ${score()} نقطة", 36f, LeagueUi.GOLD, true)
        p.gravity = Gravity.CENTER
        body.addView(p)
        body.addView(
            LeagueUi.note(
                this,
                "✅ $correctCount من $total  ·  ❌ $mistakes غلط  ·  ⏱ ${fmtTime(finalTimeMs)}",
                LeagueUi.CYAN
            )
        )
        if (practice) body.addView(LeagueUi.note(this, "ده كان تدريب، مش محسوب في اللوحة."))

        val g = game
        if (!practice && g != null) {
            val b = LeagueUi.button(this, "🏆 لوحة المتصدرين", "#6366F1")
            b.setOnClickListener { openBoard(g) }
            body.addView(b)
        }
        val back = LeagueUi.button(this, "↩ رجوع للألعاب", "#475569")
        back.setOnClickListener {
            screen = "list"
            render()
        }
        body.addView(back)

        if (done && mistakes == 0 && !confettiAdded) {
            confettiAdded = true
            findViewById<ViewGroup>(android.R.id.content).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (screen == "play" && !finished && !isChangingConfigurations) {
            finishGame("left")
            screen = if (practice) "list" else "final"
            render()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
