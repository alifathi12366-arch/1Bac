package com.example.bac1

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

        findViewById<TextView>(R.id.contentTeacherName).text = "🎮 الألعاب"
        findViewById<View>(R.id.btnMarkAttendance).visibility = View.GONE

        val grid = findViewById<LinearLayout>(R.id.gridContainer)
        val soon: () -> Unit = {
            Toast.makeText(this, "قريبًا 🔜 جارٍ تجهيز اللعبة", Toast.LENGTH_SHORT).show()
        }

        var row = CardKit.newRow(this, grid)
        CardKit.addCard(
            this, row, "دوري الأبطال", "تنافس مباشر والأعلى نقاطًا يكسب", "🏆",
            "#FCE9C8", "#FDDA96", "#F08A24", soon
        )
        CardKit.addCard(
            this, row, "عجلة الحظ", "لفّ العجلة وجاوب السؤال", "🎡",
            "#FFD9EE", "#FDC4E6", "#E5365A", soon
        )

        row = CardKit.newRow(this, grid)
        CardKit.addCard(
            this, row, "من سيربح التحدي", "أسئلة بمستويات زي المليون", "💰",
            "#D2F6DE", "#B9EFCD", "#2F7BD8", soon
        )
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
