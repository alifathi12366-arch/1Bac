package com.example.bac1

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class StudentQuestionBankActivity : AppCompatActivity() {

    private class BQ(
        val date: String,
        val subject: String,
        val text: String,
        val options: List<String>,
        val correct: Int,
        val explanation: String
    )

    private val db = FirebaseFirestore.getInstance()
    private val all = mutableListOf<BQ>()
    private val cache = mutableMapOf<String, Map<String, Pair<Boolean, String>>>()
    private val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")

    private var studentKey = ""
    private var today = ""
    private var currentSubject: String? = null

    private lateinit var container: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var tvTitle: TextView
    private lateinit var tvSub: TextView
    private lateinit var btnBackSub: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_qbank)

        container = findViewById(R.id.bankContainer)
        progress = findViewById(R.id.bankProgress)
        tvTitle = findViewById(R.id.tvBankTitle)
        tvSub = findViewById(R.id.tvBankSub)
        btnBackSub = findViewById(R.id.btnBackSubjects)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val id = prefs.all["student_id"]?.toString().orEmpty()
        studentKey = if (id.isNotEmpty()) id
        else (prefs.getString("student_phone", "") ?: "").filter { it.isDigit() }

        btnBackSub.setOnClickListener {
            currentSubject = null
            render()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentSubject != null) {
                    currentSubject = null
                    render()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        fetchServerDate()
    }

    // ---------- التحميل ----------
    private fun fetchServerDate() {
        val ref = db.collection("serverClock").document(studentKey)
        ref.set(hashMapOf("t" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                ref.get().addOnSuccessListener { d ->
                    val date = d.getTimestamp("t")?.toDate() ?: Date()
                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    fmt.timeZone = TimeZone.getTimeZone("Africa/Cairo")
                    today = fmt.format(date)
                    checkTodayFinished()
                }.addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
            }
            .addOnFailureListener { showMessage("تعذّر الاتصال، تحقق من الإنترنت وحاول تاني.") }
    }

    // أسئلة اليوم ما بتظهرش إلا بعد ما الطالب يخلص التحدي
    private fun checkTodayFinished() {
        db.collection("dailyAttempts").document("${studentKey}_$today").get()
            .addOnSuccessListener { a ->
                loadAll(a.exists() && a.getBoolean("finished") == true)
            }
            .addOnFailureListener { loadAll(false) }
    }

    private fun loadAll(includeToday: Boolean) {
        db.collection("dailyChallenges").get().addOnSuccessListener { snap ->
            all.clear()
            for (d in snap.documents) {
                val date = d.id
                if (!dateRegex.matches(date)) continue
                if (date > today) continue
                if (date == today && !includeToday) continue

                val subject = (d.getString("subject") ?: "").trim().ifEmpty { "عام" }
                val raw = d.get("questions") as? List<*> ?: continue
                for (item in raw) {
                    val m = item as? Map<*, *> ?: continue
                    val text = m["text"] as? String ?: continue
                    val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String } ?: continue
                    val c = (m["correctIndex"] as? Number)?.toInt() ?: continue
                    if (opts.size < 2 || c !in opts.indices) continue
                    val exp = (m["explanation"] as? String)?.trim() ?: ""
                    all.add(BQ(date, subject, text, opts, c, exp))
                }
            }
            progress.visibility = View.GONE
            render()
        }.addOnFailureListener {
            showMessage("تعذّر تحميل بنك الأسئلة، تحقق من الإنترنت وحاول تاني.")
        }
    }

    private fun result(q: BQ): Pair<Boolean, String>? {
        val m = cache.getOrPut(q.date) { QBankStore.load(this, q.date) }
        return m[QBankStore.key(q.text)]
    }

    // ---------- العرض ----------
    private fun showMessage(msg: String) {
        progress.visibility = View.GONE
        container.removeAllViews()
        val t = TextView(this)
        t.text = msg
        t.gravity = Gravity.CENTER
        t.textSize = 15f
        t.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
        t.setPadding(dp(16), dp(40), dp(16), 0)
        container.addView(t)
    }

    private fun render() {
        container.removeAllViews()
        val subject = currentSubject
        if (subject == null) renderSubjects() else renderQuestions(subject)
    }

    private fun renderSubjects() {
        btnBackSub.visibility = View.GONE
        tvTitle.text = "🏦 بنك الأسئلة"
        tvSub.text = "اختر المادة لتشوف أسئلتها وإجاباتها"

        if (all.isEmpty()) {
            showMessage("لسه مفيش أسئلة في البنك.\nأسئلة كل يوم بتظهر هنا بعد ما تخلص تحدي اليوم.")
            return
        }

        val groups = all.groupBy { it.subject }.toSortedMap()
        for ((subject, list) in groups) {
            val wrong = list.count { result(it)?.first == false }

            val card = MaterialCardView(this)
            card.radius = dp(16).toFloat()
            card.cardElevation = dp(3).toFloat()
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(10)
            card.layoutParams = lp

            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(16), dp(16), dp(16), dp(16))

            val title = TextView(this)
            title.text = "📘 $subject"
            title.textSize = 19f
            title.setTypeface(null, Typeface.BOLD)
            title.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
            box.addView(title)

            val sub = TextView(this)
            sub.text = "${list.size} سؤال" + if (wrong > 0) "  ·  ❌ $wrong غلط" else ""
            sub.textSize = 14f
            sub.setTextColor(
                if (wrong > 0) Color.parseColor("#C62828")
                else ContextCompat.getColor(this, R.color.text_gray)
            )
            sub.setPadding(0, dp(4), 0, 0)
            box.addView(sub)

            card.addView(box)
            card.setOnClickListener {
                currentSubject = subject
                render()
            }
            container.addView(card)
        }
    }

    private fun renderQuestions(subject: String) {
        btnBackSub.visibility = View.VISIBLE
        tvTitle.text = "📘 $subject"

        val list = all.filter { it.subject == subject }.sortedByDescending { it.date }
        val wrong = list.count { result(it)?.first == false }
        tvSub.text = "${list.size} سؤال" + if (wrong > 0) "  ·  ❌ $wrong غلط" else ""

        list.forEachIndexed { n, q ->
            val r = result(q)

            val card = MaterialCardView(this)
            card.radius = dp(16).toFloat()
            card.cardElevation = dp(3).toFloat()
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(10)
            card.layoutParams = lp

            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(dp(14), dp(14), dp(14), dp(14))

            val head = TextView(this)
            head.text = "${n + 1}  ·  📅 ${q.date}"
            head.textSize = 12f
            head.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            box.addView(head)

            if (r != null) {
                val badge = TextView(this)
                if (r.first) {
                    badge.text = "✔ جاوبت صح"
                    badge.setTextColor(Color.parseColor("#2E7D32"))
                } else {
                    badge.text = if (r.second.isEmpty()) "❌ غلط (انتهى الوقت)" else "❌ جاوبت غلط"
                    badge.setTextColor(Color.parseColor("#C62828"))
                }
                badge.textSize = 14f
                badge.setTypeface(null, Typeface.BOLD)
                badge.setPadding(0, dp(4), 0, 0)
                box.addView(badge)
            }

            val qt = TextView(this)
            qt.text = q.text
            qt.textSize = 18f
            qt.setTypeface(null, Typeface.BOLD)
            qt.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
            qt.setPadding(0, dp(8), 0, dp(4))
            box.addView(qt)

            q.options.forEachIndexed { i, opt ->
                val isCorrect = i == q.correct
                val isChosenWrong = r != null && !r.first && r.second == opt
                val tv = TextView(this)
                tv.textSize = 16f
                tv.setPadding(dp(10), dp(8), dp(10), dp(8))
                when {
                    isCorrect -> {
                        tv.text = "✅  $opt"
                        tv.setBackgroundColor(Color.parseColor("#E8F5E9"))
                        tv.setTextColor(Color.parseColor("#1B5E20"))
                        tv.setTypeface(null, Typeface.BOLD)
                    }
                    isChosenWrong -> {
                        tv.text = "❌  $opt"
                        tv.setBackgroundColor(Color.parseColor("#FFEBEE"))
                        tv.setTextColor(Color.parseColor("#B71C1C"))
                    }
                    else -> {
                        tv.text = "•  $opt"
                        tv.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                    }
                }
                val olp = LinearLayout.LayoutParams(-1, -2)
                olp.topMargin = dp(6)
                tv.layoutParams = olp
                box.addView(tv)
            }

            if (q.explanation.isNotEmpty()) {
                val ex = TextView(this)
                ex.text = "💡 ${q.explanation}"
                ex.textSize = 14f
                ex.setTextColor(Color.parseColor("#4E342E"))
                ex.setBackgroundColor(Color.parseColor("#FFF8E1"))
                ex.setPadding(dp(10), dp(8), dp(10), dp(8))
                val elp = LinearLayout.LayoutParams(-1, -2)
                elp.topMargin = dp(8)
                ex.layoutParams = elp
                box.addView(ex)
            }

            card.addView(box)
            container.addView(card)
        }
    }
}
