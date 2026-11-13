package com.example.bac1

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
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
    private var running = false
    private var submitted = false
    private var timer: CountDownTimer? = null
    private var resultId = ""
    private var studentName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_take_exam)

        val examId = intent.getStringExtra("EXAM_ID") ?: ""
        val questionsContainer = findViewById<LinearLayout>(R.id.questionsContainer)
        val tvExamTitle = findViewById<TextView>(R.id.tvExamTitle)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitExam)

        FirebaseFirestore.getInstance().collection("exams").document(examId).get()
            .addOnSuccessListener { doc ->
                exam = doc.toObject(ExamModel::class.java) ?: return@addOnSuccessListener
                tvExamTitle.text = exam.title

                val durationMin = doc.getLong("durationMinutes") ?: 0L
                val deadline = doc.getLong("deadline") ?: 0L

                val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
                studentName = prefs.getString("student_name", "طالب") ?: "طالب"
                resultId = "${examId}_$studentName"

                FirebaseFirestore.getInstance().collection("results").document(resultId).get()
                    .addOnSuccessListener { resultDoc ->
                        if (resultDoc.exists()) {
                            val autoScore = resultDoc.getLong("autoScore")?.toInt() ?: 0
                            tvExamTitle.text = "${exam.title}\n✅ تم التقديم مسبقاً (الدرجة: $autoScore / ${exam.totalScore})"
                            btnSubmit.isEnabled = false
                            btnSubmit.text = "تم التقديم من قبل"
                            
                            // استخراج الإجابات المحفوظة مسبقاً لعرضها للطالب مع التصحيح
                            val savedMcq = resultDoc.get("mcqAnswers") as? List> ?: emptyList()
                            renderQuestions(exam, questionsContainer, readOnly = true, savedAnswers = savedMcq)
                        } else if (deadline > 0L && System.currentTimeMillis() > deadline) {
                            tvExamTitle.text = "${exam.title}\n(انتهى موعد هذا الامتحان)"
                            btnSubmit.isEnabled = false
                            btnSubmit.text = "الامتحان مغلق"
                        } else {
                            renderQuestions(exam, questionsContainer, readOnly = false, savedAnswers = emptyList())
                            running = true
                            btnSubmit.setOnClickListener { submitExam(auto = false) }
                            startTimer(tvExamTitle, durationMin, deadline)
                        }
                    }
            }
    }

    private fun startTimer(title: TextView, durationMin: Long, deadline: Long) {
        val now = System.currentTimeMillis()
        var end = Long.MAX_VALUE
        if (durationMin > 0L) end = now + durationMin * 60_000L
        if (deadline > 0L) end = minOf(end, deadline)
        if (end == Long.MAX_VALUE) return

        timer = object : CountDownTimer(end - now, 1000) {
            override fun onTick(ms: Long) {
                val s = ms / 1000
                title.text = "${exam.title}\n⏱ %d:%02d".format(s / 60, s % 60)
            }
            override fun onFinish() {
                title.text = "${exam.title}\n⏱ انتهى الوقت"
                submitExam(auto = true)
            }
        }.start()
    }

    override fun onStop() {
        super.onStop()
        if (running && !submitted && !isChangingConfigurations) {
            submitExam(auto = true)
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    private fun renderQuestions(
        exam: ExamModel, 
        container: LinearLayout, 
        readOnly: Boolean, 
        savedAnswers: List>
    ) {
        exam.questions.forEachIndexed { index, q ->
            val block = LayoutInflater.from(this).inflate(R.layout.item_take_question, container, false)

            block.findViewById<TextView>(R.id.tvQuestionText).text = "${index + 1}. ${q.text}"
            block.findViewById<TextView>(R.id.tvQuestionScore).text = "(${q.score} درجة)"

            val rgOptions = block.findViewById<RadioGroup>(R.id.rgAnswerOptions)
            val etEssay = block.findViewById<EditText>(R.id.etEssayAnswer)

            val radioIds = listOf(R.id.rbAns1, R.id.rbAns2, R.id.rbAns3, R.id.rbAns4)

            if (q.type == "mcq") {
                rgOptions.visibility = View.VISIBLE
                etEssay.visibility = View.GONE

                q.options.forEachIndexed { i, optionText ->
                    val rb = block.findViewById<RadioButton>(radioIds[i])
                    rb.text = optionText
                    rb.visibility = View.VISIBLE
                }
                for (i in q.options.size until radioIds.size) {
                    block.findViewById<RadioButton>(radioIds[i]).visibility = View.GONE
                }

                // لو القراءة فقط (الطالب دخل يراجع امتحانه القديم)
                if (readOnly) {
                    val match = savedAnswers.find { it["qId"] == q.qId }
                    val chosenIndex = (match?.get("chosenIndex") as? Number)?.toInt() ?: -1

                    q.options.forEachIndexed { i, _ ->
                        val rb = block.findViewById<RadioButton>(radioIds[i])
                        rb.isEnabled = false

                        // تلوين الإجابة الصحيحة بالأخضر، واختيار الطالب الخاطئ بالأحمر
                        when {
                            i == q.correctIndex -> {
                                rb.setTextColor(Color.parseColor("#4CAF50")) // أخضر للإجابة الصحيحة
                                rb.text = "${rb.text} (✅ الإجابة الصحيحة)"
                            }
                            i == chosenIndex && i != q.correctIndex -> {
                                rb.setTextColor(Color.parseColor("#E53935")) // أحمر لاختيار الطالب الخطأ
                                rb.text = "${rb.text} (❌ اختيارك)"
                            }
                        }
                    }
                    if (chosenIndex >= 0 && chosenIndex < radioIds.size) {
                        rgOptions.check(radioIds[chosenIndex])
                    }
                }
            } else {
                rgOptions.visibility = View.GONE
                etEssay.visibility = View.VISIBLE
                if (readOnly) {
                    etEssay.isEnabled = false
                    val matchEssay = savedAnswers.find { it["qId"] == q.qId }
                    // لو مقالي بنعرض إجابة الطالب اللي كتبها
                    val savedText = matchEssay?.get("answerText") as? String ?: ""
                    etEssay.setText(savedText)
                }
            }

            container.addView(block)
            questionBlocks.add(block)
        }
    }

    private fun submitExam(auto: Boolean) {
        if (submitted || !::exam.isInitialized) return
        submitted = true
        running = false
        timer?.cancel()
        findViewById<Button>(R.id.btnSubmitExam).isEnabled = false

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

        val resultData = hashMapOf(
            "examId" to exam.examId,
            "examTitle" to exam.title,
            "teacherCode" to exam.teacherCode,
            "studentName" to studentName,
            "totalScore" to exam.totalScore,
            "autoScore" to autoScore,
            "graded" to !hasEssay,
            "autoSubmitted" to auto,
            "mcqAnswers" to mcqAnswers,
            "essayAnswers" to essayAnswers,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance().collection("results")
            .document(resultId)
            .set(resultData)
            .addOnSuccessListener {
                if (auto) {
                    Toast.makeText(this, "تم تسليم الامتحان تلقائيًا ⏱", Toast.LENGTH_LONG).show()
                    finish()
                } else if (!hasEssay) {
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
                submitted = false
                findViewById<Button>(R.id.btnSubmitExam).isEnabled = true
                Toast.makeText(this, "حدث خطأ أثناء حفظ الإجابات: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCertificate(examTitle: String, score: Int, total: Int, studentName: String) {
        setContentView(R.layout.activity_certificate)
        findViewById<TextView>(R.id.tvCertStudentName).text = studentName
        findViewById<TextView>(R.id.tvCertExamTitle).text = examTitle
        findViewById<TextView>(R.id.tvCertScore).text = "$score من $total"
        findViewById<Button>(R.id.btnCertClose).setOnClickListener { finish() }
    }
}
