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
import com.google.firebase.firestore.SetOptions

class TeacherProfileActivity : AppCompatActivity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float = 16f, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(0xFF222222.toInt())
        t.setPadding(0, dp(10), 0, dp(4))
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun edit(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        e.setTextColor(0xFF000000.toInt())
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_questions)
        val container = findViewById<LinearLayout>(R.id.adminContainer)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val code = prefs.getString("teacher_code", "") ?: ""
        val name = prefs.getString("teacher_name", "") ?: ""
        val subjectDefault = prefs.getString("teacher_subject", "") ?: ""
        val db = FirebaseFirestore.getInstance()

        container.addView(tv("👤 ملفي الشخصي", 24f, true))
        container.addView(tv("الأستاذ $name", 18f, true))
        container.addView(tv("المعلومات دي بتظهر لطلابك في صفحتك جوه التطبيق.", 13f))

        container.addView(tv("المادة"))
        val etSubject = edit("مثال: تاريخ")
        etSubject.setText(subjectDefault)
        container.addView(etSubject)

        container.addView(tv("رقم الواتساب (اختياري)"))
        val etPhone = edit("مثال: 01012345678", InputType.TYPE_CLASS_PHONE)
        container.addView(etPhone)
        container.addView(tv("⚠️ هذا الرقم سيظهر لكل طلابك ليتواصلوا معك.", 12f))

        container.addView(tv("نبذة قصيرة (سطرين، بتظهر للطلاب)"))
        val etBio = edit(
            "مثال: خبرة 10 سنوات في تدريس التاريخ",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        etBio.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(200))
        etBio.minLines = 2
        container.addView(etBio)

        val save = Button(this)
        save.text = "💾 حفظ"
        save.setTextColor(0xFFFFFFFF.toInt())
        save.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
        container.addView(save)

        if (code.isNotEmpty()) {
            db.collection("teachers").document(code).get().addOnSuccessListener { d ->
                d.getString("profileSubject")?.let { if (it.isNotBlank()) etSubject.setText(it) }
                etPhone.setText(d.getString("whatsapp") ?: "")
                etBio.setText(d.getString("bio") ?: "")
            }
        }

        save.setOnClickListener {
            if (code.isEmpty()) {
                Toast.makeText(this, "لا يوجد كود مدرس", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val phone = etPhone.text.toString().filter { it.isDigit() || it == '+' }
            save.isEnabled = false
            db.collection("teachers").document(code).set(
                hashMapOf<String, Any>(
                    "profileSubject" to etSubject.text.toString().trim(),
                    "whatsapp" to phone,
                    "bio" to etBio.text.toString().trim(),
                    "photoUrl" to ""
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                save.isEnabled = true
                Toast.makeText(this, "تم الحفظ ✅", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                save.isEnabled = true
                Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
