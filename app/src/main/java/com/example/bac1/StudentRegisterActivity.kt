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
    val phone = if (rawPhone.startsWith("0")) "+20" + rawPhone.substring(1)
                else if (!rawPhone.startsWith("+")) "+20$rawPhone"
                else rawPhone

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "من فضلك املأ كل الخانات", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

           getSharedPreferences("bac1_prefs", MODE_PRIVATE) .edit()
                .putBoolean("is_registered", true)
                .putString("student_name", name)
                .putString("student_phone", phone)
                .apply()

            FirebaseFirestore.getInstance().collection("students").add(
                hashMapOf("name" to name, "phone" to phone, "timestamp" to System.currentTimeMillis())
            )

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
