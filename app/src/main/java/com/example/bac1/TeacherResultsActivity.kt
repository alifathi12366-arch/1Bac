package com.example.bac1

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

data class ResultItem(
    val studentName: String = "",
    val score: Int = 0,
    val examId: String = ""
)

class TeacherResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutRes = resources.getIdentifier("activity_teacher_results", "layout", packageName)
        if (layoutRes != 0) setContentView(layoutRes)

        val rvId = resources.getIdentifier("rvTeacherResults", "id", packageName)
        val rvResults = if (rvId != 0) findViewById<RecyclerView>(rvId) else null
        rvResults?.layoutManager = LinearLayoutManager(this)

        val db = FirebaseFirestore.getInstance()
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_code", "") ?: ""

        db.collection("results").get()
            .addOnSuccessListener { query ->
                val list = mutableListOf<ResultItem>()
                for (doc in query.documents) {
                    val studentName = doc.getString("studentName") ?: "طالب"
                    val score = doc.getLong("score")?.toInt() ?: 0
                    val examId = doc.getString("examId") ?: ""
                    list.add(ResultItem(studentName, score, examId))
                }

                if (list.isEmpty()) {
                    Toast.makeText(this, "لا توجد نتائج مسجلة حتى الآن", Toast.LENGTH_SHORT).show()
                }

                val adapter = ResultsAdapter(list)
                rvResults?.adapter = adapter
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
        val tv = TextView(parent.context).apply {
            textSize = 16sp
            setPadding(24, 24, 24, 24)
        }
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        (holder.view as TextView).text = "اسم الطالب: ${item.studentName}\nالدرجة: ${item.score}%"
    }

    override fun getItemCount(): Int = items.size
    }
