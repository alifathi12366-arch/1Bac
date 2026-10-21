package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SuggestionActivity : AppCompatActivity() {

    companion object { private var lastSent = 0L }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(10), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        container.addView(tv("💡 اقتراح أو شكوى", 24f, true))
        container.addView(tv("اكتب ما يضايقك أو ما تتمنى إضافته. نقرأ كل الرسائل، لكننا لا نرد عليها.", 14f))

        val et = EditText(this)
        et.hint = "اكتب رسالتك هنا (حتى 500 حرف)"
        et.setTextColor(0xFF000000.toInt())
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        et.minLines = 4
        et.filters = arrayOf(InputFilter.LengthFilter(500))
        container.addView(et)

        val send = Button(this)
        send.text = "📩 إرسال"
        send.setOnClickListener {
            val text = et.text.toString().trim()
            if (text.length < 5) {
                Toast.makeText(this, "اكتب رسالة أوضح", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            if (now - lastSent < 30_000L) {
                Toast.makeText(this, "انتظر قليلًا قبل رسالة جديدة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            var count = if (prefs.getString("sug_day", "") == day) prefs.getInt("sug_count", 0) else 0
            if (count >= 5) {
                Toast.makeText(this, "وصلت للحد اليومي (5 رسائل)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            send.isEnabled = false
            FirebaseFirestore.getInstance().collection("suggestions").add(hashMapOf(
                "studentName" to (prefs.getString("student_name", "طالب") ?: "طالب"),
                "studentId" to (prefs.getString("student_id", "") ?: ""),
                "text" to text,
                "createdAt" to now
            )).addOnSuccessListener {
                lastSent = now
                count++
                prefs.edit().putString("sug_day", day).putInt("sug_count", count).apply()
                Toast.makeText(this, "وصلتنا رسالتك، شكرًا لك 🌟", Toast.LENGTH_LONG).show()
                finish()
            }.addOnFailureListener {
                send.isEnabled = true
                Toast.makeText(this, "تعذّر الإرسال، تحقق من الإنترنت", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(send)
    }
}
