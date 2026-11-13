package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class StudentRegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_register)

        val etName = findViewById<EditText>(R.id.etStudentName)
        val etPhone = findViewById<EditText>(R.id.etStudentPhone)
        val etReferralCode = findViewById<EditText>(R.id.etReferralCode)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitStudent)
        val tvContact = findViewById<TextView>(R.id.tvContactUs)

        tvContact.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse("https://wa.me/201227537847")
            startActivity(i)
        }

        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val rawPhone = etPhone.text.toString().trim()
            val referralCode = etReferralCode.text.toString().trim()

            val phone = if (rawPhone.startsWith("0")) "+20" + rawPhone.substring(1)
            else if (!rawPhone.startsWith("+")) "+20$rawPhone"
            else rawPhone

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "من فضلك املأ كل الخانات الأساسية", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

            // توليد كود الطالب الخاص به لو مش موجود
            var studentId = prefs.getString("student_id", "") ?: ""
            if (studentId.isEmpty()) {
                studentId = (1000..9999).random().toString()
            }

            prefs.edit()
                .putBoolean("is_registered", true)
                .putString("student_name", name)
                .putString("student_phone", phone)
                .putString("student_id", studentId)
                .apply()

            val db = FirebaseFirestore.getInstance()

            // حفظ بيانات الطالب الجديد في فايرستور مع كوده الخاص
            db.collection("students").add(
                hashMapOf(
                    "studentId" to studentId,
                    "name" to name,
                    "phone" to phone,
                    "invited_count" to 0L,
                    "monthly_points" to 0L,
                    "timestamp" to System.currentTimeMillis()
                )
            )

            // منطق التحقق من كود الداعي ومكافأته
            if (referralCode.isNotEmpty() && referralCode != studentId) {
                db.collection("students").whereEqualTo("studentId", referralCode).get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val doc = documents.documents[0]
                            val docId = doc.id
                            val currentCount = doc.getLong("invited_count") ?: 0L
                            
                            // التحقق من أن الطالب الداعي لم يتخطى الحد الأقصى (5 أصدقاء)
                            if (currentCount < 5) {
                                val currentPoints = doc.getLong("monthly_points") ?: 0L
                                db.collection("students").document(docId)
                                    .update(
                                        mapOf(
                                            "invited_count" to (currentCount + 1),
                                            "monthly_points" to (currentPoints + 1)
                                        )
                                    )
                            }
                        }
                    }
            }

            Toast.makeText(this, "تم التسجيل بنجاح أهلاً بك يا $name 🚀", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
