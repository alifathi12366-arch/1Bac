package com.example.bac1

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class HonorActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var container: LinearLayout
    private var chosenName = ""
    private lateinit var tvChosen: TextView

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
        container = findViewById(R.id.adminContainer)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""
        val teacherName = prefs.getString("teacher_name", "") ?: ""

        container.addView(tv("🏅 تكريم طالب", 24f, true))
        tvChosen = tv("لم تختر طالبًا بعد", 16f, true)
        tvChosen.setTextColor(0xFFE65100.toInt())
        container.addView(tvChosen)

        container.addView(tv("رسالة التكريم (اختياري)"))
        val etMsg = EditText(this)
        etMsg.hint = "مثال: لتفوقك وأخلاقك العالية"
        etMsg.setTextColor(0xFF000000.toInt())
        etMsg.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        container.addView(etMsg)

        val send = Button(this)
        send.text = "🏅 إرسال التكريم"
        send.setTextColor(0xFFFFFFFF.toInt())
        send.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFB8860B.toInt())
        send.setOnClickListener {
            if (chosenName.isEmpty()) {
                Toast.makeText(this, "اختر طالبًا أولًا", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            send.isEnabled = false
            db.collection("honors").add(hashMapOf(
                "studentName" to chosenName,
                "teacherName" to teacherName,
                "teacherCode" to teacherCode,
                "message" to etMsg.text.toString().trim(),
                "createdAt" to System.currentTimeMillis()
            )).addOnSuccessListener {
                Toast.makeText(this, "تم إرسال التكريم لـ $chosenName 🎉", Toast.LENGTH_LONG).show()
                chosenName = ""
                tvChosen.text = "لم تختر طالبًا بعد"
                etMsg.setText("")
                send.isEnabled = true
            }.addOnFailureListener {
                send.isEnabled = true
                Toast.makeText(this, "فشل الإرسال", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(send)

        container.addView(tv("اختر الطالب", 18f, true))
        val listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        container.addView(listBox)

        db.collection("users").whereEqualTo("joined_teacher_code", teacherCode).get()
            .addOnSuccessListener { res ->
                val names = res.documents.mapNotNull { it.getString("name") }
                    .filter { it.isNotBlank() }.distinct()
                if (names.isEmpty()) listBox.addView(tv("لا يوجد طلاب مسجلون معك بعد.", 14f))
                names.forEach { n ->
                    val b = Button(this)
                    b.text = n
                    b.setOnClickListener {
                        chosenName = n
                        tvChosen.text = "الطالب المختار: $n"
                    }
                    listBox.addView(b)
                }
            }
            .addOnFailureListener { listBox.addView(tv("تعذّر تحميل الطلاب", 14f)) }
    }
}
