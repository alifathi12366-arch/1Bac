package com.example.bac1

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ScheduleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        val wakeUpTime = findViewById<EditText>(R.id.wakeUpTime)
        val schoolStartTime = findViewById<EditText>(R.id.schoolStartTime)
        val schoolEndTime = findViewById<EditText>(R.id.schoolEndTime)
        val sleepTime = findViewById<EditText>(R.id.sleepTime)
        val resultView = findViewById<TextView>(R.id.scheduleResult)
        val generateButton = findViewById<Button>(R.id.generateScheduleButton)

        generateButton.setOnClickListener {
            try {
                val wakeUp = timeToMinutes(wakeUpTime.text.toString())
                val schoolStart = timeToMinutes(schoolStartTime.text.toString())
                val schoolEnd = timeToMinutes(schoolEndTime.text.toString())
                val sleep = timeToMinutes(sleepTime.text.toString())

                val freeMorning = schoolStart - wakeUp
                val freeEvening = sleep - schoolEnd

                val sb = StringBuilder()
                sb.append("جدولك اليومي:\n\n")

                sb.append("⏰ ${minutesToTime(wakeUp)} - الاستيقاظ\n\n")

                if (freeMorning > 0) {
                    val studyMorning = freeMorning / 2
                    sb.append("📚 ${minutesToTime(wakeUp)} - ${minutesToTime(wakeUp + studyMorning)} : مذاكرة\n")
                    sb.append("☕ ${minutesToTime(wakeUp + studyMorning)} - ${minutesToTime(schoolStart)} : راحة\n\n")
                }

                sb.append("🏫 ${minutesToTime(schoolStart)} - ${minutesToTime(schoolEnd)} : المدرسة\n\n")

                if (freeEvening > 0) {
                    val studyEvening = freeEvening / 2
                    sb.append("📚 ${minutesToTime(schoolEnd)} - ${minutesToTime(schoolEnd + studyEvening)} : مذاكرة\n")
                    sb.append("☕ ${minutesToTime(schoolEnd + studyEvening)} - ${minutesToTime(sleep)} : راحة\n\n")
                }

                sb.append("🌙 ${minutesToTime(sleep)} - النوم")

                resultView.text = sb.toString()
            } catch (e: Exception) {
                resultView.text = "من فضلك أدخل كل المواعيد بشكل صحيح (مثال: 06:30)"
            }
        }
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.trim().split(":")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        return hours * 60 + minutes
    }

    private fun minutesToTime(totalMinutes: Int): String {
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format("%02d:%02d", h, m)
    }
}