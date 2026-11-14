package com.example.bac1

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class WheelPlayActivity : AppCompatActivity() {

    private class Res(val name: String, val q: Int, val ok: Boolean, val ans: Int)

    private val db = FirebaseFirestore.getInstance()
    private var reg: ListenerRegistration? = null
    private var timer: CountDownTimer? = null

    private var isTeacher = false
    private var myName = ""
    private var wheelId = ""
    private var teacherCode = ""

    private var wheelTitle = ""
    private var qs: List<LeagueUi.LQ> = emptyList()
    private var names: List<String> = emptyList()
    private var seconds = 30
    private var phase = "idle"
    private var chosenName = ""
    private var lastName = ""
    private var nameSpinId = 0L
    private var qSpinId = 0L
    private var qIndex = -1
    private var answer = -1
    private var correct = false
    private var used: List<Int> = emptyList()
    private var results: List<Res> = emptyList()

    private var loaded = false
    private var seenNameSpin = -1L
    private var seenQSpin = -1L
    private var namesLanded = true
    private var qLanded = true
    private var timerFor = -1L
    private var remainingSec = 0
    private var answeredFor = -1L
    private var tvTimer: TextView? = null
    private var confetti: View? = null
    private var confettiFor = -1L

    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var namesWheel: WheelView
    private lateinit var qWheel: WheelView

    private fun dp(v: Int) = LeagueUi.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this)
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

        namesWheel = WheelView(this)
        qWheel = WheelView(this)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        isTeacher = prefs.getBoolean("is_teacher", false)
        myName = prefs.getString("student_name", "") ?: ""
        wheelId = intent.getStringExtra("wheelId") ?: ""
        teacherCode = intent.getStringExtra("teacherCode") ?: ""

        showMessage("جارٍ التحميل...")
        if (wheelId.isNotEmpty()) listen() else findWheel()
    }

    private fun showMessage(msg: String) {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "🎡 عجلة الحظ", 26f, LeagueUi.GOLD, true))
        body.addView(LeagueUi.note(this, msg))
    }

    private fun findWheel() {
        if (teacherCode.isEmpty()) {
            showMessage("تعذّر تحديد المدرس")
            return
        }
        db.collection("wheels").whereEqualTo("teacherCode", teacherCode).get()
            .addOnSuccessListener { snap ->
                val active = snap.documents.filter { (it.getString("phase") ?: "") != "finished" }
                val newest = active.maxByOrNull { it.getLong("createdAt") ?: 0L }
                if (newest == null) {
                    showMessage("لا توجد عجلة حظ شغّالة دلوقتي 🙂\nأول ما المدرس يفتح عجلة هتلاقيها هنا.")
                    return@addOnSuccessListener
                }
                wheelId = newest.id
                listen()
            }
            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    private fun listen() {
        reg = db.collection("wheels").document(wheelId).addSnapshotListener { snap, e ->
            if (e != null || snap == null || !snap.exists()) return@addSnapshotListener
            onDoc(snap)
        }
    }

    private fun parseResults(raw: Any?): List<Res> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            Res(
                m["name"] as? String ?: "",
                (m["q"] as? Number)?.toInt() ?: -1,
                m["ok"] as? Boolean ?: false,
                (m["ans"] as? Number)?.toInt() ?: -1
            )
        }
    }

    private fun unusedList(): List<Int> = qs.indices.filter { it !in used }

    private fun onDoc(d: DocumentSnapshot) {
        wheelTitle = d.getString("title") ?: ""
        qs = LeagueUi.parseQuestions(d.get("questions"))
        names = (d.get("names") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        seconds = (d.getLong("seconds") ?: 30L).toInt()
        phase = d.getString("phase") ?: "idle"
        chosenName = d.getString("chosenName") ?: ""
        lastName = d.getString("lastName") ?: ""
        nameSpinId = d.getLong("nameSpinId") ?: 0L
        qSpinId = d.getLong("qSpinId") ?: 0L
        qIndex = (d.getLong("qIndex") ?: -1L).toInt()
        answer = (d.getLong("answer") ?: -1L).toInt()
        correct = d.getBoolean("correct") ?: false
        used = (d.get("used") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        results = parseResults(d.get("results"))

        namesWheel.setItems(names)
        val unused = unusedList()
        qWheel.setItems(unused.indices.map { "${it + 1}" })

        if (phase == "student" && nameSpinId != seenNameSpin) {
            seenNameSpin = nameSpinId
            val idx = names.indexOf(chosenName)
            if (!loaded || idx < 0) {
                namesLanded = true
                if (idx >= 0) namesWheel.showIndex(idx)
            } else {
                namesLanded = false
                namesWheel.spinTo(idx) {
                    namesLanded = true
                    render()
                }
            }
        }
        if (phase == "question" && qSpinId != seenQSpin) {
            seenQSpin = qSpinId
            val pos = unused.indexOf(qIndex)
            if (!loaded || pos < 0) {
                qLanded = true
                if (pos >= 0) qWheel.showIndex(pos)
            } else {
                qLanded = false
                qWheel.spinTo(pos) {
                    qLanded = true
                    render()
                }
            }
        }
        loaded = true
        render()
    }

    // ---------- العرض ----------
    private fun addWheel(w: WheelView) {
        (w.parent as? ViewGroup)?.removeView(w)
        body.addView(w, LinearLayout.LayoutParams(-1, dp(300)))
    }

    private fun header(white: Boolean = false) {
        body.addView(
            LeagueUi.tv(this, "🎡 عجلة الحظ", 26f, if (white) "#FFFFFF" else LeagueUi.GOLD, true)
        )
        val t = LeagueUi.tv(this, wheelTitle, 15f, if (white) "#E2E8F0" else LeagueUi.MUTED)
        t.setPadding(0, 0, 0, dp(10))
        body.addView(t)
    }

    private fun render() {
        tvTimer = null
        if (phase != "question") {
            timer?.cancel()
            timerFor = -1L
        }
        body.removeAllViews()
        if (phase != "result") removeConfetti()

        val bgColor = if (phase == "result") {
            if (correct) "#15803D" else "#B91C1C"
        } else LeagueUi.BG
        root.setBackgroundColor(Color.parseColor(bgColor))

        when (phase) {
            "student" -> renderStudent()
            "question" -> renderQuestion()
            "result" -> renderResult()
            "finished" -> renderFinished()
            else -> renderIdle()
        }
    }

    private fun renderIdle() {
        header()
        addWheel(namesWheel)
        body.addView(
            LeagueUi.note(this, "👥 الحاضرين: ${names.size} · الجولات: ${results.size}", LeagueUi.CYAN)
        )
        if (isTeacher) {
            val b = LeagueUi.button(this, "🎡 لفّ عجلة الأسماء", LeagueUi.GREEN)
            b.isEnabled = names.isNotEmpty()
            b.setOnClickListener { spinNames() }
            body.addView(b)
            val f = LeagueUi.button(this, "🏁 إنهاء الحصة", "#475569")
            f.setOnClickListener { commitRound("finished") }
            body.addView(f)
        } else {
            body.addView(LeagueUi.note(this, "في انتظار المدرس يلف العجلة... 🎡"))
            if (!names.contains(myName)) {
                body.addView(LeagueUi.note(this, "اسمك مش في قائمة الحاضرين، فأنت بتتفرج 👀"))
            }
        }
    }

    private fun renderStudent() {
        header()
        addWheel(namesWheel)
        if (!namesLanded) {
            body.addView(LeagueUi.note(this, "🎡 العجلة بتلف...", LeagueUi.GOLD))
            return
        }
        val mine = !isTeacher && chosenName == myName
        val box = LeagueUi.info(
            this, "🎯 الدور على: $chosenName",
            if (mine) "ده دورك! لفّ عجلة الأسئلة 🔥" else "استنى $chosenName يلف عجلة الأسئلة..."
        )
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(8)
        box.layoutParams = lp
        body.addView(box)

        if (mine) {
            val b = LeagueUi.button(this, "🎡 لفّ عجلة الأسئلة", LeagueUi.GREEN)
            b.setOnClickListener { spinQuestion() }
            body.addView(b)
        }
        if (isTeacher) {
            val b = LeagueUi.button(this, "🎡 لفّ عجلة الأسئلة بدل الطالب", "#475569")
            b.setOnClickListener { spinQuestion() }
            body.addView(b)
        }
    }

    private fun renderQuestion() {
        header()
        val q = qs.getOrNull(qIndex)
        if (q == null) {
            body.addView(LeagueUi.note(this, "تعذّر تحميل السؤال"))
            return
        }
        if (!qLanded) {
            addWheel(qWheel)
            body.addView(LeagueUi.note(this, "🎡 العجلة بتختار السؤال...", LeagueUi.GOLD))
            return
        }

        body.addView(LeagueUi.tv(this, "🎯 الدور على: $chosenName", 18f, LeagueUi.GOLD, true))
        val t = LeagueUi.tv(this, "⏱ $remainingSec", 40f, LeagueUi.GOLD, true)
        t.gravity = android.view.Gravity.CENTER
        tvTimer = t
        body.addView(t)
        body.addView(LeagueUi.questionBox(this, q.text))

        val mine = !isTeacher && chosenName == myName && answeredFor != qSpinId
        q.options.forEachIndexed { i, opt ->
            val c = LeagueUi.optionCard(this, opt, 0)
            if (mine) c.setOnClickListener { submitAnswer(i) }
            body.addView(c)
        }

        if (isTeacher) {
            body.addView(LeagueUi.note(this, "في انتظار إجابة $chosenName...", LeagueUi.CYAN))
            val b = LeagueUi.button(this, "⏭ تخطي (بدون إجابة)", LeagueUi.RED)
            b.setOnClickListener { writeResult(-1) }
            body.addView(b)
        } else if (!mine) {
            body.addView(LeagueUi.note(this, "السؤال لـ $chosenName... فكّر معاه 🤔", LeagueUi.CYAN))
        }
        startTimerIfNeeded()
    }

    private fun startTimerIfNeeded() {
        if (timerFor == qSpinId) return
        timerFor = qSpinId
        timer?.cancel()
        remainingSec = seconds
        tvTimer?.text = "⏱ $remainingSec"
        timer = object : CountDownTimer(seconds * 1000L, 250L) {
            override fun onTick(ms: Long) {
                remainingSec = ((ms + 999) / 1000).toInt()
                tvTimer?.text = "⏱ $remainingSec"
            }

            override fun onFinish() {
                remainingSec = 0
                tvTimer?.text = "⏱ 0"
                if (!isTeacher && chosenName == myName) submitAnswer(-1)
            }
        }.start()
    }

    private fun renderResult() {
        header(true)
        val q = qs.getOrNull(qIndex)
        val verdict = when {
            answer == -1 -> "⏱ انتهى الوقت"
            correct -> "✅ إجابة صحيحة!"
            else -> "❌ إجابة خاطئة"
        }
        val big = LeagueUi.tv(this, verdict, 32f, "#FFFFFF", true)
        big.gravity = android.view.Gravity.CENTER
        big.setPadding(0, dp(10), 0, dp(4))
        body.addView(big)
        val who = LeagueUi.tv(
            this, if (correct) "أحسنت يا $chosenName 🎉" else chosenName, 18f, "#FFFFFF"
        )
        who.gravity = android.view.Gravity.CENTER
        body.addView(who)

        if (q != null) {
            body.addView(LeagueUi.questionBox(this, q.text))
            q.options.forEachIndexed { i, opt ->
                val st = when {
                    i == q.correct -> 2
                    isTeacher && answer == i -> 3
                    else -> 0
                }
                body.addView(LeagueUi.optionCard(this, opt, st))
            }
            if (isTeacher) {
                val given = if (answer in q.options.indices) q.options[answer] else "لم يجب"
                body.addView(LeagueUi.note(this, "📝 إجابة $chosenName: $given", "#FFFFFF"))
            }
        }

        if (isTeacher) {
            val next = LeagueUi.button(this, "➡ الدور التالي", LeagueUi.GOLD)
            next.setTextColor(Color.parseColor(LeagueUi.BG))
            next.setOnClickListener { commitRound("idle") }
            body.addView(next)
            val f = LeagueUi.button(this, "🏁 إنهاء الحصة", "#475569")
            f.setOnClickListener { commitRound("finished") }
            body.addView(f)
        } else {
            body.addView(LeagueUi.note(this, "في انتظار المدرس للدور التالي... 🎡", "#FFFFFF"))
        }

        if (correct && confettiFor != qSpinId) {
            confettiFor = qSpinId
            removeConfetti()
            val c = ConfettiView(this)
            c.isClickable = false
            confetti = c
            findViewById<ViewGroup>(android.R.id.content).addView(c, ViewGroup.LayoutParams(-1, -1))
            body.postDelayed({ removeConfetti() }, 4500L)
        }
    }

    private fun removeConfetti() {
        val c = confetti ?: return
        (c.parent as? ViewGroup)?.removeView(c)
        confetti = null
    }

    private fun renderFinished() {
        header()
        body.addView(LeagueUi.note(this, "🏁 انتهت الحصة", LeagueUi.GOLD))
        if (results.isEmpty()) {
            body.addView(LeagueUi.note(this, "لا توجد نتائج"))
            return
        }
        val stats = results.groupBy { it.name }.map { (n, list) ->
            Triple(n, list.count { it.ok }, list.size)
        }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })

        stats.forEachIndexed { i, s ->
            val row = LeagueUi.tv(this, "${i + 1}.  ${s.first}  —  ✅ ${s.second} من ${s.third}", 17f)
            row.setPadding(dp(14), dp(12), dp(14), dp(12))
            row.background = LeagueUi.bg(this, LeagueUi.CARD, 14)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(8)
            row.layoutParams = lp
            body.addView(row)
        }
    }

    // ---------- التحكم ----------
    private fun spinNames() {
        if (!isTeacher) return
        val cand = if (names.size > 1) names.filter { it != lastName } else names
        if (cand.isEmpty()) return
        val pick = cand.random()
        db.collection("wheels").document(wheelId).update(
            hashMapOf<String, Any>(
                "phase" to "student",
                "chosenName" to pick,
                "nameSpinId" to System.currentTimeMillis(),
                "qIndex" to -1,
                "answer" to -1,
                "correct" to false
            )
        ).addOnFailureListener {
            Toast.makeText(this, "تعذّر اللف، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun spinQuestion() {
        if (phase != "student") return
        val unused = unusedList()
        val data = hashMapOf<String, Any>(
            "phase" to "question",
            "qSpinId" to System.currentTimeMillis(),
            "answer" to -1,
            "correct" to false
        )
        val pool: List<Int>
        if (unused.isEmpty()) {
            pool = qs.indices.toList()
            data["used"] = emptyList<Any>()
        } else {
            pool = unused
        }
        if (pool.isEmpty()) return
        data["qIndex"] = pool.random()
        db.collection("wheels").document(wheelId).update(data).addOnFailureListener {
            Toast.makeText(this, "تعذّر اللف، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitAnswer(i: Int) {
        if (phase != "question" || answeredFor == qSpinId) return
        answeredFor = qSpinId
        timer?.cancel()
        writeResult(i)
    }

    private fun writeResult(i: Int) {
        if (phase != "question") return
        val q = qs.getOrNull(qIndex) ?: return
        db.collection("wheels").document(wheelId).update(
            hashMapOf<String, Any>(
                "phase" to "result",
                "answer" to i,
                "correct" to (i == q.correct)
            )
        ).addOnFailureListener {
            answeredFor = -1L
            Toast.makeText(this, "تعذّر تسجيل الإجابة، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resultMaps(list: List<Res>): List<HashMap<String, Any>> =
        list.map { hashMapOf<String, Any>("name" to it.name, "q" to it.q, "ok" to it.ok, "ans" to it.ans) }

    private fun commitRound(newPhase: String) {
        if (!isTeacher) return
        val data = hashMapOf<String, Any>("phase" to newPhase)
        if (phase == "result") {
            data["results"] = resultMaps(results + Res(chosenName, qIndex, correct, answer))
            data["used"] = if (qIndex >= 0 && qIndex !in used) used + qIndex else used
            data["lastName"] = chosenName
        }
        if (newPhase == "idle") {
            data["chosenName"] = ""
            data["qIndex"] = -1
            data["answer"] = -1
            data["correct"] = false
        }
        db.collection("wheels").document(wheelId).update(data).addOnFailureListener {
            Toast.makeText(this, "تعذّر الحفظ، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        reg?.remove()
        timer?.cancel()
        namesWheel.stop()
        qWheel.stop()
        removeConfetti()
        super.onDestroy()
    }
}
