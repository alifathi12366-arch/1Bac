package com.example.bac1

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class LeagueStudentActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var reg: ListenerRegistration? = null
    private var timer: CountDownTimer? = null

    private var teacherCode = ""
    private var studentName = ""
    private var playerKey = ""
    private var leagueId = ""
    private var offset = 0L

    private var leagueTitle = ""
    private var qs: List<LeagueUi.LQ> = emptyList()
    private var status = "waiting"
    private var ci = -1
    private var startedAtMs = 0L
    private var standings: List<LeagueUi.Entry> = emptyList()
    private var joined = false
    private var checkedQi = -1
    private var answeredQi = -1
    private var chosenIdx = -1
    private var confettiShown = false

    private lateinit var body: LinearLayout

    private fun dp(v: Int) = LeagueUi.dp(this, v)
    private fun nowServer() = System.currentTimeMillis() + offset

    private fun scoreRef() = db.collection("leagueScores").document("${leagueId}_$playerKey")
    private fun answerRef(qi: Int) = db.collection("leagueAnswers").document("${leagueId}_${playerKey}_$qi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        fetchOffset(studentKey) { findLeague() }
    }

    // فرق الوقت بين موبايلك والسيرفر (عشان العدّاد يبقى عادل للكل)
    private fun fetchOffset(key: String, after: () -> Unit) {
        val ref = db.collection("serverClock").document(key)
        ref.set(hashMapOf<String, Any>("t" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                ref.get().addOnSuccessListener { d ->
                    val ms = d.getTimestamp("t")?.toDate()?.time
                    if (ms != null) offset = ms - System.currentTimeMillis()
                    after()
                }.addOnFailureListener { after() }
            }
            .addOnFailureListener { after() }
    }

    private fun showMessage(msg: String) {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🏆 دوري الأبطال", 26f, LeagueUi.GOLD, true))
        body.addView(LeagueUi.note(this, msg))
    }

    private fun findLeague() {
        if (teacherCode.isEmpty()) {
            showMessage("تعذّر تحديد المدرس")
            return
        }
        db.collection("leagues").whereEqualTo("teacherCode", teacherCode).get()
            .addOnSuccessListener { snap ->
                val newest = snap.documents.maxByOrNull { it.getLong("createdAt") ?: 0L }
                if (newest == null) {
                    showMessage("لا يوجد دوري أبطال دلوقتي 🙂\nأول ما المدرس يفتح دوري هتلاقيه هنا.")
                    return@addOnSuccessListener
                }
                leagueId = newest.id
                scoreRef().get().addOnSuccessListener { s ->
                    joined = s.exists()
                    listen()
                }.addOnFailureListener { listen() }
            }
            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    private fun listen() {
        reg = db.collection("leagues").document(leagueId).addSnapshotListener { snap, e ->
            if (e != null || snap == null || !snap.exists()) return@addSnapshotListener
            onLeague(snap)
        }
    }

    private fun onLeague(d: DocumentSnapshot) {
        leagueTitle = d.getString("title") ?: ""
        qs = LeagueUi.parseQuestions(d.get("questions"))
        status = d.getString("status") ?: "waiting"
        ci = (d.getLong("currentIndex") ?: -1L).toInt()
        startedAtMs = d.getTimestamp("startedAt")?.toDate()?.time ?: 0L
        standings = LeagueUi.parse(d.get("standings"))
        render()
    }

    // ---------- العرض ----------
    private fun render() {
        timer?.cancel()
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🏆 دوري الأبطال", 26f, LeagueUi.GOLD, true))
        val t = LeagueUi.tv(this, leagueTitle, 15f, LeagueUi.MUTED)
        t.setPadding(0, 0, 0, dp(10))
        body.addView(t)

        when (status) {
            "waiting" -> renderWaiting()
            "question" -> renderQuestion()
            "reveal" -> renderReveal()
            "finished" -> renderFinished()
            else -> renderWaiting()
        }
    }

    private fun joinButtons(head: String, text: String, label: String) {
        body.addView(LeagueUi.info(this, head, text))
        val b = LeagueUi.button(this, label, LeagueUi.GREEN)
        b.setOnClickListener { join() }
        body.addView(b)
    }

    private fun join() {
        scoreRef().set(
            hashMapOf<String, Any>(
                "leagueId" to leagueId, "studentId" to playerKey,
                "studentName" to studentName, "joinedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            joined = true
            render()
        }.addOnFailureListener {
            Toast.makeText(this, "تعذّر الانضمام، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderWaiting() {
        if (!joined) {
            joinButtons("جاهز تنافس؟ 💪", "انضم للدوري وانتظر المدرس يبدأ الأسئلة.", "🏁 انضم للدوري")
        } else {
            body.addView(
                LeagueUi.info(this, "تم تسجيلك ✅", "في انتظار المدرس لبدء الدوري... خليك جاهز 🔥")
            )
        }
    }

    private fun renderQuestion() {
        val q = qs.getOrNull(ci)
        if (q == null) {
            body.addView(LeagueUi.note(this, "جارٍ تجهيز السؤال..."))
            return
        }
        if (!joined) {
            joinButtons("الدوري شغّال دلوقتي! ⚡", "انضم وكمّل من السؤال الحالي.", "🏁 انضم الآن")
            return
        }
        if (checkedQi != ci) {
            checkAnswer(ci)
            return
        }

        val total = q.seconds * 1000L
        val elapsed = if (startedAtMs > 0) nowServer() - startedAtMs else 0L
        val remaining = total - elapsed
        val answered = answeredQi == ci

        body.addView(
            LeagueUi.tv(this, "السؤال ${ci + 1} من ${qs.size}  ·  🏅 ${q.points} نقطة", 15f, LeagueUi.MUTED, true)
        )
        val tvTimer = LeagueUi.tv(this, "", 40f, LeagueUi.GOLD, true)
        tvTimer.gravity = Gravity.CENTER
        body.addView(tvTimer)
        body.addView(LeagueUi.questionBox(this, q.text))

        val cards = mutableListOf<TextView>()
        q.options.forEachIndexed { i, opt ->
            val c = LeagueUi.optionCard(this, opt, if (answered && chosenIdx == i) 1 else 0)
            body.addView(c)
            cards.add(c)
        }

        if (answered) {
            tvTimer.text = "✅"
            body.addView(LeagueUi.note(this, "تم تسجيل إجابتك. استنى باقي الزمايل... 👀", LeagueUi.CYAN))
            return
        }
        if (remaining <= 0) {
            tvTimer.text = "⏱ 0"
            body.addView(LeagueUi.note(this, "انتهى وقت السؤال ⏱"))
            return
        }

        cards.forEachIndexed { i, c -> c.setOnClickListener { choose(i) } }

        timer = object : CountDownTimer(remaining, 200) {
            override fun onTick(ms: Long) {
                tvTimer.text = "⏱ ${(ms + 999) / 1000}"
            }

            override fun onFinish() {
                tvTimer.text = "⏱ 0"
                cards.forEach {
                    it.setOnClickListener(null)
                    it.isClickable = false
                }
                body.addView(LeagueUi.note(this@LeagueStudentActivity, "انتهى وقت السؤال ⏱"))
            }
        }.start()
    }

    // لو الطالب جاوب قبل كده (مثلاً قفل التطبيق ورجع)
    private fun checkAnswer(qi: Int) {
        body.addView(LeagueUi.note(this, "جارٍ تحميل السؤال..."))
        answerRef(qi).get().addOnSuccessListener { d ->
            checkedQi = qi
            if (d.exists()) {
                answeredQi = qi
                chosenIdx = (d.getLong("chosen") ?: -1L).toInt()
            }
            if (ci == qi) render()
        }.addOnFailureListener {
            checkedQi = qi
            if (ci == qi) render()
        }
    }

    private fun choose(i: Int) {
        val q = qs.getOrNull(ci) ?: return
        val qi = ci
        val limit = q.seconds * 1000L
        val elapsed = if (startedAtMs > 0) nowServer() - startedAtMs else 0L
        if (elapsed > limit + 1500L) {
            render()
            return
        }
        val timeMs = elapsed.coerceIn(0L, limit)
        val correct = i == q.correct
        val points = if (correct) q.points.toLong() else 0L

        answeredQi = qi
        chosenIdx = i
        checkedQi = qi

        val batch = db.batch()
        batch.set(
            answerRef(qi),
            hashMapOf<String, Any>(
                "leagueId" to leagueId, "studentId" to playerKey, "studentName" to studentName,
                "qIndex" to qi, "chosen" to i, "correct" to correct,
                "points" to points, "timeMs" to timeMs
            )
        )
        batch.set(
            scoreRef(),
            hashMapOf<String, Any>(
                "leagueId" to leagueId, "studentId" to playerKey, "studentName" to studentName,
                "lastAnswered" to qi,
                "score" to FieldValue.increment(points),
                "correct" to FieldValue.increment(if (correct) 1L else 0L),
                "timeMs" to FieldValue.increment(if (correct) timeMs else 0L)
            ),
            SetOptions.merge()
        )
        batch.commit().addOnFailureListener {
            Toast.makeText(this, "تعذّر تسجيل إجابتك، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    private fun renderReveal() {
        val q = qs.getOrNull(ci)
        if (q == null) {
            body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
            return
        }
        if (joined && checkedQi != ci) {
            checkAnswer(ci)
            return
        }
        val answered = joined && answeredQi == ci
        val mine = standings.firstOrNull { it.id == playerKey }

        body.addView(
            LeagueUi.tv(this, "نتيجة السؤال ${ci + 1} من ${qs.size}", 15f, LeagueUi.MUTED, true)
        )
        body.addView(LeagueUi.questionBox(this, q.text))
        q.options.forEachIndexed { i, opt ->
            val st = when {
                i == q.correct -> 2
                answered && chosenIdx == i -> 3
                else -> 0
            }
            body.addView(LeagueUi.optionCard(this, opt, st))
        }

        val result: Pair<String, String> = when {
            !joined -> Pair("👀 أنت بتتفرج", LeagueUi.MUTED)
            !answered -> Pair("⏱ ما جاوبتش السؤال ده", LeagueUi.MUTED)
            chosenIdx == q.correct -> Pair("🎉 إجابة صحيحة! +${q.points} نقطة", LeagueUi.GREEN)
            else -> Pair("❌ إجابة غلط", LeagueUi.RED)
        }
        body.addView(LeagueUi.note(this, result.first, result.second))

        val h = LeagueUi.tv(this, "🏆 الترتيب الحالي", 18f, LeagueUi.GOLD, true)
        h.setPadding(0, dp(12), 0, dp(4))
        body.addView(h)
        if (standings.isEmpty()) {
            body.addView(LeagueUi.note(this, "جارٍ حساب الترتيب..."))
        } else {
            body.addView(LeagueUi.standingsView(this, standings, playerKey, 10))
            if (mine != null) {
                body.addView(LeagueUi.note(this, "ترتيبك الآن: #${mine.rank} · ${mine.score} نقطة", LeagueUi.CYAN))
            }
        }
        body.addView(LeagueUi.note(this, "في انتظار المدرس للسؤال التالي... 🔥"))
    }

    private fun renderFinished() {
        body.addView(LeagueUi.note(this, "🏁 انتهى دوري الأبطال", LeagueUi.GOLD))
        if (standings.isEmpty()) {
            body.addView(LeagueUi.note(this, "لا توجد نتائج"))
            return
        }
        val w = LeagueUi.tv(this, "👑 البطل: ${standings[0].name}", 22f, LeagueUi.GOLD, true)
        w.gravity = Gravity.CENTER
        w.setPadding(0, dp(8), 0, dp(12))
        body.addView(w)
        body.addView(LeagueUi.podium(this, standings))

        val mine = standings.firstOrNull { it.id == playerKey }
        if (mine != null) {
            body.addView(LeagueUi.note(this, "ترتيبك: #${mine.rank} · ${mine.score} نقطة", LeagueUi.CYAN))
        }
        val h = LeagueUi.tv(this, "الترتيب النهائي", 18f, LeagueUi.GOLD, true)
        h.setPadding(0, dp(12), 0, dp(4))
        body.addView(h)
        body.addView(LeagueUi.standingsView(this, standings, playerKey, 10))

        if (mine != null && mine.rank <= 3 && !confettiShown) {
            confettiShown = true
            findViewById<ViewGroup>(android.R.id.content).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1)
            )
        }
    }

    override fun onDestroy() {
        reg?.remove()
        timer?.cancel()
        super.onDestroy()
    }
}
