package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class JoinTeacherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_teacher)

        val codeInput = findViewById<EditText>(R.id.studentJoinCodeInput)
        val joinButton = findViewById<Button>(R.id.joinTeacherButton)

        joinButton.setOnClickListener {
            val enteredCode = codeInput.text.toString().trim()

            if (enteredCode.isEmpty()) {
                Toast.makeText(this, "اكتب الكود الأول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseFirestore.getInstance()
                .collection("joinCodes")
                .document(enteredCode)
                .get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        Toast.makeText(this, "الكود غير صحيح", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val teacherCode = doc.getString("teacherCode") ?: ""
                    val teacherName = doc.getString("teacherName") ?: ""
                    val teacherSubject = doc.getString("teacherSubject") ?: ""

                    val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("joined_teacher_code", teacherCode)
                        .putString("joined_teacher_name", teacherName)
                        .putString("joined_teacher_subject", teacherSubject)
                        .apply()

                    Toast.makeText(this, "تم التسجيل مع أستاذ $teacherName ✅", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
