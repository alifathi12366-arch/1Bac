package com.example.bac1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

data class ResultItem(
    val docId: String = "",
    val studentName: String = "",
    val examTitle: String = "",
    val totalScore: Int = 0,
    val autoScore: Int = 0,
    val graded: Boolean = true,
    val mcqAnswers: List<Map<String, Any>> = emptyList(),
    val essayAnswers: List<Map<String, Any>> = emptyList()
)

class TeacherResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_results)

        val rvResults = findViewById<RecyclerView>(R.id.rvTeacherResults)
        rvResults.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""

        FirebaseFirestore.getInstance().collection("results")
            .whereEqualTo("teacherCode", teacherCode)
            .get()
            .addOnSuccessListener { query ->
                val list = mutableListOf<ResultItem>()
                for (doc in query.documents) {
                    list.add(
                        ResultItem(
                            docId = doc.id,
                            studentName = doc.getString("studentName") ?: "طالب",
                            examTitle = doc.getString("examTitle") ?: "",
                            totalScore = (doc.getLong("totalScore") ?: 0L).toInt(),
                            autoScore = (doc.getLong("autoScore") ?: 0L).toInt(),
                            graded = doc.getBoolean("graded") ?: true,
                            mcqAnswers = doc.get("mcqAnswers") as? List<Map<String, Any>> ?: emptyList(),
                            essayAnswers = doc.get("essayAnswers") as? List<Map<String, Any>> ?: emptyList()
                        )
                    )
                }

                if (list.isEmpty()) {
                    Toast.makeText(this, "لا توجد نتائج مسجلة حتى الآن", Toast.LENGTH_SHORT).show()
                }

                rvResults.adapter = ResultsAdapter(list)
            }
            .addOnFailureListener {
                Toast.makeText(this, "حدث خطأ أثناء تحميل النتائج", Toast.LENGTH_SHORT).show()
            }
    }
}

class ResultsAdapter(private val items: List<ResultItem>) :
    RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val view = holder.view

        val tvHeader = view.findViewById<TextView>(R.id.tvResultHeader)
        val tvScore = view.findViewById<TextView>(R.id.tvResultScore)
        val essayContainer = view.findViewById<android.widget.LinearLayout>(R.id.essayGradingContainer)
        val btnSave = view.findViewById<Button>(R.id.btnSaveGrading)

        tvHeader.text = "${item.examTitle} - ${item.studentName}"

        essayContainer.removeAllViews()
        val essayScoreInputs = mutableListOf<EditText>()

        if (item.essayAnswers.isNotEmpty()) {
            essayContainer.visibility = View.VISIBLE

            for (essay in item.essayAnswers) {
                val essayView = LayoutInflater.from(view.context).inflate(R.layout.item_essay_grading, essayContainer, false)
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
                            Toast.makeText(view.context, "تم حفظ التصحيح ✅", Toast.LENGTH_SHORT).show()
                            tvScore.text = "الدرجة النهائية: $finalScore من ${item.totalScore} ✅"
                            btnSave.visibility = View.GONE
                        }
                        .addOnFailureListener {
                            Toast.makeText(view.context, "فشل حفظ التصحيح", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        } else {
            essayContainer.visibility = View.GONE
            btnSave.visibility = View.GONE
            tvScore.text = "الدرجة: ${item.autoScore} من ${item.totalScore} ✅"
        }
    }

    override fun getItemCount(): Int = items.size
    }
