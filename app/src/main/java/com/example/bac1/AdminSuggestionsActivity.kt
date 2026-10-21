package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminSuggestionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val pad = (10 * resources.displayMetrics.density).toInt()

        fun tv(text: String, size: Float, bold: Boolean = false): TextView {
            val t = TextView(this)
            t.text = text
            t.textSize = size
            t.setTextColor(0xFF222222.toInt())
            t.setPadding(0, pad, 0, pad / 2)
            if (bold) t.setTypeface(null, Typeface.BOLD)
            return t
        }

        container.addView(tv("💡 الاقتراحات والشكاوى", 22f, true))
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        FirebaseFirestore.getInstance().collection("suggestions").get()
            .addOnSuccessListener { res ->
                val docs = res.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }.take(100)
                if (docs.isEmpty()) container.addView(tv("لا توجد رسائل.", 14f))
                docs.forEach { d ->
                    container.addView(tv(
                        "${d.getString("studentName") ?: ""} #${d.getString("studentId") ?: ""} · " +
                                fmt.format(Date(d.getLong("createdAt") ?: 0L)), 13f, true))
                    container.addView(tv(d.getString("text") ?: "", 15f))
                }
            }
            .addOnFailureListener { container.addView(tv("تعذّر التحميل", 14f)) }
    }
}
