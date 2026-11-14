package com.example.bac1

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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.graphics.Color

class LeagueControlActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var reg: ListenerRegistration? = null
    private var scoreReg: ListenerRegistration? = null
    private var timer: CountDownTimer? = null
    private var timerFor = -2
    private var busy = false

    private var leagueId = ""
    private var leagueTitle = ""
    private var qs: List<LeagueUi.LQ> = emptyList()
    private var status = "waiting"
    private var ci = -1
    private var startedAtMs = 0L
    private var standings: List<LeagueUi.Entry> = emptyList()
    private var joinedNames: List<String> = emptyList()
    private var answeredCount = 0
    private var tvAnswered: TextView? = null
    private var tvTimer: TextView? = null

    private lateinit var body: LinearLayout

    private fun dp(v: Int) = LeagueUi.dp(this, v)

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

        leagueId = intent.getStringExtra("leagueId") ?: ""
        if (leagueId.isEmpty()) {
            body.addView(LeagueUi.note(this, "تعذّر فتح الدوري"))
            return
        }
        body.addView(LeagueUi.note(this, "جارٍ التحميل..."))

        reg = db.collection("leagues").document(leagueId).addSnapshotListener { snap, e ->
            if (e != null || snap == null || !snap.exists()) return@addSnapshotListener
            leagueTitle = snap.getString("title") ?: ""
            qs = LeagueUi.parseQuestions(snap.get("questions"))
            status = snap.getString("status") ?: "waiting"
            ci = (snap.getLong("currentIndex") ?: -1L).toInt()
            startedAtMs = snap.getTimestamp("startedAt")?.toDate()?.time ?: 0L
            standings = LeagueUi.parse(snap.get("standings"))
            render()
        }

        scoreReg = db.collection("leagueScores").whereEqualTo("leagueId", leagueId)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener
                joinedNames = snap.documents.map { it.getString("studentName") ?: "طالب" }
                answeredCount = snap.documents.count { (it.getLong("lastAnswered") ?: -1L) == ci.toLong() }
                if (status == "waiting") render()
                else if (status == "question") tvAnswered?.text = answeredText()
            }
    }

    private fun answeredText() = "✍️ أجاب $answeredCount من ${joinedNames.size}"

    // ---------- العرض ----------
    private fun render() {
        tvAnswered = null
        tvTimer = null
        if (status != "question") {
            timer?.cancel()
            timerFor = -2
        }
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🏆 $leagueTitle", 22f, LeagueUi.GOLD, true))

        when (status) {
            "waiting" -> renderWaiting()
            "question" -> renderQuestion()
            "reveal" -> renderReveal()
            "finished" -> renderFinished()
            else -> renderWaiting()
        }
    }

    private fun renderWaiting() {
        val names = if (joinedNames.isEmpty()) "لسه محدش انضم"
        else joinedNames.joinToString("، ")
        val box = LeagueUi.info(
            this, "👥 المنضمّون: ${joinedNames.size}",
            names + "\n\nقول للطلاب يدخلوا: المدرس ← الألعاب ← دوري الأبطال ← انضم."
        )
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(10)
        box.layoutParams = lp
        body.addView(box)

        val b = LeagueUi.button(this, "▶ ابدأ الدوري (السؤال 1)", LeagueUi.GREEN)
        b.isEnabled = qs.isNotEmpty()
        b.setOnClickListener { startQuestion(0) }
        body.addView(b)
    }

    private fun renderQuestion() {
        val q = qs.getOrNull(ci)
        if (q == null) {
            body.addView(LeagueUi.note(this, "تعذّر تحميل السؤال"))
            return
        }
        body.addView(
            LeagueUi.tv(this, "السؤال ${ci + 1} من ${qs.size}  ·  🏅 ${q.points} نقطة", 15f, LeagueUi.MUTED, true)
        )
        val t = LeagueUi.tv(this, "", 40f, LeagueUi.GOLD, true)
        t.gravity = Gravity.CENTER
        tvTimer = t
        body.addView(t)

        body.addView(LeagueUi.questionBox(this, q.text))
        q.options.forEachIndexed { i, opt ->
            body.addView(LeagueUi.optionCard(this, opt, if (i == q.correct) 2 else 0))
        }

        val a = LeagueUi.note(this, answeredText(), LeagueUi.CYAN)
        tvAnswered = a
        body.addView(a)

        val b = LeagueUi.button(this, "⏹ إنهاء السؤال وكشف الإجابة", LeagueUi.RED)
        b.setOnClickListener { revealNow() }
        body.addView(b)

        if (timerFor != ci) {
            timerFor = ci
            startTimer(q)
        }
    }

    private fun startTimer(q: LeagueUi.LQ) {
        timer?.cancel()
        val total = q.seconds * 1000L
        val elapsed = if (startedAtMs > 0) System.currentTimeMillis() - startedAtMs else 0L
        val remaining = (total - elapsed).coerceAtLeast(0L)
        // 1.5 ثانية سماح لوصول آخر الإجابات قبل الكشف
        timer = object : CountDownTimer(remaining + 1500L, 250) {
            override fun onTick(ms: Long) {
                val s = ((ms - 1500L).coerceAtLeast(0L) + 999) / 1000
                tvTimer?.text = "⏱ $s"
            }

            override fun onFinish() {
                tvTimer?.text = "⏱ 0"
                revealNow()
            }
        }.start()
    }

    private fun renderReveal() {
        val q = qs.getOrNull(ci)
        if (q != null) {
            body.addView(
                LeagueUi.tv(this, "نتيجة السؤال ${ci + 1} من ${qs.size}", 15f, LeagueUi.MUTED, true)
            )
            body.addView(LeagueUi.questionBox(this, q.text))
            q.options.forEachIndexed { i, opt ->
                body.addView(LeagueUi.optionCard(this, opt, if (i == q.correct) 2 else 0))
            }
        }
        val h = LeagueUi.tv(this, "🏆 الترتيب الحالي", 18f, LeagueUi.GOLD, true)
        h.setPadding(0, dp(16), 0, dp(4))
        body.addView(h)
        body.addView(LeagueUi.standingsView(this, standings, "", 10))

        val last = ci >= qs.size - 1
        val b = if (last) LeagueUi.button(this, "🏁 إعلان الفائز وإنهاء الدوري", LeagueUi.GOLD)
        else LeagueUi.button(this, "➡ السؤال التالي (${ci + 2})", LeagueUi.GREEN)
        if (last) b.setTextColor(Color.parseColor(LeagueUi.BG))
        b.setOnClickListener { if (last) finishLeague() else startQuestion(ci + 1) }
        body.addView(b)
    }

    private fun renderFinished() {
        body.addView(LeagueUi.note(this, "🏁 انتهى الدوري", LeagueUi.GOLD))
        if (standings.isEmpty()) {
            body.addView(LeagueUi.note(this, "لا توجد نتائج"))
            return
        }
        val w = LeagueUi.tv(this, "👑 البطل: ${standings[0].name}", 22f, LeagueUi.GOLD, true)
        w.gravity = Gravity.CENTER
        w.setPadding(0, dp(8), 0, dp(12))
        body.addView(w)
        body.addView(LeagueUi.podium(this, standings))
        val h = LeagueUi.tv(this, "الترتيب النهائي", 18f, LeagueUi.GOLD, true)
        h.setPadding(0, dp(16), 0, dp(4))
        body.addView(h)
        body.addView(LeagueUi.standingsView(this, standings, "", 30))
    }

    // ---------- التحكم ----------
    private fun startQuestion(n: Int) {
        if (busy) return
        busy = true
        db.collection("leagues").document(leagueId).update(
            hashMapOf<String, Any>(
                "status" to "question",
                "currentIndex" to n,
                "startedAt" to FieldValue.serverTimestamp()
            )
        ).addOnCompleteListener { busy = false }
    }

    private fun revealNow() {
        if (busy || status != "question") return
        busy = true
        timer?.cancel()
        db.collection("leagueScores").whereEqualTo("leagueId", leagueId).get()
            .addOnSuccessListener { snap ->
                val st = LeagueUi.build(snap.documents, standings)
                db.collection("leagues").document(leagueId).update(
                    hashMapOf<String, Any>("standings" to st, "status" to "reveal")
                ).addOnCompleteListener { busy = false }
            }
            .addOnFailureListener {
                busy = false
                Toast.makeText(this, "تعذّر كشف النتيجة، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
            }
    }

    private fun finishLeague() {
        if (busy) return
        busy = true
        db.collection("leagues").document(leagueId)
            .update("status", "finished")
            .addOnCompleteListener { busy = false }
    }

    override fun onDestroy() {
        reg?.remove()
        scoreReg?.remove()
        timer?.cancel()
        super.onDestroy()
    }
}
