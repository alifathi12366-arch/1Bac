package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

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
            welcomeText.text = "أهلاً بيك يا $savedName 👋"
        }

        val points = prefs.getInt("student_points", 0)
        pointsText.text = "نقاطك: $points"

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
                welcomeText.text = "أهلاً بيك يا $name 👋"
                nameInput.isEnabled = false
                saveNameButton.isEnabled = false
                saveNameButton.text = "الاسم محفوظ"
                LeaderboardManager.submitScore(studentId, name, points)
            }
        }

        facebookButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/1F8T5LvFYz/"))
            startActivity(intent)
        }

        findViewById<Button>(R.id.leaderboardButton).setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
    }
}
        }
    }
}
