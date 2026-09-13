package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class SetJoinCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_join_code)

        val codeInput = findViewById<EditText>(R.id.newJoinCodeInput)
        val saveButton = findViewById<Button>(R.id.saveJoinCodeButton)

        val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""
        val teacherName = prefs.getString("teacher_name", "") ?: ""
        val teacherSubject = prefs.getString("teacher_subject", "") ?: ""

        saveButton.setOnClickListener {
            val newCode = codeInput.text.toString().trim()

            if (newCode.isEmpty()) {
                Toast.makeText(this, "اكتب كود الأول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val db = FirebaseFirestore.getInstance()

            // تأكد إن الكود مش مستخدم عند مدرس تاني
            db.collection("joinCodes")
                .document(newCode)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists() && doc.getString("teacherCode") != teacherCode) {
                        Toast.makeText(this, "الكود ده مأخوذ، اختار كود تاني", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val data = hashMapOf(
                        "teacherCode" to teacherCode,
                        "teacherName" to teacherName,
                        "teacherSubject" to teacherSubject
                    )

                    db.collection("joinCodes")
                        .document(newCode)
                        .set(data)
                        .addOnSuccessListener {
                            prefs.edit().putString("teacher_join_code", newCode).apply()
                            Toast.makeText(this, "تم حفظ الكود ✅", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
