package com.example.bac1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class TakeExamActivity : AppCompatActivity() {

    private lateinit var exam: ExamModel
    private val questionBlocks = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_take_exam)

        val examId = intent.getStringExtra("EXAM_ID") ?: ""
        val questionsContainer = findViewById<LinearLayout>(R.id.questionsContainer)
        val tvExamTitle = findViewById<TextView>(R.id.tvExamTitle)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitExam)

        FirebaseFirestore.getInstance().collection("exams").document(examId).get()
            .addOnSuccessListener { doc ->
                exam = doc.toObject(ExamModel::class.java) ?: return@addOnSuccessListener
                tvExamTitle.text = exam.title

                val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
                val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
                val resultId = "${examId}_$studentName"

                FirebaseFirestore.getInstance().collection("results").document(resultId).get()
                    .addOnSuccessListener { resultDoc ->
                        if (resultDoc.exists()) {
                            tvExamTitle.text = "${exam.title}\n(تم التقديم بالفعل)"
                            btnSubmit.isEnabled = false
                            btnSubmit.text = "تم التقديم من قبل"
                            renderQuestions(exam, questionsContainer, readOnly = true)
                        } else {
                            renderQuestions(exam, questionsContainer, readOnly = false)
                            btnSubmit.setOnClickListener {
                                submitExam(exam, resultId, studentName)
                            }
                        }
                    }
            }
    }

    private fun renderQuestions(exam: ExamModel, container: LinearLayout, readOnly: Boolean) {
        exam.questions.forEachIndexed { index, q ->
            val block = LayoutInflater.from(this).inflate(R.layout.item_take_question, container, false)

            block.findViewById<TextView>(R.id.tvQuestionText).text = "${index + 1}. ${q.text}"
            block.findViewById<TextView>(R.id.tvQuestionScore).text = "(${q.score} درجة)"

            val rgOptions = block.findViewById<RadioGroup>(R.id.rgAnswerOptions)
            val etEssay = block.findViewById<EditText>(R.id.etEssayAnswer)

            if (q.type == "mcq") {
                rgOptions.visibility = View.VISIBLE
                etEssay.visibility = View.GONE

                val radioIds = listOf(R.id.rbAns1, R.id.rbAns2, R.id.rbAns3, R.id.rbAns4)
                q.options.forEachIndexed { i, optionText ->
                    val rb = block.findViewById<RadioButton>(radioIds[i])
                    rb.text = optionText
                    rb.visibility = View.VISIBLE
                }
                for (i in q.options.size until radioIds.size) {
                    block.findViewById<RadioButton>(radioIds[i]).visibility = View.GONE
                }
            } else {
                rgOptions.visibility = View.GONE
                etEssay.visibility = View.VISIBLE
            }

            if (readOnly) {
                rgOptions.isEnabled = false
                for (i in 0 until rgOptions.childCount) rgOptions.getChildAt(i).isEnabled = false
                etEssay.isEnabled = false
            }

            container.addView(block)
            questionBlocks.add(block)
        }
    }

    private fun submitExam(exam: ExamModel, resultId: String, studentName: String) {
        var autoScore = 0
        var hasEssay = false
        val essayAnswers = mutableListOf<HashMap<String, Any>>()
        val mcqAnswers = mutableListOf<HashMap<String, Any>>()

        exam.questions.forEachIndexed { index, q ->
            val block = questionBlocks[index]

            if (q.type == "mcq") {
                val rgOptions = block.findViewById<RadioGroup>(R.id.rgAnswerOptions)
                val radioIds = listOf(R.id.rbAns1, R.id.rbAns2, R.id.rbAns3, R.id.rbAns4)
                val selectedId = rgOptions.checkedRadioButtonId
                val chosenIndex = radioIds.indexOf(selectedId)
                val isCorrect = (chosenIndex == q.correctIndex)
                if (isCorrect) autoScore += q.score

                mcqAnswers.add(hashMapOf(
                    "qId" to q.qId,
                    "text" to q.text,
                    "chosenIndex" to chosenIndex,
                    "correct" to isCorrect,
                    "score" to (if (isCorrect) q.score else 0)
                ))
            } else {
                hasEssay = true
                val etEssay = block.findViewById<EditText>(R.id.etEssayAnswer)
                val answerText = etEssay.text.toString().trim()

                essayAnswers.add(hashMapOf(
                    "qId" to q.qId,
                    "text" to q.text,
                    "answerText" to answerText,
                    "maxScore" to q.score,
                    "awardedScore" to -1
                ))
            }
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("joined_teacher_code", "") ?: ""

        val resultData = hashMapOf(
            "examId" to exam.examId,
            "examTitle" to exam.title,
            "teacherCode" to teacherCode,
            "studentName" to studentName,
            "totalScore" to exam.totalScore,
            "autoScore" to autoScore,
            "graded" to !hasEssay,
            "mcqAnswers" to mcqAnswers,
            "essayAnswers" to essayAnswers,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance().collection("results")
            .document(resultId)
            .set(resultData)
            .addOnSuccessListener {
                if (!hasEssay) {
                    val percentage = if (exam.totalScore > 0) (autoScore * 100.0 / exam.totalScore) else 0.0
                    if (percentage >= 95.0) {
                        showCertificate(exam.title, autoScore, exam.totalScore, studentName)
                    } else {
                        Toast.makeText(this, "تم التصحيح تلقائيًا! درجتك: $autoScore من ${exam.totalScore} 🎯", Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "تم إرسال إجاباتك، هتنتظر تصحيح الأسئلة المقالية من المدرس 📩", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "حدث خطأ أثناء حفظ الإجابات: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
