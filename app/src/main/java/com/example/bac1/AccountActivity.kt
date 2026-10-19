package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AccountActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val saveNameButton = findViewById<Button>(R.id.saveNameButton)
        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        val pointsText = findViewById<TextView>(R.id.pointsText)
        val lastLoginText = findViewById<TextView>(R.id.lastLoginText)
        val facebookButton = findViewById<Button>(R.id.facebookButton)

        var studentId = prefs.getString("student_id", "") ?: ""
        if (studentId.isEmpty()) {
            studentId = (1000..9999).random().toString()
            prefs.edit().putString("student_id", studentId).apply()
        }

        val savedName = prefs.getString("student_name", "") ?: ""
        if (savedName.isNotEmpty()) {
            nameInput.setText(savedName)
            nameInput.isEnabled = false
            saveNameButton.isEnabled = false
            saveNameButton.text = "الاسم محفوظ"
            welcomeText.text = "أهلاً بيك يا $savedName (كودك: #$studentId) 👋"
        }

        val points = PointsManager.getMonthPoints(prefs)
        pointsText.text = "نقاطك هذا الشهر: $points (الإجمالي: ${prefs.getInt("student_points", 0)})"

        val lastLogin = prefs.getString("last_login_date", "")
        if (!lastLogin.isNullOrEmpty()) {
            lastLoginText.text = "آخر دخول ليك: $lastLogin"
        }

        if (savedName.isNotEmpty()) {
            LeaderboardManager.submitScore(studentId, savedName, points)
        }

        saveNameButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                prefs.edit().putString("student_name", name).apply()
                welcomeText.text = "أهلاً بيك يا $name (كودك: #$studentId) 👋"
                nameInput.isEnabled = false
                saveNameButton.isEnabled = false
                saveNameButton.text = "الاسم محفوظ"
                LeaderboardManager.submitScore(studentId, name, points)
            }
        }

        // ضغطة طويلة مخفية للأدمن (لكودك فقط + رقم سري)
        welcomeText.setOnLongClickListener {
            if (studentId == "1089") {
                val pinInput = EditText(this)
                pinInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                android.app.AlertDialog.Builder(this)
                    .setTitle("الرقم السري")
                    .setView(pinInput)
                    .setPositiveButton("دخول") { _, _ ->
                        if (pinInput.text.toString() == "7391") {
                            android.app.AlertDialog.Builder(this)
                                .setTitle("لوحة الأدمن")
                                .setItems(arrayOf("➕ أسئلة اليوم", "📢 التقييمات الأسبوعية")) { _, which ->
                                    if (which == 0) {
                                        startActivity(Intent(this, AdminQuestionsActivity::class.java))
                                    } else {
                                        startActivity(Intent(this, AdminWeeklyActivity::class.java))
                                    }
                                }
                                .show()
                        }
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            true
        }

        facebookButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/1F8T5LvFYz/"))
            startActivity(intent)
        }

        findViewById<TextView>(R.id.teacherRegisterButton).setOnClickListener {
            startActivity(Intent(this, TeacherRegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.teacherCodeButton).setOnClickListener {
            startActivity(Intent(this, TeacherCodeActivity::class.java))
        }

        // زرار مستواي الدراسي (بنستخدم الزرار القديم)
        val reportBtn = findViewById<Button>(R.id.leaderboardButton)
        reportBtn.text = "📊 مستواي الدراسي"
        reportBtn.visibility = View.VISIBLE
        reportBtn.setOnClickListener { startActivity(Intent(this, ReportActivity::class.java)) }
    }
}
