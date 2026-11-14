package com.example.bac1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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

class MatchStudentActivity : AppCompatActivity() {

    private class Game(
        val id: String, val title: String, val labelA: String, val labelB: String,
        val pairs: List<Pair<String, String>>, val createdAt: Long
    )

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
    private var orderA: List<Int> = emptyList()
    private var orderB: List<Int> = emptyList()
    private val matchedPairs = mutableSetOf<Int>()
    private val cellsA = mutableListOf<TextView>()
    private val cellsB = mutableListOf<TextView>()
    private var selA = -1
    private var selB = -1
    private var locked = false
    private var mistakes = 0
    private var finished = false
    private var outcome = ""
    private var startMs = 0L
    private var finalTimeMs = 0L
    private var confettiAdded = false
    private var tvTimer: TextView? = null
    private var tvStatus: TextView? = null

    private lateinit var body: LinearLayout

    private fun dp(v: Int) = LeagueUi.dp(this, v)
    private fun resultRef(gameId: String) = db.collection("matchResults").document("${gameId}_$playerKey")

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

    private fun score(): Long = maxOf(0L, matchedPairs.size * 10L - mistakes * 3L)

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
        body.addView(LeagueUi.tv(this, "🔗 وصّل الكلمة بمعناها", 24f, LeagueUi.GOLD, true))
    }

    private fun showMessage(msg: String) {
        body.removeAllViews()
        header()
        body.addView(LeagueUi.note(this, msg))
    }

    // ---------- التحميل ----------
    private fun parseGame(d: DocumentSnapshot): Game? {
        val raw = d.get("pairs") as? List<*> ?: return null
        val pairs = raw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val a = m["a"] as? String ?: return@mapNotNull null
            val b = m["b"] as? String ?: return@mapNotNull null
            Pair(a, b)
        }
        if (pairs.size < 2) return null
        return Game(
            d.id, d.getString("title") ?: "",
            d.getString("labelA") ?: "العمود الأول",
            d.getString("labelB") ?: "العمود الثاني",
            pairs, d.getLong("createdAt") ?: 0L
        )
    }

    private fun loadGames() {
        if (teacherCode.isEmpty()) {
            showMessage("تعذّر تحديد المدرس")
            return
        }
        db.collection("matchGames").whereEqualTo("teacherCode", teacherCode).get()
            .addOnSuccessListener { snap ->
                games = snap.documents.mapNotNull { parseGame(it) }.sortedByDescending { it.createdAt }
                db.collection("matchResults").whereEqualTo("studentId", playerKey).get()
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
            card.addView(
                LeagueUi.tv(
                    this,
                    "${g.pairs.size} أزواج · ${g.labelA} ↔ ${g.labelB}" + if (done) "  ·  ✅ لعبتها" else "",
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
                            "• +10 لكل توصيل صح، و-3 لكل غلط.\n" +
                            "• لو اتعادلتوا الأسرع يكسب.\n" +
                            "• لو خرجت من التطبيق المحاولة بتنتهي."
                )
                .setPositiveButton("▶ ابدأ") { _, _ -> startScored(g) }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun openBoard(g: Game) {
        startActivity(Intent(this, MatchBoardActivity::class.java).putExtra("gameId", g.id))
    }

    // ---------- اللعب ----------
    private fun startScored(g: Game) {
        resultRef(g.id).set(
            hashMapOf<String, Any>(
                "gameId" to g.id, "teacherCode" to teacherCode,
                "studentId" to playerKey, "studentName" to studentName,
                "score" to 0L, "matched" to 0L, "mistakes" to 0L,
                "total" to g.pairs.size.toLong(), "timeMs" to 0L,
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
        val n = g.pairs.size
        orderA = (0 until n).shuffled()
        var ob = (0 until n).shuffled()
        var tries = 0
        // نتأكد إن مفيش عنصر يظهر قصاد مقابله مباشرة
        while (tries < 60 && ob.indices.any { ob[it] == orderA[it] }) {
            ob = (0 until n).shuffled()
            tries++
        }
        orderB = ob
        matchedPairs.clear()
        selA = -1
        selB = -1
        locked = false
        mistakes = 0
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

    private fun colHeader(s: String): TextView {
        val t = LeagueUi.tv(this, s, 15f, LeagueUi.GOLD, true)
        t.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.setMargins(dp(4), dp(10), dp(4), dp(2))
        t.layoutParams = lp
        return t
    }

    private fun cell(s: String): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = 15f
        t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER
        t.minHeight = dp(64)
        t.maxLines = 5
        t.setPadding(dp(8), dp(10), dp(8), dp(10))
        val lp = LinearLayout.LayoutParams(0, -1, 1f)
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        t.layoutParams = lp
        styleCell(t, 0)
        return t
    }

    // 0 عادي، 1 مختار، 2 تم توصيله، 3 غلط
    private fun styleCell(tv: TextView, state: Int) {
        val (fill, stroke) = when (state) {
            1 -> Pair("#0E5F76", LeagueUi.CYAN)
            2 -> Pair("#14532D", LeagueUi.GREEN)
            3 -> Pair("#7F1D1D", LeagueUi.RED)
            else -> Pair("#1E2A5E", "#34428F")
        }
        tv.background = LeagueUi.bg(this, fill, 14, stroke)
    }

    private fun renderPlay() {
        val g = game ?: return
        body.addView(LeagueUi.tv(this, g.title, 16f, LeagueUi.MUTED))
        if (practice) body.addView(LeagueUi.note(this, "🎯 وضع التدريب (مش محسوب)", LeagueUi.CYAN))

        val t = LeagueUi.tv(this, "⏱ " + fmtTime(elapsed()), 30f, LeagueUi.GOLD, true)
        t.gravity = Gravity.CENTER
        tvTimer = t
        body.addView(t)

        val st = LeagueUi.note(this, "", LeagueUi.CYAN)
        tvStatus = st
        body.addView(st)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(colHeader(g.labelA))
        head.addView(colHeader(g.labelB))
        body.addView(head, LinearLayout.LayoutParams(-1, -2))

        cellsA.clear()
        cellsB.clear()
        for (i in g.pairs.indices) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val ca = cell(g.pairs[orderA[i]].first)
            val cb = cell(g.pairs[orderB[i]].second)
            ca.setOnClickListener { tapA(i) }
            cb.setOnClickListener { tapB(i) }
            row.addView(ca)
            row.addView(cb)
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
            cellsA.add(ca)
            cellsB.add(cb)
        }
        refreshCells()
    }

    private fun refreshCells() {
        if (cellsA.size != orderA.size || cellsB.size != orderB.size) return
        for (i in cellsA.indices) {
            val state = if (orderA[i] in matchedPairs) 2 else if (selA == i) 1 else 0
            styleCell(cellsA[i], state)
        }
        for (j in cellsB.indices) {
            val state = if (orderB[j] in matchedPairs) 2 else if (selB == j) 1 else 0
            styleCell(cellsB[j], state)
        }
        updateStatus()
    }

    private fun updateStatus() {
        val total = game?.pairs?.size ?: 0
        tvStatus?.text = "✅ ${matchedPairs.size} من $total   ·   ❌ $mistakes   ·   🏅 ${score()}"
    }

    private fun tapA(i: Int) {
        if (locked || finished) return
        if (orderA[i] in matchedPairs) return
        selA = if (selA == i) -1 else i
        refreshCells()
        tryResolve()
    }

    private fun tapB(j: Int) {
        if (locked || finished) return
        if (orderB[j] in matchedPairs) return
        selB = if (selB == j) -1 else j
        refreshCells()
        tryResolve()
    }

    private fun tryResolve() {
        if (selA == -1 || selB == -1) return
        val pa = orderA[selA]
        val pb = orderB[selB]
        if (pa == pb) {
            matchedPairs.add(pa)
            selA = -1
            selB = -1
            refreshCells()
            if (matchedPairs.size == (game?.pairs?.size ?: -1)) completeGame()
        } else {
            mistakes++
            locked = true
            styleCell(cellsA[selA], 3)
            styleCell(cellsB[selB], 3)
            updateStatus()
            handler.postDelayed({
                selA = -1
                selB = -1
                locked = false
                if (screen == "play") refreshCells()
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
                "matched" to matchedPairs.size.toLong(),
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
        val g = game
        val total = g?.pairs?.size ?: 0
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
                "✅ ${matchedPairs.size} من $total  ·  ❌ $mistakes غلط  ·  ⏱ ${fmtTime(finalTimeMs)}",
                LeagueUi.CYAN
            )
        )
        if (practice) body.addView(LeagueUi.note(this, "ده كان تدريب، مش محسوب في اللوحة."))

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
