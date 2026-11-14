package com.example.bac1

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

class MillionStudentActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var timer: CountDownTimer? = null

    private var teacherCode = ""
    private var studentName = ""
    private var playerKey = ""
    private var gameId = ""
    private var gameTitle = ""
    private var qs: List<LeagueUi.LQ> = emptyList()
    private var prizes: List<Long> = emptyList()
    private var checkpoints: Set<Int> = emptySet()
    private var seconds = 30

    private var screen = "load"
    private var idx = 0
    private var selected = -1
    private val hidden = mutableSetOf<Int>()
    private var used5050 = false
    private var usedTime = false
    private var safePrize = 0L
    private var currentPrize = 0L
    private var finalPrize = 0L
    private var correctCount = 0
    private var outcome = ""
    private var finished = false
    private var remainingMs = 0L
    private var qStartMs = 0L
    private var answerTimeMs = 0L
    private var lastChosen = -1
    private var lastCorrect = false
    private var tvTimer: TextView? = null
    private var confettiAdded = false

    private lateinit var body: LinearLayout

    private fun dp(v: Int) = LeagueUi.dp(this, v)
    private fun fmt(v: Long): String = NumberFormat.getInstance(Locale.US).format(v)
    private fun resultRef() = db.collection("millionResults").document("${gameId}_$playerKey")

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

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = prefs.all["student_id"]?.toString().orEmpty()
        var studentKey = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }
        if (studentKey.isEmpty()) studentKey = "anon"
        playerKey = studentKey + "_" + studentName.replace("/", "-")
        teacherCode = intent.getStringExtra("teacherCode") ?: ""

        showMessage("جارٍ التحميل...")
        findGame()
    }

    private fun showMessage(msg: String) {
        body.removeAllViews()
        header()
        body.addView(LeagueUi.note(this, msg))
    }

    private fun header() {
        body.addView(LeagueUi.tv(this, "💰 من سيربح التحدي", 24f, LeagueUi.GOLD, true))
        if (gameTitle.isNotEmpty()) {
            val t = LeagueUi.tv(this, gameTitle, 15f, LeagueUi.MUTED)
            t.setPadding(0, 0, 0, dp(8))
            body.addView(t)
        }
    }

    private fun findGame() {
        if (teacherCode.isEmpty()) {
            showMessage("تعذّر تحديد المدرس")
            return
        }
        db.collection("millionGames").whereEqualTo("teacherCode", teacherCode).get()
            .addOnSuccessListener { snap ->
                val newest = snap.documents.maxByOrNull { it.getLong("createdAt") ?: 0L }
                if (newest == null) {
                    showMessage("لا توجد لعبة دلوقتي 🙂\nأول ما المدرس ينشر لعبة هتلاقيها هنا.")
                    return@addOnSuccessListener
                }
                gameId = newest.id
                gameTitle = newest.getString("title") ?: ""
                seconds = (newest.getLong("seconds") ?: 30L).toInt()
                qs = LeagueUi.parseQuestions(newest.get("questions"))
                prizes = (newest.get("prizes") as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
                checkpoints = (newest.get("checkpoints") as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet()
                if (qs.isEmpty() || prizes.size != qs.size) {
                    showMessage("اللعبة غير مكتملة، كلّم المدرس.")
                    return@addOnSuccessListener
                }
                resultRef().get().addOnSuccessListener { r ->
                    if (r.exists()) {
                        finalPrize = r.getLong("prize") ?: 0L
                        screen = "done"
                    } else {
                        screen = "intro"
                    }
                    render()
                }.addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
            }
            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    // ---------- العرض ----------
    private fun render() {
        body.removeAllViews()
        tvTimer = null
        header()
        when (screen) {
            "intro" -> renderIntro()
            "play" -> renderPlay()
            "reveal" -> renderReveal()
            "final" -> renderFinal()
            "done" -> renderDone()
            else -> body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        }
    }

    private fun renderIntro() {
        val ladder = StringBuilder()
        for (i in qs.indices.reversed()) {
            ladder.append("${i + 1}.  ${fmt(prizes[i])} نقطة")
            if (i in checkpoints) ladder.append("  🛡️")
            if (i > 0) ladder.append("\n")
        }
        val safeLine = if (checkpoints.isEmpty())
            "• لو غلطت بتخسر المكسب كله (مفيش نقطة أمان في اللعبة دي)."
        else "• 🛡️ نقاط الأمان: لو غلطت بترجع لآخر نقطة أمان وصلتلها."
        val rules = "• ${qs.size} سؤال، ولكل سؤال $seconds ثانية.\n" +
                "• كل إجابة صح بترفعك درجة في سلم الجوائز.\n" +
                safeLine + "\n" +
                "• الإجابة الغلط أو انتهاء الوقت بينهي اللعبة.\n" +
                "• تقدر تنسحب وتحتفظ بمكسبك الحالي.\n" +
                "• مساعدات (مرة لكل واحدة): 50:50 و ⏱ +15 ثانية.\n" +
                "• لو خرجت من التطبيق أثناء اللعب بترجع لآخر نقطة أمان.\n" +
                "• محاولة واحدة فقط."
        body.addView(LeagueUi.info(this, "📋 القواعد", rules))

        val lb = LeagueUi.info(this, "📊 سلم الجوائز", ladder.toString())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(10)
        lb.layoutParams = lp
        body.addView(lb)

        val b = LeagueUi.button(this, "▶ ابدأ اللعبة", LeagueUi.GREEN)
        b.setOnClickListener {
            b.isEnabled = false
            startGame(b)
        }
        body.addView(b)
    }

    private fun renderPlay() {
        val q = qs[idx]
        body.addView(LeagueUi.tv(this, "السؤال ${idx + 1} من ${qs.size}", 15f, LeagueUi.MUTED, true))
        val prizeTv = LeagueUi.tv(this, "💰 ${fmt(prizes[idx])} نقطة", 28f, LeagueUi.GOLD, true)
        prizeTv.gravity = Gravity.CENTER
        body.addView(prizeTv)
        if (safePrize > 0) {
            body.addView(LeagueUi.note(this, "🛡️ مضمون لك: ${fmt(safePrize)} نقطة", LeagueUi.GREEN))
        }

        val t = LeagueUi.tv(this, "⏱ ${(remainingMs + 999) / 1000}", 36f, LeagueUi.GOLD, true)
        t.gravity = Gravity.CENTER
        tvTimer = t
        body.addView(t)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(lifeline("50:50", used5050) { use5050() })
        row.addView(lifeline("⏱ +15 ثانية", usedTime) { useTime() })
        body.addView(row, LinearLayout.LayoutParams(-1, -2))

        body.addView(LeagueUi.questionBox(this, q.text))
        q.options.forEachIndexed { i, opt ->
            if (i in hidden) return@forEachIndexed
            val c = LeagueUi.optionCard(this, opt, if (selected == i) 1 else 0)
            c.setOnClickListener {
                selected = i
                render()
            }
            body.addView(c)
        }

        if (selected != -1) {
            val ok = LeagueUi.button(this, "✅ تأكيد الإجابة النهائية", LeagueUi.GREEN)
            ok.setOnClickListener {
                ok.isEnabled = false
                lockAnswer(selected)
            }
            body.addView(ok)
        }
        if (currentPrize > 0) {
            val w = LeagueUi.button(this, "🚪 انسحب واحتفظ بـ ${fmt(currentPrize)} نقطة", "#475569")
            w.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("متأكد تنسحب؟")
                    .setMessage("هتاخد ${fmt(currentPrize)} نقطة وتنهي اللعبة.")
                    .setPositiveButton("انسحب") { _, _ ->
                        if (finished || screen != "play") return@setPositiveButton
                        finishGame("walked", currentPrize)
                        screen = "final"
                        render()
                    }
                    .setNegativeButton("كمّل", null)
                    .show()
            }
            body.addView(w)
        }
    }

    private fun lifeline(label: String, used: Boolean, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 15f
        b.setTextColor(Color.WHITE)
        b.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (used) "#475569" else "#6366F1"))
        b.isEnabled = !used
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.topMargin = dp(8)
        lp.marginStart = dp(4)
        lp.marginEnd = dp(4)
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        return b
    }

    private fun renderReveal() {
        val q = qs[idx]
        body.addView(LeagueUi.tv(this, "السؤال ${idx + 1} من ${qs.size}", 15f, LeagueUi.MUTED, true))
        val verdict = when {
            lastChosen == -1 -> "⏱ انتهى الوقت"
            lastCorrect -> "✅ إجابة صحيحة!"
            else -> "❌ إجابة خاطئة"
        }
        val vt = LeagueUi.tv(this, verdict, 28f, if (lastCorrect) LeagueUi.GREEN else LeagueUi.RED, true)
        vt.gravity = Gravity.CENTER
        vt.setPadding(0, dp(8), 0, dp(4))
        body.addView(vt)
        if (lastCorrect) {
            body.addView(LeagueUi.note(this, "💰 وصلت لـ ${fmt(currentPrize)} نقطة", LeagueUi.GOLD))
        }

        body.addView(LeagueUi.questionBox(this, q.text))
        q.options.forEachIndexed { i, opt ->
            val st = when {
                i == q.correct -> 2
                i == lastChosen -> 3
                else -> 0
            }
            body.addView(LeagueUi.optionCard(this, opt, st))
        }

        if (lastCorrect && !finished) {
            val b = LeagueUi.button(this, "السؤال التالي ▶", LeagueUi.GREEN)
            b.setOnClickListener { nextQuestion() }
            body.addView(b)
        } else {
            val b = LeagueUi.button(this, "🏁 شوف نتيجتك", "#6366F1")
            b.setOnClickListener {
                screen = "final"
                render()
            }
            body.addView(b)
        }
    }

    private fun renderFinal() {
        val (head, color) = when (outcome) {
            "won" -> Pair("🏆 مبروك! كسبت التحدي كله!", LeagueUi.GOLD)
            "walked" -> Pair("🚪 انسحبت بذكاء!", LeagueUi.CYAN)
            "timeout" -> Pair("⏱ انتهى الوقت", LeagueUi.RED)
            "left" -> Pair("📵 خرجت من اللعبة", LeagueUi.RED)
            else -> Pair("❌ إجابة غلط", LeagueUi.RED)
        }
        val h = LeagueUi.tv(this, head, 24f, color, true)
        h.gravity = Gravity.CENTER
        h.setPadding(0, dp(12), 0, dp(6))
        body.addView(h)

        val p = LeagueUi.tv(this, "💰 ${fmt(finalPrize)} نقطة", 36f, LeagueUi.GOLD, true)
        p.gravity = Gravity.CENTER
        body.addView(p)
        body.addView(LeagueUi.note(this, "أجبت $correctCount إجابة صحيحة من ${qs.size}", LeagueUi.CYAN))

        addBoardButton()

        if (outcome == "won" && !confettiAdded) {
            confettiAdded = true
            findViewById<ViewGroup>(android.R.id.content).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1)
            )
        }
    }

    private fun renderDone() {
        body.addView(
            LeagueUi.info(
                this, "سبق أن شاركت ✅",
                "لك محاولة واحدة فقط في اللعبة دي.\nمكسبك: ${fmt(finalPrize)} نقطة 💰"
            )
        )
        addBoardButton()
    }

    private fun addBoardButton() {
        val b = LeagueUi.button(this, "🏆 لوحة المتصدرين", "#6366F1")
        b.setOnClickListener {
            startActivity(Intent(this, MillionBoardActivity::class.java).putExtra("gameId", gameId))
        }
        body.addView(b)
    }

    // ---------- اللعب ----------
    private fun startGame(btn: Button) {
        resultRef().set(
            hashMapOf<String, Any>(
                "gameId" to gameId, "teacherCode" to teacherCode,
                "studentId" to playerKey, "studentName" to studentName,
                "prize" to 0L, "level" to 0, "finished" to false,
                "outcome" to "playing", "timeMs" to 0L,
                "startedAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            idx = 0
            selected = -1
            hidden.clear()
            used5050 = false
            usedTime = false
            safePrize = 0L
            currentPrize = 0L
            correctCount = 0
            finished = false
            answerTimeMs = 0L
            qStartMs = System.currentTimeMillis()
            screen = "play"
            startTimer(seconds * 1000L)
            render()
        }.addOnFailureListener {
            btn.isEnabled = true
            Toast.makeText(this, "تعذّر البدء، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTimer(ms: Long) {
        timer?.cancel()
        remainingMs = ms
        timer = object : CountDownTimer(ms, 250L) {
            override fun onTick(left: Long) {
                remainingMs = left
                tvTimer?.text = "⏱ ${(left + 999) / 1000}"
            }

            override fun onFinish() {
                remainingMs = 0L
                tvTimer?.text = "⏱ 0"
                if (screen == "play") lockAnswer(-1)
            }
        }.start()
    }

    private fun use5050() {
        if (used5050 || screen != "play") return
        val q = qs[idx]
        val wrong = q.options.indices.filter { it != q.correct }.shuffled()
        val n = minOf(2, (wrong.size - 1).coerceAtLeast(0))
        hidden.clear()
        hidden.addAll(wrong.take(n))
        used5050 = true
        if (selected in hidden) selected = -1
        render()
    }

    private fun useTime() {
        if (usedTime || screen != "play") return
        usedTime = true
        startTimer(remainingMs + 15000L)
        render()
    }

    private fun lockAnswer(chosen: Int) {
        if (screen != "play") return
        timer?.cancel()
        answerTimeMs += System.currentTimeMillis() - qStartMs
        val q = qs[idx]
        lastChosen = chosen
        lastCorrect = chosen == q.correct

        if (lastCorrect) {
            correctCount++
            currentPrize = prizes[idx]
            if (idx in checkpoints) safePrize = currentPrize
            if (idx == qs.size - 1) {
                finishGame("won", currentPrize)
            } else {
                resultRef().update(
                    hashMapOf<String, Any>(
                        "level" to correctCount, "prize" to safePrize, "current" to currentPrize
                    )
                )
            }
        } else {
            finishGame(if (chosen == -1) "timeout" else "wrong", safePrize)
        }
        screen = "reveal"
        render()
    }

    private fun nextQuestion() {
        idx++
        selected = -1
        hidden.clear()
        qStartMs = System.currentTimeMillis()
        screen = "play"
        startTimer(seconds * 1000L)
        render()
    }

    private fun finishGame(why: String, prize: Long) {
        if (finished) return
        finished = true
        timer?.cancel()
        outcome = why
        finalPrize = prize
        resultRef().update(
            hashMapOf<String, Any>(
                "prize" to prize, "level" to correctCount, "finished" to true,
                "outcome" to why, "timeMs" to answerTimeMs
            )
        )
    }

    override fun onStop() {
        super.onStop()
        if (!finished && (screen == "play" || screen == "reveal") && !isChangingConfigurations) {
            if (screen == "play") answerTimeMs += System.currentTimeMillis() - qStartMs
            finishGame("left", safePrize)
            screen = "final"
            render()
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
