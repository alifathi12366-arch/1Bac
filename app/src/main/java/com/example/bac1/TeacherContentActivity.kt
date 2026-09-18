package com.example.bac1

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RatingBar
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

        markStudentAttendance(joinedTeacherCode)

        val etQuestion = findViewById<EditText>(R.id.etQuestion)
        val btnSendQuestion = findViewById<Button>(R.id.btnSendQuestion)
        val btnMarkAttendance = findViewById<Button>(R.id.btnMarkAttendance)
        val ratingBar = findViewById<RatingBar>(R.id.teacherRatingBar)
        val btnSubmitRating = findViewById<Button>(R.id.btnSubmitRating)

        btnMarkAttendance.text = "✅ تم تسجيل حضورك النهاردة"
        btnMarkAttendance.isEnabled = false
        findViewById<Button>(R.id.btnGoToExams).setOnClickListener {
            startActivity(Intent(this, StudentExamsActivity::class.java))
        }

        loadExistingRating(joinedTeacherCode, ratingBar, btnSubmitRating)

        btnSubmitRating.setOnClickListener {
            val stars = ratingBar.rating.toDouble()
            if (stars <= 0.0) {
                Toast.makeText(this, "اختار نجوم الأول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            submitRating(joinedTeacherCode, stars, btnSubmitRating)
        }

        btnSendQuestion.setOnClickListener {
            val questionText = etQuestion.text?.toString()?.trim() ?: ""
            if (questionText.isNotEmpty()) {
                sendStudentQuestion(joinedTeacherCode, questionText)
                etQuestion.setText("")
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

    private fun ratingDocId(teacherCode: String): String {
        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        return "${teacherCode}_$studentName"
    }

    private fun loadExistingRating(teacherCode: String, ratingBar: RatingBar, btnSubmitRating: Button) {
        val db = FirebaseFirestore.getInstance()
        db.collection("ratings").document(ratingDocId(teacherCode)).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val stars = doc.getDouble("stars") ?: 0.0
                    ratingBar.rating = stars.toFloat()
                    btnSubmitRating.text = "تعديل تقييمك"
                }
            }
    }

    private fun submitRating(teacherCode: String, newStars: Double, btnSubmitRating: Button) {
        val db = FirebaseFirestore.getInstance()
        val ratingRef = db.collection("ratings").document(ratingDocId(teacherCode))
        val teacherRef = db.collection("teachers").document(teacherCode)

        btnSubmitRating.isEnabled = false

        ratingRef.get().addOnSuccessListener { ratingDoc ->
            val oldStars = if (ratingDoc.exists()) ratingDoc.getDouble("stars") else null

            db.runTransaction { transaction ->
                val teacherSnap = transaction.get(teacherRef)
                val currentAvg = teacherSnap.getDouble("rating") ?: 0.0
                val currentCount = (teacherSnap.getLong("ratingCount") ?: 0L).toInt()

                val newAvg: Double
                val newCount: Int

                if (oldStars == null) {
                    newCount = currentCount + 1
                    newAvg = ((currentAvg * currentCount) + newStars) / newCount
                } else {
                    newCount = currentCount
                    newAvg = if (newCount > 0)
                        ((currentAvg * currentCount) - oldStars + newStars) / newCount
                    else newStars
                }

                transaction.update(teacherRef, "rating", newAvg)
                transaction.update(teacherRef, "ratingCount", newCount)
                transaction.set(ratingRef, hashMapOf("stars" to newStars, "teacherCode" to teacherCode))
            }.addOnSuccessListener {
                btnSubmitRating.isEnabled = true
                btnSubmitRating.text = "تعديل تقييمك"
                Toast.makeText(this, "تم حفظ تقييمك، شكرًا لك 🌟", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                btnSubmitRating.isEnabled = true
                Toast.makeText(this, "فشل حفظ التقييم: ${e.message}", Toast.LENGTH_SHORT).show()
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
