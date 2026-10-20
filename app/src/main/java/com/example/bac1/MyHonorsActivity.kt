package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MyHonorsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        fun tv(text: String, size: Float, bold: Boolean = false): TextView {
            val t = TextView(this)
            t.text = text
            t.textSize = size
            t.setTextColor(0xFF222222.toInt())
            t.setPadding(0, 12, 0, 8)
            if (bold) t.setTypeface(null, Typeface.BOLD)
            return t
        }
        container.addView(tv("🏅 تكريماتي", 24f, true))

        val name = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            .getString("student_name", "") ?: ""
        FirebaseFirestore.getInstance().collection("honors")
            .whereEqualTo("studentName", name).get()
            .addOnSuccessListener { res ->
                val docs = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                if (docs.isEmpty()) container.addView(tv("لا توجد تكريمات بعد. اجتهد وستأتي 💪", 15f))
                docs.forEach { d ->
                    val b = Button(this)
                    b.isAllCaps = false
                    b.text = "🏅 من الأستاذ ${d.getString("teacherName") ?: ""}"
                    b.setOnClickListener {
                        startActivity(Intent(this, HonorCertificateActivity::class.java)
                            .putExtra("student", name)
                            .putExtra("teacher", d.getString("teacherName") ?: "")
                            .putExtra("message", d.getString("message") ?: ""))
                    }
                    container.addView(b)
                }
            }
            .addOnFailureListener { container.addView(tv("تعذّر التحميل", 15f)) }
    }
}
