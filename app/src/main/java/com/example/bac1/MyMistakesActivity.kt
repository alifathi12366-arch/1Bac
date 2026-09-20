package com.example.bac1

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MyMistakesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_mistakes)

        val container = findViewById<LinearLayout>(R.id.mistakesContainer)
        val noMistakesText = findViewById<TextView>(R.id.noMistakesText)

        val prefs = getSharedPreferences("bac1_prefs", MODE_PRIVATE)
        val studentName = prefs.getString("student_name", "طالب") ?: "طالب"
        val teacherCode = prefs.getString("joined_teacher_code", "") ?: ""

        FirebaseFirestore.getInstance().collection("results")
            .whereEqualTo("studentName", studentName)
            .whereEqualTo("teacherCode", teacherCode)
            .get()
            .addOnSuccessListener { results ->
                var foundAny = false

                for (doc in results.documents) {
                    val examTitle = doc.getString("examTitle") ?: ""
                    val mcqAnswers = doc.get("mcqAnswers") as? List<Map<String, Any>> ?: emptyList()

                    for (answer in mcqAnswers) {
                        val correct = answer["correct"] as? Boolean ?: true
                        if (!correct) {
                            foundAny = true
                            val questionText = answer["text"] as? String ?: ""

                            val row = TextView(this)
                            row.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                            row.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                            row.textSize = 14f
                            row.setPadding(20, 16, 20, 16)
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            params.bottomMargin = 12
                            row.layoutParams = params
                            row.text = "📌 من امتحان: $examTitle\n$questionText"

                            container.addView(row)
                        }
                    }
                }

                if (!foundAny) {
                    noMistakesText.visibility = View.VISIBLE
                }
            }
    }
}
