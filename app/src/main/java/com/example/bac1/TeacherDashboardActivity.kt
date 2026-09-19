package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

data class TeacherContent(
    val id: String = "",
    val teacherId: String = "",
    val title: String = "",
    val description: String = "",
    val youtubeUrl: String = "",
    val pdfUrl: String = "",
    val directVideoUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class TeacherDashboardActivity : AppCompatActivity() {

    private var selectedPdfUri: Uri? = null
    private var selectedVideoUri: Uri? = null

    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedPdfUri = uri
            Toast.makeText(this, "تم اختيار ملزمة PDF ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            Toast.makeText(this, "تم اختيار الفيديو ✅", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard)
        findViewById<Button?>(resources.getIdentifier("btnAddExam", "id", packageName))?.setOnClickListener {
    startActivity(Intent(this, AddExamActivity::class.java))
        }

        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etDescription = findViewById<EditText>(R.id.etDescription)
        val etYoutubeUrl = findViewById<EditText>(R.id.etYoutubeUrl)
        val btnSelectPdf = findViewById<Button>(R.id.btnSelectPdf)
        val btnSelectVideo = findViewById<Button>(R.id.btnSelectVideo)
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
        findViewById<Button>(R.id.btnViewResults).setOnClickListener {
            startActivity(Intent(this, TeacherResultsActivity::class.java))
        }

        btnSelectPdf.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }

        btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        btnPublish.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val youtubeUrl = etYoutubeUrl.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "يرجى كتابة عنوان الدرس", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            uploadContent(title, description, youtubeUrl)
        }

        if (teacherCode.isNotEmpty()) {
            loadStudentQuestions(teacherCode)
        }

       // جلب عدد الطلاب المسجلين
        if (teacherCode.isNotEmpty()) {
            getStudentCount(teacherCode) { count ->
                studentCountText.text = "عدد الطلاب المسجلين معاك: $count طالب 👥"
            }
        } else {
            studentCountText.text = "عدد الطلاب المسجلين معاك: 0"
        }

        loadComprehensiveStats(teacherCode)
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

            private fun uploadContent(title: String, description: String, youtubeUrl: String) {
        val progressText = findViewById<TextView>(R.id.uploadProgressText)
        progressText.visibility = android.view.View.VISIBLE
        progressText.text = "جاري الرفع... 0%"

        var pdfUploadUrl = ""
        var videoUploadUrl = ""

        fun saveToFirestore() {
            val db = FirebaseFirestore.getInstance()
            val contentId = UUID.randomUUID().toString()
            val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
            val teacherCode = prefs.getString("teacher_code", "") ?: ""

            val data = hashMapOf(
                "teacherCode" to teacherCode,
                "type" to "message",
                "text" to "$title\n\n$description",
                "youtubeUrl" to youtubeUrl,
                "pdfUrl" to pdfUploadUrl,
                "directVideoUrl" to videoUploadUrl,
                "createdAt" to System.currentTimeMillis()
            )

            db.collection("teacherContent")
                .document(contentId)
                .set(data)
                .addOnSuccessListener {
                    progressText.visibility = android.view.View.GONE
                    Toast.makeText(this, "تم نشر المحتوى بنجاح! 🚀", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    progressText.visibility = android.view.View.GONE
                    Toast.makeText(this, "فشل النشر: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        if (selectedPdfUri != null) {
            val storage = FirebaseStorage.getInstance()
            val pdfRef = storage.reference.child("pdfs/${UUID.randomUUID()}.pdf")
            val uploadTask = pdfRef.putFile(selectedPdfUri!!)
            uploadTask.addOnProgressListener { snapshot ->
                val percent = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                progressText.text = "جاري رفع الملزمة... $percent%"
            }
            uploadTask.addOnSuccessListener {
                pdfRef.downloadUrl.addOnSuccessListener { downloadUri: Uri ->
                    pdfUploadUrl = downloadUri.toString()
                    uploadVideoIfExist(progressText) { saveToFirestore() }
                }
            }
        } else {
            uploadVideoIfExist(progressText) { saveToFirestore() }
        }
    }

    private fun uploadVideoIfExist(progressText: TextView, onComplete: () -> Unit) {
        if (selectedVideoUri != null) {
            val storage = FirebaseStorage.getInstance()
            val videoRef = storage.reference.child("videos/${UUID.randomUUID()}.mp4")
            val uploadTask = videoRef.putFile(selectedVideoUri!!)
            uploadTask.addOnProgressListener { snapshot ->
                val percent = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                progressText.text = "جاري رفع الفيديو... $percent%"
            }
            uploadTask.addOnSuccessListener {
                videoRef.downloadUrl.addOnSuccessListener { downloadUri: Uri ->
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
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
