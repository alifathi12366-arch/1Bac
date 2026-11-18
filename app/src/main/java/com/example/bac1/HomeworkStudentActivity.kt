package com.example.bac1

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class HomeworkStudentActivity : AppCompatActivity() {

    private class Hw(val id: String, val title: String, val deadline: Long, val qs: List<CourseUtil.Q>)
    private class Sub(val score: Int, val total: Int, val answers: List<Int>)

    private val db = FirebaseFirestore.getInstance()
    private lateinit var body: LinearLayout
    private var teacherCode = ""
    private var playerKey = ""
    private var studentName = ""
    private var offset = 0L
    private var hws: List<Hw> = emptyList()
    private val subs = HashMap<String, Sub>()
    private var screen = "load"
    private var cur: Hw? = null
    private var answers = IntArray(0)
    private var qi = 0
    private var sending = false

    private fun dp(v: Int) = LeagueUi.dp(this, v)
    private fun now() = System.currentTimeMillis() + offset
    private fun draftKey(id: String) = "hw_${id}_$playerKey"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        body = CourseUtil.page(this)
        val st = CourseUtil.student(this)
        playerKey = st.first
        studentName = st.second
        teacherCode = intent.getStringExtra("teacherCode") ?: ""

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == "solve" || screen == "result") {
                    screen = "list"
                    render()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        message("جارٍ التحميل...")
        load()
    }

    private fun message(msg: String) {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "📚 الواجبات", 24f, LeagueUi.GOLD, true))
        body.addView(LeagueUi.note(this, msg))
    }

    private fun scrollTop() {
        (body.parent as? ScrollView)?.post { (body.parent as? ScrollView)?.scrollTo(0, 0) }
    }

    // ---------- التحميل ----------
    private fun load() {
        if (teacherCode.isEmpty()) {
            message("تعذّر تحديد المدرس")
            return
        }
        HomeworkUtil.serverOffset(db, playerKey) { off ->
            offset = off
            CourseUtil.myGroups(db, teacherCode, studentName) { mine ->
                db.collection("homeworks").whereEqualTo("teacherCode", teacherCode).get()
                    .addOnSuccessListener { snap ->
                        hws = snap.documents.mapNotNull { d ->
                            val gs = (d.get("groups") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            if (gs.isNotEmpty() && gs.none { it in mine }) null
                            else Hw(
                                d.id, d.getString("title") ?: "", d.getLong("deadline") ?: 0L,
                                CourseUtil.parseQuestions(d.get("questions"))
                            )
                        }
                        db.collection("homeworkSubmissions").whereEqualTo("studentId", playerKey).get()
                            .addOnSuccessListener { r ->
                                subs.clear()
                                for (s in r.documents) {
                                    val id = s.getString("homeworkId") ?: continue
                                    subs[id] = Sub(
                                        (s.getLong("score") ?: 0L).toInt(), (s.getLong("total") ?: 0L).toInt(),
                                        (s.get("answers") as? List<*>)?.map { (it as? Number)?.toInt() ?: -1 } ?: emptyList()
                                    )
                                }
                                screen = "list"
                                render()
                            }
                            .addOnFailureListener { message("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
                    }
                    .addOnFailureListener { message("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
            }
        }
    }

    private fun render() {
        body.removeAllViews()
        body.addView(LeagueUi.tv(this, "📚 الواجبات", 24f, LeagueUi.GOLD, true))
        when (screen) {
            "list" -> renderList()
            "solve" -> renderSolve()
            "result" -> renderResult()
            else -> body.addView(LeagueUi.note(this, "جارٍ التحميل..."))
        }
    }

    // ---------- القايمة ----------
    private fun renderList() {
        if (hws.isEmpty()) {
            body.addView(LeagueUi.note(this, "لا توجد واجبات دلوقتي 🙂"))
            return
        }
        val t = now()
        val open = hws.filter { it.id !in subs && it.deadline > t }.sortedBy { it.deadline }
        val rest = hws.filter { it.id in subs || it.deadline <= t }.sortedByDescending { it.deadline }
        (open + rest).forEach { hw ->
            val sub = subs[hw.id]
            val over = hw.deadline <= t
            val (chip, color) = when {
                sub != null -> Pair("✅ سلّمت: ${sub.score}/${sub.total}", LeagueUi.GREEN)
                over -> Pair("❌ فاتك الموعد", LeagueUi.RED)
                else -> Pair("⏳ باقي ${HomeworkUtil.remaining(hw.deadline - t)}", LeagueUi.GOLD)
            }
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(16), dp(14), dp(16), dp(14))
            card.background = LeagueUi.bg(this, LeagueUi.CARD, 16, color)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(10)
            card.layoutParams = lp
            card.addView(LeagueUi.tv(this, hw.title, 19f, LeagueUi.TEXT, true))
            card.addView(LeagueUi.tv(this, "${hw.qs.size} سؤال · آخر موعد ${HomeworkUtil.fmtDate(hw.deadline)}", 12f, LeagueUi.MUTED))
            card.addView(LeagueUi.tv(this, chip, 14f, color, true))
            card.setOnClickListener { openHw(hw) }
            body.addView(card)
        }
    }

    private fun openHw(hw: Hw) {
        cur = hw
        if (hw.id in subs || hw.deadline <= now()) {
            screen = "result"
        } else {
            val saved = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString(draftKey(hw.id), "") ?: ""
            val parts = saved.split(",")
            answers = if (parts.size == hw.qs.size) IntArray(hw.qs.size) { parts[it].toIntOrNull() ?: -1 }
            else IntArray(hw.qs.size) { -1 }
            qi = answers.indexOfFirst { it < 0 }.coerceAtLeast(0)
            screen = "solve"
        }
        render()
        scrollTop()
    }

    // ---------- الحل ----------
    private fun saveDraft() {
        val hw = cur ?: return
        getSharedPreferences("bac1_prefs", MODE_PRIVATE).edit()
            .putString(draftKey(hw.id), answers.joinToString(",")).apply()
    }

    private fun renderSolve() {
        val hw = cur ?: return
        val q = hw.qs.getOrNull(qi) ?: return
        val n = hw.qs.size
        val done = answers.count { it >= 0 }
        body.addView(LeagueUi.tv(this, hw.title, 16f, LeagueUi.MUTED, true))
        body.addView(LeagueUi.note(this, "⏳ باقي ${HomeworkUtil.remaining(hw.deadline - now())} · تمت الإجابة على $done من $n", LeagueUi.CYAN))
        body.addView(LeagueUi.tv(this, "السؤال ${qi + 1} من $n", 20f, LeagueUi.GOLD, true))
        body.addView(LeagueUi.questionBox(this, q.text))
        q.options.forEachIndexed { i, opt ->
            val c = LeagueUi.optionCard(this, opt, if (answers[qi] == i) 1 else 0)
            c.setOnClickListener {
                answers[qi] = i
                saveDraft()
                render()
            }
            body.addView(c)
        }

        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        fun navBtn(text: String, color: String, onClick: () -> Unit): Button {
            val b = LeagueUi.button(this, text, color)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.topMargin = dp(12)
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            b.layoutParams = lp
            b.setOnClickListener { onClick() }
            return b
        }
        nav.addView(navBtn("▶ السابق", "#475569") {
            if (qi > 0) {
                qi--
                render()
                scrollTop()
            }
        })
        nav.addView(navBtn("التالي ◀", LeagueUi.GREEN) {
            if (qi < n - 1) {
                qi++
                render()
                scrollTop()
            }
        })
        body.addView(nav, LinearLayout.LayoutParams(-1, -2))

        // أرقام الأسئلة
        body.addView(LeagueUi.tv(this, "الأسئلة", 14f, LeagueUi.MUTED, true))
        val perRow = 7
        var row: LinearLayout? = null
        for (i in 0 until n) {
            if (i % perRow == 0) {
                row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                body.addView(row, LinearLayout.LayoutParams(-1, -2))
            }
            val c = TextView(this)
            c.text = "${i + 1}"
            c.textSize = 14f
            c.gravity = Gravity.CENTER
            c.setTypeface(null, android.graphics.Typeface.BOLD)
            val isCur = i == qi
            c.setTextColor(if (isCur) Color.BLACK else Color.WHITE)
            c.background = LeagueUi.bg(
                this, if (isCur) LeagueUi.GOLD else if (answers[i] >= 0) "#14532D" else "#1E2A5E", 10
            )
            val lp = LinearLayout.LayoutParams(0, dp(38), 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            c.setOnClickListener {
                qi = i
                render()
                scrollTop()
            }
            row?.addView(c, lp)
        }
        val rest = (perRow - n % perRow) % perRow
        for (k in 0 until rest) {
            row?.addView(View(this), LinearLayout.LayoutParams(0, dp(38), 1f).also { it.setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }

        val submit = LeagueUi.button(this, "📤 تسليم الواجب", LeagueUi.GOLD)
        submit.setTextColor(Color.BLACK)
        submit.setOnClickListener { confirmSubmit() }
        body.addView(submit)
    }

    private fun confirmSubmit() {
        val hw = cur ?: return
        val left = answers.count { it < 0 }
        AlertDialog.Builder(this)
            .setTitle("تسليم الواجب؟")
            .setMessage(
                (if (left > 0) "فاضل $left سؤال من غير إجابة وهيتحسبوا غلط.\n" else "") +
                        "بعد التسليم مينفعش تعدّل."
            )
            .setPositiveButton("سلّم") { _, _ -> submit(hw) }
            .setNegativeButton("كمّل حل", null)
            .show()
    }

    private fun submit(hw: Hw) {
        if (sending) return
        if (now() >= hw.deadline) {
            Toast.makeText(this, "انتهى موعد التسليم", Toast.LENGTH_LONG).show()
            screen = "result"
            render()
            return
        }
        sending = true
        val score = hw.qs.indices.count { answers[it] == hw.qs[it].correct }
        db.collection("homeworkSubmissions").document("${hw.id}_$playerKey").set(
            hashMapOf<String, Any>(
                "homeworkId" to hw.id, "teacherCode" to teacherCode,
                "studentId" to playerKey, "studentName" to studentName,
                "score" to score.toLong(), "total" to hw.qs.size.toLong(),
                "answers" to answers.map { it.toLong() },
                "submittedAt" to System.currentTimeMillis()
            )
        ).addOnSuccessListener {
            sending = false
            subs[hw.id] = Sub(score, hw.qs.size, answers.toList())
            getSharedPreferences("bac1_prefs", MODE_PRIVATE).edit().remove(draftKey(hw.id)).apply()
            screen = "result"
            render()
            scrollTop()
        }.addOnFailureListener {
            sending = false
            Toast.makeText(this, "تعذّر التسليم، تحقق من الإنترنت وحاول تاني", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- النتيجة ----------
    private fun renderResult() {
        val hw = cur ?: return
        val sub = subs[hw.id]
        val over = now() >= hw.deadline
        body.addView(LeagueUi.tv(this, hw.title, 18f, LeagueUi.MUTED, true))

        if (sub != null) {
            val p = if (sub.total > 0) sub.score * 100 / sub.total else 0
            val h = LeagueUi.tv(this, "✅ تم التسليم", 26f, LeagueUi.GREEN, true)
            h.gravity = Gravity.CENTER
            body.addView(h)
            val s = LeagueUi.tv(this, "${sub.score} من ${sub.total}  ($p%)", 32f, LeagueUi.GOLD, true)
            s.gravity = Gravity.CENTER
            body.addView(s)
        } else {
            val h = LeagueUi.tv(this, "❌ انتهى الموعد وما سلّمتش", 22f, LeagueUi.RED, true)
            h.gravity = Gravity.CENTER
            body.addView(h)
        }

        if (!over) {
            body.addView(
                LeagueUi.note(this, "🔒 الإجابات الصحيحة هتظهر بعد انتهاء الموعد (${HomeworkUtil.fmtDate(hw.deadline)})", LeagueUi.CYAN)
            )
        } else {
            val rh = LeagueUi.tv(this, "📋 مراجعة الواجب", 20f, LeagueUi.GOLD, true)
            rh.setPadding(0, dp(14), 0, dp(4))
            body.addView(rh)
            hw.qs.forEachIndexed { i, q ->
                val a = sub?.answers?.getOrNull(i) ?: -1
                val (status, color) = when {
                    a < 0 -> Pair("⏱ لم تجب", LeagueUi.MUTED)
                    a == q.correct -> Pair("✅ إجابتك صحيحة", LeagueUi.GREEN)
                    else -> Pair("❌ إجابتك غلط", LeagueUi.RED)
                }
                body.addView(LeagueUi.tv(this, "السؤال ${i + 1}  ·  $status", 14f, color, true))
                body.addView(LeagueUi.questionBox(this, q.text))
                q.options.forEachIndexed { k, opt ->
                    val st = when {
                        k == q.correct -> 2
                        k == a -> 3
                        else -> 0
                    }
                    body.addView(LeagueUi.optionCard(this, opt, st))
                }
            }
        }
        val back = LeagueUi.button(this, "↩ رجوع للواجبات", "#475569")
        back.setOnClickListener {
            screen = "list"
            render()
        }
        body.addView(back)
    }
}
