package com.example.bac1

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RoomPlayActivity : AppCompatActivity() {

    private data class Q(val text: String, val options: List<String>, val correct: Int)
    private enum class State { LOADING, INFO, RUNNING, DONE }

    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var state = State.LOADING

    private var code = ""
    private var studentId = ""
    private var studentName = ""
    private var timerSeconds = 25
    private var questions: List<Q> = emptyList()
    private var index = 0
    private var score = 0
    private var answered = false
    private var startMs = 0L
    private var timer: CountDownTimer? = null

    private lateinit var panelInfo: View
    private lateinit var panelQuestion: View
    private lateinit var panelResult: View
    private lateinit var optionButtons: List<Button>

    private fun resultRef() = db.collection("roomResults").document("${code}_$studentId")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_daily_challenge)

        panelInfo = findViewById(R.id.panelInfo)
        panelQuestion = findViewById(R.id.panelQuestion)
        panelResult = findViewById(R.id.panelResult)
        optionButtons = listOf(
            findViewById(R.id.btnOpt1), findViewById(R.id.btnOpt2),
            findViewById(R.id.btnOpt3), findViewById(R.id.btnOpt4)
        )
        findViewById<Button>(R.id.btnBack).apply {
            text = "العودة إلى الغرفة"
            setOnClickListener { finish() }
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        studentId = prefs.getString("student_id", "") ?: ""
        studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        code = intent.getStringExtra("code") ?: ""

        showInfo("⚔️ غرفة التحدي", "جارٍ التحميل...", false)
        loadRoom()
    }

    private fun loadRoom() {
        db.collection("rooms").document(code).get().addOnSuccessListener { d ->
            if (!d.exists()) {
                showInfo("الغرفة غير موجودة", "تأكد من الكود ثم حاول مجددًا.", false)
                return@addOnSuccessListener
            }
            if (System.currentTimeMillis() > (d.getLong("expiresAt") ?: 0L)) {
                showInfo("انتهت الغرفة", "انتهت صلاحية هذه الغرفة.", false)
                return@addOnSuccessListener
            }
            val ids = (d.get("playerIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            if (!ids.contains(studentId)) {
                showInfo("غير مسموح", "لستَ عضوًا في هذه الغرفة.", false)
                return@addOnSuccessListener
            }
            timerSeconds = d.getLong("timerSeconds")?.toInt() ?: 25

            val raw = d.get("questions") as? List<*> ?: emptyList<Any>()
            val parsed = raw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val text = m["text"] as? String ?: return@mapNotNull null
                val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String }
                    ?: return@mapNotNull null
                val c = (m["correctIndex"] as? Number)?.toInt() ?: return@mapNotNull null
                if (opts.size < 2 || c !in opts.indices) return@mapNotNull null
                val correctText = opts[c]
                val shuffled = opts.shuffled()
                Q(text, shuffled, shuffled.indexOf(correctText))
            }
            if (parsed.isEmpty()) {
                showInfo("لا توجد أسئلة", "هذه الغرفة بدون أسئلة.", false)
                return@addOnSuccessListener
            }
            questions = parsed.shuffled()

            resultRef().get().addOnSuccessListener { r ->
                if (r.exists()) {
                    val s = r.getLong("score")?.toInt() ?: 0
                    val t = r.getLong("total")?.toInt() ?: questions.size
                    showResult("أنهيتَ هذا التحدي ✅", "حصلتَ على $s من $t.\nارجع إلى الغرفة لمشاهدة الترتيب.")
                } else {
                    val rules = "عدد الأسئلة: ${questions.size}، ولكل سؤال $timerSeconds ثانية.\n" +
                            "لكل إجابة صحيحة نقطة تُحتسب في ترتيب الغرفة.\n\n" +
                            "⚠️ تنبيهات مهمة:\n" +
                            "• لك محاولة واحدة فقط في هذه الغرفة، وتُسجَّل منذ لحظة البدء.\n" +
                            "• إذا غادرتَ التطبيق أثناء التحدي ثم عدتَ، يُنهى التحدي وتُحتسب الأسئلة المتبقية خاطئة.\n" +
                            "• انتهاء وقت السؤال يعني احتسابه خاطئًا.\n" +
                            "• التقاط الشاشة غير متاح داخل التحدي."
                    showInfo("⚔️ غرفة $code", rules, true)
                }
            }.addOnFailureListener { showInfo("تعذّر الاتصال", "تحقق من الإنترنت ثم حاول مجددًا.", false) }
        }.addOnFailureListener { showInfo("تعذّر الاتصال", "تحقق من الإنترنت ثم حاول مجددًا.", false) }
    }

    private fun startChallenge() {
        findViewById<Button>(R.id.btnStart).isEnabled = false
        resultRef().set(hashMapOf(
            "code" to code, "studentId" to studentId, "studentName" to studentName,
            "score" to 0, "total" to questions.size, "finished" to false,
            "startedAt" to FieldValue.serverTimestamp()
        )).addOnSuccessListener {
            state = State.RUNNING
            index = 0
            score = 0
            startMs = System.currentTimeMillis()
            panelInfo.visibility = View.GONE
            panelResult.visibility = View.GONE
            panelQuestion.visibility = View.VISIBLE
            showQuestion()
        }.addOnFailureListener {
            findViewById<Button>(R.id.btnStart).isEnabled = true
            showInfo("تعذّر البدء", "تحقق من الإنترنت ثم حاول مجددًا.", true)
        }
    }

    private fun showQuestion() {
        if (index >= questions.size) { finishChallenge(false); return }
        answered = false
        val q = questions[index]
        findViewById<TextView>(R.id.tvProgress).text = "السؤال ${index + 1} من ${questions.size}"
        findViewById<ProgressBar>(R.id.progressBar).apply {
            max = questions.size
            progress = index
        }
        findViewById<TextView>(R.id.tvQuestion).text = q.text

        optionButtons.forEachIndexed { i, b ->
            if (i < q.options.size) {
                b.visibility = View.VISIBLE
                b.isEnabled = true
                b.text = q.options[i]
                b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5C7CFA"))
                b.setOnClickListener { onAnswer(i) }
            } else b.visibility = View.GONE
        }

        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        tvTimer.text = "⏱ $timerSeconds"
        timer?.cancel()
        timer = object : CountDownTimer(timerSeconds * 1000L, 250) {
            override fun onTick(ms: Long) { tvTimer.text = "⏱ ${(ms + 999) / 1000}" }
            override fun onFinish() { tvTimer.text = "⏱ 0"; onAnswer(-1) }
        }.start()
    }

    private fun onAnswer(chosen: Int) {
        if (answered || state != State.RUNNING) return
        answered = true
        timer?.cancel()
        val q = questions[index]
        if (chosen == q.correct) score++

        optionButtons.forEach { it.isEnabled = false }
        // الإجابة الصحيحة تظهر بالأخضر دائمًا، والاختيار الخاطئ بالأحمر
        optionButtons[q.correct].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        if (chosen >= 0 && chosen != q.correct) {
            optionButtons[chosen].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
        }
        handler.postDelayed({ index++; showQuestion() }, 1500)
    }

    private fun finishChallenge(forced: Boolean) {
        if (state == State.DONE) return
        state = State.DONE
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)

        val total = questions.size
        val timeMs = System.currentTimeMillis() - startMs
        resultRef().update(mapOf(
            "score" to score, "finished" to true, "leftApp" to forced,
            "timeMs" to timeMs, "finishedAt" to FieldValue.serverTimestamp()
        ))

        val title = if (forced) "أُنهي التحدي ⛔" else "انتهى التحدي 🎉"
        val note = if (forced) "غادرتَ التطبيق أثناء التحدي، فاحتُسبت الأسئلة المتبقية خاطئة.\n\n" else ""
        showResult(title, "${note}أجبتَ إجابات صحيحة: $score من $total\n\nارجع إلى الغرفة لمشاهدة الترتيب.")
    }

    override fun onStop() {
        super.onStop()
        if (state == State.RUNNING && !isChangingConfigurations) {
            finishChallenge(true)
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showInfo(title: String, body: String, canStart: Boolean) {
        if (state != State.RUNNING) state = State.INFO
        panelQuestion.visibility = View.GONE
        panelResult.visibility = View.GONE
        panelInfo.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvInfoTitle).text = title
        findViewById<TextView>(R.id.tvInfoBody).text = body
        val b = findViewById<Button>(R.id.btnStart)
        b.visibility = if (canStart) View.VISIBLE else View.GONE
        b.setOnClickListener { startChallenge() }
    }

    private fun showResult(title: String, detail: String) {
        panelInfo.visibility = View.GONE
        panelQuestion.visibility = View.GONE
        panelResult.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvResultTitle).text = title
        findViewById<TextView>(R.id.tvResultDetail).text = detail
    }
}
