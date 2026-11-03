package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class GroupAttendanceActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        fun tv(text: String, size: Float, bold: Boolean = false, color: Int = 0xFF222222.toInt()): TextView {
            val t = TextView(this)
            t.text = text
            t.textSize = size
            t.setTextColor(color)
            t.setPadding(0, dp(8), 0, dp(4))
            if (bold) t.setTypeface(null, Typeface.BOLD)
            return t
        }

        val groupId = intent.getStringExtra("groupId") ?: ""
        val db = FirebaseFirestore.getInstance()
        container.addView(tv("📋 سجل الحضور", 22f, true))

        db.collection("teacherGroups").document(groupId).get().addOnSuccessListener { g ->
            val all = (g.get("studentNames") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            container.addView(tv("${g.getString("name") ?: ""} · ${all.size} طالب", 15f))

            db.collection("attendanceSessions").whereEqualTo("groupId", groupId).get()
                .addOnSuccessListener { res ->
                    if (res.isEmpty) {
                        container.addView(tv("لا توجد حصص مسجلة بعد.", 14f))
                        return@addOnSuccessListener
                    }
                    val sessions = res.documents.groupBy {
                        "${it.getString("date") ?: ""}  ${it.getString("slot") ?: ""}"
                    }
                    sessions.keys.sortedDescending().forEach { key ->
                        val present = sessions[key]!!.mapNotNull { it.getString("studentName") }.toSet()
                        val absent = all.filter { it !in present }
                        container.addView(tv("📅 $key", 17f, true))
                        container.addView(tv("✅ حضر (${present.size}): ${present.joinToString("، ")}", 14f, false, 0xFF2E7D32.toInt()))
                        container.addView(tv(
                            if (absent.isEmpty()) "كل الطلاب حضروا 🎉"
                            else "❌ غاب (${absent.size}): ${absent.joinToString("، ")}",
                            14f, false, 0xFFC62828.toInt()))
                    }
                }
                .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
        }
    }
}
