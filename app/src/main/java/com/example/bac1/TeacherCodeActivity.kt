package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class TeacherCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_code)

        val codeInput = findViewById<EditText>(R.id.teacherCodeInput)
        val verifyButton = findViewById<Button>(R.id.verifyCodeButton)

        verifyButton.setOnClickListener {
            val enteredCode = codeInput.text.toString().trim()

            if (enteredCode.isEmpty()) {
                Toast.makeText(this, "اكتب الكود الأول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseFirestore.getInstance()
                .collection("teacherCodes")
                .document(enteredCode)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val teacherName = doc.getString("name") ?: ""
                        val teacherSubject = doc.getString("subject") ?: ""

                        val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("is_teacher", true)
                            .putString("teacher_code", enteredCode)
                            .putString("teacher_name", teacherName)
                            .putString("teacher_subject", teacherSubject)
                            .apply()

                        Toast.makeText(this, "تم التفعيل بنجاح! افتح التطبيق تاني", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        Toast.makeText(this, "الكود غير صحيح", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
