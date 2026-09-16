package com.example.bac1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class AttendanceItem(
    val studentName: String = "",
    val date: String = ""
)

class AttendanceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        val recyclerView = findViewById<RecyclerView>(R.id.attendanceRecyclerView)
        val noAttendanceText = findViewById<TextView>(R.id.noAttendanceText)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherCode = prefs.getString("teacher_join_code", "") ?: ""

        if (teacherCode.isEmpty()) {
            noAttendanceText.visibility = View.VISIBLE
            return
        }

        FirebaseFirestore.getInstance()
            .collection("attendance")
            .whereEqualTo("teacherCode", teacherCode)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.map { doc ->
                    AttendanceItem(
                        studentName = doc.getString("studentName") ?: "طالب",
                        date = doc.getString("date") ?: ""
                    )
                }

                if (list.isEmpty()) {
                    noAttendanceText.visibility = View.VISIBLE
                } else {
                    noAttendanceText.visibility = View.GONE
                    recyclerView.adapter = AttendanceAdapter(list)
                }
            }
            .addOnFailureListener {
                noAttendanceText.visibility = View.VISIBLE
            }
    }
}

class AttendanceAdapter(private val list: List<AttendanceItem>) :
    RecyclerView.Adapter<AttendanceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(android.R.id.text1)
        val dateText: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.nameText.text = "👤 ${item.studentName}"
        holder.nameText.setTextColor(android.graphics.Color.WHITE)
        holder.dateText.text = "📅 التاريخ: ${item.date}"
        holder.dateText.setTextColor(android.graphics.Color.LTGRAY)
    }

    override fun getItemCount() = list.size
    }
