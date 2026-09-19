package com.example.bac1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AddExamActivity : AppCompatActivity() {

    private lateinit var questionsContainer: LinearLayout
    private val questionBlocks = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_exam)

        val etExamTitle = findViewById<EditText>(R.id.etExamTitle)
        questionsContainer = findViewById(R.id.questionsContainer)
        val btnAddQuestion = findViewById<Button>(R.id.btnAddQuestion)
        val btnSaveExam = findViewById<Button>(R.id.btnSaveExam)

        addQuestionBlock()

        btnAddQuestion.setOnClickListener {
            addQuestionBlock()
        }

        btnSaveExam.setOnClickListener {
            saveExam(etExamTitle.text.toString().trim())
        }
    }

    private fun addQuestionBlock() {
        val block = LayoutInflater.from(this).inflate(R.layout.item_add_question, questionsContainer, false)
        val index = questionBlocks.size + 1

        block.findViewById<android.widget.TextView>(R.id.tvQuestionNumber).text = "السؤال رقم $index"

        val rgType = block.findViewById<RadioGroup>(R.id.rgQuestionType)
        val mcqFields = block.findViewById<LinearLayout>(R.id.mcqFieldsContainer)

        rgType.setOnCheckedChangeListener { _, checkedId ->
            mcqFields.visibility = if (checkedId == R.id.rbTypeMcq) View.VISIBLE else View.GONE
        }

        block.findViewById<Button>(R.id.btnRemoveQuestion).setOnClickListener {
            questionsContainer.removeView(block)
            questionBlocks.remove(block)
            renumberQuestions()
        }

        questionsContainer.addView(block)
        questionBlocks.add(block)
    }

    private fun renumberQuestions() {
        questionBlocks.forEachIndexed { i, block ->
            block.findViewById<android.widget.TextView>(R.id.tvQuestionNumber).text = "السؤال رقم ${i + 1}"
        }
    }

    private fun saveExam(title: String) {
        if (title.isEmpty()) {
            Toast.makeText(this, "اكتب عنوان الامتحان الأول", Toast.LENGTH_SHORT).show()
            return
        }
        if (questionBlocks.isEmpty()) {
            Toast.makeText(this, "لازم تضيف سؤال واحد على الأقل", Toast.LENGTH_SHORT).show()
            return
        }

        val questionsData = mutableListOf<HashMap<String, Any>>()

        for (block in questionBlocks) {
            val rgType = block.findViewById<RadioGroup>(R.id.rgQuestionType)
            val isMcq = rgType.checkedRadioButtonId == R.id.rbTypeMcq

            val questionText = block.findViewById<EditText>(R.id.etQuestionText).text.toString().trim()
            val scoreText = block.findViewById<EditText>(R.id.etQuestionScore).text.toString().trim()
            val score = scoreText.toIntOrNull() ?: 0

            if (questionText.isEmpty() || score <= 0) {
                Toast.makeText(this, "لازم كل سؤال يكون له نص ودرجة أكبر من صفر", Toast.LENGTH_LONG).show()
                return
            }

            val questionMap = HashMap<String, Any>()
            questionMap["qId"] = UUID.randomUUID().toString()
            questionMap["text"] = questionText
            questionMap["score"] = score

            if (isMcq) {
                val op1 = block.findViewById<EditText>(R.id.etOption1).text.toString().trim()
                val op2 = block.findViewById<EditText>(R.id.etOption2).text.toString().trim()
                val op3 = block.findViewById<EditText>(R.id.etOption3).text.toString().trim()
                val op4 = block.findViewById<EditText>(R.id.etOption4).text.toString().trim()
                val correctStr = block.findViewById<EditText>(R.id.etCorrectAnswer).text.toString().trim()

                if (op1.isEmpty() || op2.isEmpty() || correctStr.isEmpty()) {
                    Toast.makeText(this, "لازم كل سؤال اختياري يكون له خيارات وإجابة صحيحة", Toast.LENGTH_LONG).show()
                    return
                }

                questionMap["type"] = "mcq"
                questionMap["options"] = listOf(op1, op2, op3, op4)
                questionMap["correctIndex"] = (correctStr.toIntOrNull() ?: 1) - 1
            } else {
                questionMap["type"] = "essay"
                questionMap["options"] = emptyList<String>()
                questionMap["correctIndex"] = -1
            }

            questionsData.add(questionMap)
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""
        val examId = UUID.randomUUID().toString()
        val totalScore = questionsData.sumOf { (it["score"] as? Int) ?: 0 }

        val examData = hashMapOf(
            "examId" to examId,
            "teacherCode" to teacherCode,
            "title" to title,
            "questions" to questionsData,
            "totalScore" to totalScore,
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
