package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CourseProgressActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(8), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val courseId = intent.getStringExtra("courseId") ?: ""
        val db = FirebaseFirestore.getInstance()

        container.addView(tv("📊 تقدّم الطلاب", 22f, true))

        db.collection("courses").document(courseId).get().addOnSuccessListener { c ->
            val lessons = CourseUtil.parseLessons(c.get("lessons"))
            container.addView(tv(c.getString("title") ?: "", 16f))

            db.collection("courseProgress").whereEqualTo("courseId", courseId).get()
                .addOnSuccessListener { res ->
                    val sets = res.documents.map { d ->
                        Pair(
                            d.getString("studentName") ?: "طالب",
                            (d.get("passed") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                        )
                    }
                    if (sets.isEmpty()) {
                        container.addView(tv("لسه محدش بدأ الكورس ده.", 14f))
                        return@addOnSuccessListener
                    }

                    container.addView(tv("اللي نجحوا في كل درس", 18f, true))
                    lessons.forEachIndexed { i, l ->
                        val n = sets.count { l.id in it.second }
                        container.addView(tv("${i + 1}. ${l.title}  —  ✅ $n طالب", 15f))
                    }

                    container.addView(tv("الطلاب", 18f, true))
                    sets.map { Pair(it.first, lessons.count { l -> l.id in it.second }) }
                        .sortedByDescending { it.second }
                        .forEach { container.addView(tv("${it.first}  —  ${it.second} من ${lessons.size}", 15f)) }
                }
                .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
        }.addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }
}
