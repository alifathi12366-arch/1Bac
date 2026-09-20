package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.UUID

class TeacherDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard)

        findViewById<Button?>(resources.getIdentifier("btnAddExam", "id", packageName))?.setOnClickListener {
            startActivity(Intent(this, AddExamActivity::class.java))
        }

        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etDescription = findViewById<EditText>(R.id.etDescription)
        val etYoutubeUrl = findViewById<EditText>(R.id.etYoutubeUrl)
        val btnPublish = findViewById<Button>(R.id.sendMessageButton)
        val studentCountText = findViewById<TextView>(R.id.studentCountText)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherName = prefs.getString("teacher_name", "") ?: ""
        val teacherSubject = prefs.getString("teacher_subject", "") ?: ""
        val teacherCode = prefs.getString("teacher_code", "") ?: ""
        val expired = prefs.getBoolean("teacher_expired", false)
        val expiringSoon = prefs.getBoolean("teacher_expiring_soon", false)

        findViewById<TextView>(R.id.teacherWelcomeText).text = "أهلاً بيك أستاذ $teacherName 👋"
        findViewById<TextView>(R.id.teacherSubjectText).text = "مادة: $teacherSubject"

        val expiryText = findViewById<TextView>(R.id.teacherExpiryText)
        when {
            expired -> expiryText.text = "⚠️ اشتراكك انتهى، تواصل معانا لتجديده"
            expiringSoon -> expiryText.text = "⚠️ اشتراكك هينتهي قريبًا، جدد عشان طلابك يفضلوا شايفين المحتوى"
            else -> expiryText.text = ""
        }

        val currentJoinCode = prefs.getString("teacher_join_code", "") ?: ""
        findViewById<TextView>(R.id.currentJoinCodeText).text =
            if (currentJoinCode.isNotEmpty()) "كود طلابك الحالي: $currentJoinCode" else "لسه محددتش كود لطلابك"

        findViewById<Button>(R.id.setJoinCodeButton).setOnClickListener {
            startActivity(Intent(this, SetJoinCodeActivity::class.java))
        }
        findViewById<Button>(R.id.btnMyContent).setOnClickListener {
            startActivity(Intent(this, TeacherMyContentActivity::class.java))
        }
        findViewById<Button>(R.id.btnTeacherQuestions).setOnClickListener {
            startActivity(Intent(this, TeacherQuestionsActivity::class.java))
        }
        findViewById<Button>(R.id.btnViewResults).setOnClickListener {
            startActivity(Intent(this, TeacherResultsActivity::class.java))
        }

        btnPublish.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val youtubeUrl = etYoutubeUrl.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "يرجى كتابة عنوان الدرس", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val contentId = UUID.randomUUID().toString()
            val data = hashMapOf(
                "teacherCode" to teacherCode,
                "type" to "message",
                "text" to "$title\n\n$description",
                "youtubeUrl" to youtubeUrl,
                "pdfUrl" to "",
                "directVideoUrl" to "",
                "createdAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance().collection("teacherContent")
                .document(contentId)
                .set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "تم نشر المحتوى بنجاح! 🚀", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "فشل النشر: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        if (teacherCode.isNotEmpty()) {
            loadStudentQuestions(teacherCode)
        }

        if (teacherCode.isNotEmpty()) {
            getStudentCount(teacherCode) { count ->
                studentCountText.text = "عدد الطلاب المسجلين معاك: $count طالب 👥"
            }
        } else {
            studentCountText.text = "عدد الطلاب المسجلين معاك: 0"
        }

        loadComprehensiveStats(teacherCode)
        loadAbsentToday(teacherCode)
    }

    private fun loadAbsentToday(teacherCode: String) {
        val absentText = findViewById<TextView>(R.id.absentTodayList)
        val db = FirebaseFirestore.getInstance()
        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        db.collection("users").whereEqualTo("joined_teacher_code", teacherCode).get()
            .addOnSuccessListener { rosterSnap ->
                val rosterNames = rosterSnap.documents.mapNotNull { it.getString("name") }.filter { it.isNotBlank() }
                if (rosterNames.isEmpty()) {
                    absentText.text = "لا يوجد طلاب مسجلين بعد"
                    return@addOnSuccessListener
                }
                db.collection("attendance")
                    .whereEqualTo("teacherCode", teacherCode)
                    .whereEqualTo("date", todayDate)
                    .get()
                    .addOnSuccessListener { attSnap ->
                        val attendedNames = attSnap.documents.mapNotNull { it.getString("studentName") }.toSet()
                        val absentNames = rosterNames.filter { it !in attendedNames }
                        absentText.text = if (absentNames.isEmpty())
                            "كل الطلاب حضروا النهاردة ✅"
                        else
                            absentNames.joinToString("، ")
                    }
            }
    }

    private fun loadComprehensiveStats(teacherCode: String) {
        val db = FirebaseFirestore.getInstance()
        val statsAttendanceText = findViewById<TextView>(R.id.statsAttendanceText)
        val statsRatingText = findViewById<TextView>(R.id.statsRatingText)

        db.collection("attendance")
            .whereEqualTo("teacherCode", teacherCode)
            .get()
            .addOnSuccessListener { snapshot ->
                statsAttendanceText.text = "إجمالي مرات الحضور المسجلة: ${snapshot.size()} 📈"
            }
            .addOnFailureListener {
                statsAttendanceText.text = "إجمالي مرات الحضور المسجلة: 0 📈"
            }

        db.collection("teachers").document(teacherCode).get()
            .addOnSuccessListener { doc ->
                val rating = doc.getDouble("rating") ?: 0.0
                val ratingCount = (doc.getLong("ratingCount") ?: 0L).toInt()
                statsRatingText.text = if (ratingCount > 0)
                    "متوسط تقييمك: ${String.format("%.1f", rating)} ⭐ (من $ratingCount تقييم)"
                else
                    "متوسط تقييمك: لسه مفيش تقييمات ⭐"
            }
    }

    private fun getStudentCount(joinCode: String, onResult: (Int) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .whereEqualTo("joined_teacher_code", joinCode)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.size())
            }
            .addOnFailureListener {
                onResult(0)
            }
    }

    private fun loadStudentQuestions(teacherCode: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("interactive_questions")
            .whereEqualTo("teacherCode", teacherCode)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
            }
    }
}
