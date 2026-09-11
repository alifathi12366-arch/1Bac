package com.example.bac1

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class TeacherRegisterActivity : AppCompatActivity() {

    private val subjects = arrayOf(
        "اللغة العربية", "اللغة الإنجليزية", "الرياضيات", "العلوم المتكاملة",
        "الفلسفة والمنطق", "التاريخ", "البرمجة", "التربية الإسلامية", "اللغة الفرنسية"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_register)

        val nameInput = findViewById<EditText>(R.id.teacherNameInput)
        val phoneInput = findViewById<EditText>(R.id.teacherPhoneInput)
        val subjectSpinner = findViewById<Spinner>(R.id.teacherSubjectSpinner)
        val submitButton = findViewById<Button>(R.id.submitTeacherRequestButton)

        subjectSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, subjects)

        submitButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val subject = subjectSpinner.selectedItem.toString()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "من فضلك املأ كل الخانات", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val requestId = UUID.randomUUID().toString()
            val data = hashMapOf(
                "name" to name,
                "phone" to phone,
                "subject" to subject,
                "status" to "pending",
                "requestedAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("teacherRequests")
                .document(requestId)
                .set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "تم إرسال طلبك، هيتم التواصل معاك قريبًا", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حصل خطأ، حاول تاني", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
