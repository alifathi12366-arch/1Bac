package com.example.bac1

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LessonsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lessons)

        val subjectKey = intent.getStringExtra("subjectKey") ?: "unknown"
        val subjectName = intent.getStringExtra("subjectName") ?: "المادة"

        val titleView = findViewById<TextView>(R.id.lessonsTitle)
        titleView.text = "دروس $subjectName"

        val progressText = findViewById<TextView>(R.id.progressText)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val recyclerView = findViewById<RecyclerView>(R.id.lessonsRecyclerView)

        val lessons = LessonsData.getLessons(subjectKey).toMutableList()

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)

        fun updateProgress() {
            var doneCount = 0
            for (i in lessons.indices) {
                if (prefs.getBoolean("$subjectKey-$i", false)) doneCount++
            }
            val percent = if (lessons.isNotEmpty()) (doneCount * 100 / lessons.size) else 0
            progressText.text = "التقدم: $percent%"
            progressBar.progress = percent
        }

        val adapter = LessonAdapter(lessons, subjectKey, prefs) {
            updateProgress()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        updateProgress()
    }
}
