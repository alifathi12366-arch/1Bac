package com.example.bac1

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AddExamActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_exam)

        val etExamTitle = findViewById<EditText>(R.id.etExamTitle)
        val etQuestionText = findViewById<EditText>(R.id.etQuestionText)
        val etOption1 = findViewById<EditText>(R.id.etOption1)
        val etOption2 = findViewById<EditText>(R.id.etOption2)
        val etOption3 = findViewById<EditText>(R.id.etOption3)
        val etOption4 = findViewById<EditText>(R.id.etOption4)
        val etCorrectAnswer = findViewById<EditText>(R.id.etCorrectAnswer)
        val btnSaveExam = findViewById<Button>(R.id.btnSaveExam)

        btnSaveExam.setOnClickListener {
            val title = etExamTitle.text.toString().trim()
            val question = etQuestionText.text.toString().trim()
            val op1 = etOption1.text.toString().trim()
            val op2 = etOption2.text.toString().trim()
            val op3 = etOption3.text.toString().trim()
            val op4 = etOption4.text.toString().trim()
            val correctStr = etCorrectAnswer.text.toString().trim()

            if (title.isEmpty() || question.isEmpty() || op1.isEmpty() || op2.isEmpty() || correctStr.isEmpty()) {
                Toast.makeText(this, "يرجى ملء البيانات الرئيسية والسؤال والإجابة الصحيحة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            val teacherCode = prefs.getString("teacher_join_code", "") ?: ""

            val examId = UUID.randomUUID().toString()
            val examData = hashMapOf(
                "examId" to examId,
                "teacherCode" to teacherCode,
                "title" to title,
                "question" to question,
                "options" to listOf(op1, op2, op3, op4),
                "correctIndex" to (correctStr.toIntOrNull() ?: 1) - 1,
                "createdAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("exams")
                .document(examId)
                .set(examData)
                .addOnSuccessListener {
                    Toast.makeText(this, "تم نشر الامتحان بنجاح! 📝✅", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "فشل حفظ الامتحان: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
