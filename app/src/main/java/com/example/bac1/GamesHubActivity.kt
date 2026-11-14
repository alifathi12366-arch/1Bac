package com.example.bac1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class GamesHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_hub)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.hubRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val isTeacher = prefs.getBoolean("is_teacher", false)
        val teacherCode = if (isTeacher) prefs.getString("teacher_code", "") ?: ""
        else intent.getStringExtra("teacherCode") ?: ""

        findViewById<TextView>(R.id.contentTeacherName).text =
            if (isTeacher) "🎮 ألعاب الحصة" else "🎮 الألعاب"
        findViewById<View>(R.id.btnMarkAttendance).visibility = View.GONE

        val grid = findViewById<LinearLayout>(R.id.gridContainer)
        val soon: () -> Unit = {
            Toast.makeText(this, "قريبًا 🔜 جارٍ تجهيز اللعبة", Toast.LENGTH_SHORT).show()
        }

        var row = CardKit.newRow(this, grid)
        CardKit.addCard(
            this, row, "دوري الأبطال", "تنافس مباشر والأعلى نقاطًا يكسب", "🏆",
            "#FCE9C8", "#FDDA96", "#F08A24"
        ) {
            if (isTeacher) {
                startActivity(Intent(this, LeagueTeacherHomeActivity::class.java))
            } else {
                startActivity(
                    Intent(this, LeagueStudentActivity::class.java).putExtra("teacherCode", teacherCode)
                )
            }
        }
        CardKit.addCard(
            this, row, "عجلة الحظ", "لفّ العجلة وجاوب السؤال", "🎡",
            "#FFD9EE", "#FDC4E6", "#E5365A"
        ) {
            if (isTeacher) {
                startActivity(Intent(this, WheelTeacherHomeActivity::class.java))
            } else {
                startActivity(
                    Intent(this, WheelPlayActivity::class.java).putExtra("teacherCode", teacherCode)
                )
            }
        }

        row = CardKit.newRow(this, grid)
        CardKit.addCard(
            this, row, "من سيربح التحدي", "أسئلة بمستويات زي المليون", "💰",
            "#D2F6DE", "#B9EFCD", "#2F7BD8"
        ) {
            if (isTeacher) {
                startActivity(Intent(this, MillionTeacherHomeActivity::class.java))
            } else {
                startActivity(
                    Intent(this, MillionStudentActivity::class.java).putExtra("teacherCode", teacherCode)
                )
            }
        }
        CardKit.addCard(
            this, row, "وصّل الكلمة بمعناها", "وصّل كل كلمة بمعناها", "🔗",
            "#D3E8FF", "#BEDDFD", "#2F6BEA", soon
        )

        row = CardKit.newRow(this, grid)
        CardKit.addCard(
            this, row, "صنّف المعلومات", "اسحب كل معلومة لتصنيفها", "🗂️",
            "#E3D8FE", "#D0BCFC", "#6A3CD6", soon
        )
    }
}
