package com.example.bac1

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TeacherDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_dashboard)

        val prefs = getSharedPreferences("bac1_prefs", Context.MODE_PRIVATE)
        val teacherName = prefs.getString("teacher_name", "") ?: ""
        val teacherSubject = prefs.getString("teacher_subject", "") ?: ""
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

        findViewById<android.widget.Button>(R.id.sendMessageButton).setOnClickListener {
            startActivity(android.content.Intent(this, SendMessageActivity::class.java))
        }
    }
}
