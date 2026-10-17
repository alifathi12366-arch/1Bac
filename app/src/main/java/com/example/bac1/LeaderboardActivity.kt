package com.example.bac1

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class LeaderboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        val months = listOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
        val cal = Calendar.getInstance()
        val monthName = months[cal.get(Calendar.MONTH)]
        val year = cal.get(Calendar.YEAR)
        val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysLeft = lastDay - cal.get(Calendar.DAY_OF_MONTH)
        val leftText = if (daysLeft == 0) "ينتهي اليوم" else "باقي $daysLeft يوم"

        findViewById<TextView>(R.id.deadlineText).text =
            "مسابقة $monthName $year · الإعلان عن البطل يوم $lastDay $monthName · $leftText"

        val recyclerView = findViewById<RecyclerView>(R.id.leaderboardRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        LeaderboardManager.getTopStudents { students ->
            recyclerView.adapter = LeaderboardAdapter(students)
        }
    }
}
