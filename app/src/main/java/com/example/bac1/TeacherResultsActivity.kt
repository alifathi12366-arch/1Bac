package com.example.bac1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

data class ResultItem(
    val docId: String = "",
    val studentName: String = "",
    val examId: String = "",
    val examTitle: String = "",
    val totalScore: Int = 0,
    val autoScore: Int = 0,
    val graded: Boolean = true,
    val essayAnswers: List<Map<String, Any>> = emptyList()
)

class TeacherResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_results)

        val resultsContainer = findViewById<LinearLayout>(R.id.resultsContainer)
        val absenteesContainer = findViewById<LinearLayout>(R.id.examAbsenteesContainer)
        val absenteesTitle = findViewById<TextView>(R.id.absenteesTitle)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""
        val db = FirebaseFirestore.getInstance()

        db.collection("results")
            .whereEqualTo("teacherCode", teacherCode)
            .get()
            .addOnSuccessListener { query ->
                val list = mutableListOf<ResultItem>()
                for (doc in query.documents) {
                    list.add(
                        ResultItem(
                            docId = doc.id,
                            studentName = doc.getString("studentName") ?: "طالب",
                            examId = doc.getString("examId") ?: "",
                            examTitle = doc.getString("examTitle") ?: "",
                            totalScore = (doc.getLong("totalScore") ?: 0L).toInt(),
                            autoScore = (doc.getLong("autoScore") ?: 0L).toInt(),
                            graded = doc.getBoolean("graded") ?: true,
                            essayAnswers = doc.get("essayAnswers") as? List<Map<String, Any>> ?: emptyList()
                        )
                    )
                }

                if (list.isEmpty()) {
                    Toast.makeText(this, "لا توجد نتائج مسجلة حتى الآن", Toast.LENGTH_SHORT).show()
                }

                for (item in list) {
                    renderResultCard(item, resultsContainer)
                }

                loadExamAbsentees(teacherCode, list, absenteesContainer, absenteesTitle)
            }
            .addOnFailureListener {
                Toast.makeText(this, "حدث خطأ أثناء تحميل النتائج", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderResultCard(item: ResultItem, container: LinearLayout) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_teacher_result, container, false)

        val tvHeader = view.findViewById<TextView>(R.id.tvResultHeader)
        val tvScore = view.findViewById<TextView>(R.id.tvResultScore)
        val essayContainer = view.findViewById<LinearLayout>(R.id.essayGradingContainer)
        val btnSave = view.findViewById<Button>(R.id.btnSaveGrading)

        tvHeader.text = "${item.examTitle} - ${item.studentName}"

        val essayScoreInputs = mutableListOf<EditText>()

        if (item.essayAnswers.isNotEmpty()) {
            essayContainer.visibility = View.VISIBLE

            for (essay in item.essayAnswers) {
                val essayView = LayoutInflater.from(this).inflate(R.layout.item_essay_grading, essayContainer, false)
                essayView.findViewById<TextView>(R.id.tvEssayQuestion).text = essay["text"] as? String ?: ""
                essayView.findViewById<TextView>(R.id.tvEssayAnswer).text = essay["answerText"] as? String ?: "(لم يجب)"

                val maxScore = (essay["maxScore"] as? Long)?.toInt() ?: 0
                val awardedScore = (essay["awardedScore"] as? Long)?.toInt() ?: -1
                val etScore = essayView.findViewById<EditText>(R.id.etAwardedScore)
                etScore.hint = "الدرجة من $maxScore"
                if (awardedScore >= 0) etScore.setText(awardedScore.toString())

                essayScoreInputs.add(etScore)
                essayContainer.addView(essayView)
            }

            if (item.graded) {
                tvScore.text = "الدرجة النهائية: ${item.autoScore} من ${item.totalScore} ✅ (تم التصحيح)"
                btnSave.visibility = View.GONE
                for (input in essayScoreInputs) input.isEnabled = false
            } else {
                tvScore.text = "درجة الاختياري: ${item.autoScore} من ${item.totalScore} (بانتظار تصحيح المقالي)"
                btnSave.visibility = View.VISIBLE

                btnSave.setOnClickListener {
                    var essayTotal = 0
                    val updatedEssays = item.essayAnswers.mapIndexed { i, essay ->
                        val maxScore = (essay["maxScore"] as? Long)?.toInt() ?: 0
                        val enteredScore = essayScoreInputs[i].text.toString().toIntOrNull() ?: 0
                        val finalScore = enteredScore.coerceIn(0, maxScore)
                        essayTotal += finalScore
                        hashMapOf(
                            "qId" to (essay["qId"] as? String ?: ""),
                            "text" to (essay["text"] as? String ?: ""),
                            "answerText" to (essay["answerText"] as? String ?: ""),
                            "maxScore" to maxScore,
                            "awardedScore" to finalScore
                        )
                    }

                    val finalScore = item.autoScore + essayTotal

                    FirebaseFirestore.getInstance().collection("results").document(item.docId)
                        .update(
                            mapOf(
                                "essayAnswers" to updatedEssays,
                                "autoScore" to finalScore,
                                "graded" to true
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(this, "تم حفظ التصحيح ✅", Toast.LENGTH_SHORT).show()
                            tvScore.text = "الدرجة النهائية: $finalScore من ${item.totalScore} ✅"
                            btnSave.visibility = View.GONE
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "فشل حفظ التصحيح", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        } else {
            essayContainer.visibility = View.GONE
            btnSave.visibility = View.GONE
            tvScore.text = "الدرجة: ${item.autoScore} من ${item.totalScore} ✅"
        }

        container.addView(view)
    }

    private fun loadExamAbsentees(
        teacherCode: String,
        results: List<ResultItem>,
        container: LinearLayout,
        title: TextView
    ) {
        val db = FirebaseFirestore.getInstance()

        db.collection("users").whereEqualTo("joined_teacher_code", teacherCode).get()
            .addOnSuccessListener { rosterSnap ->
                val rosterNames = rosterSnap.documents.mapNotNull { it.getString("name") }.filter { it.isNotBlank() }.distinct()
                if (rosterNames.isEmpty()) return@addOnSuccessListener

                db.collection("exams").whereEqualTo("teacherCode", teacherCode).get()
                    .addOnSuccessListener { examsSnap ->
                        if (examsSnap.isEmpty) return@addOnSuccessListener

                        title.visibility = View.VISIBLE

                        for (examDoc in examsSnap.documents) {
                            val examId = examDoc.getString("examId") ?: examDoc.id
                            val examTitle = examDoc.getString("title") ?: ""

                            val submittedNames = results.filter { it.examId == examId }.map { it.studentName }.toSet()
                            val absentNames = rosterNames.filter { it !in submittedNames }

                            val row = TextView(this)
                            row.setTextColor(android.graphics.Color.parseColor("#FF8A65"))
                            row.textSize = 14f
                            row.setPadding(0, 0, 0, 10)
                            row.text = if (absentNames.isEmpty())
                                "✅ $examTitle: كل الطلاب قدّموا الامتحان"
                            else
                                "⚠️ $examTitle: ${absentNames.joinToString("، ")}"

                            container.addView(row)
                        }
                    }
            }
    }
}
