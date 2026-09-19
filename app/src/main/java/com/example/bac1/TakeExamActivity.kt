package com.example.bac1

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class TakeExamActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutRes = resources.getIdentifier("activity_take_exam", "layout", packageName)
        if (layoutRes != 0) setContentView(layoutRes)

        fun getTv(idName: String): TextView? {
            val id = resources.getIdentifier(idName, "id", packageName)
            return if (id != 0) findViewById(id) else null
        }

        val tvTitle = getTv("tvExamTitle")
        val tvQuestion = getTv("tvQuestion")

        val rgId = resources.getIdentifier("rgOptions", "id", packageName)
        val rgOptions = if (rgId != 0) findViewById<RadioGroup>(rgId) else null

        val rb1 = getTv("rbOption1") as? RadioButton
        val rb2 = getTv("rbOption2") as? RadioButton
        val rb3 = getTv("rbOption3") as? RadioButton
        val rb4 = getTv("rbOption4") as? RadioButton

        val btnSubmitId = resources.getIdentifier("btnSubmitExam", "id", packageName)
        val btnSubmit = if (btnSubmitId != 0) findViewById<Button>(btnSubmitId) else null

        val title = intent.getStringExtra("EXAM_TITLE") ?: ""
        val question = intent.getStringExtra("EXAM_QUESTION") ?: ""
        val op1 = intent.getStringExtra("EXAM_OP1") ?: ""
        val op2 = intent.getStringExtra("EXAM_OP2") ?: ""
        val op3 = intent.getStringExtra("EXAM_OP3") ?: ""
        val op4 = intent.getStringExtra("EXAM_OP4") ?: ""
        val correctIndex = intent.getIntExtra("CORRECT_INDEX", 0)
        val examId = intent.getStringExtra("EXAM_ID") ?: ""

        tvTitle?.text = title
        tvQuestion?.text = question
        rb1?.text = op1
        rb2?.text = op2
        rb3?.text = op3
        rb4?.text = op4

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val resultId = "${examId}_$studentName"

        FirebaseFirestore.getInstance().collection("results").document(resultId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val oldScore = doc.getLong("score")?.toInt() ?: 0
                    tvQuestion?.text = "لقد قدّمت هذا الامتحان من قبل، درجتك: $oldScore%"
                    rgOptions?.isEnabled = false
                    rb1?.isEnabled = false
                    rb2?.isEnabled = false
                    rb3?.isEnabled = false
                    rb4?.isEnabled = false
                    btnSubmit?.isEnabled = false
                    btnSubmit?.text = "تم التقديم من قبل"
                }
            }

        btnSubmit?.setOnClickListener {
            val selectedId = rgOptions?.checkedRadioButtonId ?: -1
            if (selectedId == -1) {
                Toast.makeText(this, "يرجى اختيار إجابة أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var chosenIndex = -1
            if (selectedId == rb1?.id) chosenIndex = 0
            if (selectedId == rb2?.id) chosenIndex = 1
            if (selectedId == rb3?.id) chosenIndex = 2
            if (selectedId == rb4?.id) chosenIndex = 3

            val isCorrect = (chosenIndex == correctIndex)
            val score = if (isCorrect) 100 else 0
            val teacherCode = prefs.getString("joined_teacher_code", "") ?: ""

            val resultData = hashMapOf(
                "examId" to examId,
                "examTitle" to title,
                "teacherCode" to teacherCode,
                "studentName" to studentName,
                "score" to score,
                "timestamp" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("results")
                .document(resultId)
                .set(resultData)
                .addOnSuccessListener {
                    if (isCorrect) {
                        Toast.makeText(this, "إجابة صحيحة! أحسنت 🎯 (100%)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "إجابة خاطئة ❌", Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "حدث خطأ أثناء حفظ النتيجة", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
