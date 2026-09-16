package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeacherContentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_content)

        val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val joinedTeacherCode = prefs.getString("joined_teacher_code", "") ?: ""
        val joinedTeacherName = prefs.getString("joined_teacher_name", "") ?: ""

        val nameView = findViewById<android.widget.TextView>(R.id.contentTeacherName)
        val noContentText = findViewById<android.widget.TextView>(R.id.noContentText)
        val recyclerView = findViewById<RecyclerView>(R.id.contentRecyclerView)
        
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (joinedTeacherCode.isEmpty()) {
            nameView.text = "لسه مسجلتش مع أي مدرس"
            noContentText.visibility = View.VISIBLE
            return
        }

        nameView.text = "أستاذ $joinedTeacherName"

        val etQuestion = findViewById<EditText?>(resources.getIdentifier("etQuestion", "id", packageName))
        val btnSendQuestion = findViewById<Button?>(resources.getIdentifier("btnSendQuestion", "id", packageName))
        val btnMarkAttendance = findViewById<Button?>(resources.getIdentifier("btnMarkAttendance", "id", packageName))

        // زرار تسجيل الحضور
        btnMarkAttendance?.setOnClickListener {
            markStudentAttendance(joinedTeacherCode)
        }

        btnSendQuestion?.setOnClickListener {
            val questionText = etQuestion?.text?.toString()?.trim() ?: ""
            if (questionText.isNotEmpty()) {
                sendStudentQuestion(joinedTeacherCode, questionText)
                etQuestion?.setText("")
            } else {
                Toast.makeText(this, "اكتب سؤالك الأول", Toast.LENGTH_SHORT).show()
            }
        }

        FirebaseFirestore.getInstance()
            .collection("teacherContent")
            .whereEqualTo("teacherCode", joinedTeacherCode)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val items = result.documents.map { doc ->
                    TeacherContentItem(
                        type = doc.getString("type") ?: "message",
                        text = doc.getString("text") ?: ""
                    )
                }
                if (items.isEmpty()) {
                    noContentText.visibility = View.VISIBLE
                } else {
                    recyclerView.adapter = TeacherContentAdapter(items)
                }
            }
    }

    private fun markStudentAttendance(teacherCode: String) {
        val db = FirebaseFirestore.getInstance()
        val sharedPref = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = sharedPref.getString("student_name", "طالب") ?: "طالب"
        
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val attendanceId = "${teacherCode}_${studentName}_$todayDate"

        val attendanceData = hashMapOf(
            "studentName" to studentName,
            "teacherCode" to teacherCode,
            "date" to todayDate,
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("attendance")
            .document(attendanceId)
            .set(attendanceData)
            .addOnSuccessListener {
                Toast.makeText(this, "تم تسجيل حضورك اليوم بنجاح ✋✅", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "فشل تسجيل الحضور: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendStudentQuestion(teacherCode: String, questionText: String) {
        val db = FirebaseFirestore.getInstance()
        val sharedPref = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = sharedPref.getString("student_name", "طالب") ?: "طالب"

        val questionData = hashMapOf(
            "studentName" to studentName,
            "teacherCode" to teacherCode,
            "question" to questionText,
            "reply" to "",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        db.collection("interactive_questions")
            .add(questionData)
            .addOnSuccessListener {
                Toast.makeText(this, "تم إرسال سؤالك للمدرس بنجاح 📩", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "فشل إرسال السؤال: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}      
