package com.example.bac1

import android.content.Context
import android.content.Intent
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

                    val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
                    val studentPhone = prefs.getString("student_phone", "") ?: ""
                    val studentName = prefs.getString("student_name", "طالب") ?: "طالب"

                    val studentIdentifier = if (studentPhone.isNotEmpty()) studentPhone else android.provider.Settings.Secure.getString(
                        contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    )

                    val userDocId = "${teacherCode}_$studentIdentifier"

                    db.collection("users").document(userDocId).get()
                        .addOnSuccessListener { existingDoc ->
                            val alreadyJoined = existingDoc.exists()

                            db.collection("users").document(userDocId).set(
                                hashMapOf("joined_teacher_code" to teacherCode, "phone" to studentPhone, "name" to studentName)
                            ).addOnSuccessListener {

                                val existingCodes = prefs.getStringSet("joined_teacher_codes", emptySet()) ?: emptySet()
                                val updatedCodes = HashSet(existingCodes)
                                updatedCodes.add(teacherCode)
                                prefs.edit().putStringSet("joined_teacher_codes", updatedCodes).apply()

                                if (!alreadyJoined) {
                                    db.collection("teachers").document(teacherCode)
                                        .update("studentsCount", FieldValue.increment(1))
                                }

                                Toast.makeText(this, "تم التسجيل مع أستاذ $teacherName ✅", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this, MyTeachersActivity::class.java))
                                finish()
                            }.addOnFailureListener {
                                Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
