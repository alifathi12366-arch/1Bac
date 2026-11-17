package com.example.bac1

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class TeacherPublishActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, size: Float = 16f): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    private fun edit(hint: String, multiline: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setTextColor(0xFF000000.toInt())
        if (multiline) {
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            e.minLines = 4
        }
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)
        val teacherCode = getSharedPreferences("bac1_prefs", MODE_PRIVATE).getString("teacher_code", "") ?: ""

        container.addView(label("📢 رسالة لطلابي", 22f))
        container.addView(label("بتظهر لكل طلابك في صفحتك (إعلان، تنبيه، سؤال رأي...). للفيديوهات استخدم الكورسات.", 13f))

        container.addView(label("عنوان الرسالة"))
        val etTitle = edit("مثال: محتاج رأيكم")
        container.addView(etTitle)

        container.addView(label("نص الرسالة"))
        val etMsg = edit("اكتب رسالتك هنا", true)
        container.addView(etMsg)

        val btn = Button(this)
        btn.text = "🚀 إرسال الرسالة"
        btn.setTextColor(0xFFFFFFFF.toInt())
        btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(24)
        lp.bottomMargin = dp(40)
        btn.layoutParams = lp
        container.addView(btn)

        btn.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val msg = etMsg.text.toString().trim()
            if (title.isEmpty() || msg.isEmpty()) {
                Toast.makeText(this, "اكتب العنوان ونص الرسالة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (teacherCode.isEmpty()) {
                Toast.makeText(this, "لا يوجد كود مدرس", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btn.isEnabled = false
            FirebaseFirestore.getInstance().collection("teacherContent")
                .document(UUID.randomUUID().toString())
                .set(
                    hashMapOf<String, Any>(
                        "teacherCode" to teacherCode,
                        "type" to "message",
                        "text" to "$title\n\n$msg",
                        "youtubeUrl" to "",
                        "pdfUrl" to "",
                        "directVideoUrl" to "",
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    TeacherActivityLog.post(this, "بعت رسالة جديدة 📢")
                    Toast.makeText(this, "تم إرسال الرسالة 🚀", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    btn.isEnabled = true
                    Toast.makeText(this, "فشل الإرسال: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
