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

data class ExamModel(
    val examId: String = "",
    val title: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val teacherCode: String = ""
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
            val intent = Intent(this, TakeExamActivity::class.java).apply {
                putExtra("EXAM_ID", exam.examId)
                putExtra("EXAM_TITLE", exam.title)
                putExtra("EXAM_QUESTION", exam.question)
                putExtra("EXAM_OP1", exam.options.getOrNull(0) ?: "")
                putExtra("EXAM_OP2", exam.options.getOrNull(1) ?: "")
                putExtra("EXAM_OP3", exam.options.getOrNull(2) ?: "")
                putExtra("EXAM_OP4", exam.options.getOrNull(3) ?: "")
                putExtra("CORRECT_INDEX", exam.correctIndex)
            }
            startActivity(intent)
        }
        rvExamsList?.adapter = adapter

        loadExams()
    }

    private fun loadExams() {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val joinedCode = prefs.getString("joined_teacher_code", "") ?: ""

        FirebaseFirestore.getInstance().collection("exams")
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

        inner class ExamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView? = view.findViewById(resources.getIdentifier("tvItemExamTitle", "id", packageName))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
            val itemLayoutRes = resources.getIdentifier("item_exam", "layout", packageName)
            val view = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
            return ExamViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
            val item = list[position]
            holder.tvTitle?.text = item.title
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
