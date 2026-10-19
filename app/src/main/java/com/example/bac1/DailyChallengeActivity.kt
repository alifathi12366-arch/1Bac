package com.example.bac1

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DailyChallengeActivity : AppCompatActivity() {

    private data class Q(val text: String, val options: List<String>, val correct: Int)
    private enum class State { LOADING, INFO, RUNNING, DONE }

    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var state = State.LOADING

    private var studentKey = ""
    private var studentName = ""
    private var today = ""
    private var subject = ""
    private var timerSeconds = 25
    private var questions: List<Q> = emptyList()
    private var index = 0
    private var score = 0
    private var answered = false
    private var startMs = 0L
    private var timer: CountDownTimer? = null
    private var boardBtn: Button? = null

    private lateinit var panelInfo: View
    private lateinit var panelQuestion: View
    private lateinit var panelResult: View
    private lateinit var optionButtons: List<Button>

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
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val id = prefs.all["student_id"]?.toString().orEmpty()
        studentKey = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }

        showInfo("جارٍ التحميل...", "يُرجى الانتظار قليلًا.", false)
        fetchServerDate()
    }

    // ---------- التاريخ من السيرفر ----------
    private fun fetchServerDate() {
        val ref = db.collection("serverClock").document(studentKey)
        ref.set(hashMapOf("t" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                ref.get().addOnSuccessListener { d ->
                    val date = d.getTimestamp("t")?.toDate() ?: Date()
                    onDateReady(date)
                }.addOnFailureListener { showInfo("تعذّر الاتصال", "تحقق من الإنترنت ثم حاول مجددًا.", false) }
            }
            .addOnFailureListener { showInfo("تعذّر الاتصال", "تحقق من الإنترنت ثم حاول مجددًا.", false) }
    }

    private fun onDateReady(date: Date) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Africa/Cairo")
        today = fmt.format(date)
        checkAttempt()
    }

    private fun attemptRef() = db.collection("dailyAttempts").document("${studentKey}_$today")

    private fun checkAttempt() {
        attemptRef().get().addOnSuccessListener { a ->
            if (a.exists()) {
                val s = a.getLong("score")?.toInt() ?: 0
                val total = a.getLong("total")?.toInt() ?: 5
                showResult("أنهيتَ تحدي اليوم ✅",
                    "حصلتَ على $s من $total.\nعُد غدًا لتحدٍّ جديد.")
            } else loadChallenge()
        }.addOnFailureListener { showInfo("تعذّر الاتصال", "تحقق من الإنترنت ثم حاول مجددًا.", false) }
    }

    private fun loadChallenge() {
        db.collection("dailyChallenges").document(today).get().addOnSuccessListener { d ->
            if (!d.exists()) {
                showInfo("🔥 تحدي اليوم", "لا يوجد تحدٍّ لهذا اليوم بعد.\nعُد لاحقًا.", false)
                return@addOnSuccessListener
            }
            subject = d.getString("subject") ?: ""
            timerSeconds = d.getLong("timerSeconds")?.toInt() ?: 25
            val raw = d.get("questions") as? List<*> ?: emptyList<Any>()
            val parsed = raw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val text = m["text"] as? String ?: return@mapNotNull null
                val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String } ?: return@mapNotNull null
                val c = (m["correctIndex"] as? Number)?.toInt() ?: return@mapNotNull null
                if (opts.size < 2 || c !in opts.indices) return@mapNotNull null
                val correctText = opts[c]
                val shuffled = opts.shuffled()
                Q(text, shuffled, shuffled.indexOf(correctText))
            }
            if (parsed.isEmpty()) {
                showInfo("🔥 تحدي اليوم", "لا يوجد تحدٍّ لهذا اليوم بعد.", false)
                return@addOnSuccessListener
            }
            questions = parsed.shuffled()
            val rules = "المادة: $subject\n" +
                "عدد الأسئلة: ${questions.size}، ولكل سؤال $timerSeconds ثانية.\n" +
                "لكل إجابة صحيحة نقطة واحدة.\n\n" +
                "⚠️ تنبيهات مهمة:\n" +
                "• لك محاولة واحدة فقط في اليوم، وتُسجَّل منذ لحظة البدء.\n" +
                "• إذا غادرتَ التطبيق أثناء التحدي ثم عدتَ، يُنهى التحدي وتُحتسب الأسئلة المتبقية خاطئة.\n" +
                "• انتهاء وقت السؤال يعني احتسابه خاطئًا.\n" +
                "• التقاط الشاشة غير متاح داخل التحدي."
            showInfo("🔥 تحدي اليوم", rules, true)
        }.addOnFailureListener { showInfo("تعذّر الاتصال", "تحقق من الإنترنت ثم حاول مجددًا.", false) }
    }

    private fun startChallenge() {
        findViewById<Button>(R.id.btnStart).isEnabled = false
        attemptRef().set(hashMapOf(
            "studentKey" to studentKey, "studentName" to studentName,
            "date" to today, "subject" to subject,
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

        optionButtons.forEachIndexed { i, btn ->
            if (i < q.options.size) {
                btn.visibility = View.VISIBLE
                btn.isEnabled = true
                btn.text = q.options[i]
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#5C7CFA"))
                btn.setOnClickListener { onAnswer(i) }
            } else btn.visibility = View.GONE
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
        optionButtons[q.correct].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        if (chosen >= 0 && chosen != q.correct) {
            optionButtons[chosen].backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
        }
        handler.postDelayed({ index++; showQuestion() }, 1200)
    }

    private fun finishChallenge(forced: Boolean) {
        if (state == State.DONE) return
        state = State.DONE
        timer?.cancel()
        handler.removeCallbacksAndMessages(null)

        val total = questions.size
        val timeMs = System.currentTimeMillis() - startMs
        attemptRef().update(mapOf(
            "score" to score, "finished" to true, "leftApp" to forced,
            "timeMs" to timeMs, "finishedAt" to FieldValue.serverTimestamp()
        ))

        if (score > 0) {
            val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            val monthPoints = PointsManager.addPoints(prefs, score)
            LeaderboardManager.submitScore(studentKey, studentName, monthPoints)
        }

        val perfect = !forced && total > 0 && score == total
        val title = when {
            forced -> "أُنهي التحدي ⛔"
            perfect -> "ممتاز! إجابات كلها صحيحة 🌟"
            else -> "انتهى التحدي 🎉"
        }
        val note = if (forced) "غادرتَ التطبيق أثناء التحدي، فاحتُسبت الأسئلة المتبقية خاطئة.\n\n" else ""
        showResult(title, "${note}أجبتَ إجابات صحيحة: $score من $total\nكسبتَ $score نقطة ⭐\n\nعُد غدًا لتحدٍّ جديد.")

        if (perfect) {
            findViewById<ViewGroup>(android.R.id.content).addView(
                ConfettiView(this), ViewGroup.LayoutParams(-1, -1))
        }
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
        val btn = findViewById<Button>(R.id.btnStart)
        btn.visibility = if (canStart) View.VISIBLE else View.GONE
        btn.setOnClickListener { startChallenge() }
    }

    private fun showResult(title: String, detail: String) {
        panelInfo.visibility = View.GONE
        panelQuestion.visibility = View.GONE
        panelResult.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvResultTitle).text = title
        findViewById<TextView>(R.id.tvResultDetail).text = detail

        if (boardBtn == null && today.isNotEmpty()) {
            val b = Button(this)
            b.text = "🏆 ترتيب اليوم والرسائل"
            b.setTextColor(Color.WHITE)
            b.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#283593"))
            b.setOnClickListener {
                val i = android.content.Intent(this, DailyBoardActivity::class.java)
                i.putExtra("date", today)
                startActivity(i)
            }
            (panelResult as LinearLayout).addView(b, 2)
            boardBtn = b
        }
    }
}
