package com.example.yourApp // ⚠️ غير لاسم الباكيج بتاعك

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

class TeacherDashboardActivity : AppCompatActivity() {

    private var selectedPdfUri: Uri? = null
    private var selectedVideoUri: Uri? = null

    // مسجلات النتائج لاختيار الملفات من الموبايل
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

        // اختيار ملف PDF
        btnSelectPdf.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }

        // اختيار فيديو
        btnSelectVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        // نشر المحتوى
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

        val storage = FirebaseStorage.getInstance()
        var pdfUploadUrl = ""
        var videoUploadUrl = ""

        // دالة مساعدة للحفظ في الفايربيس بعد الرفع
        fun saveToFirestore() {
            val db = FirebaseFirestore.getInstance()
            val contentId = UUID.randomUUID().toString()
            
            // ⚠️ غير "TEACHER_ID_HERE" لمعرّف المدرس أو الكود الخاص بيه
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

        // رفع الـ PDF لو موجود الأول، بعدها رفع الفيديو، ثم الحفظ
        if (selectedPdfUri != null) {
            val pdfRef = storage.reference.child("pdfs/${UUID.randomUUID()}.pdf")
            pdfRef.putFile(selectedPdfUri!!).addOnSuccessListener {
                pdfRef.downloadUrl.addOnSuccessListener { uri ->
                    pdfUploadUrl = uri.toString()
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
                videoRef.downloadUrl.addOnSuccessListener { uri ->
                    // حفظ رابط الفيديو المرفوع
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
    }
}
