package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CourseProgressActivity : AppCompatActivity() {

    private class Row(val name: String, val passed: Set<String>, val watch: Map<*, *>?)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(6), 0, dp(3))
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
                    val rows = res.documents.map { d ->
                        Row(
                            d.getString("studentName") ?: "طالب",
                            (d.get("passed") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                            d.get("watch") as? Map<*, *>
                        )
                    }
                    if (rows.isEmpty()) {
                        container.addView(tv("لسه محدش بدأ الكورس ده.", 14f))
                        return@addOnSuccessListener
                    }

                    lessons.forEachIndexed { i, l ->
                        container.addView(tv("${i + 1}. ${l.title}", 18f, true))
                        container.addView(
                            tv(
                                if (l.qs.isEmpty()) "من غير امتحان"
                                else "✅ نجح في الامتحان: ${rows.count { l.id in it.passed }} طالب", 14f
                            )
                        )
                        val watchers = rows.mapNotNull { r ->
                            val w = r.watch?.get(l.id) as? Map<*, *> ?: return@mapNotNull null
                            val max = (w["max"] as? Number)?.toInt() ?: 0
                            if (max <= 0) return@mapNotNull null
                            val dur = (w["dur"] as? Number)?.toInt() ?: 0
                            val pct = if (dur > 0) " (${(max * 100 / dur).coerceAtMost(100)}%)" else ""
                            "• ${r.name} — حتى ${CourseUtil.fmtTime(max)}" +
                                    (if (dur > 0) " من ${CourseUtil.fmtTime(dur)}" else "") + pct +
                                    (if (l.id in r.passed) " ✅" else "")
                        }
                        container.addView(tv("👁 شاهد: ${watchers.size} طالب", 14f))
                        watchers.forEach { container.addView(tv(it, 13f)) }
                    }
                }
                .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
        }.addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }
}
