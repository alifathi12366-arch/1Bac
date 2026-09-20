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

class TeacherRegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_register)

        val etName = findViewById<EditText>(R.id.etTeacherName)
        val etSubject = findViewById<EditText>(R.id.etTeacherSubject)
        val etPhone = findViewById<EditText>(R.id.etTeacherPhone)
        val etCode = findViewById<EditText>(R.id.etTeacherCode)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitTeacher)
        val tvContact = findViewById<TextView>(R.id.tvContactUsTeacher)

        tvContact.setOnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse("https://wa.me/201227537847")
            startActivity(i)
        }

        btnSubmit.setOnClickListener {
            val name = etName.text.toString().trim()
            val subject = etSubject.text.toString().trim()
            val rawPhone = etPhone.text.toString().trim()
            val phone = if (rawPhone.startsWith("0")) "+20" + rawPhone.substring(1)
                        else if (!rawPhone.startsWith("+")) "+20$rawPhone"
                        else rawPhone
            val code = etCode.text.toString().trim()

            if (name.isEmpty() || subject.isEmpty() || phone.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "من فضلك املأ كل الخانات", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            val db = FirebaseFirestore.getInstance()
            db.collection("teacherCodes").document(code).get()
                .addOnSuccessListener { doc ->
                    btnSubmit.isEnabled = true
                    if (doc.exists()) {
                        val expiresAt = doc.getLong("expiresAt") ?: 0L
                        if (expiresAt > System.currentTimeMillis()) {
                            getSharedPreferences("bac1_prefs", MODE_PRIVATE).edit()
                                .putBoolean("is_registered", true)
                                .putBoolean("is_teacher", true)
                                .putString("teacher_name", name)
                                .putString("teacher_subject", subject)
                                .putString("teacher_phone", phone)
                                .putString("teacher_code", code)
                                .apply()

                            db.collection("teacherRequests").add(
                                hashMapOf("name" to name, "subject" to subject, "phone" to phone,
                                    "code" to code, "timestamp" to System.currentTimeMillis())
                            )
                            db.collection("teachers").document(code).set(
                                hashMapOf("id" to code, "name" to name, "subject" to subject, "phone" to phone),
                                com.google.firebase.firestore.SetOptions.merge()
                            )

                            startActivity(Intent(this, TeacherDashboardActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "الكود ده منتهي، تواصل معانا", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "الكود غير صحيح", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    btnSubmit.isEnabled = true
                    Toast.makeText(this, "تأكد من اتصال الإنترنت وحاول تاني", Toast.LENGTH_LONG).show()
                }
        }
    }
}
