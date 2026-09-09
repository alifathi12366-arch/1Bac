package com.example.bac1

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ScheduleActivity : AppCompatActivity() {

    private val days = listOf(
        "السبت" to "sat", "الأحد" to "sun", "الاثنين" to "mon",
        "الثلاثاء" to "tue", "الأربعاء" to "wed", "الخميس" to "thu", "الجمعة" to "fri"
    )

    private val categories = listOf(
        "🏫 المدرسة" to "school", "📖 الدروس" to "lessons",
        "📚 المذاكرة" to "study", "🛌 الراحة" to "rest"
    )

    private val allFields = mutableMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        val container = findViewById<LinearLayout>(R.id.scheduleContainer)
        val prefs = getSharedPreferences("bac1_schedule", Context.MODE_PRIVATE)

        for ((dayName, dayKey) in days) {
            val dayTitle = TextView(this).apply {
                text = dayName
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#3DBFA0"))
                setPadding(0, 32, 0, 8)
            }
            container.addView(dayTitle)

            for ((catLabel, catKey) in categories) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 4, 0, 4)
                }

                val label = TextView(this).apply {
                    text = catLabel
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val fromKey = "${dayKey}_${catKey}_from"
                val toKey = "${dayKey}_${catKey}_to"

                val fromField = EditText(this).apply {
                    hint = "من"
                    inputType = InputType.TYPE_CLASS_TEXT
                    setText(prefs.getString(fromKey, ""))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val toField = EditText(this).apply {
                    hint = "إلى"
                    inputType = InputType.TYPE_CLASS_TEXT
                    setText(prefs.getString(toKey, ""))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                allFields[fromKey] = fromField
                allFields[toKey] = toField

                row.addView(label)
                row.addView(fromField)
                row.addView(toField)
                container.addView(row)
            }
        }

        findViewById<Button>(R.id.saveScheduleButton).setOnClickListener {
            val editor = prefs.edit()
            for ((key, field) in allFields) {
                editor.putString(key, field.text.toString())
            }
            editor.apply()
            Toast.makeText(this, "تم حفظ الجدول ✅", Toast.LENGTH_SHORT).show()
        }
    }
}
