package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ExamAnalysisActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, bold: Boolean = false, color: Int = 0xFF222222.toInt()): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        container.addView(tv("📉 أكتر الأسئلة غلطًا", 22f, true))
        container.addView(tv("جارٍ التحميل...", 13f))

        val code = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            .getString("teacher_code", "") ?: ""

        FirebaseFirestore.getInstance().collection("results")
            .whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { res ->
                container.removeAllViews()
                container.addView(tv("📉 أكتر الأسئلة غلطًا", 22f, true))
                container.addView(tv("من الأسئلة الاختيارية فقط (المقالي يُصحَّح يدويًا).", 12f))

                if (res.isEmpty) {
                    container.addView(tv("لا توجد نتائج امتحانات بعد.", 14f))
                    return@addOnSuccessListener
                }

                // تجميع النتائج حسب الامتحان
                val byExam = res.documents.groupBy { it.getString("examId") ?: "" }
                val exams = byExam.entries.sortedByDescending { e ->
                    e.value.maxOf { it.getLong("timestamp") ?: 0L }
                }

                exams.forEach { (_, docs) ->
                    val title = docs.first().getString("examTitle") ?: "امتحان"
                    val students = docs.size
                    container.addView(tv("📝 $title", 19f, true))
                    container.addView(tv("عدد من حلّوا: $students", 13f))

                    // qId -> [نص السؤال، عدد الغلط، عدد من أجاب]
                    val wrong = LinkedHashMap<String, Triple<String, Int, Int>>()
                    for (d in docs) {
                        val answers = d.get("mcqAnswers") as? List<*> ?: continue
                        for (a in answers) {
                            val m = a as? Map<*, *> ?: continue
                            val qid = m["qId"] as? String ?: continue
                            val text = m["text"] as? String ?: ""
                            val ok = m["correct"] as? Boolean ?: false
                            val old = wrong[qid]
                            wrong[qid] = Triple(
                                text,
                                (old?.second ?: 0) + if (ok) 0 else 1,
                                (old?.third ?: 0) + 1
                            )
                        }
                    }

                    val top = wrong.values.filter { it.second > 0 }
                        .sortedByDescending { it.second }.take(5)

                    if (top.isEmpty()) {
                        container.addView(tv(
                            if (wrong.isEmpty()) "لا توجد أسئلة اختيارية في هذا الامتحان."
                            else "لا أحد أخطأ في أي سؤال 🎉", 14f, false, 0xFF2E7D32.toInt()))
                    }
                    top.forEachIndexed { i, (text, bad, total) ->
                        val pct = bad * 100 / total
                        container.addView(tv("${i + 1}. $text", 16f, true))
                        container.addView(tv("❌ غلط فيه $bad من $total طالب ($pct%)", 14f, false, 0xFFC62828.toInt()))
                        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                        bar.max = 100
                        bar.progress = pct
                        bar.progressTintList = android.content.res.ColorStateList.valueOf(0xFFC62828.toInt())
                        container.addView(bar, LinearLayout.LayoutParams(-1, dp(10)))
                    }
                }
            }
            .addOnFailureListener {
                container.removeAllViews()
                container.addView(tv("تعذّر تحميل النتائج، تحقق من الإنترنت.", 15f, true))
            }
    }
}
