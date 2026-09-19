package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
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

            val db = FirebaseFirestore.getInstance()

            db.collection("joinCodes")
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
                    val studentPhone = prefs.getString("student_phone", "") ?: ""
                    val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
                    val previousTeacherCode = prefs.getString("joined_teacher_code", "") ?: ""

                    val userDocId = if (studentPhone.isNotEmpty()) studentPhone else android.provider.Settings.Secure.getString(
                        contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    )

                    db.collection("users").document(userDocId).set(
                        hashMapOf("joined_teacher_code" to teacherCode, "phone" to studentPhone, "name" to studentName)
                    ).addOnSuccessListener {

                        prefs.edit()
                            .putString("joined_teacher_code", teacherCode)
                            .putString("joined_teacher_name", teacherName)
                            .putString("joined_teacher_subject", teacherSubject)
                            .apply()

                        db.collection("teachers").document(teacherCode)
                            .update("studentsCount", FieldValue.increment(1))

                        if (previousTeacherCode.isNotEmpty() && previousTeacherCode != teacherCode) {
                            db.collection("teachers").document(previousTeacherCode)
                                .update("studentsCount", FieldValue.increment(-1))
                        }

                        Toast.makeText(this, "تم التسجيل مع أستاذ $teacherName ✅", Toast.LENGTH_LONG).show()
                        finish()
                    }.addOnFailureListener {
                        Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
