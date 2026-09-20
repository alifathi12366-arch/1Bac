package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

data class ExamQuestion(
    val qId: String = "",
    val text: String = "",
    val type: String = "mcq",
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val score: Int = 0
)

data class ExamModel(
    val examId: String = "",
    val title: String = "",
    val teacherCode: String = "",
    val totalScore: Int = 0,
    val questions: List<ExamQuestion> = emptyList()
)

class StudentExamsActivity : AppCompatActivity() {

    private val examList = mutableListOf<ExamModel>()
    private lateinit var adapter: ExamAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutRes = resources.getIdentifier("activity_student_exams", "layout", packageName)
        if (layoutRes != 0) setContentView(layoutRes)

        val rvId = resources.getIdentifier("rvExamsList", "id", packageName)
        val rvExamsList = if (rvId != 0) findViewById<RecyclerView>(rvId) else null

        rvExamsList?.layoutManager = LinearLayoutManager(this)
        adapter = ExamAdapter(examList) { exam ->
            val intent = Intent(this, TakeExamActivity::class.java)
            intent.putExtra("EXAM_ID", exam.examId)
            startActivity(intent)
        }
        rvExamsList?.adapter = adapter

        loadExams()
    }

    override fun onResume() {
        super.onResume()
        loadExams()
    }

    private fun loadExams() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val joinedCode = prefs.getString("joined_teacher_code", "") ?: ""
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"

        val db = FirebaseFirestore.getInstance()

        db.collection("exams")
            .whereEqualTo("teacherCode", joinedCode)
            .get()
            .addOnSuccessListener { documents ->
                examList.clear()
                for (doc in documents) {
                    val exam = doc.toObject(ExamModel::class.java)
                    examList.add(exam)
                }
                adapter.notifyDataSetChanged()

                if (examList.isEmpty()) {
                    Toast.makeText(this, "لا توجد امتحانات متاحة حالياً", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("results")
                    .whereEqualTo("studentName", studentName)
                    .whereEqualTo("teacherCode", joinedCode)
                    .get()
                    .addOnSuccessListener { resultsSnap ->
                        val resultsMap = mutableMapOf<String, Pair<Int, Boolean>>()
                        for (doc in resultsSnap.documents) {
                            val examId = doc.getString("examId") ?: continue
                            val score = (doc.getLong("autoScore") ?: 0L).toInt()
                            val graded = doc.getBoolean("graded") ?: true
                            resultsMap[examId] = Pair(score, graded)
                        }
                        adapter.updateResults(resultsMap)
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "خطأ في تحميل الامتحانات", Toast.LENGTH_SHORT).show()
            }
    }

    inner class ExamAdapter(
        private val list: List<ExamModel>,
        private val onClick: (ExamModel) -> Unit
    ) : RecyclerView.Adapter<ExamAdapter.ExamViewHolder>() {

        private var resultsMap: Map<String, Pair<Int, Boolean>> = emptyMap()

        fun updateResults(map: Map<String, Pair<Int, Boolean>>) {
            resultsMap = map
            notifyDataSetChanged()
        }

        inner class ExamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView? = view.findViewById(resources.getIdentifier("tvItemExamTitle", "id", packageName))
            val tvStatus: TextView? = view.findViewById(resources.getIdentifier("tvItemExamStatus", "id", packageName))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
            val itemLayoutRes = resources.getIdentifier("item_exam", "layout", packageName)
            val view = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
            return ExamViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
            val item = list[position]
            holder.tvTitle?.text = item.title

            val result = resultsMap[item.examId]
            holder.tvStatus?.text = when {
                result == null -> "اضغط للبدء في الامتحان"
                !result.second -> "بانتظار تصحيح المدرس ⏳"
                else -> "✅ درجتك: ${result.first} من ${item.totalScore}"
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
