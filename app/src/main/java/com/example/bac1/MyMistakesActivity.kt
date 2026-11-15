package com.example.bac1

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class MyMistakesActivity : AppCompatActivity() {

    private class QInfo(val text: String, val options: List<String>, val correct: Int)
    private class ExamInfo(val title: String, val byId: Map<String, QInfo>, val byText: Map<String, QInfo>)
    private class Mistake(
        val examId: String, val examTitle: String, val qId: String,
        val text: String, val chosen: Int
    )

    private val db = FirebaseFirestore.getInstance()
    private val exams = HashMap<String, ExamInfo>()
    private lateinit var container: LinearLayout
    private lateinit var noMistakesText: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_mistakes)

        container = findViewById(R.id.mistakesContainer)
        noMistakesText = findViewById(R.id.noMistakesText)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val teacherCode = intent.getStringExtra(MyTeachersActivity.EXTRA_TEACHER_CODE) ?: ""

        db.collection("results")
            .whereEqualTo("studentName", studentName)
            .whereEqualTo("teacherCode", teacherCode)
            .get()
            .addOnSuccessListener { results ->
                val mistakes = mutableListOf<Mistake>()
                val docs = results.documents.sortedByDescending { it.getLong("timestamp") ?: 0L }
                for (doc in docs) {
                    val examId = doc.getString("examId") ?: ""
                    val examTitle = doc.getString("examTitle") ?: ""
                    val list = doc.get("mcqAnswers") as? List<*> ?: continue
                    for (a in list) {
                        val m = a as? Map<*, *> ?: continue
                        if ((m["correct"] as? Boolean) != false) continue
                        mistakes.add(
                            Mistake(
                                examId, examTitle,
                                m["qId"]?.toString() ?: "",
                                m["text"] as? String ?: "",
                                (m["chosenIndex"] as? Number)?.toInt() ?: -1
                            )
                        )
                    }
                }
                if (mistakes.isEmpty()) {
                    noMistakesText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }
                loadExams(mistakes.map { it.examId }.filter { it.isNotEmpty() }.distinct()) {
                    render(mistakes)
                }
            }
            .addOnFailureListener {
                noMistakesText.text = "تعذّر التحميل، تحقق من الإنترنت وحاول تاني."
                noMistakesText.visibility = View.VISIBLE
            }
    }

    // نجيب الامتحانات عشان نعرض الاختيارات والإجابة الصحيحة
    private fun loadExams(ids: List<String>, done: () -> Unit) {
        if (ids.isEmpty()) {
            done()
            return
        }
        var pending = ids.size
        for (id in ids) {
            db.collection("exams").document(id).get()
                .addOnSuccessListener { d ->
                    if (d.exists()) exams[id] = parseExam(d)
                    pending -= 1
                    if (pending == 0) done()
                }
                .addOnFailureListener {
                    pending -= 1
                    if (pending == 0) done()
                }
        }
    }

    private fun parseExam(d: DocumentSnapshot): ExamInfo {
        val byId = HashMap<String, QInfo>()
        val byText = HashMap<String, QInfo>()
        val raw = d.get("questions") as? List<*> ?: emptyList<Any>()
        for (item in raw) {
            val m = item as? Map<*, *> ?: continue
            val text = m["text"] as? String ?: continue
            val opts = (m["options"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val c = (m["correctIndex"] as? Number)?.toInt() ?: -1
            val q = QInfo(text, opts, c)
            val qid = m["qId"]?.toString() ?: ""
            if (qid.isNotEmpty()) byId[qid] = q
            byText[text] = q
        }
        return ExamInfo(d.getString("title") ?: "", byId, byText)
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#1E1E1E"))
        bg.cornerRadius = dp(14).toFloat()
        c.background = bg
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(10)
        c.layoutParams = lp
        return c
    }

    private fun render(mistakes: List<Mistake>) {
        val groups = LinkedHashMap<String, MutableList<Mistake>>()
        for (m in mistakes) groups.getOrPut(m.examId) { mutableListOf() }.add(m)

        for ((examId, list) in groups) {
            val info = exams[examId]
            val title = list[0].examTitle.ifEmpty { info?.title ?: "" }.ifEmpty { "امتحان" }

            val head = card()
            val ht = TextView(this)
            ht.text = "📌 $title\n❌ ${list.size} إجابة غلط"
            ht.textSize = 17f
            ht.setTypeface(null, Typeface.BOLD)
            ht.setTextColor(Color.parseColor("#FFC83D"))
            head.addView(ht)
            if (examId.isNotEmpty()) {
                val b = Button(this)
                b.text = "📝 افتح الامتحان"
                b.setOnClickListener {
                    startActivity(Intent(this, TakeExamActivity::class.java).putExtra("EXAM_ID", examId))
                }
                head.addView(b)
            }
            container.addView(head)

            list.forEachIndexed { i, mk ->
                container.addView(questionCard(i + 1, mk, info))
            }
        }
    }

    private fun questionCard(n: Int, mk: Mistake, info: ExamInfo?): View {
        val c = card()
        val q = (if (mk.qId.isNotEmpty()) info?.byId?.get(mk.qId) else null) ?: info?.byText?.get(mk.text)

        val qt = TextView(this)
        qt.text = "$n. ${if (mk.text.isNotEmpty()) mk.text else (q?.text ?: "")}"
        qt.textSize = 16f
        qt.setTypeface(null, Typeface.BOLD)
        qt.setTextColor(Color.WHITE)
        qt.setPadding(0, 0, 0, dp(6))
        c.addView(qt)

        if (q == null || q.options.isEmpty()) {
            val t = TextView(this)
            t.text = "تعذّر تحميل اختيارات السؤال، افتح الامتحان من الزرار فوق."
            t.textSize = 13f
            t.setTextColor(Color.parseColor("#BDBDBD"))
            c.addView(t)
            return c
        }

        q.options.forEachIndexed { i, opt ->
            val isCorrect = i == q.correct
            val isChosen = i == mk.chosen
            val t = TextView(this)
            t.textSize = 15f
            t.setPadding(dp(10), dp(8), dp(10), dp(8))
            val obg = GradientDrawable()
            obg.cornerRadius = dp(10).toFloat()
            when {
                isCorrect -> {
                    t.text = "✅  $opt  (الإجابة الصحيحة)"
                    t.setTextColor(Color.parseColor("#81C784"))
                    t.setTypeface(null, Typeface.BOLD)
                    obg.setColor(Color.parseColor("#1B3A1F"))
                }
                isChosen -> {
                    t.text = "❌  $opt  (إجابتك)"
                    t.setTextColor(Color.parseColor("#EF9A9A"))
                    obg.setColor(Color.parseColor("#3A1B1B"))
                }
                else -> {
                    t.text = "•  $opt"
                    t.setTextColor(Color.parseColor("#BDBDBD"))
                    obg.setColor(Color.TRANSPARENT)
                }
            }
            t.background = obg
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(4)
            t.layoutParams = lp
            c.addView(t)
        }
        if (mk.chosen < 0) {
            val t = TextView(this)
            t.text = "⏱ ما جاوبتش على السؤال ده"
            t.textSize = 13f
            t.setTextColor(Color.parseColor("#FFB74D"))
            t.setPadding(0, dp(6), 0, 0)
            c.addView(t)
        }
        return c
    }
}
