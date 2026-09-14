package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

// تعريف الكلاس هنا مباشرة لمنع أي خطأ Unresolved Reference
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

        val etTitle = findViewById<EditText>(R.id.etTitle)
        val etDescription = findViewById<EditText>(R.id.etDescription)
        val etYoutubeUrl = findViewById<EditText>(R.id.etYoutubeUrl)
        val btnSelectPdf = findViewById<Button>(R.id.btnSelectPdf)
        val btnSelectVideo = findViewById<Button>(R.id.btnSelectVideo)
        val btnPublish = findViewById<Button>(R.id.sendMessageButton)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val teacherName = prefs.getString("teacher_name", "") ?: ""
        val teacherSubject = prefs.getString("teacher_subject", "") ?: ""
        val expired = prefs.getBoolean("teacher_expired", false)
        val expiringSoon = prefs.getBoolean("teacher_expiring_soon", false)

        findViewById<android.widget.TextView>(R.id.teacherWelcomeText).text = "أهلاً بيك أستاذ $teacherName 👋"
        findViewById<android.widget.TextView>(R.id.teacherSubjectText).text = "مادة: $teacherSubject"

        val expiryText = findViewById<android.widget.TextView>(R.id.teacherExpiryText)
        when {
            expired -> expiryText.text = "⚠️ اشتراكك انتهى، تواصل معانا لتجديده"
            expiringSoon -> expiryText.text = "⚠️ اشتراكك هينتهي قريبًا، جدد عشان طلابك يفضلوا شايفين المحتوى"
            else -> expiryText.text = ""
        }

        val currentJoinCode = prefs.getString("teacher_join_code", "") ?: ""
        findViewById<android.widget.TextView>(R.id.currentJoinCodeText).text =
            if (currentJoinCode.isNotEmpty()) "كود طلابك الحالي: $currentJoinCode" else "لسه محددتش كود لطلابك"

        findViewById<Button>(R.id.setJoinCodeButton).setOnClickListener {
            startActivity(Intent(this, SetJoinCodeActivity::class.java))
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
    }

    private fun uploadContent(title: String, description: String, youtubeUrl: String) {
        Toast.makeText(this, "جاري رفع المحتوى...", Toast.LENGTH_SHORT).show()

        var pdfUploadUrl = ""
        var videoUploadUrl = ""

        fun saveToFirestore() {
            val db = FirebaseFirestore.getInstance()
            val contentId = UUID.randomUUID().toString()
            val teacherId = "TEACHER_ID_HERE"

            val content = TeacherContent(
                id = contentId,
                teacherId = teacherId,
                title = title,
                description = description,
                youtubeUrl = youtubeUrl,
                pdfUrl = pdfUploadUrl,
                directVideoUrl = videoUploadUrl
            )

            db.collection("teacher_contents")
                .document(contentId)
                .set(content)
                .addOnSuccessListener {
                    Toast.makeText(this, "تم نشر المحتوى بنجاح! 🚀", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "فشل النشر: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        if (selectedPdfUri != null) {
            val storage = FirebaseStorage.getInstance()
            val pdfRef = storage.reference.child("pdfs/${UUID.randomUUID()}.pdf")
            pdfRef.putFile(selectedPdfUri!!).addOnSuccessListener {
                pdfRef.downloadUrl.addOnSuccessListener { downloadUri: Uri ->
                    pdfUploadUrl = downloadUri.toString()
                    uploadVideoIfExist { saveToFirestore() }
                }
            }
        } else {
            uploadVideoIfExist { saveToFirestore() }
        }
    }

    private fun uploadVideoIfExist(onComplete: () -> Unit) {
        if (selectedVideoUri != null) {
            val storage = FirebaseStorage.getInstance()
            val videoRef = storage.reference.child("videos/${UUID.randomUUID()}.mp4")
            videoRef.putFile(selectedVideoUri!!).addOnSuccessListener {
                videoRef.downloadUrl.addOnSuccessListener { downloadUri: Uri ->
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
    }
}
