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
        
        // لو حبيت تضيف EditText جديد في ملف الـ XML باسم etReferralCode، هتعرفه هنا:
        // (لو مش حابب تلعب في الـ XML، هوريك طريقة سهلة حالا)
        // val etReferralCode = findViewById<EditText>(R.id.etReferralCode)

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
            // val referralCode = etReferralCode.text.toString().trim() // لو أضفت الخانة في الـ XML

            val phone = if (rawPhone.startsWith("0")) "+20" + rawPhone.substring(1)
            else if (!rawPhone.startsWith("+")) "+20$rawPhone"
            else rawPhone

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "من فضلك املأ كل الخانات", Toast.LENGTH_SHORT).show()
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

            // حفظ بيانات الطالب في فايرستور مع كوده الخاص
            FirebaseFirestore.getInstance().collection("students").add(
                hashMapOf(
                    "studentId" to studentId,
                    "name" to name,
                    "phone" to phone,
                    "timestamp" to System.currentTimeMillis()
                )
            )

            // 🎁 منطق مكافأة دعوة الأصدقاء (لو كتب كود صحيح لصاحبه)
            /* 
            if (referralCode.isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                db.collection("students").whereEqualTo("studentId", referralCode).get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val docId = documents.documents[0].id
                            val currentCount = documents.documents[0].getLong("invited_count") ?: 0L
                            
                            // الشرط: الحد الأقصى 5 أصدقاء فقط
                            if (currentCount < 5) {
                                db.collection("students").document(docId)
                                    .update("invited_count", currentCount + 1)
                                
                                // ملاحظة: يمكنك تحديث نقاط المسابقة الشهرية للطالب الداعي هنا أو عبر قاعدة البيانات
                            }
                        }
                    }
            }
            */

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
