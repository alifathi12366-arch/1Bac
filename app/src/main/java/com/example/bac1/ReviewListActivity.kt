package com.example.bac1

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ReviewListActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, bold: Boolean = false): TextView {
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
        container = findViewById(R.id.adminContainer)
    }

    override fun onResume() {
        super.onResume()
        container.removeAllViews()
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val isTeacher = prefs.getBoolean("is_teacher", false)
        val code = intent.getStringExtra("teacherCode") ?: prefs.getString("teacher_code", "") ?: ""
        container.addView(tv("📘 المراجعات", 22f, true))

        if (isTeacher) {
            val add = Button(this)
            add.text = "➕ مراجعة جديدة"
            add.setOnClickListener { startActivity(Intent(this, ReviewEditActivity::class.java)) }
            container.addView(add)
        }

        FirebaseFirestore.getInstance().collection("teacherReviews")
            .whereEqualTo("teacherCode", code).get()
            .addOnSuccessListener { res ->
                val docs = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                if (docs.isEmpty()) container.addView(tv("لا توجد مراجعات بعد.", 14f))
                docs.forEach { d ->
                    val b = Button(this)
                    b.isAllCaps = false
                    b.text = "📘 ${d.getString("title") ?: ""}"
                    b.setOnClickListener {
                        val target = if (isTeacher) ReviewEditActivity::class.java
                        else ReviewViewActivity::class.java
                        startActivity(Intent(this, target).putExtra("reviewId", d.id))
                    }
                    container.addView(b)
                }
            }
            .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }
}
