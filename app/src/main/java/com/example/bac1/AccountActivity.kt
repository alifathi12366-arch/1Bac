package com.example.bac1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        val facebookButton = findViewById<Button>(R.id.facebookButton)

        // استرجاع الاسم المحفوظ لو موجود
        val savedName = prefs.getString("student_name", "") ?: ""
        if (savedName.isNotEmpty()) {
            nameInput.setText(savedName)
            welcomeText.text = "أهلاً بيك يا $savedName 👋"
        }

        // استرجاع النقط
        val points = prefs.getInt("student_points", 0)
        pointsText.text = "نقاطك: $points"

        saveNameButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                prefs.edit().putString("student_name", name).apply()
                welcomeText.text = "أهلاً بيك يا $name 👋"
            }
        }

        facebookButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/1F8T5LvFYz/"))
            startActivity(intent)
        }
    }
}
