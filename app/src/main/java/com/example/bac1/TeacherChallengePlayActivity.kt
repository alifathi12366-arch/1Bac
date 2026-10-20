package com.example.bac1

import android.os.Bundle
import android.os.CountDownTimer
import android.graphics.Typeface
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class TeacherChallengePlayActivity : AppCompatActivity() {

    private class Q(val text: String, val options: List<String>, val correct: Int)

    private val db = FirebaseFirestore.getInstance()
    private lateinit var root: LinearLayout
    private lateinit var timerText: TextView
    private lateinit var body: LinearLayout

    private var challengeId = ""
    private var studentId = ""
    private var studentName = ""
    private var title = ""
    private var teacherCode = ""
    private var durationMin = 30L
    private var deadline = 0L
    private var questions: List<Q> = emptyList()
    private val groups = mutableListOf<RadioGroup>()
    private var running = false
    private var submitted = false
    private var startMs = 0L
    private var timer: CountDownTimer? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(dp(16), dp(8), dp(16), dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun resultRef() = db.collection("teacherChallengeResults")
        .document("${challengeId}_$studentId")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFFF5F5F5.toInt())
        timerText = tv("", 18f, true)
        timerText.setTextColor(0xFFE65100.toInt())
        root.addView(timerText)
        val scroll = ScrollView(this)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        studentId = prefs.getString("student_id", "") ?: ""
        studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        challengeId = intent.getStringExtra("challengeId") ?: ""

        message("جارٍ التحميل...", "")
        load()
    }

    private fun message(head: String, text: String, startable: Boolean = false) {
        body.removeAllViews()
        body.addView(tv(head, 22f, true))
        if (text.isNotEmpty()) body.addView(tv(text, 16f))
        if (startable) {
            val b = Button(this)
            b.text = "▶️ ابدأ التحدي"
            b.setOnClickListener { start() }
            body.addView(b)
        } else {
            val b = Button(this)
            b.text = "رجوع"
            b.setOnClickListener { finish() }
            body.addView(b)
        }
    }

    private fun load() {
        db.collection("teacherChallenges").document(challengeId).get().addOnSuccessListener { d ->
            if (!d.exists()) { message("غير موجود", "هذا التحدي غير موجود."); return@addOnSuccessListener }
            title = d.getString("title") ?: ""
            teacherCode = d.getString("teacherCode") ?: ""
            durationMin = d.getLong("durationMinutes") ?: 30L
            deadline = d.getLong("deadline") ?: 0L
            val prize = d.getString("prize") ?: ""

            val raw = d.get("questions") as? List<*> ?: emptyList<Any>()
            questions = raw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val text = m["text"] as? String ?: return@mapNotNull null
                val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String } ?: return@mapNotNull null
                val c = (m["correctIndex"] as? Number)?.toInt() ?: return@mapNotNull null
                if (opts.size < 2 || c !in opts.indices) return@mapNotNull null
                val correctText = opts[c]
                val sh = opts.shuffled()
                Q(text, sh, sh.indexOf(correctText))
            }.shuffled()

            if (System.currentTimeMillis() >= deadline) {
                message("انتهى التحدي", "انتهى موعد هذا التحدي.")
                return@addOnSuccessListener
            }
            resultRef().get().addOnSuccessListener { r ->
                if (r.exists()) message("سبق أن شاركت", "لك محاولة واحدة فقط في هذا التحدي.")
                else message("🎯 $title",
                    "🎁 الجائزة: $prize\nعدد الأسئلة: ${questions.size}\nمدة الحل: $durationMin دقيقة\n\n" +
                            "⚠️ تنبيهات:\n• محاولة واحدة فقط وتُسجَّل منذ البدء.\n" +
                            "• إذا غادرتَ التطبيق يُسلَّم حلّك فورًا بإجاباتك الحالية.\n" +
                            "• عند انتهاء الوقت يُسلَّم حلّك تلقائيًا.\n• التقاط الشاشة غير متاح.\n" +
                            "• الفائز: أعلى درجة ثم الأسرع، ويُعلن بعد انتهاء الموعد.", true)
            }
        }.addOnFailureListener { message("تعذّر الاتصال", "تحقق من الإنترنت.") }
    }

    private fun start() {
        resultRef().set(hashMapOf(
            "challengeId" to challengeId, "teacherCode" to teacherCode,
            "studentId" to studentId, "studentName" to studentName,
            "score" to 0, "total" to questions.size, "finished" to false,
            "startedAt" to System.currentTimeMillis()
        )).addOnSuccessListener {
            startMs = System.currentTimeMillis()
            running = true
            renderQuestions()
            val end = minOf(startMs + durationMin * 60_000L, deadline)
            timer = object : CountDownTimer(end - startMs, 1000) {
                override fun onTick(ms: Long) {
                    val s = ms / 1000
                    timerText.text = "⏱ %d:%02d".format(s / 60, s % 60)
                }
                override fun onFinish() { timerText.text = "⏱ انتهى الوقت"; submit(true) }
            }.start()
        }.addOnFailureListener {
            Toast.makeText(this, "تعذّر البدء، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderQuestions() {
        body.removeAllViews()
        groups.clear()
        body.addView(tv("🎯 $title", 20f, true))
        questions.forEachIndexed { i, q ->
            body.addView(tv("${i + 1}. ${q.text}", 17f, true))
            val rg = RadioGroup(this)
            rg.setPadding(dp(16), 0, dp(16), 0)
            q.options.forEach { o ->
                val rb = RadioButton(this)
                rb.id = View.generateViewId()
                rb.text = o
                rb.setTextColor(0xFF000000.toInt())
                rg.addView(rb)
            }
            body.addView(rg)
            groups.add(rg)
        }
        val send = Button(this)
        send.text = "📩 تسليم الحل"
        send.setOnClickListener { submit(false) }
        body.addView(send)
    }

    private fun submit(auto: Boolean) {
        if (submitted || !running) return
        submitted = true
        running = false
        timer?.cancel()
        var score = 0
        questions.forEachIndexed { i, q ->
            val g = groups[i]
            if (g.checkedRadioButtonId != -1 &&
                g.indexOfChild(g.findViewById(g.checkedRadioButtonId)) == q.correct) score++
        }
        val timeMs = System.currentTimeMillis() - startMs
        resultRef().update(mapOf(
            "score" to score, "finished" to true, "autoSubmitted" to auto,
            "timeMs" to timeMs, "finishedAt" to System.currentTimeMillis()
        )).addOnCompleteListener {
            timerText.text = ""
            message(if (auto) "تم تسليم حلّك تلقائيًا ⏱" else "تم التسليم ✅",
                "درجتك: $score من ${questions.size}\n\nستظهر النتيجة النهائية والفائز بعد انتهاء موعد التحدي.")
        }
    }

    override fun onStop() {
        super.onStop()
        if (running && !submitted && !isChangingConfigurations) submit(true)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
